package jquant.cashflows;

import jquant.CashFlow;
import jquant.patterns.AcyclicVisitor;
import jquant.time.Date;

import java.util.Optional;

/**
 * Predetermined cash flow paying a fixed amount at a given date.
 *
 * <p>https://github.com/lballabio/QuantLib/blob/master/ql/cashflows/dividend.hpp
 */
public class FixedDividend extends Dividend {
    private final double amount_;

    public FixedDividend(double amount, Date date) {
        super(date);
        amount_ = amount;
    }

    @Override
    public double amount() {
        return amount_;
    }

    @Override
    public double amount(double underlying) {
        return amount_;
    }

    @Override
    public boolean hasOccurred(Date refDate, Optional<Boolean> includeRefDate) {
        return CashFlow.hasOccurred(date_, refDate, includeRefDate);
    }

    @Override
    public void performCalculations() {
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
