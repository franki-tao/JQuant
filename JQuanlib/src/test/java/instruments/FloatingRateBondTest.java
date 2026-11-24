package instruments;

import jquant.CashFlow;
import jquant.Handle;
import jquant.RelinkableHandle;
import jquant.SavedSettings;
import jquant.Settings;
import jquant.cashflows.BlackIborCouponPricer;
import jquant.cashflows.FloatingRateCoupon;
import jquant.cashflows.IborCoupon;
import jquant.indexes.AUDLibor;
import jquant.indexes.IborIndex;
import jquant.indexes.IndexManager;
import jquant.indexes.USDLibor;
import jquant.instruments.FloatingRateBond;
import jquant.pricingengines.DiscountingBondEngine;
import jquant.quotes.SimpleQuote;
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
import jquant.time.TimeUtils;
import jquant.time.calendars.Australia;
import jquant.time.calendars.UnitedStates;
import jquant.time.daycounters.Actual360;
import jquant.time.daycounters.Actual365Fixed;
import jquant.time.daycounters.ActualActual;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static jquant.cashflows.CashflowUtil.setCouponPricer;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Official floating-rate bond cases from {@code test-suite/bonds.cpp}:
 * {@code testCachedFloating} and {@code testFixingConvention}.
 *
 * <p>All official dates, curves, calendars, conventions, cached prices and
 * tolerances are copied verbatim. As in official, the expected plain and
 * spread cached prices depend on
 * {@link IborCoupon.Settings#usingAtParCoupons()}; the global default is read
 * but never changed here.
 *
 * <p>Methods whose names start with {@code engineering} are regressions for
 * the Java adapter/observer wiring, <b>not</b> official BOOST ports; they
 * assert no invented cached values.
 */
public class FloatingRateBondTest {

    private SavedSettings savedSettings_;

    @BeforeEach
    public void setUp() {
        savedSettings_ = new SavedSettings();
    }

    @AfterEach
    public void tearDown() {
        try {
            IndexManager.Instance.clearHistories();
        } finally {
            savedSettings_.restore();
        }
    }

    /** Official {@code flatRate}: FlatForward defaults are Continuous/Annual. */
    private static FlatForward flatRate(Date referenceDate, double rate, DayCounter dayCounter) {
        return new FlatForward(referenceDate, rate, dayCounter);
    }

    /**
     * The unadjusted quarterly schedule used by official
     * {@code testFixingConvention}: 22 March 2024 - 22 December 2024,
     * generated forward.
     */
    private static Schedule fixingConventionSchedule(Calendar calendar) {
        return new Schedule(
                new Date(22, Month.MARCH, 2024),
                new Date(22, Month.DECEMBER, 2024),
                new Period(Frequency.QUARTERLY),
                calendar,
                BusinessDayConvention.UNADJUSTED,
                BusinessDayConvention.UNADJUSTED,
                DateGenerationRule.FORWARD, false,
                new Date(), new Date());
    }

    /**
     * Official {@code findCouponStarting}: the first floating-rate coupon whose
     * accrual start date matches, or {@code null} if there is none.
     */
    private static FloatingRateCoupon findCouponStarting(FloatingRateBond bond, Date accrualStart) {
        for (CashFlow cashFlow : bond.cashflows()) {
            if (cashFlow instanceof FloatingRateCoupon coupon
                    && TimeUtils.equals(coupon.accrualStartDate(), accrualStart))
                return coupon;
        }
        return null;
    }

    /**
     * Official {@code testFixingConvention}. June 22, 2024 is a Saturday;
     * with an unadjusted schedule and fixingDays=0, the fixing date follows
     * the convention: Preceding gives Friday June 21, Following gives Monday
     * June 24. Only the fixing dates are checked, as in official; no pricing
     * is involved.
     */
    @Test
    public void testFixingConvention() {
        Date today = new Date(1, Month.JANUARY, 2024);
        Settings.instance.evaluationDate().equal(today);

        Calendar calendar = new Australia();
        int settlementDays = 0;
        double faceAmount = 100.0;

        Schedule schedule = fixingConventionSchedule(calendar);

        Handle<YieldTermStructure> curve =
                new Handle<>(flatRate(today, 0.05, new Actual365Fixed()), true);
        IborIndex index = new AUDLibor(new Period(3, TimeUnit.MONTHS), curve);

        // Default (Preceding) fixing convention.
        FloatingRateBond bondPreceding = new FloatingRateBond(
                settlementDays, faceAmount, schedule, index, new Actual365Fixed(),
                BusinessDayConvention.FOLLOWING, 0);

        // Following fixing convention.
        FloatingRateBond bondFollowing = new FloatingRateBond(
                settlementDays, faceAmount, schedule, index, new Actual365Fixed(),
                BusinessDayConvention.FOLLOWING, 0,
                List.of(1.0), List.of(0.0), List.of(), List.of(),
                false, 100.0, new Date(),
                new Period(), null, BusinessDayConvention.UNADJUSTED, false,
                BusinessDayConvention.FOLLOWING);

        Date june22 = new Date(22, Month.JUNE, 2024);  // Saturday
        FloatingRateCoupon couponP = findCouponStarting(bondPreceding, june22);
        FloatingRateCoupon couponF = findCouponStarting(bondFollowing, june22);

        assertNotNull(couponP, "no coupon starting on " + june22 + " in bondPreceding");
        assertNotNull(couponF, "no coupon starting on " + june22 + " in bondFollowing");

        Date expectedPreceding = new Date(21, Month.JUNE, 2024);  // Friday
        Date expectedFollowing = new Date(24, Month.JUNE, 2024);  // Monday

        assertTrue(TimeUtils.equals(expectedPreceding, couponP.fixingDate()),
                "Preceding fixing date: expected " + expectedPreceding
                        + ", got " + couponP.fixingDate());
        assertTrue(TimeUtils.equals(expectedFollowing, couponF.fixingDate()),
                "Following fixing date: expected " + expectedFollowing
                        + ", got " + couponF.fixingDate());
    }

    /**
     * Engineering regression, <b>not</b> an official BOOST port and with no
     * official cached value. The forecast curve is a {@link FlatForward} on a
     * {@link SimpleQuote}, and the same instance is handed to the engine as
     * its discount curve. A quote change must invalidate both the handle seen
     * by the index and the bare curve held by the engine; restoring the quote
     * must restore the price. The quote is restored in {@code finally} so a
     * failing assertion cannot leak state.
     */
    @Test
    public void engineeringQuoteChangeRepricesBond() {
        Date today = new Date(1, Month.JANUARY, 2024);
        Settings.instance.evaluationDate().equal(today);

        SimpleQuote quote = new SimpleQuote(0.05);
        FlatForward curve = new FlatForward(today, new Handle<>(quote, true),
                new Actual365Fixed());
        IborIndex index = new AUDLibor(new Period(3, TimeUnit.MONTHS),
                new Handle<>(curve, true));

        FloatingRateBond bond = new FloatingRateBond(
                0, 100.0, fixingConventionSchedule(new Australia()), index,
                new Actual365Fixed(), BusinessDayConvention.FOLLOWING, 0);
        bond.setPricingEngine(new DiscountingBondEngine(curve));
        setCouponPricer(bond.cashflows(), new BlackIborCouponPricer());

        try {
            double basePrice = bond.cleanPrice();

            quote.setValue(0.08);
            double repriced = bond.cleanPrice();
            assertTrue(Math.abs(repriced - basePrice) > 1.0e-8,
                    "a forecast/discount quote change must reprice the bond: base="
                            + basePrice + " repriced=" + repriced);

            quote.setValue(0.05);
            assertEquals(basePrice, bond.cleanPrice(), 1.0e-12,
                    "restoring the quote must restore the price");
        } finally {
            quote.setValue(0.05);
        }
    }

    /**
     * Engineering regression documenting a <b>limitation</b>, not an official
     * BOOST port and with no official cached value: the bond and its index
     * follow a relinked forecast handle, but the engine was constructed with
     * the pre-relink bare curve and keeps discounting with it. The resulting
     * price is therefore a hybrid; it differs from the fully consistent price
     * obtained by also handing the engine the current curve. This test pins
     * the documented behavior; it does not claim the engine should take a
     * Handle.
     */
    @Test
    public void engineeringRelinkUpdatesIndexButNotBareEngineCurve() {
        Date today = new Date(1, Month.JANUARY, 2024);
        Settings.instance.evaluationDate().equal(today);

        FlatForward fivePercent = flatRate(today, 0.05, new Actual365Fixed());
        FlatForward eightPercent = flatRate(today, 0.08, new Actual365Fixed());

        RelinkableHandle<YieldTermStructure> forecast =
                new RelinkableHandle<>(fivePercent, true);
        IborIndex index = new AUDLibor(new Period(3, TimeUnit.MONTHS), forecast);

        FloatingRateBond bond = new FloatingRateBond(
                0, 100.0, fixingConventionSchedule(new Australia()), index,
                new Actual365Fixed(), BusinessDayConvention.FOLLOWING, 0);
        bond.setPricingEngine(new DiscountingBondEngine(forecast.currentLink()));
        setCouponPricer(bond.cashflows(), new BlackIborCouponPricer());

        try {
            double beforeRelink = bond.cleanPrice();

            forecast.linkTo(eightPercent, true);
            double hybrid = bond.cleanPrice();

            // The index now forecasts at 8% while the engine still discounts
            // at 5%, so the price moves away from the pre-relink value.
            assertTrue(Math.abs(hybrid - beforeRelink) > 1.0e-8,
                    "relinking the shared forecast handle must reach the index: before="
                            + beforeRelink + " hybrid=" + hybrid);

            // Passing the current curve (8%) to a new engine is materially
            // different, showing that the old engine did not follow the relink.
            bond.setPricingEngine(new DiscountingBondEngine(forecast.currentLink()));
            double consistent = bond.cleanPrice();
            assertTrue(Math.abs(consistent - hybrid) > 1.0e-8,
                    "the bare engine curve must not follow the relink: hybrid="
                            + hybrid + " fully-consistent=" + consistent);
        } finally {
            forecast.linkTo(fivePercent, true);
        }
    }

    /**
     * Engineering regression, <b>not</b> an official BOOST port and with no
     * official cached value. A coupon whose fixing date is already past is
     * priced from the stored historical fixing; overwriting that fixing (the
     * index history is isolated by {@link #tearDown()}) must reprice the bond.
     */
    @Test
    public void engineeringHistoricalFixingRepricesBond() {
        // Settlement is 3 June 2024; the 22 March 2024 fixing is in the past.
        Date today = new Date(1, Month.JUNE, 2024);
        Settings.instance.evaluationDate().equal(today);

        FlatForward curve = flatRate(today, 0.05, new Actual365Fixed());
        IborIndex index = new AUDLibor(new Period(3, TimeUnit.MONTHS),
                new Handle<>(curve, true));

        FloatingRateBond bond = new FloatingRateBond(
                0, 100.0, fixingConventionSchedule(new Australia()), index,
                new Actual365Fixed(), BusinessDayConvention.FOLLOWING, 0);
        bond.setPricingEngine(new DiscountingBondEngine(curve));
        setCouponPricer(bond.cashflows(), new BlackIborCouponPricer());

        Date pastFixing = new Date(22, Month.MARCH, 2024);
        try {
            index.addFixing(pastFixing, 0.10, false);
            double highFixingPrice = bond.cleanPrice();

            index.addFixing(pastFixing, 0.02, true);
            double lowFixingPrice = bond.cleanPrice();

            assertTrue(Math.abs(highFixingPrice - lowFixingPrice) > 1.0e-8,
                    "the historical coupon fixing must reach the coupon pricer: high="
                            + highFixingPrice + " low=" + lowFixingPrice);
        } finally {
            index.clearFixings();
        }
    }

    @Test
    public void testCachedFloating() {
        boolean usingAtParCoupons = IborCoupon.Settings.instance().usingAtParCoupons();

        Date today = new Date(22, Month.NOVEMBER, 2004);
        Settings.instance.evaluationDate().equal(today);

        int settlementDays = 1;
        double faceAmount = 1.0e6;

        FlatForward riskFreeFlat = flatRate(today, 0.025, new Actual360());
        FlatForward discountFlat = flatRate(today, 0.03, new Actual360());
        Handle<YieldTermStructure> riskFreeRate = new Handle<>(riskFreeFlat, true);

        IborIndex index = new USDLibor(new Period(6, TimeUnit.MONTHS), riskFreeRate);
        int fixingDays = 1;

        double tolerance = 1.0e-6;

        BlackIborCouponPricer pricer = new BlackIborCouponPricer();

        // plain

        Schedule sch = new Schedule(
                new Date(30, Month.NOVEMBER, 2004),
                new Date(30, Month.NOVEMBER, 2008),
                new Period(Frequency.SEMIANNUAL),
                new UnitedStates(UnitedStates.Market.GovernmentBond),
                BusinessDayConvention.MODIFIED_FOLLOWING,
                BusinessDayConvention.MODIFIED_FOLLOWING,
                DateGenerationRule.BACKWARD, false,
                new Date(), new Date());

        FloatingRateBond bond1 = new FloatingRateBond(
                settlementDays, faceAmount, sch, index,
                new ActualActual(ActualActual.Convention.ISMA),
                BusinessDayConvention.MODIFIED_FOLLOWING, fixingDays,
                List.of(), List.of(), List.of(), List.of(),
                false, 100.0, new Date(30, Month.NOVEMBER, 2004));
        bond1.setPricingEngine(new DiscountingBondEngine(riskFreeFlat));
        setCouponPricer(bond1.cashflows(), pricer);

        double cachedPrice1 = usingAtParCoupons ? 99.874646 : 99.874645;
        assertEquals(cachedPrice1, bond1.cleanPrice(), tolerance,
                "failed to reproduce cached plain price");

        // different risk-free and discount curve

        FloatingRateBond bond2 = new FloatingRateBond(
                settlementDays, faceAmount, sch, index,
                new ActualActual(ActualActual.Convention.ISMA),
                BusinessDayConvention.MODIFIED_FOLLOWING, fixingDays,
                List.of(), List.of(), List.of(), List.of(),
                false, 100.0, new Date(30, Month.NOVEMBER, 2004));
        bond2.setPricingEngine(new DiscountingBondEngine(discountFlat));
        setCouponPricer(bond2.cashflows(), pricer);

        assertEquals(97.955904, bond2.cleanPrice(), tolerance,
                "failed to reproduce cached price with a different discount curve");

        // varying spread

        List<Double> spreads = List.of(0.001, 0.0012, 0.0014, 0.0016);

        FloatingRateBond bond3 = new FloatingRateBond(
                settlementDays, faceAmount, sch, index,
                new ActualActual(ActualActual.Convention.ISMA),
                BusinessDayConvention.MODIFIED_FOLLOWING, fixingDays,
                List.of(), spreads, List.of(), List.of(),
                false, 100.0, new Date(30, Month.NOVEMBER, 2004));
        bond3.setPricingEngine(new DiscountingBondEngine(discountFlat));
        setCouponPricer(bond3.cashflows(), pricer);

        double cachedPrice3 = usingAtParCoupons ? 98.495459 : 98.495458;
        assertEquals(cachedPrice3, bond3.cleanPrice(), tolerance,
                "failed to reproduce cached price with varying spreads");

        // ex-coupon period and a historic fixing

        Schedule sch2 = new Schedule(
                new Date(26, Month.NOVEMBER, 2003),
                new Date(26, Month.NOVEMBER, 2007),
                new Period(Frequency.SEMIANNUAL),
                new UnitedStates(UnitedStates.Market.GovernmentBond),
                BusinessDayConvention.MODIFIED_FOLLOWING,
                BusinessDayConvention.MODIFIED_FOLLOWING,
                DateGenerationRule.BACKWARD, false,
                new Date(), new Date());

        FloatingRateBond bond4 = new FloatingRateBond(
                settlementDays, faceAmount, sch2, index,
                new ActualActual(ActualActual.Convention.ISMA),
                BusinessDayConvention.MODIFIED_FOLLOWING, fixingDays,
                List.of(), spreads, List.of(), List.of(),
                false, 100.0, new Date(29, Month.OCTOBER, 2004),
                new Period(6, TimeUnit.DAYS));

        index.addFixing(new Date(25, Month.MAY, 2004), 0.0402, false);
        bond4.setPricingEngine(new DiscountingBondEngine(discountFlat));
        setCouponPricer(bond4.cashflows(), pricer);

        double cachedPrice4 = usingAtParCoupons ? 98.892055 : 98.892346;
        assertEquals(cachedPrice4, bond4.cleanPrice(), tolerance,
                "failed to reproduce cached ex-coupon price");
    }
}
