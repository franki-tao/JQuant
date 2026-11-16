package jquant.methods.lattices;

import jquant.StochasticProcess1D;

import static jquant.math.CommonUtil.QL_REQUIRE;

//! Additive equal probabilities binomial tree
/*! \ingroup lattices */
public class AdditiveEQPBinomialTree extends EqualProbabilitiesBinomialTree<AdditiveEQPBinomialTree> {
    public AdditiveEQPBinomialTree(StochasticProcess1D process, double end, int steps, double strike) {
        super(process, end, steps);
        double radicand = 4.0 * process.variance(0.0, x0, dt)
                - 3.0 * driftPerStep * driftPerStep;
        QL_REQUIRE(radicand >= 0.0,
                "drift per step too large for the additive EQP tree: "
                        + "more steps are needed");
        up = -0.5 * driftPerStep + 0.5 * Math.sqrt(radicand);
    }

    @Override
    protected void specificAction() {
    }
}
