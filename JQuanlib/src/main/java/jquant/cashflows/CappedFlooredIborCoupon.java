package jquant.cashflows;

import jquant.indexes.IborIndex;
import jquant.patterns.AcyclicVisitor;
import jquant.patterns.Visitor;
import jquant.time.Date;
import jquant.time.DayCounter;

import java.util.Optional;

import static jquant.math.CommonUtil.QL_FAIL;

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
        super(new IborCoupon(paymentDate, nominal, startDate, endDate, fixingDays,
                index, gearing, spread, refPeriodStart, refPeriodEnd,
                dayCounter, isInArrears, exCouponDate), cap, floor);
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
                QL_FAIL("not a Black-volatility term structure visitor");
            }
        }
    }

    @Override
    public boolean hasOccurred(Date refDate, Optional<Boolean> includeRefDate) {
        return false;
    }

    @Override
    public boolean tradingExCoupon(Date refDate) {
        return false;
    }
}
