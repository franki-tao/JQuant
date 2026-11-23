package jquant.cashflows;

import jquant.patterns.AcyclicVisitor;
import jquant.patterns.Visitor;

import static jquant.math.CommonUtil.QL_REQUIRE;
import static jquant.math.MathUtils.close_enough;

/**
 * Capped and/or floored overnight-indexed coupon.
 *
 * <p>Port of QuantLib's {@code CappedFlooredOvernightIndexedCoupon} in
 * {@code ql/cashflows/overnightindexedcoupon.cpp}. The coupon decomposes the
 * capped/floored payoff into its underlying compounded swaplet plus the
 * difference between floorlet and caplet rates.
 *
 * <p>https://github.com/lballabio/QuantLib/blob/master/ql/cashflows/overnightindexedcoupon.cpp
 */
public class CappedFlooredOvernightIndexedCoupon extends FloatingRateCoupon {
    protected OvernightIndexedCoupon underlying_;
    protected boolean nakedOption_ = false;
    protected boolean dailyCapFloor_ = false;
    protected double cap_ = Double.NaN;
    protected double floor_ = Double.NaN;
    protected double effectiveCapletVolatility_ = Double.NaN;
    protected double effectiveFloorletVolatility_ = Double.NaN;

    public CappedFlooredOvernightIndexedCoupon(
            final OvernightIndexedCoupon underlying,
            final double cap,
            final double floor) {
        this(underlying, cap, floor, false, false);
    }

    public CappedFlooredOvernightIndexedCoupon(
            final OvernightIndexedCoupon underlying,
            final double cap,
            final double floor,
            final boolean nakedOption,
            final boolean dailyCapFloor) {
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
                false);

        underlying_ = underlying;
        nakedOption_ = nakedOption;
        dailyCapFloor_ = dailyCapFloor;

        QL_REQUIRE(!underlying_.compoundSpreadDaily()
                        || close_enough(underlying_.gearing(), 1.0, 42),
                "CappedFlooredOvernightIndexedCoupon: if include spread = true, "
                        + "only a gearing 1.0 is allowed - scale the notional in "
                        + "this case instead.");

