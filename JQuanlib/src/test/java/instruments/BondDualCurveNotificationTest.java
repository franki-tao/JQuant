package instruments;

import jquant.Handle;
import jquant.RelinkableHandle;
import jquant.SavedSettings;
import jquant.Settings;
import jquant.cashflows.BlackIborCouponPricer;
import jquant.indexes.AUDLibor;
import jquant.indexes.IborIndex;
import jquant.indexes.IndexManager;
import jquant.instruments.FixedRateBond;
import jquant.instruments.FloatingRateBond;
import jquant.pricingengines.DiscountingBondEngine;
import jquant.quotes.SimpleQuote;
import jquant.termstructures.YieldTermStructure;
import jquant.termstructures.yield.FlatForward;
import jquant.time.BusinessDayConvention;
import jquant.time.Calendar;
import jquant.time.Date;
import jquant.time.DateGenerationRule;
import jquant.time.Frequency;
import jquant.time.Month;
import jquant.time.Period;
import jquant.time.Schedule;
import jquant.time.TimeUnit;
import jquant.time.calendars.Australia;
import jquant.time.calendars.UnitedStates;
import jquant.time.daycounters.Actual365Fixed;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static jquant.cashflows.CashflowUtil.setCouponPricer;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Engineering acceptance for the discount/forecast dual-curve wiring of the
 * bond and {@link DiscountingBondEngine} observers. These are <b>not</b>
 * official BOOST ports: no official dates, cached prices or tolerances are
 * asserted, and every price assertion is a relative-change, no-change or
 * restore identity.
 *
 * <p>A fixed-rate bond has no forecast curve: it must react only to the
 * discount curve. A floating-rate bond depends on both curves: its coupons
 * forecast on the index curve while its cash flows discount on the engine
 * curve. The two curves have independent {@link SimpleQuote}s and independent
 * {@link RelinkableHandle}s except in the explicitly shared case.
 *
 * <p>Mutations are always undone in {@code finally}; after the restore each
 * price must match its original value to 1e-12. Moved checks use 1e-8.
 */
public class BondDualCurveNotificationTest {

    /** "The price moved" threshold; not an official tolerance. */
    private static final double MOVED = 1.0e-8;

    /** "The restored price is identical" threshold; not an official tolerance. */
    private static final double RESTORED = 1.0e-12;

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

    /** Ordinary annual fixed-rate bond: 15 Jan 2024 - 15 Jan 2026, 5%. */
    private static FixedRateBond fixedBond() {
        Calendar calendar = new UnitedStates(UnitedStates.Market.GovernmentBond);
        Schedule schedule = new Schedule(
                new Date(15, Month.JANUARY, 2024),
                new Date(15, Month.JANUARY, 2026),
                new Period(Frequency.ANNUAL),
                calendar,
                BusinessDayConvention.UNADJUSTED,
                BusinessDayConvention.UNADJUSTED,
                DateGenerationRule.FORWARD,
                false,
                new Date(),
                new Date());
        return new FixedRateBond(0, 100.0, schedule, new double[] {0.05},
                new Actual365Fixed(), BusinessDayConvention.UNADJUSTED, 100.0);
    }

    /** Ordinary quarterly Ibor floating-rate bond, 22 Mar 2024 - 22 Dec 2024. */
    private static FloatingRateBond floatingBond(IborIndex index) {
        Schedule schedule = new Schedule(
                new Date(22, Month.MARCH, 2024),
                new Date(22, Month.DECEMBER, 2024),
                new Period(Frequency.QUARTERLY),
                new Australia(),
                BusinessDayConvention.UNADJUSTED,
                BusinessDayConvention.UNADJUSTED,
                DateGenerationRule.FORWARD,
                false,
                new Date(),
                new Date());
        FloatingRateBond bond = new FloatingRateBond(
                0, 100.0, schedule, index, new Actual365Fixed(),
                BusinessDayConvention.FOLLOWING, 0);
        setCouponPricer(bond.cashflows(), new BlackIborCouponPricer());
        return bond;
    }

