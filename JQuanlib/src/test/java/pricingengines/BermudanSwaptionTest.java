package pricingengines;

import jquant.BermudanExercise;
import jquant.CashFlow;
import jquant.Exercise;
import jquant.Handle;
import jquant.RelinkableHandle;
import jquant.SavedSettings;
import jquant.Settings;
import jquant.cashflows.Coupon;
import jquant.cashflows.IborCoupon;
import jquant.cashflows.RateAveraging;
import jquant.indexes.Eonia;
import jquant.indexes.Euribor3M;
import jquant.indexes.Euribor6M;
import jquant.indexes.IborIndex;
import jquant.indexes.IndexManager;
import jquant.instruments.MakeVanillaSwap;
import jquant.instruments.OvernightIndexedSwap;
import jquant.instruments.Swap;
import jquant.instruments.Swaption;
import jquant.instruments.VanillaSwap;
import jquant.models.shortrate.onefactormodels.HullWhite;
import jquant.models.shortrate.twofactormodels.G2;
import jquant.patterns.Observer;
import jquant.pricingengines.DiscountingSwapEngine;
import jquant.pricingengines.swaption.FdG2SwaptionEngine;
import jquant.pricingengines.swaption.FdHullWhiteSwaptionEngine;
import jquant.pricingengines.swaption.TreeSwaptionEngine;
import jquant.termstructures.YieldTermStructure;
import jquant.termstructures.yield.FlatForward;
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
import jquant.time.daycounters.Actual365Fixed;
import jquant.time.daycounters.Thirty360;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.fail;

/**
 * Official QuantLib {@code test-suite/bermudanswaption.cpp} tests
 * (suite {@code BermudanSwaptionTests}).
 *
 * <p>Official {@code BOOST_ERROR} records a failure but continues the test
 * case; the messages are accumulated and the test fails at the end. Official
 * {@code BOOST_FAIL} aborts immediately.
 */
public class BermudanSwaptionTest {

    private SavedSettings savedSettings;
    private Set<Observer> evalObserversAtStart;

    private final List<String> boostErrors = new ArrayList<>();

    @BeforeEach
    void saveSettings() {
        savedSettings = new SavedSettings();
        evalObserversAtStart = snapshotEvalDateObservers();
    }

    @AfterEach
    void restoreSettings() {
        final Set<Observer> added = snapshotEvalDateObservers();
        if (evalObserversAtStart != null) {
            added.removeAll(evalObserversAtStart);
        }
        for (Observer o : added) {
            o.unregisterWithAll();
        }
        IndexManager.Instance.clearHistories();
        savedSettings.restore();
    }

    @SuppressWarnings("unchecked")
    private static Set<Observer> snapshotEvalDateObservers() {
        final Object observable = Settings.instance.evaluationDate();
        Class<?> c = observable.getClass();
        while (c != null) {
            try {
                final Field f = c.getDeclaredField("observers");
                f.setAccessible(true);
                final Object v = f.get(observable);
                if (v instanceof Set) {
                    return new HashSet<>((Set<Observer>) v);
                }
            } catch (NoSuchFieldException ignored) {
            } catch (IllegalAccessException e) {
                throw new IllegalStateException(e);
            }
            c = c.getSuperclass();
        }
        return Set.of();
    }

    private void checkBoostErrors() {
        if (!boostErrors.isEmpty())
            fail(String.join("\n", boostErrors));
    }

    private static FlatForward flatRate(Date settlement, double rate, DayCounter dc) {
        return new FlatForward(settlement, rate, dc,
                jquant.Compounding.Continuous, Frequency.ANNUAL);
    }

    private static String isoDate(Date date) {
        return String.format("%04d-%02d-%02d",
                date.year(), date.month().getValue(), date.dayOfMonth());
    }

    // ------------------------------------------------------------------
    // Official CommonVars
    // ------------------------------------------------------------------

    private static class CommonVars {
        // global data
        Date today, settlement;
        Calendar calendar;

        // underlying swap parameters
        int startYears, length;
        Swap.Type type;
        double nominal;
        BusinessDayConvention fixedConvention, floatingConvention;
        Frequency fixedFrequency, floatingFrequency;
        DayCounter fixedDayCount;
        IborIndex index;
        int settlementDays;

