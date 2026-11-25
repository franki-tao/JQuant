package cashflows;

import jquant.CashFlow;
import jquant.Handle;
import jquant.Quote;
import jquant.RelinkableHandle;
import jquant.SavedSettings;
import jquant.Settings;
import jquant.cashflows.CappedFlooredCoupon;
import jquant.cashflows.CappedFlooredCmsCoupon;
import jquant.cashflows.CmsCoupon;
import jquant.cashflows.CmsCouponPricer;
import jquant.cashflows.CmsLeg;
import jquant.cashflows.FixedRateCoupon;
import jquant.cashflows.FloatingRateCoupon;
import jquant.cashflows.IborCoupon;
import jquant.indexes.EuriborSwapIsdaFixA;
import jquant.indexes.IndexManager;
import jquant.indexes.SwapIndex;
import jquant.quotes.SimpleQuote;
import jquant.termstructures.YieldTermStructure;
import jquant.termstructures.volatility.swaption.ConstantSwaptionVolatility;
import jquant.termstructures.volatility.swaption.SwaptionVolatilityStructure;
import jquant.termstructures.yield.FlatForward;
import jquant.time.BusinessDayConvention;
import jquant.time.Calendar;
import jquant.time.Date;
import jquant.time.DateGenerationRule;
import jquant.time.DayCounter;
import jquant.time.Frequency;
import jquant.time.Period;
import jquant.time.Schedule;
import jquant.time.TimeUnit;
import jquant.time.TimeUtils;
import jquant.time.calendars.Target;
import jquant.time.daycounters.Actual365Fixed;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static jquant.termstructures.volatility.Sarb.VolatilityType.ShiftedLognormal;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Engineering tests, <b>not</b> official BOOST ports.
 *
 * <p>They cover the CMS coupon infrastructure of DON-20 task 2:
 * {@link CmsCoupon}, {@link CappedFlooredCmsCoupon}, {@link CmsLeg} and the
 * swaption-volatility subscription of {@link CmsCouponPricer}.
 *
 * <p>The evaluation date is {@code TARGET.adjust(today)} as official
 * CommonVars. No official CMS prices are invented: the fixing test stores
 * its own past fixing and only checks that the index lookup returns it.
 */
public class CmsCouponTest {

    private static final Calendar CALENDAR = new Target();
    private static final DayCounter DC = new Actual365Fixed();
    private static final double NOTIONAL = 100.0;

    private SavedSettings savedSettings;

    @BeforeEach
    void saveSettings() {
        savedSettings = new SavedSettings();
        // Official-style evaluation date; new Date() is the empty serial-0
        // date, so use today's actual date here.
        Settings.instance.evaluationDate().equal(
                CALENDAR.adjust(Date.todaysDate()));
    }

    @AfterEach
    void restoreSettings() {
        IndexManager.Instance.clearHistories();
        savedSettings.restore();
    }

    private static SwapIndex swapIndex() {
        Date eval = Settings.instance.evaluationDate().Date();
        YieldTermStructure curve = new FlatForward(eval, 0.03, DC);
        Handle<YieldTermStructure> curveHandle = new Handle<>(curve, true);
        return new EuriborSwapIsdaFixA(
                new Period(10, TimeUnit.YEARS), curveHandle);
    }

    private static Schedule schedule() {
        Date eval = Settings.instance.evaluationDate().Date();
        Date start = eval;
        Date end = CALENDAR.advance(start,
                new Period(2, TimeUnit.YEARS), BusinessDayConvention.FOLLOWING);
        return new Schedule(start, end,
                new Period(Frequency.SEMIANNUAL), CALENDAR,
                BusinessDayConvention.FOLLOWING,
                BusinessDayConvention.FOLLOWING,
                DateGenerationRule.FORWARD, false,
                new Date(), new Date());
    }

    private static ConstantSwaptionVolatility constantVol(SimpleQuote quote) {
        Handle<Quote> quoteHandle = new Handle<>(quote, true);
        return new ConstantSwaptionVolatility(
                Settings.instance.evaluationDate().Date(), CALENDAR,
                BusinessDayConvention.FOLLOWING, quoteHandle, DC,
                ShiftedLognormal, 0.0);
    }

