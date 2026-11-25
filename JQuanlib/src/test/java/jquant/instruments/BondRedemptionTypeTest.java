package jquant.instruments;

import jquant.CashFlow;
import jquant.SavedSettings;
import jquant.Settings;
import jquant.cashflows.AmortizingPayment;
import jquant.cashflows.CashFlows;
import jquant.cashflows.FixedRateCoupon;
import jquant.cashflows.Redemption;
import jquant.cashflows.SimpleCashFlow;
import jquant.indexes.IndexManager;
import jquant.pricingengines.DiscountingBondEngine;
import jquant.termstructures.YieldTermStructure;
import jquant.termstructures.yield.FlatForward;
import jquant.time.BusinessDayConvention;
import jquant.time.Calendar;
import jquant.time.Date;
import jquant.time.Month;
import jquant.time.TimeUtils;
import jquant.time.calendars.Target;
import jquant.time.daycounters.Actual360;
import jquant.time.daycounters.Actual365Fixed;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Engineering tests, <b>not</b> official BOOST ports. They pin the redemption
 * cash-flow types produced by {@link Bond}:
 * <ul>
 *   <li>{@link AmortizingPayment} for intermediate notional changes and
 *       {@link Redemption} for the last one;</li>
 *   <li>{@link Redemption} from the numeric
 *       {@code setSingleRedemption(notional, redemption, date)} path used by
 *       {@link ZeroCouponBond};</li>
 *   <li>no rewriting of caller-supplied cash flows in the old face-plus-leg
 *       constructor;</li>
 *   <li>characterization of official ratio indexing, which is
 *       {@code redemptions[i]} and the last element when the vector is
 *       shorter. This is official semantics, not a bug to fix.</li>
 * </ul>
 *
 * <p>Amounts, dates and sort order are unchanged by the type change; the NPV
 * reference below rebuilds the same flows as {@link SimpleCashFlow}s.
 */
public class BondRedemptionTypeTest {

    private static final Calendar CALENDAR = new Target();
    private static final int SETTLEMENT_DAYS = 3;
    private static final Date TODAY = new Date(24, Month.APRIL, 2007);

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

    // ------------------------------------------------------------------
    // Multi-period fixture: initial notional 100, amortized to 60, then 0.
    // ------------------------------------------------------------------

    private static List<CashFlow> couponFixture() {
        List<CashFlow> coupons = new ArrayList<>();
        coupons.add(coupon(new Date(15, Month.JUNE, 2020), 100.0,
                new Date(15, Month.JUNE, 2019)));
        coupons.add(coupon(new Date(15, Month.JUNE, 2021), 100.0,
                new Date(15, Month.JUNE, 2020)));
        coupons.add(coupon(new Date(15, Month.JUNE, 2022), 60.0,
                new Date(15, Month.JUNE, 2021)));
        coupons.add(coupon(new Date(15, Month.JUNE, 2023), 60.0,
                new Date(15, Month.JUNE, 2022)));
        return coupons;
    }

    private static FixedRateCoupon coupon(Date paymentDate, double nominal,
                                          Date accrualStartDate) {
        return new FixedRateCoupon(paymentDate, nominal, 0.05, new Actual360(),
                accrualStartDate, paymentDate);
    }

    /** Exposes the protected redemption helper for ratio characterization. */
    private static class MultiNotionalBond extends Bond {
        MultiNotionalBond(List<CashFlow> coupons, double[] redemptionRatios) {
            super(SETTLEMENT_DAYS, CALENDAR, new Date());
            cashflows_ = new ArrayList<>(coupons);
            maturityDate_ = coupons.get(coupons.size() - 1).date().copy();
            addRedemptionsToCashflows(redemptionRatios);
        }
    }

    // ------------------------------------------------------------------
    // Types, amounts, dates, ordering, NPV
    // ------------------------------------------------------------------

    @Test
    public void multiPeriodBondUsesAmortizingPaymentAndFinalRedemption() {
        List<CashFlow> coupons = couponFixture();
        Bond bond = new MultiNotionalBond(coupons, new double[0]);

        List<CashFlow> redemptions = bond.redemptions();
        assertEquals(2, redemptions.size(),
                "one intermediate amortizing payment and one final redemption");
        CashFlow amortizing = redemptions.get(0);
        CashFlow redemption = redemptions.get(1);
        assertInstanceOf(AmortizingPayment.class, amortizing);
        assertInstanceOf(Redemption.class, redemption);

        // Default ratios: R = 100 at both steps.
        // i=1: (100/100) * (100 - 60) = 40 on 15 Jun 2021.
        // i=2: (100/100) * (60 - 0)   = 60 on 15 Jun 2023.
        assertEquals(40.0, amortizing.amount(), 1.0e-12);
        assertEquals(60.0, redemption.amount(), 1.0e-12);
        assertTrue(TimeUtils.equals(new Date(15, Month.JUNE, 2021),
                amortizing.date()), "amortizing payment date");
        assertTrue(TimeUtils.equals(new Date(15, Month.JUNE, 2023),
                redemption.date()), "final redemption date");

        // Stable sort: same-date coupons come before the generated flows.
        List<CashFlow> flows = bond.cashflows();
        int firstCouponIndex = indexOfIdentity(flows, coupons.get(1));
        int amortizingIndex = indexOfIdentity(flows, amortizing);
        int lastCouponIndex = indexOfIdentity(flows, coupons.get(3));
        int redemptionIndex = indexOfIdentity(flows, redemption);
        assertTrue(firstCouponIndex >= 0 && firstCouponIndex < amortizingIndex,
                "coupon must sort before the same-date amortizing payment");
        assertTrue(lastCouponIndex >= 0 && lastCouponIndex < redemptionIndex,
                "coupon must sort before the same-date final redemption");

        // NPV oracle: the same amounts and dates as plain SimpleCashFlows.
        YieldTermStructure curve = new FlatForward(TODAY, 0.05,
                new Actual365Fixed());
        bond.setPricingEngine(new DiscountingBondEngine(curve));
        double engineNpv = bond.settlementValue();
        double referenceNpv = simpleCashFlowNpv(coupons, redemptions, curve,
                bond.settlementDate());
        assertEquals(referenceNpv, engineNpv, 1.0e-12,
                "type change must not move the NPV");
    }

