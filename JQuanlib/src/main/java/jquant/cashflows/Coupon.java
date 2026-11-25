package jquant.cashflows;

import jquant.CashFlow;
import jquant.patterns.AcyclicVisitor;
import jquant.patterns.Visitor;
import jquant.time.Date;
import jquant.time.DayCounter;
import jquant.time.TimeUtils;

import static jquant.math.CommonUtil.QL_FAIL;

//! %coupon accruing over a fixed period
/*! This class implements part of the CashFlow interface but it is
    still abstract and provides derived classes with methods for
    accrual period calculations.
*/
public abstract class Coupon extends CashFlow {
    protected Date paymentDate_;
    protected double nominal_;
    protected Date accrualStartDate_, accrualEndDate_, refPeriodStart_, refPeriodEnd_;
    protected Date exCouponDate_;
    protected double accrualPeriod_;

    public Coupon(final Date paymentDate,
                  double nominal,
                  final Date accrualStartDate,
                  final Date accrualEndDate,
                  final Date refPeriodStart,
                  final Date refPeriodEnd,
                  final Date exCouponDate) {
        // Official Date is a value type; Java Dates are mutable, so every
        // stored date is an owned copy rather than an alias of the caller's.
        paymentDate_ = copyOrEmpty(paymentDate);
        nominal_ = nominal;
        accrualStartDate_ = copyOrEmpty(accrualStartDate);
        accrualEndDate_ = copyOrEmpty(accrualEndDate);
        refPeriodStart_ = copyOrEmpty(refPeriodStart);
        refPeriodEnd_ = copyOrEmpty(refPeriodEnd);
        exCouponDate_ = copyOrEmpty(exCouponDate);
        accrualPeriod_ = Double.NaN;
        // The official fallback for an empty reference period must not alias
        // the accrual dates, or mutating one accessor result would edit both.
        if (TimeUtils.equals(refPeriodStart_, new Date()))
            refPeriodStart_ = accrualStartDate_.copy();
        if (TimeUtils.equals(refPeriodEnd_, new Date()))
            refPeriodEnd_ = accrualEndDate_.copy();
    }

    private static Date copyOrEmpty(Date date) {
        return date == null ? new Date() : date.copy();
    }

    @Override
    public Date date() {
        return paymentDate_.copy();
    }

    @Override
    public Date exCouponDate() {
        return exCouponDate_.copy();
    }

    public double nominal() {
        return nominal_;
    }

    //! start of the accrual period
    public final Date accrualStartDate() {
        return accrualStartDate_.copy();
    }

    //! end of the accrual period
    public final Date accrualEndDate() {
        return accrualEndDate_.copy();
    }

    //! start date of the reference period
    public final Date referencePeriodStart() {
        return refPeriodStart_.copy();
    }

    //! end date of the reference period
    public final Date referencePeriodEnd() {
        return refPeriodEnd_.copy();
    }

    //! accrual period as fraction of year
    public double accrualPeriod() {
        if (Double.isNaN(accrualPeriod_))
            accrualPeriod_ = dayCounter().yearFraction(accrualStartDate_, accrualEndDate_,
                    refPeriodStart_, refPeriodEnd_);
        return accrualPeriod_;
    }

    //! accrual period in days
    public int accrualDays() {
        return dayCounter().dayCount(accrualStartDate_,
                accrualEndDate_);
    }

    //! accrued rate
    public abstract double rate();

    //! day counter for accrual calculation
    public abstract DayCounter dayCounter();

    //! accrued period as fraction of year at the given date
    public double accruedPeriod(final Date d) {
        if (TimeUtils.leq(d, accrualStartDate_) || TimeUtils.greater(d, paymentDate_)) {
            return 0.0;
        } else if (tradingExCoupon(d)) {
            return -dayCounter().yearFraction(d, TimeUtils.max(d, accrualEndDate_),
                    refPeriodStart_, refPeriodEnd_);
        } else {
            return dayCounter().yearFraction(accrualStartDate_,
                    TimeUtils.min(d, accrualEndDate_),
                    refPeriodStart_,
                    refPeriodEnd_);
        }
    }

    //! accrued days at the given date
    public int accruedDays(final Date d) {
        if (TimeUtils.leq(d, accrualStartDate_) || TimeUtils.greater(d, paymentDate_)) {
            return 0;
        } else {
            return dayCounter().dayCount(accrualStartDate_,
                    TimeUtils.min(d, accrualEndDate_));
        }
    }
    //! accrued amount at the given date
    public abstract double accruedAmount(final Date d);

    public void accept(AcyclicVisitor v) {
        if (v instanceof Visitor) {
            try {
                // 尝试强制转型
                @SuppressWarnings("unchecked")
                Visitor<Coupon> vv = (Visitor<Coupon>) v;
                vv.visit(this);
            } catch (ClassCastException e) {
                // 如果类型不匹配，说明该访问者不关心这个特定的子类
                QL_FAIL("not a Black-volatility term structure visitor");
            }
        }
    }
}
