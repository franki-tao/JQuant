package pricingengines;

import jquant.CashFlow;
import jquant.Compounding;
import jquant.Handle;
import jquant.Quote;
import jquant.RelinkableHandle;
import jquant.SavedSettings;
import jquant.Settings;
import jquant.cashflows.FixedRateCoupon;
import jquant.cashflows.IborCoupon;
import jquant.indexes.Eonia;
import jquant.indexes.Euribor6M;
import jquant.indexes.EuriborSwapIsdaFixA;
import jquant.indexes.IborIndex;
import jquant.indexes.IndexManager;
import jquant.indexes.OvernightIndex;
import jquant.instruments.MakeOIS;
import jquant.instruments.MakeSwaption;
import jquant.instruments.MakeVanillaSwap;
import jquant.instruments.OvernightIndexedSwap;
import jquant.instruments.Settlement;
import jquant.instruments.Swap;
import jquant.instruments.Swaption;
import jquant.instruments.VanillaSwap;
import jquant.pricingengines.DiscountingSwapEngine;
import jquant.pricingengines.swaption.BachelierSwaptionEngine;
import jquant.pricingengines.swaption.BlackSwaptionEngine;
import jquant.quotes.SimpleQuote;
import jquant.termstructures.YieldTermStructure;
import jquant.termstructures.yield.FlatForward;
import jquant.termstructures.yield.ZeroSpreadedTermStructure;
import jquant.time.BusinessDayConvention;
import jquant.time.Calendar;
import jquant.time.Date;
import jquant.time.DateGenerationRule;
import jquant.time.DayCounter;
import jquant.time.Frequency;
import jquant.time.Month;
import jquant.time.Period;
import jquant.time.Schedule;
import jquant.time.TimeUnit;
import jquant.time.TimeUtils;
import jquant.time.calendars.UnitedStates;
import jquant.time.daycounters.Actual360;
import jquant.time.daycounters.Actual365Fixed;
import jquant.time.daycounters.Thirty360;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Swaption pricing from QuantLib {@code test-suite/swaption.cpp}.
 *
 * <p>All 13 official {@code BOOST_AUTO_TEST_CASE}s are ported (the working
 * brief says 12; the official file contains 13).
 *
 * <p>Cases with official dates keep them verbatim: 13/15 March 2002 for
 * {@code testCachedValue} and 9 October 2015 for the two {@code MakeSwaption}
 * cases. All other cases use the run-day evaluation date, as the official
 * suite does. No historic fixings are injected.
 */
public class SwaptionTest {

    private SavedSettings savedSettings_;

    @BeforeEach
    public void setUp() {
        savedSettings_ = new SavedSettings();
    }

    @AfterEach
    public void tearDown() {
        try {
            savedSettings_.restore();
        } finally {
            IndexManager.Instance.clearHistories();
        }
    }

    private static final Period[] exercises = {
            new Period(1, TimeUnit.YEARS), new Period(2, TimeUnit.YEARS),
            new Period(3, TimeUnit.YEARS), new Period(5, TimeUnit.YEARS),
            new Period(7, TimeUnit.YEARS), new Period(10, TimeUnit.YEARS)
    };

    private static final Period[] lengths = {
            new Period(1, TimeUnit.YEARS), new Period(2, TimeUnit.YEARS),
            new Period(3, TimeUnit.YEARS), new Period(5, TimeUnit.YEARS),
            new Period(7, TimeUnit.YEARS), new Period(10, TimeUnit.YEARS),
            new Period(15, TimeUnit.YEARS), new Period(20, TimeUnit.YEARS)
    };

    private static final Swap.Type[] type = {Swap.Type.Receiver, Swap.Type.Payer};

    private static FlatForward flatRate(Date settlement, double rate, DayCounter dc) {
        return new FlatForward(settlement, rate, dc,
                Compounding.Continuous, Frequency.ANNUAL);
    }

    private static class CommonVars {
        Date today;
        Date settlement;
        double nominal;
        Calendar calendar;
        BusinessDayConvention fixedConvention;
        Frequency fixedFrequency;
        DayCounter fixedDayCount;
        BusinessDayConvention floatingConvention;
        Period floatingTenor;
        IborIndex index;
        OvernightIndex oisIndex;
        int settlementDays;
        final RelinkableHandle<YieldTermStructure> termStructure =
                new RelinkableHandle<>();

        CommonVars() {
            settlementDays = 2;
            nominal = 1000000.0;
            fixedConvention = BusinessDayConvention.UNADJUSTED;
            fixedFrequency = Frequency.ANNUAL;
            fixedDayCount = new Thirty360(Thirty360.Convention.BondBasis);

            index = new Euribor6M(termStructure);
            oisIndex = new Eonia(new Handle<YieldTermStructure>(
                    new ZeroSpreadedTermStructure(termStructure,
                            new Handle<>(new SimpleQuote(-0.01), true)), true));
            floatingConvention = index.businessDayConvention();
            floatingTenor = index.tenor();
            calendar = index.fixingCalendar();
            today = Settings.instance.evaluationDate().Date();
            settlement = calendar.advance(today, settlementDays, TimeUnit.DAYS);
            termStructure.linkTo(flatRate(settlement, 0.05,
                    new Actual365Fixed()), true);
        }

        Swaption makeSwaption(VanillaSwap swap,
                              Date exercise,
                              double volatility,
                              Settlement.Type settlementType,
                              Settlement.Method settlementMethod,
                              BlackSwaptionEngine.CashAnnuityModel model) {
            Handle<Quote> vol = new Handle<>(new SimpleQuote(volatility), true);
            BlackSwaptionEngine engine = new BlackSwaptionEngine(
                    termStructure, vol, new Actual365Fixed(), 0.0, model);
            Swaption result = new Swaption(swap, new jquant.EuropeanExercise(exercise),
                    settlementType, settlementMethod);
            result.setPricingEngine(engine);
            return result;
        }

        Swaption makeSwaption(VanillaSwap swap,
                              Date exercise,
                              double volatility,
                              Settlement.Type settlementType,
                              Settlement.Method settlementMethod) {
            return makeSwaption(swap, exercise, volatility, settlementType,
                    settlementMethod, BlackSwaptionEngine.CashAnnuityModel.SwapRate);
        }

        Swaption makeSwaption(VanillaSwap swap, Date exercise, double volatility) {
            return makeSwaption(swap, exercise, volatility,
                    Settlement.Type.Physical, Settlement.Method.PhysicalOTC,
                    BlackSwaptionEngine.CashAnnuityModel.SwapRate);
        }

