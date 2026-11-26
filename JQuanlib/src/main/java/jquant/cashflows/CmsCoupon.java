package jquant.cashflows;

import jquant.indexes.SwapIndex;
import jquant.patterns.AcyclicVisitor;
import jquant.patterns.Visitor;
import jquant.time.BusinessDayConvention;
import jquant.time.Date;
import jquant.time.DayCounter;

/**
 * CMS coupon.
 *
 * <p>Ported from QuantLib {@code ql/cashflows/cmscoupon.{hpp,cpp}}. The
 * coupon pays the swap rate of a {@link SwapIndex} plus a spread, scaled by
 * the gearing.
 *
 * <p>As official, no date adjustment is performed here: the payment, start
 * and end dates passed to the constructor should already be business days.
 * The parent {@link FloatingRateCoupon} copies the dates it stores.
 */
public class CmsCoupon extends FloatingRateCoupon {

    private final SwapIndex swapIndex_;

    public CmsCoupon(final Date paymentDate,
                     double nominal,
                     final Date startDate,
                     final Date endDate,
                     int fixingDays,
                     final SwapIndex index,
                     double gearing,
                     double spread,
                     final Date refPeriodStart,
                     final Date refPeriodEnd,
                     final DayCounter dayCounter,
                     boolean isInArrears,
                     final Date exCouponDate,
                     BusinessDayConvention fixingConvention) {
        super(paymentDate, nominal, startDate, endDate,
                fixingDays, index, gearing, spread,
                refPeriodStart, refPeriodEnd,
                dayCounter, isInArrears, exCouponDate, fixingConvention);
        swapIndex_ = index;
    }

    /** Official defaults: not in arrears, no ex-coupon date, Preceding fixing. */
    public CmsCoupon(final Date paymentDate,
                     double nominal,
                     final Date startDate,
                     final Date endDate,
                     int fixingDays,
                     final SwapIndex index,
                     double gearing,
                     double spread,
                     final Date refPeriodStart,
                     final Date refPeriodEnd,
                     final DayCounter dayCounter) {
        this(paymentDate, nominal, startDate, endDate, fixingDays, index,
                gearing, spread, refPeriodStart, refPeriodEnd, dayCounter,
                false, new Date(), BusinessDayConvention.PRECEDING);
    }

    /** Official default fixing convention: Preceding. */
    public CmsCoupon(final Date paymentDate,
                     double nominal,
                     final Date startDate,
                     final Date endDate,
                     int fixingDays,
                     final SwapIndex index,
                     double gearing,
                     double spread,
                     final Date refPeriodStart,
                     final Date refPeriodEnd,
                     final DayCounter dayCounter,
                     boolean isInArrears) {
        this(paymentDate, nominal, startDate, endDate, fixingDays, index,
                gearing, spread, refPeriodStart, refPeriodEnd, dayCounter,
                isInArrears, new Date(), BusinessDayConvention.PRECEDING);
    }

    /** Official defaults for everything after the spread. */
    public CmsCoupon(final Date paymentDate,
                     double nominal,
                     final Date startDate,
                     final Date endDate,
                     int fixingDays,
                     final SwapIndex index,
                     double gearing,
                     double spread) {
        this(paymentDate, nominal, startDate, endDate, fixingDays, index,
                gearing, spread, new Date(), new Date(), new DayCounter());
    }

    /** Official default fixing convention: Preceding. */
    public CmsCoupon(final Date paymentDate,
                     double nominal,
                     final Date startDate,
                     final Date endDate,
                     int fixingDays,
                     final SwapIndex index,
                     double gearing,
                     double spread,
                     final Date refPeriodStart,
                     final Date refPeriodEnd,
                     final DayCounter dayCounter,
                     boolean isInArrears,
                     final Date exCouponDate) {
        this(paymentDate, nominal, startDate, endDate, fixingDays, index,
                gearing, spread, refPeriodStart, refPeriodEnd, dayCounter,
                isInArrears, exCouponDate, BusinessDayConvention.PRECEDING);
    }

    public final SwapIndex swapIndex() {
        return swapIndex_;
    }

    @Override
    public void accept(AcyclicVisitor v) {
        if (v instanceof Visitor) {
            try {
                @SuppressWarnings("unchecked")
                Visitor<CmsCoupon> vv = (Visitor<CmsCoupon>) v;
                vv.visit(this);
            } catch (ClassCastException e) {
                // The visitor is interested in another coupon type.
                super.accept(v);
            }
        }
    }
}
