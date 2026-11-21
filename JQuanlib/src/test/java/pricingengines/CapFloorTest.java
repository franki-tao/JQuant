package pricingengines;

import jquant.Compounding;
import jquant.Handle;
import jquant.Instrument;
import jquant.PricingEngine;
import jquant.Quote;
import jquant.RelinkableHandle;
import jquant.SavedSettings;
import jquant.Settings;
import jquant.cashflows.FloatingRateCoupon;
import jquant.cashflows.IborCoupon;
import jquant.indexes.Euribor6M;
import jquant.indexes.IborIndex;
import jquant.indexes.IndexManager;
import jquant.instruments.CapFloor;
import jquant.instruments.Swap;
import jquant.instruments.VanillaSwap;
import jquant.pricingengines.DiscountingSwapEngine;
import jquant.pricingengines.capfloor.BachelierCapFloorEngine;
import jquant.pricingengines.capfloor.BlackCapFloorEngine;
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
import jquant.time.daycounters.Actual360;
import jquant.time.daycounters.Actual365Fixed;
import jquant.time.daycounters.ActualActual;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.fail;

/**
 * Cap/floor pricing from QuantLib {@code test-suite/capfloor.cpp}.
 *
 * <p>All 11 official cases are ported.
 *
 * <p><b>Date adaptation.</b> Official {@code capfloor.cpp} leaves the
 * non-cached cases on {@code Settings::evaluationDate() =
 * Date::todaysDate()} with no business-day adjustment. On weekends the
 * first T+2 fixing is the previous Friday, so official QuantLib throws
 * "Missing Euribor6M Actual/360 fixing". The seven unpinned cases are
 * therefore pinned here to TARGET business day 20 November 2026, which
 * makes the suite repeatable across weekends. This is a user-approved
 * date adaptation, not a verbatim date port. No historic fixings are
 * injected. The four officially pinned cached/delta cases keep their
 * verbatim 14/18 March 2002 dates.
 */
public class CapFloorTest {

    /**
     * TARGET business day used to pin the seven officially unpinned cases
     * (official uses the run-day {@code todaysDate} without adjustment).
     */
    private static final Date UNPINNED_TEST_DATE =
            new Date(20, Month.NOVEMBER, 2026);

    private static void pinUnpinnedTestDate() {
        Settings.instance.evaluationDate().equal(UNPINNED_TEST_DATE);
    }

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

    private static class CommonVars {
        Date settlement;
        final List<Double> nominals = new ArrayList<>(List.of(100.0));
        BusinessDayConvention convention;
        Frequency frequency;
        IborIndex index;
        Calendar calendar;
        int fixingDays;
        final RelinkableHandle<YieldTermStructure> termStructure =
                new RelinkableHandle<>();

        CommonVars() {
            frequency = Frequency.SEMIANNUAL;
            index = new Euribor6M(termStructure);
            calendar = index.fixingCalendar();
            convention = BusinessDayConvention.MODIFIED_FOLLOWING;
            Date today = Settings.instance.evaluationDate().Date();
            int settlementDays = 2;
            fixingDays = 2;
            settlement = calendar.advance(today, settlementDays, TimeUnit.DAYS);
            termStructure.linkTo(flatRate(settlement, 0.05,
                    new ActualActual(ActualActual.Convention.ISDA)), true);
        }

        List<jquant.CashFlow> makeLeg(Date startDate, int length) {
            Date endDate = calendar.advance(startDate, length, TimeUnit.YEARS, convention);
            Schedule schedule = new Schedule(startDate, endDate,
                    new Period(frequency), calendar, convention, convention,
                    DateGenerationRule.FORWARD, false, new Date(), new Date());
            return new jquant.cashflows.IborLeg(schedule, index)
                    .withNotionals(nominals)
                    .withPaymentDayCounter(index.dayCounter())
                    .withPaymentAdjustment(convention)
                    .withFixingDays(fixingDays)
                    .leg();
        }

        PricingEngine makeEngine(double volatility) {
            Handle<Quote> vol = new Handle<>(new SimpleQuote(volatility), true);
            return new BlackCapFloorEngine(termStructure, vol);
        }

        PricingEngine makeBachelierEngine(double volatility) {
            Handle<Quote> vol = new Handle<>(new SimpleQuote(volatility), true);
            return new BachelierCapFloorEngine(termStructure, vol);
        }

        CapFloor makeCapFloor(CapFloor.Type type, List<jquant.CashFlow> leg,
                              double strike, double volatility,
                              boolean isLogNormal) {
            CapFloor result;
            switch (type) {
                case Cap:
                    result = new CapFloor.Cap(leg, List.of(strike));
                    break;
                case Floor:
                    result = new CapFloor.Floor(leg, List.of(strike));
                    break;
                default:
                    throw new IllegalArgumentException("unknown cap/floor type");
            }
            if (isLogNormal) {
                result.setPricingEngine(makeEngine(volatility));
            } else {
                result.setPricingEngine(makeBachelierEngine(volatility));
            }
            return result;
        }
    }

    private static FlatForward flatRate(Date settlement, double rate, DayCounter dc) {
        return new FlatForward(settlement, rate, dc,
                Compounding.Continuous, Frequency.ANNUAL);
    }

