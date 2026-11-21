package jquant.cashflows;

import jquant.indexes.IborIndex;
import jquant.patterns.AcyclicVisitor;
import jquant.patterns.Visitor;
import jquant.time.BusinessDayConvention;
import jquant.time.Date;
import jquant.time.DayCounter;

public class CappedFlooredIborCoupon extends CappedFlooredCoupon {
    public CappedFlooredIborCoupon(
            final Date paymentDate,
            double nominal,
            final Date startDate,
            final Date endDate,
            int fixingDays,
            final IborIndex index,
            double gearing, // = 1.0,
            double spread, // = 0.0,
            double cap, // = Null<Rate>(),
            double floor, // = Null<Rate>(),
            final Date refPeriodStart, // = Date(),
            final Date refPeriodEnd, // = Date(),
            final DayCounter dayCounter, // = DayCounter(),
            boolean isInArrears, // = false,
            final Date exCouponDate // = Date()
    ) {
        this(paymentDate, nominal, startDate, endDate, fixingDays,
                index, gearing, spread, cap, floor, refPeriodStart, refPeriodEnd,
                dayCounter, isInArrears, exCouponDate,
                BusinessDayConvention.PRECEDING);
    }

    public CappedFlooredIborCoupon(
            final Date paymentDate,
            double nominal,
            final Date startDate,
            final Date endDate,
            int fixingDays,
            final IborIndex index,
            double gearing,
            double spread,
            double cap,
            double floor,
            final Date refPeriodStart,
            final Date refPeriodEnd,
            final DayCounter dayCounter,
            boolean isInArrears,
            final Date exCouponDate,
            BusinessDayConvention fixingConvention
    ) {
        super(new IborCoupon(paymentDate, nominal, startDate, endDate, fixingDays,
                index, gearing, spread, refPeriodStart, refPeriodEnd,
                dayCounter, isInArrears, exCouponDate, fixingConvention), cap, floor);
    }

    @Override
    public void accept(AcyclicVisitor v) {
        if (v instanceof Visitor) {
            try {
                // 尝试强制转型
                @SuppressWarnings("unchecked")
                Visitor<CappedFlooredIborCoupon> vv = (Visitor<CappedFlooredIborCoupon>) v;
                vv.visit(this);
            } catch (ClassCastException e) {
                // 如果类型不匹配，说明该访问者不关心这个特定的子类
                super.accept(v);
            }
        }
    }
}
