package jquant.cashflows;

import jquant.CashFlow;
import jquant.Handle;
import jquant.RelinkableHandle;
import jquant.SavedSettings;
import jquant.Settings;
import jquant.indexes.Estr;
import jquant.indexes.IndexManager;
import jquant.indexes.OvernightIndex;
import jquant.indexes.Sofr;
import jquant.termstructures.YieldTermStructure;
import jquant.termstructures.yield.CubicZeroCurve;
import jquant.termstructures.yield.DiscountCurve;
import jquant.termstructures.yield.FlatForward;
import jquant.time.BusinessDayConvention;
import jquant.time.Calendar;
import jquant.time.Date;
import jquant.time.DateGenerationRule;
import jquant.time.DayCounter;
import jquant.time.MakeSchedule;
import jquant.time.Month;
import jquant.time.Period;
import jquant.time.Schedule;
import jquant.time.TimeUnit;
import jquant.time.TimeUtils;
import jquant.time.calendars.NullCalendar;
import jquant.time.calendars.UnitedStates;
import jquant.time.daycounters.Actual360;
import jquant.time.daycounters.Actual365Fixed;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import time.WeekendsOnly;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Port of the default-constructor overnight-indexed coupon cases from
 * QuantLib {@code test-suite/overnightindexedcoupon.cpp}.
 *
 * <p>This class covers the first nine official cases only: past, current and
 * future coupon rates, the holiday evaluation date, and accrued amounts in
 * the past/spanning today/in the future/on holidays.
 */
public class OvernightIndexedCouponTest {

    private SavedSettings savedSettings;

    @BeforeEach
    void saveSettings() {
        savedSettings = new SavedSettings();
    }

    @AfterEach
    void restoreSettings() {
        // Official TopLevelFixture clears index histories before SavedSettings
        // restores the evaluation date and other settings.
        IndexManager.Instance.clearHistories();
        savedSettings.restore();
    }

    private static void checkOisCouponResult(String what,
                                             double calculated,
                                             double expected,
                                             double tolerance) {
        if (Math.abs(calculated - expected) > tolerance)
            fail("Failed to reproduce " + what
                    + ":\n    expected:   " + expected
                    + "\n    calculated: " + calculated
                    + "\n    error:      " + Math.abs(calculated - expected)
                    + "\n    tolerance:  " + tolerance);
    }

    /** Official {@code CommonVars} from test-suite/overnightindexedcoupon.cpp. */
    private static class CommonVars {
        final Date today;
        final double notional = 10000.0;
        final OvernightIndex sofr;
        final RelinkableHandle<YieldTermStructure> forecastCurve =
                new RelinkableHandle<>();

        CommonVars() {
            this(new Date(23, Month.NOVEMBER, 2021));
        }

        CommonVars(Date evaluationDate) {
            today = evaluationDate;

            Settings.instance.evaluationDate().equal(today);

            sofr = new Sofr(forecastCurve);

            List<Date> pastDates = List.of(
                    new Date(21, Month.JUNE, 2019),
                    new Date(24, Month.JUNE, 2019),
                    new Date(25, Month.JUNE, 2019),
                    new Date(26, Month.JUNE, 2019),
                    new Date(27, Month.JUNE, 2019),
                    new Date(28, Month.JUNE, 2019),
                    new Date(1, Month.JULY, 2019),
                    new Date(2, Month.JULY, 2019),
                    new Date(3, Month.JULY, 2019),
                    new Date(5, Month.JULY, 2019),
                    new Date(8, Month.JULY, 2019),
                    new Date(9, Month.JULY, 2019),
                    new Date(10, Month.JULY, 2019),
                    new Date(11, Month.JULY, 2019),
                    new Date(12, Month.JULY, 2019),
                    new Date(15, Month.JULY, 2019),
                    new Date(16, Month.JULY, 2019),
                    new Date(17, Month.JULY, 2019),
                    new Date(18, Month.JULY, 2019),
                    new Date(19, Month.JULY, 2019),
                    new Date(22, Month.JULY, 2019),
                    new Date(23, Month.JULY, 2019),
                    new Date(24, Month.JULY, 2019),
                    new Date(25, Month.JULY, 2019),
                    new Date(26, Month.JULY, 2019),
                    new Date(29, Month.JULY, 2019),
                    new Date(30, Month.JULY, 2019),
                    new Date(31, Month.JULY, 2019),
                    new Date(1, Month.AUGUST, 2019),
                    new Date(2, Month.AUGUST, 2019),
                    new Date(5, Month.AUGUST, 2019),

                    new Date(18, Month.OCTOBER, 2021),
                    new Date(19, Month.OCTOBER, 2021),
                    new Date(20, Month.OCTOBER, 2021),
                    new Date(21, Month.OCTOBER, 2021),
                    new Date(22, Month.OCTOBER, 2021),
                    new Date(25, Month.OCTOBER, 2021),
                    new Date(26, Month.OCTOBER, 2021),
                    new Date(27, Month.OCTOBER, 2021),
                    new Date(28, Month.OCTOBER, 2021),
                    new Date(29, Month.OCTOBER, 2021),
                    new Date(1, Month.NOVEMBER, 2021),
                    new Date(2, Month.NOVEMBER, 2021),
                    new Date(3, Month.NOVEMBER, 2021),
                    new Date(4, Month.NOVEMBER, 2021),
                    new Date(5, Month.NOVEMBER, 2021),
                    new Date(8, Month.NOVEMBER, 2021),
                    new Date(9, Month.NOVEMBER, 2021),
                    new Date(10, Month.NOVEMBER, 2021),
                    new Date(12, Month.NOVEMBER, 2021),
                    new Date(15, Month.NOVEMBER, 2021),
                    new Date(16, Month.NOVEMBER, 2021),
                    new Date(17, Month.NOVEMBER, 2021),
                    new Date(18, Month.NOVEMBER, 2021),
                    new Date(19, Month.NOVEMBER, 2021),
                    new Date(22, Month.NOVEMBER, 2021));

            double[] pastRates = {
                    0.0237, 0.0239, 0.0241,
                    0.0243, 0.0242, 0.025,
                    0.0242, 0.0251, 0.0256,
                    0.0259, 0.0248, 0.0245,
                    0.0246, 0.0241, 0.0236,
                    0.0246, 0.0247, 0.0247,
                    0.0246, 0.0241, 0.024,
                    0.024, 0.0241, 0.0242,
                    0.0241, 0.024, 0.0239,
                    0.0255, 0.0219, 0.0219,
                    0.0213,

                    0.0008, 0.0009, 0.0008,
                    0.0010, 0.0012, 0.0011,
                    0.0013, 0.0012, 0.0012,
                    0.0008, 0.0009, 0.0010,
                    0.0011, 0.0014, 0.0013,
                    0.0011, 0.0009, 0.0008,
                    0.0007, 0.0008, 0.0008,
                    0.0007, 0.0009, 0.0010,
                    0.0009};

            sofr.addFixings(pastDates, toDoubleList(pastRates), false);
        }

        OvernightIndexedCoupon makeCoupon(Date startDate, Date endDate) {
            return new OvernightIndexedCoupon(
                    endDate, notional, startDate, endDate, sofr,
                    1.0, 0.0,
                    new Date(), new Date(),
                    new DayCounter(),
                    false, RateAveraging.Compound);
        }

        /**
         * Extended official {@code makeCoupon} helper. The official
         * fixingDays/lockout/shift arguments are not ported (SOFR fixingDays
         * is already zero), so only telescopic flag, averaging, payment date
         * and notional are exposed here.
         */
        OvernightIndexedCoupon makeCoupon(Date startDate, Date endDate,
                                          boolean telescopicValueDates,
                                          RateAveraging averagingMethod,
                                          Date paymentDate,
                                          double couponNotional) {
            return new OvernightIndexedCoupon(
                    paymentDate, couponNotional, startDate, endDate, sofr,
                    1.0, 0.0,
                    new Date(), new Date(),
                    new DayCounter(),
                    telescopicValueDates, averagingMethod);
        }

        /** Official lookback/lockout/observation-shift helper. */
        OvernightIndexedCoupon makeCoupon(Date startDate, Date endDate,
                                          Integer lookbackDays) {
            return makeCoupon(startDate, endDate, lookbackDays, 0, false);
        }

        OvernightIndexedCoupon makeCoupon(Date startDate, Date endDate,
                                          Integer lookbackDays,
                                          int lockoutDays) {
            return makeCoupon(startDate, endDate, lookbackDays, lockoutDays,
                    false);
        }

        OvernightIndexedCoupon makeCoupon(Date startDate, Date endDate,
                                          Integer lookbackDays,
                                          int lockoutDays,
                                          boolean applyObservationShift) {
            return makeCoupon(startDate, endDate, lookbackDays, lockoutDays,
                    applyObservationShift, false, RateAveraging.Compound);
        }

        OvernightIndexedCoupon makeCoupon(Date startDate, Date endDate,
                                          Integer lookbackDays,
                                          int lockoutDays,
                                          boolean applyObservationShift,
                                          boolean telescopicValueDates) {
            return makeCoupon(startDate, endDate, lookbackDays, lockoutDays,
                    applyObservationShift, telescopicValueDates,
                    RateAveraging.Compound);
        }

        OvernightIndexedCoupon makeCoupon(Date startDate, Date endDate,
                                          Integer lookbackDays,
                                          int lockoutDays,
                                          boolean applyObservationShift,
                                          boolean telescopicValueDates,
                                          RateAveraging averagingMethod) {
            return new OvernightIndexedCoupon(
                    endDate, notional, startDate, endDate, sofr,
                    1.0, 0.0,
                    new Date(), new Date(),
                    new DayCounter(),
                    telescopicValueDates, averagingMethod,
                    lookbackDays, lockoutDays, applyObservationShift);
        }

        /** Official full {@code makeCoupon} overload with an explicit
         *  payment date, notional and coupon day counter. */
        OvernightIndexedCoupon makeCoupon(Date startDate, Date endDate,
                                          Integer lookbackDays,
                                          int lockoutDays,
                                          boolean applyObservationShift,
                                          boolean telescopicValueDates,
                                          RateAveraging averagingMethod,
                                          Date paymentDate,
                                          double couponNotional,
                                          DayCounter dayCounter) {
            return new OvernightIndexedCoupon(
                    paymentDate, couponNotional, startDate, endDate, sofr,
                    1.0, 0.0,
                    new Date(), new Date(),
                    dayCounter,
                    telescopicValueDates, averagingMethod,
                    lookbackDays, lockoutDays, applyObservationShift);
        }
    }

    private static List<Double> toDoubleList(double[] values) {
        return java.util.Arrays.stream(values).boxed().toList();
    }

    /**
     * Official {@code QL_CHECK_CLOSE}/BOOST_CHECK_CLOSE semantics: the
     * tolerance is a percentage, so the check fails iff
     * {@code 100*|calculated-expected|/|expected| > tolerance}.
     */
    private static void checkClose(String what, double calculated,
                                   double expected, double tolerance) {
        if (expected == 0.0) {
            if (Math.abs(calculated) > tolerance)
                fail("Failed to reproduce " + what
                        + ":\n    expected:   " + expected
                        + "\n    calculated: " + calculated
                        + "\n    tolerance:  " + tolerance);
            return;
        }
        double percentError = 100.0 * Math.abs(calculated - expected)
                / Math.abs(expected);
        if (percentError > tolerance)
            fail("Failed to reproduce " + what
                    + ":\n    expected:   " + expected
                    + "\n    calculated: " + calculated
                    + "\n    percent:    " + percentError
                    + "\n    tolerance:  " + tolerance);
    }

    /**
     * Official {@code CommonVarsONLeg} from
     * test-suite/overnightindexedcoupon.cpp.
     *
     * <p>The official {@code makeLeg} always calls {@code withLockoutDays},
     * {@code withObservationShift} and {@code withTelescopicValueDates}; the
     * first two are not ported in Java, and the defaults used here are 0 and
     * false respectively.
     */
    private static class CommonVarsONLeg {
        final Date today;
        final double notional = 1000000.0;
        final OvernightIndex sofr;
        final RelinkableHandle<YieldTermStructure> forecastCurve =
                new RelinkableHandle<>();
        final Schedule legSchedule;
        final DayCounter dc = new Actual360();

        CommonVarsONLeg() {
            this(new Date(1, Month.JUNE, 2025));
        }