    private static boolean checkAbsError(double x1, double x2, double tolerance) {
        return Math.abs(x1 - x2) < tolerance;
    }

    private static String typeToString(CapFloor.Type type) {
        switch (type) {
            case Cap:
                return "cap";
            case Floor:
                return "floor";
            case Collar:
                return "collar";
            default:
                throw new IllegalArgumentException("unknown cap/floor type");
        }
    }

    @SuppressWarnings("unchecked")
    private static List<Double> doubleVector(Instrument instrument, String tag) {
        return (List<Double>) instrument.result(tag, List.class);
    }

    @Test
    public void testVega() {
        pinUnpinnedTestDate();
        CommonVars vars = new CommonVars();

        int[] lengths = {1, 2, 3, 4, 5, 6, 7, 10, 15, 20, 30};
        double[] vols = {0.01, 0.05, 0.10, 0.15, 0.20};
        double[] strikes = {0.01, 0.02, 0.03, 0.04, 0.05, 0.06, 0.07, 0.08, 0.09};
        CapFloor.Type[] types = {CapFloor.Type.Cap, CapFloor.Type.Floor};

        Date startDate = vars.termStructure.currentLink().referenceDate();
        final double shift = 1e-8;
        final double tolerance = 0.005;

        for (int length : lengths) {
            for (double vol : vols) {
                for (double strike : strikes) {
                    for (CapFloor.Type type : types) {
                        List<jquant.CashFlow> leg = vars.makeLeg(startDate, length);
                        CapFloor capFloor = vars.makeCapFloor(type, leg, strike, vol, true);
                        CapFloor shiftedCapFloor2 =
                                vars.makeCapFloor(type, leg, strike, vol + shift, true);
                        CapFloor shiftedCapFloor1 =
                                vars.makeCapFloor(type, leg, strike, vol - shift, true);
                        double value1 = shiftedCapFloor1.NPV();
                        double value2 = shiftedCapFloor2.NPV();
                        double numericalVega = (value2 - value1) / (2 * shift);
                        if (numericalVega > 1.0e-4) {
                            double analyticalVega = capFloor.result("vega", Double.class);
                            double discrepancy =
                                    Math.abs(numericalVega - analyticalVega);
                            discrepancy /= numericalVega;
                            if (discrepancy > tolerance) {
                                fail("failed to compute cap/floor vega:"
                                        + "\n   lengths:     " + length
                                        + "\n   strike:      " + strike
                                        + "\n   calculated:  " + analyticalVega
                                        + "\n   expected:    " + numericalVega
                                        + "\n   discrepancy: " + discrepancy
                                        + "\n   tolerance:   " + tolerance);
                            }
                        }
                    }
                }
            }
        }
    }

    @Test
    public void testStrikeDependency() {
        pinUnpinnedTestDate();
        CommonVars vars = new CommonVars();

        int[] lengths = {1, 2, 3, 5, 7, 10, 15, 20};
        double[] vols = {0.01, 0.05, 0.10, 0.15, 0.20};
        double[] strikes = {0.03, 0.04, 0.05, 0.06, 0.07};

        Date startDate = vars.termStructure.currentLink().referenceDate();

        for (int length : lengths) {
            for (double vol : vols) {
                // store the results for different strikes...
                List<Double> capValues = new ArrayList<>();
                List<Double> floorValues = new ArrayList<>();
                for (double strike : strikes) {
                    List<jquant.CashFlow> leg = vars.makeLeg(startDate, length);
                    CapFloor cap =
                            vars.makeCapFloor(CapFloor.Type.Cap, leg, strike, vol, true);
                    capValues.add(cap.NPV());
                    CapFloor floor =
                            vars.makeCapFloor(CapFloor.Type.Floor, leg, strike, vol, true);
                    floorValues.add(floor.NPV());
                }
                // and check that they go the right way
                for (int n = 0; n + 1 < capValues.size(); n++) {
                    if (capValues.get(n) < capValues.get(n + 1)) {
                        fail("NPV is increasing with the strike in a cap: \n"
                                + "    length:     " + length + " years\n"
                                + "    volatility: " + vol + "\n"
                                + "    value:      " + capValues.get(n)
                                + " at strike: " + strikes[n] + "\n"
                                + "    value:      " + capValues.get(n + 1)
                                + " at strike: " + strikes[n + 1]);
                    }
                }
                // same for floors
                for (int n = 0; n + 1 < floorValues.size(); n++) {
                    if (floorValues.get(n) > floorValues.get(n + 1)) {
                        fail("NPV is decreasing with the strike in a floor: \n"
                                + "    length:     " + length + " years\n"
                                + "    volatility: " + vol + "\n"
                                + "    value:      " + floorValues.get(n)
                                + " at strike: " + strikes[n] + "\n"
                                + "    value:      " + floorValues.get(n + 1)
                                + " at strike: " + strikes[n + 1]);
                    }
                }
            }
        }
    }

