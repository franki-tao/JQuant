package jquant.cashflows;

import jquant.CashFlow;
import jquant.RelinkableHandle;
import jquant.SavedSettings;
import jquant.Settings;
import jquant.indexes.Euribor1M;
import jquant.indexes.IborIndex;
import jquant.indexes.IndexManager;
import jquant.termstructures.YieldTermStructure;
import jquant.termstructures.yield.FlatForward;
import jquant.time.BusinessDayConvention;
import jquant.time.Calendar;
import jquant.time.Date;
import jquant.time.DayCounter;
import jquant.time.Frequency;
import jquant.time.MakeSchedule;
import jquant.time.Month;
import jquant.time.Period;
import jquant.time.Schedule;
import jquant.time.TimeUnit;
import jquant.time.calendars.Target;
import jquant.time.daycounters.Actual365Fixed;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Port of the multiple-resets-coupon cases from QuantLib
 * {@code test-suite/multipleresetscoupons.cpp}.
 *
 * Official coverage is the five BOOST_AUTO_TEST_CASE entries in that file.
 */
public class MultipleResetsCouponTest {

    private SavedSettings savedSettings;

    @BeforeEach
    void saveSettings() {
        savedSettings = new SavedSettings();
    }

    @AfterEach
    void restoreSettings() {
        // Official TopLevelFixture clears index histories before
        // SavedSettings restores the evaluation date and other settings.
        IndexManager.Instance.clearHistories();
        savedSettings.restore();
    }

    /** Official {@code CommonVars} from test-suite/multipleresetscoupons.cpp. */
    private static class CommonVars {
        final Date today;
        final Calendar calendar;
        final DayCounter dayCount;
        final BusinessDayConvention businessConvention;

        final IborIndex euribor;
        final RelinkableHandle<YieldTermStructure> euriborHandle =
                new RelinkableHandle<>();

        CommonVars() {
            dayCount = new Actual365Fixed();
            businessConvention = BusinessDayConvention.MODIFIED_FOLLOWING;

            euribor = new Euribor1M(euriborHandle);
            euribor.addFixing(new Date(13, Month.JANUARY, 2021), 0.0077, false);
            euribor.addFixing(new Date(11, Month.FEBRUARY, 2021), 0.0075, false);
            euribor.addFixing(new Date(11, Month.MARCH, 2021), 0.0073, false);

            calendar = euribor.fixingCalendar();
            today = calendar.adjust(new Date(15, Month.MARCH, 2021),
                    BusinessDayConvention.FOLLOWING);
            Settings.instance.evaluationDate().equal(today);

            euriborHandle.linkTo(new FlatForward(today, 0.007, dayCount), true);
        }

        Schedule createSchedule(final Date start, final Date end) {
            return new MakeSchedule()
                    .from(start)
                    .to(end)
                    .withTenor(euribor.tenor())
                    .withCalendar(euribor.fixingCalendar())
                    .withConvention(euribor.businessDayConvention())
                    .toSchedule();
        }

        List<CashFlow> createIborLeg(final Schedule schedule, double spread) {
            return new IborLeg(schedule, euribor)
                    .withNotionals(1.0)
                    .withSpreads(spread)
                    .withExCouponPeriod(new Period(2, TimeUnit.DAYS), calendar,
                            businessConvention, false)
                    .withPaymentLag(1)
                    .withFixingDays(euribor.fixingDays())
                    .leg();
        }