        CommonVarsONLeg(Date evaluationDate) {
            today = evaluationDate;

            Settings.instance.evaluationDate().equal(today);

            sofr = new Sofr(forecastCurve);

            // Quarterly schedule for testing.
            legSchedule = new Schedule(
                    new Date(1, Month.JULY, 2025),
                    new Date(1, Month.JULY, 2026),
                    new Period(3, TimeUnit.MONTHS),
                    new UnitedStates(UnitedStates.Market.GovernmentBond),
                    BusinessDayConvention.MODIFIED_FOLLOWING,
                    BusinessDayConvention.MODIFIED_FOLLOWING,
                    DateGenerationRule.FORWARD,
                    false,
                    new Date(), new Date());

            List<Date> pastDates = List.of(
                    new Date(2, Month.JUNE, 2025),
                    new Date(3, Month.JUNE, 2025),
                    new Date(4, Month.JUNE, 2025),
                    new Date(5, Month.JUNE, 2025),
                    new Date(6, Month.JUNE, 2025),
                    new Date(9, Month.JUNE, 2025),
                    new Date(10, Month.JUNE, 2025),
                    new Date(11, Month.JUNE, 2025),
                    new Date(12, Month.JUNE, 2025),
                    new Date(13, Month.JUNE, 2025),
                    new Date(16, Month.JUNE, 2025),
                    new Date(17, Month.JUNE, 2025),
                    new Date(18, Month.JUNE, 2025),
                    new Date(20, Month.JUNE, 2025),
                    new Date(23, Month.JUNE, 2025),
                    new Date(24, Month.JUNE, 2025),
                    new Date(25, Month.JUNE, 2025),
                    new Date(26, Month.JUNE, 2025),
                    new Date(27, Month.JUNE, 2025),
                    new Date(30, Month.JUNE, 2025),
                    new Date(1, Month.JULY, 2025),
                    new Date(2, Month.JULY, 2025),
                    new Date(3, Month.JULY, 2025),
                    new Date(7, Month.JULY, 2025),
                    new Date(8, Month.JULY, 2025),
                    new Date(9, Month.JULY, 2025),
                    new Date(10, Month.JULY, 2025),
                    new Date(11, Month.JULY, 2025),
                    new Date(14, Month.JULY, 2025),
                    new Date(15, Month.JULY, 2025),
                    new Date(16, Month.JULY, 2025),
                    new Date(17, Month.JULY, 2025),
                    new Date(18, Month.JULY, 2025),
                    new Date(21, Month.JULY, 2025),
                    new Date(22, Month.JULY, 2025),
                    new Date(23, Month.JULY, 2025),
                    new Date(24, Month.JULY, 2025),
                    new Date(25, Month.JULY, 2025),
                    new Date(28, Month.JULY, 2025),
                    new Date(29, Month.JULY, 2025),
                    new Date(30, Month.JULY, 2025),
                    new Date(31, Month.JULY, 2025),
                    new Date(1, Month.AUGUST, 2025));

            double[] pastRates = {
                    0.0435, 0.0432, 0.0428, 0.0429, 0.0429, 0.0429, 0.0428,
                    0.0428, 0.0428, 0.0428, 0.0432, 0.0431, 0.0428, 0.0429,
                    0.0429, 0.0430, 0.0436, 0.0440, 0.0439, 0.0445, 0.0444,
                    0.0440, 0.0435, 0.0433, 0.0434, 0.0432, 0.0431, 0.0431,
                    0.0433, 0.0437, 0.0434, 0.0434, 0.0430, 0.0428, 0.0428,
                    0.0428, 0.0430, 0.0436, 0.0436, 0.0436, 0.0432, 0.0439,
                    0.0434};

            sofr.addFixings(pastDates, toDoubleList(pastRates), false);
        }

        void setupForecastCurve() {
            List<Date> curveDates = List.of(
                    today,
                    new Date(30, Month.JULY, 2025),
                    new Date(29, Month.AUGUST, 2025),
                    new Date(30, Month.SEPTEMBER, 2025),
                    new Date(30, Month.DECEMBER, 2025),
                    new Date(30, Month.MARCH, 2026),
                    new Date(30, Month.JUNE, 2026));

            double[] zeroRates = {
                    0.0434, 0.0436, 0.0431, 0.0413, 0.0390, 0.0370, 0.0348};

            // Official: InterpolatedZeroCurve<Cubic> with the default
            // Kruger / non-monotonic / second-derivative-0.0 interpolator.
            CubicZeroCurve zeroCurve = new CubicZeroCurve(curveDates, zeroRates, dc,
                    new UnitedStates(UnitedStates.Market.SOFR));
            zeroCurve.enableExtrapolation();
            forecastCurve.linkTo(zeroCurve, true);
        }

        List<CashFlow> makeLeg(List<Double> gearings, List<Double> spreads) {
            return makeLeg(null, 0, false, false, RateAveraging.Compound,
                    gearings, spreads);
        }

        List<CashFlow> makeLeg(Integer fixingDays) {
            return makeLeg(fixingDays, 0, false, false, RateAveraging.Compound,
                    List.of(), List.of());
        }

        List<CashFlow> makeLeg(Integer fixingDays, int lockoutDays) {
            return makeLeg(fixingDays, lockoutDays, false, false,
                    RateAveraging.Compound, List.of(), List.of());
        }

        List<CashFlow> makeLeg(Integer fixingDays, int lockoutDays,
                               boolean applyObservationShift) {
            return makeLeg(fixingDays, lockoutDays, applyObservationShift,
                    false, RateAveraging.Compound, List.of(), List.of());
        }

        List<CashFlow> makeLeg(Integer fixingDays, int lockoutDays,
                               boolean applyObservationShift,
                               boolean telescopicValueDates,
                               RateAveraging averaging) {
            return makeLeg(fixingDays, lockoutDays, applyObservationShift,
                    telescopicValueDates, averaging, List.of(), List.of());
        }

        List<CashFlow> makeLeg(Integer fixingDays, int lockoutDays,
                               boolean applyObservationShift,
                               boolean telescopicValueDates,
                               RateAveraging averaging,
                               List<Double> gearings, List<Double> spreads) {
            OvernightLeg leg = new OvernightLeg(legSchedule, sofr);
            // Official makeLeg always calls withLockoutDays and
            // withObservationShift, even at their defaults.
            leg.withNotionals(notional)
                    .withPaymentDayCounter(dc)
                    .withAveragingMethod(averaging)
                    .withLockoutDays(lockoutDays)
                    .withObservationShift(applyObservationShift)
                    .withTelescopicValueDates(telescopicValueDates);

            if (fixingDays != null)
                leg.withLookbackDays(fixingDays);
            if (!gearings.isEmpty())
                leg.withGearings(gearings);
            if (!spreads.isEmpty())
                leg.withSpreads(spreads);

            // OvernightLeg has no implicit conversion to a leg in Java.
            return leg.build();
        }
    }

    private static void linkFlatCurve(CommonVars vars) {
        vars.forecastCurve.linkTo(new FlatForward(0, new NullCalendar(), 0.0010,
                new Actual360()), true);
    }

    @Test
    public void testPastCouponRate() {
        CommonVars vars = new CommonVars();

        // coupon entirely in the past
        OvernightIndexedCoupon pastCoupon = vars.makeCoupon(
                new Date(18, Month.OCTOBER, 2021),
                new Date(18, Month.NOVEMBER, 2021));

        double expectedRate = 0.000987136104;
        double expectedAmount = vars.notional * expectedRate * 31.0 / 360.0;
        checkOisCouponResult("coupon rate", pastCoupon.rate(), expectedRate,
                1e-12);
        checkOisCouponResult("coupon amount", pastCoupon.amount(),
                expectedAmount, 1e-8);
    }

    @Test
    public void testCurrentCouponRate() {
        CommonVars vars = new CommonVars();
        linkFlatCurve(vars);

        // coupon partly in the past, today not fixed
        OvernightIndexedCoupon currentCoupon = vars.makeCoupon(
                new Date(10, Month.NOVEMBER, 2021),
                new Date(10, Month.DECEMBER, 2021));

        double expectedRate = 0.000926701551;
        double expectedAmount = vars.notional * expectedRate * 30.0 / 360.0;
        checkOisCouponResult("coupon rate", currentCoupon.rate(), expectedRate,
                1e-12);
        checkOisCouponResult("coupon amount", currentCoupon.amount(),
                expectedAmount, 1e-8);

        // coupon partly in the past, today fixed
        vars.sofr.addFixing(new Date(23, Month.NOVEMBER, 2021), 0.0007, false);

        expectedRate = 0.000916700760;
        expectedAmount = vars.notional * expectedRate * 30.0 / 360.0;
        checkOisCouponResult("coupon rate", currentCoupon.rate(), expectedRate,
                1e-12);
        checkOisCouponResult("coupon amount", currentCoupon.amount(),
                expectedAmount, 1e-8);
    }

    @Test
    public void testFutureCouponRate() {
        CommonVars vars = new CommonVars();
        linkFlatCurve(vars);

        // coupon entirely in the future
        OvernightIndexedCoupon futureCoupon = vars.makeCoupon(
                new Date(10, Month.DECEMBER, 2021),
                new Date(10, Month.JANUARY, 2022));

        double expectedRate = 0.001000043057;
        double expectedAmount = vars.notional * expectedRate * 31.0 / 360.0;
        checkOisCouponResult("coupon rate", futureCoupon.rate(), expectedRate,
                1e-12);
        checkOisCouponResult("coupon amount", futureCoupon.amount(),
                expectedAmount, 1e-8);
    }

    @Test
    public void testRateWhenTodayIsHoliday() {
        CommonVars vars = new CommonVars();

        Settings.instance.evaluationDate().equal(
                new Date(20, Month.NOVEMBER, 2021));
        linkFlatCurve(vars);

        OvernightIndexedCoupon coupon = vars.makeCoupon(
                new Date(10, Month.NOVEMBER, 2021),
                new Date(10, Month.DECEMBER, 2021));

        double expectedRate = 0.000930035180;
        double expectedAmount = vars.notional * expectedRate * 30.0 / 360.0;
        checkOisCouponResult("coupon rate", coupon.rate(), expectedRate, 1e-12);
        checkOisCouponResult("coupon amount", coupon.amount(), expectedAmount,
                1e-8);
    }

    @Test
    public void testAccruedAmountInThePast() {
        CommonVars vars = new CommonVars();

        OvernightIndexedCoupon coupon = vars.makeCoupon(
                new Date(18, Month.OCTOBER, 2021),
                new Date(18, Month.JANUARY, 2022));

        double expectedAmount =
                vars.notional * 0.000987136104 * 31.0 / 360.0;
        checkOisCouponResult("coupon amount",
                coupon.accruedAmount(new Date(18, Month.NOVEMBER, 2021)),
                expectedAmount, 1e-8);
    }

    @Test
    public void testAccruedAmountSpanningToday() {
        CommonVars vars = new CommonVars();
        linkFlatCurve(vars);

        // coupon partly in the past, today not fixed
        OvernightIndexedCoupon coupon = vars.makeCoupon(
                new Date(10, Month.NOVEMBER, 2021),
                new Date(10, Month.JANUARY, 2022));

        double expectedAmount =
                vars.notional * 0.000926701551 * 30.0 / 360.0;
        checkOisCouponResult("coupon amount",
                coupon.accruedAmount(new Date(10, Month.DECEMBER, 2021)),
                expectedAmount, 1e-8);

        // coupon partly in the past, today fixed
        vars.sofr.addFixing(new Date(23, Month.NOVEMBER, 2021), 0.0007, false);

        expectedAmount = vars.notional * 0.000916700760 * 30.0 / 360.0;
        checkOisCouponResult("coupon amount",
                coupon.accruedAmount(new Date(10, Month.DECEMBER, 2021)),
                expectedAmount, 1e-8);
    }

    @Test
    public void testAccruedAmountInTheFuture() {
        CommonVars vars = new CommonVars();
        linkFlatCurve(vars);

        // coupon entirely in the future
        OvernightIndexedCoupon coupon = vars.makeCoupon(
                new Date(10, Month.DECEMBER, 2021),
                new Date(10, Month.MARCH, 2022));

        Date accrualDate = new Date(10, Month.JANUARY, 2022);
        double expectedRate = 0.001000043057;
        double expectedAmount = vars.notional * expectedRate * 31.0 / 360.0;
        checkOisCouponResult("coupon amount",
                coupon.accruedAmount(accrualDate), expectedAmount, 1e-8);
    }

