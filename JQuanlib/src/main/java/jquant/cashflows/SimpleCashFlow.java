package jquant.cashflows;

import jquant.CashFlow;
import jquant.patterns.AcyclicVisitor;
import jquant.time.Date;

import java.util.Optional;

/**
 * Single unadjusted cash flow. Used for the bond redemption.
 *
 * <p>https://github.com/lballabio/QuantLib/blob/master/ql/cashflows/simplecashflow.hpp
 */
public class SimpleCashFlow extends CashFlow {
    private final double amount_;
    private final Date date_;

    public SimpleCashFlow(double amount, Date date) {
        amount_ = amount;
        date_ = date;
    }

    @Override
    public Date date() {
        return date_;
    }

    @Override
    public boolean hasOccurred(Date refDate, Optional<Boolean> includeRefDate) {
        return CashFlow.hasOccurred(date_, refDate, includeRefDate);
    }

    @Override
    public void performCalculations() {
    }

    @Override
    public double amount() {
        return amount_;
    }

    @Override
    public Date exCouponDate() {
        return new Date();
    }

    @Override
    public boolean tradingExCoupon(Date refDate) {
        return false;
    }

    @Override
    public void accept(AcyclicVisitor v) {
    }
}