        CashFlow createMultipleResetsCoupon(final Schedule schedule,
                                            double rateSpread,
                                            RateAveraging averaging) {
            Calendar paymentCalendar = euribor.fixingCalendar();
            BusinessDayConvention paymentBdc = euribor.businessDayConvention();
            Date paymentDate = paymentCalendar.advance(schedule.back(), 1,
                    TimeUnit.DAYS, paymentBdc);
            Date exCouponDate = paymentCalendar.advance(paymentDate, -2,
                    TimeUnit.DAYS, paymentBdc);
            MultipleResetsCoupon cpn = new MultipleResetsCoupon(
                    paymentDate, 1.0, schedule, euribor.fixingDays(), euribor, 1.0, 0.0,
                    rateSpread, new Date(), new Date(), new DayCounter(), exCouponDate);
            if (averaging == RateAveraging.Compound)
                cpn.setPricer(new CompoundingMultipleResetsPricer());
            else
                cpn.setPricer(new AveragingMultipleResetsPricer());
            return cpn;
        }

        MultipleResetsLeg createMultipleResetsLeg(final Date start, final Date end) {
            Schedule s = createSchedule(start, end);
            return new MultipleResetsLeg(s, euribor, 6)
                    .withNotionals(1.0)
                    .withExCouponPeriod(new Period(2, TimeUnit.DAYS), calendar,
                            businessConvention, false)
                    .withPaymentLag(1)
                    .withFixingDays(2)
                    .withRateSpreads(0.0)
                    .withCouponSpreads(0.0)
                    .withAveragingMethod(RateAveraging.Compound);
        }
    }

    @Test
    public void testCompoundedCouponWithMultipleResets() {
        CommonVars vars = new CommonVars();

        Date start = vars.today.substract(new Period(2, TimeUnit.MONTHS));
        Date end = start.add(new Period(6, TimeUnit.MONTHS));

        double spread = 0.001;

        Schedule schedule = vars.createSchedule(start, end);

        List<CashFlow> iborLeg = vars.createIborLeg(schedule, spread);
        CashFlow testCpn = vars.createMultipleResetsCoupon(schedule, spread,
                RateAveraging.Compound);

        final double tolerance = 1.0e-14;

        double actualPayment = testCpn.amount();

        double compound = 1.0;
        for (CashFlow cf : iborLeg) {
            IborCoupon cpn = (IborCoupon) cf;
            double yearFraction = cpn.accrualPeriod();
            double fixing = vars.euribor.fixing(cpn.fixingDate(), false);
            compound *= (1.0 + yearFraction * (fixing + cpn.spread()));
        }
        double expectedPayment = compound - 1.0;

        if (Math.abs(actualPayment - expectedPayment) > tolerance)
            fail("unable to replicate compounded multiple-resets coupon payment\n"
                    + "    calculated:    " + actualPayment + "\n"
                    + "    expected:    " + expectedPayment + "\n"
                    + "    start:    " + start + "\n"
                    + "    end:    " + end + "\n");
    }

    @Test
    public void testAveragedCouponWithMultipleResets() {
        CommonVars vars = new CommonVars();

        Date start = vars.today.substract(new Period(2, TimeUnit.MONTHS));
        Date end = start.add(new Period(6, TimeUnit.MONTHS));

        double spread = 0.001;

        Schedule schedule = vars.createSchedule(start, end);

        List<CashFlow> iborLeg = vars.createIborLeg(schedule, spread);
        CashFlow testCpn = vars.createMultipleResetsCoupon(schedule, spread,
                RateAveraging.Simple);

        final double tolerance = 1.0e-14;

        double actualPayment = testCpn.amount();

        double expectedPayment = 0.0;
        for (CashFlow cf : iborLeg) {
            IborCoupon cpn = (IborCoupon) cf;
            double yearFraction = cpn.accrualPeriod();
            double fixing = vars.euribor.fixing(cpn.fixingDate(), false);
            expectedPayment += yearFraction * (fixing + cpn.spread());
        }

        if (Math.abs(actualPayment - expectedPayment) > tolerance)
            fail("unable to replicate averaged multiple-resets coupon payment\n"
                    + "    calculated:    " + actualPayment + "\n"
                    + "    expected:    " + expectedPayment + "\n"
                    + "    start:    " + start + "\n"
                    + "    end:    " + end + "\n");
    }

