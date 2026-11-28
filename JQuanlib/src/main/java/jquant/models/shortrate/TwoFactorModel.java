package jquant.models.shortrate;

import jquant.Lattice;
import jquant.StochasticProcess;
import jquant.StochasticProcess1D;
import jquant.TimeGrid;
import jquant.math.Array;
import jquant.math.Matrix;
import jquant.methods.lattices.TreeLattice2D;
import jquant.methods.lattices.TrinomialTree;
import jquant.methods.lattices.impl.TreeLatticeImpl;
import jquant.models.ShortRateModel;
import jquant.processes.StochasticProcessArray;

import java.util.ArrayList;
import java.util.List;

/**
 * Abstract base class for two-factor models.
 *
 * <p>Ported from QuantLib
 * {@code ql/models/shortrate/twofactormodel.{hpp,cpp}}.
 */
public abstract class TwoFactorModel extends ShortRateModel {

    public TwoFactorModel(int nArguments) {
        super(nArguments);
    }

    /**
     * Class describing the dynamics of the two state variables.
     *
     * <p>The short rate is a function of the two state variables
     * {@code x} and {@code y}: {@code r_t = f(t, x_t, y_t)}.
     */
    public abstract static class ShortRateDynamics {

        private final StochasticProcess1D xProcess_;
        private final StochasticProcess1D yProcess_;
        private final double correlation_;

        public ShortRateDynamics(StochasticProcess1D xProcess,
                                 StochasticProcess1D yProcess,
                                 double correlation) {
            xProcess_ = xProcess;
            yProcess_ = yProcess;
            correlation_ = correlation;
        }

        public abstract double shortRate(double t, double x, double y);

        /** Risk-neutral dynamics of the first state variable x. */
        public final StochasticProcess1D xProcess() {
            return xProcess_;
        }

        /** Risk-neutral dynamics of the second state variable y. */
        public final StochasticProcess1D yProcess() {
            return yProcess_;
        }

        /** Correlation between the two Brownian motions. */
        public final double correlation() {
            return correlation_;
        }

        /** Joint process of the two variables. */
        public StochasticProcess process() {
            final Matrix correlation = new Matrix(2, 2, 0.0);
            correlation.set(0, 0, 1.0);
            correlation.set(1, 1, 1.0);
            correlation.set(0, 1, correlation_);
            correlation.set(1, 0, correlation_);

            final List<StochasticProcess1D> processes = new ArrayList<>(2);
            processes.add(xProcess_);
            processes.add(yProcess_);
            return new StochasticProcessArray(processes, correlation);
        }
    }

    /**
     * Recombining two-dimensional tree discretizing the state variables.
     */
    public static class ShortRateTree extends TreeLattice2D {

        private final ShortRateDynamics dynamics_;

        public ShortRateTree(TrinomialTree tree1,
                             TrinomialTree tree2,
                             ShortRateDynamics dynamics) {
            super(tree1, tree2, dynamics.correlation());
            dynamics_ = dynamics;
            initImpl();
        }

        private void initImpl() {
            setImpl_(new TreeLatticeImpl() {
                @Override
                public int size(int index) {
                    return ShortRateTree.this.size(index);
                }

                @Override
                public double discount(int i, int j) {
                    return ShortRateTree.this.discount(i, j);
                }

                @Override
                public int descendant(int i, int j, int branch) {
                    return ShortRateTree.this.descendant(i, j, branch);
                }

                @Override
                public double probability(int i, int j, int branch) {
                    return ShortRateTree.this.probability(i, j, branch);
                }

                @Override
                public void stepback(int i, Array values, Array newValues) {
                    ShortRateTree.this.stepback(i, values, newValues);
                }

                @Override
                public double underlying(int i, int j) {
                    // A two-dimensional tree has no single underlying value.
                    return 0.0;
                }
            });
        }

        public double discount(int i, int index) {
            final int modulo = tree1_.size(i);
            final int index1 = index % modulo;
            final int index2 = index / modulo;

            final double x = tree1_.underlying(i, index1);
            final double y = tree2_.underlying(i, index2);

            final double r = dynamics_.shortRate(timeGrid().get(i), x, y);
            return Math.exp(-r * timeGrid().dt(i));
        }
    }

    /** Returns the short-rate dynamics. */
    public abstract ShortRateDynamics dynamics();

    /** Returns a two-dimensional trinomial tree. */
    @Override
    public Lattice tree(final TimeGrid grid) {
        final ShortRateDynamics dynamics = dynamics();

        final TrinomialTree tree1 = new TrinomialTree(dynamics.xProcess(), grid, false);
        final TrinomialTree tree2 = new TrinomialTree(dynamics.yProcess(), grid, false);

        return new ShortRateTree(tree1, tree2, dynamics);
    }
}