    private static RelinkableHandle<YieldTermStructure> handle(
            Date referenceDate, SimpleQuote quote) {
        FlatForward curve = new FlatForward(referenceDate,
                new Handle<>(quote, true), new Actual365Fixed());
        return new RelinkableHandle<>(curve, true);
    }

    /**
     * Quote changes: discount only, forecast only, and both. The fixed bond
     * must move only with the discount quote; the floating bond must move with
     * both. No manual {@code setPricingEngine} is used after a quote change.
     */
    @Test
    public void engineeringQuoteChangesDiscountForecastAndBoth() {
        Date today = new Date(1, Month.JANUARY, 2024);
        Settings.instance.evaluationDate().equal(today);

        SimpleQuote discountQuote = new SimpleQuote(0.03);
        SimpleQuote forecastQuote = new SimpleQuote(0.02);

        RelinkableHandle<YieldTermStructure> discount = handle(today, discountQuote);
        RelinkableHandle<YieldTermStructure> forecast = handle(today, forecastQuote);

        FixedRateBond fixed = fixedBond();
        fixed.setPricingEngine(new DiscountingBondEngine(discount));

        FloatingRateBond floating = floatingBond(
                new AUDLibor(new Period(3, TimeUnit.MONTHS), forecast));
        floating.setPricingEngine(new DiscountingBondEngine(discount));

        double fixedBase = Double.NaN;
        double floatingBase = Double.NaN;
        double fixedDiscountOnly;
        double floatingForecastOnly;
        try {
            fixedBase = fixed.cleanPrice();
            floatingBase = floating.cleanPrice();

            // Discount quote only: both bonds must reprice.
            discountQuote.setValue(0.04);
            fixedDiscountOnly = fixed.cleanPrice();
            double floatingDiscountOnly = floating.cleanPrice();
            assertTrue(Math.abs(fixedDiscountOnly - fixedBase) > MOVED,
                    "the fixed-rate bond must follow a discount quote change: base="
                            + fixedBase + " repriced=" + fixedDiscountOnly);
            assertTrue(Math.abs(floatingDiscountOnly - floatingBase) > MOVED,
                    "the floating-rate bond must follow a discount quote change: base="
                            + floatingBase + " repriced=" + floatingDiscountOnly);

            discountQuote.setValue(0.03);
            assertEquals(fixedBase, fixed.cleanPrice(), RESTORED,
                    "restoring the discount quote must restore the fixed price");
            assertEquals(floatingBase, floating.cleanPrice(), RESTORED,
                    "restoring the discount quote must restore the floating price");

            // Forecast quote only: only the floating bond must reprice.
            forecastQuote.setValue(0.03);
            double fixedForecastOnly = fixed.cleanPrice();
            floatingForecastOnly = floating.cleanPrice();
            assertEquals(fixedBase, fixedForecastOnly, 0.0,
                    "the fixed-rate bond must not react to a forecast quote change");
            assertTrue(Math.abs(floatingForecastOnly - floatingBase) > MOVED,
                    "the floating-rate bond must follow a forecast quote change: base="
                            + floatingBase + " repriced=" + floatingForecastOnly);

            forecastQuote.setValue(0.02);
            assertEquals(fixedBase, fixed.cleanPrice(), RESTORED,
                    "restoring the forecast quote must restore the fixed price");
            assertEquals(floatingBase, floating.cleanPrice(), RESTORED,
                    "restoring the forecast quote must restore the floating price");

            // Both quotes: the fixed change must equal the discount-only change
            // and the floating bond must include both effects.
            discountQuote.setValue(0.04);
            forecastQuote.setValue(0.03);
            double fixedBoth = fixed.cleanPrice();
            double floatingBoth = floating.cleanPrice();
            assertEquals(fixedDiscountOnly, fixedBoth, RESTORED,
                    "the fixed-rate bond's both-curves move must match the "
                            + "discount-only move of the same size");
            assertTrue(Math.abs(floatingBoth - floatingBase) > MOVED,
                    "the floating-rate bond must follow the both-curves change: base="
                            + floatingBase + " repriced=" + floatingBoth);
            assertTrue(Math.abs(floatingBoth - floatingForecastOnly) > MOVED,
                    "the both-curves floating price must also include the "
                            + "discount move: forecast-only=" + floatingForecastOnly
                            + " both=" + floatingBoth);
        } finally {
            discountQuote.setValue(0.03);
            forecastQuote.setValue(0.02);
        }

        assertEquals(fixedBase, fixed.cleanPrice(), RESTORED,
                "after the finally restore the fixed price must be the original");
        assertEquals(floatingBase, floating.cleanPrice(), RESTORED,
                "after the finally restore the floating price must be the original");
    }