        Swaption makeOISwaption(OvernightIndexedSwap swap,
                                Date exercise,
                                double volatility,
                                Settlement.Type settlementType,
                                Settlement.Method settlementMethod,
                                BlackSwaptionEngine.CashAnnuityModel model) {
            Handle<Quote> vol = new Handle<>(new SimpleQuote(volatility), true);
            BlackSwaptionEngine engine = new BlackSwaptionEngine(
                    termStructure, vol, new Actual365Fixed(), 0.0, model);
            Swaption result = new Swaption(swap, new jquant.EuropeanExercise(exercise),
                    settlementType, settlementMethod);
            result.setPricingEngine(engine);
            return result;
        }

        BlackSwaptionEngine makeEngine(double volatility,
                                       BlackSwaptionEngine.CashAnnuityModel model) {
            Handle<Quote> h = new Handle<>(new SimpleQuote(volatility), true);
            return new BlackSwaptionEngine(termStructure, h,
                    new Actual365Fixed(), 0.0, model);
        }
    }

    private static String describe(Object... parts) {
        StringBuilder b = new StringBuilder();
        for (Object p : parts)
            b.append(p).append(' ');
        return b.toString();
    }

    @Test
    public void testBlackEngineCaching() {
        CommonVars vars = new CommonVars();

        Date exerciseDate = vars.calendar.advance(vars.today,
                new Period(1, TimeUnit.YEARS));
        Date startDate = vars.calendar.advance(exerciseDate,
                vars.settlementDays, TimeUnit.DAYS);

        VanillaSwap swap = new MakeVanillaSwap(new Period(1, TimeUnit.YEARS), vars.index)
                .withFixedRate(0.03)
                .withEffectiveDate(startDate)
                .withFixedLegTenor(new Period(1, TimeUnit.YEARS))
                .withFixedLegDayCount(vars.fixedDayCount)
                .withFloatingLegSpread(0.0)
                .withType(Swap.Type.Payer)
                .makeSwap();
        Swaption swaption = vars.makeSwaption(swap, exerciseDate, 0.12);

        assertFalse(swaption.isCalculated(), "swaption should not be calculated yet");

        swaption.NPV();

        assertTrue(swaption.isCalculated(), "swaption should be calculated");
    }

    @Test
    public void testStrikeDependency() {
        CommonVars vars = new CommonVars();

        double[] strikes = {0.03, 0.04, 0.05, 0.06, 0.07};

        for (Period exercise : exercises) {
            for (Period length : lengths) {
                for (Swap.Type k : type) {
                    Date exerciseDate = vars.calendar.advance(vars.today, exercise);
                    Date startDate = vars.calendar.advance(exerciseDate,
                            vars.settlementDays, TimeUnit.DAYS);
                    List<Double> values = new ArrayList<>();
                    List<Double> valuesCash = new ArrayList<>();
                    double vol = 0.20;
                    for (double strike : strikes) {
                        VanillaSwap swap = new MakeVanillaSwap(length, vars.index)
                                .withFixedRate(strike)
                                .withEffectiveDate(startDate)
                                .withFixedLegTenor(new Period(1, TimeUnit.YEARS))
                                .withFixedLegDayCount(vars.fixedDayCount)
                                .withFloatingLegSpread(0.0)
                                .withType(k)
                                .makeSwap();
                        values.add(vars.makeSwaption(swap, exerciseDate, vol).NPV());
                        valuesCash.add(vars.makeSwaption(swap, exerciseDate, vol,
                                Settlement.Type.Cash, Settlement.Method.ParYieldCurve).NPV());
                    }
                    if (k == Swap.Type.Payer) {
                        for (int n = 0; n + 1 < values.size(); n++) {
                            if (values.get(n + 1) >= values.get(n))
                                fail(describe("NPV of Payer swaption with delivery settlement",
                                        "is increasing with the strike:", "option tenor:", exercise,
                                        "option date:", exerciseDate, "volatility:", vol,
                                        "swap tenor:", length, "value:", values.get(n),
                                        "at strike:", strikes[n], "value:", values.get(n + 1),
                                        "at strike:", strikes[n + 1]));
                            if (valuesCash.get(n + 1) >= valuesCash.get(n))
                                fail(describe("NPV of Payer swaption with cash settlement",
                                        "is increasing with the strike:", "option tenor:", exercise,
                                        "option date:", exerciseDate, "volatility:", vol,
                                        "swap tenor:", length, "value:", valuesCash.get(n),
                                        "at strike:", strikes[n], "value:", valuesCash.get(n + 1),
                                        "at strike:", strikes[n + 1]));
                        }
                    } else {
                        for (int n = 0; n + 1 < values.size(); n++) {
                            if (values.get(n + 1) <= values.get(n))
                                fail(describe("NPV of Receiver swaption with delivery settlement",
                                        "is increasing with the strike:", "option tenor:", exercise,
                                        "option date:", exerciseDate, "volatility:", vol,
                                        "swap tenor:", length, "value:", values.get(n),
                                        "at strike:", strikes[n], "value:", values.get(n + 1),
                                        "at strike:", strikes[n + 1]));
                            if (valuesCash.get(n + 1) <= valuesCash.get(n))
                                fail(describe("NPV of Receiver swaption with cash settlement",
                                        "is increasing with the strike:", "option tenor:", exercise,
                                        "option date:", exerciseDate, "volatility:", vol,
                                        "swap tenor:", length, "value:", valuesCash.get(n),
                                        "at strike:", strikes[n], "value:", valuesCash.get(n + 1),
                                        "at strike:", strikes[n + 1]));
                        }
                    }
                }
            }
        }
    }