    @Test
    public void cmsCouponConstructionAndDateCopies() {
        SwapIndex index = swapIndex();
        Date eval = Settings.instance.evaluationDate().Date();

        Date payment = CALENDAR.advance(eval, new Period(6, TimeUnit.MONTHS),
                BusinessDayConvention.FOLLOWING);
        Date start = eval;
        Date end = CALENDAR.advance(start, new Period(6, TimeUnit.MONTHS),
                BusinessDayConvention.FOLLOWING);
        Date refStart = start.substract(3);
        Date refEnd = end.add(3);
        Date exCouponDate = payment.substract(2);

        CmsCoupon coupon = new CmsCoupon(payment, NOTIONAL, start, end,
                2, index, 1.25, 0.005, refStart, refEnd, DC,
                true, exCouponDate, BusinessDayConvention.PRECEDING);

        assertSame(index, coupon.swapIndex(), "swapIndex");
        assertSame(index, coupon.index(), "coupon index");
        assertEquals(NOTIONAL, coupon.nominal(), 0.0, "nominal");
        assertEquals(2, coupon.fixingDays(), "fixing days");
        assertEquals(1.25, coupon.gearing(), 0.0, "gearing");
        assertEquals(0.005, coupon.spread(), 0.0, "spread");
        assertTrue(coupon.isInArrears(), "in arrears");
        assertEquals(BusinessDayConvention.PRECEDING,
                coupon.fixingConvention(), "fixing convention");

        assertTrue(TimeUtils.equals(payment, coupon.date()),
                "payment date");
        assertTrue(TimeUtils.equals(start, coupon.accrualStartDate()),
                "accrual start");
        assertTrue(TimeUtils.equals(end, coupon.accrualEndDate()),
                "accrual end");
        assertTrue(TimeUtils.equals(refStart, coupon.referencePeriodStart()),
                "reference start");
        assertTrue(TimeUtils.equals(refEnd, coupon.referencePeriodEnd()),
                "reference end");
        assertTrue(TimeUtils.equals(exCouponDate, coupon.exCouponDate()),
                "ex-coupon date");

        // In arrears: the fixing is taken from the accrual end, fixingDays
        // before it, with the Preceding convention.
        Date expectedFixing = CALENDAR.advance(end, -2, TimeUnit.DAYS,
                BusinessDayConvention.PRECEDING, false);
        assertTrue(TimeUtils.equals(expectedFixing, coupon.fixingDate()),
                "fixing date: expected " + expectedFixing
                        + ", got " + coupon.fixingDate());

        // Parent copies its dates; mutating the caller's Date objects after
        // construction must not change the coupon (DON-18 regression).
        Date paymentBefore = coupon.date();
        Date startBefore = coupon.accrualStartDate();
        Date endBefore = coupon.accrualEndDate();
        Date refStartBefore = coupon.referencePeriodStart();
        Date refEndBefore = coupon.referencePeriodEnd();
        Date exCouponBefore = coupon.exCouponDate();
        Date fixingBefore = coupon.fixingDate();

        payment.addEquals(1);
        start.addEquals(1);
        end.addEquals(1);
        refStart.addEquals(1);
        refEnd.addEquals(1);
        exCouponDate.addEquals(1);

        assertTrue(TimeUtils.equals(paymentBefore, coupon.date()),
                "payment date must not alias the constructor input");
        assertTrue(TimeUtils.equals(startBefore, coupon.accrualStartDate()),
                "accrual start must not alias the constructor input");
        assertTrue(TimeUtils.equals(endBefore, coupon.accrualEndDate()),
                "accrual end must not alias the constructor input");
        assertTrue(TimeUtils.equals(refStartBefore,
                        coupon.referencePeriodStart()),
                "reference start must not alias the constructor input");
        assertTrue(TimeUtils.equals(refEndBefore,
                        coupon.referencePeriodEnd()),
                "reference end must not alias the constructor input");
        assertTrue(TimeUtils.equals(exCouponBefore, coupon.exCouponDate()),
                "ex-coupon date must not alias the constructor input");
        assertTrue(TimeUtils.equals(fixingBefore, coupon.fixingDate()),
                "fixing date must not move when input dates are mutated");
    }

