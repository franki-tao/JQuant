package jquant.cashflows;


import jquant.patterns.AcyclicVisitor;
import jquant.patterns.Visitor;

import static jquant.math.CommonUtil.QL_FAIL;
import static jquant.math.CommonUtil.QL_REQUIRE;

//! Capped and/or floored floating-rate coupon
/*! The payoff \f$ P \f$ of a capped floating-rate coupon is:
    \f[ P = N \times T \times \min(a L + b, C). \f]
    The payoff of a floored floating-rate coupon is:
    \f[ P = N \times T \times \max(a L + b, F). \f]
    The payoff of a collared floating-rate coupon is:
    \f[ P = N \times T \times \min(\max(a L + b, F), C). \f]

    where \f$ N \f$ is the notional, \f$ T \f$ is the accrual
    time, \f$ L \f$ is the floating rate, \f$ a \f$ is its
    gearing, \f$ b \f$ is the spread, and \f$ C \f$ and \f$ F \f$
    the strikes.

    They can be decomposed in the following manner.
    Decomposition of a capped floating rate coupon:
    \f[
    R = \min(a L + b, C) = (a L + b) + \min(C - b - \xi |a| L, 0)
    \f]
    where \f$ \xi = sgn(a) \f$. Then:
    \f[
    R = (a L + b) + |a| \min(\frac{C - b}{|a|} - \xi L, 0)
    \f]
*/
public abstract class CappedFlooredCoupon extends FloatingRateCoupon {
    protected FloatingRateCoupon underlying_;
    protected boolean isCapped_ = false, isFloored_ = false;
    protected double cap_, floor_;

    public CappedFlooredCoupon(final FloatingRateCoupon underlying,
                               double cap,
                               double floor) {
        super(underlying.date(),
                underlying.nominal(),
                underlying.accrualStartDate(),
                underlying.accrualEndDate(),
                underlying.fixingDays(),
                underlying.index(),
                underlying.gearing(),
                underlying.spread(),
                underlying.referencePeriodStart(),
                underlying.referencePeriodEnd(),
                underlying.dayCounter(),
                underlying.isInArrears(),
                underlying.exCouponDate());
        underlying_ = underlying;
        if (gearing_ > 0) {
            if (!Double.isNaN(cap)) {
                isCapped_ = true;
                cap_ = cap;
            }
            if (!Double.isNaN(floor)) {
                floor_ = floor;
                isFloored_ = true;
            }
        } else {
            if (!Double.isNaN(cap)) {
                floor_ = cap;
                isFloored_ = true;
            }
            if (!Double.isNaN(floor)) {
                isCapped_ = true;
                cap_ = floor;
            }
        }

        if (isCapped_ && isFloored_) {
            QL_REQUIRE(cap >= floor,
                    "cap level (" + cap +
                            ") less than floor level (" + floor + ")");
        }
        registerWith(underlying_);
    }

    public void deepUpdate() {
        update();
        underlying_.update();
    }

    @Override
    public void performCalculations() {
        QL_REQUIRE(underlying_.pricer() != null, "pricer not set");
        double swapletRate = underlying_.rate();
        double floorletRate = 0.;
        if (isFloored_)
            floorletRate = underlying_.pricer().floorletRate(effectiveFloor());
        double capletRate = 0.;
        if (isCapped_)
            capletRate = underlying_.pricer().capletRate(effectiveCap());
        rate_ = swapletRate + floorletRate - capletRate;
    }

    @Override
    public double rate() {
        calculate();
        return rate_;
    }

    @Override
    public double convexityAdjustment() {
        return underlying_.convexityAdjustment();
    }

    public double cap() {
        if ((gearing_ > 0) && isCapped_)
            return cap_;
        if ((gearing_ < 0) && isFloored_)
            return floor_;
        return Double.NaN;
    }

    public double floor() {
        if ((gearing_ > 0) && isFloored_)
            return floor_;
        if ((gearing_ < 0) && isCapped_)
            return cap_;
        return Double.NaN;
    }

    //! effective cap of fixing
    public double effectiveCap() {
        if (isCapped_)
            return (cap_ - spread()) / gearing();
        else
            return Double.NaN;
    }

    //! effective floor of fixing
    public double effectiveFloor() {
        if (isFloored_)
            return (floor_ - spread()) / gearing();
        else
            return Double.NaN;
    }

    @Override
    public void accept(AcyclicVisitor v) {
        if (v instanceof Visitor) {
            try {
                // 尝试强制转型
                @SuppressWarnings("unchecked")
                Visitor<CappedFlooredCoupon> vv = (Visitor<CappedFlooredCoupon>) v;
                vv.visit(this);
            } catch (ClassCastException e) {
                // 如果类型不匹配，说明该访问者不关心这个特定的子类
                QL_FAIL("not a Black-volatility term structure visitor");
            }
        }
    }

    public boolean isCapped() {
        return isCapped_;
    }

    public boolean isFloored() {
        return isFloored_;
    }

    @Override
    public void setPricer(
            final FloatingRateCouponPricer pricer) {
        super.setPricer(pricer);
        underlying_.setPricer(pricer);
    }

    public FloatingRateCoupon underlying() {
        return underlying_;
    }
}