        final RelinkableHandle<YieldTermStructure> termStructure =
                new RelinkableHandle<>();

        CommonVars() {
            startYears = 1;
            length = 5;
            type = Swap.Type.Payer;
            nominal = 1000.0;
            settlementDays = 2;
            fixedConvention = BusinessDayConvention.UNADJUSTED;
            floatingConvention = BusinessDayConvention.MODIFIED_FOLLOWING;
            fixedFrequency = Frequency.ANNUAL;
            floatingFrequency = Frequency.SEMIANNUAL;
            fixedDayCount = new Thirty360(Thirty360.Convention.BondBasis);
            index = new Euribor6M(termStructure);
            calendar = index.fixingCalendar();
            today = calendar.adjust(Date.todaysDate());
            settlement = calendar.advance(today, settlementDays, TimeUnit.DAYS);
        }

        VanillaSwap makeSwap(double fixedRate) {
            Date start = calendar.advance(settlement, startYears, TimeUnit.YEARS);
            Date maturity = calendar.advance(start, length, TimeUnit.YEARS);
            Schedule fixedSchedule = new Schedule(
                    start, maturity, new Period(fixedFrequency),
                    calendar, fixedConvention, fixedConvention,
                    DateGenerationRule.FORWARD, false,
                    new Date(), new Date());
            Schedule floatSchedule = new Schedule(
                    start, maturity, new Period(floatingFrequency),
                    calendar, floatingConvention, floatingConvention,
                    DateGenerationRule.FORWARD, false,
                    new Date(), new Date());
            VanillaSwap.Type vanillaType = type == Swap.Type.Payer
                    ? VanillaSwap.Type.Payer : VanillaSwap.Type.Receiver;
            VanillaSwap swap = new VanillaSwap(vanillaType, nominal,
                    fixedSchedule, fixedRate, fixedDayCount,
                    floatSchedule, index, 0.0,
                    index.dayCounter());
            swap.setPricingEngine(new DiscountingSwapEngine(termStructure));
            return swap;
        }
    }

    // ------------------------------------------------------------------
    // Tests
    // ------------------------------------------------------------------