    @Test
    public void testConsistency() {
        pinUnpinnedTestDate();
        CommonVars vars = new CommonVars();

        int[] lengths = {1, 2, 3, 5, 7, 10, 15, 20};
        double[] capRates = {0.03, 0.04, 0.05, 0.06, 0.07};
        double[] floorRates = {0.03, 0.04, 0.05, 0.06, 0.07};
        double[] vols = {0.01, 0.05, 0.10, 0.15, 0.20};

        Date startDate = vars.termStructure.currentLink().referenceDate();

        for (int length : lengths) {
            for (double capRate : capRates) {
                for (double floorRate : floorRates) {
                    for (double vol : vols) {

                        List<jquant.CashFlow> leg = vars.makeLeg(startDate, length);
                        CapFloor cap = vars.makeCapFloor(
                                CapFloor.Type.Cap, leg, capRate, vol, true);
                        CapFloor floor = vars.makeCapFloor(
                                CapFloor.Type.Floor, leg, floorRate, vol, true);
                        CapFloor.Collar collar = new CapFloor.Collar(
                                leg, List.of(capRate), List.of(floorRate));
                        collar.setPricingEngine(vars.makeEngine(vol));

                        if (Math.abs((cap.NPV() - floor.NPV()) - collar.NPV()) > 1e-10) {
                            // test re-composition by optionlets, N.B. two per year
                            double capletsNPV = 0.0;
                            List<CapFloor> caplets = new ArrayList<>();
                            for (int m = 0; m < length * 2; m++) {
                                caplets.add(cap.optionlet(m));
                                caplets.get(m).setPricingEngine(vars.makeEngine(vol));
                                capletsNPV += caplets.get(m).NPV();
                            }

                            if (Math.abs(cap.NPV() - capletsNPV) > 1e-10) {
                                fail("sum of caplet NPVs does not equal cap NPV:\n"
                                        + "    length:       " + length + " years\n"
                                        + "    volatility:   " + vol + "\n"
                                        + "    cap value:    " + cap.NPV()
                                        + " at strike: " + capRate + "\n"
                                        + "    sum of caplets value:  " + capletsNPV
                                        + " at strike (first): "
                                        + caplets.get(0).capRates().get(0) + "\n");
                            }

                            double floorletsNPV = 0.0;
                            List<CapFloor> floorlets = new ArrayList<>();
                            for (int m = 0; m < length * 2; m++) {
                                floorlets.add(floor.optionlet(m));
                                floorlets.get(m).setPricingEngine(vars.makeEngine(vol));
                                floorletsNPV += floorlets.get(m).NPV();
                            }

                            if (Math.abs(floor.NPV() - floorletsNPV) > 1e-10) {
                                fail("sum of floorlet NPVs does not equal floor NPV:\n"
                                        + "    length:       " + length + " years\n"
                                        + "    volatility:   " + vol + "\n"
                                        + "    cap value:    " + floor.NPV()
                                        + " at strike: " + floorRate + "\n"
                                        + "    sum of floorlets value:  " + floorletsNPV
                                        + " at strike (first): "
                                        + floorlets.get(0).floorRates().get(0) + "\n");
                            }

                            double collarletsNPV = 0.0;
                            List<CapFloor> collarlets = new ArrayList<>();
                            for (int m = 0; m < length * 2; m++) {
                                collarlets.add(collar.optionlet(m));
                                collarlets.get(m).setPricingEngine(vars.makeEngine(vol));
                                collarletsNPV += collarlets.get(m).NPV();
                            }

                            if (Math.abs(collar.NPV() - collarletsNPV) > 1e-10) {
                                fail("sum of collarlet NPVs does not equal floor NPV:\n"
                                        + "    length:       " + length + " years\n"
                                        + "    volatility:   " + vol + "\n"
                                        + "    cap value:    " + collar.NPV()
                                        + " at strike floor: " + floorRate
                                        + " at strike cap: " + capRate + "\n"
                                        + "    sum of collarlets value:  " + collarletsNPV
                                        + " at strike floor (first): "
                                        + collarlets.get(0).floorRates().get(0)
                                        + " at strike cap (first): "
                                        + collarlets.get(0).capRates().get(0) + "\n");
                            }

                            fail("inconsistency between cap, floor and collar:\n"
                                    + "    length:       " + length + " years\n"
                                    + "    volatility:   " + vol + "\n"
                                    + "    cap value:    " + cap.NPV()
                                    + " at strike: " + capRate + "\n"
                                    + "    floor value:  " + floor.NPV()
                                    + " at strike: " + floorRate + "\n"
                                    + "    collar value: " + collar.NPV());
                        }
                    }
                }
            }
        }
    }

