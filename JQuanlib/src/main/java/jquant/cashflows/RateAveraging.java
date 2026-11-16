package jquant.cashflows;

/**
 * Rate-averaging method for multi-fixing coupons.
 *
 * <p>Port of QuantLib's {@code RateAveraging} in
 * {@code ql/cashflows/rateaveraging.hpp}.
 *
 * <p>https://github.com/lballabio/QuantLib/blob/master/ql/cashflows/rateaveraging.hpp
 */
public enum RateAveraging {
    /**
     * The amount of interest is calculated by applying the sub-rate to the
     * principal; the payment due at the end of the period is the sum of those
     * amounts.
     */
    Simple,

    /**
     * The additional amount of interest owed each period is calculated by
     * applying the rate both to the principal and the accumulated unpaid
     * interest.
     */
    Compound
}
