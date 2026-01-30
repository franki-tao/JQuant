package jquant.models.shortrate;

import jquant.Lattice;
import jquant.StochasticProcess1D;
import jquant.TimeGrid;
import jquant.math.Array;
import jquant.math.Function;
import jquant.math.solvers1d.Brent;
import jquant.methods.lattices.TreeLattice1D;
import jquant.methods.lattices.TrinomialTree;
import jquant.models.ShortRateModel;
import jquant.models.TermStructureFittingParameter;

//! Single-factor short-rate model abstract class
/*! \ingroup shortrate */
public abstract class OneFactorModel extends ShortRateModel {

    public OneFactorModel(int nArguments) {
        super(nArguments);
    }

    //! Base class describing the short-rate dynamics
    public static abstract class ShortRateDynamics {
        private StochasticProcess1D process_;

        public ShortRateDynamics(StochasticProcess1D process) {
            process_ = process;
        }

        //! Compute state variable from short rate
        public abstract double variable(double t, double r);

        //! Compute short rate from state variable
        public abstract double shortRate(double t, double variable);

        //! Returns the risk-neutral dynamics of the state variable
        public final StochasticProcess1D process() {
            return process_;
        }
    }

    //! Recombining trinomial tree discretizing the state variable
    public static class ShortRateTree extends TreeLattice1D {
        private TrinomialTree tree_;
        private ShortRateDynamics dynamics_;

        private static class Helper implements Function {
            private int size_;
            private int i_;
            private Array statePrices_;
            private double discountBondPrice_;
            private TermStructureFittingParameter.NumericalImpl theta_;
            private ShortRateTree tree_;

            public Helper(int i, double discountBondPrice,
                          TermStructureFittingParameter.NumericalImpl theta,
                          ShortRateTree tree) {
                size_ = tree.size(i);
                i_ = i;
                statePrices_ = tree.statePrices(i);
                discountBondPrice_ = discountBondPrice;
                theta_ = theta;
                tree_ = tree;
                theta_.set(tree.timeGrid().get(i), 0.0);
            }

            @Override
            public double value(double theta) {
                double value = discountBondPrice_;
                theta_.change(theta);
                for (int j = 0; j < size_; j++) {
                    value -= statePrices_.get(j) * tree_.discount(i_, j);
                }
                return value;
            }
        }

        private double spread_;

        //! Plain tree build-up from short-rate dynamics
        public ShortRateTree(final TrinomialTree tree, ShortRateDynamics dynamics, final TimeGrid timeGrid) {
            super(timeGrid, tree.size(1));
            tree_ = tree;
            dynamics_ = dynamics;
            spread_ = 0.0;
        }

        //! Tree build-up + numerical fitting to term-structure
        public ShortRateTree(final TrinomialTree tree, ShortRateDynamics dynamics,
                             final TermStructureFittingParameter.NumericalImpl theta,
                             final TimeGrid timeGrid) {
            super(timeGrid, tree.size(1));
            tree_ = tree;
            dynamics_ = dynamics;
            spread_ = 0.0;
            theta.reset();
            double value = 1.0;
            double vMin = -100.0;
            double vMax = 100.0;
            for (int i=0; i<(timeGrid.size() - 1); i++) {
                double discountBond = theta.termStructure().getValue().discount(t_.get(i+1), false);
                Helper finder = new Helper(i, discountBond, theta, this);
                Brent s1d = new Brent();
                s1d.setMaxEvaluations(1000);
                value = s1d.solve(finder, 1e-7, value, vMin, vMax);
                // vMin = value - 1.0;
                // vMax = value + 1.0;
                theta.change(value);
            }
        }

        public int size(int i) {
            return tree_.size(i);
        }

        public double discount(int i, int index) {
            double x = tree_.underlying(i, index);
            double r = dynamics_.shortRate(timeGrid().get(i), x) + spread_;
            return Math.exp(-r * timeGrid().dt(i));
        }

        @Override
        public double underlying(int i, int index) {
            return tree_.underlying(i, index);
        }

        public int descendant(int i, int index, int branch) {
            return tree_.descendant(i, index, branch);
        }

        public double probability(int i, int index, int branch) {
            return tree_.probability(i, index, branch);
        }

        public void setSpread(int spread) {
            spread_ = spread;
        }
    }

    //! returns the short-rate dynamics
    public abstract ShortRateDynamics dynamics();

    //! Return by default a trinomial recombining tree
    @Override
    public Lattice tree(final TimeGrid grid) {
        TrinomialTree trinomial = new TrinomialTree(dynamics().process(), grid, false);
        return new ShortRateTree(trinomial, dynamics(), grid);
    }
}
