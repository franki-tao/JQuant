package jquant.instruments;

import jquant.time.BusinessDayConvention;
import jquant.time.Calendar;
import jquant.time.Date;

/**
 * Zero-coupon bond: a single redemption at the adjusted maturity date.
 *
 * <p>https://github.com/lballabio/QuantLib/blob/master/ql/instruments/bonds/zerocouponbond.cpp
 */
public class ZeroCouponBond extends Bond {

    public ZeroCouponBond(int settlementDays,
                          Calendar calendar,
                          double faceAmount,
                          Date maturityDate) {
        this(settlementDays, calendar, faceAmount, maturityDate,
                BusinessDayConvention.FOLLOWING, 100.0, new Date());
    }

    /** Official defaults: redemption 100 and the null issue date. */
    public ZeroCouponBond(int settlementDays,
                          Calendar calendar,
                          double faceAmount,
                          Date maturityDate,
                          BusinessDayConvention paymentConvention) {
        this(settlementDays, calendar, faceAmount, maturityDate,
                paymentConvention, 100.0, new Date());
    }

    public ZeroCouponBond(int settlementDays,
                          Calendar calendar,
                          double faceAmount,
                          Date maturityDate,
                          BusinessDayConvention paymentConvention,
                          double redemption,
                          Date issueDate) {
        super(settlementDays, calendar, issueDate);
        maturityDate_ = maturityDate.copy();
        Date redemptionDate = calendar_.adjust(maturityDate, paymentConvention);
        setSingleRedemption(faceAmount, redemption, redemptionDate);
    }
}
