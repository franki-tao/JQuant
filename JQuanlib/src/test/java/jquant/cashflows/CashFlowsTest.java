package jquant.cashflows;

import jquant.Compounding;
import jquant.Handle;
import jquant.InterestRate;
import jquant.RelinkableHandle;
import jquant.SavedSettings;
import jquant.Settings;
import jquant.CashFlow;
import jquant.indexes.Bkbm1M;
import jquant.indexes.Bkbm2M;
import jquant.indexes.Bkbm3M;
import jquant.indexes.Bkbm6M;
import jquant.indexes.Currencies;
import jquant.indexes.Euribor3M;
import jquant.indexes.IborIndex;
import jquant.indexes.USDLibor;
import jquant.patterns.Observable;
import jquant.patterns.Observer;
import jquant.termstructures.YieldTermStructure;
import jquant.termstructures.volatility.optionlet.ConstantOptionletVolatility;
import jquant.termstructures.yield.FlatForward;
import jquant.time.BusinessDayConvention;
import jquant.time.Calendar;
import jquant.time.Date;
import jquant.time.DateGenerationRule;
import jquant.time.DayCounter;
import jquant.time.Frequency;
import jquant.time.MakeSchedule;
import jquant.time.Month;
import jquant.time.Period;
import jquant.time.Schedule;
import jquant.time.TimeUnit;
import jquant.time.TimeUtils;
import jquant.time.calendars.NullCalendar;
import jquant.time.calendars.Target;
import jquant.time.calendars.UnitedStates;
import jquant.time.calendars.NewZealand;
import jquant.time.daycounters.Actual360;
import jquant.time.daycounters.Actual365Fixed;
import jquant.time.daycounters.ActualActual;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

/**
 * Port of the official {@code test-suite/cashflows.cpp} test cases.
 *
 * <p>https://github.com/lballabio/QuantLib/blob/master/test-suite/cashflows.cpp
 */
public class CashFlowsTest {

    private static void checkSmall(double actual, double expected, double tolerance) {
        assertEquals(0.0, actual - expected, tolerance);
    }

    private static void assertDateEquals(Date expected, Date actual) {
        assertEquals(expected.serialNumber(), actual.serialNumber(),
                "expected " + expected + ", got " + actual);
    }

    private static boolean isNullDate(Date d) {
        return d.serialNumber() == 0;
    }

    private static double dayCount(Date from, Date to) {
        return TimeUtils.substract(to, from);
    }

    private static List<CashFlow> leg(CashFlow... flows) {
        List<CashFlow> result = new ArrayList<>();
        for (CashFlow flow : flows)
            result.add(flow);
        return result;
    }

    /** Test-local port of the official {@code Flag} observer utility. */
    private static class Flag implements Observer {
        private boolean up_ = false;
        private final Set<Observable> observables_ = new HashSet<>();

        public void raise() {
            up_ = true;
        }

        public void lower() {
            up_ = false;
        }

        public boolean isUp() {
            return up_;
        }

        @Override
        public void registerWith(Observable observable) {
            observable.registerObserver(this);
            observables_.add(observable);
        }

        @Override
        public void unregisterWithAll() {
            for (Observable observable : observables_)
                observable.unregisterObserver(this);
            observables_.clear();
        }

        @Override
        public void unregisterWith(Observable o) {
            o.unregisterObserver(this);
            observables_.remove(o);
        }

        @Override
        public void update() {
            raise();
        }
    }

    @Test
    public void testSimpleDuration() {
        Date settlement = new Date(15, Month.JANUARY, 2024);
        Settings.instance.evaluationDate().equal(settlement);
        DayCounter dayCounter = new Actual365Fixed();
        List<CashFlow> leg = leg(
                new SimpleCashFlow(40.0, settlement.add(180)),
                new SimpleCashFlow(70.0, settlement.add(480)));
        double firstTime = 180.0 / 365.0, secondInterval = 300.0 / 365.0;

        for (double rate : new double[]{-0.02, 0.0, 0.05}) {
            for (Compounding compounding : new Compounding[]{
                    Compounding.Simple, Compounding.Compounded, Compounding.Continuous,
                    Compounding.SimpleThenCompounded, Compounding.CompoundedThenSimple}) {
                double firstDiscount, secondDiscount;
                double firstSimple = 1.0 / (1.0 + rate * firstTime);
                double secondSimple = 1.0 / (1.0 + rate * secondInterval);
                double firstCompounded = Math.pow(1.0 + rate / 2.0, -2.0 * firstTime);
                double secondCompounded = Math.pow(1.0 + rate / 2.0, -2.0 * secondInterval);
                if (compounding == Compounding.Continuous) {
                    firstDiscount = Math.exp(-rate * firstTime);
                    secondDiscount = Math.exp(-rate * (firstTime + secondInterval));
                } else if (compounding == Compounding.Simple) {
                    firstDiscount = firstSimple;
                    secondDiscount = firstSimple * secondSimple;
                } else if (compounding == Compounding.SimpleThenCompounded) {
                    firstDiscount = firstSimple;
                    secondDiscount = firstSimple * secondCompounded;
                } else if (compounding == Compounding.CompoundedThenSimple) {
                    firstDiscount = firstCompounded;
                    secondDiscount = firstCompounded * secondSimple;
                } else {
                    firstDiscount = firstCompounded;
                    secondDiscount = firstCompounded * secondCompounded;
                }
                double expected = (firstTime * 40.0 * firstDiscount
                        + (firstTime + secondInterval) * 70.0 * secondDiscount)
                        / (40.0 * firstDiscount + 70.0 * secondDiscount);
                InterestRate yield = new InterestRate(rate, dayCounter, compounding,
                        Frequency.SEMIANNUAL);
                checkSmall(CashFlows.duration(leg, yield, Duration.Type.Simple, false),
                        expected, 1.0e-12);
                checkSmall(CashFlows.duration(leg, rate, dayCounter, compounding,
                                Frequency.SEMIANNUAL, Duration.Type.Simple, false,
                                settlement, settlement),
                        expected, 1.0e-12);
            }
        }
    }

    @Test
    public void testDurationAndConvexityWithNoValue() {
        Date settlement = new Date(15, Month.JANUARY, 2024);
        Settings.instance.evaluationDate().equal(settlement);
        InterestRate yield = new InterestRate(0.04, new Actual365Fixed(),
                Compounding.Compounded, Frequency.ANNUAL);
        List<CashFlow> expired = leg(new SimpleCashFlow(100.0, settlement.substract(1)));
        List<CashFlow> zero = leg(new SimpleCashFlow(0.0, settlement.add(365)));
        List<CashFlow> cancelling = leg(
                new SimpleCashFlow(100.0, settlement.add(365)),
                new SimpleCashFlow(-100.0, settlement.add(365)));
        for (List<CashFlow> l : List.of(new ArrayList<CashFlow>(), expired, zero)) {
            for (Duration.Type type : Duration.Type.values()) {
                assertEquals(0.0, CashFlows.duration(l, yield, type, false));
            }
            assertEquals(0.0, CashFlows.convexity(l, yield, false));
            assertEquals(0.0, CashFlows.basisPointValue(l, yield, false));
        }
        InterestRate zeroYield = new InterestRate(0.0, new Actual365Fixed(),
                Compounding.Compounded, Frequency.ANNUAL);
        assertEquals(0.0, CashFlows.npv(cancelling, zeroYield, false));
        for (Duration.Type type : Duration.Type.values()) {
            assertEquals(0.0, CashFlows.duration(cancelling, zeroYield, type, false));
        }
        assertEquals(0.0, CashFlows.convexity(cancelling, zeroYield, false));
        assertEquals(0.0, CashFlows.basisPointValue(cancelling, zeroYield, false));
    }

    private record NavigationCase(Date settlement, boolean include, Date previous, Date next,
                                  double previousAmount, double nextAmount) {
    }