    @Test
    public void testAccruedAmountOnPastHoliday() {
        CommonVars vars = new CommonVars();

        // coupon entirely in the past
        OvernightIndexedCoupon coupon = vars.makeCoupon(
                new Date(18, Month.OCTOBER, 2021),
                new Date(18, Month.JANUARY, 2022));

        Date accrualDate = new Date(13, Month.NOVEMBER, 2021);
        double expectedAmount = vars.notional * 0.000074724810;
        checkOisCouponResult("coupon amount",
                coupon.accruedAmount(accrualDate), expectedAmount, 1e-8);
    }

    @Test
    public void testAccruedAmountOnFutureHoliday() {
        CommonVars vars = new CommonVars();
        linkFlatCurve(vars);

        // coupon entirely in the future
        OvernightIndexedCoupon coupon = vars.makeCoupon(
                new Date(10, Month.DECEMBER, 2021),
                new Date(10, Month.MARCH, 2022));

        Date accrualDate = new Date(15, Month.JANUARY, 2022);
        double expectedAmount = vars.notional * 0.000100005012;
        checkOisCouponResult("coupon amount",
                coupon.accruedAmount(accrualDate), expectedAmount, 1e-8);
    }

    @Test
    public void testOvernightLegBasicFunctionality() {
        CommonVarsONLeg vars = new CommonVarsONLeg();
        vars.forecastCurve.linkTo(new FlatForward(0, new NullCalendar(), 0.0010,
                new Actual360()), true);

        List<CashFlow> leg = vars.makeLeg(List.of(), List.of());

        // Official comment says "monthly over 1 year = 12 coupons" but the
        // official assertion is 4 (the schedule is quarterly).
        assertEquals(4, leg.size());

        for (CashFlow cf : leg) {
            assertTrue(cf instanceof OvernightIndexedCoupon);
            OvernightIndexedCoupon coupon = (OvernightIndexedCoupon) cf;
            assertEquals(vars.notional, coupon.nominal(), 0.0);
            assertEquals(RateAveraging.Compound, coupon.averagingMethod());
            assertEquals(0, coupon.lockoutDays());
            assertFalse(coupon.applyObservationShift());
        }
    }

    @Test
    public void testOvernightLegWithGearingsAndSpreads() {
        CommonVarsONLeg vars = new CommonVarsONLeg();
        vars.setupForecastCurve();

        List<Double> gearings = List.of(1.0, 1.25, 2.0, 0.5);
        List<Double> spreads = List.of(0.0001, 0.0001, 0.0002, 0.0002);

        List<CashFlow> leg = vars.makeLeg(gearings, spreads);

        assertEquals(4, leg.size());

        for (int i = 0; i < leg.size(); ++i) {
            assertTrue(leg.get(i) instanceof OvernightIndexedCoupon);
            OvernightIndexedCoupon coupon =
                    (OvernightIndexedCoupon) leg.get(i);
            checkClose("gearing", coupon.gearing(), gearings.get(i), 1e-12);
            checkClose("spread", coupon.spread(), spreads.get(i), 1e-12);
        }
    }

    @Test
    public void testOvernightLegWeekendStub() {
        CommonVars vars = new CommonVars(new Date(16, Month.APRIL, 2025));
        vars.forecastCurve.linkTo(new FlatForward(0, new NullCalendar(), 0.04,
                new Actual360()), true);

        Schedule schedule = new Schedule(List.of(
                new Date(27, Month.MARCH, 2026), // Friday
                new Date(28, Month.MARCH, 2026), // Saturday
                new Date(30, Month.MARCH, 2026))); // Monday

        double notional = 10000000.0;
        List<CashFlow> leg = new OvernightLeg(schedule, vars.sofr)
                .withNotionals(notional)
                .build();
        assertEquals(2, leg.size());

        double oneDayInterest = vars.sofr
                .fixing(new Date(27, Month.MARCH, 2026), false)
                / 360.0 * notional;

        Date[] valueDates = {
                new Date(27, Month.MARCH, 2026),
                new Date(28, Month.MARCH, 2026),
                new Date(29, Month.MARCH, 2026),
                new Date(30, Month.MARCH, 2026),
                new Date(31, Month.MARCH, 2026)};
        double[] expectedAmounts = {
                0.0,
                oneDayInterest,
                oneDayInterest,
                2.0 * oneDayInterest,
                0.0};

        for (int i = 0; i < valueDates.length; ++i) {
            checkOisCouponResult("leg accruedAmount",
                    CashFlows.accruedAmount(leg, true, valueDates[i]),
                    expectedAmounts[i], 1e-8);
        }
    }

    @Test
    public void testOvernightIndexedCouponPaymentBeforeAccrualEnd() {
        Date accrualStart = new Date(18, Month.SEPTEMBER, 2025);
        Settings.instance.evaluationDate().equal(accrualStart);

        Handle<YieldTermStructure> h = new Handle<>(
                new FlatForward(accrualStart, 0.05, new Actual365Fixed()), true);
        OvernightIndex estr = new Estr(h);

        Calendar cal = new WeekendsOnly();
        Date accrualEnd = cal.advance(accrualStart,
                new Period(6, TimeUnit.MONTHS));
        Date paymentDate = cal.advance(accrualEnd,
                new Period(-1, TimeUnit.DAYS));

        assertThrows(IllegalArgumentException.class,
                () -> new OvernightIndexedCoupon(paymentDate, 1.0,
                        accrualStart, accrualEnd, estr));
    }

    @Test
    public void testInterestCalculatedAccrualDateFixingHoliday() {
        CommonVars vars = new CommonVars(new Date(19, Month.APRIL, 2023));
        OvernightIndex sofr = vars.sofr;
        Calendar fixingCal = sofr.fixingCalendar();
        vars.forecastCurve.linkTo(new FlatForward(vars.today, 0.0432,
                new Actual360()), true);

        Schedule sch = new MakeSchedule()
                .from(new Date(15, Month.OCTOBER, 2021))
                .to(new Date(14, Month.APRIL, 2023))
                .withTenor(new Period(1, TimeUnit.DAYS))
                .withCalendar(fixingCal)
                .withConvention(sofr.businessDayConvention())
                .forwards()
                .toSchedule();

        List<Date> pastDates = sch.dates();
        List<Double> pastRates = new ArrayList<>(pastDates.size());
        for (int i = 0; i < pastDates.size(); ++i)
            pastRates.add(0.048);

        // Overwrite the fixings provided within CommonVars to cover interest
        // allocation to coupons which start/end on Good Friday, the only
        // holiday in the SOFR fixing calendar but not in the Federal Reserve
        // calendar used to generate SOFR swap coupons.
        sofr.clearFixings();
        sofr.addFixings(pastDates, pastRates, false);

        DayCounter dc = sofr.dayCounter();

        Date[][] cases = {
                // fixed
                {new Date(15, Month.OCTOBER, 2021),
                        new Date(17, Month.APRIL, 2023),
                        new Date(15, Month.APRIL, 2022)},
                // forward
                {new Date(18, Month.OCTOBER, 2024),
                        new Date(20, Month.APRIL, 2026),
                        new Date(18, Month.APRIL, 2025)}};

        for (Date[] tc : cases) {
            Date startDate = tc[0];
            Date endDate = tc[1];
            Date splitAt = tc[2];

            assertTrue(fixingCal.isHoliday(splitAt));

            OvernightIndexedCoupon cpnTotal = new OvernightIndexedCoupon(
                    fixingCal.advance(endDate, 2, TimeUnit.DAYS),
                    10000000.0, startDate, endDate, sofr);
            OvernightIndexedCoupon cpnLeft = new OvernightIndexedCoupon(
                    fixingCal.advance(splitAt, 2, TimeUnit.DAYS),
                    10000000.0, startDate, splitAt, sofr);
            OvernightIndexedCoupon cpnRight = new OvernightIndexedCoupon(
                    cpnTotal.date(), 10000000.0, splitAt, endDate, sofr);

            double compounded =
                    (1.0 + cpnLeft.rate() * cpnLeft.accrualPeriod())
                    * (1.0 + cpnRight.rate() * cpnRight.accrualPeriod());
            double total =
                    1.0 + cpnTotal.rate() * cpnTotal.accrualPeriod();

            Date valueDate = fixingCal.adjust(splitAt,
                    BusinessDayConvention.PRECEDING);
            Date maturityDate = fixingCal.adjust(splitAt,
                    BusinessDayConvention.FOLLOWING);
            double fixing = sofr.fixing(valueDate, false);

            // Check that interest is split across coupons as expected.
            double adjustment =
                    (1.0 + fixing * dc.yearFraction(valueDate, maturityDate,
                            new Date(), new Date()))
                    / (1.0 + fixing * dc.yearFraction(valueDate, splitAt,
                            new Date(), new Date()))
                    / (1.0 + fixing * dc.yearFraction(splitAt, maturityDate,
                            new Date(), new Date()));

            checkOisCouponResult("compound factor", compounded * adjustment,
                    total, 1e-12);
        }
    }

    @Test
    public void testErrorWhenCurveNullOrTooNarrow() {
        CommonVars vars = new CommonVars(new Date(26, Month.MARCH, 2026));

        OvernightIndexedCoupon coupon = vars.makeCoupon(
                new Date(31, Month.MARCH, 2026),
                new Date(31, Month.MARCH, 2027),
                true, RateAveraging.Compound,
                new Date(2, Month.APRIL, 2027), 1.0);

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class, coupon::rate);
        assertTrue(exception.getMessage()
                        .contains("null term structure set to this instance"),
                exception.getMessage());

        vars.forecastCurve.linkTo(getCurve(vars.today,
                coupon.accrualEndDate().substract(1), false), true);
        exception = assertThrows(IllegalArgumentException.class,
                coupon::rate);
        assertTrue(exception.getMessage().contains("coupon requires a range"),
                exception.getMessage());

        vars.forecastCurve.linkTo(getCurve(vars.today,
                coupon.accrualEndDate(), false), true);
        coupon.rate();

