package jquant.processes;

import jquant.Handle;
import jquant.Quote;
import jquant.StochasticProcess1DImpl;
import jquant.termstructures.YieldTermStructure;
import jquant.termstructures.volatility.equityfx.BlackVolTermStructure;

//! Garman-Kohlhagen (1983) stochastic process
/*! This class describes the stochastic process \f$ S \f$ for an exchange
    rate given by
    \f[
        d\ln S(t) = (r(t) - r_f(t) - \frac{\sigma(t, S)^2}{2}) dt
                 + \sigma dW_t.
    \f]

    \warning while the interface is expressed in terms of \f$ S \f$,
             the internal calculations work on \f$ ln S \f$.

    \ingroup processes
*/
public class GarmanKohlagenProcess extends GeneralizedBlackScholesProcess {
    public GarmanKohlagenProcess(
            final Handle<Quote> x0,
            final Handle<YieldTermStructure> foreignRiskFreeTS,
            final Handle<YieldTermStructure> domesticRiskFreeTS,
            final Handle<BlackVolTermStructure> blackVolTS,
            final StochasticProcess1DImpl d,
            boolean forceDiscretization) {
        super(x0,foreignRiskFreeTS,domesticRiskFreeTS,
                blackVolTS,d,forceDiscretization);
    }
}