    private static double simpleCashFlowNpv(List<CashFlow> coupons,
                                            List<CashFlow> redemptions,
                                            YieldTermStructure curve,
                                            Date settlementDate) {
        List<CashFlow> reference = new ArrayList<>();
        for (CashFlow coupon : coupons)
            reference.add(new SimpleCashFlow(coupon.amount(), coupon.date()));
        for (CashFlow redemption : redemptions)
            reference.add(new SimpleCashFlow(redemption.amount(),
                    redemption.date()));
        reference.sort(Comparator.comparingInt(
                (CashFlow flow) -> flow.date().serialNumber()));
        return CashFlows.npv(reference, curve, false, settlementDate,
                settlementDate);
    }

    // ------------------------------------------------------------------
    // ZeroCouponBond numeric setSingleRedemption path
    // ------------------------------------------------------------------

    @Test
    public void zeroCouponBondRedemptionIsRedemption() {
        Bond bond = new ZeroCouponBond(SETTLEMENT_DAYS, CALENDAR, 100.0,
                new Date(15, Month.JUNE, 2010),
                BusinessDayConvention.FOLLOWING, 95.0,
                new Date(15, Month.JUNE, 2000));

        CashFlow redemption = bond.redemption();
        assertInstanceOf(Redemption.class, redemption);
        assertEquals(95.0, redemption.amount(), 1.0e-12,
                "face * redemption / 100");
        assertSame(bond.cashflows().get(bond.cashflows().size() - 1),
                redemption, "redemption flow is stored in the cash flows");
    }

    // ------------------------------------------------------------------
    // Caller-supplied types are preserved by the old face-plus-leg constructor
    // ------------------------------------------------------------------

    @Test
    public void oldFaceAndLegConstructorKeepsCallerFlowType() {
        Date issueDate = new Date(15, Month.JUNE, 2019);
        Date maturityDate = new Date(15, Month.JUNE, 2027);
        List<CashFlow> leg = new ArrayList<>();
        leg.add(coupon(new Date(15, Month.JUNE, 2020), 100.0, issueDate));
        SimpleCashFlow callerFlow = new SimpleCashFlow(100.0, maturityDate);
        leg.add(callerFlow);

        Bond bond = new Bond(SETTLEMENT_DAYS, CALENDAR, 100.0, maturityDate,
                issueDate, leg);

        assertSame(callerFlow, bond.redemption(),
                "the caller's last flow must not be replaced");
        assertEquals(SimpleCashFlow.class, bond.redemption().getClass(),
                "caller's SimpleCashFlow must not become a Redemption");
        assertSame(callerFlow,
                bond.cashflows().get(bond.cashflows().size() - 1),
                "the caller's object stays in the cash-flow list");
    }

    // ------------------------------------------------------------------
    // Official ratio indexing characterization (redemptions[i], not i-1)
    // ------------------------------------------------------------------

    @Test
    public void officialIndexingReusesLastRatioForShortVector() {
        // Official: redemptions.size() == 1, so i >= size uses back(), i.e.
        // redemptions[0] = 100 for both notional changes.
        double[] amounts = redemptionAmounts(new double[]{100.0});
        assertEquals(40.0, amounts[0], 1.0e-12);
        assertEquals(60.0, amounts[1], 1.0e-12);
    }

    @Test
    public void officialIndexingUsesRedemptionsAtI() {
        // Official redemptions[i] semantics, NOT redemptions[i-1]:
        // i=1 uses element 1 (90), i=2 uses element 2 (100); element 0 is
        // unused. The 0-based alternative would give 32 and 54. Do not "fix"
        // this index: it is the official formula.
        double[] amounts = redemptionAmounts(new double[]{80.0, 90.0, 100.0});
        assertEquals(36.0, amounts[0], 1.0e-12);
        assertEquals(60.0, amounts[1], 1.0e-12);
    }

    @Test
    public void officialIndexingFallsBackToBackForTwoElementVector() {
        // i=1 uses redemptions[1] = 90; i=2 is out of range and uses back(),
        // which is also 90. This is the official redemptions[i] rule.
        double[] amounts = redemptionAmounts(new double[]{110.0, 90.0});
        assertEquals(36.0, amounts[0], 1.0e-12);
        assertEquals(54.0, amounts[1], 1.0e-12);
    }

    private static double[] redemptionAmounts(double[] ratios) {
        Bond bond = new MultiNotionalBond(couponFixture(), ratios);
        List<CashFlow> redemptions = bond.redemptions();
        assertEquals(2, redemptions.size());
        assertInstanceOf(AmortizingPayment.class, redemptions.get(0));
        assertInstanceOf(Redemption.class, redemptions.get(1));
        assertTrue(TimeUtils.equals(new Date(15, Month.JUNE, 2021),
                redemptions.get(0).date()));
        assertTrue(TimeUtils.equals(new Date(15, Month.JUNE, 2023),
                redemptions.get(1).date()));
        return new double[]{redemptions.get(0).amount(),
                redemptions.get(1).amount()};
    }

    private static int indexOfIdentity(List<CashFlow> flows, CashFlow target) {
        for (int i = 0; i < flows.size(); i++) {
            if (flows.get(i) == target)
                return i;
        }
        return -1;
    }
}
