package jquant.quotes;

import jquant.Quote;

import static jquant.math.CommonUtil.QL_REQUIRE;

//! market element returning a stored value
public class SimpleQuote extends Quote {
    private double value_;
    public SimpleQuote(double value) {
        this.value_ = value;
    }
    //! \name Quote interface
    //@{
    @Override
    public double value() {
        QL_REQUIRE(isValid(), "invalid SimpleQuote");
        return value_;
    }

    @Override
    public boolean isValid() {
        return !Double.isNaN(value_);
    }
    //@}
    //! \name Modifiers
    //@{
    //! returns the difference between the new value and the old value
    public double setValue(double value) {
        double diff = value-value_;
        if (diff != 0.0) {
            value_ = value;
            notifyObservers();
        }
        return diff;
    }
    public void reset() {
        setValue(Double.NaN);
    }
    //@}
}
