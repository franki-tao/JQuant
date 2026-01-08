package jquant.cashflows;

import jquant.CashFlow;
import jquant.time.Date;

/**
 * Predetermined cash flow
 * This cash flow pays a predetermined amount at a given date.
 */
public abstract class Dividend extends CashFlow {
    protected Date date_;
    public Dividend(final Date date) {
        date_ = date;
    }
    //! \name Event interface
    //@{
    @Override
    public Date date() {
        return date_;
    }
    //@}
    //! \name CashFlow interface
    //@{
    public abstract double amount();
    //@}
    public abstract double amount(double underlying);
}
