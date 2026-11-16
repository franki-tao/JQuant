package jquant.methods.lattices;

import jquant.StochasticProcess1D;

import static jquant.math.CommonUtil.QL_REQUIRE;
import static jquant.math.MathUtils.PeizerPrattMethod2Inversion;
import static jquant.math.MathUtils.PeizerPrattMethod2LogInversion;

//! Leisen & Reimer tree: multiplicative approach
/*! \ingroup lattices */
public class LeisenReimer extends BinomialTree<LeisenReimer> {
    protected double up_, down_, pu_, pd_;

    /** One Leisen-Reimer step: probabilities and multiplicative moves. */
    public static final class LeisenReimerStep {
        public final double upProbability;
        public final double downProbability;
        public final double up;
        public final double down;

        public LeisenReimerStep(double upProbability, double downProbability,
                                double up, double down) {
            this.upProbability = upProbability;
            this.downProbability = downProbability;
            this.up = up;
            this.down = down;
        }
    }

    /**
     * One Leisen-Reimer step, as in {@code ql/methods/lattices/binomialtree.hpp}.
     *
     * <p>For an up probability strictly inside {@code (0, 1)} the moves are
     * the historical ones. Once that probability rounds to 0 or 1, the
     * complementary probability is Peizer-Pratt at {@code -z}. If the
     * complementary probability underflows as well, the moves are formed from
     * the log inversion so the factors stay finite.
     */
    public static LeisenReimerStep leisenReimerStep(double d2, double ermqdt,
                                                    double variance, int oddSteps) {
        final double width = Math.sqrt(variance);
        final double pu = PeizerPrattMethod2Inversion(d2, oddSteps);
        final double pdash = PeizerPrattMethod2Inversion(d2 + width, oddSteps);

        // Keep the historical moves whenever pu still has a complementary digit.
        // Prices on this branch stay exactly what they were before.
        if (pu > 0.0 && pu < 1.0) {
            double up = ermqdt * pdash / pu;
            double down = (ermqdt - pu * up) / (1.0 - pu);
            return new LeisenReimerStep(pu, 1.0 - pu, up, down);
        }

        final double pd = PeizerPrattMethod2Inversion(-d2, oddSteps);
        final double pdashDown = PeizerPrattMethod2Inversion(-(d2 + width), oddSteps);
        if (pu > 0.0 && pd > 0.0 && pdash > 0.0 && pdashDown > 0.0) {
            double up = ermqdt * pdash / pu;
            double down = ermqdt * pdashDown / pd;
            return new LeisenReimerStep(pu, pd, up, down);
        }

        final double logUp = PeizerPrattMethod2LogInversion(d2 + width, oddSteps)
                - PeizerPrattMethod2LogInversion(d2, oddSteps);
        final double logDown = PeizerPrattMethod2LogInversion(-(d2 + width), oddSteps)
                - PeizerPrattMethod2LogInversion(-d2, oddSteps);
        double up = ermqdt * Math.exp(logUp);
        double down = ermqdt * Math.exp(logDown);
        double upProbability = pu > 0.0 ? pu : 0.0;
        double downProbability = pd > 0.0 ? pd : 0.0;
        if (upProbability == 0.0 && downProbability == 0.0) {
            if (d2 >= 0.0)
                upProbability = 1.0;
            else
                downProbability = 1.0;
        } else if (upProbability == 0.0) {
            downProbability = 1.0;
        } else if (downProbability == 0.0) {
            upProbability = 1.0;
        }
        QL_REQUIRE(Double.isFinite(up) && Double.isFinite(down)
                        && up > 0.0 && down > 0.0,
                "invalid Leisen-Reimer factors: " + up + ", " + down);
        return new LeisenReimerStep(upProbability, downProbability, up, down);
    }

    public LeisenReimer(StochasticProcess1D process, double end, int steps, double strike) {
        super(process, end, ((steps % 2) != 0 ? steps : (steps + 1)));
        QL_REQUIRE(strike>0.0, "strike must be positive");
        int oddSteps = ((steps % 2) != 0 ? steps : (steps + 1));
        double variance = process.variance(0.0, x0, end);
        double ermqdt = Math.exp(driftPerStep + 0.5*variance/oddSteps);
        double d2 = (Math.log(x0/strike) + driftPerStep*oddSteps ) / Math.sqrt(variance);
        LeisenReimerStep step = leisenReimerStep(d2, ermqdt, variance, oddSteps);
        pu_ = step.upProbability;
        pd_ = step.downProbability;
        up_ = step.up;
        down_ = step.down;
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
