package jquant.pricingengines.swap;

import jquant.CashFlow;
import jquant.DiscretizedAsset;
import jquant.DiscretizedDiscountBond;
import jquant.Settings;
import jquant.cashflows.Coupon;
import jquant.cashflows.FloatingRateCoupon;
import jquant.instruments.Swap;
import jquant.instruments.Swaption;
import jquant.math.Array;
import jquant.time.Date;
import jquant.time.DayCounter;

import java.util.ArrayList;
import java.util.List;

import static jquant.math.CommonUtil.QL_REQUIRE;

/**
 * Discretized fixed-vs-floating swap.
 *
 * <p>Ported from QuantLib
 * {@code ql/pricingengines/swap/discretizedswap.{hpp,cpp}}. The official
 * class consumes {@code VanillaSwap::arguments}; JQuant's swap arguments are
 * a different shape ({@code DiscountingSwapEngine.arguments}), so this port
 * reads the coupon data from the {@link Swaption.arguments#swap} that the
 * arguments already carry. The resulting dates, amounts, accrual times and
 * spreads are the same as the official argument arrays.
 */
public class DiscretizedSwap extends DiscretizedAsset {

    private final Swap.Type type_;
    private final double nominal_;

    private final List<Double> fixedResetTimes_ = new ArrayList<>();
    private final List<Double> fixedPayTimes_ = new ArrayList<>();
    private final List<Boolean> fixedResetTimeIsInPast_ = new ArrayList<>();
    private final List<Double> fixedCoupons_ = new ArrayList<>();

    private final List<Double> floatingResetTimes_ = new ArrayList<>();
    private final List<Double> floatingPayTimes_ = new ArrayList<>();
    private final List<Boolean> floatingResetTimeIsInPast_ = new ArrayList<>();
    private final List<Double> floatingCoupons_ = new ArrayList<>();
    private final List<Double> floatingAccrualTimes_ = new ArrayList<>();
    private final List<Double> floatingSpreads_ = new ArrayList<>();

    private final List<CouponAdjustment> fixedCouponAdjustments_;
    private final List<CouponAdjustment> floatingCouponAdjustments_;

    public DiscretizedSwap(Swaption.arguments args,
                           Date referenceDate,
                           DayCounter dayCounter) {
        this(args, referenceDate, dayCounter,
                new ArrayList<>(java.util.Collections.nCopies(
                        args.swap.fixedLeg().size(), CouponAdjustment.pre)),
                new ArrayList<>(java.util.Collections.nCopies(
                        args.swap.floatingLeg().size(), CouponAdjustment.pre)));
    }

    public DiscretizedSwap(Swaption.arguments args,
                           Date referenceDate,
                           DayCounter dayCounter,
                           List<CouponAdjustment> fixedCouponAdjustments,
                           List<CouponAdjustment> floatingCouponAdjustments) {
        type_ = args.swap.swapType();
        nominal_ = args.nominal;
        fixedCouponAdjustments_ = new ArrayList<>(fixedCouponAdjustments);
        floatingCouponAdjustments_ = new ArrayList<>(floatingCouponAdjustments);

        final List<CashFlow> fixedLeg = args.swap.fixedLeg();
        final List<CashFlow> floatingLeg = args.swap.floatingLeg();

        QL_REQUIRE(fixedCouponAdjustments_.size() == fixedLeg.size(),
                "The fixed coupon adjustments must have the same size as the number of fixed coupons.");
        QL_REQUIRE(floatingCouponAdjustments_.size() == floatingLeg.size(),
                "The floating coupon adjustments must have the same size as the number of "
                        + "floating coupons.");

        final boolean includeTodaysCashFlows =
                Settings.instance.includeTodaysCashFlows().isPresent()
                        && Settings.instance.includeTodaysCashFlows().get();

        for (int i = 0; i < fixedLeg.size(); ++i) {
            final Coupon coupon = (Coupon) fixedLeg.get(i);
            final double resetTime = dayCounter.yearFraction(
                    referenceDate, coupon.accrualStartDate());
            final double payTime = dayCounter.yearFraction(
                    referenceDate, coupon.date());
            final boolean resetIsInPast =
                    isResetTimeInPast(resetTime, payTime, includeTodaysCashFlows);

            fixedResetTimes_.add(resetTime);
            fixedPayTimes_.add(payTime);
            fixedResetTimeIsInPast_.add(resetIsInPast);
            fixedCoupons_.add(coupon.amount());
            if (resetIsInPast)
                fixedCouponAdjustments_.set(i, CouponAdjustment.post);
        }

        for (int i = 0; i < floatingLeg.size(); ++i) {
            final CashFlow cashFlow = floatingLeg.get(i);
            final Coupon coupon = (Coupon) cashFlow;
            final double resetTime = dayCounter.yearFraction(
                    referenceDate, coupon.accrualStartDate());
            final double payTime = dayCounter.yearFraction(
                    referenceDate, coupon.date());
            final boolean resetIsInPast =
                    isResetTimeInPast(resetTime, payTime, includeTodaysCashFlows);

            floatingResetTimes_.add(resetTime);
            floatingPayTimes_.add(payTime);
            floatingResetTimeIsInPast_.add(resetIsInPast);
            floatingAccrualTimes_.add(coupon.accrualPeriod());
            floatingSpreads_.add(((FloatingRateCoupon) cashFlow).spread());
            if (resetIsInPast)
                floatingCouponAdjustments_.set(i, CouponAdjustment.post);

            try {
                floatingCoupons_.add(coupon.amount());
            } catch (RuntimeException e) {
                floatingCoupons_.add(Double.NaN);
            }
        }
    }

