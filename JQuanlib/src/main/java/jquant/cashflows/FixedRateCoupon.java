package jquant.cashflows;

import jquant.Compounding;
import jquant.InterestRate;
import jquant.time.Date;
import jquant.time.DayCounter;
import jquant.time.Frequency;
import jquant.time.TimeUtils;

import java.util.Optional;

//! %Coupon paying a fixed interest rate
public class FixedRateCoupon extends Coupon {
    private InterestRate rate_;
    private double amount_;

    public FixedRateCoupon(final Date paymentDate,
                           double nominal,
                           double rate,
                           final DayCounter dayCounter,
                           final Date accrualStartDate,
                           final Date accrualEndDate,
                           final Date refPeriodStart, // = Date(),
                           final Date refPeriodEnd, // = Date(),
                           final Date exCouponDate //= Date()
    ) {
        super(paymentDate, nominal, accrualStartDate, accrualEndDate,
                refPeriodStart, refPeriodEnd, exCouponDate);
        rate_ = new InterestRate(rate, dayCounter, Compounding.Simple, Frequency.ANNUAL);
    }

    public FixedRateCoupon(final Date paymentDate,
                           double nominal,
                           InterestRate interestRate,
                           final Date accrualStartDate,
                           final Date accrualEndDate,
                           final Date refPeriodStart, // = Date(),
                           final Date refPeriodEnd, // = Date(),
                           final Date exCouponDate //= Date()
    ) {
        super(paymentDate,
                nominal,
                accrualStartDate,
                accrualEndDate,
                refPeriodStart,
                refPeriodEnd,
                exCouponDate);
        rate_ = interestRate;
    }

    @Override
    public boolean hasOccurred(Date refDate, Optional<Boolean> includeRefDate) {
        return false;
    }

    @Override
    public void performCalculations() {
        amount_ = nominal() * (rate_.compoundFactor(accrualStartDate_, accrualEndDate_,
                refPeriodStart_, refPeriodEnd_) -
                1.0);
    }

    @Override
    public double amount() {
        calculate();
        return amount_;
    }

    @Override
    public boolean tradingExCoupon(Date refDate) {
        return false;
    }

    public double rate() {
        return rate_.rate();
    }

    public InterestRate interestRate() {
        return rate_;
    }

    @Override
    public DayCounter dayCounter() {
        return rate_.dayCounter();
    }

    @Override
    public double accruedAmount(final Date d) {
        if (TimeUtils.leq(d, accrualStartDate_) || TimeUtils.greater(d, paymentDate_)) {
            // out of coupon range
            return 0.0;
        } else if (tradingExCoupon(d)) {
            return -nominal() * (rate_.compoundFactor(d,
                    TimeUtils.max(d, accrualEndDate_),
                    refPeriodStart_,
                    refPeriodEnd_) - 1.0);
        } else {
            // usual case
            return nominal() * (rate_.compoundFactor(accrualStartDate_,
                    TimeUtils.min(d, accrualEndDate_),
                    refPeriodStart_,
                    refPeriodEnd_) - 1.0);
        }
    }
}
