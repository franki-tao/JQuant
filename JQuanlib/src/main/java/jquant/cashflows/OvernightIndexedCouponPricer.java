package jquant.cashflows;

import jquant.Handle;
import jquant.termstructures.volatility.optionlet.OptionletVolatilityStructure;
import jquant.time.Date;

import java.util.List;

import static jquant.math.CommonUtil.QL_FAIL;
import static jquant.math.CommonUtil.QL_REQUIRE;

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
    protected Handle<OptionletVolatilityStructure> capletVol_;
    protected boolean effectiveVolatilityInput_;
    protected double effectiveCapletVolatility_ = Double.NaN;
    protected double effectiveFloorletVolatility_ = Double.NaN;

    protected OvernightIndexedCouponPricer() {
        this(null, false);
    }

    protected OvernightIndexedCouponPricer(
            final Handle<OptionletVolatilityStructure> v) {
        this(v, false);
    }

    protected OvernightIndexedCouponPricer(
            final Handle<OptionletVolatilityStructure> v,
            final boolean effectiveVolatilityInput) {
        capletVol_ = v == null ? new Handle<>(null, false) : v;
        effectiveVolatilityInput_ = effectiveVolatilityInput;
        registerWith(capletVol_.get());
    }

    @Override
    public void initialize(final FloatingRateCoupon coupon) {
        if (coupon instanceof CappedFlooredOvernightIndexedCoupon) {
            // Official OvernightIndexedCouponPricer::initialize unwraps the
            // capped/floored coupon and stores its underlying coupon.
            final OvernightIndexedCoupon underlying =
                    ((CappedFlooredOvernightIndexedCoupon) coupon).underlying();
            QL_REQUIRE(underlying != null,
                    "OvernightIndexedCouponPricer: CappedFlooredOvernightIndexedCoupon "
                            + "underlying coupon not defined");
            coupon_ = underlying;
        } else if (coupon instanceof OvernightIndexedCoupon) {
            coupon_ = (OvernightIndexedCoupon) coupon;
        } else {
            QL_FAIL("OvernightIndexedCouponPricer: unsupported coupon type");
        }
    }

    public void setCapletVolatility(
            final Handle<OptionletVolatilityStructure> v) {
        unregisterWith(capletVol_.get());
        capletVol_ = v == null ? new Handle<>(null, false) : v;
        registerWith(capletVol_.get());
        update();
    }

    public Handle<OptionletVolatilityStructure> capletVolatility() {
        return capletVol_;
    }

    public void setEffectiveVolatilityInput(
            final boolean effectiveVolatilityInput) {
        effectiveVolatilityInput_ = effectiveVolatilityInput;
    }

    public boolean effectiveVolatilityInput() {
        return effectiveVolatilityInput_;
    }

    public double effectiveCapletVolatility() {
        return effectiveCapletVolatility_;
    }

    public double effectiveFloorletVolatility() {
        return effectiveFloorletVolatility_;
    }

    public double capletRate(final double effectiveCap,
                             final boolean dailyCapFloor) {
        QL_FAIL("capletRate(Rate, bool) not available");
        return 0.0;
    }

    public double floorletRate(final double effectiveFloor,
                               final boolean dailyCapFloor) {
        QL_FAIL("floorletRate(Rate, bool) not available");
        return 0.0;
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