    @Test
    public void testCashFlowNavigation() {
        Date today = new Date(15, Month.JANUARY, 2024);
        Settings.instance.evaluationDate().equal(today);
        Settings.instance.setIncludeTodaysCashFlows(Optional.empty());
        List<CashFlow> leg = leg(
                new SimpleCashFlow(10.0, today.substract(10)),
                new SimpleCashFlow(20.0, today),
                new SimpleCashFlow(30.0, today),
                new SimpleCashFlow(40.0, today.add(10)),
                new SimpleCashFlow(50.0, today.add(10)));

        NavigationCase[] cases = {
                new NavigationCase(today.substract(11), false, new Date(), today.substract(10), 0.0, 10.0),
                new NavigationCase(today.substract(10), true, new Date(), today.substract(10), 0.0, 10.0),
                new NavigationCase(today.substract(10), false, today.substract(10), today, 10.0, 50.0),
                new NavigationCase(today, true, today.substract(10), today, 10.0, 50.0),
                new NavigationCase(today, false, today, today.add(10), 50.0, 90.0),
                new NavigationCase(today.add(1), true, today, today.add(10), 50.0, 90.0),
                new NavigationCase(today.add(10), true, today, today.add(10), 50.0, 90.0),
                new NavigationCase(today.add(10), false, today.add(10), new Date(), 90.0, 0.0),
                new NavigationCase(today.add(11), true, today.add(10), new Date(), 90.0, 0.0)
        };

        for (NavigationCase test : cases) {
            Optional<CashFlow> previous = CashFlows.previousCashFlow(leg, test.include(), test.settlement());
            Optional<CashFlow> next = CashFlows.nextCashFlow(leg, test.include(), test.settlement());
            if (isNullDate(test.previous())) {
                assertTrue(previous.isEmpty(), "expected no previous cash flow");
            } else {
                assertTrue(previous.isPresent(), "expected a previous cash flow");
                assertDateEquals(test.previous(), previous.get().date());
            }
            if (isNullDate(test.next())) {
                assertTrue(next.isEmpty(), "expected no next cash flow");
            } else {
                assertTrue(next.isPresent(), "expected a next cash flow");
                assertDateEquals(test.next(), next.get().date());
            }
            assertDateEquals(test.previous(),
                    CashFlows.previousCashFlowDate(leg, test.include(), test.settlement()));
            assertDateEquals(test.next(),
                    CashFlows.nextCashFlowDate(leg, test.include(), test.settlement()));
            assertEquals(test.previousAmount(),
                    CashFlows.previousCashFlowAmount(leg, test.include(), test.settlement()));
            assertEquals(test.nextAmount(),
                    CashFlows.nextCashFlowAmount(leg, test.include(), test.settlement()));
            assertEquals(isNullDate(test.next()),
                    CashFlows.isExpired(leg, test.include(), test.settlement()));
        }

        for (boolean include : new boolean[]{false, true}) {
            Settings.instance.setIncludeReferenceDateEvents(include);
            assertDateEquals(include ? today.substract(10) : today,
                    CashFlows.previousCashFlowDate(leg));
            assertDateEquals(include ? today : today.add(10),
                    CashFlows.nextCashFlowDate(leg));
            assertEquals(include ? 10.0 : 50.0, CashFlows.previousCashFlowAmount(leg));
            assertEquals(include ? 50.0 : 90.0, CashFlows.nextCashFlowAmount(leg));
            assertFalse(CashFlows.isExpired(leg));
            List<CashFlow> empty = new ArrayList<>();
            assertTrue(CashFlows.previousCashFlow(empty, include).isEmpty());
            assertTrue(CashFlows.nextCashFlow(empty, include).isEmpty());
            assertDateEquals(new Date(), CashFlows.previousCashFlowDate(empty, include));
            assertDateEquals(new Date(), CashFlows.nextCashFlowDate(empty, include));
            assertEquals(0.0, CashFlows.previousCashFlowAmount(empty, include));
            assertEquals(0.0, CashFlows.nextCashFlowAmount(empty, include));
            assertTrue(CashFlows.isExpired(empty, include));
        }
    }

    @Test
    public void testCouponInspectors() {
        Date start = new Date(1, Month.JANUARY, 2023), end = new Date(1, Month.JANUARY, 2024),
                payment = new Date(3, Month.JANUARY, 2024);
        Date settlement = start.add(100);
        Settings.instance.evaluationDate().equal(settlement);
        DayCounter dayCounter = new Actual365Fixed();
        FixedRateCoupon coupon = new FixedRateCoupon(payment, 100.0, 0.05, dayCounter,
                start, end, start, end);
        List<CashFlow> leg = leg(
                new SimpleCashFlow(7.0, settlement.substract(1)),
                new SimpleCashFlow(10.0, payment), coupon,
                new SimpleCashFlow(100.0, payment),
                new FixedRateCoupon(payment.add(365), 200.0, 0.06, dayCounter,
                        end, end.add(365)));
        assertDateEquals(start, CashFlows.startDate(leg));
        assertDateEquals(end.add(365), CashFlows.maturityDate(leg));
        for (Date date : new Date[]{new Date(), settlement}) {
            assertEquals(100.0, CashFlows.nominal(leg, false, date));
            assertDateEquals(start, CashFlows.accrualStartDate(leg, false, date));
            assertDateEquals(end, CashFlows.accrualEndDate(leg, false, date));
            assertDateEquals(start, CashFlows.referencePeriodStart(leg, false, date));
            assertDateEquals(end, CashFlows.referencePeriodEnd(leg, false, date));
            assertEquals(1.0, CashFlows.accrualPeriod(leg, false, date));
            assertEquals(365, CashFlows.accrualDays(leg, false, date));
            checkSmall(CashFlows.accruedPeriod(leg, false, date), 100.0 / 365.0, 1.0e-12);
            assertEquals(100, CashFlows.accruedDays(leg, false, date));
            checkSmall(CashFlows.accruedAmount(leg, false, date), 5.0 * 100.0 / 365.0, 1.0e-12);
        }
        assertEquals(100.0, CashFlows.nominal(leg, true, payment));
        assertEquals(200.0, CashFlows.nominal(leg, false, payment));
        assertDateEquals(end, CashFlows.accrualStartDate(leg, false, payment));
        assertDateEquals(end.add(365), CashFlows.referencePeriodEnd(leg, false, payment));

        List<CashFlow> redemption = leg(new SimpleCashFlow(100.0, payment));
        List<CashFlow> redemptionThenCoupon = leg(
                new SimpleCashFlow(100.0, settlement.add(1)), coupon);
        List<CashFlow> expired = leg(new FixedRateCoupon(settlement.substract(1), 100.0, 0.05,
                dayCounter, start.substract(365), start));
        for (List<CashFlow> noCoupon : List.of(new ArrayList<CashFlow>(), redemption,
                redemptionThenCoupon, expired)) {
            assertEquals(0.0, CashFlows.nominal(noCoupon, false));
            assertDateEquals(new Date(), CashFlows.accrualStartDate(noCoupon, false));
            assertDateEquals(new Date(), CashFlows.accrualEndDate(noCoupon, false));
            assertDateEquals(new Date(), CashFlows.referencePeriodStart(noCoupon, false));
            assertDateEquals(new Date(), CashFlows.referencePeriodEnd(noCoupon, false));
            assertEquals(0.0, CashFlows.accrualPeriod(noCoupon, false));
            assertEquals(0, CashFlows.accrualDays(noCoupon, false));
            assertEquals(0.0, CashFlows.accruedPeriod(noCoupon, false));
            assertEquals(0, CashFlows.accruedDays(noCoupon, false));
            assertEquals(0.0, CashFlows.accruedAmount(noCoupon, false));
        }
        IllegalArgumentException emptyStart = assertThrows(IllegalArgumentException.class,
                () -> CashFlows.startDate(new ArrayList<>()));
        assertTrue(emptyStart.getMessage().contains("empty leg"));
        IllegalArgumentException emptyMaturity = assertThrows(IllegalArgumentException.class,
                () -> CashFlows.maturityDate(new ArrayList<>()));
        assertTrue(emptyMaturity.getMessage().contains("empty leg"));
    }

