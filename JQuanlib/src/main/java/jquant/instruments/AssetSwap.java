package jquant.instruments;

import jquant.CashFlow;
import jquant.PricingEngine;
import jquant.cashflows.Coupon;
import jquant.cashflows.FixedRateCoupon;
import jquant.cashflows.FloatingRateCoupon;
import jquant.cashflows.IborLeg;
import jquant.cashflows.OvernightLeg;
import jquant.cashflows.SimpleCashFlow;
import jquant.indexes.IborIndex;
import jquant.indexes.OvernightIndex;
import jquant.pricingengines.DiscountingMultiLegSwapEngine;
import jquant.time.BusinessDayConvention;
import jquant.time.Date;
import jquant.time.DateGenerationRule;
import jquant.time.DayCounter;
import jquant.time.Schedule;
import jquant.time.TimeUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static jquant.math.CommonUtil.QL_FAIL;
import static jquant.math.CommonUtil.QL_REQUIRE;

//! Bullet bond vs %Libor swap
/*! For mechanics of par asset swap and market asset swap, refer to
    "Introduction to Asset Swap", Lehman Brothers European Fixed
    Income Research - January 2000, D. O'Kane.

    <p>Port of QuantLib {@code ql/instruments/assetswap.{hpp,cpp}}.

    <p><b>Warning</b>: {@code bondCleanPrice} must be the (forward) price at
    the float-schedule start date.
*/
public class AssetSwap extends Swap {
    private final Bond bond_;
    private final double bondCleanPrice_;
    private double nonParRepayment_;
    private final double spread_;
    private final boolean parSwap_;
    private final Date upfrontDate_;
    // results
    private double fairSpread_ = Double.NaN;
    private double fairCleanPrice_ = Double.NaN;
    private double fairNonParRepayment_ = Double.NaN;

    /** Official defaults: empty float schedule and day counter, par swap,
        gearing 1, null non-par repayment, null deal maturity. */
    public AssetSwap(boolean payBondCoupon,
                     Bond bond,
                     double bondCleanPrice,
                     IborIndex iborIndex,
                     double spread) {
        this(payBondCoupon, bond, bondCleanPrice, iborIndex, spread,
                new Schedule(), new DayCounter(), true, 1.0, Double.NaN, new Date());
    }

    public AssetSwap(boolean payBondCoupon,
                     Bond bond,
                     double bondCleanPrice,
                     IborIndex iborIndex,
                     double spread,
                     Schedule floatSchedule,
                     DayCounter floatingDayCounter,
                     boolean parAssetSwap) {
        this(payBondCoupon, bond, bondCleanPrice, iborIndex, spread,
                floatSchedule, floatingDayCounter, parAssetSwap,
                1.0, Double.NaN, new Date());
    }