    /**
     * Handle link changes: discount only, forecast only, and both. As above,
     * the fixed bond must ignore a forecast relink, and every link is restored
     * in {@code finally}. No manual {@code setPricingEngine} is used.
     */
    @Test
    public void engineeringLinkChangesDiscountForecastAndBoth() {
        Date today = new Date(1, Month.JANUARY, 2024);
        Settings.instance.evaluationDate().equal(today);

        FlatForward discountThree = new FlatForward(
                today, 0.03, new Actual365Fixed());
        FlatForward discountFour = new FlatForward(
                today, 0.04, new Actual365Fixed());
        FlatForward forecastTwo = new FlatForward(
                today, 0.02, new Actual365Fixed());
        FlatForward forecastThree = new FlatForward(
                today, 0.03, new Actual365Fixed());

        RelinkableHandle<YieldTermStructure> discount =
                new RelinkableHandle<>(discountThree, true);
        RelinkableHandle<YieldTermStructure> forecast =
                new RelinkableHandle<>(forecastTwo, true);

        FixedRateBond fixed = fixedBond();
        fixed.setPricingEngine(new DiscountingBondEngine(discount));

        FloatingRateBond floating = floatingBond(
                new AUDLibor(new Period(3, TimeUnit.MONTHS), forecast));
        floating.setPricingEngine(new DiscountingBondEngine(discount));

        double fixedBase = Double.NaN;
        double floatingBase = Double.NaN;
        double fixedDiscountOnly;
        double floatingForecastOnly;
        try {
            fixedBase = fixed.cleanPrice();
            floatingBase = floating.cleanPrice();

            // Discount link only: both bonds must reprice.
            discount.linkTo(discountFour, true);
            fixedDiscountOnly = fixed.cleanPrice();
            double floatingDiscountOnly = floating.cleanPrice();
            assertTrue(Math.abs(fixedDiscountOnly - fixedBase) > MOVED,
                    "the fixed-rate bond must follow a discount relink: base="
                            + fixedBase + " repriced=" + fixedDiscountOnly);
            assertTrue(Math.abs(floatingDiscountOnly - floatingBase) > MOVED,
                    "the floating-rate bond must follow a discount relink: base="
                            + floatingBase + " repriced=" + floatingDiscountOnly);

            discount.linkTo(discountThree, true);
            assertEquals(fixedBase, fixed.cleanPrice(), RESTORED,
                    "restoring the discount link must restore the fixed price");
            assertEquals(floatingBase, floating.cleanPrice(), RESTORED,
                    "restoring the discount link must restore the floating price");

            // Forecast link only: only the floating bond must reprice.
            forecast.linkTo(forecastThree, true);
            double fixedForecastOnly = fixed.cleanPrice();
            floatingForecastOnly = floating.cleanPrice();
            assertEquals(fixedBase, fixedForecastOnly, 0.0,
                    "the fixed-rate bond must not react to a forecast relink");
            assertTrue(Math.abs(floatingForecastOnly - floatingBase) > MOVED,
                    "the floating-rate bond must follow a forecast relink: base="
                            + floatingBase + " repriced=" + floatingForecastOnly);

            forecast.linkTo(forecastTwo, true);
            assertEquals(fixedBase, fixed.cleanPrice(), RESTORED,
                    "restoring the forecast link must restore the fixed price");
            assertEquals(floatingBase, floating.cleanPrice(), RESTORED,
                    "restoring the forecast link must restore the floating price");

            // Both links: the fixed move must match the discount-only move and
            // the floating bond must include both effects.
            discount.linkTo(discountFour, true);
            forecast.linkTo(forecastThree, true);
            double fixedBoth = fixed.cleanPrice();
            double floatingBoth = floating.cleanPrice();
            assertEquals(fixedDiscountOnly, fixedBoth, RESTORED,
                    "the fixed-rate bond's both-links move must match the "
                            + "discount-only move of the same size");
            assertTrue(Math.abs(floatingBoth - floatingBase) > MOVED,
                    "the floating-rate bond must follow the both-links change: base="
                            + floatingBase + " repriced=" + floatingBoth);
            assertTrue(Math.abs(floatingBoth - floatingForecastOnly) > MOVED,
                    "the both-links floating price must also include the "
                            + "discount move: forecast-only=" + floatingForecastOnly
                            + " both=" + floatingBoth);
        } finally {
            discount.linkTo(discountThree, true);
            forecast.linkTo(forecastTwo, true);
        }

        assertEquals(fixedBase, fixed.cleanPrice(), RESTORED,
                "after the finally restore the fixed price must be the original");
        assertEquals(floatingBase, floating.cleanPrice(), RESTORED,
                "after the finally restore the floating price must be the original");
    }