    @Test
    void testCachedValues() {
        System.out.println(
                "Testing Bermudan swaption with HW model against cached values...");

        boolean usingAtParCoupons = IborCoupon.Settings.instance().usingAtParCoupons();

        CommonVars vars = new CommonVars();

        vars.today = new Date(15, Month.FEBRUARY, 2002);

        Settings.instance.evaluationDate().equal(vars.today);

        vars.settlement = new Date(19, Month.FEBRUARY, 2002);
        // flat yield term structure impling 1x5 swap at 5%
        vars.termStructure.linkTo(flatRate(vars.settlement,
                0.04875825,
                new Actual365Fixed()), true);

        double atmRate = vars.makeSwap(0.0).fairRate();

        VanillaSwap itmSwap = vars.makeSwap(0.8 * atmRate);
        VanillaSwap atmSwap = vars.makeSwap(atmRate);
        VanillaSwap otmSwap = vars.makeSwap(1.2 * atmRate);

        double a = 0.048696, sigma = 0.0058904;
        HullWhite model = new HullWhite(vars.termStructure, a, sigma);
        List<Date> exerciseDates = new ArrayList<>();
        for (CashFlow i : atmSwap.fixedLeg()) {
            Coupon coupon = (Coupon) i;
            exerciseDates.add(coupon.accrualStartDate());
        }
        Exercise exercise = new BermudanExercise(exerciseDates);

        TreeSwaptionEngine treeEngine = new TreeSwaptionEngine(model, 50);
        FdHullWhiteSwaptionEngine fdmEngine = new FdHullWhiteSwaptionEngine(model);

        double itmValue, atmValue, otmValue;
        double itmValueFdm, atmValueFdm, otmValueFdm;
        if (!usingAtParCoupons) {
            itmValue = 42.2402;
            atmValue = 12.9032;
            otmValue = 2.49758;
            itmValueFdm = 42.2111;
            atmValueFdm = 12.8879;
            otmValueFdm = 2.44443;
        } else {
            itmValue = 42.2460;
            atmValue = 12.9069;
            otmValue = 2.4985;
            itmValueFdm = 42.2091;
            atmValueFdm = 12.8864;
            otmValueFdm = 2.4437;
        }

        double tolerance = 1.0e-4;

        Swaption swaption = new Swaption(itmSwap, exercise);
        swaption.setPricingEngine(treeEngine);
        if (Math.abs(swaption.NPV() - itmValue) > tolerance)
            boostErrors.add("failed to reproduce cached in-the-money swaption value:\n"
                    + "calculated: " + swaption.NPV() + "\n"
                    + "expected:   " + itmValue);
        swaption.setPricingEngine(fdmEngine);
        if (Math.abs(swaption.NPV() - itmValueFdm) > tolerance)
            boostErrors.add("failed to reproduce cached in-the-money swaption value:\n"
                    + "calculated: " + swaption.NPV() + "\n"
                    + "expected:   " + itmValueFdm);

        swaption = new Swaption(atmSwap, exercise);
        swaption.setPricingEngine(treeEngine);
        if (Math.abs(swaption.NPV() - atmValue) > tolerance)
            boostErrors.add("failed to reproduce cached at-the-money swaption value:\n"
                    + "calculated: " + swaption.NPV() + "\n"
                    + "expected:   " + atmValue);

        swaption.setPricingEngine(fdmEngine);
        if (Math.abs(swaption.NPV() - atmValueFdm) > tolerance)
            boostErrors.add("failed to reproduce cached at-the-money swaption value:\n"
                    + "calculated: " + swaption.NPV() + "\n"
                    + "expected:   " + atmValueFdm);

        swaption = new Swaption(otmSwap, exercise);
        swaption.setPricingEngine(treeEngine);
        if (Math.abs(swaption.NPV() - otmValue) > tolerance)
            boostErrors.add("failed to reproduce cached out-of-the-money "
                    + "swaption value:\n"
                    + "calculated: " + swaption.NPV() + "\n"
                    + "expected:   " + otmValue);

        swaption.setPricingEngine(fdmEngine);
        if (Math.abs(swaption.NPV() - otmValueFdm) > tolerance)
            boostErrors.add("failed to reproduce cached out-of-the-money "
                    + "swaption value:\n"
                    + "calculated: " + swaption.NPV() + "\n"
                    + "expected:   " + otmValueFdm);

        for (int i = 0; i < exerciseDates.size(); i++)
            exerciseDates.set(i, vars.calendar.adjust(exerciseDates.get(i).substract(10)));
        exercise = new BermudanExercise(exerciseDates);

        if (!usingAtParCoupons) {
            itmValue = 42.1791;
            atmValue = 12.7699;
            otmValue = 2.4368;
        } else {
            itmValue = 42.1849;
            atmValue = 12.7736;
            otmValue = 2.4379;
        }

        swaption = new Swaption(itmSwap, exercise);
        swaption.setPricingEngine(treeEngine);
        if (Math.abs(swaption.NPV() - itmValue) > tolerance)
            boostErrors.add("failed to reproduce cached in-the-money swaption value:\n"
                    + "calculated: " + swaption.NPV() + "\n"
                    + "expected:   " + itmValue);
        swaption = new Swaption(atmSwap, exercise);
        swaption.setPricingEngine(treeEngine);
        if (Math.abs(swaption.NPV() - atmValue) > tolerance)
            boostErrors.add("failed to reproduce cached at-the-money swaption value:\n"
                    + "calculated: " + swaption.NPV() + "\n"
                    + "expected:   " + atmValue);
        swaption = new Swaption(otmSwap, exercise);
        swaption.setPricingEngine(treeEngine);
        if (Math.abs(swaption.NPV() - otmValue) > tolerance)
            boostErrors.add("failed to reproduce cached out-of-the-money "
                    + "swaption value:\n"
                    + "calculated: " + swaption.NPV() + "\n"
                    + "expected:   " + otmValue);

        checkBoostErrors();
    }