    @Test
    public void testCouponRateAggregation() {
        Date start = new Date(1, Month.JANUARY, 2023), end = new Date(1, Month.JANUARY, 2024),
                payment = new Date(3, Month.JANUARY, 2024);
        FixedRateCoupon first = new FixedRateCoupon(payment, 100.0, 0.03,
                new Actual365Fixed(), start, end);
        FixedRateCoupon second = new FixedRateCoupon(payment, 100.0, 0.02,
                new Actual365Fixed(), start, end);
        List<CashFlow> leg = leg(new SimpleCashFlow(10.0, payment), first, second,
                new SimpleCashFlow(100.0, payment));
        checkSmall(CashFlows.nextCouponRate(leg, false, payment.substract(1)), 0.05, 1.0e-14);
        checkSmall(CashFlows.previousCouponRate(leg, false, payment), 0.05, 1.0e-14);
        checkSmall(CashFlows.nextCouponRate(leg, true, payment), 0.05, 1.0e-14);
        assertEquals(0.0, CashFlows.previousCouponRate(leg, true, payment));
        assertEquals(0.0, CashFlows.nextCouponRate(leg, false, payment));
        assertEquals(0.0, CashFlows.previousCouponRate(new ArrayList<>(), false, payment));
        assertEquals(0.0, CashFlows.nextCouponRate(new ArrayList<>(), false, payment));

        List<FixedRateCoupon> incompatible = List.of(
                new FixedRateCoupon(payment, 200.0, 0.02, new Actual365Fixed(), start, end),
                new FixedRateCoupon(payment, 100.0, 0.02, new Actual365Fixed(), start.add(1), end),
                new FixedRateCoupon(payment, 100.0, 0.02,
                        new ActualActual(ActualActual.Convention.ISMA), start, end));
        assertEquals(incompatible.get(2).accrualPeriod(), first.accrualPeriod());
        for (FixedRateCoupon coupon : incompatible) {
            List<CashFlow> invalid = leg(first, coupon);
            IllegalArgumentException e1 = assertThrows(IllegalArgumentException.class,
                    () -> CashFlows.nextCouponRate(invalid, false, payment.substract(1)));
            assertTrue(e1.getMessage().contains("cannot aggregate two different coupons"));
            IllegalArgumentException e2 = assertThrows(IllegalArgumentException.class,
                    () -> CashFlows.previousCouponRate(invalid, false, payment));
            assertTrue(e2.getMessage().contains("cannot aggregate two different coupons"));
        }
        List<CashFlow> redemption = leg(new SimpleCashFlow(100.0, payment));
        IllegalArgumentException e1 = assertThrows(IllegalArgumentException.class,
                () -> CashFlows.nextCouponRate(redemption, false, payment.substract(1)));
        assertTrue(e1.getMessage().contains("no coupon paid at cashflow date"));
        IllegalArgumentException e2 = assertThrows(IllegalArgumentException.class,
                () -> CashFlows.previousCouponRate(redemption, false, payment));
        assertTrue(e2.getMessage().contains("no coupon paid at cashflow date"));
    }

    @Test
    public void testPricingDefaultsAndOverloads() {
        Date settlement = new Date(15, Month.JANUARY, 2024);
        Settings.instance.evaluationDate().equal(settlement);
        Settings.instance.setIncludeTodaysCashFlows(Optional.empty());
        DayCounter dayCounter = new Actual365Fixed();
        InterestRate yield = new InterestRate(0.04, dayCounter, Compounding.Continuous,
                Frequency.ANNUAL);
        FlatForward curve = new FlatForward(settlement, 0.04, dayCounter, Compounding.Continuous);
        List<CashFlow> leg = leg(
                new SimpleCashFlow(20.0, settlement.substract(1)),
                new SimpleCashFlow(3.0, settlement),
                new FixedRateCoupon(settlement.add(365), 100.0, 0.05, dayCounter,
                        settlement, settlement.add(365)),
                new SimpleCashFlow(100.0, settlement.add(365)));
        double discount = Math.exp(-0.04);
        for (boolean include : new boolean[]{false, true}) {
            Settings.instance.setIncludeReferenceDateEvents(include);
            double expectedNpv = 105.0 * discount + (include ? 3.0 : 0.0);
            double expectedBps = 0.01 * discount;
            checkSmall(CashFlows.npv(leg, curve), expectedNpv, 1.0e-12);
            checkSmall(CashFlows.bps(leg, curve), expectedBps, 1.0e-12);
            CashFlows.NpvBps values = CashFlows.npvbps(leg, curve);
            checkSmall(values.npv(), expectedNpv, 1.0e-12);
            checkSmall(values.bps(), expectedBps, 1.0e-12);
            checkSmall(CashFlows.npv(leg, yield), expectedNpv, 1.0e-12);
            checkSmall(CashFlows.bps(leg, yield), expectedBps, 1.0e-12);
            checkSmall(CashFlows.bps(leg, 0.04, dayCounter, Compounding.Continuous,
                    Frequency.ANNUAL), expectedBps, 1.0e-12);
            checkSmall(CashFlows.atmRate(leg, curve), 0.05, 1.0e-12);

            Date npvDate = settlement.add(30);
            double npvDiscount = Math.exp(-0.04 * 30.0 / 365.0);
            CashFlows.NpvBps forwardValues = CashFlows.npvbps(leg, curve, include, settlement,
                    npvDate);
            checkSmall(forwardValues.npv(), expectedNpv / npvDiscount, 1.0e-12);
            checkSmall(forwardValues.bps(), expectedBps / npvDiscount, 1.0e-12);
            checkSmall(CashFlows.npv(leg, curve, include, settlement, npvDate),
                    expectedNpv / npvDiscount, 1.0e-12);
            checkSmall(CashFlows.bps(leg, curve, include, settlement, npvDate),
                    expectedBps / npvDiscount, 1.0e-12);
            double targetNpv = (106.0 * discount + (include ? 3.0 : 0.0)) / npvDiscount;
            checkSmall(CashFlows.atmRate(leg, curve, include, settlement, npvDate, targetNpv),
                    0.06, 1.0e-12);
        }

        List<CashFlow> future = leg(leg.get(2), leg.get(3));
        double expectedNpv = 105.0 * discount;
        checkSmall(CashFlows.duration(future, yield, Duration.Type.Simple, false), 1.0, 1.0e-12);
        checkSmall(CashFlows.duration(future, yield, Duration.Type.Modified, false), 1.0, 1.0e-12);
        checkSmall(CashFlows.convexity(future, yield, false), 1.0, 1.0e-12);
        checkSmall(CashFlows.convexity(future, 0.04, dayCounter, Compounding.Continuous,
                Frequency.ANNUAL, false), 1.0, 1.0e-12);
        double expectedBpv = expectedNpv * (-0.0001 + 0.5 * 0.0001 * 0.0001);
        checkSmall(CashFlows.basisPointValue(future, yield, false), expectedBpv, 1.0e-12);
        checkSmall(CashFlows.basisPointValue(future, 0.04, dayCounter, Compounding.Continuous,
                Frequency.ANNUAL, false), expectedBpv, 1.0e-12);
        double expectedYvbp = -0.01 / expectedNpv;
        checkSmall(CashFlows.yieldValueBasisPoint(future, yield, false), expectedYvbp, 1.0e-12);
        checkSmall(CashFlows.yieldValueBasisPoint(future, 0.04, dayCounter,
                Compounding.Continuous, Frequency.ANNUAL, false), expectedYvbp, 1.0e-12);
        checkSmall(CashFlows.yield(future, expectedNpv, dayCounter, Compounding.Continuous,
                Frequency.ANNUAL, false), 0.04, 1.0e-10);
        double expectedSpreadNpv = 105.0 * Math.exp(-0.05);
        checkSmall(CashFlows.npv(future, curve, 0.01, Compounding.Continuous,
                Frequency.ANNUAL, false), expectedSpreadNpv, 1.0e-12);
        checkSmall(CashFlows.zSpread(future, expectedSpreadNpv, curve, Compounding.Continuous,
                Frequency.ANNUAL, false), 0.01, 1.0e-10);
    }

    @Test
    public void testPricingEmptyLegsAndErrors() {
        Date settlement = new Date(15, Month.JANUARY, 2024);
        Settings.instance.evaluationDate().equal(settlement);
        InterestRate yield = new InterestRate(0.04, new Actual365Fixed(),
                Compounding.Compounded, Frequency.SEMIANNUAL);
        FlatForward curve = new FlatForward(settlement, 0.04, new Actual365Fixed());
        List<CashFlow> empty = new ArrayList<>();
        assertEquals(0.0, CashFlows.npv(empty, curve, false));
        assertEquals(0.0, CashFlows.bps(empty, curve, false));
        CashFlows.NpvBps values = CashFlows.npvbps(empty, curve, false);
        assertEquals(0.0, values.npv());
        assertEquals(0.0, values.bps());
        assertEquals(0.0, CashFlows.atmRate(empty, curve, false));
        assertEquals(0.0, CashFlows.npv(empty, yield, false));
        assertEquals(0.0, CashFlows.bps(empty, yield, false));
        assertEquals(0.0, CashFlows.yieldValueBasisPoint(empty, yield, false));
        assertEquals(0.0, CashFlows.npv(empty, curve, 0.01, Compounding.Continuous,
                Frequency.ANNUAL, false));

        List<CashFlow> redemption = leg(new SimpleCashFlow(100.0, settlement.add(365)));
        assertEquals(0.0, CashFlows.atmRate(redemption, curve, false));
        IllegalArgumentException nullBps = assertThrows(IllegalArgumentException.class,
                () -> CashFlows.atmRate(redemption, curve, false, settlement, settlement,
                        101.0));
        assertTrue(nullBps.getMessage().contains("null bps"));
        IllegalArgumentException unknown = assertThrows(IllegalArgumentException.class,
                () -> CashFlows.duration(redemption, yield, (Duration.Type) null, false));
        assertTrue(unknown.getMessage().contains("unknown duration type"));
        InterestRate continuous = new InterestRate(0.04, new Actual365Fixed(),
                Compounding.Continuous, Frequency.ANNUAL);
        IllegalArgumentException compounded = assertThrows(IllegalArgumentException.class,
                () -> CashFlows.duration(redemption, continuous, Duration.Type.Macaulay, false));
        assertTrue(compounded.getMessage().contains("compounded rate required"));
        IllegalArgumentException sign = assertThrows(IllegalArgumentException.class,
                () -> CashFlows.yield(redemption, -100.0, new Actual365Fixed(),
                        Compounding.Compounded, Frequency.ANNUAL, false));
        assertTrue(sign.getMessage().contains("due to their sign"));
    }

