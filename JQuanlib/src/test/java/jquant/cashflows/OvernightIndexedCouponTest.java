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
import jquant.termstructures.yield.DiscountCurve;
import jquant.termstructures.yield.FlatForward;
import jquant.termstructures.yield.ZeroCurve;
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

            // Official uses InterpolatedZeroCurve<Cubic>; the existing
            // Java ZeroCurve (linear) is sufficient here because this test
            // only inspects the coupons' gearings and spreads.
            ZeroCurve zeroCurve = new ZeroCurve(curveDates, zeroRates, dc,
                    new UnitedStates(UnitedStates.Market.SOFR));
            forecastCurve.linkTo(zeroCurve, true);
        }

        List<CashFlow> makeLeg(List<Double> gearings, List<Double> spreads) {
            OvernightLeg leg = new OvernightLeg(legSchedule, sofr);
            leg.withNotionals(notional)
                    .withPaymentDayCounter(dc)
                    .withAveragingMethod(RateAveraging.Compound)
                    .withTelescopicValueDates(false);

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
}
