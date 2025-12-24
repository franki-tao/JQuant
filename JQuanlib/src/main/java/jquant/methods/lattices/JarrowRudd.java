package jquant.methods.lattices;

import jquant.StochasticProcess1D;

//! Jarrow-Rudd (multiplicative) equal probabilities binomial tree
/*! \ingroup lattices */
public class JarrowRudd extends EqualProbabilitiesBinomialTree<JarrowRudd> {
    public JarrowRudd(StochasticProcess1D process, double end, int steps, double strike) {
        super(process, end, steps);
        up = process.stdDeviation(0.0, x0, dt);
    }

    @Override
    protected void specificAction() {
    }
}
