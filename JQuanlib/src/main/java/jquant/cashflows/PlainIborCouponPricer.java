package jquant.cashflows;

import static jquant.math.CommonUtil.QL_FAIL;

//! gearing * index fixing + spread, with no convexity adjustment
public class PlainIborCouponPricer extends IborCouponPricer {
    public PlainIborCouponPricer() {
        super(true);
    }

    @Override
    public double swapletPrice() {
        QL_FAIL("not available");
        return 0.0;
    }

    @Override
    public double swapletRate() {
        return gearing_ * coupon_.indexFixing() + spread_;
    }

    @Override
    public double capletPrice(double effectiveCap) {
        QL_FAIL("not available");
        return 0.0;
    }

    @Override
    public double capletRate(double effectiveCap) {
        QL_FAIL("not available");
        return 0.0;
    }

    @Override
    public double floorletPrice(double effectiveFloor) {
        QL_FAIL("not available");
        return 0.0;
    }

    @Override
    public double floorletRate(double effectiveFloor) {
        QL_FAIL("not available");
        return 0.0;
    }
}
