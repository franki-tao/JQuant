package jquant.methods.lattices;

import jquant.StochasticProcess1D;

import static jquant.math.CommonUtil.QL_REQUIRE;

public class Joshi4 extends BinomialTree<Joshi4> {
    protected double up_, down_, pu_, pd_;

    public Joshi4(StochasticProcess1D process, double end, int steps, double strike) {
        super(process, end, (steps % 2) != 0 ? steps : (steps + 1));
        QL_REQUIRE(strike > 0.0, "strike must be positive");
        int oddSteps = (steps % 2) != 0 ? steps : (steps + 1);
        double variance = process.variance(0.0, x0, end);
        double ermqdt = Math.exp(driftPerStep + 0.5 * variance / oddSteps);
        double d2 = (Math.log(x0 / strike) + driftPerStep * oddSteps) /
                Math.sqrt(variance);
        pu_ = computeUpProb((oddSteps - 1.0) / 2.0, d2);
        pd_ = 1.0 - pu_;
        double pdash = computeUpProb((oddSteps - 1.0) / 2.0, d2 + Math.sqrt(variance));
        up_ = ermqdt * pdash / pu_;
        down_ = (ermqdt - pu_ * up_) / (1.0 - pu_);
    }

    public double underlying(int i, int index) {
        return x0 * Math.pow(down_, i - index)
                * Math.pow(up_, index);
    }

    public double probability(int x, int y, int branch) {
        return (branch == 1 ? pu_ : pd_);
    }

    protected double computeUpProb(double k, double dj) {
        double alpha = dj / (Math.sqrt(8.0));
        double alpha2 = alpha * alpha;
        double alpha3 = alpha * alpha2;
        double alpha5 = alpha3 * alpha2;
        double alpha7 = alpha5 * alpha2;
        double beta = -0.375 * alpha - alpha3;
        double gamma = (5.0 / 6.0) * alpha5 + (13.0 / 12.0) * alpha3
                + (25.0 / 128.0) * alpha;
        double delta = -0.1025 * alpha - 0.9285 * alpha3
                - 1.43 * alpha5 - 0.5 * alpha7;
        double p = 0.5;
        double rootk = Math.sqrt(k);
        p += alpha / rootk;
        p += beta / (k * rootk);
        p += gamma / (k * k * rootk);
        // delete next line to get results for j three tree
        p += delta / (k * k * k * rootk);
        return p;
    }

    @Override
    protected void specificAction() {

    }
}
