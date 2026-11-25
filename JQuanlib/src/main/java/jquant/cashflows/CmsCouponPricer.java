package jquant.cashflows;

import jquant.Handle;
import jquant.termstructures.volatility.swaption.SwaptionVolatilityStructure;

/**
 * Base pricer for CMS coupons.
 *
 * <p>Ported from QuantLib
 * {@code ql/cashflows/couponpricer.hpp} ({@code CmsCouponPricer}). It only
 * owns the swaption-volatility {@link Handle}; concrete pricers (Hagan,
 * Linear TSR, ...) implement the actual coupon-rate calculation.
 *
 * <p>The Handle is registered through {@link Handle#get()} (the observable
 * Link), not {@code currentLink()} (the current surface), so a later
 * {@code linkTo} on a {@code RelinkableHandle} reaches this pricer, while a
 * quote change on a surface that is no longer linked does not (same
 * convention as {@link IborCouponPricer} after PR101).
 */
public abstract class CmsCouponPricer extends FloatingRateCouponPricer {

    private Handle<SwaptionVolatilityStructure> swaptionVol_;

    /** Official default: an empty swaption-volatility Handle. */
    public CmsCouponPricer() {
        this(null);
    }

    public CmsCouponPricer(Handle<SwaptionVolatilityStructure> v) {
        // Official always registerWith(swaptionVol_), including an empty
        // Handle, so a later linkTo on the same RelinkableHandle reaches
        // this pricer.
        swaptionVol_ = v == null
                ? new Handle<SwaptionVolatilityStructure>(null, false)
                : v;
        registerWith(swaptionVol_.get());
    }

    public Handle<SwaptionVolatilityStructure> swaptionVolatility() {
        return swaptionVol_;
    }

    public void setSwaptionVolatility() {
        setSwaptionVolatility(null);
    }

    public void setSwaptionVolatility(Handle<SwaptionVolatilityStructure> v) {
        // Unregister the Link, not the old surface, so a later quote change
        // on the unbound surface does not leak through.
        unregisterWith(swaptionVol_.get());
        swaptionVol_ = v == null
                ? new Handle<SwaptionVolatilityStructure>(null, false)
                : v;
        registerWith(swaptionVol_.get());
        update();
    }
}