    @Test
    public void testSettings() {
        Date today = Date.todaysDate();
        Settings.instance.evaluationDate().equal(today);

        // cash flows at T+0, T+1, T+2
        List<CashFlow> leg = new ArrayList<>();
        for (int i = 0; i < 3; ++i)
            leg.add(new SimpleCashFlow(1.0, today.add(i)));

        // case 1: don't include reference-date payments, no override at
        //         today's date
        Settings.instance.setIncludeReferenceDateEvents(false);
        Settings.instance.setIncludeTodaysCashFlows(Optional.empty());
        checkInclusion(leg, today, 0, 0, false);
        checkInclusion(leg, today, 0, 1, false);
        checkInclusion(leg, today, 1, 0, true);
        checkInclusion(leg, today, 1, 1, false);
        checkInclusion(leg, today, 1, 2, false);
        checkInclusion(leg, today, 2, 1, true);
        checkInclusion(leg, today, 2, 2, false);
        checkInclusion(leg, today, 2, 3, false);

        // case 2: same, but with explicit setting at today's date
        Settings.instance.setIncludeReferenceDateEvents(false);
        Settings.instance.setIncludeTodaysCashFlows(false);
        checkInclusion(leg, today, 0, 0, false);
        checkInclusion(leg, today, 0, 1, false);
        checkInclusion(leg, today, 1, 0, true);
        checkInclusion(leg, today, 1, 1, false);
        checkInclusion(leg, today, 1, 2, false);
        checkInclusion(leg, today, 2, 1, true);
        checkInclusion(leg, today, 2, 2, false);
        checkInclusion(leg, today, 2, 3, false);

        // case 3: do include reference-date payments, no override at
        //         today's date
        Settings.instance.setIncludeReferenceDateEvents(true);
        Settings.instance.setIncludeTodaysCashFlows(Optional.empty());
        checkInclusion(leg, today, 0, 0, true);
        checkInclusion(leg, today, 0, 1, false);
        checkInclusion(leg, today, 1, 0, true);
        checkInclusion(leg, today, 1, 1, true);
        checkInclusion(leg, today, 1, 2, false);
        checkInclusion(leg, today, 2, 1, true);
        checkInclusion(leg, today, 2, 2, true);
        checkInclusion(leg, today, 2, 3, false);

        // case 4: do include reference-date payments, explicit (and same)
        //         setting at today's date
        Settings.instance.setIncludeReferenceDateEvents(true);
        Settings.instance.setIncludeTodaysCashFlows(true);
        checkInclusion(leg, today, 0, 0, true);
        checkInclusion(leg, today, 0, 1, false);
        checkInclusion(leg, today, 1, 0, true);
        checkInclusion(leg, today, 1, 1, true);
        checkInclusion(leg, today, 1, 2, false);
        checkInclusion(leg, today, 2, 1, true);
        checkInclusion(leg, today, 2, 2, true);
        checkInclusion(leg, today, 2, 3, false);

        // case 5: do include reference-date payments, override at today's date
        Settings.instance.setIncludeReferenceDateEvents(true);
        Settings.instance.setIncludeTodaysCashFlows(false);
        checkInclusion(leg, today, 0, 0, false);
        checkInclusion(leg, today, 0, 1, false);
        checkInclusion(leg, today, 1, 0, true);
        checkInclusion(leg, today, 1, 1, true);
        checkInclusion(leg, today, 1, 2, false);
        checkInclusion(leg, today, 2, 1, true);
        checkInclusion(leg, today, 2, 2, true);
        checkInclusion(leg, today, 2, 3, false);

        // no discount to make calculations easier
        InterestRate noDiscount = new InterestRate(0.0, new Actual365Fixed(),
                Compounding.Continuous, Frequency.ANNUAL);

        // no override
        Settings.instance.setIncludeTodaysCashFlows(Optional.empty());
        checkNpv(leg, noDiscount, today, false, 2.0);
        checkNpv(leg, noDiscount, today, true, 3.0);

        // override
        Settings.instance.setIncludeTodaysCashFlows(false);
        checkNpv(leg, noDiscount, today, false, 2.0);
        checkNpv(leg, noDiscount, today, true, 2.0);
    }

    private static void checkInclusion(List<CashFlow> leg, Date today, int n, int days,
                                       boolean expected) {
        assertEquals(expected, !leg.get(n).hasOccurred(today.add(days)),
                "cashflow at T+" + n + (expected ? " not" : "") + "included at T+" + days);
    }

    private static void checkNpv(List<CashFlow> leg, InterestRate noDiscount, Date today,
                                 boolean includeRef, double expected) {
        double NPV = CashFlows.npv(leg, noDiscount, includeRef, today);
        assertEquals(expected, NPV, 1.0e-6);
    }

    @Test
    public void testAccessViolation() {
        Date todaysDate = new Date(7, Month.APRIL, 2010);
        Date settlementDate = new Date(9, Month.APRIL, 2010);
        Settings.instance.evaluationDate().equal(todaysDate);
        Calendar calendar = new Target();

        Handle<YieldTermStructure> rhTermStructure = new Handle<>(
                new FlatForward(settlementDate, 0.04875825, new Actual365Fixed()), true);

        double volatility = 0.10;
        Handle<jquant.termstructures.volatility.optionlet.OptionletVolatilityStructure> vol =
                new Handle<>(new ConstantOptionletVolatility(
                        2, calendar, BusinessDayConvention.MODIFIED_FOLLOWING,
                        volatility, new Actual365Fixed()), true);

        IborIndex index3m = new USDLibor(new Period(3, TimeUnit.MONTHS), rhTermStructure);

        Date payDate = new Date(20, Month.DECEMBER, 2013);
        Date startDate = new Date(20, Month.SEPTEMBER, 2013);
        Date endDate = new Date(20, Month.DECEMBER, 2013);
        double spread = 0.0115;
        IborCouponPricer pricer = new BlackIborCouponPricer(vol);
        FloatingRateCoupon coupon = new FloatingRateCoupon(payDate, 100, startDate, endDate, 2,
                index3m, 1.0, spread / 100);
        coupon.setPricer(pricer);

        try {
            // this caused an access violation in version 1.0
            coupon.amount();
        } catch (IllegalArgumentException expectedError) {
            // ok; proper exception thrown.  This mirrors the official test,
            // which catches QuantLib::Error here: the pricer requires an
            // IborCoupon and the coupon above is a plain FloatingRateCoupon.
        }
    }

    @Test
    public void testDefaultSettlementDate() {
        Date today = Settings.instance.evaluationDate().Date();
        Schedule schedule = new MakeSchedule()
                .from(today.add(new Period(-2, TimeUnit.MONTHS)))
                .to(today.add(new Period(4, TimeUnit.MONTHS)))
                .withFrequency(Frequency.SEMIANNUAL)
                .withCalendar(new Target())
                .withConvention(BusinessDayConvention.UNADJUSTED)
                .backwards()
                .toSchedule();

        List<CashFlow> leg = new FixedRateLeg(schedule)
                .withNotionals(100.0)
                .withCouponRates(0.03, new Actual360())
                .withPaymentCalendar(new Target())
                .withPaymentAdjustment(BusinessDayConvention.FOLLOWING)
                .leg();

        assertFalse(CashFlows.accruedPeriod(leg, false) == 0.0,
                "null accrued period with default settlement date");
        assertFalse(CashFlows.accruedDays(leg, false) == 0,
                "no accrued days with default settlement date");
        assertFalse(CashFlows.accruedAmount(leg, false) == 0.0,
                "null accrued amount with default settlement date");
    }

    @Test
    public void testNullFixingDays() {
        boolean previousAtPar = IborCoupon.Settings.instance().usingAtParCoupons();
        try {
            IborCoupon.Settings.instance().createAtParCoupons();
            Date today = Settings.instance.evaluationDate().Date();
            Schedule schedule = new MakeSchedule()
                    .from(today.add(new Period(-2, TimeUnit.MONTHS)))
                    .to(today.add(new Period(4, TimeUnit.MONTHS)))
                    .withFrequency(Frequency.SEMIANNUAL)
                    .withCalendar(new Target())
                    .withConvention(BusinessDayConvention.FOLLOWING)
                    .backwards()
                    .toSchedule();

            IborIndex index = new USDLibor(new Period(6, TimeUnit.MONTHS));
            // this can happen with default values, and caused an
            // exception when the null was not managed properly
            IborLeg leg = new IborLeg(schedule, index)
                    .withNotionals(100.0)
                    .withFixingDays((Integer) null);
            assertNotNull(leg.leg());
        } finally {
            if (previousAtPar)
                IborCoupon.Settings.instance().createAtParCoupons();
            else
                IborCoupon.Settings.instance().createIndexedCoupons();
        }
    }

