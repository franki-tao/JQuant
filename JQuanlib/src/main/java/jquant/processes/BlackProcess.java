package jquant.processes;

import jquant.Handle;
import jquant.Quote;
import jquant.StochasticProcess1DImpl;
import jquant.discretization;
import jquant.termstructures.YieldTermStructure;
import jquant.termstructures.volatility.equityfx.BlackVolTermStructure;

//! Black (1976) stochastic process
/*! This class describes the stochastic process \f$ S \f$ for a
    forward or futures contract given by
    \f[
        d\ln S(t) = -\frac{\sigma(t, S)^2}{2} dt + \sigma dW_t.
    \f]

    \warning while the interface is expressed in terms of \f$ S \f$,
             the internal calculations work on \f$ ln S \f$.

    \ingroup processes
*/
public class BlackProcess extends GeneralizedBlackScholesProcess {
    public BlackProcess(
    final Handle<Quote> x0,
    final Handle<YieldTermStructure> riskFreeTS,
    final Handle<BlackVolTermStructure> blackVolTS,
    final StochasticProcess1DImpl d,
    boolean forceDiscretization) {
        super(x0,riskFreeTS,riskFreeTS,blackVolTS,d,
                forceDiscretization);
    }
}