    /*! If the passed iborIndex is an overnight rate such as SOFR, ESTR or
        SONIA, the floatSchedule argument is required and will be used to build
        overnight-indexed coupons.
    */
    public AssetSwap(boolean payBondCoupon,
                     Bond bond,
                     double bondCleanPrice,
                     IborIndex iborIndex,
                     double spread,
                     Schedule floatSchedule,
                     DayCounter floatingDayCounter,
                     boolean parAssetSwap,
                     double gearing,
                     double nonParRepayment,
                     Date dealMaturity) {
        super(2);

        bond_ = bond;
        bondCleanPrice_ = bondCleanPrice;
        nonParRepayment_ = nonParRepayment;
        spread_ = spread;
        parSwap_ = parAssetSwap;

        if (floatSchedule == null)
            floatSchedule = new Schedule();
        if (floatingDayCounter == null)
            floatingDayCounter = new DayCounter();
        if (dealMaturity == null)
            dealMaturity = new Date();

        OvernightIndex overnight = iborIndex instanceof OvernightIndex
                ? (OvernightIndex) iborIndex : null;
        if (overnight != null) {
            QL_REQUIRE(!floatSchedule.empty(),
                    "floating schedule is needed when using an overnight index");
        }

        Schedule schedule = floatSchedule.empty()
                ? new Schedule(bond_.settlementDate(),
                        bond_.maturityDate(),
                        iborIndex.tenor(),
                        iborIndex.fixingCalendar(),
                        iborIndex.businessDayConvention(),
                        iborIndex.businessDayConvention(),
                        DateGenerationRule.BACKWARD,
                        false, // endOfMonth
                        new Date(), new Date())
                : floatSchedule.copy();

        if (TimeUtils.equals(dealMaturity, new Date()))
            dealMaturity = schedule.back();
        QL_REQUIRE(TimeUtils.leq(dealMaturity, schedule.back()),
                "deal maturity " + dealMaturity
                        + " cannot be later than (adjusted) bond maturity "
                        + schedule.back());
        QL_REQUIRE(TimeUtils.greater(dealMaturity, schedule.front()),
                "deal maturity " + dealMaturity
                        + " must be later than swap start date "
                        + schedule.front());

        // the following might become an input parameter
        BusinessDayConvention paymentAdjustment = BusinessDayConvention.FOLLOWING;

        Date finalDate = schedule.calendar().adjust(dealMaturity, paymentAdjustment);
        schedule = schedule.until(finalDate);

        // bondCleanPrice must be the (forward) clean price
        // at the floating schedule start date
        upfrontDate_ = schedule.startDate().copy();
        double dirtyPrice = bondCleanPrice_ + bond_.accruedAmount(upfrontDate_);

        double notional = bond_.notional(upfrontDate_);
        /* In the market asset swap, the bond is purchased in return for
           payment of the full price. The notional of the floating leg is
           then scaled by the full price. */
        if (!parSwap_)
            notional *= dirtyPrice / 100.0;

        /******** Bond leg ********/

        List<CashFlow> bondLeg = bond_.cashflows();
        QL_REQUIRE(!bondLeg.isEmpty(), "no cashflows from bond");

        boolean includeOnUpfrontDate = false; // a cash flow on the upfront
                                              // date must be discarded

        // add coupons for the time being, not the redemption
        // The bond's cash-flow objects are shared, not cloned, as official.
        int i = 0;
        for (; i < bondLeg.size() - 1
                && TimeUtils.leq(bondLeg.get(i).date(), dealMaturity); ++i) {
            CashFlow cashflow = bondLeg.get(i);
            if (!cashflow.hasOccurred(upfrontDate_, Optional.of(includeOnUpfrontDate)))
                legs_.get(0).add(cashflow);
        }

        // if we're skipping a cashflow before the redemption
        // and it's a coupon, then add the accrued coupon.
        if (i < bondLeg.size() - 1) {
            if (bondLeg.get(i) instanceof Coupon coupon) {
                double accruedAmount = coupon.accruedAmount(dealMaturity);
                legs_.get(0).add(new SimpleCashFlow(accruedAmount, finalDate));
            }
        }

        // add the redemption, or whatever the final payment is
        if (Double.isNaN(nonParRepayment_)) {
            CashFlow redemption = bondLeg.get(bondLeg.size() - 1);
            legs_.get(0).add(new SimpleCashFlow(redemption.amount(), finalDate));
            nonParRepayment_ = 100.0;
        } else {
            legs_.get(0).add(new SimpleCashFlow(nonParRepayment_, finalDate));
        }

        /******** Floating leg ********/

        List<CashFlow> floatingLeg;
        if (overnight != null) {
            floatingLeg = new OvernightLeg(schedule, overnight)
                    .withNotionals(notional)
                    .withPaymentAdjustment(paymentAdjustment)
                    .withGearings(gearing)
                    .withSpreads(spread)
                    .withPaymentDayCounter(floatingDayCounter)
                    .build();
        } else {
            floatingLeg = new IborLeg(schedule, iborIndex)
                    .withNotionals(notional)
                    .withPaymentAdjustment(paymentAdjustment)
                    .withGearings(gearing)
                    .withSpreads(spread)
                    .withPaymentDayCounter(floatingDayCounter)
                    .leg();
        }

        if (parSwap_) {
            // upfront
            double upfront = (dirtyPrice - 100.0) / 100.0 * notional;
            floatingLeg.add(0, new SimpleCashFlow(upfront, upfrontDate_));
            // backpayment (accounts for non-par redemption, if any)
            floatingLeg.add(new SimpleCashFlow(notional, finalDate));
        } else {
            // final notional exchange
            floatingLeg.add(new SimpleCashFlow(notional, finalDate));
        }
        legs_.set(1, floatingLeg);

        /******** registration and sides ********/

        registerLegs();

        if (payBondCoupon) {
            payer_[0] = -1.0;
            payer_[1] = +1.0;
        } else {
            payer_[0] = +1.0;
            payer_[1] = -1.0;
        }
    }

