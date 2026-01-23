package jquant.processes;

import jquant.*;
import jquant.termstructures.YieldTermStructure;
import jquant.termstructures.volatility.equityfx.BlackVolTermStructure;
import jquant.termstructures.yield.FlatForward;
import jquant.time.Frequency;
import jquant.time.calendars.NullCalendar;
import jquant.time.daycounters.Actual365Fixed;

//! Black-Scholes (1973) stochastic process
/*! This class describes the stochastic process \f$ S \f$ for a stock
    given by
    \f[
        d\ln S(t) = (r(t) - \frac{\sigma(t, S)^2}{2}) dt + \sigma dW_t.
    \f]

    \warning while the interface is expressed in terms of \f$ S \f$,
             the internal calculations work on \f$ ln S \f$.

    \ingroup processes
*/
public class BlackScholesProcess extends GeneralizedBlackScholesProcess {
    public BlackScholesProcess(
            final Handle<Quote> x0,
            final Handle<YieldTermStructure> riskFreeTS,
            final Handle<BlackVolTermStructure> blackVolTS,
            final StochasticProcess1DImpl d,
            boolean forceDiscretization) {
        super(x0,
                new Handle<>(new FlatForward(0, new NullCalendar(),
                        0, new Actual365Fixed(), Compounding.Continuous, Frequency.ANNUAL), true),
                riskFreeTS,
                blackVolTS,
                d,
                forceDiscretization
        );
    }
}