    @Test
    public void testExCouponDates() {
        Date today = Date.todaysDate();
        Schedule schedule = new MakeSchedule()
                .from(today)
                .to(today.add(new Period(5, TimeUnit.YEARS)))
                .withFrequency(Frequency.MONTHLY)
                .withCalendar(new Target())
                .withConvention(BusinessDayConvention.FOLLOWING)
                .toSchedule();

        // no ex-coupon dates
        List<CashFlow> l1 = new FixedRateLeg(schedule).withNotionals(100.0)
                .withCouponRates(0.03, new Actual360()).leg();
        for (CashFlow i : l1) {
            Coupon c = (Coupon) i;
            if (!isNullDate(c.exCouponDate()))
                throw new AssertionError("ex-coupon date found (none expected)");
        }

        // same for floating legs
        IborIndex index = new Euribor3M();
        List<CashFlow> l2 = new IborLeg(schedule, index).withNotionals(100.0).leg();
        for (CashFlow i : l2) {
            Coupon c = (Coupon) i;
            if (!isNullDate(c.exCouponDate()))
                throw new AssertionError("ex-coupon date found (none expected)");
        }

        // calendar days
        List<CashFlow> l5 = new FixedRateLeg(schedule)
                .withNotionals(100.0)
                .withCouponRates(0.03, new Actual360())
                .withExCouponPeriod(new Period(2, TimeUnit.DAYS), new NullCalendar(),
                        BusinessDayConvention.UNADJUSTED, false)
                .leg();
        for (CashFlow i : l5) {
            Coupon c = (Coupon) i;
            Date expected = c.accrualEndDate().substract(2);
            assertDateEquals(expected, c.exCouponDate());
        }

        List<CashFlow> l6 = new IborLeg(schedule, index)
                .withNotionals(100.0)
                .withExCouponPeriod(new Period(2, TimeUnit.DAYS), new NullCalendar(),
                        BusinessDayConvention.UNADJUSTED, false)
                .leg();
        for (CashFlow i : l6) {
            Coupon c = (Coupon) i;
            Date expected = c.accrualEndDate().substract(2);
            assertDateEquals(expected, c.exCouponDate());
        }

        // business days
        List<CashFlow> l7 = new FixedRateLeg(schedule)
                .withNotionals(100.0)
                .withCouponRates(0.03, new Actual360())
                .withExCouponPeriod(new Period(2, TimeUnit.DAYS), new Target(),
                        BusinessDayConvention.PRECEDING, false)
                .leg();
        for (CashFlow i : l7) {
            Coupon c = (Coupon) i;
            Date expected = new Target().advance(c.accrualEndDate(), -2, TimeUnit.DAYS);
            assertDateEquals(expected, c.exCouponDate());
        }

        List<CashFlow> l8 = new IborLeg(schedule, index)
                .withNotionals(100.0)
                .withExCouponPeriod(new Period(2, TimeUnit.DAYS), new Target(),
                        BusinessDayConvention.PRECEDING, false)
                .leg();
        for (CashFlow i : l8) {
            Coupon c = (Coupon) i;
            Date expected = new Target().advance(c.accrualEndDate(), -2, TimeUnit.DAYS);
            assertDateEquals(expected, c.exCouponDate());
        }
    }

    @Test
    public void testIrregularFirstCouponReferenceDatesAtEndOfMonth() {
        Schedule schedule = new MakeSchedule()
                .from(new Date(17, Month.JANUARY, 2017))
                .to(new Date(28, Month.FEBRUARY, 2018))
                .withFrequency(Frequency.SEMIANNUAL)
                .withConvention(BusinessDayConvention.UNADJUSTED)
                .endOfMonth()
                .backwards()
                .toSchedule();

        List<CashFlow> leg = new FixedRateLeg(schedule)
                .withNotionals(100.0)
                .withCouponRates(0.01, new Actual360())
                .leg();

        Coupon firstCoupon = (Coupon) leg.get(0);
        assertDateEquals(new Date(31, Month.AUGUST, 2016),
                firstCoupon.referencePeriodStart());
    }

    @Test
    public void testIrregularFirstCouponReferenceDatesAtEndOfCalendarMonth() {
        Schedule schedule = new MakeSchedule()
                .withCalendar(new UnitedStates(UnitedStates.Market.GovernmentBond))
                .from(new Date(30, Month.SEPTEMBER, 2017))
                .to(new Date(30, Month.SEPTEMBER, 2022))
                .withTenor(new Period(6, TimeUnit.MONTHS))
                .withConvention(BusinessDayConvention.UNADJUSTED)
                .withTerminationDateConvention(BusinessDayConvention.UNADJUSTED)
                .withFirstDate(new Date(31, Month.MARCH, 2018))
                .withNextToLastDate(new Date(31, Month.MARCH, 2022))
                .endOfMonth()
                .backwards()
                .toSchedule();

        List<CashFlow> leg = new FixedRateLeg(schedule)
                .withNotionals(100.0)
                .withCouponRates(0.01875,
                        new ActualActual(ActualActual.Convention.ISMA))
                .leg();

        Coupon firstCoupon = (Coupon) leg.get(0);
        assertDateEquals(new Date(30, Month.SEPTEMBER, 2017),
                firstCoupon.referencePeriodStart());
        // Expect first cashflow to be 0.9375
        assertEquals(0.9375, firstCoupon.amount(), 0.0001);
    }

    @Test
    public void testIrregularLastCouponReferenceDatesAtEndOfMonth() {
        Schedule schedule = new MakeSchedule()
                .from(new Date(17, Month.JANUARY, 2017))
                .to(new Date(15, Month.SEPTEMBER, 2018))
                .withNextToLastDate(new Date(28, Month.FEBRUARY, 2018))
                .withFrequency(Frequency.SEMIANNUAL)
                .withConvention(BusinessDayConvention.UNADJUSTED)
                .endOfMonth()
                .backwards()
                .toSchedule();

        List<CashFlow> leg = new FixedRateLeg(schedule)
                .withNotionals(100.0)
                .withCouponRates(0.01, new Actual360())
                .leg();

        Coupon lastCoupon = (Coupon) leg.get(leg.size() - 1);
        assertDateEquals(new Date(31, Month.AUGUST, 2018),
                lastCoupon.referencePeriodEnd());
    }