    // results
    public double fairSpread() {
        final double basisPoint = 1.0e-4;
        calculate();
        if (!Double.isNaN(fairSpread_)) {
            return fairSpread_;
        } else if (legBPS_.length > 1 && !Double.isNaN(legBPS_[1])) {
            fairSpread_ = spread_ - NPV_ / legBPS_[1] * basisPoint;
            return fairSpread_;
        } else {
            QL_FAIL("fair spread not available");
            return Double.NaN;
        }
    }

    public double floatingLegBPS() {
        calculate();
        QL_REQUIRE(legBPS_.length > 1 && !Double.isNaN(legBPS_[1]),
                "floating-leg BPS not available");
        return legBPS_[1];
    }

    public double floatingLegNPV() {
        calculate();
        QL_REQUIRE(legNPV_.length > 1 && !Double.isNaN(legNPV_[1]),
                "floating-leg NPV not available");
        return legNPV_[1];
    }

    public double fairCleanPrice() {
        calculate();
        if (!Double.isNaN(fairCleanPrice_)) {
            return fairCleanPrice_;
        } else {
            QL_REQUIRE(!Double.isNaN(startDiscounts_[1]),
                    "fair clean price not available for seasoned deal");
            double notional = bond_.notional(upfrontDate_);
            if (parSwap_) {
                fairCleanPrice_ = bondCleanPrice_ - payer_[1]
                        * NPV_ * npvDateDiscount_ / startDiscounts_[1]
                        / (notional / 100.0);
            } else {
                double accruedAmount = bond_.accruedAmount(upfrontDate_);
                double dirtyPrice = bondCleanPrice_ + accruedAmount;
                double fairDirtyPrice = -legNPV_[0] / legNPV_[1] * dirtyPrice;
                fairCleanPrice_ = fairDirtyPrice - accruedAmount;
            }
            return fairCleanPrice_;
        }
    }

    public double fairNonParRepayment() {
        calculate();
        if (!Double.isNaN(fairNonParRepayment_)) {
            return fairNonParRepayment_;
        } else {
            QL_REQUIRE(!Double.isNaN(endDiscounts_[1]),
                    "fair non par repayment not available for expired leg");
            double notional = bond_.notional(upfrontDate_);
            fairNonParRepayment_ = nonParRepayment_ - payer_[0]
                    * NPV_ * npvDateDiscount_ / endDiscounts_[1]
                    / (notional / 100.0);
            return fairNonParRepayment_;
        }
    }

    // inspectors
    public boolean parSwap() {
        return parSwap_;
    }

    public double spread() {
        return spread_;
    }

    public double cleanPrice() {
        return bondCleanPrice_;
    }

    public double nonParRepayment() {
        return nonParRepayment_;
    }

    public Bond bond() {
        return bond_;
    }

    public boolean payBondCoupon() {
        return payer_[0] == -1.0;
    }

    public List<CashFlow> bondLeg() {
        return legs_.get(0);
    }

    public List<CashFlow> floatingLeg() {
        return legs_.get(1);
    }