    @Test
    void testCachedG2Values() {
        System.out.println(
                "Testing Bermudan swaption with G2 model against cached values...");

        boolean usingAtParCoupons = IborCoupon.Settings.instance().usingAtParCoupons();

        CommonVars vars = new CommonVars();

        vars.today = new Date(15, Month.SEPTEMBER, 2016);
        Settings.instance.evaluationDate().equal(vars.today);
        vars.settlement = new Date(19, Month.SEPTEMBER, 2016);

        // flat yield term structure impling 1x5 swap at 5%
        vars.termStructure.linkTo(flatRate(vars.settlement,
                0.04875825,
                new Actual365Fixed()), true);

        final double atmRate = vars.makeSwap(0.0).fairRate();
        List<Swaption> swaptions = new ArrayList<>();
        for (double s = 0.5; s < 1.51; s += 0.25) {
            VanillaSwap swap = vars.makeSwap(s * atmRate);

            List<Date> exerciseDates = new ArrayList<>();
            for (CashFlow i : swap.fixedLeg()) {
                Coupon coupon = (Coupon) i;
                exerciseDates.add(coupon.accrualStartDate());
            }

            swaptions.add(new Swaption(swap, new BermudanExercise(exerciseDates)));
        }

        final double a = 0.1, sigma = 0.01, b = 0.2, eta = 0.013, rho = -0.5;

        G2 g2Model = new G2(vars.termStructure, a, sigma, b, eta, rho);
        FdG2SwaptionEngine fdmEngine =
                new FdG2SwaptionEngine(g2Model, 50, 75, 75, 0, 1e-3);
        TreeSwaptionEngine treeEngine = new TreeSwaptionEngine(g2Model, 50);

        double[] expectedFdm;
        double[] expectedTree;
        if (!usingAtParCoupons) {
            expectedFdm = new double[]{103.231, 54.6519, 20.0475, 5.26941, 1.07097};
            expectedTree = new double[]{103.245, 54.6685, 20.1656, 5.43999, 1.12702};
        } else {
            expectedFdm = new double[]{103.227, 54.6502, 20.0469, 5.26924, 1.07093};
            expectedTree = new double[]{103.248, 54.6726, 20.1685, 5.44118, 1.12737};
        }

        final double tol = 0.005;
        for (int i = 0; i < swaptions.size(); ++i) {
            swaptions.get(i).setPricingEngine(fdmEngine);
            final double calculatedFdm = swaptions.get(i).NPV();

            if (Math.abs(calculatedFdm - expectedFdm[i]) > tol)
                boostErrors.add("failed to reproduce cached G2 FDM swaption value:\n"
                        + "calculated: " + calculatedFdm + "\n"
                        + "expected:   " + expectedFdm[i]);

            swaptions.get(i).setPricingEngine(treeEngine);
            final double calculatedTree = swaptions.get(i).NPV();

            if (Math.abs(calculatedTree - expectedTree[i]) > tol)
                boostErrors.add("failed to reproduce cached G2 Tree swaption value:\n"
                        + "calculated: " + calculatedTree + "\n"
                        + "expected:   " + expectedTree[i]);
        }

        checkBoostErrors();
    }