    @Test
    public void testPartialScheduleLegConstruction() {
        Schedule schedule = new MakeSchedule()
                .from(new Date(15, Month.SEPTEMBER, 2017))
                .to(new Date(30, Month.SEPTEMBER, 2020))
                .withNextToLastDate(new Date(25, Month.SEPTEMBER, 2020))
                .withFrequency(Frequency.SEMIANNUAL)
                .backwards()
                .toSchedule();
        // same schedule, date based, with metadata
        Schedule schedule2 = new Schedule(schedule.dates(), new NullCalendar(),
                BusinessDayConvention.UNADJUSTED, Optional.of(BusinessDayConvention.UNADJUSTED),
                Optional.of(new Period(6, TimeUnit.MONTHS)), Optional.empty(),
                Optional.of(schedule.endOfMonth()), schedule.isRegular());
        // same schedule, date based, without metadata
        Schedule schedule3 = new Schedule(schedule.dates());

        // fixed rate legs based on the three schedules
        List<CashFlow> leg = new FixedRateLeg(schedule).withNotionals(100.0)
                .withCouponRates(0.01, new ActualActual(ActualActual.Convention.ISMA)).leg();
        List<CashFlow> leg2 = new FixedRateLeg(schedule2).withNotionals(100.0)
                .withCouponRates(0.01, new ActualActual(ActualActual.Convention.ISMA)).leg();
        List<CashFlow> leg3 = new FixedRateLeg(schedule3).withNotionals(100.0)
                .withCouponRates(0.01, new ActualActual(ActualActual.Convention.ISMA)).leg();

        checkPartialFixedLeg(leg, new Date(25, Month.MARCH, 2017),
                new Date(25, Month.SEPTEMBER, 2017),
                new Date(25, Month.SEPTEMBER, 2020),
                new Date(25, Month.MARCH, 2021));
        checkPartialFixedLeg(leg2, new Date(25, Month.MARCH, 2017),
                new Date(25, Month.SEPTEMBER, 2017),
                new Date(25, Month.SEPTEMBER, 2020),
                new Date(25, Month.MARCH, 2021));
        checkPartialFixedLeg(leg3, new Date(15, Month.SEPTEMBER, 2017),
                new Date(25, Month.SEPTEMBER, 2017),
                new Date(25, Month.SEPTEMBER, 2020),
                new Date(30, Month.SEPTEMBER, 2020));

        // same check as above for a floating leg
        IborIndex iborIndex = new USDLibor(new Period(3, TimeUnit.MONTHS));
        DayCounter dc = new ActualActual(ActualActual.Convention.ISMA);
        List<CashFlow> legf = new IborLeg(schedule, iborIndex).withNotionals(100.0)
                .withPaymentDayCounter(dc).leg();
        List<CashFlow> legf2 = new IborLeg(schedule2, iborIndex).withNotionals(100.0)
                .withPaymentDayCounter(dc).leg();
        List<CashFlow> legf3 = new IborLeg(schedule3, iborIndex).withNotionals(100.0)
                .withPaymentDayCounter(dc).leg();

        checkPartialFloatingLeg(legf, new Date(25, Month.MARCH, 2017),
                new Date(25, Month.SEPTEMBER, 2017),
                new Date(25, Month.SEPTEMBER, 2020),
                new Date(25, Month.MARCH, 2021));
        checkPartialFloatingLeg(legf2, new Date(25, Month.MARCH, 2017),
                new Date(25, Month.SEPTEMBER, 2017),
                new Date(25, Month.SEPTEMBER, 2020),
                new Date(25, Month.MARCH, 2021));
        checkPartialFloatingLeg(legf3, new Date(15, Month.SEPTEMBER, 2017),
                new Date(25, Month.SEPTEMBER, 2017),
                new Date(25, Month.SEPTEMBER, 2020),
                new Date(30, Month.SEPTEMBER, 2020));
    }

    private static void checkPartialFixedLeg(List<CashFlow> leg, Date firstRefStart,
                                             Date firstRefEnd, Date lastRefStart,
                                             Date lastRefEnd) {
        FixedRateCoupon firstCpn = (FixedRateCoupon) leg.get(0);
        FixedRateCoupon lastCpn = (FixedRateCoupon) leg.get(leg.size() - 1);
        assertDateEquals(firstRefStart, firstCpn.referencePeriodStart());
        assertDateEquals(firstRefEnd, firstCpn.referencePeriodEnd());
        assertDateEquals(lastRefStart, lastCpn.referencePeriodStart());
        assertDateEquals(lastRefEnd, lastCpn.referencePeriodEnd());
    }

    private static void checkPartialFloatingLeg(List<CashFlow> leg, Date firstRefStart,
                                                Date firstRefEnd, Date lastRefStart,
                                                Date lastRefEnd) {
        FloatingRateCoupon firstCpn = (FloatingRateCoupon) leg.get(0);
        FloatingRateCoupon lastCpn = (FloatingRateCoupon) leg.get(leg.size() - 1);
        assertDateEquals(firstRefStart, firstCpn.referencePeriodStart());
        assertDateEquals(firstRefEnd, firstCpn.referencePeriodEnd());
        assertDateEquals(lastRefStart, lastCpn.referencePeriodStart());
        assertDateEquals(lastRefEnd, lastCpn.referencePeriodEnd());
    }

    @Test
    public void testFixedIborCouponWithoutForecastCurve() {
        Date today = Settings.instance.evaluationDate().Date();

        IborIndex index = new USDLibor(new Period(6, TimeUnit.MONTHS));
        Calendar calendar = index.fixingCalendar();

        Date fixingDate = calendar.advance(today, -2, TimeUnit.MONTHS);
        double pastFixing = 0.01;
        index.addFixing(fixingDate, pastFixing, false);

        Date startDate = index.valueDate(fixingDate);
        Date endDate = index.maturityDate(fixingDate);

        IborCoupon coupon = new IborCoupon(endDate, 100.0, startDate, endDate,
                index.fixingDays(), index);
        coupon.setPricer(new BlackIborCouponPricer());

        assertDoesNotThrow(coupon::amount);

        // the main check is the one above, but let's check for consistency too:
        double amount = coupon.amount();
        double expected = pastFixing * coupon.nominal() * coupon.accrualPeriod();
        assertEquals(expected, amount, 1.0e-8);
    }

    private static IborCoupon iborCouponForFixingDate(IborIndex index, Date fixingDate) {
        Date startDate = index.valueDate(fixingDate);
        Date endDate = index.maturityDate(fixingDate);

        IborCoupon coupon = new IborCoupon(endDate, 100.0, startDate, endDate,
                index.fixingDays(), index);
        coupon.setPricer(new BlackIborCouponPricer());

        return coupon;
    }

    @Test
    public void testIborCouponKnowsWhenitHasFixed() {
        // The official test leaves the global setting at true; restore it so
        // that JUnit's unspecified method order cannot leak it into other cases.
        boolean previousEnforcesTodaysHistoricFixings =
                Settings.instance.enforcesTodaysHistoricFixings();
        try {
        Date today = Settings.instance.evaluationDate().Date();

        IborIndex index = new Euribor3M();
        Calendar calendar = index.fixingCalendar();

        {
            IborCoupon coupon = iborCouponForFixingDate(index,
                    calendar.advance(today, -1, TimeUnit.DAYS));
            index.clearFixings();
            // this should not throw an exception if the fixing is missing
            assertEquals(true, coupon.hasFixed());
            // but this should
            assertThrows(IllegalArgumentException.class, coupon::rate);
        }

        {
            IborCoupon coupon = iborCouponForFixingDate(index, today);
            Settings.instance.setEnforcesTodaysHistoricFixings(false);
            index.clearFixings();
            assertEquals(false, coupon.hasFixed());
        }

        {
            IborCoupon coupon = iborCouponForFixingDate(index, today);
            Settings.instance.setEnforcesTodaysHistoricFixings(false);
            index.addFixing(coupon.fixingDate(), 0.01, false);
            assertEquals(true, coupon.hasFixed());
        }

        {
            IborCoupon coupon = iborCouponForFixingDate(index, today);
            Settings.instance.setEnforcesTodaysHistoricFixings(true);
            index.clearFixings();
            assertEquals(true, coupon.hasFixed());
            assertThrows(IllegalArgumentException.class, coupon::rate);
        }

        {
            IborCoupon coupon = iborCouponForFixingDate(index,
                    calendar.advance(today, 1, TimeUnit.DAYS));
            assertEquals(false, coupon.hasFixed());
        }
        } finally {
            Settings.instance.setEnforcesTodaysHistoricFixings(
                    previousEnforcesTodaysHistoricFixings);
        }
    }

