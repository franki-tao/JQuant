package jquant.methods.lattices;

import jquant.StochasticProcess1D;

//! Base class for equal probabilities binomial tree
/*! \ingroup lattices */
public abstract class EqualProbabilitiesBinomialTree<T extends EqualProbabilitiesBinomialTree<T>> extends BinomialTree<T> {

    protected double up;

    public EqualProbabilitiesBinomialTree(StochasticProcess1D process, double end, int steps) {
        super(process, end, steps);
    }

    /**
     * 计算第 i 步，第 index 个节点的标的资产价格
     */
    public double underlying(int i, int index) {
        // j 表示向上和向下跳跃的净差
        int j = 2 * index - i;
        return this.x0 * Math.exp(i * this.driftPerStep + j * this.up);
    }

    /**
     * 概率固定为 0.5
     */
    public double probability(int step, int index, int branch) {
        return 0.5;
    }
}
