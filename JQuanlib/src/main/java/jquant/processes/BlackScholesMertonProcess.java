package jquant.processes;

import jquant.Handle;
import jquant.Quote;
import jquant.StochasticProcess1DImpl;
import jquant.termstructures.YieldTermStructure;
import jquant.termstructures.volatility.equityfx.BlackVolTermStructure;

//! Merton (1973) extension to the Black-Scholes stochastic process
/*! This class describes the stochastic process ln(S) for a stock or
    stock index paying a continuous dividend yield given by
    \f[
        d\ln S(t, S) = (r(t) - q(t) - \frac{\sigma(t, S)^2}{2}) dt
                 + \sigma dW_t.
    \f]

    \ingroup processes
*/
// d = EulerDiscretization, forceDiscretization = false
public class BlackScholesMertonProcess extends GeneralizedBlackScholesProcess {
    public BlackScholesMertonProcess(final Handle<Quote> x0,
                                     final Handle<YieldTermStructure> dividendTS,
                                     final Handle<YieldTermStructure> riskFreeTS,
                                     final Handle<BlackVolTermStructure> blackVolTS,
                                     final StochasticProcess1DImpl d,
                                     boolean forceDiscretization) {
        super(x0, dividendTS,riskFreeTS,blackVolTS,d, forceDiscretization);
    }
}
