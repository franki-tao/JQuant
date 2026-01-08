package jquant.termstructures.yield;
import jquant.Handle;
import jquant.termstructures.YieldTermStructure;
import jquant.termstructures.volatility.equityfx.BlackVolTermStructure;
import jquant.time.*;

import static jquant.Compounding.Continuous;

//! Quanto term structure
/*! Quanto term structure for modelling quanto effect in
    option pricing.

    \note This term structure will remain linked to the original
          structures, i.e., any changes in the latters will be
          reflected in this structure as well.
*/
public class QuantoTermStructure extends ZeroYieldStructure {
    private Handle<YieldTermStructure> underlyingDividendTS_, riskFreeTS_,
            foreignRiskFreeTS_;
    private Handle<BlackVolTermStructure> underlyingBlackVolTS_,
            exchRateBlackVolTS_;
    private double underlyingExchRateCorrelation_, strike_, exchRateATMlevel_;

    public QuantoTermStructure(final Handle<YieldTermStructure> underlyingDividendTS,
                               Handle<YieldTermStructure> riskFreeTS,
                               Handle<YieldTermStructure> foreignRiskFreeTS,
                               Handle<BlackVolTermStructure> underlyingBlackVolTS,
                               double strike,
                               Handle<BlackVolTermStructure> exchRateBlackVolTS,
                               double exchRateATMlevel,
                               double underlyingExchRateCorrelation) {
        super(underlyingDividendTS.getValue().dayCounter());
        underlyingDividendTS_ = underlyingDividendTS;
        riskFreeTS_ = riskFreeTS;
        foreignRiskFreeTS_ = foreignRiskFreeTS;
        underlyingBlackVolTS_ = underlyingBlackVolTS;
        exchRateATMlevel_ = exchRateATMlevel;
        underlyingExchRateCorrelation_ = underlyingExchRateCorrelation;
        exchRateBlackVolTS_ = exchRateBlackVolTS;
        strike_ = strike;
        registerWith(underlyingDividendTS_.currentLink());
        registerWith(riskFreeTS_.currentLink());
        registerWith(foreignRiskFreeTS_.currentLink());
        registerWith(underlyingBlackVolTS_.currentLink());
        registerWith(exchRateBlackVolTS_.currentLink());
    }

    public DayCounter dayCounter() {
        return underlyingDividendTS_.getValue().dayCounter();
    }

    public Calendar calendar() {
        return underlyingDividendTS_.getValue().calendar();
    }

    public int settlementDays() {
        return underlyingDividendTS_.getValue().settlementDays();
    }

    public Date referenceDate() {
        return underlyingDividendTS_.getValue().referenceDate();
    }

    public Date maxDate() {
        Date maxDate = TimeUtils.min(underlyingDividendTS_.getValue().maxDate(),
                riskFreeTS_.getValue().maxDate());
        maxDate = TimeUtils.min(maxDate, foreignRiskFreeTS_.currentLink().maxDate());
        maxDate = TimeUtils.min(maxDate, underlyingBlackVolTS_.currentLink().maxDate());
        maxDate = TimeUtils.min(maxDate, exchRateBlackVolTS_.currentLink().maxDate());
        return maxDate;
    }
    //! returns the zero yield as seen from the evaluation date
    protected double zeroYieldImpl(double t) {
        // warning: here it is assumed that all TS have the same daycount.
        //          It should be QL_REQUIREd
        return underlyingDividendTS_.getValue().zeroRate(t, Continuous, Frequency.NO_FREQUENCY, true).rate()
                + riskFreeTS_.getValue().zeroRate(t, Continuous, Frequency.NO_FREQUENCY, true).rate()
                - foreignRiskFreeTS_.getValue().zeroRate(t, Continuous, Frequency.NO_FREQUENCY, true).rate()
                + underlyingExchRateCorrelation_
                * underlyingBlackVolTS_.getValue().blackVol(t, strike_, true)
                * exchRateBlackVolTS_.currentLink().blackVol(t, exchRateATMlevel_, true);
    }
}