    @Test
    void testTreeEngineTimeSnapping() {
        System.out.println("Testing snap of exercise dates for discretized swaption...");

        Date today = new Date(8, Month.JULY, 2021);
        Settings.instance.evaluationDate().equal(today);

        RelinkableHandle<YieldTermStructure> termStructure = new RelinkableHandle<>();
        termStructure.linkTo(new FlatForward(today, 0.02, new Actual365Fixed()), true);
        Euribor3M index = new Euribor3M(termStructure);

        int intervalOfDaysToTest = 10;

        for (int i = -intervalOfDaysToTest; i < intervalOfDaysToTest + 1; i++) {
            Date initialCallDate = new Date(15, Month.MAY, 2030);
            Calendar calendar = index.fixingCalendar();

            Date callDate = initialCallDate.add(i);
            if (!calendar.isBusinessDay(callDate))
                continue;

            Date effectiveDate = new Date(15, Month.MAY, 2025);
            VanillaSwap swap = new MakeVanillaSwap(
                    new Period(10, TimeUnit.YEARS), index)
                    .withFixedRate(0.05)
                    .withEffectiveDate(effectiveDate)
                    .withNominal(10000.00)
                    .withType(Swap.Type.Payer)
                    .makeSwap();

            List<Date> exerciseDates = new ArrayList<>();
            exerciseDates.add(effectiveDate);
            exerciseDates.add(callDate);
            BermudanExercise bermudanExercise = new BermudanExercise(exerciseDates);
            Swaption bermudanSwaption = new Swaption(swap, bermudanExercise);

            HullWhite model = new HullWhite(termStructure);

            bermudanSwaption.setPricingEngine(new FdHullWhiteSwaptionEngine(model));
            double npvFD = bermudanSwaption.NPV();

            final int timesteps = 14 * 4 * 4;

            bermudanSwaption.setPricingEngine(new TreeSwaptionEngine(model, timesteps));
            double npvTree = bermudanSwaption.NPV();

            double npvDiff = npvTree - npvFD;

            final double tolerance = 1.0;
            if (Math.abs(npvTree - npvFD) > tolerance) {
                boostErrors.add("At " + isoDate(callDate)
                        + ": The difference between the npv of the FD and the tree "
                        + "engine is expected to be smaller than "
                        + tolerance + " but was " + npvDiff + ". (FD: " + npvFD
                        + ", tree: " + npvTree + ")");
            }
        }

        checkBoostErrors();
    }

