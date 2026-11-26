package jquant.cashflows;

import jquant.Handle;
import jquant.Quote;

/**
 * Base pricer for vanilla CMS spread coupons.
 *
 * <p>Ported from QuantLib
 * {@code ql/experimental/coupons/cmsspreadcoupon.hpp}
 * ({@code CmsSpreadCouponPricer}). It only owns the correlation
 * {@link Handle}; concrete pricers (Lognormal, ...) implement the actual
 * coupon-rate calculation.
 *
 * <p>The Handle is registered through {@link Handle#get()} (the observable
 * Link), not {@code currentLink()}, so a later {@code linkTo} on a
 * {@code RelinkableHandle} reaches this pricer.
 */
public abstract class CmsSpreadCouponPricer extends FloatingRateCouponPricer {

    private Handle<Quote> correlation_;

    /** Official default: an empty correlation Handle. */
    public CmsSpreadCouponPricer() {
        this(null);
    }

    public CmsSpreadCouponPricer(Handle<Quote> correlation) {
        correlation_ = correlation == null
                ? new Handle<Quote>(null, false)
                : correlation;
        registerWith(correlation_.get());
    }

    public Handle<Quote> correlation() {
        return correlation_;
    }

    public void setCorrelation() {
        setCorrelation(null);
    }

    public void setCorrelation(Handle<Quote> correlation) {
        // Unregister the Link, not the old quote.
        unregisterWith(correlation_.get());
        correlation_ = correlation == null
                ? new Handle<Quote>(null, false)
                : correlation;
        registerWith(correlation_.get());
        update();
    }
}
