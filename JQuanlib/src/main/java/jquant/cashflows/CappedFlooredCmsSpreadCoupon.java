package jquant.cashflows;

import jquant.indexes.SwapSpreadIndex;
import jquant.patterns.AcyclicVisitor;
import jquant.patterns.Visitor;
import jquant.time.BusinessDayConvention;
import jquant.time.Date;
import jquant.time.DayCounter;

/**
 * Capped and/or floored CMS spread coupon.
 *
 * <p>Ported from QuantLib
 * {@code ql/experimental/coupons/cmsspreadcoupon.hpp}. The underlying coupon
 * is a {@link CmsSpreadCoupon}; cap and floor are {@code Double.NaN} when not
 * supplied, matching official {@code Null<Rate>()} via the local
 * {@link CappedFlooredCoupon} convention.
 */
public class CappedFlooredCmsSpreadCoupon extends CappedFlooredCoupon {

    public CappedFlooredCmsSpreadCoupon(
            final Date paymentDate,
            double nominal,
            final Date startDate,
            final Date endDate,
            int fixingDays,
            final SwapSpreadIndex index,
            double gearing,
            double spread,
            double cap,
            double floor,
            final Date refPeriodStart,
            final Date refPeriodEnd,
            final DayCounter dayCounter,
            boolean isInArrears,
            final Date exCouponDate,
            BusinessDayConvention fixingConvention) {
        super(new CmsSpreadCoupon(paymentDate, nominal, startDate, endDate,
                fixingDays, index, gearing, spread, refPeriodStart,
                refPeriodEnd, dayCounter, isInArrears, exCouponDate,
                fixingConvention), cap, floor);
    }

    /** Official defaults: not in arrears, no ex-coupon date, Preceding. */
    public CappedFlooredCmsSpreadCoupon(
            final Date paymentDate,
            double nominal,
            final Date startDate,
            final Date endDate,
            int fixingDays,
            final SwapSpreadIndex index,
            double gearing,
            double spread,
            double cap,
            double floor,
            final Date refPeriodStart,
            final Date refPeriodEnd,
            final DayCounter dayCounter) {
        this(paymentDate, nominal, startDate, endDate, fixingDays, index,
                gearing, spread, cap, floor, refPeriodStart, refPeriodEnd,
                dayCounter, false, new Date());
    }

    /** Official defaults for everything after the day counter. */
    public CappedFlooredCmsSpreadCoupon(
            final Date paymentDate,
            double nominal,
            final Date startDate,
            final Date endDate,
            int fixingDays,
            final SwapSpreadIndex index,
            double gearing,
            double spread,
            double cap,
            double floor,
            final Date refPeriodStart,
            final Date refPeriodEnd,
            final DayCounter dayCounter,
            boolean isInArrears) {
        this(paymentDate, nominal, startDate, endDate, fixingDays, index,
                gearing, spread, cap, floor, refPeriodStart, refPeriodEnd,
                dayCounter, isInArrears, new Date());
    }

    /** Official default fixing convention: Preceding. */
    public CappedFlooredCmsSpreadCoupon(
            final Date paymentDate,
            double nominal,
            final Date startDate,
            final Date endDate,
            int fixingDays,
            final SwapSpreadIndex index,
            double gearing,
            double spread,
            double cap,
            double floor,
            final Date refPeriodStart,
            final Date refPeriodEnd,
            final DayCounter dayCounter,
            boolean isInArrears,
            final Date exCouponDate) {
        this(paymentDate, nominal, startDate, endDate, fixingDays, index,
                gearing, spread, cap, floor, refPeriodStart, refPeriodEnd,
                dayCounter, isInArrears, exCouponDate,
                BusinessDayConvention.PRECEDING);
    }

    @Override
    public void accept(AcyclicVisitor v) {
        if (v instanceof Visitor) {
            try {
                @SuppressWarnings("unchecked")
                Visitor<CappedFlooredCmsSpreadCoupon> vv =
                        (Visitor<CappedFlooredCmsSpreadCoupon>) v;
                vv.visit(this);
            } catch (ClassCastException e) {
                // The visitor is interested in another coupon type.
                super.accept(v);
            }
        }
    }
}