    @Test
    void testBermudanOISSwaptionWithHW() {
        System.out.println(
                "Testing Bermudan swaption with OIS underlying and HW model...");

        CommonVars vars = new CommonVars();

        vars.today = new Date(15, Month.FEBRUARY, 2002);
        Settings.instance.evaluationDate().equal(vars.today);
        vars.settlement = new Date(19, Month.FEBRUARY, 2002);

        vars.termStructure.linkTo(flatRate(vars.settlement,
                0.04875825,
                new Actual365Fixed()), true);

        // Build OIS with same economics as the VanillaSwap test
        Eonia overnightIndex = new Eonia(vars.termStructure);

        Date start = vars.calendar.advance(vars.settlement,
                vars.startYears, TimeUnit.YEARS);
        Date maturity = vars.calendar.advance(start, vars.length, TimeUnit.YEARS);

        Schedule fixedSchedule = new Schedule(
                start, maturity, new Period(vars.fixedFrequency),
                vars.calendar, vars.fixedConvention, vars.fixedConvention,
                DateGenerationRule.FORWARD, false,
                new Date(), new Date());
        Schedule overnightSchedule = new Schedule(
                start, maturity, new Period(vars.floatingFrequency),
                vars.calendar, vars.floatingConvention, vars.floatingConvention,
                DateGenerationRule.FORWARD, false,
                new Date(), new Date());

        // Under a flat single curve, the VanillaSwap ATM rate equals the OIS ATM rate
        final double atmRate = vars.makeSwap(0.0).fairRate();

        // Build Bermudan exercise from fixed leg
        OvernightIndexedSwap refOIS = new OvernightIndexedSwap(
                vars.type, vars.nominal, fixedSchedule, atmRate,
                vars.fixedDayCount, overnightSchedule, overnightIndex, 0.0);
        refOIS.setPricingEngine(new DiscountingSwapEngine(vars.termStructure));

        List<Date> exerciseDates = new ArrayList<>();
        for (CashFlow i : refOIS.fixedLeg()) {
            Coupon coupon = (Coupon) i;
            exerciseDates.add(coupon.accrualStartDate());
        }
        BermudanExercise exercise = new BermudanExercise(exerciseDates);

        final double a = 0.048696, sigma = 0.0058904;
        HullWhite model = new HullWhite(vars.termStructure, a, sigma);
        FdHullWhiteSwaptionEngine fdmEngine = new FdHullWhiteSwaptionEngine(model);

        OvernightIndexedSwap itmOIS = makeOIS(vars, fixedSchedule, overnightSchedule,
                overnightIndex, 0.8 * atmRate);
        OvernightIndexedSwap atmOIS = makeOIS(vars, fixedSchedule, overnightSchedule,
                overnightIndex, atmRate);
        OvernightIndexedSwap otmOIS = makeOIS(vars, fixedSchedule, overnightSchedule,
                overnightIndex, 1.2 * atmRate);

        // Price OIS Bermudan swaptions
        Swaption itmSwaption = new Swaption(itmOIS, exercise);
        itmSwaption.setPricingEngine(fdmEngine);
        double itmValue = itmSwaption.NPV();

        Swaption atmSwaption = new Swaption(atmOIS, exercise);
        atmSwaption.setPricingEngine(fdmEngine);
        double atmValue = atmSwaption.NPV();

        Swaption otmSwaption = new Swaption(otmOIS, exercise);
        otmSwaption.setPricingEngine(fdmEngine);
        double otmValue = otmSwaption.NPV();

        // OIS Bermudan should produce positive values
        if (itmValue <= 0.0)
            boostErrors.add("ITM OIS Bermudan swaption has non-positive value: "
                    + itmValue);
        if (atmValue <= 0.0)
            boostErrors.add("ATM OIS Bermudan swaption has non-positive value: "
                    + atmValue);
        if (otmValue <= 0.0)
            boostErrors.add("OTM OIS Bermudan swaption has non-positive value: "
                    + otmValue);

        // ITM > ATM > OTM monotonicity
        if (itmValue <= atmValue)
            boostErrors.add("ITM OIS Bermudan (" + itmValue
                    + ") should exceed ATM (" + atmValue + ")");
        if (atmValue <= otmValue)
            boostErrors.add("ATM OIS Bermudan (" + atmValue
                    + ") should exceed OTM (" + otmValue + ")");

        // Compare with VanillaSwap Bermudan - under HW, difference should be small
        VanillaSwap itmVanilla = vars.makeSwap(0.8 * atmRate);
        VanillaSwap atmVanilla = vars.makeSwap(atmRate);
        VanillaSwap otmVanilla = vars.makeSwap(1.2 * atmRate);

        Swaption itmVS = new Swaption(itmVanilla, exercise);
        itmVS.setPricingEngine(fdmEngine);
        Swaption atmVS = new Swaption(atmVanilla, exercise);
        atmVS.setPricingEngine(fdmEngine);
        Swaption otmVS = new Swaption(otmVanilla, exercise);
        otmVS.setPricingEngine(fdmEngine);

        // Under single-factor HW with flat curve, VanillaSwap and OIS
        // Bermudans should be close (floating leg ≈ par in both cases).
        // Allow up to 5% relative difference.
        final double relTol = 0.05;

        double itmDiff = relDiff(itmValue, itmVS.NPV());
        double atmDiff = relDiff(atmValue, atmVS.NPV());
        double otmDiff = relDiff(otmValue, otmVS.NPV());

        if (itmDiff > relTol)
            boostErrors.add("ITM OIS vs VanillaSwap Bermudan difference too large: "
                    + itmValue + " vs " + itmVS.NPV() + " (rel diff " + itmDiff + ")");
        if (atmDiff > relTol)
            boostErrors.add("ATM OIS vs VanillaSwap Bermudan difference too large: "
                    + atmValue + " vs " + atmVS.NPV() + " (rel diff " + atmDiff + ")");
        if (otmDiff > relTol)
            boostErrors.add("OTM OIS vs VanillaSwap Bermudan difference too large: "
                    + otmValue + " vs " + otmVS.NPV() + " (rel diff " + otmDiff + ")");

        checkBoostErrors();
    }

    private static OvernightIndexedSwap makeOIS(CommonVars vars,
                                                Schedule fixedSchedule,
                                                Schedule overnightSchedule,
                                                Eonia overnightIndex,
                                                double fixedRate) {
        OvernightIndexedSwap ois = new OvernightIndexedSwap(
                vars.type, vars.nominal, fixedSchedule, fixedRate,
                vars.fixedDayCount, overnightSchedule, overnightIndex, 0.0);
        ois.setPricingEngine(new DiscountingSwapEngine(vars.termStructure));
        return ois;
    }

    private static double relDiff(double a, double b) {
        return Math.abs(a - b) / Math.max(Math.abs(b), 1e-10);
    }

