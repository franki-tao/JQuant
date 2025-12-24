package jquant.methods.lattices;

import jquant.StochasticProcess1D;

//! Binomial tree base class
/*! \ingroup lattices */
public abstract class BinomialTree<T extends BinomialTree<T>> extends Tree<T> {
    public static final int BRANCHES = 2;

    protected double x0;
    protected double driftPerStep;
    protected double dt;

    public BinomialTree(StochasticProcess1D process, double end, int steps) {
        super(steps + 1);
        this.x0 = process.x0();
        this.dt = end / steps;
        // 假设 process 有 drift 方法
        this.driftPerStep = process.drift(0.0, x0) * dt;
    }

    public int size(int i) {
        return i + 1;
    }

    public int descendant(int level, int index, int branch) {
        return index + branch;
    }
}