    @Test
    public void testExCouponCashFlow() {
        CommonVars vars = new CommonVars();

        Date start = vars.calendar.advance(vars.today, -6, TimeUnit.MONTHS);
        Date end = vars.today;
        Schedule schedule = vars.createSchedule(start, end);

        Calendar paymentCalendar = vars.euribor.fixingCalendar();
        Date paymentDate = paymentCalendar.advance(end, 2, TimeUnit.DAYS);
        Date exCouponDate = paymentCalendar.advance(end, -2, TimeUnit.DAYS);

        MultipleResetsCoupon cpn = new MultipleResetsCoupon(
                paymentDate, 1.0, schedule, 2, vars.euribor,
                1.0, 0.0, 0.0, new Date(), new Date(), new DayCounter(), exCouponDate);
        cpn.setPricer(new CompoundingMultipleResetsPricer());

        double npv = CashFlows.npv(List.<CashFlow>of(cpn), vars.euriborHandle.currentLink(),
                false, vars.today, vars.today);

        final double tolerance = 1.0e-14;

        if (Math.abs(npv) > tolerance)
            fail("cash flow was expected to go ex-coupon\n"
                    + "    calculated:    " + npv + "\n"
                    + "    expected:    " + 0.0 + "\n"
                    + "    start:    " + start + "\n"
                    + "    end:    " + end + "\n");
    }

    @Test
    public void testMultipleResetsLegConsistencyChecks() {
        CommonVars vars = new CommonVars();

        Date start = new Date(18, Month.MARCH, 2021);
        Date end = new Date(18, Month.MARCH, 2031);

        List<CashFlow> validLeg = vars.createMultipleResetsLeg(start, end).leg();
        int N = validLeg.size();

        assertThrows(IllegalArgumentException.class,
                () -> vars.createMultipleResetsLeg(start, end)
                        .withNotionals(new ArrayList<Double>()).leg());

        assertThrows(IllegalArgumentException.class,
                () -> vars.createMultipleResetsLeg(start, end)
                        .withNotionals(Collections.nCopies(N + 1, 1.0)).leg());

        assertThrows(IllegalArgumentException.class,
                () -> vars.createMultipleResetsLeg(start, end)
                        .withFixingDays(Collections.nCopies(N + 1, 2)).leg());

        assertThrows(IllegalArgumentException.class,
                () -> vars.createMultipleResetsLeg(start, end)
                        .withGearings(0.0).leg());

        assertThrows(IllegalArgumentException.class,
                () -> vars.createMultipleResetsLeg(start, end)
                        .withGearings(Collections.nCopies(N + 1, 1.0)).leg());

        assertThrows(IllegalArgumentException.class,
                () -> vars.createMultipleResetsLeg(start, end)
                        .withCouponSpreads(Collections.nCopies(N + 1, 0.0)).leg());

        assertThrows(IllegalArgumentException.class,
                () -> vars.createMultipleResetsLeg(start, end)
                        .withRateSpreads(Collections.nCopies(N + 1, 0.0)).leg());
    }

    @Test
    public void testMultipleResetsLegRegression() {
        Schedule schedule = new MakeSchedule()
                .from(new Date(1, Month.AUGUST, 2024))
                .to(new Date(1, Month.AUGUST, 2025))
                .withFrequency(Frequency.MONTHLY)
                .withCalendar(new Target())
                .toSchedule();

        int resetsPerCoupon = 3;
        List<CashFlow> leg = new MultipleResetsLeg(schedule, new Euribor1M(),
                resetsPerCoupon)
                .withNotionals(100.0)
                .withAveragingMethod(RateAveraging.Compound)
                .leg();

        for (CashFlow cf : leg) {
            MultipleResetsCoupon c = (MultipleResetsCoupon) cf;
            if (c.fixingDates().size() != 3)
                fail("Unexpected number of fixing dates (" + c.fixingDates().size() + ") "
                        + "in coupon paying on " + c.date());
        }
    }
}
