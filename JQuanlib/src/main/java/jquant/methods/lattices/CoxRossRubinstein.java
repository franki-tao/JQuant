package jquant.methods.lattices;

import jquant.StochasticProcess1D;

import static jquant.math.CommonUtil.QL_REQUIRE;

//! Cox-Ross-Rubinstein (multiplicative) equal jumps binomial tree
/*! \ingroup lattices */
public class CoxRossRubinstein extends EqualJumpsBinomialTree<CoxRossRubinstein> {
    public CoxRossRubinstein(StochasticProcess1D process, double end, int steps, double strike) {
        super(process, end, steps);
        dx_ = process.stdDeviation(0.0, x0, dt);
        pu_ = 0.5 + 0.5 * driftPerStep / dx_;
        pd_ = 1.0 - pu_;

        QL_REQUIRE(pu_ <= 1.0, "negative probability");
        QL_REQUIRE(pu_ >= 0.0, "negative probability");
    }

    @Override
    protected void specificAction() {
    }
}