    @Test
    void testBermudanOISSwaptionWithG2() {
        System.out.println(
                "Testing Bermudan swaption with OIS underlying and G2 model...");

        CommonVars vars = new CommonVars();

        vars.today = new Date(15, Month.FEBRUARY, 2002);
        Settings.instance.evaluationDate().equal(vars.today);
        vars.settlement = new Date(19, Month.FEBRUARY, 2002);

        vars.termStructure.linkTo(flatRate(vars.settlement,
                0.04875825,
                new Actual365Fixed()), true);

        Eonia overnightIndex = new Eonia(vars.termStructure);

        Date start = vars.calendar.advance(vars.settlement,
                vars.startYears, TimeUnit.YEARS);
        Date maturity = vars.calendar.advance(start, vars.length, TimeUnit.YEARS);

        Schedule fixedSchedule = new Schedule(
                start, maturity, new Period(vars.fixedFrequency),
                vars.calendar, vars.fixedConvention, vars.fixedConvention,
                DateGenerationRule.FORWARD, false,
                new Date(), new Date());
        Schedule overnightSchedule = new Schedule(
                start, maturity, new Period(vars.floatingFrequency),
                vars.calendar, vars.floatingConvention, vars.floatingConvention,
                DateGenerationRule.FORWARD, false,
                new Date(), new Date());

        // Under a flat single curve, the VanillaSwap ATM rate equals the OIS ATM rate
        final double atmRate = vars.makeSwap(0.0).fairRate();

        OvernightIndexedSwap atmOIS = makeOIS(vars, fixedSchedule,
                overnightSchedule, overnightIndex, atmRate);

        List<Date> exerciseDates = new ArrayList<>();
        for (CashFlow i : atmOIS.fixedLeg()) {
            Coupon coupon = (Coupon) i;
            exerciseDates.add(coupon.accrualStartDate());
        }
        BermudanExercise exercise = new BermudanExercise(exerciseDates);

        G2 model = new G2(vars.termStructure);
        FdG2SwaptionEngine fdmEngine = new FdG2SwaptionEngine(model);

        Swaption oisSwaption = new Swaption(atmOIS, exercise);
        oisSwaption.setPricingEngine(fdmEngine);
        double oisValue = oisSwaption.NPV();

        if (oisValue <= 0.0)
            boostErrors.add("ATM OIS Bermudan swaption (G2) has non-positive value: "
                    + oisValue);

        // Compare with VanillaSwap Bermudan
        VanillaSwap atmVanilla = vars.makeSwap(atmRate);
        Swaption vsSwaption = new Swaption(atmVanilla, exercise);
        vsSwaption.setPricingEngine(fdmEngine);
        double vsValue = vsSwaption.NPV();

        double diff = relDiff(oisValue, vsValue);
        final double relTol = 0.05;
        if (diff > relTol)
            boostErrors.add("ATM OIS vs VanillaSwap Bermudan (G2) difference too large: "
                    + oisValue + " vs " + vsValue
                    + " (rel diff " + diff + ")");

        checkBoostErrors();
    }

