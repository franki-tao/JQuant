package jquant.methods.lattices;

import jquant.TimeGrid;
import jquant.math.Array;
import jquant.methods.lattices.impl.TreeLatticeImpl;

//! Simple binomial lattice approximating the Black-Scholes model
/*! \ingroup lattices */
public class BlackScholesLattice extends TreeLattice1D {
    protected double riskFreeRate_;
    protected double dt_;
    protected double discount_;
    protected double pd_, pu_;

    public BlackScholesLattice(TreeLatticeImpl tree,
                               double riskFreeRate,
                               double end,
                               int steps) {
        super(new TimeGrid(end, steps), 2);
        setImpl_(tree);
        riskFreeRate_ = riskFreeRate;
        dt_ = end / steps;
        discount_ = Math.exp(-riskFreeRate * (dt_));
        pd_ = tree.probability(0, 0, 0);
        pu_ = tree.probability(0, 0, 1);
    }

    public double riskFreeRate() {
        return riskFreeRate_;
    }

    public double dt() {
        return dt_;
    }

    public int size(int i) {
        return impl_.size(i);
    }

    public double discount(int i, int j) {
        return discount_;
    }

    public void stepback(int i, Array values, Array newValues) {
        for (int j = 0; j < size(i); j++) {
            newValues.set(j, (pd_ * values.get(j) + pu_ * values.get(j + 1)) * discount_);
        }
    }

    public int descendant(int i, int index, int branch) {
        return impl_.descendant(i, index, branch);
    }

    public double probability(int i, int index, int branch) {
        return impl_.probability(i, index, branch);
    }
}