        vars.forecastCurve.linkTo(getCurve(vars.today,
                coupon.accrualEndDate().substract(1), true), true);
        coupon.rate();
    }

    private static YieldTermStructure getCurve(Date today, Date endDate,
                                               boolean extrapolate) {
        DiscountCurve curve = new DiscountCurve(
                List.of(today, endDate), new double[]{1.0, 0.9},
                new Actual360());
        if (extrapolate)
            curve.enableExtrapolation();
        return curve;
    }

    @Test
    public void testPastCouponRateWithLookback() {
        CommonVars vars = new CommonVars();

        // Coupon entirely in the past with lookback period.
        OvernightIndexedCoupon pastCoupon = vars.makeCoupon(
                new Date(1, Month.JULY, 2019),
                new Date(15, Month.JULY, 2019), 5);

        double expectedRate = 0.024781644454;
        checkOisCouponResult("coupon rate", pastCoupon.rate(), expectedRate,
                1e-12);
    }

    @Test
    public void testPastCouponRateWithLookbackAndObservationShift() {
        CommonVars vars = new CommonVars();

        // Coupon entirely in the past with lookback and observation shift.
        OvernightIndexedCoupon pastCoupon = vars.makeCoupon(
                new Date(1, Month.JULY, 2019),
                new Date(31, Month.JULY, 2019), 5, 0, true);

        double expectedRate = 0.024603611707;
        checkOisCouponResult("coupon rate", pastCoupon.rate(), expectedRate,
                1e-12);
    }

    @Test
    public void testPastCouponRateWithLockout() {
        CommonVars vars = new CommonVars();

        OvernightIndexedCoupon couponWithLockout = vars.makeCoupon(
                new Date(1, Month.JULY, 2019),
                new Date(31, Month.JULY, 2019), null, 3);
        List<Date> fixingDates = couponWithLockout.fixingDates();
        int n = fixingDates.size();

        Date expectedLockoutDate = new Date(25, Month.JULY, 2019);
        assertTrue(TimeUtils.equals(fixingDates.get(n - 4), expectedLockoutDate));
        assertTrue(TimeUtils.equals(fixingDates.get(n - 3), expectedLockoutDate));
        assertTrue(TimeUtils.equals(fixingDates.get(n - 2), expectedLockoutDate));
        assertTrue(TimeUtils.equals(fixingDates.get(n - 1), expectedLockoutDate));
    }

    @Test
    public void testPastCouponRateWithLookbackObservationShiftAndLockout() {
        CommonVars vars = new CommonVars();

        // Coupon entirely in the past with lookback, observation shift and
        // lockout.
        OvernightIndexedCoupon pastCoupon = vars.makeCoupon(
                new Date(1, Month.JULY, 2019),
                new Date(31, Month.JULY, 2019), 5, 3, true);

        double expectedRate = 0.024693783702;
        checkOisCouponResult("coupon rate", pastCoupon.rate(), expectedRate,
                1e-12);
    }

    @Test
    public void testIncorrectNumberOfLockoutDays() {
        CommonVars vars = new CommonVars();

        OvernightIndexedCoupon couponWithoutLockout = vars.makeCoupon(
                new Date(1, Month.JULY, 2019),
                new Date(31, Month.JULY, 2019));
        int numberOfFixings = couponWithoutLockout.fixingDates().size();

        // Lockout days equal to the number of daily fixings.
        assertThrows(IllegalArgumentException.class,
                () -> vars.makeCoupon(new Date(1, Month.JULY, 2019),
                        new Date(31, Month.JULY, 2019), null,
                        numberOfFixings));

        // Negative number of lockout days.
        assertThrows(IllegalArgumentException.class,
                () -> vars.makeCoupon(new Date(1, Month.JULY, 2019),
                        new Date(31, Month.JULY, 2019), null, -1));
    }

    @Test
    public void testFutureCouponRateWithLookback() {
        CommonVars vars = new CommonVars(new Date(12, Month.MARCH, 2019));

        vars.forecastCurve.linkTo(new FlatForward(0, new NullCalendar(), 0.0250,
                new Actual360()), true);

        OvernightIndexedCoupon coupon8July = vars.makeCoupon(
                new Date(1, Month.JULY, 2019),
                new Date(8, Month.JULY, 2019), 5, 0, false);
        double expectedRate8July = 0.0250050849311315;
        checkOisCouponResult("coupon rate", coupon8July.rate(),
                expectedRate8July, 1e-12);

        OvernightIndexedCoupon coupon15July = vars.makeCoupon(
                new Date(1, Month.JULY, 2019),
                new Date(15, Month.JULY, 2019), 5, 0, false);
        double expectedRate15July = 0.0250118464503275;
        checkOisCouponResult("coupon rate", coupon15July.rate(),
                expectedRate15July, 1e-12);
    }

    @Test
    public void testFutureCouponRateWithLookbackAndObservationShift() {
        CommonVars vars = new CommonVars(new Date(12, Month.MARCH, 2019));

        vars.forecastCurve.linkTo(new FlatForward(0, new NullCalendar(), 0.0250,
                new Actual360()), true);

        OvernightIndexedCoupon futureCoupon = vars.makeCoupon(
                new Date(1, Month.JULY, 2019),
                new Date(8, Month.JULY, 2019), 5, 0, true);
        double expectedRate = 0.025003472543756455;
        checkOisCouponResult("coupon rate", futureCoupon.rate(),
                expectedRate, 1e-12);
    }

    @Test
    public void testFutureCouponRateWithLookout() {
        CommonVars vars = new CommonVars(new Date(12, Month.MARCH, 2019));

        vars.forecastCurve.linkTo(new FlatForward(0, new NullCalendar(), 0.0250,
                new Actual360()), true);

        OvernightIndexedCoupon coupon15July = vars.makeCoupon(
                new Date(1, Month.JULY, 2019),
                new Date(15, Month.JULY, 2019), null, 2, false);

        double lockoutFixing = vars.sofr.fixing(
                new Date(10, Month.JULY, 2019), false);
        double expectedRate15July =
                (vars.forecastCurve.currentLink()
                        .discount(new Date(1, Month.JULY, 2019))
                        / vars.forecastCurve.currentLink()
                        .discount(new Date(11, Month.JULY, 2019))
                        * (1.0 + 1.0 / 360.0 * lockoutFixing)
                        * (1.0 + 3.0 / 360.0 * lockoutFixing)
                        - 1.0) * 360.0 / 14.0;

        checkOisCouponResult("coupon rate", coupon15July.rate(),
                expectedRate15July, 1e-12);
    }

    @Test
    public void testPartiallyAccruedAmountOfFutureCouponWithLookout() {
        CommonVars vars = new CommonVars(new Date(12, Month.MARCH, 2019));

        vars.forecastCurve.linkTo(new FlatForward(0, new NullCalendar(), 0.0250,
                new Actual360()), true);

        OvernightIndexedCoupon coupon15July = vars.makeCoupon(
                new Date(1, Month.JULY, 2019),
                new Date(15, Month.JULY, 2019), null, 2, false);

        double lockoutFixing = vars.sofr.fixing(
                new Date(10, Month.JULY, 2019), false);
        double expectedRate15July =
                (vars.forecastCurve.currentLink()
                        .discount(new Date(1, Month.JULY, 2019))
                        / vars.forecastCurve.currentLink()
                        .discount(new Date(11, Month.JULY, 2019))
                        * (1.0 + 1.0 / 360.0 * lockoutFixing)
                        * (1.0 + 2.0 / 360.0 * lockoutFixing)
                        - 1.0) * 360.0 / 13.0;

        Date accrualDate = new Date(14, Month.JULY, 2019);
        double expectedAccruedAmount = coupon15July.nominal()
                * coupon15July.accruedPeriod(accrualDate)
                * expectedRate15July;
        checkOisCouponResult("accrued amount",
                coupon15July.accruedAmount(accrualDate),
                expectedAccruedAmount, 1e-12);
    }

    @Test
    public void testTelescopicFormulaWhenLookbackWithObservationShiftAndNoIndexFixingDelay() {
        CommonVars vars = new CommonVars(new Date(12, Month.MARCH, 2019));

        vars.forecastCurve.linkTo(new FlatForward(0, new NullCalendar(), 0.0250,
                new Actual360()), true);

        OvernightIndexedCoupon coupon15July = vars.makeCoupon(
                new Date(1, Month.JULY, 2019),
                new Date(15, Month.JULY, 2019), 3, 0, true);

        double actualRate = coupon15July.rate();

        OvernightIndexedCoupon coupon15JulyWithTelescopicDates = vars.makeCoupon(
                new Date(1, Month.JULY, 2019),
                new Date(15, Month.JULY, 2019), 3, 0, true, true);

        checkOisCouponResult("telescopic value dates coupon rate",
                actualRate, coupon15JulyWithTelescopicDates.rate(), 1e-12);

        double expectedRateTelescopicSeries =
                (vars.forecastCurve.currentLink()
                        .discount(new Date(26, Month.JUNE, 2019))
                        / vars.forecastCurve.currentLink()
                        .discount(new Date(10, Month.JULY, 2019))
                        - 1.0) * 360.0 / 14.0;

        checkOisCouponResult("coupon rate using telescopic formula",
                actualRate, expectedRateTelescopicSeries, 1e-12);

        List<Date> fixingDates = coupon15July.fixingDates();
        double[] dts = coupon15July.dt();
        int n = fixingDates.size();

        double expectedRateIterativeFormula = 1.0;
        for (int i = 0; i < n; ++i) {
            expectedRateIterativeFormula *= 1.0 + dts[i]
                    * coupon15July.index().fixing(fixingDates.get(i), false);
        }
        expectedRateIterativeFormula -= 1.0;
        expectedRateIterativeFormula /= coupon15July.accrualPeriod();

        checkOisCouponResult("coupon rate using iterative formula",
                actualRate, expectedRateIterativeFormula, 1e-12);
    }

    @Test
    public void testErrorWhenTelescopicValueDatesEnforcedWithLookback() {
        CommonVars vars = new CommonVars();

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> vars.makeCoupon(new Date(1, Month.JULY, 2019),
                        new Date(31, Month.JULY, 2019), 2, 0, false, true));
        assertTrue(exception.getMessage()
                        .contains("Telescopic formula cannot be applied for a coupon with lookback."),
                exception.getMessage());
    }

    @Test
    public void testErrorWhenLookbackOrLockoutAppliedForSimpleAveraging() {
        CommonVars vars = new CommonVars();

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> vars.makeCoupon(new Date(1, Month.JULY, 2019),
                        new Date(31, Month.JULY, 2019), 2, 0, false, false,
                        RateAveraging.Simple));
        assertTrue(exception.getMessage().contains(
                        "Cannot price an overnight coupon with simple averaging with lookback or lockout."),
                exception.getMessage());

        exception = assertThrows(IllegalArgumentException.class,
                () -> vars.makeCoupon(new Date(1, Month.JULY, 2019),
                        new Date(31, Month.JULY, 2019), null, 2, false, false,
                        RateAveraging.Simple));
        assertTrue(exception.getMessage().contains(
                        "Cannot price an overnight coupon with simple averaging with lookback or lockout."),
                exception.getMessage());

        exception = assertThrows(IllegalArgumentException.class,
                () -> vars.makeCoupon(new Date(1, Month.JULY, 2019),
                        new Date(31, Month.JULY, 2019), null, 0, true, false,
                        RateAveraging.Simple));
        assertTrue(exception.getMessage().contains(
                        "Cannot price an overnight coupon with simple averaging with lookback or lockout."),
                exception.getMessage());
    }

    @Test
    public void testOvernightLegWithLookback() {
        CommonVarsONLeg vars = new CommonVarsONLeg();
        vars.forecastCurve.linkTo(new FlatForward(0, new NullCalendar(), 0.0010,
                new Actual360()), true);

        int lookbackDays = 5;
        List<CashFlow> leg = vars.makeLeg(lookbackDays);

        for (CashFlow cf : leg) {
            assertTrue(cf instanceof OvernightIndexedCoupon);
            OvernightIndexedCoupon oisCoupon = (OvernightIndexedCoupon) cf;
            // Official check keeps the disjunction even though the Java
            // implementation resolves the lookback to five days.
            assertTrue(oisCoupon.fixingDays() == lookbackDays
                            || oisCoupon.fixingDays() == oisCoupon.index().fixingDays(),
                    "fixingDays: " + oisCoupon.fixingDays());
        }
    }

    @Test
    public void testOvernightLegWithLockout() {
        CommonVarsONLeg vars = new CommonVarsONLeg();
        vars.forecastCurve.linkTo(new FlatForward(0, new NullCalendar(), 0.0010,
                new Actual360()), true);

        int lockoutDays = 3;
        List<CashFlow> leg = vars.makeLeg(null, lockoutDays);

        for (CashFlow cf : leg) {
            assertTrue(cf instanceof OvernightIndexedCoupon);
            assertEquals(lockoutDays,
                    ((OvernightIndexedCoupon) cf).lockoutDays());
        }
    }

    @Test
    public void testOvernightLegWithObservationShift() {
        CommonVarsONLeg vars = new CommonVarsONLeg();
        vars.forecastCurve.linkTo(new FlatForward(0, new NullCalendar(), 0.0010,
                new Actual360()), true);

        List<CashFlow> leg = vars.makeLeg(null, 0, true);

        for (CashFlow cf : leg) {
            assertTrue(cf instanceof OvernightIndexedCoupon);
            assertTrue(((OvernightIndexedCoupon) cf).applyObservationShift());
        }
    }

    @Test
    public void testOvernightLegErrorConditions() {
        CommonVarsONLeg vars = new CommonVarsONLeg();
        vars.forecastCurve.linkTo(new FlatForward(0, new NullCalendar(), 0.0010,
                new Actual360()), true);

        // Lookback with simple averaging.
        assertThrows(IllegalArgumentException.class,
                () -> vars.makeLeg(5, 0, false, false, RateAveraging.Simple));

        // Lockout with simple averaging.
        assertThrows(IllegalArgumentException.class,
                () -> vars.makeLeg(null, 3, false, false,
                        RateAveraging.Simple));

        // Observation shift with simple averaging.
        assertThrows(IllegalArgumentException.class,
                () -> vars.makeLeg(null, 0, true, false,
                        RateAveraging.Simple));
    }

    @Test
    public void testOvernightLegNPV() {
        CommonVarsONLeg vars = new CommonVarsONLeg();
        vars.setupForecastCurve();

        // Null<Natural>() lookback, 3 lockout days, no observation shift,
        // telescopic value dates, compounded averaging.
        List<CashFlow> leg = vars.makeLeg(null, 3, false, true,
                RateAveraging.Compound);

        YieldTermStructure discountCurve = new FlatForward(0,
                new NullCalendar(), 0.0015, new Actual360());

        double expectedNpv = 34883.949669756257;
        double npv = 0.0;
        for (CashFlow cf : leg) {
            npv += cf.amount() * discountCurve.discount(cf.date());
        }

        System.out.println("OvernightLeg NPV: " + npv);
        checkOisCouponResult("OvernightLeg NPV", npv, expectedNpv, 1e-8);
    }

    private record Accrual(Date date, double amount) {
    }

    private record TestCase(Date startDate, Date endDate,
                            Integer lookbackDays, boolean obsShift,
                            int lockoutDays, DayCounter dayCounter,
                            Date telescopicLowerCut, Date telescopicUpperCut,
                            List<Accrual> accruals) {
    }

    private static Accrual accrual(int day, Month month, int year, double amount) {
        return new Accrual(new Date(day, month, year), amount);
    }

    private static void checkAccruals(OvernightIndexedCoupon cpn,
                                      List<Accrual> accruals,
                                      Date telescopicLowerCut,
                                      Date telescopicUpperCut) {
        for (Accrual accrual : accruals) {
            if (TimeUtils.less(accrual.date(), telescopicLowerCut)
                    || TimeUtils.geq(accrual.date(), telescopicUpperCut)) {
                checkOisCouponResult("accrual amount",
                        cpn.accruedAmount(accrual.date()),
                        accrual.amount(), 1e-8);
            }
        }
    }

    @Test
    public void testInterestCalculatedAccrualDateFixingHolidayAccruals() {
        CommonVars vars = new CommonVars(new Date(29, Month.MAY, 2025));
        vars.forecastCurve.linkTo(new FlatForward(0, new NullCalendar(),
                0.0433, new Actual360()), true);

        List<Date> pastDates = List.of(
                new Date(10, Month.OCTOBER, 2024),
                new Date(11, Month.OCTOBER, 2024),
                new Date(15, Month.OCTOBER, 2024),
                new Date(16, Month.OCTOBER, 2024),
                new Date(17, Month.OCTOBER, 2024),
                new Date(18, Month.OCTOBER, 2024),
                new Date(21, Month.OCTOBER, 2024),
                new Date(22, Month.OCTOBER, 2024),
                new Date(23, Month.OCTOBER, 2024),
                new Date(24, Month.OCTOBER, 2024),
                new Date(25, Month.OCTOBER, 2024),
                new Date(28, Month.OCTOBER, 2024),
                new Date(29, Month.OCTOBER, 2024),
                new Date(30, Month.OCTOBER, 2024),
                new Date(31, Month.OCTOBER, 2024),
                new Date(1, Month.NOVEMBER, 2024),
                new Date(4, Month.NOVEMBER, 2024),
                new Date(5, Month.NOVEMBER, 2024),
                new Date(6, Month.NOVEMBER, 2024),
                new Date(7, Month.NOVEMBER, 2024),
                new Date(8, Month.NOVEMBER, 2024),
                new Date(12, Month.NOVEMBER, 2024),
                new Date(13, Month.NOVEMBER, 2024),
                new Date(14, Month.NOVEMBER, 2024),
                new Date(15, Month.NOVEMBER, 2024),
                new Date(18, Month.NOVEMBER, 2024),
                new Date(19, Month.NOVEMBER, 2024),
                new Date(20, Month.NOVEMBER, 2024),
                new Date(21, Month.NOVEMBER, 2024),
                new Date(22, Month.NOVEMBER, 2024),
                new Date(25, Month.NOVEMBER, 2024),
                new Date(26, Month.NOVEMBER, 2024),
                new Date(27, Month.NOVEMBER, 2024),
                new Date(29, Month.NOVEMBER, 2024),
                new Date(2, Month.DECEMBER, 2024),
                new Date(3, Month.DECEMBER, 2024),
                new Date(4, Month.DECEMBER, 2024),
                new Date(5, Month.DECEMBER, 2024),
                new Date(6, Month.DECEMBER, 2024),
                new Date(9, Month.DECEMBER, 2024),
                new Date(10, Month.DECEMBER, 2024),
                new Date(11, Month.DECEMBER, 2024),
                new Date(12, Month.DECEMBER, 2024),
                new Date(13, Month.DECEMBER, 2024),
                new Date(16, Month.DECEMBER, 2024),
                new Date(17, Month.DECEMBER, 2024),
                new Date(18, Month.DECEMBER, 2024),
                new Date(19, Month.DECEMBER, 2024),
                new Date(20, Month.DECEMBER, 2024),
                new Date(23, Month.DECEMBER, 2024),
                new Date(24, Month.DECEMBER, 2024),
                new Date(26, Month.DECEMBER, 2024),
                new Date(27, Month.DECEMBER, 2024),
                new Date(30, Month.DECEMBER, 2024),
                new Date(31, Month.DECEMBER, 2024),
                new Date(2, Month.JANUARY, 2025),
                new Date(3, Month.JANUARY, 2025),
                new Date(6, Month.JANUARY, 2025),
                new Date(7, Month.JANUARY, 2025),
                new Date(8, Month.JANUARY, 2025),
                new Date(9, Month.JANUARY, 2025),
                new Date(10, Month.JANUARY, 2025),
                new Date(13, Month.JANUARY, 2025),
                new Date(14, Month.JANUARY, 2025),
                new Date(15, Month.JANUARY, 2025),
                new Date(16, Month.JANUARY, 2025),
                new Date(17, Month.JANUARY, 2025),
                new Date(21, Month.JANUARY, 2025),
                new Date(22, Month.JANUARY, 2025),
                new Date(23, Month.JANUARY, 2025),
                new Date(24, Month.JANUARY, 2025),
                new Date(27, Month.JANUARY, 2025),
                new Date(28, Month.JANUARY, 2025),
                new Date(29, Month.JANUARY, 2025),
                new Date(30, Month.JANUARY, 2025),
                new Date(31, Month.JANUARY, 2025),
                new Date(3, Month.FEBRUARY, 2025),
                new Date(4, Month.FEBRUARY, 2025),
                new Date(5, Month.FEBRUARY, 2025),
                new Date(6, Month.FEBRUARY, 2025),
                new Date(7, Month.FEBRUARY, 2025),
                new Date(10, Month.FEBRUARY, 2025),
                new Date(11, Month.FEBRUARY, 2025),
                new Date(12, Month.FEBRUARY, 2025),
                new Date(13, Month.FEBRUARY, 2025),
                new Date(14, Month.FEBRUARY, 2025),
                new Date(18, Month.FEBRUARY, 2025),
                new Date(19, Month.FEBRUARY, 2025),
                new Date(20, Month.FEBRUARY, 2025),
                new Date(21, Month.FEBRUARY, 2025),
                new Date(24, Month.FEBRUARY, 2025),
                new Date(25, Month.FEBRUARY, 2025),
                new Date(26, Month.FEBRUARY, 2025),
                new Date(27, Month.FEBRUARY, 2025),
                new Date(28, Month.FEBRUARY, 2025),
                new Date(3, Month.MARCH, 2025),
                new Date(4, Month.MARCH, 2025),
                new Date(5, Month.MARCH, 2025),
                new Date(6, Month.MARCH, 2025),
                new Date(7, Month.MARCH, 2025),
                new Date(10, Month.MARCH, 2025),
                new Date(11, Month.MARCH, 2025),
                new Date(12, Month.MARCH, 2025),
                new Date(13, Month.MARCH, 2025),
                new Date(14, Month.MARCH, 2025),
                new Date(17, Month.MARCH, 2025),
                new Date(18, Month.MARCH, 2025),
                new Date(19, Month.MARCH, 2025),
                new Date(20, Month.MARCH, 2025),
                new Date(21, Month.MARCH, 2025),
                new Date(24, Month.MARCH, 2025),
                new Date(25, Month.MARCH, 2025),
                new Date(26, Month.MARCH, 2025),
                new Date(27, Month.MARCH, 2025),
                new Date(28, Month.MARCH, 2025),
                new Date(31, Month.MARCH, 2025),
                new Date(1, Month.APRIL, 2025),
                new Date(2, Month.APRIL, 2025),
                new Date(3, Month.APRIL, 2025),
                new Date(4, Month.APRIL, 2025),
                new Date(7, Month.APRIL, 2025),
                new Date(8, Month.APRIL, 2025),
                new Date(9, Month.APRIL, 2025),
                new Date(10, Month.APRIL, 2025),
                new Date(11, Month.APRIL, 2025),
                new Date(14, Month.APRIL, 2025),
                new Date(15, Month.APRIL, 2025),
                new Date(16, Month.APRIL, 2025),
                new Date(17, Month.APRIL, 2025),
                new Date(21, Month.APRIL, 2025),
                new Date(22, Month.APRIL, 2025),
                new Date(23, Month.APRIL, 2025),
                new Date(24, Month.APRIL, 2025),
                new Date(25, Month.APRIL, 2025),
                new Date(28, Month.APRIL, 2025),
                new Date(29, Month.APRIL, 2025),
                new Date(30, Month.APRIL, 2025),
                new Date(1, Month.MAY, 2025),
                new Date(2, Month.MAY, 2025),
                new Date(5, Month.MAY, 2025),
                new Date(6, Month.MAY, 2025),
                new Date(7, Month.MAY, 2025),
                new Date(8, Month.MAY, 2025),
                new Date(9, Month.MAY, 2025),
                new Date(12, Month.MAY, 2025),
                new Date(13, Month.MAY, 2025),
                new Date(14, Month.MAY, 2025),
                new Date(15, Month.MAY, 2025),
                new Date(16, Month.MAY, 2025),
                new Date(19, Month.MAY, 2025),
                new Date(20, Month.MAY, 2025),
                new Date(21, Month.MAY, 2025),
                new Date(22, Month.MAY, 2025),
                new Date(23, Month.MAY, 2025),
                new Date(27, Month.MAY, 2025),
                new Date(28, Month.MAY, 2025),
                new Date(29, Month.MAY, 2025));

        double[] pastRates = {
                0.0482, 0.0481, 0.0486, 0.0486,
                0.0485, 0.0484, 0.0482, 0.0483,
                0.0483, 0.0483, 0.0483, 0.0482,
                0.0482, 0.0481, 0.049, 0.0486,
                0.0482, 0.0482, 0.0481, 0.0482,
                0.046, 0.046, 0.0459, 0.0458,
                0.0457, 0.0457, 0.0457, 0.0456,
                0.0457, 0.0457, 0.0458, 0.0458,
                0.0457, 0.0459, 0.0464, 0.0464,
                0.0459, 0.0459, 0.046, 0.0463,
                0.0464, 0.0462, 0.0462, 0.046,
                0.0465, 0.0462, 0.0457, 0.043,
                0.043, 0.0431, 0.044, 0.0453,
                0.0446, 0.0437, 0.0449, 0.044,
                0.0431, 0.0427, 0.0427, 0.0429,
                0.043, 0.043, 0.0429, 0.0428,
                0.0428, 0.0429, 0.0429, 0.0429,
                0.043, 0.0435, 0.0434, 0.0434,
                0.0435, 0.0435, 0.0436, 0.0438,
                0.0435, 0.0433, 0.0433, 0.0436,
                0.0435, 0.0435, 0.0434, 0.0432,
                0.0433, 0.0433, 0.0437, 0.0435,
                0.0433, 0.0434, 0.0434, 0.0433,
                0.0433, 0.0436, 0.0439, 0.0433,
                0.0433, 0.0434, 0.0435, 0.0434,
                0.0433, 0.0432, 0.0431, 0.043,
                0.043, 0.0432, 0.0431, 0.0429,
                0.0429, 0.043, 0.0431, 0.0433,
                0.0435, 0.0436, 0.0434, 0.0441,
                0.0439, 0.0437, 0.0439, 0.0435,
                0.0433, 0.044, 0.0442, 0.0437,
                0.0433, 0.0433, 0.0436, 0.0431,
                0.0432, 0.0432, 0.043, 0.0428,
                0.0429, 0.0433, 0.0436, 0.0436,
                0.0441, 0.0439, 0.0436, 0.0433,
                0.0432, 0.043, 0.0429, 0.0428,
                0.0428, 0.043, 0.0429, 0.0431,
                0.043, 0.0429, 0.0427, 0.0426,
                0.0426, 0.0426, 0.0431, 0.0433,
                0.0433};

        // Overwrite the fixings provided within CommonVars to cover interest
        // accrual for coupons which start on, end on, or otherwise intersect
        // good friday, the only holiday that is in the SOFR fixing calendar
        // but not the federal reserve calendar used to generate SOFR swap
        // coupons.
        vars.sofr.clearFixings();
        vars.sofr.addFixings(pastDates, toDoubleList(pastRates), false);

        UnitedStates fedCal =
                new UnitedStates(UnitedStates.Market.FederalReserve);

        Date noCut = new Date();

        TestCase[] cases = {
                // coupon ends on a fixing holiday, vanilla case
                new TestCase(new Date(18, Month.OCTOBER, 2024),
                        new Date(18, Month.APRIL, 2025), 0, false, 0,
                        new DayCounter(), noCut, noCut,
                        List.of(
                                accrual(18, Month.OCTOBER, 2024, 0.0),
                                accrual(19, Month.OCTOBER, 2024, 1344.444444444),
                                accrual(20, Month.OCTOBER, 2024, 2688.88888888),
                                accrual(21, Month.OCTOBER, 2024, 4033.333333333),
                                accrual(22, Month.OCTOBER, 2024, 5372.76224074),
                                accrual(2, Month.MARCH, 2025, 169984.93805796764),
                                accrual(3, Month.MARCH, 2025, 171224.80883054345),
                                accrual(17, Month.APRIL, 2025, 226499.35892772756),
                                accrual(18, Month.APRIL, 2025, 227726.5388507987),
                                accrual(19, Month.APRIL, 2025, 227726.5388507987),
                                accrual(20, Month.APRIL, 2025, 227726.5388507987),
                                accrual(21, Month.APRIL, 2025, 227726.5388507987),
                                accrual(22, Month.APRIL, 2025, 227726.5388507987),
                                accrual(23, Month.APRIL, 2025, 0.0))),
                // coupon ends on a fixing holiday with obs-shift, but no lookback
                new TestCase(new Date(18, Month.OCTOBER, 2024),
                        new Date(18, Month.APRIL, 2025), 0, true, 0,
                        new DayCounter(), noCut, noCut,
                        List.of(
                                accrual(18, Month.OCTOBER, 2024, 0.0),
                                accrual(19, Month.OCTOBER, 2024, 1344.444444444),
                                accrual(20, Month.OCTOBER, 2024, 2688.88888888),
                                accrual(21, Month.OCTOBER, 2024, 4033.333333333),
                                accrual(22, Month.OCTOBER, 2024, 5372.76224074),
                                accrual(2, Month.MARCH, 2025, 169984.93805796764),
                                accrual(3, Month.MARCH, 2025, 171224.80883054345),
                                accrual(17, Month.APRIL, 2025, 226499.35892772756),
                                accrual(18, Month.APRIL, 2025, 227726.5388507987),
                                accrual(19, Month.APRIL, 2025, 227726.5388507987),
                                accrual(20, Month.APRIL, 2025, 227726.5388507987),
                                accrual(21, Month.APRIL, 2025, 227726.5388507987),
                                accrual(22, Month.APRIL, 2025, 227726.5388507987),
                                accrual(23, Month.APRIL, 2025, 0.0))),
                // coupon ends on a fixing holiday with 4d lockout
                new TestCase(new Date(18, Month.OCTOBER, 2024),
                        new Date(18, Month.APRIL, 2025), 0, false, 4,
                        new DayCounter(), noCut, noCut,
                        List.of(
                                accrual(18, Month.OCTOBER, 2024, 0.0),
                                accrual(19, Month.OCTOBER, 2024, 1344.444444444),
                                accrual(20, Month.OCTOBER, 2024, 2688.88888888),
                                accrual(21, Month.OCTOBER, 2024, 4033.333333333),
                                accrual(22, Month.OCTOBER, 2024, 5372.76224074),
                                accrual(2, Month.MARCH, 2025, 169984.93805796764),
                                accrual(3, Month.MARCH, 2025, 171224.80883054345),
                                accrual(17, Month.APRIL, 2025, 226496.51858061668),
                                accrual(18, Month.APRIL, 2025, 227726.53885632323),
                                accrual(19, Month.APRIL, 2025, 227726.53885632323),
                                accrual(20, Month.APRIL, 2025, 227726.53885632323),
                                accrual(21, Month.APRIL, 2025, 227726.53885632323),
                                accrual(22, Month.APRIL, 2025, 227726.53885632323),
                                accrual(23, Month.APRIL, 2025, 0.0))),
                // coupon ends on a fixing holiday with 4d lockout, with obs-shift but no lookback
                new TestCase(new Date(18, Month.OCTOBER, 2024),
                        new Date(18, Month.APRIL, 2025), 0, true, 4,
                        new DayCounter(), noCut, noCut,
                        List.of(
                                accrual(18, Month.OCTOBER, 2024, 0.0),
                                accrual(19, Month.OCTOBER, 2024, 1344.444444444),
                                accrual(20, Month.OCTOBER, 2024, 2688.88888888),
                                accrual(21, Month.OCTOBER, 2024, 4033.333333333),
                                accrual(22, Month.OCTOBER, 2024, 5372.76224074),
                                accrual(2, Month.MARCH, 2025, 169984.93805796764),
                                accrual(3, Month.MARCH, 2025, 171224.80883054345),
                                accrual(17, Month.APRIL, 2025, 226496.51858061668),
                                accrual(18, Month.APRIL, 2025, 227726.53885632323),
                                accrual(19, Month.APRIL, 2025, 227726.53885632323),
                                accrual(20, Month.APRIL, 2025, 227726.53885632323),
                                accrual(21, Month.APRIL, 2025, 227726.53885632323),
                                accrual(22, Month.APRIL, 2025, 227726.53885632323),
                                accrual(23, Month.APRIL, 2025, 0.0))),
                // coupon ends on a fixing holiday with 5d lookback
                new TestCase(new Date(18, Month.OCTOBER, 2024),
                        new Date(18, Month.APRIL, 2025), 5, false, 0,
                        new DayCounter(), noCut, noCut,
                        List.of(
                                accrual(18, Month.OCTOBER, 2024, 0.0),
                                accrual(19, Month.OCTOBER, 2024, 1338.888888888),
                                accrual(20, Month.OCTOBER, 2024, 2677.777777777),
                                accrual(21, Month.OCTOBER, 2024, 4016.666666666),
                                accrual(22, Month.OCTOBER, 2024, 5353.314449075),
                                accrual(2, Month.MARCH, 2025, 171188.0732570225),
                                accrual(3, Month.MARCH, 2025, 172413.9709083072),
                                accrual(17, Month.APRIL, 2025, 227743.262731344),
                                accrual(18, Month.APRIL, 2025, 228984.79712184728),
                                accrual(19, Month.APRIL, 2025, 228984.79712184728),
                                accrual(20, Month.APRIL, 2025, 228984.79712184728),
                                accrual(21, Month.APRIL, 2025, 228984.79712184728),
                                accrual(22, Month.APRIL, 2025, 228984.79712184728),
                                accrual(23, Month.APRIL, 2025, 0.0))),
                // coupon ends on a fixing holiday with 5d lookback and obs-shift
                new TestCase(new Date(18, Month.OCTOBER, 2024),
                        new Date(18, Month.APRIL, 2025), 5, true, 0,
                        new DayCounter(), noCut, noCut,
                        List.of(
                                accrual(18, Month.OCTOBER, 2024, 0.0),
                                accrual(19, Month.OCTOBER, 2024, 1338.888888888),
                                accrual(20, Month.OCTOBER, 2024, 2677.777777777),
                                accrual(21, Month.OCTOBER, 2024, 4016.666666666),
                                accrual(22, Month.OCTOBER, 2024, 5347.239116048),
                                accrual(1, Month.MARCH, 2025, 169713.19769125758),
                                accrual(2, Month.MARCH, 2025, 170979.7140919386),
                                accrual(3, Month.MARCH, 2025, 172246.23049261962),
                                accrual(17, Month.APRIL, 2025, 227590.6084479198),
                                accrual(18, Month.APRIL, 2025, 228832.36294407956),
                                accrual(19, Month.APRIL, 2025, 228832.36294407956),
                                accrual(20, Month.APRIL, 2025, 228832.36294407956),
                                accrual(21, Month.APRIL, 2025, 228832.36294407956),
                                accrual(22, Month.APRIL, 2025, 228832.36294407956),
                                accrual(23, Month.APRIL, 2025, 0.0))),
                // coupon ends on a fixing holiday with 5d lookback and 4d lockout
                new TestCase(new Date(18, Month.OCTOBER, 2024),
                        new Date(18, Month.APRIL, 2025), 5, false, 4,
                        new DayCounter(), noCut, noCut,
                        List.of(
                                accrual(18, Month.OCTOBER, 2024, 0.0),
                                accrual(19, Month.OCTOBER, 2024, 1338.8888888887784),
                                accrual(20, Month.OCTOBER, 2024, 2677.7777777775568),
                                accrual(21, Month.OCTOBER, 2024, 4016.666666666),
                                accrual(22, Month.OCTOBER, 2024, 5353.314449074),
                                accrual(2, Month.MARCH, 2025, 171188.07325702257),
                                accrual(3, Month.MARCH, 2025, 172413.970908307),
                                accrual(17, Month.APRIL, 2025, 227714.85583612081),
                                accrual(18, Month.APRIL, 2025, 228950.70471453448),
                                accrual(19, Month.APRIL, 2025, 228950.70471453448),
                                accrual(20, Month.APRIL, 2025, 228950.70471453448),
                                accrual(21, Month.APRIL, 2025, 228950.70471453448),
                                accrual(22, Month.APRIL, 2025, 228950.70471453448),
                                accrual(23, Month.APRIL, 2025, 0.0))),
                // coupon ends on a fixing holiday with 5d lookback, obs-shift, and 4d lockout
                new TestCase(new Date(18, Month.OCTOBER, 2024),
                        new Date(18, Month.APRIL, 2025), 5, true, 4,
                        new DayCounter(), noCut, noCut,
                        List.of(
                                accrual(18, Month.OCTOBER, 2024, 0.0),
                                accrual(19, Month.OCTOBER, 2024, 1338.8888888887784),
                                accrual(20, Month.OCTOBER, 2024, 2677.7777777775568),
                                accrual(21, Month.OCTOBER, 2024, 4016.666666666),
                                accrual(22, Month.OCTOBER, 2024, 5347.2391160482857),
                                accrual(1, Month.MARCH, 2025, 169713.19769125758),
                                accrual(2, Month.MARCH, 2025, 170979.7140919386),
                                accrual(3, Month.MARCH, 2025, 172246.23049261962),
                                accrual(17, Month.APRIL, 2025, 227562.35458301706),
                                accrual(18, Month.APRIL, 2025, 228798.4531716943),
                                accrual(19, Month.APRIL, 2025, 228798.4531716943),
                                accrual(20, Month.APRIL, 2025, 228798.4531716943),
                                accrual(21, Month.APRIL, 2025, 228798.4531716943),
                                accrual(22, Month.APRIL, 2025, 228798.4531716943),
                                accrual(23, Month.APRIL, 2025, 0.0))),
                // coupon starts on a fixing holiday
                new TestCase(new Date(18, Month.APRIL, 2025),
                        new Date(19, Month.MAY, 2025), 0, false, 0,
                        new DayCounter(), noCut, noCut,
                        List.of(
                                accrual(18, Month.APRIL, 2025, 0.0),
                                accrual(19, Month.APRIL, 2025, 1200.0),
                                accrual(20, Month.APRIL, 2025, 2400.0),
                                accrual(21, Month.APRIL, 2025, 3600.0),
                                accrual(22, Month.APRIL, 2025, 4800.432),
                                accrual(15, Month.MAY, 2025, 32462.79161128074),
                                accrual(16, Month.MAY, 2025, 33663.900351055265),
                                accrual(17, Month.MAY, 2025, 34862.365761374473),
                                accrual(18, Month.MAY, 2025, 36060.831171695892),
                                accrual(19, Month.MAY, 2025, 37259.296582012881),
                                accrual(20, Month.MAY, 2025, 37259.296582012881),
                                accrual(21, Month.MAY, 2025, 37259.296582012881),
                                accrual(22, Month.MAY, 2025, 0.0))),
                // test daycounter different than index with lookback
                new TestCase(new Date(18, Month.APRIL, 2025),
                        new Date(19, Month.MAY, 2025), 5, true, 0,
                        new Actual365Fixed(), noCut, noCut,
                        List.of(
                                accrual(18, Month.APRIL, 2025, 0.0),
                                accrual(19, Month.APRIL, 2025, 1197.2602739727813),
                                accrual(20, Month.APRIL, 2025, 2394.5205479455626),
                                accrual(21, Month.APRIL, 2025, 3591.780821918343),
                                accrual(22, Month.APRIL, 2025, 4756.5963949772768),
                                accrual(15, Month.MAY, 2025, 32114.584259337709),
                                accrual(16, Month.MAY, 2025, 33294.244344813727),
                                accrual(17, Month.MAY, 2025, 34449.672542982727),
                                accrual(18, Month.MAY, 2025, 35637.592285844192),
                                accrual(19, Month.MAY, 2025, 36825.512028705671),
                                accrual(20, Month.MAY, 2025, 36825.512028705671),
                                accrual(21, Month.MAY, 2025, 36825.512028705671),
                                accrual(22, Month.MAY, 2025, 0.0))),
                // test daycounter different than index
                new TestCase(new Date(18, Month.APRIL, 2025),
                        new Date(19, Month.MAY, 2025), 0, false, 0,
                        new Actual365Fixed(), noCut, noCut,
                        List.of(
                                accrual(18, Month.APRIL, 2025, 0.0),
                                accrual(19, Month.APRIL, 2025, 1183.5616438346101),
                                accrual(20, Month.APRIL, 2025, 2367.1232876714103),
                                accrual(21, Month.APRIL, 2025, 3550.6849315060199),
                                accrual(22, Month.APRIL, 2025, 4734.6726575326247),
                                accrual(15, Month.MAY, 2025, 32018.095835783744),
                                accrual(16, Month.MAY, 2025, 33202.751031177795),
                                accrual(17, Month.MAY, 2025, 34384.799107109066),
                                accrual(18, Month.MAY, 2025, 35566.847183042526),
                                accrual(19, Month.MAY, 2025, 36748.895258971606),
                                accrual(20, Month.MAY, 2025, 36748.895258971606),
                                accrual(21, Month.MAY, 2025, 36748.895258971606),
                                accrual(22, Month.MAY, 2025, 0.0))),
                // lookback lands on a fixing holiday without obs-shift
                new TestCase(new Date(25, Month.APRIL, 2025),
                        new Date(27, Month.MAY, 2025), 5, false, 0,
                        new DayCounter(), noCut, noCut,
                        List.of(
                                accrual(25, Month.APRIL, 2025, 0.0),
                                accrual(26, Month.APRIL, 2025, 1200.0),
                                accrual(27, Month.APRIL, 2025, 2400.0),
                                accrual(28, Month.APRIL, 2025, 3600.0),
                                accrual(22, Month.MAY, 2025, 32462.79161128074),
                                accrual(23, Month.MAY, 2025, 33663.900351055265),
                                accrual(24, Month.MAY, 2025, 34862.365761374473),
                                accrual(25, Month.MAY, 2025, 36060.831171695892),
                                accrual(26, Month.MAY, 2025, 37259.296582012881),
                                accrual(27, Month.MAY, 2025, 38457.761992334308),
                                accrual(28, Month.MAY, 2025, 38457.761992334308),
                                accrual(29, Month.MAY, 2025, 38457.761992334308),
                                accrual(30, Month.MAY, 2025, 0.0))),
                // lookback lands on a fixing holiday with obs-shift
                new TestCase(new Date(25, Month.APRIL, 2025),
                        new Date(27, Month.MAY, 2025), 5, true, 0,
                        new DayCounter(), noCut, noCut,
                        List.of(
                                accrual(25, Month.APRIL, 2025, 0.0),
                                accrual(26, Month.APRIL, 2025, 1200.0),
                                accrual(27, Month.APRIL, 2025, 2400.0),
                                accrual(28, Month.APRIL, 2025, 3600.0),
                                accrual(22, Month.MAY, 2025, 32463.887689144285),
                                accrual(23, Month.MAY, 2025, 33665.178902847132),
                                accrual(24, Month.MAY, 2025, 34857.396658668215),
                                accrual(25, Month.MAY, 2025, 36059.3758537947),
                                accrual(26, Month.MAY, 2025, 37261.355048921192),
                                accrual(27, Month.MAY, 2025, 38463.334244047684),
                                accrual(28, Month.MAY, 2025, 38463.334244047684),
                                accrual(29, Month.MAY, 2025, 38463.334244047684),
                                accrual(30, Month.MAY, 2025, 0.0))),
                // Coupon period spans fixing holiday
                new TestCase(new Date(14, Month.APRIL, 2025),
                        new Date(14, Month.MAY, 2025), 0, false, 0,
                        new DayCounter(), noCut, noCut,
                        List.of(
                                accrual(14, Month.APRIL, 2025, 0.0),
                                accrual(15, Month.APRIL, 2025, 1202.7777777778858),
                                accrual(16, Month.APRIL, 2025, 2414.0345586420867),
                                accrual(17, Month.APRIL, 2025, 3611.5457944463355),
                                accrual(18, Month.APRIL, 2025, 4811.9791799416953),
                                accrual(19, Month.APRIL, 2025, 6012.4125654370528),
                                accrual(10, Month.MAY, 2025, 31317.54145558663),
                                accrual(11, Month.MAY, 2025, 32510.011880697577),
                                accrual(12, Month.MAY, 2025, 33702.482305808524),
                                accrual(13, Month.MAY, 2025, 34895.378045369798),
                                accrual(14, Month.MAY, 2025, 36093.990548857757),
                                accrual(15, Month.MAY, 2025, 36093.990548857757),
                                accrual(16, Month.MAY, 2025, 36093.990548857757),
                                accrual(17, Month.MAY, 2025, 0.0))),
                // Partially fixed coupon
                new TestCase(new Date(14, Month.APRIL, 2025),
                        new Date(16, Month.JUNE, 2025), 0, false, 0,
                        new DayCounter(),
                        new Date(11, Month.JUNE, 2025),
                        new Date(14, Month.JUNE, 2025),
                        List.of(
                                accrual(14, Month.APRIL, 2025, 0.0),
                                accrual(15, Month.APRIL, 2025, 1202.7777777778858),
                                accrual(16, Month.APRIL, 2025, 2414.0345586420867),
                                accrual(17, Month.APRIL, 2025, 3611.5457944463355),
                                accrual(18, Month.APRIL, 2025, 4811.9791799416953),
                                accrual(19, Month.APRIL, 2025, 6012.4125654370528),
                                accrual(28, Month.MAY, 2025, 52812.944149152048),
                                accrual(29, Month.MAY, 2025, 54022.074150490429),
                                accrual(30, Month.MAY, 2025, 55231.349583297902),
                                accrual(31, Month.MAY, 2025, 56440.988690997779),
                                accrual(1, Month.JUNE, 2025, 57650.627798697671),
                                accrual(2, Month.JUNE, 2025, 58860.266906397563),
                                accrual(3, Month.JUNE, 2025, 60070.197028672112),
                                accrual(4, Month.JUNE, 2025, 61280.272687407327),
                                accrual(5, Month.JUNE, 2025, 62490.493900104753),
                                accrual(6, Month.JUNE, 2025, 63700.860684274834),
                                accrual(7, Month.JUNE, 2025, 64911.518669761303),
                                accrual(8, Month.JUNE, 2025, 66122.176655245552),
                                accrual(9, Month.JUNE, 2025, 67332.83464073202),
                                accrual(10, Month.JUNE, 2025, 68543.783885914861),
                                accrual(11, Month.JUNE, 2025, 69754.878790140312),
                                accrual(12, Month.JUNE, 2025, 70966.119370929882),
                                accrual(13, Month.JUNE, 2025, 72177.505645807381),
                                accrual(14, Month.JUNE, 2025, 73389.183367278398),
                                accrual(15, Month.JUNE, 2025, 74600.861088749429),
                                accrual(16, Month.JUNE, 2025, 75812.538810220431),
                                accrual(17, Month.JUNE, 2025, 75812.538810220431),
                                accrual(18, Month.JUNE, 2025, 75812.538810220431),
                                accrual(19, Month.JUNE, 2025, 0.0))),
                // Partially fixed coupon with 1d lockout
                new TestCase(new Date(14, Month.APRIL, 2025),
                        new Date(16, Month.JUNE, 2025), 0, false, 1,
                        new DayCounter(),
                        new Date(11, Month.JUNE, 2025),
                        new Date(14, Month.JUNE, 2025),
                        List.of(
                                accrual(14, Month.APRIL, 2025, 0.0),
                                accrual(15, Month.APRIL, 2025, 1202.7777777778858),
                                accrual(16, Month.APRIL, 2025, 2414.0345586420867),
                                accrual(17, Month.APRIL, 2025, 3611.5457944463355),
                                accrual(18, Month.APRIL, 2025, 4811.9791799416953),
                                accrual(19, Month.APRIL, 2025, 6012.4125654370528),
                                accrual(28, Month.MAY, 2025, 52812.944149152048),
                                accrual(29, Month.MAY, 2025, 54022.074150490429),
                                accrual(30, Month.MAY, 2025, 55231.349583297902),
                                accrual(31, Month.MAY, 2025, 56440.988690997779),
                                accrual(1, Month.JUNE, 2025, 57650.627798697671),
                                accrual(2, Month.JUNE, 2025, 58860.266906397563),
                                accrual(3, Month.JUNE, 2025, 60070.197028672112),
                                accrual(4, Month.JUNE, 2025, 61280.272687407327),
                                accrual(5, Month.JUNE, 2025, 62490.493900104753),
                                accrual(6, Month.JUNE, 2025, 63700.860684274834),
                                accrual(7, Month.JUNE, 2025, 64911.518669761303),
                                accrual(8, Month.JUNE, 2025, 66122.176655245552),
                                accrual(9, Month.JUNE, 2025, 67332.83464073202),
                                accrual(10, Month.JUNE, 2025, 68543.783885914861),
                                accrual(11, Month.JUNE, 2025, 69754.878790140312),
                                accrual(12, Month.JUNE, 2025, 70966.119370929882),
                                accrual(13, Month.JUNE, 2025, 72177.505645807381),
                                accrual(14, Month.JUNE, 2025, 73389.037632296531),
                                accrual(15, Month.JUNE, 2025, 74600.569618785696),
                                accrual(16, Month.JUNE, 2025, 75812.101605274845),
                                accrual(17, Month.JUNE, 2025, 75812.101605274845),
                                accrual(18, Month.JUNE, 2025, 75812.101605274845),
                                accrual(19, Month.JUNE, 2025, 0.0))),
                // Partially fixed coupon with 5d lockout
                new TestCase(new Date(14, Month.APRIL, 2025),
                        new Date(16, Month.JUNE, 2025), 0, false, 5,
                        new DayCounter(),
                        new Date(11, Month.JUNE, 2025),
                        new Date(19, Month.JUNE, 2025),
                        List.of(
                                accrual(14, Month.APRIL, 2025, 0.0),
                                accrual(15, Month.APRIL, 2025, 1202.7777777778858),
                                accrual(16, Month.APRIL, 2025, 2414.0345586420867),
                                accrual(17, Month.APRIL, 2025, 3611.5457944463355),
                                accrual(18, Month.APRIL, 2025, 4811.9791799416953),
                                accrual(19, Month.APRIL, 2025, 6012.4125654370528),
                                accrual(28, Month.MAY, 2025, 52812.944149152048),
                                accrual(29, Month.MAY, 2025, 54022.074150490429),
                                accrual(30, Month.MAY, 2025, 55231.349583297902),
                                accrual(31, Month.MAY, 2025, 56440.988690997779),
                                accrual(1, Month.JUNE, 2025, 57650.627798697671),
                                accrual(2, Month.JUNE, 2025, 58860.266906397563),
                                accrual(3, Month.JUNE, 2025, 60070.197028672112),
                                accrual(4, Month.JUNE, 2025, 61280.272687407327),
                                accrual(5, Month.JUNE, 2025, 62490.493900104753),
                                accrual(6, Month.JUNE, 2025, 63700.860684274834),
                                accrual(7, Month.JUNE, 2025, 64911.518669761303),
                                accrual(8, Month.JUNE, 2025, 66122.176655245552),
                                accrual(9, Month.JUNE, 2025, 67332.83464073202),
                                accrual(10, Month.JUNE, 2025, 68543.929550799468),
                                accrual(11, Month.JUNE, 2025, 69755.170154954802),
                                accrual(12, Month.JUNE, 2025, 70966.556470726253),
                                accrual(13, Month.JUNE, 2025, 72178.088515641997),
                                accrual(14, Month.JUNE, 2025, 73389.766307232479),
                                accrual(15, Month.JUNE, 2025, 74601.44409882075),
                                accrual(16, Month.JUNE, 2025, 75813.121890411217),
                                accrual(17, Month.JUNE, 2025, 75813.121890411217),
                                accrual(18, Month.JUNE, 2025, 75813.121890411217),
                                accrual(19, Month.JUNE, 2025, 0.0))),
                // partially fixed 5d lookback
                new TestCase(new Date(23, Month.APRIL, 2025),
                        new Date(23, Month.JUNE, 2025), 5, false, 0,
                        new DayCounter(), noCut, noCut,
                        List.of(
                                accrual(23, Month.APRIL, 2025, 0.0),
                                accrual(24, Month.APRIL, 2025, 1211.111111110160),
                                accrual(25, Month.APRIL, 2025, 2408.478330246930),
                                accrual(26, Month.APRIL, 2025, 3608.767347644810),
                                accrual(27, Month.APRIL, 2025, 4809.056365047140),
                                accrual(28, Month.APRIL, 2025, 6009.345382445020),
                                accrual(28, Month.MAY, 2025, 42072.0404553032),
                                accrual(29, Month.MAY, 2025, 43263.1417778784),
                                accrual(30, Month.MAY, 2025, 44451.5945829904),
                                accrual(31, Month.MAY, 2025, 45640.1880216828),
                                accrual(8, Month.JUNE, 2025, 55246.0306584401),
                                accrual(9, Month.JUNE, 2025, 56455.3805639378),
                                accrual(10, Month.JUNE, 2025, 57665.0214144325),
                                accrual(11, Month.JUNE, 2025, 58874.8077665890),
                                accrual(12, Month.JUNE, 2025, 60084.7396379112),
                                accrual(13, Month.JUNE, 2025, 61294.8170459027),
                                accrual(14, Month.JUNE, 2025, 62505.1855855885),
                                accrual(15, Month.JUNE, 2025, 63715.5541252765),
                                accrual(16, Month.JUNE, 2025, 64925.9226649645),
                                accrual(17, Month.JUNE, 2025, 66136.5823947113),
                                accrual(18, Month.JUNE, 2025, 67347.3877486774),
                                accrual(19, Month.JUNE, 2025, 68558.3387443800),
                                accrual(20, Month.JUNE, 2025, 69769.2897400826),
                                accrual(21, Month.JUNE, 2025, 70980.5320542944),
                                accrual(22, Month.JUNE, 2025, 72191.7743685063),
                                accrual(23, Month.JUNE, 2025, 73403.0166827182),
                                accrual(24, Month.JUNE, 2025, 73403.0166827182),
                                accrual(25, Month.JUNE, 2025, 73403.0166827182),
                                accrual(26, Month.JUNE, 2025, 0.0))),
                // partially fixed 5d lookback with obsshift
                new TestCase(new Date(23, Month.APRIL, 2025),
                        new Date(23, Month.JUNE, 2025), 5, true, 0,
                        new DayCounter(),
                        new Date(10, Month.JUNE, 2025),
                        new Date(21, Month.JUNE, 2025),
                        List.of(
                                accrual(23, Month.APRIL, 2025, 0.0),
                                accrual(24, Month.APRIL, 2025, 1211.111111110160),
                                accrual(25, Month.APRIL, 2025, 2408.478330246930),
                                accrual(26, Month.APRIL, 2025, 3604.817199922560),
                                accrual(27, Month.APRIL, 2025, 4806.422933230080),
                                accrual(28, Month.APRIL, 2025, 6008.0286665376),
                                accrual(29, Month.APRIL, 2025, 7208.999619405480),
                                accrual(28, Month.MAY, 2025, 42077.6147132629),
                                accrual(29, Month.MAY, 2025, 43268.7166970069),
                                accrual(30, Month.MAY, 2025, 44457.1701618179),
                                accrual(31, Month.MAY, 2025, 45645.7642602892),
                                accrual(8, Month.JUNE, 2025, 55248.4485876401),
                                accrual(9, Month.JUNE, 2025, 56449.5018178061),
                                accrual(10, Month.JUNE, 2025, 57659.1082384094),
                                accrual(11, Month.JUNE, 2025, 58868.8643952469),
                                accrual(12, Month.JUNE, 2025, 60078.7700574061),
                                accrual(13, Month.JUNE, 2025, 61288.8250130815),
                                accrual(14, Month.JUNE, 2025, 62515.4436336153),
                                accrual(15, Month.JUNE, 2025, 63717.6637034925),
                                accrual(16, Month.JUNE, 2025, 64919.8837733697),
                                accrual(17, Month.JUNE, 2025, 66130.5341082391),
                                accrual(18, Month.JUNE, 2025, 67341.3329076791),
                                accrual(19, Month.JUNE, 2025, 68552.2800428873),
                                accrual(20, Month.JUNE, 2025, 69754.9516225870),
                                accrual(21, Month.JUNE, 2025, 70966.1922121385),
                                accrual(22, Month.JUNE, 2025, 72169.0090292934),
                                accrual(23, Month.JUNE, 2025, 73371.8258464483),
                                accrual(24, Month.JUNE, 2025, 73371.8258464483),
                                accrual(25, Month.JUNE, 2025, 73371.8258464483),
                                accrual(26, Month.JUNE, 2025, 0.0))),
                // partially fixed 5d lookback with 4d lockout
                new TestCase(new Date(23, Month.APRIL, 2025),
                        new Date(23, Month.JUNE, 2025), 5, false, 5,
                        new DayCounter(), noCut, noCut,
                        List.of(
                                accrual(23, Month.APRIL, 2025, 0.0),
                                accrual(24, Month.APRIL, 2025, 1211.111111110160),
                                accrual(25, Month.APRIL, 2025, 2408.478330246930),
                                accrual(26, Month.APRIL, 2025, 3608.767347644810),
                                accrual(27, Month.APRIL, 2025, 4809.056365047140),
                                accrual(28, Month.APRIL, 2025, 6009.345382445020),
                                accrual(28, Month.MAY, 2025, 42072.0404553032),
                                accrual(29, Month.MAY, 2025, 43263.1417778784),
                                accrual(30, Month.MAY, 2025, 44451.5945829904),
                                accrual(31, Month.MAY, 2025, 45640.1880216828),
                                accrual(8, Month.JUNE, 2025, 55246.0306584401),
                                accrual(9, Month.JUNE, 2025, 56455.3805639378),
                                accrual(10, Month.JUNE, 2025, 57665.0214144325),
                                accrual(11, Month.JUNE, 2025, 58874.8077665890),
                                accrual(12, Month.JUNE, 2025, 60084.7396379112),
                                accrual(13, Month.JUNE, 2025, 61294.8170459027),
                                accrual(14, Month.JUNE, 2025, 62505.0400080696),
                                accrual(15, Month.JUNE, 2025, 63715.2629702364),
                                accrual(16, Month.JUNE, 2025, 64925.4859324033),
                                accrual(17, Month.JUNE, 2025, 66136.1456096188),
                                accrual(18, Month.JUNE, 2025, 67346.9509110469),
                                accrual(19, Month.JUNE, 2025, 68557.9018542048),
                                accrual(20, Month.JUNE, 2025, 69768.8527973628),
                                accrual(21, Month.JUNE, 2025, 70980.0950590167),
                                accrual(22, Month.JUNE, 2025, 72191.3373206706),
                                accrual(23, Month.JUNE, 2025, 73402.5795823268),
                                accrual(24, Month.JUNE, 2025, 73402.5795823268),
                                accrual(25, Month.JUNE, 2025, 73402.5795823268),
                                accrual(26, Month.JUNE, 2025, 0.0))),
                // partially fixed 5d lookback with obsshift and 4d lockout
                new TestCase(new Date(23, Month.APRIL, 2025),
                        new Date(23, Month.JUNE, 2025), 5, true, 4,
                        new DayCounter(),
                        new Date(10, Month.JUNE, 2025),
                        new Date(26, Month.JUNE, 2025),
                        List.of(
                                accrual(23, Month.APRIL, 2025, 0.0),
                                accrual(24, Month.APRIL, 2025, 1211.111111110160),
                                accrual(25, Month.APRIL, 2025, 2408.478330246930),
                                accrual(26, Month.APRIL, 2025, 3604.817199922560),
                                accrual(27, Month.APRIL, 2025, 4806.422933230080),
                                accrual(28, Month.APRIL, 2025, 6008.0286665376),
                                accrual(29, Month.APRIL, 2025, 7208.999619405480),
                                accrual(28, Month.MAY, 2025, 42077.6147132629),
                                accrual(29, Month.MAY, 2025, 43268.7166970069),
                                accrual(30, Month.MAY, 2025, 44457.1701618179),
                                accrual(31, Month.MAY, 2025, 45645.7642602892),
                                accrual(8, Month.JUNE, 2025, 55248.4485876401),
                                accrual(9, Month.JUNE, 2025, 56449.5018178061),
                                accrual(10, Month.JUNE, 2025, 57659.1082384094),
                                accrual(11, Month.JUNE, 2025, 58868.8643952469),
                                accrual(12, Month.JUNE, 2025, 60078.7700574061),
                                accrual(13, Month.JUNE, 2025, 61288.8250130815),
                                accrual(14, Month.JUNE, 2025, 62515.4436336153),
                                accrual(15, Month.JUNE, 2025, 63717.6637034925),
                                accrual(16, Month.JUNE, 2025, 64919.8837733697),
                                accrual(17, Month.JUNE, 2025, 66130.6771547586),
                                accrual(18, Month.JUNE, 2025, 67341.6191264041),
                                accrual(19, Month.JUNE, 2025, 68552.7095548175),
                                accrual(20, Month.JUNE, 2025, 69755.3886698143),
                                accrual(21, Month.JUNE, 2025, 70966.7750118714),
                                accrual(22, Month.JUNE, 2025, 72169.6017069879),
                                accrual(23, Month.JUNE, 2025, 73372.4284021044),
                                accrual(24, Month.JUNE, 2025, 73372.4284021044),
                                accrual(25, Month.JUNE, 2025, 73372.4284021044),
                                accrual(26, Month.JUNE, 2025, 0.0))),
                // floating starts on holiday
                new TestCase(new Date(26, Month.MARCH, 2027),
                        new Date(28, Month.JUNE, 2027), 0, false, 0,
                        new DayCounter(), noCut, noCut,
                        List.of(
                                accrual(26, Month.MARCH, 2027, 0.0),
                                accrual(27, Month.MARCH, 2027, 1203.0671590612662),
                                // 1d of interest
                                accrual(28, Month.MARCH, 2027, 2 * 1203.0671590612662),
                                // 2d of interest
                                accrual(29, Month.MARCH, 2027, 3 * 1203.0671590612662),
                                // 3d of interest
                                accrual(30, Month.MARCH, 2027, 4812.4857244213899),
                                accrual(31, Month.MARCH, 2027, 6015.914708716874),
                                accrual(25, Month.JUNE, 2027, 110054.18406154988),
                                accrual(26, Month.JUNE, 2027, 111270.41832775576),
                                accrual(27, Month.JUNE, 2027, 112486.65259396384),
                                accrual(28, Month.JUNE, 2027, 113702.88686016972),
                                accrual(29, Month.JUNE, 2027, 113702.88686016972),
                                accrual(30, Month.JUNE, 2027, 113702.88686016972),
                                accrual(1, Month.JULY, 2027, 0.0))),
                // floating ends on holiday
                new TestCase(new Date(26, Month.FEBRUARY, 2027),
                        new Date(26, Month.MARCH, 2027), 0, false, 1,
                        new DayCounter(),
                        new Date(25, Month.MARCH, 2027),
                        new Date(26, Month.MARCH, 2027),
                        List.of(
                                accrual(25, Month.MARCH, 2027, 32527.788409235647),
                                // only get 1d of interest
                                accrual(26, Month.MARCH, 2027, 33734.551129034335),
                                accrual(31, Month.MARCH, 2027, 0.0)))
        };

        java.util.function.BiFunction<TestCase, Boolean, OvernightIndexedCoupon>
                makeCoupon = (tc, telescopicValueDates) -> {
            DayCounter dc = tc.dayCounter().empty()
                    ? vars.sofr.dayCounter() : tc.dayCounter();
            return vars.makeCoupon(tc.startDate(), tc.endDate(),
                    tc.lookbackDays(), tc.lockoutDays(), tc.obsShift(),
                    telescopicValueDates, RateAveraging.Compound,
                    fedCal.advance(tc.endDate(), 2, TimeUnit.DAYS),
                    10000000.0, dc);
        };

        for (TestCase tc : cases) {
            OvernightIndexedCoupon cpn = makeCoupon.apply(tc, false);
            checkAccruals(cpn, tc.accruals(), new Date(), new Date());
            if (cpn.canApplyTelescopicFormula()) {
                OvernightIndexedCoupon cpnTelescopic =
                        makeCoupon.apply(tc, true);
                checkAccruals(cpnTelescopic, tc.accruals(),
                        tc.telescopicLowerCut(), tc.telescopicUpperCut());
            }
        }
    }
}
