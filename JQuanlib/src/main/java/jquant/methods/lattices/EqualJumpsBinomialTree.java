package jquant.methods.lattices;

import jquant.StochasticProcess1D;

public abstract class EqualJumpsBinomialTree<T extends EqualJumpsBinomialTree<T>> extends BinomialTree<T> {
    protected double dx_, pu_, pd_;

    public EqualJumpsBinomialTree(final StochasticProcess1D process, double end, int steps) {
        super(process, end, steps);
    }

    public double underlying(int i, int index) {
        int j = 2 * index - i;
        // exploiting equal jump and the x0_ tree centering
        return this.x0 * Math.exp(j * dx_);
    }

    public double probability(int x, int y, int branch) {
        return (branch == 1 ? pu_ : pd_);
    }
}