    @Test
    public void testSpreadDependency() {
        CommonVars vars = new CommonVars();

        double[] spreads = {-0.002, -0.001, 0.0, 0.001, 0.002};

        for (Period exercise : exercises) {
            for (Period length : lengths) {
                for (Swap.Type k : type) {
                    Date exerciseDate = vars.calendar.advance(vars.today, exercise);
                    Date startDate = vars.calendar.advance(exerciseDate,
                            vars.settlementDays, TimeUnit.DAYS);
                    List<Double> values = new ArrayList<>();
                    List<Double> valuesCash = new ArrayList<>();
                    for (double spread : spreads) {
                        VanillaSwap swap = new MakeVanillaSwap(length, vars.index)
                                .withFixedRate(0.06)
                                .withFixedLegTenor(new Period(1, TimeUnit.YEARS))
                                .withFixedLegDayCount(vars.fixedDayCount)
                                .withEffectiveDate(startDate)
                                .withFloatingLegSpread(spread)
                                .withType(k)
                                .makeSwap();
                        values.add(vars.makeSwaption(swap, exerciseDate, 0.20).NPV());
                        valuesCash.add(vars.makeSwaption(swap, exerciseDate, 0.20,
                                Settlement.Type.Cash, Settlement.Method.ParYieldCurve).NPV());
                    }
                    if (k == Swap.Type.Payer) {
                        for (int n = 0; n + 1 < values.size(); n++) {
                            if (values.get(n + 1) <= values.get(n))
                                fail(describe("NPV is decreasing with the spread",
                                        "in a payer swaption (physical delivered):",
                                        "exercise date:", exerciseDate, "length:", length,
                                        "value:", values.get(n), "for spread:", spreads[n],
                                        "value:", values.get(n + 1), "for spread:", spreads[n + 1]));
                            if (valuesCash.get(n + 1) <= valuesCash.get(n))
                                fail(describe("NPV is decreasing with the spread",
                                        "in a payer swaption (cash delivered):",
                                        "exercise date:", exerciseDate, "length:", length,
                                        "value:", valuesCash.get(n), "for spread:", spreads[n],
                                        "value:", valuesCash.get(n + 1), "for spread:", spreads[n + 1]));
                        }
                    } else {
                        for (int n = 0; n + 1 < values.size(); n++) {
                            if (values.get(n + 1) >= values.get(n))
                                fail(describe("NPV is increasing with the spread",
                                        "in a receiver swaption (physical delivered):",
                                        "exercise date:", exerciseDate, "length:", length,
                                        "value:", values.get(n), "for spread:", spreads[n],
                                        "value:", values.get(n + 1), "for spread:", spreads[n + 1]));
                            if (valuesCash.get(n + 1) >= valuesCash.get(n))
                                fail(describe("NPV is increasing with the spread",
                                        "in a receiver swaption (cash delivered):",
                                        "exercise date:", exerciseDate, "length:", length,
                                        "value:", valuesCash.get(n), "for spread:", spreads[n],
                                        "value:", valuesCash.get(n + 1), "for spread:", spreads[n + 1]));
                        }
                    }
                }
            }
        }
    }

    @Test
    public void testSpreadTreatment() {
        CommonVars vars = new CommonVars();

        double[] spreads = {-0.002, -0.001, 0.0, 0.001, 0.002};

        for (Period exercise : exercises) {
            for (Period length : lengths) {
                for (Swap.Type k : type) {
                    Date exerciseDate = vars.calendar.advance(vars.today, exercise);
                    Date startDate = vars.calendar.advance(exerciseDate,
                            vars.settlementDays, TimeUnit.DAYS);
                    for (double spread : spreads) {
                        VanillaSwap swap = new MakeVanillaSwap(length, vars.index)
                                .withFixedRate(0.06)
                                .withFixedLegTenor(new Period(1, TimeUnit.YEARS))
                                .withFixedLegDayCount(vars.fixedDayCount)
                                .withEffectiveDate(startDate)
                                .withFloatingLegSpread(spread)
                                .withType(k)
                                .makeSwap();
                        double correction = spread * swap.floatingLegBPS() / swap.fixedLegBPS();
                        VanillaSwap equivalentSwap = new MakeVanillaSwap(length, vars.index)
                                .withFixedRate(0.06 + correction)
                                .withFixedLegTenor(new Period(1, TimeUnit.YEARS))
                                .withFixedLegDayCount(vars.fixedDayCount)
                                .withEffectiveDate(startDate)
                                .withFloatingLegSpread(0.0)
                                .withType(k)
                                .makeSwap();
                        Swaption swaption1 = vars.makeSwaption(swap, exerciseDate, 0.20);
                        Swaption swaption2 = vars.makeSwaption(equivalentSwap, exerciseDate, 0.20);
                        Swaption swaption1Cash = vars.makeSwaption(swap, exerciseDate, 0.20,
                                Settlement.Type.Cash, Settlement.Method.ParYieldCurve);
                        Swaption swaption2Cash = vars.makeSwaption(equivalentSwap, exerciseDate, 0.20,
                                Settlement.Type.Cash, Settlement.Method.ParYieldCurve);
                        if (Math.abs(swaption1.NPV() - swaption2.NPV()) > 1.0e-6)
                            fail(describe("wrong spread treatment:", "exercise:", exerciseDate,
                                    "length:", length, "type", k, "spread:", spread,
                                    "original swaption value:", swaption1.NPV(),
                                    "equivalent swaption value:", swaption2.NPV()));
                        if (Math.abs(swaption1Cash.NPV() - swaption2Cash.NPV()) > 1.0e-6)
                            fail(describe("wrong spread treatment:", "exercise date:", exerciseDate,
                                    "length:", length, "pay", (k == Swap.Type.Payer ? "fixed" : "floating"),
                                    "spread:", spread,
                                    "value of original swaption:", swaption1Cash.NPV(),
                                    "value of equivalent swaption:", swaption2Cash.NPV()));
                    }
                }
            }
        }
    }

    @Test
    public void testCachedValue() {
        boolean usingAtParCoupons = IborCoupon.Settings.instance().usingAtParCoupons();

        CommonVars vars = new CommonVars();

        vars.today = new Date(13, Month.MARCH, 2002);
        vars.settlement = new Date(15, Month.MARCH, 2002);
        Settings.instance.evaluationDate().equal(vars.today);
        vars.termStructure.linkTo(flatRate(vars.settlement, 0.05,
                new Actual365Fixed()), true);
        Date exerciseDate = vars.calendar.advance(vars.settlement,
                new Period(5, TimeUnit.YEARS));
        Date startDate = vars.calendar.advance(exerciseDate,
                vars.settlementDays, TimeUnit.DAYS);
        VanillaSwap swap = new MakeVanillaSwap(new Period(10, TimeUnit.YEARS), vars.index)
                .withFixedRate(0.06)
                .withEffectiveDate(startDate)
                .withFixedLegTenor(new Period(1, TimeUnit.YEARS))
                .withFixedLegDayCount(vars.fixedDayCount)
                .makeSwap();

        Swaption swaption = vars.makeSwaption(swap, exerciseDate, 0.20);

        double cachedNPV = usingAtParCoupons ? 0.036418158579 : 0.036421429684;

        if (Math.abs(swaption.NPV() - cachedNPV) > 1.0e-12)
            fail("failed to reproduce cached swaption value:\n"
                    + "calculated: " + swaption.NPV() + "\nexpected:   " + cachedNPV);

        OvernightIndexedSwap oiswap = new MakeOIS(new Period(10, TimeUnit.YEARS), vars.oisIndex)
                .withFixedRate(0.06)
                .withEffectiveDate(startDate)
                .withPaymentFrequency(Frequency.ANNUAL)
                .withFixedLegDayCount(vars.fixedDayCount)
                .makeSwap();

        Swaption oiswaption = vars.makeOISwaption(oiswap, exerciseDate, 0.20,
                Settlement.Type.Physical, Settlement.Method.PhysicalOTC,
                BlackSwaptionEngine.CashAnnuityModel.SwapRate);

        cachedNPV = 0.014101075767;

        if (Math.abs(oiswaption.NPV() - cachedNPV) > 1.0e-12)
            fail("failed to reproduce cached overnight-indexed swaption value:\n"
                    + "calculated: " + oiswaption.NPV() + "\nexpected:   " + cachedNPV);
    }