    @Test
    public void cmsLegBuildsCmsCouponsCapsFloorsAndFixedZeroGearing() {
        SwapIndex index = swapIndex();
        Schedule schedule = schedule();
        int n = schedule.size() - 1;

        List<CashFlow> plain = new CmsLeg(schedule, index)
                .withNotionals(NOTIONAL)
                .withGearings(1.5)
                .withSpreads(0.01)
                .withPaymentDayCounter(DC)
                .leg();
        assertEquals(n, plain.size(), "plain leg size");
        for (int i = 0; i < plain.size(); ++i) {
            CashFlow flow = plain.get(i);
            assertInstanceOf(CmsCoupon.class, flow,
                    "plain leg must contain CMS coupons");
            assertFalse(flow instanceof IborCoupon,
                    "plain CMS leg must not contain IborCoupon");
            CmsCoupon coupon = (CmsCoupon) flow;
            assertSame(index, coupon.swapIndex(), "swap index at " + i);
            assertEquals(NOTIONAL, coupon.nominal(), 0.0,
                    "nominal at " + i);
            assertEquals(1.5, coupon.gearing(), 0.0, "gearing at " + i);
            assertEquals(0.01, coupon.spread(), 0.0, "spread at " + i);
            assertEquals(index.fixingDays(), coupon.fixingDays(),
                    "default fixing days at " + i);
            assertTrue(TimeUtils.equals(schedule.date(i),
                            coupon.accrualStartDate()),
                    "accrual start at " + i);
            assertTrue(TimeUtils.equals(schedule.date(i + 1),
                            coupon.accrualEndDate()),
                    "accrual end at " + i);
            assertTrue(TimeUtils.equals(
                            CALENDAR.adjust(schedule.date(i + 1)),
                            coupon.date()),
                    "payment date at " + i);
        }

        List<CashFlow> capped = new CmsLeg(schedule, index)
                .withNotionals(NOTIONAL)
                .withCaps(0.05)
                .leg();
        for (CashFlow flow : capped) {
            assertInstanceOf(CappedFlooredCmsCoupon.class, flow,
                    "capped leg must contain CappedFlooredCmsCoupon");
            CappedFlooredCoupon coupon = (CappedFlooredCoupon) flow;
            assertEquals(0.05, coupon.cap(), 0.0, "cap");
            assertTrue(Double.isNaN(coupon.floor()), "no floor");
            assertTrue(coupon.isCapped(), "capped");
            assertFalse(coupon.isFloored(), "not floored");
        }

        List<CashFlow> floored = new CmsLeg(schedule, index)
                .withNotionals(NOTIONAL)
                .withFloors(0.02)
                .leg();
        for (CashFlow flow : floored) {
            assertInstanceOf(CappedFlooredCmsCoupon.class, flow,
                    "floored leg must contain CappedFlooredCmsCoupon");
            CappedFlooredCoupon coupon = (CappedFlooredCoupon) flow;
            assertEquals(0.02, coupon.floor(), 0.0, "floor");
            assertTrue(Double.isNaN(coupon.cap()), "no cap");
            assertTrue(coupon.isFloored(), "floored");
            assertFalse(coupon.isCapped(), "not capped");
        }

        // Official FloatingLeg: a zero gearing produces a fixed-rate coupon
        // paying the spread (no caps/floors here).
        List<CashFlow> fixed = new CmsLeg(schedule, index)
                .withNotionals(NOTIONAL)
                .withGearings(0.0)
                .withSpreads(0.02)
                .withPaymentDayCounter(DC)
                .leg();
        for (CashFlow flow : fixed) {
            assertInstanceOf(FixedRateCoupon.class, flow,
                    "zero gearing must produce a FixedRateCoupon");
            FixedRateCoupon fixedCoupon = (FixedRateCoupon) flow;
            assertEquals(0.02, fixedCoupon.rate(), 0.0,
                    "fixed rate falls back to the spread");
            assertEquals(NOTIONAL, fixedCoupon.nominal(), 0.0,
                    "nominal retained");
        }

        // Official requires a non-null swap index.
        Exception noIndex = assertThrows(Exception.class,
                () -> new CmsLeg(schedule, null));
        assertTrue(noIndex.getMessage() != null
                        && noIndex.getMessage().contains("no index provided"),
                "expected 'no index provided', got: " + noIndex.getMessage());
    }

    @Test
    public void pastFixingIsReadAndRateWithoutPricerFails() {
        SwapIndex index = swapIndex();
        Date eval = Settings.instance.evaluationDate().Date();
        Date past = CALENDAR.adjust(
                eval.substract(new Period(1, TimeUnit.MONTHS)));
        // forceOverwrite so a stale fixing from an earlier test class cannot
        // make this engineering assertion order-dependent.
        index.addFixing(past, 0.0325, true);

        Date end = CALENDAR.advance(past, new Period(3, TimeUnit.MONTHS),
                BusinessDayConvention.FOLLOWING);
        CmsCoupon coupon = new CmsCoupon(end, NOTIONAL, past, end, 0, index,
                1.0, 0.0, new Date(), new Date(), DC);

        assertTrue(TimeUtils.equals(past, coupon.fixingDate()),
                "fixing date: expected " + past
                        + ", got " + coupon.fixingDate());
        assertEquals(0.0325, coupon.indexFixing(), 0.0,
                "coupon must read the stored historic fixing");

        // No zero-convexity pricer is invented: rate() must fail loudly.
        IllegalArgumentException noPricer = assertThrows(
                IllegalArgumentException.class, coupon::rate);
        assertTrue(noPricer.getMessage() != null
                        && noPricer.getMessage().contains("pricer not set"),
                "expected 'pricer not set', got: " + noPricer.getMessage());
    }

