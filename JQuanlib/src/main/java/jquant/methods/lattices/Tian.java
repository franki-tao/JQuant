package jquant.methods.lattices;

import jquant.StochasticProcess1D;

import static jquant.math.CommonUtil.QL_REQUIRE;

//! %Tian tree: third moment matching, multiplicative approach
/*! \ingroup lattices */
public class Tian extends BinomialTree<Tian> {
    protected double up_, down_, pu_, pd_;

    public Tian(StochasticProcess1D process, double end, int steps, double strike) {
        super(process, end, steps);
        double q = Math.exp(process.variance(0.0, x0, dt));
        double r = Math.exp(driftPerStep) * Math.sqrt(q);

        up_ = 0.5 * r * q * (q + 1 + Math.sqrt (q * q + 2 * q - 3));
        down_ = 0.5 * r * q * (q + 1 - Math.sqrt (q * q + 2 * q - 3));

        pu_ = (r - down_) / (up_ - down_);
        pd_ = 1.0 - pu_;

        // doesn't work
        //     treeCentering_ = (up_+down_)/2.0;
        //     up_ = up_-treeCentering_;

        QL_REQUIRE(pu_ <= 1.0, "negative probability");
        QL_REQUIRE(pu_ >= 0.0, "negative probability");
    }

    public double underlying(int i, int index) {
        return x0 * Math.pow(down_, i - index) * Math.pow(up_, index);
    }

    public double probability(int x, int y, int branch) {
        return (branch == 1 ? pu_ : pd_);
    }

    @Override
    protected void specificAction() {

    }
}