    private static boolean isResetTimeInPast(double resetTime,
                                              double payTime,
                                              boolean includeTodaysCashFlows) {
        return resetTime < 0.0
                && (payTime > 0.0 || (includeTodaysCashFlows && payTime == 0.0));
    }

    @Override
    public void reset(int size) {
        values_ = new Array(size, 0.0);
        adjustValues();
    }

    @Override
    public List<Double> mandatoryTimes() {
        final List<Double> times = new ArrayList<>();
        for (double t : fixedResetTimes_) if (t >= 0.0) times.add(t);
        for (double t : fixedPayTimes_) if (t >= 0.0) times.add(t);
        for (double t : floatingResetTimes_) if (t >= 0.0) times.add(t);
        for (double t : floatingPayTimes_) if (t >= 0.0) times.add(t);
        return times;
    }

    @Override
    protected void preAdjustValuesImpl() {
        // floating payments
        for (int i = 0; i < floatingResetTimes_.size(); i++) {
            final double t = floatingResetTimes_.get(i);
            if (floatingCouponAdjustments_.get(i) == CouponAdjustment.pre
                    && t >= 0.0 && isOnTime(t)) {
                addFloatingCoupon(i);
            }
        }
        // fixed payments
        for (int i = 0; i < fixedResetTimes_.size(); i++) {
            final double t = fixedResetTimes_.get(i);
            if (fixedCouponAdjustments_.get(i) == CouponAdjustment.pre
                    && t >= 0.0 && isOnTime(t)) {
                addFixedCoupon(i);
            }
        }
    }

    @Override
    protected void postAdjustValuesImpl() {
        // floating payments
        for (int i = 0; i < floatingResetTimes_.size(); i++) {
            final double t = floatingResetTimes_.get(i);
            if (floatingCouponAdjustments_.get(i) == CouponAdjustment.post
                    && t >= 0.0 && isOnTime(t)) {
                addFloatingCoupon(i);
            }
        }
        // fixed payments
        for (int i = 0; i < fixedResetTimes_.size(); i++) {
            final double t = fixedResetTimes_.get(i);
            if (fixedCouponAdjustments_.get(i) == CouponAdjustment.post
                    && t >= 0.0 && isOnTime(t)) {
                addFixedCoupon(i);
            }
        }

        // fixed coupons whose reset time is in the past won't be managed
        // in preAdjustValues()
        for (int i = 0; i < fixedPayTimes_.size(); i++) {
            final double t = fixedPayTimes_.get(i);
            if (fixedResetTimeIsInPast_.get(i) && isOnTime(t)) {
                final double fixedCoupon = fixedCoupons_.get(i);
                for (int j = 0; j < values_.size(); j++) {
                    if (type_ == Swap.Type.Payer)
                        values_.set(j, values_.get(j) - fixedCoupon);
                    else
                        values_.set(j, values_.get(j) + fixedCoupon);
                }
            }
        }

        // the same applies to floating payments whose rate is already fixed
        for (int i = 0; i < floatingPayTimes_.size(); i++) {
            final double t = floatingPayTimes_.get(i);
            if (floatingResetTimeIsInPast_.get(i) && isOnTime(t)) {
                final double currentFloatingCoupon = floatingCoupons_.get(i);
                QL_REQUIRE(!Double.isNaN(currentFloatingCoupon),
                        "current floating coupon not given");
                for (int j = 0; j < values_.size(); j++) {
                    if (type_ == Swap.Type.Payer)
                        values_.set(j, values_.get(j) + currentFloatingCoupon);
                    else
                        values_.set(j, values_.get(j) - currentFloatingCoupon);
                }
            }
        }
    }

    private void addFixedCoupon(int i) {
        final DiscretizedDiscountBond bond = new DiscretizedDiscountBond() {
            @Override
            protected void preAdjustValuesImpl() {
            }

            @Override
            protected void postAdjustValuesImpl() {
            }
        };
        bond.initialize(method(), fixedPayTimes_.get(i));
        bond.rollback(time_);

        final double fixedCoupon = fixedCoupons_.get(i);
        for (int j = 0; j < values_.size(); j++) {
            final double coupon = fixedCoupon * bond.values().get(j);
            if (type_ == Swap.Type.Payer)
                values_.set(j, values_.get(j) - coupon);
            else
                values_.set(j, values_.get(j) + coupon);
        }
    }

    private void addFloatingCoupon(int i) {
        final DiscretizedDiscountBond bond = new DiscretizedDiscountBond() {
            @Override
            protected void preAdjustValuesImpl() {
            }

            @Override
            protected void postAdjustValuesImpl() {
            }
        };
        bond.initialize(method(), floatingPayTimes_.get(i));
        bond.rollback(time_);

        QL_REQUIRE(!Double.isNaN(nominal_),
                "non-constant nominals are not supported yet");

        final double nominal = nominal_;
        final double T = floatingAccrualTimes_.get(i);
        final double spread = floatingSpreads_.get(i);
        final double accruedSpread = nominal * T * spread;
        for (int j = 0; j < values_.size(); j++) {
            final double coupon = nominal * (1.0 - bond.values().get(j))
                    + accruedSpread * bond.values().get(j);
            if (type_ == Swap.Type.Payer)
                values_.set(j, values_.get(j) + coupon);
            else
                values_.set(j, values_.get(j) - coupon);
        }
    }
}