    @Test
    public void pricerFollowsQuoteChangeRelinkAndUnbindsOldSurface() {
        SimpleQuote quote1 = new SimpleQuote(0.10);
        SimpleQuote quote2 = new SimpleQuote(0.20);
        ConstantSwaptionVolatility vol1 = constantVol(quote1);
        ConstantSwaptionVolatility vol2 = constantVol(quote2);
        RelinkableHandle<SwaptionVolatilityStructure> handle =
                new RelinkableHandle<>(vol1, true);
        TestCmsCouponPricer pricer = new TestCmsCouponPricer(handle);

        try {
            assertEquals(0.10, pricer.currentVol(), 0.0, "initial vol");
            int before = pricer.notifications();

            quote1.setValue(0.11);
            assertTrue(pricer.notifications() > before,
                    "a quote change on the linked surface must notify the "
                            + "pricer through the Handle Link");
            assertEquals(0.11, pricer.currentVol(), 0.0,
                    "pricer must see the bumped quote");

            before = pricer.notifications();
            handle.linkTo(vol2, true);
            assertTrue(pricer.notifications() > before,
                    "a relink must notify the pricer");
            assertEquals(0.20, pricer.currentVol(), 0.0,
                    "pricer must see the relinked surface");

            // The old surface is unbound: its quote change must not leak.
            before = pricer.notifications();
            quote1.setValue(0.30);
            assertEquals(before, pricer.notifications(),
                    "a quote change on the unbound old surface must not "
                            + "notify the pricer");
            assertEquals(0.20, pricer.currentVol(), 0.0,
                    "pricer must keep the new surface vol");

            before = pricer.notifications();
            quote2.setValue(0.21);
            assertTrue(pricer.notifications() > before,
                    "a quote change on the new surface must notify");
            assertEquals(0.21, pricer.currentVol(), 0.0,
                    "pricer must follow the new surface quote");

            before = pricer.notifications();
            handle.linkTo(vol1, true);
            assertTrue(pricer.notifications() > before,
                    "relinking back must notify the pricer");
            assertEquals(0.30, pricer.currentVol(), 0.0,
                    "pricer must see the restored surface");
        } finally {
            quote1.setValue(0.10);
            quote2.setValue(0.20);
            handle.linkTo(vol1, true);
        }
    }

    @Test
    public void emptyHandleThenLinkAndResetDoesNotKeepStaleSurface() {
        SimpleQuote quote = new SimpleQuote(0.10);
        ConstantSwaptionVolatility vol = constantVol(quote);
        RelinkableHandle<SwaptionVolatilityStructure> handle =
                new RelinkableHandle<>();
        TestCmsCouponPricer pricer = new TestCmsCouponPricer(handle);

        try {
            assertTrue(pricer.swaptionVolatility().empty(),
                    "pricer starts with an empty Handle");
            assertTrue(Double.isNaN(pricer.currentVol()),
                    "empty Handle has no vol");

            int before = pricer.notifications();
            handle.linkTo(vol, true);
            assertTrue(pricer.notifications() > before,
                    "linking an originally empty Handle must notify");
            assertEquals(0.10, pricer.currentVol(), 0.0, "vol after link");

            before = pricer.notifications();
            quote.setValue(0.12);
            assertTrue(pricer.notifications() > before,
                    "an originally empty Handle must still follow quotes");
            assertEquals(0.12, pricer.currentVol(), 0.0, "bumped vol");

            before = pricer.notifications();
            handle.reset();
            assertTrue(pricer.notifications() > before,
                    "unlinking must notify the pricer");
            assertTrue(pricer.swaptionVolatility().empty(),
                    "reset leaves an empty Handle");
            assertTrue(Double.isNaN(pricer.currentVol()),
                    "unlinked Handle must not keep the stale surface");

            before = pricer.notifications();
            handle.linkTo(vol, true);
            assertTrue(pricer.notifications() > before,
                    "relinking after reset must notify");
            assertEquals(0.12, pricer.currentVol(), 0.0,
                    "relinked surface vol");
        } finally {
            quote.setValue(0.10);
            handle.linkTo(vol, true);
        }
    }

