package jquant.termstructures.volatility.equityfx;

import jquant.Handle;
import jquant.Quote;
import jquant.patterns.AcyclicVisitor;
import jquant.patterns.Visitor;
import jquant.quotes.SimpleQuote;
import jquant.time.BusinessDayConvention;
import jquant.time.Calendar;
import jquant.time.Date;
import jquant.time.DayCounter;

import static jquant.math.CommonUtil.QL_FAIL;

//! Constant Black volatility, no time-strike dependence
/*! This class implements the BlackVolatilityTermStructure
    interface for a constant Black volatility (no time/strike
    dependence).
*/
public class BlackConstantVol extends BlackVolatilityTermStructure {
    private Handle<Quote> volatility_;

    public BlackConstantVol(final Date referenceDate,
                            final Calendar cal,
                            double volatility,
                            final DayCounter dc) {
        super(referenceDate, cal, BusinessDayConvention.FOLLOWING, dc);
        volatility_ = new Handle<Quote>(new SimpleQuote(volatility), true);
    }

    public BlackConstantVol(final Date referenceDate,
                            final Calendar cal,
                            Handle<Quote> volatility,
                            final DayCounter dc) {
        super(referenceDate, cal, BusinessDayConvention.FOLLOWING, dc);
        volatility_ = volatility;
        registerWith(volatility_.getValue());
    }

    public BlackConstantVol(int settlementDays,
                            final Calendar cal,
                            double volatility,
                            final DayCounter dc) {
        super(settlementDays, cal, BusinessDayConvention.FOLLOWING, dc);
        volatility_ = new Handle<>(new SimpleQuote(volatility), true);
    }

    public BlackConstantVol(int settlementDays,
                            final Calendar cal,
                            Handle<Quote> volatility,
                            final DayCounter dc) {
        super(settlementDays, cal, BusinessDayConvention.FOLLOWING, dc);
        volatility_ = volatility;
        registerWith(volatility_.getValue());
    }

    //! \name TermStructure interface
    //@{
    public Date maxDate() {
        return Date.maxDate();
    }

    //@}
    //! \name VolatilityTermStructure interface
    //@{
    public double minStrike() {
        return Double.MIN_VALUE;
    }

    public double maxStrike() {
        return Double.MAX_VALUE;
    }

    //@}
    //! \name Visitability
    //@{
    public void accept(AcyclicVisitor v) {
        if (v instanceof Visitor) {
            try {
                // 尝试强制转型
                @SuppressWarnings("unchecked")
                Visitor<BlackConstantVol> vv = (Visitor<BlackConstantVol>) v;
                vv.visit(this);
            } catch (ClassCastException e) {
                // 如果类型不匹配，说明该访问者不关心这个特定的子类
                QL_FAIL("not a Black-volatility term structure visitor");
            }
        }
    }

    //@}
    protected double blackVolImpl(double t, double v) {
        return volatility_.getValue().value();
    }
}
