package instruments;

import jquant.CashFlow;
import jquant.Handle;
import jquant.RelinkableHandle;
import jquant.SavedSettings;
import jquant.Settings;
import jquant.cashflows.BlackIborCouponPricer;
import jquant.cashflows.FloatingRateCoupon;
import jquant.cashflows.IborCouponPricer;
import jquant.indexes.AUDLibor;
import jquant.indexes.IborIndex;
import jquant.indexes.IndexManager;
import jquant.instruments.FloatingRateBond;
import jquant.pricingengines.DiscountingBondEngine;
import jquant.quotes.SimpleQuote;
import jquant.termstructures.volatility.optionlet.ConstantOptionletVolatility;
import jquant.termstructures.volatility.optionlet.OptionletVolatilityStructure;
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
import jquant.time.daycounters.Actual365Fixed;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static jquant.cashflows.CashflowUtil.setCouponPricer;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Engineering tests, <b>not</b> official BOOST ports. They check whether a
 * capped/floored floating-rate bond automatically reprices when the Black
 * caplet volatility {@link SimpleQuote} changes, and when the same
 * {@link RelinkableHandle} is relinked to a new volatility surface.
 *
 * <p>Official {@code IborCouponPricer} registers with the caplet-vol
 * {@code Handle} (and {@code setCapletVolatility} unregisters the old Handle
 * before registering the new one). Java must register the Handle's observable
 * {@link Handle#get() Link}, not {@code currentLink()} (the current surface).
 * {@code new Handle(surface)} creates a distinct Link and does not share a
 * relink with another Handle around the same surface; tests therefore pass
 * the same Handle/RelinkableHandle instance.
 *
 * <p>A later quote change on an unbound old surface must not move the new
 * price. Fresh-bond comparisons are engineering references, not official
 * cached values. Quotes and links are restored in {@code finally}.
 */
public class FloatingRateBondCapletVolNotificationTest {

    private static final Date TODAY = new Date(1, Month.JANUARY, 2024);
    private static final double FACE = 100.0;
    private static final double BASE_VOL = 0.10;
    private static final double BUMPED_VOL = 0.40;
    private static final double CAP = 0.03;
    private static final double FLOOR = 0.07;
    private static final double RATE = 0.05;

    private SavedSettings savedSettings;

    @BeforeEach
    void saveSettings() {
        savedSettings = new SavedSettings();
        Settings.instance.evaluationDate().equal(TODAY);
    }

    @AfterEach
    void restoreSettings() {
        IndexManager.Instance.clearHistories();
        savedSettings.restore();
    }

    private static Schedule schedule(Calendar calendar) {
        return new Schedule(
                new Date(1, Month.JANUARY, 2024),
                new Date(1, Month.JANUARY, 2027),
                new Period(Frequency.QUARTERLY),
                calendar,
                BusinessDayConvention.FOLLOWING,
                BusinessDayConvention.FOLLOWING,
                DateGenerationRule.FORWARD, false,
                new Date(), new Date());
    }

    private static ConstantOptionletVolatility constantVol(Handle<jquant.Quote> quote) {
        return new ConstantOptionletVolatility(
                TODAY, new Australia(), BusinessDayConvention.FOLLOWING,
                quote, new Actual365Fixed());
    }

    private static FloatingRateBond makeFrn(
            Handle<OptionletVolatilityStructure> capletVol,
            List<Double> caps,
            List<Double> floors) {
        Calendar calendar = new Australia();
        FlatForward curve = new FlatForward(TODAY, RATE, new Actual365Fixed());
        IborIndex index = new AUDLibor(new Period(3, TimeUnit.MONTHS),
                new Handle<>(curve, true));

        FloatingRateBond bond = new FloatingRateBond(
                0, FACE, schedule(calendar), index, new Actual365Fixed(),
                BusinessDayConvention.FOLLOWING, 0,
                List.of(), List.of(), caps, floors,
                false, 100.0, new Date());
        bond.setPricingEngine(new DiscountingBondEngine(curve));
        setCouponPricer(bond.cashflows(), new BlackIborCouponPricer(capletVol));
        return bond;
    }

    /** Shared-handle capped FRN; the same Handle instance is required for relink. */
    private static FloatingRateBond makeCappedFrn(
            Handle<OptionletVolatilityStructure> capletVol) {
        return makeFrn(capletVol, List.of(CAP), List.of());
    }

    private static FloatingRateBond makeFlooredFrn(
            Handle<OptionletVolatilityStructure> capletVol) {
        return makeFrn(capletVol, List.of(), List.of(FLOOR));
    }

    /**
     * The last coupon whose fixing is still in the future. Past or
     * same-day fixings are already determined and do not depend on
     * caplet volatility (official BlackIborCouponPricer path).
     */
    private static FloatingRateCoupon futureFloatingCoupon(FloatingRateBond bond) {
        FloatingRateCoupon last = null;
        Date today = Settings.instance.evaluationDate().Date();
        for (CashFlow flow : bond.cashflows()) {
            if (flow instanceof FloatingRateCoupon coupon
                    && today.serialNumber() < coupon.fixingDate().serialNumber())
                last = coupon;
        }
        if (last == null)
            throw new IllegalStateException("no future floating coupon");
        return last;
    }

    private static IborCouponPricer iborPricer(FloatingRateBond bond) {
        return (IborCouponPricer) futureFloatingCoupon(bond).pricer();
    }

    @Test
    public void capletVolQuoteChangeRepricesExistingBondWithoutRecalculate() {
        SimpleQuote volQuote = new SimpleQuote(BASE_VOL);
        Handle<OptionletVolatilityStructure> capletVol = new Handle<>(
                constantVol(new Handle<>(volQuote, true)), true);

        FloatingRateBond bond = makeCappedFrn(capletVol);
        double npvBefore = bond.NPV();
        double settlementBefore = bond.settlementValue();
        FloatingRateCoupon coupon = futureFloatingCoupon(bond);
        double couponBefore = coupon.amount();

        try {
            volQuote.setValue(BUMPED_VOL);

            // The coupon observes the pricer, which observes the current vol
            // structure, which observes the quote Handle. A quote bump must
            // already be visible on the coupon even if the bond cache is not.
            double couponAfter = coupon.amount();
            assertTrue(Math.abs(couponAfter - couponBefore) > 1.0e-8,
                    "caplet vol quote change must move the coupon amount: before="
                            + couponBefore + " after=" + couponAfter);

            // Existing bond: no recalculate / no update / no new engine.
            double npvExisting = bond.NPV();
            double settlementExisting = bond.settlementValue();

            FloatingRateBond fresh = makeCappedFrn(capletVol);
            double npvFresh = fresh.NPV();
            double settlementFresh = fresh.settlementValue();

            assertTrue(Math.abs(npvFresh - npvBefore) > 1.0e-8,
                    "a freshly built bond must move when the vol quote changes: before="
                            + npvBefore + " fresh=" + npvFresh);

            assertEquals(npvFresh, npvExisting, 1.0e-10,
                    "existing bond must auto-reprice after a caplet vol quote "
                            + "change, without recalculate; couponBefore="
                            + couponBefore + " couponAfter=" + couponAfter
                            + " npvBefore=" + npvBefore
                            + " npvExisting=" + npvExisting
                            + " npvFresh=" + npvFresh
                            + " settlementBefore=" + settlementBefore
                            + " settlementExisting=" + settlementExisting
                            + " settlementFresh=" + settlementFresh);

            assertEquals(settlementFresh, settlementExisting, 1.0e-10,
                    "existing bond settlementValue must auto-reprice with NPV");

            volQuote.setValue(BASE_VOL);
            assertEquals(npvBefore, bond.NPV(), 1.0e-10,
                    "restoring the vol quote must restore the existing bond NPV");
            assertEquals(settlementBefore, bond.settlementValue(), 1.0e-10,
                    "restoring the vol quote must restore settlementValue");
            assertEquals(couponBefore, coupon.amount(), 1.0e-10,
                    "restoring the vol quote must restore the coupon amount");
        } finally {
            volQuote.setValue(BASE_VOL);
        }
    }

    @Test
    public void capletVolQuoteRestoredInFinallyAfterFailure() {
        SimpleQuote volQuote = new SimpleQuote(BASE_VOL);
        Handle<OptionletVolatilityStructure> capletVol = new Handle<>(
                constantVol(new Handle<>(volQuote, true)), true);

        FloatingRateBond bond = makeCappedFrn(capletVol);
        double npvBefore = bond.NPV();
        RuntimeException thrown = null;
        try {
            volQuote.setValue(BUMPED_VOL);
            assertTrue(Math.abs(bond.NPV() - npvBefore) > 1.0e-8,
                    "vol bump must move the bond before the simulated failure");
            throw new IllegalStateException("simulated failure after vol bump");
        } catch (IllegalStateException ex) {
            thrown = ex;
        } finally {
            volQuote.setValue(BASE_VOL);
        }
        assertTrue(thrown != null && "simulated failure after vol bump".equals(thrown.getMessage()));
        assertEquals(npvBefore, bond.NPV(), 1.0e-10,
                "finally-restored vol quote must restore the existing bond NPV "
                        + "without a manual recalculate");
    }

    @Test
    public void capletVolHandleRelinkRepricesExistingBondAndUnbindsOldSurface() {
        SimpleQuote oldQuote = new SimpleQuote(BASE_VOL);
        SimpleQuote newQuote = new SimpleQuote(BUMPED_VOL);
        OptionletVolatilityStructure vol1 = constantVol(new Handle<>(oldQuote, true));
        OptionletVolatilityStructure vol2 = constantVol(new Handle<>(newQuote, true));
        RelinkableHandle<OptionletVolatilityStructure> capletVol =
                new RelinkableHandle<>(vol1, true);

        FloatingRateBond bond = makeCappedFrn(capletVol);
        double npvBefore = bond.NPV();
        double settlementBefore = bond.settlementValue();
        FloatingRateCoupon coupon = futureFloatingCoupon(bond);
        double couponBefore = coupon.amount();

        try {
            capletVol.linkTo(vol2, true);

            FloatingRateBond fresh = makeCappedFrn(capletVol);
            double npvFresh = fresh.NPV();
            double settlementFresh = fresh.settlementValue();
            double couponAfter = coupon.amount();
            double npvExisting = bond.NPV();
            double settlementExisting = bond.settlementValue();

            assertTrue(Math.abs(npvFresh - npvBefore) > 1.0e-8,
                    "a freshly built bond must see the relinked caplet vol; before="
                            + npvBefore + " fresh=" + npvFresh);
            assertTrue(Math.abs(couponAfter - couponBefore) > 1.0e-8,
                    "relink must move the existing coupon without recalculate; before="
                            + couponBefore + " after=" + couponAfter);
            assertEquals(npvFresh, npvExisting, 1.0e-10,
                    "existing bond must auto-reprice after Handle relink, "
                            + "without recalculate or rebuild; couponBefore="
                            + couponBefore + " couponAfter=" + couponAfter
                            + " npvBefore=" + npvBefore
                            + " npvExisting=" + npvExisting
                            + " npvFresh=" + npvFresh);
            assertEquals(settlementFresh, settlementExisting, 1.0e-10,
                    "existing settlementValue must auto-reprice after Handle relink");

            // The old surface is unbound: a quote change on it must not
            // reach the pricer or move the new price.
            oldQuote.setValue(0.80);
            assertEquals(npvFresh, bond.NPV(), 1.0e-10,
                    "a quote change on the unbound old surface must not move "
                            + "the existing bond after relink");
            assertEquals(fresh.NPV(), bond.NPV(), 1.0e-10,
                    "old-surface quote change must not move the fresh bond "
                            + "built on the new surface either");

            // The new surface is live: its quote change must still auto-reprice.
            newQuote.setValue(0.25);
            FloatingRateBond freshAfterNewQuote = makeCappedFrn(capletVol);
            assertTrue(Math.abs(freshAfterNewQuote.NPV() - npvFresh) > 1.0e-8,
                    "a quote change on the new surface must move a fresh bond");
            assertEquals(freshAfterNewQuote.NPV(), bond.NPV(), 1.0e-10,
                    "existing bond must follow a quote change on the new surface");
            newQuote.setValue(BUMPED_VOL);

            oldQuote.setValue(BASE_VOL);
            capletVol.linkTo(vol1, true);
            assertEquals(npvBefore, bond.NPV(), 1.0e-10,
                    "restoring the original link and quote must restore NPV");
            assertEquals(settlementBefore, bond.settlementValue(), 1.0e-10,
                    "restoring the original link and quote must restore settlementValue");
            assertEquals(couponBefore, coupon.amount(), 1.0e-10,
                    "restoring the original link and quote must restore the coupon");
        } finally {
            oldQuote.setValue(BASE_VOL);
            newQuote.setValue(BUMPED_VOL);
            capletVol.linkTo(vol1, true);
        }
    }

    @Test
    public void floorletVolHandleRelinkRepricesExistingBond() {
        SimpleQuote oldQuote = new SimpleQuote(BASE_VOL);
        SimpleQuote newQuote = new SimpleQuote(BUMPED_VOL);
        OptionletVolatilityStructure vol1 = constantVol(new Handle<>(oldQuote, true));
        OptionletVolatilityStructure vol2 = constantVol(new Handle<>(newQuote, true));
        RelinkableHandle<OptionletVolatilityStructure> capletVol =
                new RelinkableHandle<>(vol1, true);

        FloatingRateBond bond = makeFlooredFrn(capletVol);
        double npvBefore = bond.NPV();
        FloatingRateCoupon coupon = futureFloatingCoupon(bond);
        double couponBefore = coupon.amount();

        try {
            capletVol.linkTo(vol2, true);
            FloatingRateBond fresh = makeFlooredFrn(capletVol);
            assertTrue(Math.abs(fresh.NPV() - npvBefore) > 1.0e-8,
                    "floored fresh bond must move on vol relink; before="
                            + npvBefore + " fresh=" + fresh.NPV());
            assertTrue(Math.abs(coupon.amount() - couponBefore) > 1.0e-8,
                    "floored existing coupon must move on vol relink");
            assertEquals(fresh.NPV(), bond.NPV(), 1.0e-10,
                    "existing floored bond must auto-reprice after Handle relink");

            oldQuote.setValue(0.80);
            assertEquals(fresh.NPV(), bond.NPV(), 1.0e-10,
                    "old floor-vol surface quote must not move the new price");

            oldQuote.setValue(BASE_VOL);
            capletVol.linkTo(vol1, true);
            assertEquals(npvBefore, bond.NPV(), 1.0e-10,
                    "restoring the floored-bond vol link must restore NPV");
        } finally {
            oldQuote.setValue(BASE_VOL);
            newQuote.setValue(BUMPED_VOL);
            capletVol.linkTo(vol1, true);
        }
    }

    @Test
    public void emptyCapletVolHandleThenLinkRepricesWithoutRebuild() {
        RelinkableHandle<OptionletVolatilityStructure> capletVol =
                new RelinkableHandle<>();
        OptionletVolatilityStructure vol = constantVol(
                new Handle<>(new SimpleQuote(BASE_VOL), true));

        FloatingRateBond bond = makeCappedFrn(capletVol);
        // Official empty caplet vol is an error, not a silent zero price.
        Exception empty = assertThrows(Exception.class, bond::NPV,
                "pricing with an empty caplet-vol Handle must not succeed silently");
        assertTrue(empty.getMessage() != null && !empty.getMessage().isBlank(),
                "empty-handle pricing error should carry a message, got: "
                        + empty);

        capletVol.linkTo(vol, true);
        FloatingRateBond fresh = makeCappedFrn(capletVol);
        assertEquals(fresh.NPV(), bond.NPV(), 1.0e-10,
                "linking an originally empty Handle must reprice the existing "
                        + "bond without rebuild; error was: " + empty.getMessage());

        // A first successful NPV after the empty-handle error already leaves
        // the cache dirty, so the assertion above does not by itself prove
        // Handle subscription. Relink now, with a filled cache, must notify.
        double npvAfterFirstLink = bond.NPV();
        OptionletVolatilityStructure bumped = constantVol(
                new Handle<>(new SimpleQuote(BUMPED_VOL), true));
        capletVol.linkTo(bumped, true);
        FloatingRateBond freshBumped = makeCappedFrn(capletVol);
        assertTrue(Math.abs(freshBumped.NPV() - npvAfterFirstLink) > 1.0e-8,
                "fresh bond must move on a later relink of the originally empty Handle");
        assertEquals(freshBumped.NPV(), bond.NPV(), 1.0e-10,
                "an originally empty Handle must still notify after the first link");
    }

    @Test
    public void setCapletVolatilitySameHandleAndNewHandleUnbindsOld() {
        SimpleQuote oldQuote = new SimpleQuote(BASE_VOL);
        SimpleQuote newQuote = new SimpleQuote(BUMPED_VOL);
        OptionletVolatilityStructure vol1 = constantVol(new Handle<>(oldQuote, true));
        OptionletVolatilityStructure vol2 = constantVol(new Handle<>(newQuote, true));
        RelinkableHandle<OptionletVolatilityStructure> handle1 =
                new RelinkableHandle<>(vol1, true);
        RelinkableHandle<OptionletVolatilityStructure> handle2 =
                new RelinkableHandle<>(vol2, true);

        FloatingRateBond bond = makeCappedFrn(handle1);
        IborCouponPricer pricer = iborPricer(bond);
        double npvOnVol1 = bond.NPV();

        try {
            // Setting the same Handle instance again must not drop the
            // subscription (unregister then register the same Link).
            pricer.setCapletVolatility(handle1);
            // setCapletVolatility always calls update(), which dirties the
            // cache; reprice first so the next relink has to arrive as a
            // Handle notification, not a leftover uncalculated flag.
            assertEquals(npvOnVol1, bond.NPV(), 1.0e-10,
                    "setCapletVolatility of the same Handle must not change the price");
            handle1.linkTo(vol2, true);
            FloatingRateBond freshOnVol2ViaSameHandle = makeCappedFrn(handle1);
            assertEquals(freshOnVol2ViaSameHandle.NPV(), bond.NPV(), 1.0e-10,
                    "setCapletVolatility of the same Handle must still follow relink");

            handle1.linkTo(vol1, true);
            assertEquals(npvOnVol1, bond.NPV(), 1.0e-10,
                    "relinking the same Handle back must restore NPV");

            // A different Handle instance (even around a surface) is a new
            // Link. After setCapletVolatility(handle2), handle1 / vol1 must
            // be unbound.
            pricer.setCapletVolatility(handle2);
            FloatingRateBond freshOnHandle2 = makeCappedFrn(handle2);
            assertEquals(freshOnHandle2.NPV(), bond.NPV(), 1.0e-10,
                    "setCapletVolatility to a new Handle must reprice without rebuild");
            assertTrue(Math.abs(bond.NPV() - npvOnVol1) > 1.0e-8,
                    "the new Handle's surface must actually move the price");

            oldQuote.setValue(0.80);
            assertEquals(freshOnHandle2.NPV(), bond.NPV(), 1.0e-10,
                    "quote changes on the previous Handle's surface must not "
                            + "move the price after setCapletVolatility");

            newQuote.setValue(0.25);
            FloatingRateBond freshAfterNewQuote = makeCappedFrn(handle2);
            assertEquals(freshAfterNewQuote.NPV(), bond.NPV(), 1.0e-10,
                    "the existing bond must follow quotes on the new Handle");
            newQuote.setValue(BUMPED_VOL);

            pricer.setCapletVolatility(handle1);
            oldQuote.setValue(BASE_VOL);
            assertEquals(npvOnVol1, bond.NPV(), 1.0e-10,
                    "switching back to the original Handle must restore NPV");
        } finally {
            oldQuote.setValue(BASE_VOL);
            newQuote.setValue(BUMPED_VOL);
            handle1.linkTo(vol1, true);
            pricer.setCapletVolatility(handle1);
        }
    }

    @Test
    public void unlinkingCapletVolHandleDoesNotKeepOldSurface() {
        SimpleQuote quote = new SimpleQuote(BASE_VOL);
        OptionletVolatilityStructure vol = constantVol(new Handle<>(quote, true));
        RelinkableHandle<OptionletVolatilityStructure> capletVol =
                new RelinkableHandle<>(vol, true);

        FloatingRateBond bond = makeCappedFrn(capletVol);
        double npvBefore = bond.NPV();

        try {
            capletVol.reset();
            Exception empty = assertThrows(Exception.class, bond::NPV,
                    "unlinking the caplet-vol Handle must not silently keep "
                            + "the previous surface / NPV " + npvBefore);
            assertTrue(empty.getMessage() != null && !empty.getMessage().isBlank(),
                    "unlink pricing error should carry a message, got: " + empty);

            capletVol.linkTo(vol, true);
            assertEquals(npvBefore, bond.NPV(), 1.0e-10,
                    "relinking the original surface after reset must restore NPV; "
                            + "empty error was: " + empty.getMessage());
        } finally {
            quote.setValue(BASE_VOL);
            capletVol.linkTo(vol, true);
        }
    }
}