    @Test
    public void setSwaptionVolatilityUnbindsPreviousLink() {
        SimpleQuote quote1 = new SimpleQuote(0.10);
        SimpleQuote quote2 = new SimpleQuote(0.20);
        ConstantSwaptionVolatility vol1 = constantVol(quote1);
        ConstantSwaptionVolatility vol2 = constantVol(quote2);
        RelinkableHandle<SwaptionVolatilityStructure> handle1 =
                new RelinkableHandle<>(vol1, true);
        RelinkableHandle<SwaptionVolatilityStructure> handle2 =
                new RelinkableHandle<>(vol2, true);
        TestCmsCouponPricer pricer = new TestCmsCouponPricer(handle1);

        try {
            int before = pricer.notifications();
            pricer.setSwaptionVolatility(handle2);
            assertTrue(pricer.notifications() > before,
                    "setSwaptionVolatility must update the pricer");
            assertSame(handle2, pricer.swaptionVolatility(),
                    "pricer must store the new Handle");
            assertEquals(0.20, pricer.currentVol(), 0.0,
                    "pricer must see the new Handle's surface");

            // handle1 / vol1 are unbound now.
            before = pricer.notifications();
            quote1.setValue(0.30);
            assertEquals(before, pricer.notifications(),
                    "quote change on the previous Handle's surface must not "
                            + "notify after setSwaptionVolatility");
            assertEquals(0.20, pricer.currentVol(), 0.0,
                    "pricer must keep the new Handle's surface");

            before = pricer.notifications();
            quote2.setValue(0.21);
            assertTrue(pricer.notifications() > before,
                    "the new Handle's quote must notify");
            assertEquals(0.21, pricer.currentVol(), 0.0,
                    "pricer must follow the new Handle's quote");

            // A null argument becomes an empty Handle.
            before = pricer.notifications();
            pricer.setSwaptionVolatility(null);
            assertTrue(pricer.notifications() > before,
                    "setting a null Handle must update the pricer");
            assertTrue(pricer.swaptionVolatility().empty(),
                    "null must become an empty Handle");
            assertTrue(Double.isNaN(pricer.currentVol()),
                    "empty Handle has no vol");

            before = pricer.notifications();
            pricer.setSwaptionVolatility(handle1);
            assertTrue(pricer.notifications() > before,
                    "setting a Handle after the empty one must update");
            assertEquals(0.30, pricer.currentVol(), 0.0,
                    "pricer must use handle1's current surface");
            assertEquals(0.30, pricer.swaptionVolatility()
                    .currentLink().volatility(1.0, 5.0, 0.05, true), 0.0,
                    "handle1 quote");

            // handle2 is unbound again.
            before = pricer.notifications();
            quote2.setValue(0.22);
            assertEquals(before, pricer.notifications(),
                    "quote change on handle2 must not notify after switching "
                            + "back to handle1");
            assertEquals(0.30, pricer.currentVol(), 0.0,
                    "pricer must stay on handle1");
        } finally {
            quote1.setValue(0.10);
            quote2.setValue(0.20);
            pricer.setSwaptionVolatility(handle1);
        }
    }

    /**
     * Minimal concrete pricer for the Handle-subscription tests. No CMS
     * pricing is implemented; {@code swapletRate} just returns the current
     * constant vol. This is an engineering test double, not official code.
     */
    private static final class TestCmsCouponPricer extends CmsCouponPricer {

        private int notifications_ = 0;

        TestCmsCouponPricer() {
            super();
        }

        TestCmsCouponPricer(Handle<SwaptionVolatilityStructure> handle) {
            super(handle);
        }

        @Override
        public void update() {
            ++notifications_;
            super.update();
        }

        int notifications() {
            return notifications_;
        }

        double currentVol() {
            if (swaptionVolatility().empty())
                return Double.NaN;
            return swaptionVolatility().currentLink()
                    .volatility(1.0, 5.0, 0.05, true);
        }

        @Override
        public void initialize(FloatingRateCoupon coupon) {
            // no-op test double
        }

        @Override
        public double swapletPrice() {
            return 0.0;
        }

        @Override
        public double swapletRate() {
            return currentVol();
        }

        @Override
        public double capletPrice(double effectiveCap) {
            return 0.0;
        }

        @Override
        public double capletRate(double effectiveCap) {
            return 0.0;
        }

        @Override
        public double floorletPrice(double effectiveFloor) {
            return 0.0;
        }

        @Override
        public double floorletRate(double effectiveFloor) {
            return 0.0;
        }
    }
}