    @Test
    public void testInterpolatedIborStubCoupon() {
        SavedSettings backup = new SavedSettings();
        try {
            Date today = new Date(27, Month.MAY, 2026);
            Settings.instance.evaluationDate().equal(today);

            RelinkableHandle<YieldTermStructure> twoMonthCurve = new RelinkableHandle<>();
            RelinkableHandle<YieldTermStructure> threeMonthCurve = new RelinkableHandle<>();
            twoMonthCurve.linkTo(new FlatForward(today, 0.02, new Actual365Fixed()), true);
            threeMonthCurve.linkTo(new FlatForward(today, 0.04, new Actual365Fixed()), true);

            Calendar calendar = new NewZealand();
            IborIndex bkbm2m = new IborIndex(
                    "Bkbm", new Period(2, TimeUnit.MONTHS), 0, Currencies.nzd(), calendar,
                    BusinessDayConvention.MODIFIED_FOLLOWING, false,
                    new Actual365Fixed(), twoMonthCurve);
            IborIndex bkbm3m = new IborIndex(
                    "Bkbm", new Period(3, TimeUnit.MONTHS), 0, Currencies.nzd(), calendar,
                    BusinessDayConvention.MODIFIED_FOLLOWING, false,
                    new Actual365Fixed(), threeMonthCurve);
            Date start = new Date(29, Month.MAY, 2026);
            Date maturity = new Date(28, Month.FEBRUARY, 2027);

            Schedule schedule = new MakeSchedule()
                    .from(start)
                    .to(maturity)
                    .withTenor(new Period(3, TimeUnit.MONTHS))
                    .withCalendar(calendar)
                    .withConvention(BusinessDayConvention.MODIFIED_FOLLOWING)
                    .backwards()
                    .toSchedule();
            assertFalse(schedule.isRegular(1));
            assertDateEquals(new Date(28, Month.AUGUST, 2026), schedule.date(1));

            StubIndexSelection convention = new StubIndexSelection(
                    StubIndexSelection.Convention.Interpolated, List.of(bkbm2m, bkbm3m));
            List<CashFlow> leg = new IborLeg(schedule, bkbm3m)
                    .withNotionals(1.0)
                    .withIndexedCoupons(true)
                    .withStubIndexSelection(convention)
                    .leg();

            assertTrue(leg.get(0) instanceof StubIborCoupon, "first coupon should be a stub");
            StubIborCoupon coupon = (StubIborCoupon) leg.get(0);
            assertFalse(leg.get(1) instanceof StubIborCoupon,
                    "second coupon should not be a stub");

            Date fixingDate = coupon.fixingDate();
            Date shortMaturity = bkbm2m.maturityDate(start);
            Date longMaturity = bkbm3m.maturityDate(start);
            assertDateEquals(new Date(29, Month.JULY, 2026), shortMaturity);
            assertDateEquals(new Date(31, Month.AUGUST, 2026), longMaturity);

            double shortRate = bkbm2m.fixing(fixingDate, true);
            double longRate = bkbm3m.fixing(fixingDate, true);
            double weight = dayCount(shortMaturity, coupon.accrualEndDate())
                    / dayCount(shortMaturity, longMaturity);
            double expected = shortRate + (longRate - shortRate) * weight;
            checkSmall(coupon.indexFixing(), expected, 1.0e-14);

            StubIndexSelection closest = new StubIndexSelection(
                    StubIndexSelection.Convention.ClosestIndex, List.of(bkbm2m, bkbm3m));
            List<CashFlow> closestLeg = new IborLeg(schedule, bkbm3m)
                    .withNotionals(1.0)
                    .withIndexedCoupons(true)
                    .withStubIndexSelection(closest)
                    .leg();
            assertTrue(closestLeg.get(0) instanceof StubIborCoupon);
            StubIborCoupon closestCoupon = (StubIborCoupon) closestLeg.get(0);
            checkSmall(closestCoupon.indexFixing(), longRate, 1.0e-14);

            // stub index conventions require indexed coupons...
            assertThrows(IllegalArgumentException.class, () -> new IborLeg(schedule, bkbm3m)
                    .withNotionals(1.0)
                    .withIndexedCoupons(false)
                    .withStubIndexSelection(convention)
                    .leg());

            // ...and candidates with distinct maturities
            StubIndexSelection duplicate = new StubIndexSelection(
                    StubIndexSelection.Convention.Interpolated,
                    List.of(bkbm2m, bkbm3m, bkbm3m));
            assertThrows(IllegalArgumentException.class, () -> new IborLeg(schedule, bkbm3m)
                    .withNotionals(1.0)
                    .withIndexedCoupons(true)
                    .withStubIndexSelection(duplicate)
                    .leg());

            // a default-constructed configuration is empty and leaves the leg alone
            assertTrue(new StubIndexSelection().empty());
            List<CashFlow> defaultLeg = new IborLeg(schedule, bkbm3m)
                    .withNotionals(1.0)
                    .withIndexedCoupons(true)
                    .withStubIndexSelection(new StubIndexSelection())
                    .leg();
            assertFalse(defaultLeg.get(0) instanceof StubIborCoupon);

            // configurations without usable candidates are rejected on construction
            assertThrows(IllegalArgumentException.class, () -> new StubIndexSelection(
                    StubIndexSelection.Convention.Interpolated, new ArrayList<>()));
            List<IborIndex> withNull = new ArrayList<>();
            withNull.add(bkbm2m);
            withNull.add(null);
            assertThrows(IllegalArgumentException.class, () -> new StubIndexSelection(
                    StubIndexSelection.Convention.Interpolated, withNull));
        } finally {
            backup.restore();
        }
    }

    @Test
    public void testIborStubInterpolationEndpointsAndRegularSchedule() {
        SavedSettings backup = new SavedSettings();
        try {
            Date today = new Date(27, Month.MAY, 2026);
            Settings.instance.evaluationDate().equal(today);

            Handle<YieldTermStructure> twoMonthCurve = new Handle<>(
                    new FlatForward(today, 0.02, new Actual365Fixed()), true);
            Handle<YieldTermStructure> threeMonthCurve = new Handle<>(
                    new FlatForward(today, 0.04, new Actual365Fixed()), true);
            IborIndex bkbm2m = new Bkbm2M(twoMonthCurve);
            IborIndex bkbm3m = new Bkbm3M(threeMonthCurve);
            Date start = new Date(29, Month.MAY, 2026);
            StubIndexSelection convention = new StubIndexSelection(
                    StubIndexSelection.Convention.Interpolated, List.of(bkbm2m, bkbm3m));

            for (IborIndex endpoint : List.of(bkbm2m, bkbm3m)) {
                Date end = endpoint.maturityDate(start);
                Schedule endpointSchedule = new Schedule(List.of(start, end),
                        endpoint.fixingCalendar(), BusinessDayConvention.MODIFIED_FOLLOWING,
                        Optional.empty(), Optional.of(new Period(3, TimeUnit.MONTHS)),
                        Optional.of(DateGenerationRule.BACKWARD), Optional.of(true),
                        List.of(false));
                List<CashFlow> leg = new IborLeg(endpointSchedule, bkbm3m)
                        .withNotionals(1.0)
                        .withIndexedCoupons(true)
                        .withStubIndexSelection(convention)
                        .leg();
                assertTrue(leg.get(0) instanceof StubIborCoupon);
                StubIborCoupon coupon = (StubIborCoupon) leg.get(0);
                checkSmall(coupon.indexFixing(),
                        endpoint.fixing(coupon.fixingDate(), true), 1.0e-14);
            }

            Date regularEnd = bkbm3m.maturityDate(start);
            Schedule regularSchedule = new Schedule(List.of(start, regularEnd),
                    bkbm3m.fixingCalendar(), BusinessDayConvention.MODIFIED_FOLLOWING,
                    Optional.empty(), Optional.of(new Period(3, TimeUnit.MONTHS)),
                    Optional.of(DateGenerationRule.BACKWARD), Optional.of(true),
                    List.of(true));
            List<CashFlow> defaultLeg = new IborLeg(regularSchedule, bkbm3m)
                    .withNotionals(1.0)
                    .withIndexedCoupons(true)
                    .leg();
            List<CashFlow> configuredLeg = new IborLeg(regularSchedule, bkbm3m)
                    .withNotionals(1.0)
                    .withIndexedCoupons(true)
                    .withStubIndexSelection(convention)
                    .leg();
            assertFalse(configuredLeg.get(0) instanceof StubIborCoupon);
            IborCoupon defaultCoupon = (IborCoupon) defaultLeg.get(0);
            IborCoupon configuredCoupon = (IborCoupon) configuredLeg.get(0);
            checkSmall(defaultCoupon.rate(), configuredCoupon.rate(), 1.0e-14);
        } finally {
            backup.restore();
        }
    }