    @Test
    void testBermudanOISSwaptionPreservesFeatures() {
        System.out.println(
                "Testing that Bermudan OIS swaption preserves averaging and lockout...");

        CommonVars vars = new CommonVars();

        vars.today = new Date(15, Month.FEBRUARY, 2002);
        Settings.instance.evaluationDate().equal(vars.today);
        vars.settlement = new Date(19, Month.FEBRUARY, 2002);

        vars.termStructure.linkTo(flatRate(vars.settlement,
                0.04875825,
                new Actual365Fixed()), true);

        Eonia overnightIndex = new Eonia(vars.termStructure);

        Date start = vars.calendar.advance(vars.settlement,
                vars.startYears, TimeUnit.YEARS);
        Date maturity = vars.calendar.advance(start, vars.length, TimeUnit.YEARS);

        Schedule fixedSchedule = new Schedule(
                start, maturity, new Period(vars.fixedFrequency),
                vars.calendar, vars.fixedConvention, vars.fixedConvention,
                DateGenerationRule.FORWARD, false,
                new Date(), new Date());
        Schedule overnightSchedule = new Schedule(
                start, maturity, new Period(vars.floatingFrequency),
                vars.calendar, vars.floatingConvention, vars.floatingConvention,
                DateGenerationRule.FORWARD, false,
                new Date(), new Date());

        // Under a flat single curve, the VanillaSwap ATM rate equals the OIS ATM rate
        final double atmRate = vars.makeSwap(0.0).fairRate();

        // Build exercise from fixed leg
        OvernightIndexedSwap refOIS = makeOISWithFeatures(vars, fixedSchedule,
                overnightSchedule, overnightIndex, atmRate,
                RateAveraging.Compound, 0);
        List<Date> exerciseDates = new ArrayList<>();
        for (CashFlow i : refOIS.fixedLeg()) {
            Coupon coupon = (Coupon) i;
            exerciseDates.add(coupon.accrualStartDate());
        }
        BermudanExercise exercise = new BermudanExercise(exerciseDates);

        final double a = 0.048696, sigma = 0.0058904;
        HullWhite model = new HullWhite(vars.termStructure, a, sigma);
        FdHullWhiteSwaptionEngine fdmEngine = new FdHullWhiteSwaptionEngine(model);

        // Price with compound averaging (default)
        OvernightIndexedSwap compoundOIS = makeOISWithFeatures(vars, fixedSchedule,
                overnightSchedule, overnightIndex, atmRate,
                RateAveraging.Compound, 0);
        Swaption compoundSwaption = new Swaption(compoundOIS, exercise);
        compoundSwaption.setPricingEngine(fdmEngine);
        double compoundValue = compoundSwaption.NPV();

        // Price with simple averaging — should differ meaningfully
        OvernightIndexedSwap simpleOIS = makeOISWithFeatures(vars, fixedSchedule,
                overnightSchedule, overnightIndex, atmRate,
                RateAveraging.Simple, 0);
        Swaption simpleSwaption = new Swaption(simpleOIS, exercise);
        simpleSwaption.setPricingEngine(fdmEngine);
        double simpleValue = simpleSwaption.NPV();

        // Simple vs compound averaging produces ~9-10% difference at 5% rate
        // level (arithmetic vs geometric compounding over semi-annual periods).
        // Use a conservative 0.1% floor to avoid false passes.
        double avgDiff = Math.abs(compoundValue - simpleValue)
                / Math.max(compoundValue, 1e-10);
        if (avgDiff < 0.001)
            boostErrors.add("Simple vs compound OIS Bermudan should differ,"
                    + " got " + (avgDiff * 100) + "% (compound=" + compoundValue
                    + ", simple=" + simpleValue + ")");

        // Price with lockout — should differ from plain compound
        OvernightIndexedSwap lockoutOIS = makeOISWithFeatures(vars, fixedSchedule,
                overnightSchedule, overnightIndex, atmRate,
                RateAveraging.Compound, 5);
        Swaption lockoutSwaption = new Swaption(lockoutOIS, exercise);
        lockoutSwaption.setPricingEngine(fdmEngine);
        double lockoutValue = lockoutSwaption.NPV();

        // Lockout freezes the last N fixings, producing a small but
        // non-zero change. Use relative tolerance rather than exact equality.
        double lockDiff = Math.abs(lockoutValue - compoundValue)
                / Math.max(compoundValue, 1e-10);
        if (lockDiff < 1e-8)
            boostErrors.add("5-day lockout OIS Bermudan should differ from plain,"
                    + " rel diff = " + lockDiff
                    + " (lockout=" + lockoutValue
                    + ", plain=" + compoundValue + ")");

        checkBoostErrors();
    }

    private static OvernightIndexedSwap makeOISWithFeatures(
            CommonVars vars,
            Schedule fixedSchedule,
            Schedule overnightSchedule,
            Eonia overnightIndex,
            double fixedRate,
            RateAveraging avg,
            int lockout) {
        OvernightIndexedSwap ois = new OvernightIndexedSwap(
                vars.type, vars.nominal, fixedSchedule, fixedRate,
                vars.fixedDayCount, overnightSchedule, overnightIndex, 0.0,
                0,                    // paymentLag
                vars.floatingConvention,
                null,                 // paymentCalendar
                false,                // telescopicValueDates
                avg,
                null,                 // lookbackDays
                lockout,
                false);               // applyObservationShift
        ois.setPricingEngine(new DiscountingSwapEngine(vars.termStructure));
        return ois;
    }
}
