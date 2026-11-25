package jquant.cashflows;

import jquant.CashFlow;
import jquant.time.Date;

import java.util.ArrayList;
import java.util.List;

import static jquant.math.CommonUtil.QL_REQUIRE;

/**
 * Predetermined cash flow
 * This cash flow pays a predetermined amount at a given date.
 */
public abstract class Dividend extends CashFlow {
    protected Date date_;
    public Dividend(final Date date) {
        // Official Date is a value type; store an owned copy (empty serial 0
        // stays an empty Date through Date.copy()).
        date_ = date == null ? new Date() : date.copy();
    }
    //! \name Event interface
    //@{
    @Override
    public Date date() {
        return date_.copy();
    }
    //@}
    //! \name CashFlow interface
    //@{
    public abstract double amount();
    //@}
    public abstract double amount(double underlying);

    /**
     * Helper building a sequence of fixed dividends from matching dates and
     * amounts. {@code QuantLib::DividendVector}.
     */
    public static List<Dividend> dividendVector(List<Date> dividendDates, double[] dividends) {
        QL_REQUIRE(dividendDates.size() == dividends.length,
                "size mismatch between dividend dates and amounts");
        List<Dividend> items = new ArrayList<>(dividendDates.size());
        for (int i = 0; i < dividendDates.size(); i++) {
            items.add(new FixedDividend(dividends[i], dividendDates.get(i)));
        }
        return items;
    }
}