    // other
    @Override
    public void setupArguments(PricingEngine.arguments args) {
        super.setupArguments(args);

        if (!(args instanceof AssetSwap.arguments arguments)) // it's a swap engine...
            return;

        List<CashFlow> fixedCoupons = bondLeg();

        arguments.fixedResetDates = new ArrayList<>(fixedCoupons.size());
        arguments.fixedPayDates = new ArrayList<>(fixedCoupons.size());
        arguments.fixedCoupons = new ArrayList<>(fixedCoupons.size());

        for (CashFlow cashflow : fixedCoupons) {
            FixedRateCoupon coupon = (FixedRateCoupon) cashflow;

            arguments.fixedPayDates.add(coupon.date());
            arguments.fixedResetDates.add(coupon.accrualStartDate());
            arguments.fixedCoupons.add(coupon.amount());
        }

        List<CashFlow> floatingCoupons = floatingLeg();

        arguments.floatingResetDates = new ArrayList<>(floatingCoupons.size());
        arguments.floatingPayDates = new ArrayList<>(floatingCoupons.size());
        arguments.floatingFixingDates = new ArrayList<>(floatingCoupons.size());
        arguments.floatingAccrualTimes = new ArrayList<>(floatingCoupons.size());
        arguments.floatingSpreads = new ArrayList<>(floatingCoupons.size());

        for (CashFlow cashflow : floatingCoupons) {
            FloatingRateCoupon coupon = (FloatingRateCoupon) cashflow;

            arguments.floatingResetDates.add(coupon.accrualStartDate());
            arguments.floatingPayDates.add(coupon.date());
            arguments.floatingFixingDates.add(coupon.fixingDate());
            arguments.floatingAccrualTimes.add(coupon.accrualPeriod());
            arguments.floatingSpreads.add(coupon.spread());
        }
    }

    @Override
    protected void setupExpired() {
        super.setupExpired();
        fairSpread_ = Double.NaN;
        fairCleanPrice_ = Double.NaN;
        fairNonParRepayment_ = Double.NaN;
    }

    @Override
    public void fetchResults(PricingEngine.results r) {
        super.fetchResults(r);
        if (r instanceof AssetSwap.results results) {
            fairSpread_ = results.fairSpread;
            fairCleanPrice_ = results.fairCleanPrice;
            fairNonParRepayment_ = results.fairNonParRepayment;
        } else {
            fairSpread_ = Double.NaN;
            fairCleanPrice_ = Double.NaN;
            fairNonParRepayment_ = Double.NaN;
        }
    }

    //! %Arguments for asset swap calculation
    public static class arguments extends DiscountingMultiLegSwapEngine.arguments {
        public List<Date> fixedResetDates;
        public List<Date> fixedPayDates;
        public List<Double> fixedCoupons;
        public List<Double> floatingAccrualTimes;
        public List<Date> floatingResetDates;
        public List<Date> floatingFixingDates;
        public List<Date> floatingPayDates;
        public List<Double> floatingSpreads;

        @Override
        public void validate() {
            QL_REQUIRE(fixedResetDates.size() == fixedPayDates.size(),
                    "number of fixed start dates different from "
                            + "number of fixed payment dates");
            QL_REQUIRE(fixedPayDates.size() == fixedCoupons.size(),
                    "number of fixed payment dates different from "
                            + "number of fixed coupon amounts");
            QL_REQUIRE(floatingResetDates.size() == floatingPayDates.size(),
                    "number of floating start dates different from "
                            + "number of floating payment dates");
            QL_REQUIRE(floatingFixingDates.size() == floatingPayDates.size(),
                    "number of floating fixing dates different from "
                            + "number of floating payment dates");
            QL_REQUIRE(floatingAccrualTimes.size() == floatingPayDates.size(),
                    "number of floating accrual times different from "
                            + "number of floating payment dates");
            QL_REQUIRE(floatingSpreads.size() == floatingPayDates.size(),
                    "number of floating spreads different from "
                            + "number of floating payment dates");
        }
    }

    //! %Results from simple swap calculation
    public static class results extends DiscountingMultiLegSwapEngine.results {
        public double fairSpread = Double.NaN;
        public double fairCleanPrice = Double.NaN;
        public double fairNonParRepayment = Double.NaN;

        @Override
        public void reset() {
            super.reset();
            // Official Swap::results::reset clears the leg results too.
            legNPV = null;
            legBPS = null;
            startDiscounts = null;
            endDiscounts = null;
            npvDateDiscount = Double.NaN;
            fairSpread = Double.NaN;
            fairCleanPrice = Double.NaN;
            fairNonParRepayment = Double.NaN;
        }
    }
}