    @Test
    public void testVega() {
        CommonVars vars = new CommonVars();

        Settlement.Type[] types = {Settlement.Type.Physical, Settlement.Type.Cash};
        Settlement.Method[] methods = {
                Settlement.Method.PhysicalOTC, Settlement.Method.ParYieldCurve};
        double[] strikes = {0.03, 0.04, 0.05, 0.06, 0.07};
        double[] vols = {0.01, 0.20, 0.30, 0.70, 0.90};
        double shift = 1e-8;
        for (Period exercise : exercises) {
            Date exerciseDate = vars.calendar.advance(vars.today, exercise);
            Date startDate = vars.calendar.advance(exerciseDate,
                    vars.settlementDays, TimeUnit.DAYS);
            for (Period length : lengths) {
                for (double strike : strikes) {
                    for (int h = 0; h < type.length; h++) {
                        VanillaSwap swap = new MakeVanillaSwap(length, vars.index)
                                .withFixedRate(strike)
                                .withEffectiveDate(startDate)
                                .withFixedLegTenor(new Period(1, TimeUnit.YEARS))
                                .withFixedLegDayCount(vars.fixedDayCount)
                                .withFloatingLegSpread(0.0)
                                .withType(type[h])
                                .makeSwap();
                        for (double vol : vols) {
                            Swaption swaption = vars.makeSwaption(swap, exerciseDate, vol,
                                    types[h], methods[h]);
                            Swaption swaption1 = vars.makeSwaption(swap, exerciseDate, vol - shift,
                                    types[h], methods[h]);
                            Swaption swaption2 = vars.makeSwaption(swap, exerciseDate, vol + shift,
                                    types[h], methods[h]);

                            double swaptionNPV = swaption.NPV();
                            double numericalVegaPerPoint =
                                    (swaption2.NPV() - swaption1.NPV()) / (200.0 * shift);
                            // check only relevant vega
                            if (numericalVegaPerPoint / swaptionNPV > 1.0e-7) {
                                double analyticalVegaPerPoint =
                                        swaption.result("vega", Double.class) / 100.0;
                                double discrepancy = Math.abs(analyticalVegaPerPoint
                                        - numericalVegaPerPoint);
                                discrepancy /= numericalVegaPerPoint;
                                double tolerance = 0.015;
                                if (discrepancy > tolerance)
                                    fail(describe("failed to compute swaption vega:",
                                            "option tenor:", exercise, "volatility:", vol,
                                            "option type:", swaption.type(), "swap tenor:", length,
                                            "strike:", strike, "settlement:", types[h],
                                            "nominal:", swaption.underlying().nominal(),
                                            "npv:", swaptionNPV,
                                            "calculated vega:", analyticalVegaPerPoint,
                                            "expected vega:", numericalVegaPerPoint,
                                            "discrepancy:", discrepancy,
                                            "tolerance:", tolerance));
                            }
                        }
                    }
                }
            }
        }
    }