    /**
     * Explicit shared-curve case: one quote, curve and Handle are used as
     * discount curve by both engines and as forecast curve by the index. A
     * single quote change must update the floating bond through both paths;
     * the result must equal a reference floating bond priced on two separate
     * but equally-valued curves, so the shared notification is not
     * double-counted. The fixed bond sees only the discount side.
     */
    @Test
    public void engineeringSharedCurveUpdatesFloatingThroughBothPaths() {
        Date today = new Date(1, Month.JANUARY, 2024);
        Settings.instance.evaluationDate().equal(today);

        SimpleQuote sharedQuote = new SimpleQuote(0.05);
        RelinkableHandle<YieldTermStructure> shared = handle(today, sharedQuote);

        FixedRateBond fixed = fixedBond();
        fixed.setPricingEngine(new DiscountingBondEngine(shared));

        FloatingRateBond floating = floatingBond(
                new AUDLibor(new Period(3, TimeUnit.MONTHS), shared));
        floating.setPricingEngine(new DiscountingBondEngine(shared));

        double fixedBase = Double.NaN;
        double floatingBase = Double.NaN;
        try {
            fixedBase = fixed.cleanPrice();
            floatingBase = floating.cleanPrice();

            sharedQuote.setValue(0.06);
            double fixedRepriced = fixed.cleanPrice();
            double floatingRepriced = floating.cleanPrice();

            assertTrue(Math.abs(fixedRepriced - fixedBase) > MOVED,
                    "the shared discount quote must move the fixed-rate bond: base="
                            + fixedBase + " repriced=" + fixedRepriced);
            assertTrue(Math.abs(floatingRepriced - floatingBase) > MOVED,
                    "the shared quote must move the floating-rate bond: base="
                            + floatingBase + " repriced=" + floatingRepriced);

            // Fully consistent reference: separate 6% forecast and discount
            // curves. Equality proves that both shared paths were refreshed.
            FlatForward referenceForecast = new FlatForward(
                    today, 0.06, new Actual365Fixed());
            FlatForward referenceDiscount = new FlatForward(
                    today, 0.06, new Actual365Fixed());
            FloatingRateBond reference = floatingBond(
                    new AUDLibor(new Period(3, TimeUnit.MONTHS),
                            new Handle<>(referenceForecast, true)));
            reference.setPricingEngine(
                    new DiscountingBondEngine(referenceDiscount));

            assertEquals(reference.cleanPrice(), floatingRepriced, RESTORED,
                    "the shared quote must reprice the floating bond through "
                            + "both the forecast and the discount path");
        } finally {
            sharedQuote.setValue(0.05);
        }

        assertEquals(fixedBase, fixed.cleanPrice(), RESTORED,
                "after the finally restore the fixed price must be the original");
        assertEquals(floatingBase, floating.cleanPrice(), RESTORED,
                "after the finally restore the floating price must be the original");
    }
}