    @Test
    public void testFinalAndLongIborStubCoupons() {
        SavedSettings backup = new SavedSettings();
        try {
            Date today = new Date(27, Month.MAY, 2026);
            Settings.instance.evaluationDate().equal(today);
            Calendar calendar = new NewZealand();

            Handle<YieldTermStructure> oneMonthCurve = new Handle<>(
                    new FlatForward(today, 0.01, new Actual365Fixed()), true);
            Handle<YieldTermStructure> threeMonthCurve = new Handle<>(
                    new FlatForward(today, 0.03, new Actual365Fixed()), true);
            Handle<YieldTermStructure> sixMonthCurve = new Handle<>(
                    new FlatForward(today, 0.06, new Actual365Fixed()), true);
            IborIndex bkbm1m = new Bkbm1M(oneMonthCurve);
            IborIndex bkbm3m = new Bkbm3M(threeMonthCurve);
            IborIndex bkbm6m = new Bkbm6M(sixMonthCurve);

            Schedule finalStubSchedule = new Schedule(
                    List.of(new Date(29, Month.MAY, 2026),
                            new Date(31, Month.AUGUST, 2026),
                            new Date(30, Month.OCTOBER, 2026)),
                    calendar, BusinessDayConvention.MODIFIED_FOLLOWING, Optional.empty(),
                    Optional.of(new Period(3, TimeUnit.MONTHS)),
                    Optional.of(DateGenerationRule.FORWARD), Optional.of(true),
                    List.of(true, false));
            StubIndexSelection finalConvention = new StubIndexSelection(
                    StubIndexSelection.Convention.Interpolated, List.of(bkbm1m, bkbm3m));
            List<CashFlow> finalStubLeg = new IborLeg(finalStubSchedule, bkbm3m)
                    .withNotionals(1.0)
                    .withIndexedCoupons(true)
                    .withStubIndexSelection(finalConvention)
                    .leg();
            assertFalse(finalStubLeg.get(0) instanceof StubIborCoupon);
            assertTrue(finalStubLeg.get(finalStubLeg.size() - 1) instanceof StubIborCoupon);
            StubIborCoupon finalCoupon = (StubIborCoupon) finalStubLeg.get(
                    finalStubLeg.size() - 1);

            Date finalStart = finalCoupon.accrualStartDate();
            Date oneMonthMaturity = bkbm1m.maturityDate(finalStart);
            Date threeMonthMaturity = bkbm3m.maturityDate(finalStart);
            double finalWeight = dayCount(oneMonthMaturity, finalCoupon.accrualEndDate())
                    / dayCount(oneMonthMaturity, threeMonthMaturity);
            double expectedFinal = bkbm1m.fixing(finalCoupon.fixingDate(), true)
                    + (bkbm3m.fixing(finalCoupon.fixingDate(), true)
                    - bkbm1m.fixing(finalCoupon.fixingDate(), true)) * finalWeight;
            checkSmall(finalCoupon.indexFixing(), expectedFinal, 1.0e-14);

            Date longStart = new Date(29, Month.MAY, 2026);
            Date longEnd = new Date(30, Month.OCTOBER, 2026);
            Schedule longStubSchedule = new Schedule(List.of(longStart, longEnd),
                    calendar, BusinessDayConvention.MODIFIED_FOLLOWING, Optional.empty(),
                    Optional.of(new Period(3, TimeUnit.MONTHS)),
                    Optional.of(DateGenerationRule.BACKWARD), Optional.of(true),
                    List.of(false));
            StubIndexSelection longConvention = new StubIndexSelection(
                    StubIndexSelection.Convention.Interpolated, List.of(bkbm3m, bkbm6m));
            List<CashFlow> longStubLeg = new IborLeg(longStubSchedule, bkbm3m)
                    .withNotionals(1.0)
                    .withIndexedCoupons(true)
                    .withStubIndexSelection(longConvention)
                    .leg();
            assertTrue(longStubLeg.get(0) instanceof StubIborCoupon);
            StubIborCoupon longCoupon = (StubIborCoupon) longStubLeg.get(0);
            Date threeMonthLongMaturity = bkbm3m.maturityDate(longStart);
            Date sixMonthMaturity = bkbm6m.maturityDate(longStart);
            double longWeight = dayCount(threeMonthLongMaturity, longEnd)
                    / dayCount(threeMonthLongMaturity, sixMonthMaturity);
            double expectedLong = bkbm3m.fixing(longCoupon.fixingDate(), true)
                    + (bkbm6m.fixing(longCoupon.fixingDate(), true)
                    - bkbm3m.fixing(longCoupon.fixingDate(), true)) * longWeight;
            checkSmall(longCoupon.indexFixing(), expectedLong, 1.0e-14);
        } finally {
            backup.restore();
        }
    }

    @Test
    public void testIborStubCouponHistoricalFixingsAndRelinking() {
        SavedSettings backup = new SavedSettings();
        try {
            Date fixingDate = new Date(29, Month.MAY, 2026);
            Date start = fixingDate;
            Date end = new Date(28, Month.AUGUST, 2026);

            RelinkableHandle<YieldTermStructure> twoMonthCurve = new RelinkableHandle<>();
            RelinkableHandle<YieldTermStructure> threeMonthCurve = new RelinkableHandle<>();
            twoMonthCurve.linkTo(new FlatForward(fixingDate, 0.02, new Actual365Fixed()), true);
            threeMonthCurve.linkTo(new FlatForward(fixingDate, 0.04, new Actual365Fixed()), true);
            IborIndex bkbm2m = new Bkbm2M(twoMonthCurve);
            IborIndex bkbm3m = new Bkbm3M(threeMonthCurve);
            StubIndexSelection convention = new StubIndexSelection(
                    StubIndexSelection.Convention.Interpolated, List.of(bkbm2m, bkbm3m));
            Schedule schedule = new Schedule(List.of(start, end),
                    bkbm3m.fixingCalendar(), BusinessDayConvention.MODIFIED_FOLLOWING,
                    Optional.empty(), Optional.of(new Period(3, TimeUnit.MONTHS)),
                    Optional.of(DateGenerationRule.BACKWARD), Optional.of(true),
                    List.of(false));
            List<CashFlow> leg = new IborLeg(schedule, bkbm3m)
                    .withNotionals(1.0)
                    .withIndexedCoupons(true)
                    .withStubIndexSelection(convention)
                    .leg();
            assertTrue(leg.get(0) instanceof StubIborCoupon);
            StubIborCoupon coupon = (StubIborCoupon) leg.get(0);

            Settings.instance.evaluationDate().equal(new Date(27, Month.MAY, 2026));
            double initialRate = coupon.rate();
            Flag flag = new Flag();
            flag.registerWith(coupon);
            twoMonthCurve.linkTo(new FlatForward(fixingDate, 0.03, new Actual365Fixed()), true);
            assertTrue(flag.isUp());
            assertTrue(Math.abs(coupon.rate() - initialRate) > 1.0e-6);

            Settings.instance.evaluationDate().equal(new Date(1, Month.JUNE, 2026));
            bkbm2m.addFixing(fixingDate, 0.05, false);
            bkbm3m.addFixing(fixingDate, 0.08, false);
            Date shortMaturity = bkbm2m.maturityDate(start);
            Date longMaturity = bkbm3m.maturityDate(start);
            double weight = dayCount(shortMaturity, end) / dayCount(shortMaturity, longMaturity);
            double expected = 0.05 + (0.08 - 0.05) * weight;
            checkSmall(coupon.indexFixing(), expected, 1.0e-14);

            bkbm2m.clearFixings();
            bkbm3m.clearFixings();
        } finally {
            backup.restore();
        }
    }

    @Test
    public void testIborStubCouponHasFixedUsesSelectedIndices() {
        SavedSettings backup = new SavedSettings();
        try {
            Date fixingDate = new Date(29, Month.MAY, 2026);
            Date start = fixingDate;
            Date end = new Date(28, Month.AUGUST, 2026);
            Settings.instance.evaluationDate().equal(fixingDate);
            // official default; stated explicitly so the case is independent
            Settings.instance.setEnforcesTodaysHistoricFixings(false);

            Handle<YieldTermStructure> twoMonthCurve = new Handle<>(
                    new FlatForward(fixingDate, 0.02, new Actual365Fixed()), true);
            Handle<YieldTermStructure> threeMonthCurve = new Handle<>(
                    new FlatForward(fixingDate, 0.04, new Actual365Fixed()), true);
            IborIndex bkbm2m = new Bkbm2M(twoMonthCurve);
            IborIndex bkbm3m = new Bkbm3M(threeMonthCurve);
            StubIndexSelection convention = new StubIndexSelection(
                    StubIndexSelection.Convention.Interpolated, List.of(bkbm2m, bkbm3m));
            Schedule schedule = new Schedule(List.of(start, end),
                    bkbm3m.fixingCalendar(), BusinessDayConvention.MODIFIED_FOLLOWING,
                    Optional.empty(), Optional.of(new Period(3, TimeUnit.MONTHS)),
                    Optional.of(DateGenerationRule.BACKWARD), Optional.of(true),
                    List.of(false));
            List<CashFlow> leg = new IborLeg(schedule, bkbm3m)
                    .withNotionals(1.0)
                    .withIndexedCoupons(true)
                    .withStubIndexSelection(convention)
                    .leg();
            assertTrue(leg.get(0) instanceof StubIborCoupon);
            StubIborCoupon coupon = (StubIborCoupon) leg.get(0);
            assertDateEquals(fixingDate, coupon.fixingDate());

            assertEquals(false, coupon.hasFixed());

            // The leg index's own print must not flip the stub to fixed: the
            // coupon reads the bracketing candidates, and the 2M print is
            // still missing.  Checked through a base-class pointer so the
            // virtual dispatch is covered.
            bkbm3m.addFixing(fixingDate, 0.08, false);
            IborCoupon asIborCoupon = coupon;
            assertEquals(false, asIborCoupon.hasFixed());

            bkbm2m.addFixing(fixingDate, 0.05, false);
            assertEquals(true, asIborCoupon.hasFixed());

            Date shortMaturity = bkbm2m.maturityDate(start);
            Date longMaturity = bkbm3m.maturityDate(start);
            double weight = dayCount(shortMaturity, end) / dayCount(shortMaturity, longMaturity);
            checkSmall(coupon.indexFixing(), 0.05 + (0.08 - 0.05) * weight, 1.0e-14);

            bkbm2m.clearFixings();
            bkbm3m.clearFixings();
        } finally {
            backup.restore();
        }
    }
}
