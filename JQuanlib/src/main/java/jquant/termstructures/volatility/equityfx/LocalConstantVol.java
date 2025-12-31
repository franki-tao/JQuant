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
import jquant.time.calendars.NullCalendar;

import static jquant.math.CommonUtil.QL_FAIL;

//! Constant local volatility, no time-strike dependence
/*! This class implements the LocalVolatilityTermStructure
    interface for a constant local volatility (no time/asset
    dependence).  Local volatility and Black volatility are the
    same when volatility is at most time dependent, so this class
    is basically a proxy for BlackVolatilityTermStructure.
*/
public class LocalConstantVol extends LocalVolTermStructure {
    private Handle<Quote> volatility_;
    private DayCounter dayCounter_;

    public LocalConstantVol(final Date referenceDate,
                            double volatility,
                            DayCounter dayCounter) {
        super(referenceDate, new NullCalendar(), BusinessDayConvention.FOLLOWING, new DayCounter());
        volatility_ = new Handle<>(new SimpleQuote(volatility), true);
        dayCounter_ = dayCounter;
    }

    public LocalConstantVol(final Date referenceDate,
                            Handle<Quote> volatility,
                            DayCounter dayCounter) {
        super(referenceDate, new NullCalendar(), BusinessDayConvention.FOLLOWING, new DayCounter());
        volatility_ = volatility;
        dayCounter_ = dayCounter;
        registerWith(volatility_.getValue());
    }

    public LocalConstantVol(int settlementDays,
                            final Calendar calendar,
                            double volatility,
                            DayCounter dayCounter) {
        super(settlementDays, calendar, BusinessDayConvention.FOLLOWING, new DayCounter());
        volatility_ = new Handle<>(new SimpleQuote(volatility), true);
        dayCounter_ = dayCounter;
    }

    public LocalConstantVol(int settlementDays,
                            final Calendar calendar,
                            Handle<Quote> volatility,
                            DayCounter dayCounter) {
        super(settlementDays, calendar, BusinessDayConvention.FOLLOWING, new DayCounter());
        volatility_ = volatility;
        dayCounter_ = dayCounter;
        registerWith(volatility_.getValue());
    }
    //! \name TermStructure interface
    //@{
    @Override
    public DayCounter dayCounter() {
        return dayCounter_;
    }
    @Override
    public Date maxDate() {
        return Date.maxDate();
    }
    //@}
    //! \name VolatilityTermStructure interface
    //@{
    @Override
    public double minStrike() {
        return Double.MIN_VALUE;
    }
    @Override
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
                Visitor<LocalConstantVol> vv = (Visitor<LocalConstantVol>) v;
                vv.visit(this);
            } catch (ClassCastException e) {
                // 如果类型不匹配，说明该访问者不关心这个特定的子类
                QL_FAIL("not a Black-volatility term structure visitor");
            }
        }
    }
    //@}

    protected double localVolImpl(double a, double b) {
        return volatility_.getValue().value();
    }
}
