package jquant.methods.lattices;

import jquant.StochasticProcess1D;

import static jquant.math.CommonUtil.QL_REQUIRE;
//! %Trigeorgis (additive equal jumps) binomial tree
/*! \ingroup lattices */
public class Trigeorgis extends EqualJumpsBinomialTree<Trigeorgis> {
    public Trigeorgis(StochasticProcess1D process, double end, int steps, double strike) {
        super(process, end, steps);
        dx_ = Math.sqrt(process.variance(0.0, x0, dt) +
                driftPerStep * driftPerStep);
        pu_ = 0.5 + 0.5 * driftPerStep / dx_;

        pd_ = 1.0 - pu_;

        QL_REQUIRE(pu_ <= 1.0, "negative probability");
        QL_REQUIRE(pu_ >= 0.0, "negative probability");
    }

    @Override
    protected void specificAction() {
    }
}