    @Test
    public void testCashSettledSwaptions() {
        CommonVars vars = new CommonVars();

        double strike = 0.05;

        for (Period exercise : exercises) {
            for (Period length : lengths) {
                Date exerciseDate = vars.calendar.advance(vars.today, exercise);
                Date startDate = vars.calendar.advance(exerciseDate,
                        vars.settlementDays, TimeUnit.DAYS);
                Date maturity = vars.calendar.advance(startDate, length,
                        vars.floatingConvention);
                Schedule floatSchedule = new Schedule(startDate, maturity,
                        vars.floatingTenor, vars.calendar, vars.floatingConvention,
                        vars.floatingConvention, DateGenerationRule.FORWARD, false,
                        new Date(), new Date());
                // Swap with fixed leg conventions: Business Days = Unadjusted, DayCount = 30/360
                Schedule fixedScheduleU = new Schedule(startDate, maturity,
                        new Period(vars.fixedFrequency), vars.calendar,
                        BusinessDayConvention.UNADJUSTED, BusinessDayConvention.UNADJUSTED,
                        DateGenerationRule.FORWARD, true, new Date(), new Date());
                VanillaSwap swapU360 = new VanillaSwap(VanillaSwap.Type.Receiver,
                        vars.nominal, fixedScheduleU, strike,
                        new Thirty360(Thirty360.Convention.BondBasis), floatSchedule,
                        vars.index, 0.0, vars.index.dayCounter());

                // Swap with fixed leg conventions: Business Days = Unadjusted, DayCount = Act/365
                VanillaSwap swapU365 = new VanillaSwap(VanillaSwap.Type.Receiver,
                        vars.nominal, fixedScheduleU, strike, new Actual365Fixed(),
                        floatSchedule, vars.index, 0.0, vars.index.dayCounter());

                // Swap with fixed leg conventions: Modified Following, DayCount = 30/360
                Schedule fixedScheduleA = new Schedule(startDate, maturity,
                        new Period(vars.fixedFrequency), vars.calendar,
                        BusinessDayConvention.MODIFIED_FOLLOWING,
                        BusinessDayConvention.MODIFIED_FOLLOWING,
                        DateGenerationRule.FORWARD, true, new Date(), new Date());
                VanillaSwap swapA360 = new VanillaSwap(VanillaSwap.Type.Receiver,
                        vars.nominal, fixedScheduleA, strike,
                        new Thirty360(Thirty360.Convention.BondBasis), floatSchedule,
                        vars.index, 0.0, vars.index.dayCounter());

                // Swap with fixed leg conventions: Modified Following, DayCount = Act/365
                VanillaSwap swapA365 = new VanillaSwap(VanillaSwap.Type.Receiver,
                        vars.nominal, fixedScheduleA, strike, new Actual365Fixed(),
                        floatSchedule, vars.index, 0.0, vars.index.dayCounter());

                DiscountingSwapEngine swapEngine =
                        new DiscountingSwapEngine(vars.termStructure);

                swapU360.setPricingEngine(swapEngine);
                swapA360.setPricingEngine(swapEngine);
                swapU365.setPricingEngine(swapEngine);
                swapA365.setPricingEngine(swapEngine);

                List<CashFlow> swapFixedLegU360 = swapU360.fixedLeg();
                List<CashFlow> swapFixedLegA360 = swapA360.fixedLeg();
                List<CashFlow> swapFixedLegU365 = swapU365.fixedLeg();
                List<CashFlow> swapFixedLegA365 = swapA365.fixedLeg();

                // FlatForward curves
                Handle<YieldTermStructure> termStructureU360 = new Handle<>(
                        new FlatForward(vars.settlement, swapU360.fairRate(),
                                new Thirty360(Thirty360.Convention.BondBasis),
                                Compounding.Compounded, vars.fixedFrequency), true);
                Handle<YieldTermStructure> termStructureA360 = new Handle<>(
                        new FlatForward(vars.settlement, swapA360.fairRate(),
                                new Thirty360(Thirty360.Convention.BondBasis),
                                Compounding.Compounded, vars.fixedFrequency), true);
                Handle<YieldTermStructure> termStructureU365 = new Handle<>(
                        new FlatForward(vars.settlement, swapU365.fairRate(),
                                new Actual365Fixed(), Compounding.Compounded,
                                vars.fixedFrequency), true);
                Handle<YieldTermStructure> termStructureA365 = new Handle<>(
                        new FlatForward(vars.settlement, swapA365.fairRate(),
                                new Actual365Fixed(), Compounding.Compounded,
                                vars.fixedFrequency), true);

                // Annuity calculated by swap method fixedLegBPS().
                double annuityU360 = swapU360.fixedLegBPS() / 0.0001;
                annuityU360 = swapU360.type() == VanillaSwap.Type.Payer
                        ? -annuityU360 : annuityU360;
                double annuityA365 = swapA365.fixedLegBPS() / 0.0001;
                annuityA365 = swapA365.type() == VanillaSwap.Type.Payer
                        ? -annuityA365 : annuityA365;
                double annuityA360 = swapA360.fixedLegBPS() / 0.0001;
                annuityA360 = swapA360.type() == VanillaSwap.Type.Payer
                        ? -annuityA360 : annuityA360;
                double annuityU365 = swapU365.fixedLegBPS() / 0.0001;
                annuityU365 = swapU365.type() == VanillaSwap.Type.Payer
                        ? -annuityU365 : annuityU365;

                // Calculation of Modified Annuity (cash settlement)
                double cashannuityU360 = 0.;
                for (CashFlow cashflow : swapFixedLegU360)
                    cashannuityU360 += cashflow.amount() / strike
                            * termStructureU360.currentLink().discount(cashflow.date());
                double cashannuityU365 = 0.;
                for (CashFlow cashflow : swapFixedLegU365)
                    cashannuityU365 += cashflow.amount() / strike
                            * termStructureU365.currentLink().discount(cashflow.date());
                double cashannuityA360 = 0.;
                for (CashFlow cashflow : swapFixedLegA360)
                    cashannuityA360 += cashflow.amount() / strike
                            * termStructureA360.currentLink().discount(cashflow.date());
                double cashannuityA365 = 0.;
                for (CashFlow cashflow : swapFixedLegA365)
                    cashannuityA365 += cashflow.amount() / strike
                            * termStructureA365.currentLink().discount(cashflow.date());

                // Swaptions: underlying swap fixed leg conventions:
                // unadjusted, 30/360
                Swaption swaptionPU360 = vars.makeSwaption(swapU360, exerciseDate, 0.20);
                double valuePU360 = swaptionPU360.NPV();
                Swaption swaptionCU360 = vars.makeSwaption(swapU360, exerciseDate, 0.20,
                        Settlement.Type.Cash, Settlement.Method.ParYieldCurve);
                double valueCU360 = swaptionCU360.NPV();
                double npvRatioU360 = valueCU360 / valuePU360;
                double annuityRatioU360 = cashannuityU360 / annuityU360;

                // modified following, act/365
                Swaption swaptionPA365 = vars.makeSwaption(swapA365, exerciseDate, 0.20);
                double valuePA365 = swaptionPA365.NPV();
                Swaption swaptionCA365 = vars.makeSwaption(swapA365, exerciseDate, 0.20,
                        Settlement.Type.Cash, Settlement.Method.ParYieldCurve);
                double valueCA365 = swaptionCA365.NPV();
                double npvRatioA365 = valueCA365 / valuePA365;
                double annuityRatioA365 = cashannuityA365 / annuityA365;

                // modified following, 30/360
                Swaption swaptionPA360 = vars.makeSwaption(swapA360, exerciseDate, 0.20);
                double valuePA360 = swaptionPA360.NPV();
                Swaption swaptionCA360 = vars.makeSwaption(swapA360, exerciseDate, 0.20,
                        Settlement.Type.Cash, Settlement.Method.ParYieldCurve);
                double valueCA360 = swaptionCA360.NPV();
                double npvRatioA360 = valueCA360 / valuePA360;
                double annuityRatioA360 = cashannuityA360 / annuityA360;

                // unadjusted, act/365
                Swaption swaptionPU365 = vars.makeSwaption(swapU365, exerciseDate, 0.20);
                double valuePU365 = swaptionPU365.NPV();
                Swaption swaptionCU365 = vars.makeSwaption(swapU365, exerciseDate, 0.20,
                        Settlement.Type.Cash, Settlement.Method.ParYieldCurve);
                double valueCU365 = swaptionCU365.NPV();
                double npvRatioU365 = valueCU365 / valuePU365;
                double annuityRatioU365 = cashannuityU365 / annuityU365;

                if (Math.abs(annuityRatioU360 - npvRatioU360) > 1e-10)
                    fail(describe("The npv's ratio must be equal to annuities ratio",
                            "Swaption", exercise, "x", length,
                            "(underlying swap fixed leg Unadjusted, 30/360)",
                            "Today:", vars.today, "Settlement date:", vars.settlement,
                            "Exercise date:", exerciseDate, "Swap start date:", startDate,
                            "Swap end date:", maturity,
                            "physical delivered swaption npv:", valuePU360,
                            "annuity:", annuityU360,
                            "cash delivered swaption npv:", valueCU360,
                            "annuity:", cashannuityU360,
                            "npv ratio:", npvRatioU360,
                            "annuity ratio:", annuityRatioU360,
                            "difference:", (annuityRatioU360 - npvRatioU360)));
                if (Math.abs(annuityRatioA365 - npvRatioA365) > 1e-10)
                    fail(describe("The npv's ratio must be equal to annuities ratio",
                            "Swaption", exercise, "x", length,
                            "(underlying swap fixed leg Modified Following, act/365)",
                            "Today:", vars.today, "Settlement date:", vars.settlement,
                            "Exercise date:", exerciseDate, "Swap start date:", startDate,
                            "Swap end date:", maturity,
                            "physical delivered swaption npv:", valuePA365,
                            "annuity:", annuityA365,
                            "cash delivered swaption npv:", valueCA365,
                            "annuity:", cashannuityA365,
                            "npv ratio:", npvRatioA365,
                            "annuity ratio:", annuityRatioA365,
                            "difference:", (annuityRatioA365 - npvRatioA365)));
                if (Math.abs(annuityRatioA360 - npvRatioA360) > 1e-10)
                    fail(describe("The npv's ratio must be equal to annuities ratio",
                            "Swaption", exercise, "x", length,
                            "(underlying swap fixed leg Modified Following, 30/360)",
                            "Today:", vars.today, "Settlement date:", vars.settlement,
                            "Exercise date:", exerciseDate, "Swap start date:", startDate,
                            "Swap end date:", maturity,
                            "physical delivered swaption npv:", valuePA360,
                            "annuity:", annuityA360,
                            "cash delivered swaption npv:", valueCA360,
                            "annuity:", cashannuityA360,
                            "npv ratio:", npvRatioA360,
                            "annuity ratio:", annuityRatioA360,
                            "difference:", (annuityRatioA360 - npvRatioA360)));
                if (Math.abs(annuityRatioU365 - npvRatioU365) > 1e-10)
                    fail(describe("The npv's ratio must be equal to annuities ratio",
                            "Swaption", exercise, "x", length,
                            "(underlying swap fixed leg Unadjusted, act/365)",
                            "Today:", vars.today, "Settlement date:", vars.settlement,
                            "Exercise date:", exerciseDate, "Swap start date:", startDate,
                            "Swap end date:", maturity,
                            "physical delivered swaption npv:", valuePU365,
                            "annuity:", annuityU365,
                            "cash delivered swaption npv:", valueCU365,
                            "annuity:", cashannuityU365,
                            "npv ratio:", npvRatioU365,
                            "annuity ratio:", annuityRatioU365,
                            "difference:", (annuityRatioU365 - npvRatioU365)));
            }
        }
    }