    @Test
    public void testParity() {
        pinUnpinnedTestDate();
        CommonVars vars = new CommonVars();

        int[] lengths = {1, 2, 3, 5, 7, 10, 15, 20};
        double[] strikes = {0.0, 0.03, 0.04, 0.05, 0.06, 0.07};
        double[] vols = {0.01, 0.05, 0.10, 0.15, 0.20};

        Date startDate = vars.termStructure.currentLink().referenceDate();

        for (int length : lengths) {
            for (double strike : strikes) {
                for (double vol : vols) {

                    List<jquant.CashFlow> leg = vars.makeLeg(startDate, length);
                    CapFloor cap = vars.makeCapFloor(
                            CapFloor.Type.Cap, leg, strike, vol, true);
                    CapFloor floor = vars.makeCapFloor(
                            CapFloor.Type.Floor, leg, strike, vol, true);
                    Date maturity = vars.calendar.advance(
                            startDate, length, TimeUnit.YEARS, vars.convention);
                    Schedule schedule = new Schedule(startDate, maturity,
                            new Period(vars.frequency), vars.calendar,
                            vars.convention, vars.convention,
                            DateGenerationRule.FORWARD, false,
                            new Date(), new Date());
                    VanillaSwap swap = new VanillaSwap(VanillaSwap.Type.Payer,
                            vars.nominals.get(0), schedule, strike,
                            vars.index.dayCounter(), schedule, vars.index, 0.0,
                            vars.index.dayCounter());
                    swap.setPricingEngine(new DiscountingSwapEngine(vars.termStructure));
                    if (Math.abs((cap.NPV() - floor.NPV()) - swap.NPV()) > 1.0e-10) {
                        fail("put/call parity violated:\n"
                                + "    length:      " + length + " years\n"
                                + "    volatility:  " + vol + "\n"
                                + "    strike:      " + strike + "\n"
                                + "    cap value:   " + cap.NPV() + "\n"
                                + "    floor value: " + floor.NPV() + "\n"
                                + "    swap value:  " + swap.NPV());
                    }
                }
            }
        }
    }

    @Test
    public void testATMRate() {
        pinUnpinnedTestDate();
        CommonVars vars = new CommonVars();

        int[] lengths = {1, 2, 3, 5, 7, 10, 15, 20};
        double[] strikes = {0.0, 0.03, 0.04, 0.05, 0.06, 0.07};
        double[] vols = {0.01, 0.05, 0.10, 0.15, 0.20};

        Date startDate = vars.termStructure.currentLink().referenceDate();

        for (int length : lengths) {
            List<jquant.CashFlow> leg = vars.makeLeg(startDate, length);
            Date maturity = vars.calendar.advance(
                    startDate, length, TimeUnit.YEARS, vars.convention);
            Schedule schedule = new Schedule(startDate, maturity,
                    new Period(vars.frequency), vars.calendar,
                    vars.convention, vars.convention,
                    DateGenerationRule.FORWARD, false,
                    new Date(), new Date());

            for (double strike : strikes) {
                for (double vol : vols) {
                    CapFloor cap = vars.makeCapFloor(
                            CapFloor.Type.Cap, leg, strike, vol, true);
                    CapFloor floor = vars.makeCapFloor(
                            CapFloor.Type.Floor, leg, strike, vol, true);
                    double capATMRate = cap.atmRate(vars.termStructure.currentLink());
                    double floorATMRate = floor.atmRate(vars.termStructure.currentLink());
                    if (!checkAbsError(floorATMRate, capATMRate, 1.0e-10)) {
                        fail("Cap ATM Rate and floor ATM Rate should be equal :\n"
                                + "   length:        " + length + " years\n"
                                + "   volatility:    " + vol + "\n"
                                + "   strike:        " + strike + "\n"
                                + "   cap ATM rate:  " + capATMRate + "\n"
                                + "   floor ATM rate:" + floorATMRate + "\n");
                    }
                    VanillaSwap swap = new VanillaSwap(VanillaSwap.Type.Payer,
                            vars.nominals.get(0), schedule, floorATMRate,
                            vars.index.dayCounter(), schedule, vars.index, 0.0,
                            vars.index.dayCounter());
                    swap.setPricingEngine(new DiscountingSwapEngine(vars.termStructure));
                    double swapNPV = swap.NPV();
                    if (!checkAbsError(swapNPV, 0, 1.0e-10)) {
                        fail("the NPV of a Swap struck at ATM rate "
                                + "should be equal to 0:\n"
                                + "   length:        " + length + " years\n"
                                + "   volatility:    " + vol + "\n"
                                + "   ATM rate:      " + floorATMRate + "\n"
                                + "   swap NPV:      " + swapNPV);
                    }
                }
            }
        }
    }

