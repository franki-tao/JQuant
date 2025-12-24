package jquant.methods.lattices;

import jquant.StochasticProcess1D;

//! Additive equal probabilities binomial tree
/*! \ingroup lattices */
public class AdditiveEQPBinomialTree extends EqualProbabilitiesBinomialTree<AdditiveEQPBinomialTree> {
    public AdditiveEQPBinomialTree(StochasticProcess1D process, double end, int steps, double strike) {
        super(process, end, steps);
        up = -0.5 * driftPerStep + 0.5 *
                Math.sqrt(4.0 * process.variance(0.0, x0, dt) - 3.0 * driftPerStep * driftPerStep);
    }

    @Override
    protected void specificAction() {
    }
}
