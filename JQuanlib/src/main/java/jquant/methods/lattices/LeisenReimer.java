package jquant.methods.lattices;

import jquant.StochasticProcess1D;

import static jquant.math.CommonUtil.QL_REQUIRE;
import static jquant.math.MathUtils.PeizerPrattMethod2Inversion;

//! Leisen & Reimer tree: multiplicative approach
/*! \ingroup lattices */
public class LeisenReimer extends BinomialTree<LeisenReimer> {
    protected double up_, down_, pu_, pd_;

    public LeisenReimer(StochasticProcess1D process, double end, int steps, double strike) {
        super(process, end, ((steps % 2) != 0 ? steps : (steps + 1)));
        QL_REQUIRE(strike>0.0, "strike must be positive");
        int oddSteps = ((steps % 2) != 0 ? steps : (steps + 1));
        double variance = process.variance(0.0, x0, end);
        double ermqdt = Math.exp(driftPerStep + 0.5*variance/oddSteps);
        double d2 = (Math.log(x0/strike) + driftPerStep*oddSteps ) / Math.sqrt(variance);
        pu_ = PeizerPrattMethod2Inversion(d2, oddSteps);
        pd_ = 1.0 - pu_;
        double pdash = PeizerPrattMethod2Inversion(d2+Math.sqrt(variance), oddSteps);
        up_ = ermqdt * pdash / pu_;
        down_ = (ermqdt - pu_ * up_) / (1.0 - pu_);
    }

    public double underlying(int i, int index) {
        return x0 * Math.pow(down_, i - index)
                * Math.pow(up_, index);
    }

    public double probability(int x, int y, int branch)
    {
        return (branch == 1 ? pu_ : pd_);
    }

    @Override
    protected void specificAction() {

    }
}
