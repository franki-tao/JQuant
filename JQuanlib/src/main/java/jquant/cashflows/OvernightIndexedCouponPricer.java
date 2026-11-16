package jquant.cashflows;

import jquant.time.Date;

import java.util.List;

import static jquant.math.CommonUtil.QL_FAIL;

/**
 * Base pricer for overnight-indexed floating coupons.
 *
 * <p>Port of QuantLib's {@code OvernightIndexedCouponPricer} in
 * {@code ql/cashflows/overnightindexedcouponpricer.cpp}.
 *
 * <p>https://github.com/lballabio/QuantLib/blob/master/ql/cashflows/overnightindexedcouponpricer.cpp
 */
public abstract class OvernightIndexedCouponPricer extends FloatingRateCouponPricer {
    protected OvernightIndexedCoupon coupon_;

    @Override
    public void initialize(final FloatingRateCoupon coupon) {
        if (!(coupon instanceof OvernightIndexedCoupon))
            QL_FAIL("OvernightIndexedCouponPricer: unsupported coupon type");
        coupon_ = (OvernightIndexedCoupon) coupon;
    }

    /** Number of interest periods which have started before {@code date}.
     *
     * <p>Equivalent to QuantLib's {@code std::lower_bound} over the interest
     * dates with the last one excluded.
     */
    protected static int determineNumberOfFixings(final List<Date> interestDates,
                                                  final Date date) {
        int lo = 0, hi = interestDates.size() - 1;
        while (lo < hi) {
            int mid = (lo + hi) / 2;
            if (jquant.time.TimeUtils.less(interestDates.get(mid), date))
                lo = mid + 1;
            else
                hi = mid;
        }
        return lo;
    }

    public abstract double averageRate(final Date date);

    //! \name FloatingRateCoupon interface
    //@{
    @Override
    public double swapletPrice() {
        QL_FAIL("swapletPrice not available");
        return 0.0;
    }

    @Override
    public double capletPrice(double effectiveCap) {
        QL_FAIL("capletPrice not available");
        return 0.0;
    }

    @Override
    public double capletRate(double effectiveCap) {
        QL_FAIL("capletRate not available");
        return 0.0;
    }

    @Override
    public double floorletPrice(double effectiveFloor) {
        QL_FAIL("floorletPrice not available");
        return 0.0;
    }

    @Override
    public double floorletRate(double effectiveFloor) {
        QL_FAIL("floorletRate not available");
        return 0.0;
    }
    //@}
}