    @Test
    public void testImpliedVolatility() {
        pinUnpinnedTestDate();
        CommonVars vars = new CommonVars();

        int maxEvaluations = 100;
        double tolerance = 1.0e-8;

        CapFloor.Type[] types = {CapFloor.Type.Cap, CapFloor.Type.Floor};
        double[] strikes = {0.02, 0.03, 0.04};
        int[] lengths = {1, 5, 10};

        // test data
        double[] rRates = {0.02, 0.03, 0.04, 0.05, 0.06, 0.07};
        double[] vols = {0.01, 0.05, 0.10, 0.20, 0.30, 0.70, 0.90};

        for (int length : lengths) {
            List<jquant.CashFlow> leg = vars.makeLeg(vars.settlement, length);

            for (CapFloor.Type type : types) {
                for (double strike : strikes) {

                    CapFloor capfloor =
                            vars.makeCapFloor(type, leg, strike, 0.0, true);

                    for (double r : rRates) {
                        for (double v : vols) {

                            vars.termStructure.linkTo(flatRate(
                                    vars.settlement, r, new Actual360()), true);
                            capfloor.setPricingEngine(vars.makeEngine(v));

                            double value = capfloor.NPV();
                            double implVol = 0.0;
                            try {
                                implVol = capfloor.impliedVolatility(value,
                                        vars.termStructure,
                                        0.10,
                                        tolerance,
                                        maxEvaluations,
                                        10.0e-7, 4.0,
                                        jquant.termstructures.volatility.Sarb.VolatilityType.ShiftedLognormal,
                                        0.0);
                            } catch (RuntimeException e) {
                                // couldn't bracket?
                                capfloor.setPricingEngine(vars.makeEngine(0.0));
                                double value2 = capfloor.NPV();
                                if (Math.abs(value - value2) < tolerance) {
                                    // ok, just skip:
                                    continue;
                                }
                                // otherwise, report error
                                fail("implied vol failure: "
                                        + typeToString(type) + "\n  strike:     "
                                        + strike + "\n  risk-free:  " + r
                                        + "\n  length:     " + length + "Y"
                                        + "\n  volatility: " + v
                                        + "\n  price:      " + value + "\n"
                                        + e.getMessage());
                            }
                            if (Math.abs(implVol - v) > tolerance) {
                                // the difference might not matter
                                capfloor.setPricingEngine(vars.makeEngine(implVol));
                                double value2 = capfloor.NPV();
                                if (Math.abs(value - value2) > tolerance) {
                                    fail("implied vol failure: "
                                            + typeToString(type)
                                            + "\n  strike:        " + strike
                                            + "\n  risk-free:     " + r
                                            + "\n  length:        " + length + "Y"
                                            + "\n  volatility:    " + v
                                            + "\n  price:         " + value
                                            + "\n  implied vol:   " + implVol
                                            + "\n  implied price: " + value2);
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    @Test
    public void testCachedValue() {
        CommonVars vars = new CommonVars();

        Date cachedToday = new Date(14, Month.MARCH, 2002);
        Date cachedSettlement = new Date(18, Month.MARCH, 2002);
        Settings.instance.evaluationDate().equal(cachedToday);
        vars.termStructure.linkTo(flatRate(cachedSettlement, 0.05, new Actual360()), true);
        Date startDate = vars.termStructure.currentLink().referenceDate();
        List<jquant.CashFlow> leg = vars.makeLeg(startDate, 20);
        CapFloor cap = vars.makeCapFloor(CapFloor.Type.Cap, leg, 0.07, 0.20, true);
        CapFloor floor = vars.makeCapFloor(CapFloor.Type.Floor, leg, 0.03, 0.20, true);

        double cachedCapNPV, cachedFloorNPV;
        if (!IborCoupon.Settings.instance().usingAtParCoupons()) {
            // index fixing price
            cachedCapNPV = 6.87630307745;
            cachedFloorNPV = 2.65796764715;
        } else {
            // par coupon price
            cachedCapNPV = 6.87570026732;
            cachedFloorNPV = 2.65812927959;
        }

        // test Black cap price against cached value
        if (Math.abs(cap.NPV() - cachedCapNPV) > 1.0e-11) {
            fail("failed to reproduce cached cap value:\n"
                    + "    calculated: " + cap.NPV() + "\n"
                    + "    expected:   " + cachedCapNPV);
        }
        // test Black floor price against cached value
        if (Math.abs(floor.NPV() - cachedFloorNPV) > 1.0e-11) {
            fail("failed to reproduce cached floor value:\n"
                    + "    calculated: " + floor.NPV() + "\n"
                    + "    expected:   " + cachedFloorNPV);
        }
    }

    @Test
    public void testCachedValueFromOptionLets() {
        CommonVars vars = new CommonVars();

        Date cachedToday = new Date(14, Month.MARCH, 2002);
        Date cachedSettlement = new Date(18, Month.MARCH, 2002);
        Settings.instance.evaluationDate().equal(cachedToday);
        YieldTermStructure baseCurve = flatRate(cachedSettlement, 0.05, new Actual360());
        vars.termStructure.linkTo(baseCurve, true);
        Date startDate = vars.termStructure.currentLink().referenceDate();
        List<jquant.CashFlow> leg = vars.makeLeg(startDate, 20);

        CapFloor cap = vars.makeCapFloor(CapFloor.Type.Cap, leg, 0.07, 0.20, true);
        CapFloor floor = vars.makeCapFloor(CapFloor.Type.Floor, leg, 0.03, 0.20, true);
        double calculatedCapletsNPV = 0.0;
        double calculatedFloorletsNPV = 0.0;

        double cachedCapNPV, cachedFloorNPV;
        if (IborCoupon.Settings.instance().usingAtParCoupons()) {
            cachedCapNPV = 6.87570026732;
            cachedFloorNPV = 2.65812927959;
        } else {
            cachedCapNPV = 6.87630307745;
            cachedFloorNPV = 2.65796764715;
        }

        // test Black floor price against cached value
        List<Double> capletPrices = doubleVector(cap, "optionletsPrice");
        List<Double> floorletPrices = doubleVector(floor, "optionletsPrice");

        if (capletPrices.size() != 40) {
            fail("failed to produce prices for all caplets:\n"
                    + "    calculated: " + capletPrices.size() + " caplet prices\n"
                    + "    expected:   40");
        }

        for (double capletPrice : capletPrices) {
            calculatedCapletsNPV += capletPrice;
        }

        for (double floorletPrice : floorletPrices) {
            calculatedFloorletsNPV += floorletPrice;
        }

        if (Math.abs(calculatedCapletsNPV - cachedCapNPV) > 1.0e-11) {
            fail("failed to reproduce cached cap value from its caplets' values:\n"
                    + "    calculated: " + calculatedCapletsNPV + "\n"
                    + "    expected:   " + cachedCapNPV);
        }
        // test Black floor price against cached value
        if (Math.abs(calculatedFloorletsNPV - cachedFloorNPV) > 1.0e-11) {
            fail("failed to reproduce cached floor value from its floorlets' values:\n"
                    + "    calculated: " + calculatedFloorletsNPV + "\n"
                    + "    expected:   " + cachedFloorNPV);
        }
    }

    @Test
    public void testOptionLetsDelta() {
        CommonVars vars = new CommonVars();

        Date cachedToday = new Date(14, Month.MARCH, 2002);
        Date cachedSettlement = new Date(18, Month.MARCH, 2002);
        Settings.instance.evaluationDate().equal(cachedToday);
        YieldTermStructure baseCurve = flatRate(cachedSettlement, 0.05, new Actual360());
        RelinkableHandle<YieldTermStructure> baseCurveHandle =
                new RelinkableHandle<>(baseCurve, true);

        // Define spreaded curve with eps as spread used for FD sensitivities
        double eps = 1.0e-6;
        SimpleQuote spread = new SimpleQuote(0.0);
        YieldTermStructure spreadCurve = new ZeroSpreadedTermStructure(
                baseCurveHandle,
                new Handle<>(spread, true),
                Compounding.Continuous, Frequency.NO_FREQUENCY);
        vars.termStructure.linkTo(spreadCurve, true);
        Date startDate = vars.termStructure.currentLink().referenceDate();
        List<jquant.CashFlow> leg = vars.makeLeg(startDate, 20);

        CapFloor cap = vars.makeCapFloor(CapFloor.Type.Cap, leg, 0.05, 0.20, true);
        CapFloor floor = vars.makeCapFloor(CapFloor.Type.Floor, leg, 0.05, 0.20, true);

        // so far tests pass, now try to get additional results and it will fail
        int capletsNum = cap.capRates().size();
        List<Double> capletUpPrices;
        List<Double> capletDownPrices;
        List<Double> capletAnalyticDelta;
        List<Double> capletDiscountFactorsUp;
        List<Double> capletDiscountFactorsDown;
        List<Double> capletForwardsUp;
        List<Double> capletForwardsDown;
        List<Double> capletFDDelta =
                new ArrayList<>(Collections.nCopies(capletsNum, 0.0));
        int floorletNum = floor.floorRates().size();
        List<Double> floorletUpPrices;
        List<Double> floorletDownPrices;
        List<Double> floorletAnalyticDelta;
        List<Double> floorletDiscountFactorsUp;
        List<Double> floorletDiscountFactorsDown;
        List<Double> floorletForwardsUp;
        List<Double> floorletForwardsDown;
        List<Double> floorletFDDelta =
                new ArrayList<>(Collections.nCopies(floorletNum, 0.0));

        capletAnalyticDelta = doubleVector(cap, "optionletsDelta");
        floorletAnalyticDelta = doubleVector(floor, "optionletsDelta");

        spread.setValue(eps);
        capletUpPrices = doubleVector(cap, "optionletsPrice");
        floorletUpPrices = doubleVector(floor, "optionletsPrice");
        capletDiscountFactorsUp = doubleVector(cap, "optionletsDiscountFactor");
        floorletDiscountFactorsUp = doubleVector(floor, "optionletsDiscountFactor");
        capletForwardsUp = doubleVector(cap, "optionletsAtmForward");
        floorletForwardsUp = doubleVector(floor, "optionletsAtmForward");

        spread.setValue(-eps);
        capletDownPrices = doubleVector(cap, "optionletsPrice");
        floorletDownPrices = doubleVector(floor, "optionletsPrice");
        capletDiscountFactorsDown = doubleVector(cap, "optionletsDiscountFactor");
        floorletDiscountFactorsDown = doubleVector(floor, "optionletsDiscountFactor");
        capletForwardsDown = doubleVector(cap, "optionletsAtmForward");
        floorletForwardsDown = doubleVector(floor, "optionletsAtmForward");

        double accrualFactor;
        List<jquant.CashFlow> capLeg = cap.floatingLeg();
        List<jquant.CashFlow> floorLeg = floor.floatingLeg();

        for (int n = 1; n < capletUpPrices.size(); n++) {
            // calculating only caplet's FD sensitivity w.r.t. forward rate
            // without the effect of sensitivity related to changed discount factor
            FloatingRateCoupon c = (FloatingRateCoupon) capLeg.get(n);
            accrualFactor = c.nominal() * c.accrualPeriod() * c.gearing();
            capletFDDelta.set(n, (capletUpPrices.get(n) / capletDiscountFactorsUp.get(n)
                    - capletDownPrices.get(n) / capletDiscountFactorsDown.get(n))
                    / (capletForwardsUp.get(n) - capletForwardsDown.get(n))
                    / accrualFactor);
        }

        for (int n = 0; n < floorletUpPrices.size(); n++) {
            // calculating only caplet's FD sensitivity w.r.t. forward rate
            // without the effect of sensitivity related to changed discount factor
            FloatingRateCoupon c = (FloatingRateCoupon) floorLeg.get(n);
            accrualFactor = c.nominal() * c.accrualPeriod() * c.gearing();
            floorletFDDelta.set(n, (floorletUpPrices.get(n) / floorletDiscountFactorsUp.get(n)
                    - floorletDownPrices.get(n) / floorletDiscountFactorsDown.get(n))
                    / (floorletForwardsUp.get(n) - floorletForwardsDown.get(n))
                    / accrualFactor);
        }

        for (int n = 0; n < capletAnalyticDelta.size(); n++) {
            if (Math.abs(capletAnalyticDelta.get(n) - capletFDDelta.get(n)) > 1.0e-6) {
                fail("failed to compare analytical and finite difference caplet delta:\n"
                        + "caplet number:\t" + n + "\n"
                        + "    finite difference: " + capletFDDelta.get(n) + "\n"
                        + "    analytical value:   " + capletAnalyticDelta.get(n) + "\n"
                        + "    resulting ratio: "
                        + capletFDDelta.get(n) / capletAnalyticDelta.get(n));
            }
        }

        for (int n = 0; n < floorletAnalyticDelta.size(); n++) {
            if (Math.abs(floorletAnalyticDelta.get(n) - floorletFDDelta.get(n)) > 1.0e-6) {
                fail("failed to compare analytical and finite difference floorlet delta:\n"
                        + "floorlet number:\t" + n + "\n"
                        + "    finite difference: " + floorletFDDelta.get(n) + "\n"
                        + "    analytical value:   " + floorletAnalyticDelta.get(n) + "\n"
                        + "    resulting ratio: "
                        + floorletFDDelta.get(n) / floorletAnalyticDelta.get(n));
            }
        }
    }

    @Test
    public void testBachelierOptionLetsDelta() {
        CommonVars vars = new CommonVars();

        Date cachedToday = new Date(14, Month.MARCH, 2002);
        Date cachedSettlement = new Date(18, Month.MARCH, 2002);
        Settings.instance.evaluationDate().equal(cachedToday);
        YieldTermStructure baseCurve = flatRate(cachedSettlement, 0.05, new Actual360());
        RelinkableHandle<YieldTermStructure> baseCurveHandle =
                new RelinkableHandle<>(baseCurve, true);

        // Define spreaded curve with eps as spread used for FD sensitivities
        double eps = 1.0e-6;
        SimpleQuote spread = new SimpleQuote(0.0);
        YieldTermStructure spreadCurve = new ZeroSpreadedTermStructure(
                baseCurveHandle,
                new Handle<>(spread, true),
                Compounding.Continuous, Frequency.NO_FREQUENCY);
        vars.termStructure.linkTo(spreadCurve, true);
        Date startDate = vars.termStructure.currentLink().referenceDate();
        List<jquant.CashFlow> leg = vars.makeLeg(startDate, 20);

        // Use normal model (BachelierCapFloorEngine)
        boolean isLogNormal = false;

        CapFloor cap = vars.makeCapFloor(
                CapFloor.Type.Cap, leg, 0.05, 0.01, isLogNormal);
        CapFloor floor = vars.makeCapFloor(
                CapFloor.Type.Floor, leg, 0.05, 0.01, isLogNormal);

        // so far tests pass, now try to get additional results and it will fail
        int capletsNum = cap.capRates().size();
        List<Double> capletUpPrices;
        List<Double> capletDownPrices;
        List<Double> capletAnalyticDelta;
        List<Double> capletDiscountFactorsUp;
        List<Double> capletDiscountFactorsDown;
        List<Double> capletForwardsUp;
        List<Double> capletForwardsDown;
        List<Double> capletFDDelta =
                new ArrayList<>(Collections.nCopies(capletsNum, 0.0));
        int floorletNum = floor.floorRates().size();
        List<Double> floorletUpPrices;
        List<Double> floorletDownPrices;
        List<Double> floorletAnalyticDelta;
        List<Double> floorletDiscountFactorsUp;
        List<Double> floorletDiscountFactorsDown;
        List<Double> floorletForwardsUp;
        List<Double> floorletForwardsDown;
        List<Double> floorletFDDelta =
                new ArrayList<>(Collections.nCopies(floorletNum, 0.0));

        capletAnalyticDelta = doubleVector(cap, "optionletsDelta");
        floorletAnalyticDelta = doubleVector(floor, "optionletsDelta");

        spread.setValue(eps);
        capletUpPrices = doubleVector(cap, "optionletsPrice");
        floorletUpPrices = doubleVector(floor, "optionletsPrice");
        capletDiscountFactorsUp = doubleVector(cap, "optionletsDiscountFactor");
        floorletDiscountFactorsUp = doubleVector(floor, "optionletsDiscountFactor");
        capletForwardsUp = doubleVector(cap, "optionletsAtmForward");
        floorletForwardsUp = doubleVector(floor, "optionletsAtmForward");

        spread.setValue(-eps);
        capletDownPrices = doubleVector(cap, "optionletsPrice");
        floorletDownPrices = doubleVector(floor, "optionletsPrice");
        capletDiscountFactorsDown = doubleVector(cap, "optionletsDiscountFactor");
        floorletDiscountFactorsDown = doubleVector(floor, "optionletsDiscountFactor");
        capletForwardsDown = doubleVector(cap, "optionletsAtmForward");
        floorletForwardsDown = doubleVector(floor, "optionletsAtmForward");

        double accrualFactor;
        List<jquant.CashFlow> capLeg = cap.floatingLeg();
        List<jquant.CashFlow> floorLeg = floor.floatingLeg();

        for (int n = 1; n < capletUpPrices.size(); n++) {
            // calculating only caplet's FD sensitivity w.r.t. forward rate
            // without the effect of sensitivity related to changed discount factor
            FloatingRateCoupon c = (FloatingRateCoupon) capLeg.get(n);
            accrualFactor = c.nominal() * c.accrualPeriod() * c.gearing();
            capletFDDelta.set(n, (capletUpPrices.get(n) / capletDiscountFactorsUp.get(n)
                    - capletDownPrices.get(n) / capletDiscountFactorsDown.get(n))
                    / (capletForwardsUp.get(n) - capletForwardsDown.get(n))
                    / accrualFactor);
        }

        for (int n = 0; n < floorletUpPrices.size(); n++) {
            // calculating only caplet's FD sensitivity w.r.t. forward rate
            // without the effect of sensitivity related to changed discount factor
            FloatingRateCoupon c = (FloatingRateCoupon) floorLeg.get(n);
            accrualFactor = c.nominal() * c.accrualPeriod() * c.gearing();
            floorletFDDelta.set(n, (floorletUpPrices.get(n) / floorletDiscountFactorsUp.get(n)
                    - floorletDownPrices.get(n) / floorletDiscountFactorsDown.get(n))
                    / (floorletForwardsUp.get(n) - floorletForwardsDown.get(n))
                    / accrualFactor);
        }

        for (int n = 0; n < capletAnalyticDelta.size(); n++) {
            if (Math.abs(capletAnalyticDelta.get(n) - capletFDDelta.get(n)) > 1.0e-6) {
                fail("failed to compare analytical and finite difference caplet delta:\n"
                        + "caplet number:\t" + n + "\n"
                        + "    finite difference: " + capletFDDelta.get(n) + "\n"
                        + "    analytical value:   " + capletAnalyticDelta.get(n) + "\n"
                        + "    resulting ratio: "
                        + capletFDDelta.get(n) / capletAnalyticDelta.get(n));
            }
        }

        for (int n = 0; n < floorletAnalyticDelta.size(); n++) {
            if (Math.abs(floorletAnalyticDelta.get(n) - floorletFDDelta.get(n)) > 1.0e-6) {
                fail("failed to compare analytical and finite difference floorlet delta:\n"
                        + "floorlet number:\t" + n + "\n"
                        + "    finite difference: " + floorletFDDelta.get(n) + "\n"
                        + "    analytical value:   " + floorletAnalyticDelta.get(n) + "\n"
                        + "    resulting ratio: "
                        + floorletFDDelta.get(n) / floorletAnalyticDelta.get(n));
            }
        }
    }

    @Test
    public void testOptionLetsDeltaAtZeroVolatility() {
        pinUnpinnedTestDate();
        CommonVars vars = new CommonVars();

        Date today = Settings.instance.evaluationDate().Date();
        List<jquant.CashFlow> leg = vars.makeLeg(vars.settlement, 10);

        // the forwards are close to 5%, so each optionlet is clearly in or out of the money
        double[] strikes = {0.03, 0.07};
        CapFloor.Type[] types = {CapFloor.Type.Cap, CapFloor.Type.Floor};
        boolean[] lognormal = {true, false};

        for (boolean isLogNormal : lognormal) {
            for (CapFloor.Type type : types) {
                for (double strike : strikes) {
                    CapFloor capFloor =
                            vars.makeCapFloor(type, leg, strike, 0.0, isLogNormal);
                    List<Double> deltas = doubleVector(capFloor, "optionletsDelta");
                    List<Double> forwards = doubleVector(
                            capFloor, "optionletsAtmForward");

                    for (int n = 0; n < leg.size(); ++n) {
                        FloatingRateCoupon coupon = (FloatingRateCoupon) leg.get(n);
                        // optionlets already fixed have no delta
                        double expected = 0.0;
                        if (TimeUtils.greater(coupon.fixingDate(), today)) {
                            if (type == CapFloor.Type.Cap)
                                expected = forwards.get(n) > strike ? 1.0 : 0.0;
                            else
                                expected = forwards.get(n) < strike ? -1.0 : 0.0;
                        }
                        if (Math.abs(deltas.get(n) - expected) > 1.0e-12) {
                            fail("wrong optionlet delta at zero volatility:\n"
                                    + "    engine:     "
                                    + (isLogNormal ? "Black" : "Bachelier") + "\n"
                                    + "    type:       " + typeToString(type) + "\n"
                                    + "    optionlet:  " + n + "\n"
                                    + "    strike:     " + strike + "\n"
                                    + "    forward:    " + forwards.get(n) + "\n"
                                    + "    calculated: " + deltas.get(n) + "\n"
                                    + "    expected:   " + expected);
                        }
                    }
                }
            }
        }
    }
}