    @Test
    public void testImpliedVolatility() {
        CommonVars vars = new CommonVars();

        int maxEvaluations = 100;
        double tolerance = 1.0e-08;

        Settlement.Type[] types = {Settlement.Type.Physical, Settlement.Type.Cash};
        Settlement.Method[] methods = {
                Settlement.Method.PhysicalOTC, Settlement.Method.ParYieldCurve};
        Swaption.PriceType[] priceTypes = {Swaption.PriceType.Spot, Swaption.PriceType.Forward};
        double[] strikes = {0.02, 0.03, 0.04, 0.05, 0.06, 0.07};
        double[] vols = {0.01, 0.05, 0.10, 0.20, 0.30, 0.70, 0.90};

        for (Period exercise : exercises) {
            for (Period length : lengths) {
                Date exerciseDate = vars.calendar.advance(vars.today, exercise);
                Date startDate = vars.calendar.advance(exerciseDate,
                        vars.settlementDays, TimeUnit.DAYS);

                for (double strike : strikes) {
                    for (Swap.Type k : type) {
                        VanillaSwap swap = new MakeVanillaSwap(length, vars.index)
                                .withFixedRate(strike)
                                .withEffectiveDate(startDate)
                                .withFixedLegTenor(new Period(1, TimeUnit.YEARS))
                                .withFixedLegDayCount(vars.fixedDayCount)
                                .withFloatingLegSpread(0.0)
                                .withType(k)
                                .makeSwap();
                        for (int h = 0; h < types.length; h++) {
                            for (Swaption.PriceType priceType : priceTypes) {
                                for (double vol : vols) {
                                    Swaption swaption = vars.makeSwaption(swap, exerciseDate, vol,
                                            types[h], methods[h],
                                            BlackSwaptionEngine.CashAnnuityModel.DiscountCurve);
                                    double value = priceType == Swaption.PriceType.Spot
                                            ? swaption.NPV()
                                            : swaption.result("forwardPrice", Double.class);
                                    double implVol = 0.0;
                                    try {
                                        implVol = swaption.impliedVolatility(value,
                                                vars.termStructure, 0.10, tolerance,
                                                maxEvaluations, 1.0e-7, 4.0,
                                                jquant.termstructures.volatility.Sarb.VolatilityType.ShiftedLognormal,
                                                0.0, priceType);
                                    } catch (RuntimeException e) {
                                        // couldn't bracket?
                                        swaption.setPricingEngine(vars.makeEngine(0.0,
                                                BlackSwaptionEngine.CashAnnuityModel.DiscountCurve));
                                        double value2 = priceType == Swaption.PriceType.Spot
                                                ? swaption.NPV()
                                                : swaption.result("forwardPrice", Double.class);
                                        if (Math.abs(value - value2) < tolerance) {
                                            // ok, just skip:
                                            continue;
                                        }
                                        fail(describe("implied vol failure:", exercise, "x", length,
                                                k, "settlement:", types[h], "strike", strike,
                                                "atm level:", swap.fairRate(), "vol:", vol,
                                                "price:", value,
                                                "type:", (priceType == Swaption.PriceType.Spot
                                                        ? "spot" : "forward"), e.getMessage()));
                                    }
                                    if (Math.abs(implVol - vol) > tolerance) {
                                        // the difference might not matter
                                        swaption.setPricingEngine(vars.makeEngine(implVol,
                                                BlackSwaptionEngine.CashAnnuityModel.DiscountCurve));
                                        double value2 = priceType == Swaption.PriceType.Spot
                                                ? swaption.NPV()
                                                : swaption.result("forwardPrice", Double.class);
                                        if (Math.abs(value - value2) > tolerance)
                                            fail(describe("implied vol failure:", exercise, "x", length,
                                                    k, "settlement:", types[h], "strike", strike,
                                                    "atm level:", swap.fairRate(), "vol:", vol,
                                                    "price:", value,
                                                    "type:", (priceType == Swaption.PriceType.Spot
                                                            ? "spot" : "forward"),
                                                    "implied vol:", implVol,
                                                    "implied price:", value2));
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    @Test
    public void testImpliedVolatilityOis() {
        CommonVars vars = new CommonVars();

        int maxEvaluations = 100;
        double tolerance = 1.0e-08;

        Settlement.Type[] types = {Settlement.Type.Physical, Settlement.Type.Cash};
        Settlement.Method[] methods = {
                Settlement.Method.PhysicalOTC, Settlement.Method.ParYieldCurve};
        Swaption.PriceType[] priceTypes = {Swaption.PriceType.Spot, Swaption.PriceType.Forward};
        double[] strikes = {0.02, 0.03, 0.04, 0.05, 0.06, 0.07};
        double[] vols = {0.01, 0.05, 0.10, 0.20, 0.30, 0.70, 0.90};

        for (Period exercise : exercises) {
            for (Period length : lengths) {
                Date exerciseDate = vars.calendar.advance(vars.today, exercise);
                Date startDate = vars.calendar.advance(exerciseDate,
                        vars.settlementDays, TimeUnit.DAYS);

                for (double strike : strikes) {
                    for (Swap.Type k : type) {
                        OvernightIndexedSwap swap = new MakeOIS(length, vars.oisIndex)
                                .withFixedRate(strike)
                                .withEffectiveDate(startDate)
                                .withPaymentFrequency(Frequency.ANNUAL)
                                .withFixedLegDayCount(vars.fixedDayCount)
                                .withType(k)
                                .makeSwap();
                        for (int h = 0; h < types.length; h++) {
                            for (Swaption.PriceType priceType : priceTypes) {
                                for (double vol : vols) {
                                    Swaption swaption = vars.makeOISwaption(swap, exerciseDate, vol,
                                            types[h], methods[h],
                                            BlackSwaptionEngine.CashAnnuityModel.DiscountCurve);
                                    double value = priceType == Swaption.PriceType.Spot
                                            ? swaption.NPV()
                                            : swaption.result("forwardPrice", Double.class);
                                    double implVol = 0.0;
                                    try {
                                        implVol = swaption.impliedVolatility(value,
                                                vars.termStructure, 0.10, tolerance,
                                                maxEvaluations, 1.0e-7, 4.0,
                                                jquant.termstructures.volatility.Sarb.VolatilityType.ShiftedLognormal,
                                                0.0, priceType);
                                    } catch (RuntimeException e) {
                                        // couldn't bracket?
                                        swaption.setPricingEngine(vars.makeEngine(0.0,
                                                BlackSwaptionEngine.CashAnnuityModel.DiscountCurve));
                                        double value2 = priceType == Swaption.PriceType.Spot
                                                ? swaption.NPV()
                                                : swaption.result("forwardPrice", Double.class);
                                        if (Math.abs(value - value2) < tolerance) {
                                            // ok, just skip:
                                            continue;
                                        }
                                        fail(describe("implied vol failure:", exercise, "x", length,
                                                k, "settlement:", types[h], "strike", strike,
                                                "atm level:", swap.fairRate(), "vol:", vol,
                                                "price:", value,
                                                "type:", (priceType == Swaption.PriceType.Spot
                                                        ? "spot" : "forward"), e.getMessage()));
                                    }
                                    if (Math.abs(implVol - vol) > tolerance) {
                                        // the difference might not matter
                                        swaption.setPricingEngine(vars.makeEngine(implVol,
                                                BlackSwaptionEngine.CashAnnuityModel.DiscountCurve));
                                        double value2 = priceType == Swaption.PriceType.Spot
                                                ? swaption.NPV()
                                                : swaption.result("forwardPrice", Double.class);
                                        if (Math.abs(value - value2) > tolerance)
                                            fail(describe("implied vol failure:", exercise, "x", length,
                                                    k, "settlement:", types[h], "strike", strike,
                                                    "atm level:", swap.fairRate(), "vol:", vol,
                                                    "price:", value,
                                                    "type:", (priceType == Swaption.PriceType.Spot
                                                            ? "spot" : "forward"),
                                                    "implied vol:", implVol,
                                                    "implied price:", value2));
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private enum SwaptionEngineKind {Black, Bachelier}

    private void checkSwaptionDelta(SwaptionEngineKind kind, boolean useBachelierVol) {
        CommonVars vars = new CommonVars();
        Date today = vars.today;
        Calendar calendar = vars.calendar;

        double bump = 1.e-4;
        double epsilon = 1.e-10;

        RelinkableHandle<YieldTermStructure> projectionCurveHandle =
                new RelinkableHandle<>();

        double projectionRate = 0.01;
        // Java adapter: the official test relinks a RelinkableHandle<Quote>,
        // but jquant's FlatForward registers with the current quote rather
        // than with the link, so a relink is not observed. Mutating the quote
        // is the equivalent bump on this adapter.
        SimpleQuote projectionQuote = new SimpleQuote(projectionRate);
        Handle<Quote> projectionQuoteHandle = new Handle<>(projectionQuote, true);

        YieldTermStructure projectionCurve = new FlatForward(today,
                projectionQuoteHandle, new Actual365Fixed());
        projectionCurveHandle.linkTo(projectionCurve, true);

        Handle<YieldTermStructure> discountHandle = new Handle<>(
                new FlatForward(today,
                        new Handle<>(new SimpleQuote(0.0085), true),
                        new Actual365Fixed()), true);
        DiscountingSwapEngine swapEngine = new DiscountingSwapEngine(discountHandle);

        IborIndex idx = new Euribor6M(projectionCurveHandle);

        Settlement.Type[] types = {Settlement.Type.Physical, Settlement.Type.Cash};
        Settlement.Method[] methods = {
                Settlement.Method.PhysicalOTC, Settlement.Method.CollateralizedCashPrice};

        double[] strikes = {0.03, 0.04, 0.05, 0.06, 0.07};
        double[] vols = {0.0, 0.10, 0.20, 0.30, 0.70, 0.90};

        for (double vol : vols) {
            for (Period exercise : exercises) {
                for (Period length : lengths) {
                    for (double strike : strikes) {
                        for (int h = 0; h < type.length; h++) {
                            double volatility = useBachelierVol ? vol / 100.0 : vol;
                            Handle<Quote> hq = new Handle<>(new SimpleQuote(volatility), true);
                            jquant.PricingEngine swaptionEngine =
                                    kind == SwaptionEngineKind.Black
                                            ? new BlackSwaptionEngine(discountHandle, hq,
                                            new Actual365Fixed(), 0.0,
                                            BlackSwaptionEngine.CashAnnuityModel.DiscountCurve)
                                            : new BachelierSwaptionEngine(discountHandle, hq,
                                            new Actual365Fixed(),
                                            BachelierSwaptionEngine.CashAnnuityModel.DiscountCurve);

                            Date exerciseDate = calendar.advance(today, exercise);
                            Date startDate = calendar.advance(exerciseDate, 2, TimeUnit.DAYS);
                            projectionQuote.setValue(projectionRate);

                            VanillaSwap underlying =
                                    new MakeVanillaSwap(length, idx)
                                            .withFixedRate(strike)
                                            .withEffectiveDate(startDate)
                                            .withFixedLegTenor(new Period(1, TimeUnit.YEARS))
                                            .withFixedLegDayCount(
                                                    new Thirty360(Thirty360.Convention.BondBasis))
                                            .withFloatingLegSpread(0.0)
                                            .withType(type[h])
                                            .makeSwap();
                            underlying.setPricingEngine(swapEngine);

                            double fairRate = underlying.fairRate();

                            Swaption swaption = new Swaption(underlying,
                                    new jquant.EuropeanExercise(exerciseDate),
                                    types[h], methods[h]);
                            swaption.setPricingEngine(swaptionEngine);

                            double value = swaption.NPV();
                            double delta = swaption.result("delta", Double.class) * bump;

                            projectionQuote.setValue(projectionRate + bump);

                            double bumpedFairRate = underlying.fairRate();
                            double bumpedValue = swaption.NPV();
                            double bumpedDelta = swaption.result("delta", Double.class) * bump;

                            double deltaBump = bumpedFairRate - fairRate;
                            double approxDelta = (bumpedValue - value) / deltaBump * bump;

                            double lowerBound = Math.min(delta, bumpedDelta) - epsilon;
                            double upperBound = Math.max(delta, bumpedDelta) + epsilon;

                            /*
                             * Based on the Mean Value Theorem, the below
                             * inequality should hold for any function that is
                             * monotonic in the area of the bump.
                             */
                            boolean checkIsCorrect =
                                    (lowerBound < approxDelta) && (approxDelta < upperBound);

                            if (!checkIsCorrect)
                                fail(describe("failed to compute swaption delta:",
                                        "option tenor:", exerciseDate, "volatility:", volatility,
                                        "option type:", swaption.type(), "swap tenor:", length,
                                        "strike:", strike, "settlement:", types[h],
                                        "method:", methods[h],
                                        "nominal:", swaption.underlying().nominal(),
                                        "npv:", value, "calculated delta:", delta,
                                        "expected delta:", approxDelta));
                        }
                    }
                }
            }
        }
    }

    @Test
    public void testSwaptionDeltaInBlackModel() {
        checkSwaptionDelta(SwaptionEngineKind.Black, false);
    }

    @Test
    public void testSwaptionDeltaInBachelierModel() {
        checkSwaptionDelta(SwaptionEngineKind.Bachelier, true);
    }

    @Test
    public void testMakeSwaptionWithExerciseCalendar() {
        Date today = new Date(9, Month.OCTOBER, 2015);
        Settings.instance.evaluationDate().equal(today);
        RelinkableHandle<YieldTermStructure> termStructure = new RelinkableHandle<>();
        termStructure.linkTo(flatRate(today, 0.05, new Actual365Fixed()), true);

        EuriborSwapIsdaFixA swapIndex = new EuriborSwapIsdaFixA(
                new Period(5, TimeUnit.YEARS), termStructure);
        Calendar targetCalendar = swapIndex.fixingCalendar();
        Calendar usCalendar = new UnitedStates(UnitedStates.Market.Settlement);

        // Default uses swap index's fixing calendar (TARGET)
        Swaption defaultSwaption = new MakeSwaption(swapIndex,
                new Period(1, TimeUnit.YEARS), 0.05).makeSwaption();
        Date defaultExercise = defaultSwaption.exercise().dates().get(0);

        Date expected = targetCalendar.advance(
                targetCalendar.adjust(today, BusinessDayConvention.FOLLOWING),
                new Period(1, TimeUnit.YEARS), BusinessDayConvention.MODIFIED_FOLLOWING);
        assertTrue(TimeUtils.equals(expected, defaultExercise), "default exercise date");

        // With custom calendar, exercise date differs
        Swaption customSwaption = new MakeSwaption(swapIndex,
                new Period(1, TimeUnit.YEARS), 0.05)
                .withExerciseCalendar(usCalendar)
                .makeSwaption();
        Date customExercise = customSwaption.exercise().dates().get(0);

        Date expectedCustom = usCalendar.advance(
                usCalendar.adjust(today, BusinessDayConvention.FOLLOWING),
                new Period(1, TimeUnit.YEARS), BusinessDayConvention.MODIFIED_FOLLOWING);
        assertTrue(TimeUtils.equals(expectedCustom, customExercise), "custom exercise date");
        assertFalse(TimeUtils.equals(defaultExercise, customExercise),
                "custom and default exercise dates should differ");

        // Explicit withExerciseDate takes precedence over calendar
        Date explicitDate = targetCalendar.advance(today, new Period(6, TimeUnit.MONTHS));
        Date fixingDate = targetCalendar.advance(today, new Period(1, TimeUnit.YEARS));
        Swaption explicitSwaption = new MakeSwaption(swapIndex, fixingDate, 0.05)
                .withExerciseCalendar(usCalendar)
                .withExerciseDate(explicitDate)
                .makeSwaption();
        assertTrue(TimeUtils.equals(explicitDate, explicitSwaption.exercise().dates().get(0)),
                "explicit exercise date");
    }

    @Test
    public void testMakeSwaptionFixingDateNominal() {
        Date today = new Date(9, Month.OCTOBER, 2015);
        Settings.instance.evaluationDate().equal(today);
        RelinkableHandle<YieldTermStructure> termStructure = new RelinkableHandle<>();
        termStructure.linkTo(flatRate(today, 0.05, new Actual365Fixed()), true);

        EuriborSwapIsdaFixA swapIndex = new EuriborSwapIsdaFixA(
                new Period(5, TimeUnit.YEARS), termStructure);
        Date fixingDate = swapIndex.fixingCalendar().advance(today,
                new Period(1, TimeUnit.YEARS));

        // The fixingDate-based constructor should default the nominal to 1.0,
        // just like the optionTenor-based constructor does.
        Swaption swaption = new MakeSwaption(swapIndex, fixingDate, 0.05).makeSwaption();
        assertEquals(1.0, swaption.underlying().nominal(), 0.0, "default nominal");

        // withNominal should still override the default.
        Swaption customNominalSwaption = new MakeSwaption(swapIndex, fixingDate, 0.05)
                .withNominal(1234.0)
                .makeSwaption();
        assertEquals(1234.0, customNominalSwaption.underlying().nominal(), 0.0,
                "custom nominal");
    }
}
