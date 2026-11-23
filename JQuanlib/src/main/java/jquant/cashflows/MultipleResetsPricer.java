package jquant.cashflows;

import jquant.indexes.IborIndex;
import jquant.indexes.InterestRateIndex;
import jquant.time.Date;

import java.util.ArrayList;
import java.util.List;

import static jquant.math.CommonUtil.QL_FAIL;
import static jquant.math.CommonUtil.QL_REQUIRE;

/*! Base pricer for multiple-reset coupons.

    Port of QuantLib's {@code MultipleResetsPricer} in
    {@code ql/cashflows/multipleresetscoupon.hpp} and
    {@code ql/cashflows/multipleresetscoupon.cpp}.
*/
public abstract class MultipleResetsPricer extends FloatingRateCouponPricer {
    protected MultipleResetsCoupon coupon_;
    protected List<Double> subPeriodFixings_ = new ArrayList<>();

    @Override
    public double swapletPrice() {
        QL_FAIL("MultipleResetsPricer::swapletPrice not implemented");
        return 0.0;
    }

    @Override
    public double capletPrice(double effectiveCap) {
        QL_FAIL("MultipleResetsPricer::capletPrice not implemented");
        return 0.0;
    }

    @Override
    public double capletRate(double effectiveCap) {
        QL_FAIL("MultipleResetsPricer::capletRate not implemented");
        return 0.0;
    }

    @Override
    public double floorletPrice(double effectiveFloor) {
        QL_FAIL("MultipleResetsPricer::floorletPrice not implemented");
        return 0.0;
    }

    @Override
    public double floorletRate(double effectiveFloor) {
        QL_FAIL("MultipleResetsPricer::floorletRate not implemented");
        return 0.0;
    }

    @Override
    public void initialize(final FloatingRateCoupon coupon) {
        QL_REQUIRE(coupon instanceof MultipleResetsCoupon, "sub-periods coupon required");
        coupon_ = (MultipleResetsCoupon) coupon;

        InterestRateIndex index = coupon_.index();
        if (!(index instanceof IborIndex))
            // coupon was right, index is not
            QL_FAIL("IborIndex required");

        QL_REQUIRE(coupon_.accrualPeriod() != 0.0, "null accrual period");

        List<Date> fixingDates = coupon_.fixingDates();
        int n = fixingDates.size();
        subPeriodFixings_ = new ArrayList<>(n);

        for (int i = 0; i < n; i++)
            subPeriodFixings_.add(((IborIndex) index).fixing(fixingDates.get(i), false)
                    + coupon_.rateSpread());
    }
}