        if (!dailyCapFloor) {
            if (gearing_ > 0.0) {
                cap_ = cap;
                floor_ = floor;
            } else {
                cap_ = floor;
                floor_ = cap;
            }
        } else {
            cap_ = cap;
            floor_ = floor;
        }
        if (!Double.isNaN(cap_) && !Double.isNaN(floor_)) {
            QL_REQUIRE(cap_ >= floor_,
                    "cap level (" + cap_ + ") less than floor level (" + floor_ + ")");
        }
        registerWith(underlying_);
        if (nakedOption_)
            underlying_.alwaysForwardNotifications();
    }

    @Override
    public void alwaysForwardNotifications() {
        super.alwaysForwardNotifications();
        underlying_.alwaysForwardNotifications();
    }

    @Override
    public void deepUpdate() {
        update();
        underlying_.deepUpdate();
    }

    @Override
    public void performCalculations() {
        QL_REQUIRE(underlying_.pricer() != null,
                "underlying coupon pricer not set");
        final double swapletRate = nakedOption_ ? 0.0 : underlying_.rate();

        final FloatingRateCouponPricer pricer = pricer();
        QL_REQUIRE(pricer instanceof OvernightIndexedCouponPricer,
                "coupon pricer not an instance of OvernightIndexedCouponPricer");
        final OvernightIndexedCouponPricer cfONPricer =
                (OvernightIndexedCouponPricer) pricer;

        if (isFloored() || isCapped())
            cfONPricer.initialize(this);

        double floorletRate = 0.0;
        if (isFloored())
            floorletRate = cfONPricer.floorletRate(effectiveFloor(), dailyCapFloor_);

        double capletRate = 0.0;
        if (isCapped())
            capletRate = (nakedOption_ && Double.isNaN(floor_) ? -1.0 : 1.0)
                    * cfONPricer.capletRate(effectiveCap(), dailyCapFloor_);

        rate_ = swapletRate + floorletRate - capletRate;

        effectiveCapletVolatility_ = cfONPricer.effectiveCapletVolatility();
        effectiveFloorletVolatility_ = cfONPricer.effectiveFloorletVolatility();
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

    @Override
    public jquant.time.Date fixingDate() {
        return underlying_.fixingDate();
    }

    //! cap
    public double cap() {
        return gearing_ > 0.0 ? cap_ : floor_;
    }

    //! floor
    public double floor() {
        return gearing_ > 0.0 ? floor_ : cap_;
    }

    //! effective cap of fixing
    public double effectiveCap() {
        if (Double.isNaN(cap_))
            return Double.NaN;
        /* We have four cases dependent on dailyCapFloor_ and
           compoundSpreadDaily. Notation in the formulas:
           g         gearing,
           s         spread,
           A         coupon amount,
           f_i       daily fixings,
           \tau_i    daily accrual fractions,
           \tau      coupon accrual fraction,
           C         cap rate
           F         floor rate
        */
        if (dailyCapFloor_) {
            if (underlying_.compoundSpreadDaily()) {
                // A = g * (prod (1 + tau_i min(max(f_i + s, F), C)) - 1) / tau
                return cap_ - underlying_.spread();
            } else {
                // A = g * (prod (1 + tau_i min(max(f_i, F), C)) - 1) / tau + s
                return cap_;
            }
        } else {
            if (underlying_.compoundSpreadDaily()) {
                // A = min(max(g * (prod (1 + tau_i(f_i + s)) - 1) / tau, F), C)
                return cap_ / gearing() - underlying_.effectiveSpread();
            } else {
                // A = min(max(g * (prod (1 + tau_i f_i) - 1) / tau + s, F), C)
                return (cap_ - underlying_.effectiveSpread()) / gearing();
            }
        }
    }

    //! effective floor of fixing
    public double effectiveFloor() {
        if (Double.isNaN(floor_))
            return Double.NaN;
        if (dailyCapFloor_) {
            if (underlying_.compoundSpreadDaily()) {
                return floor_ - underlying_.spread();
            } else {
                return floor_;
            }
        } else {
            if (underlying_.compoundSpreadDaily()) {
                return floor_ - underlying_.effectiveSpread();
            } else {
                return (floor_ - underlying_.effectiveSpread()) / gearing();
            }
        }
    }

    //! effective caplet volatility
    public double effectiveCapletVolatility() {
        calculate();
        return effectiveCapletVolatility_;
    }

    //! effective floorlet volatility
    public double effectiveFloorletVolatility() {
        calculate();
        return effectiveFloorletVolatility_;
    }

    @Override
    public void accept(final AcyclicVisitor v) {
        if (v instanceof Visitor) {
            try {
                @SuppressWarnings("unchecked")
                final Visitor<CappedFlooredOvernightIndexedCoupon> vv =
                        (Visitor<CappedFlooredOvernightIndexedCoupon>) v;
                vv.visit(this);
            } catch (ClassCastException e) {
                super.accept(v);
            }
        }
    }

    public boolean isCapped() {
        return !Double.isNaN(cap_);
    }

    public boolean isFloored() {
        return !Double.isNaN(floor_);
    }

    @Override
    public void setPricer(final FloatingRateCouponPricer pricer) {
        // Pinned official implementation only forwards to
        // FloatingRateCoupon::setPricer; it does not install the pricer on
        // the underlying coupon.
        QL_REQUIRE(pricer instanceof OvernightIndexedCouponPricer,
                "The pricer is required to be an instance of "
                        + "OvernightIndexedCouponPricer");
        super.setPricer(pricer);
    }

    public OvernightIndexedCoupon underlying() {
        return underlying_;
    }
}
