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
import jquant.instruments.QuoteBumpRevalue;
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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Engineering tests for the new business API
 * {@link QuoteBumpRevalue}. These are <b>not</b> official BOOST ports and
 * assert no official cached price, date or tolerance.
 *
 * <p>Isolation uses {@link SavedSettings} plus
 * {@link IndexManager#clearHistories()}. The tool restores its own quotes in
 * {@code finally}; tests also restore anything they bump themselves.
 */
public class QuoteBumpRevalueTest {

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
     * Fixed-rate bond, discount rate quote only, default 1bp central
     * difference. The measure is {@code Bond.settlementValue()}.
     */
    @Test
    public void engineeringFixedRateDiscountQuoteCentralPvChange() {
        Date today = new Date(1, Month.JANUARY, 2024);
        Settings.instance.evaluationDate().equal(today);

        SimpleQuote discountQuote = new SimpleQuote(0.03);
        FixedRateBond bond = fixedBond();
        bond.setPricingEngine(new DiscountingBondEngine(
                handle(today, discountQuote)));

        double base = bond.settlementValue();
        try {
            QuoteBumpRevalue.Result result =
                    QuoteBumpRevalue.centralPvChangeForRateQuotes(
                            bond, discountQuote);

            assertEquals(QuoteBumpRevalue.ONE_BASIS_POINT, result.bump(), 0.0,
                    "the rate convenience method must use 1e-4");
            assertEquals(base, result.basePv(), 1.0e-12,
                    "basePv must be the pre-bump settlement value");

            assertTrue(result.centralPvChange() < 0.0,
                    "a long bond must lose value when a discount rate quote "
                            + "rises: " + result.centralPvChange());
            assertTrue(Math.abs(result.centralPvChange()) > 1.0e-6,
                    "the central PV change must be clearly non-zero: "
                            + result.centralPvChange());
            assertTrue(result.upDelta() < 0.0,
                    "upDelta must be negative for a long bond vs a rate: "
                            + result.upDelta());
            assertTrue(result.downDelta() > 0.0,
                    "downDelta must be positive for a long bond vs a rate: "
                            + result.downDelta());

            assertEquals((result.upPv() - result.downPv()) / 2.0,
                    result.centralPvChange(), 0.0,
                    "centralPvChange is (upPv - downPv) / 2, not a derivative");
            assertEquals(result.upPv() - result.basePv(),
                    result.upDelta(), 0.0, "upDelta definition");
            assertEquals(result.downPv() - result.basePv(),
                    result.downDelta(), 0.0, "downDelta definition");

            assertEquals(0.03, discountQuote.value(), 0.0,
                    "the tool must restore the quote");
            assertEquals(base, bond.settlementValue(), 1.0e-12,
                    "after the call the bond must equal the pre-call value");

            // BondFunctions.basisPointValue / yieldValueBasisPoint (and
            // CashFlows.bps) are yield or coupon-annuity sensitivities of the
            // cash-flow leg, reported as unscaled cash amounts. They are a
            // different measure from this quote bump-and-revalue number and
            // are deliberately not asserted here.
        } finally {
            discountQuote.setValue(0.03);
        }
    }

    /**
     * Floating-rate bond with independent discount and forecast quotes:
     * discount only, forecast only, both, and identity deduplication.
     */
    @Test
    public void engineeringFloatingRateDualQuoteMatrixAndDedup() {
        Date today = new Date(1, Month.JANUARY, 2024);
        Settings.instance.evaluationDate().equal(today);

        SimpleQuote discountQuote = new SimpleQuote(0.03);
        SimpleQuote forecastQuote = new SimpleQuote(0.02);

        RelinkableHandle<YieldTermStructure> discount =
                handle(today, discountQuote);
        RelinkableHandle<YieldTermStructure> forecast =
                handle(today, forecastQuote);

        FloatingRateBond bond = floatingBond(
                new AUDLibor(new Period(3, TimeUnit.MONTHS), forecast));
        bond.setPricingEngine(new DiscountingBondEngine(discount));

        double base = bond.settlementValue();
        try {
            QuoteBumpRevalue.Result discountOnly =
                    QuoteBumpRevalue.centralPvChangeForRateQuotes(
                            bond, discountQuote);
            QuoteBumpRevalue.Result forecastOnly =
                    QuoteBumpRevalue.centralPvChangeForRateQuotes(
                            bond, forecastQuote);
            QuoteBumpRevalue.Result both =
                    QuoteBumpRevalue.centralPvChangeForRateQuotes(
                            bond, discountQuote, forecastQuote);
            QuoteBumpRevalue.Result dedup =
                    QuoteBumpRevalue.centralPvChangeForRateQuotes(
                            bond, discountQuote, discountQuote);

            assertTrue(Math.abs(discountOnly.centralPvChange()) > 1.0e-8,
                    "the floating bond must react to the discount quote: "
                            + discountOnly.centralPvChange());
            assertTrue(Math.abs(forecastOnly.centralPvChange()) > 1.0e-8,
                    "the floating bond must react to the forecast quote: "
                            + forecastOnly.centralPvChange());
            assertTrue(discountOnly.centralPvChange() < 0.0,
                    "a discount-rate rise must lower the floating PV: "
                            + discountOnly.centralPvChange());
            assertTrue(forecastOnly.centralPvChange() > 0.0,
                    "a forecast-rate rise must raise the floating PV: "
                            + forecastOnly.centralPvChange());

            assertTrue(Math.abs(both.centralPvChange()
                            - discountOnly.centralPvChange()) > 1.0e-8,
                    "the simultaneous scenario must differ from discount-only");
            assertTrue(Math.abs(both.centralPvChange()
                            - forecastOnly.centralPvChange()) > 1.0e-8,
                    "the simultaneous scenario must differ from forecast-only");

            // A quote passed twice is bumped once: the result is identical to
            // passing it once (identity deduplication, no double shock).
            assertEquals(discountOnly.basePv(), dedup.basePv(), 0.0,
                    "dedup must not change basePv");
            assertEquals(discountOnly.upPv(), dedup.upPv(), 0.0,
                    "dedup must not double-shock upPv");
            assertEquals(discountOnly.downPv(), dedup.downPv(), 0.0,
                    "dedup must not double-shock downPv");
            assertEquals(discountOnly.centralPvChange(),
                    dedup.centralPvChange(), 0.0,
                    "passing the same quote twice must equal passing it once");

            assertEquals(0.03, discountQuote.value(), 0.0,
                    "the discount quote must be restored");
            assertEquals(0.02, forecastQuote.value(), 0.0,
                    "the forecast quote must be restored");
            assertEquals(base, bond.settlementValue(), 1.0e-12,
                    "after the calls the bond must equal the pre-call value");
        } finally {
            discountQuote.setValue(0.03);
            forecastQuote.setValue(0.02);
        }
    }

    /**
     * The general overload honours an explicit bump; the rate convenience
     * method is exactly the general overload with {@code 1e-4}.
     */
    @Test
    public void engineeringExplicitBumpVersusRateDefault() {
        Date today = new Date(1, Month.JANUARY, 2024);
        Settings.instance.evaluationDate().equal(today);

        SimpleQuote discountQuote = new SimpleQuote(0.03);
        FixedRateBond bond = fixedBond();
        bond.setPricingEngine(new DiscountingBondEngine(
                handle(today, discountQuote)));

        double base = bond.settlementValue();
        try {
            QuoteBumpRevalue.Result rateDefault =
                    QuoteBumpRevalue.centralPvChangeForRateQuotes(
                            bond, discountQuote);
            QuoteBumpRevalue.Result explicitOneBp =
                    QuoteBumpRevalue.centralPvChange(
                            bond, QuoteBumpRevalue.ONE_BASIS_POINT,
                            discountQuote);
            QuoteBumpRevalue.Result explicitLarge =
                    QuoteBumpRevalue.centralPvChange(
                            bond, 0.005, discountQuote);

            assertEquals(QuoteBumpRevalue.ONE_BASIS_POINT,
                    rateDefault.bump(), 0.0,
                    "the convenience method must use 1e-4");
            assertEquals(QuoteBumpRevalue.ONE_BASIS_POINT,
                    explicitOneBp.bump(), 0.0,
                    "the explicit bump must be reported unchanged");
            assertEquals(rateDefault.centralPvChange(),
                    explicitOneBp.centralPvChange(), 0.0,
                    "the convenience method is the general call with 1e-4");

            assertEquals(0.005, explicitLarge.bump(), 0.0,
                    "the explicit large bump must be reported unchanged");
            assertTrue(Math.abs(explicitLarge.centralPvChange())
                            > Math.abs(rateDefault.centralPvChange()),
                    "a larger rate bump must move the settlement value more: "
                            + "1bp=" + rateDefault.centralPvChange()
                            + " 50bp=" + explicitLarge.centralPvChange());

            assertEquals(0.03, discountQuote.value(), 0.0,
                    "the tool must restore the quote");
            assertEquals(base, bond.settlementValue(), 1.0e-12,
                    "after the calls the bond must equal the pre-call value");
        } finally {
            discountQuote.setValue(0.03);
        }
    }

    /**
     * A pricing failure during the up bump is rethrown, the quote is
     * restored, and the bond returns to its pre-call value.
     */
    @Test
    public void engineeringPricingFailureIsRethrownAndRestored() {
        Date today = new Date(1, Month.JANUARY, 2024);
        Settings.instance.evaluationDate().equal(today);

        ThrowingOnUpBumpQuote quote =
                new ThrowingOnUpBumpQuote(0.03, 0.03005);
        FixedRateBond bond = fixedBond();
        bond.setPricingEngine(new DiscountingBondEngine(
                handle(today, quote)));

        double base = bond.settlementValue();
        try {
            IllegalStateException error = assertThrows(
                    IllegalStateException.class,
                    () -> QuoteBumpRevalue.centralPvChangeForRateQuotes(
                            bond, quote));
            assertEquals("up bump rejected", error.getMessage(),
                    "the pricing/set failure must not be swallowed");
            assertEquals(0, error.getSuppressed().length,
                    "no restore failure here, so no suppressed exception");

            assertEquals(0.03, quote.value(), 0.0,
                    "the failed bump must be restored");
            assertEquals(base, bond.settlementValue(), 1.0e-12,
                    "after the failure the bond must equal the pre-call value");
        } finally {
            quote.setValue(0.03);
        }
    }

    /**
     * When the restore also fails, the original pricing exception stays the
     * thrown exception and the restore failure is attached as suppressed.
     */
    @Test
    public void engineeringRestoreFailureIsSuppressedOntoPrimary() {
        Date today = new Date(1, Month.JANUARY, 2024);
        Settings.instance.evaluationDate().equal(today);

        AlwaysThrowingQuote quote = new AlwaysThrowingQuote(0.03);
        FixedRateBond bond = fixedBond();
        bond.setPricingEngine(new DiscountingBondEngine(
                handle(today, quote)));

        double base = bond.settlementValue();
        IllegalStateException error = assertThrows(
                IllegalStateException.class,
                () -> QuoteBumpRevalue.centralPvChangeForRateQuotes(
                        bond, quote));

        assertEquals("setValue failure 1", error.getMessage(),
                "the original bump failure must remain the thrown exception");
        assertEquals(1, error.getSuppressed().length,
                "the restore failure must be suppressed, not replace the primary");
        assertEquals("setValue failure 2", error.getSuppressed()[0].getMessage(),
                "the suppressed exception must be the restore failure");
        assertEquals(2, quote.setValueCalls(),
                "one failed bump and one failed restore");

        // The quote's value was never changed, so there is nothing to restore
        // here; the bond is still consistent with its pre-call state.
        assertEquals(0.03, quote.value(), 0.0,
                "the failed setValue must not change the value");
        assertEquals(base, bond.settlementValue(), 1.0e-12,
                "after the failure the bond must equal the pre-call value");
    }

    /** SimpleQuote that rejects any up-bump above a fixed threshold. */
    private static final class ThrowingOnUpBumpQuote extends SimpleQuote {
        private final double failAbove;

        private ThrowingOnUpBumpQuote(double value, double failAbove) {
            super(value);
            this.failAbove = failAbove;
        }

        @Override
        public double setValue(double value) {
            if (value > failAbove)
                throw new IllegalStateException("up bump rejected");
            return super.setValue(value);
        }
    }

    /** SimpleQuote whose setValue always fails; used to fail the restore too. */
    private static final class AlwaysThrowingQuote extends SimpleQuote {
        private int setValueCalls;

        private AlwaysThrowingQuote(double value) {
            super(value);
        }

        @Override
        public double setValue(double value) {
            ++setValueCalls;
            throw new IllegalStateException(
                    "setValue failure " + setValueCalls);
        }

        private int setValueCalls() {
            return setValueCalls;
        }
    }
}
