package jquant.methods.lattices;

import jquant.math.Array;
import jquant.math.Matrix;

import static jquant.math.CommonUtil.QL_FAIL;

//! Two-dimensional tree-based lattice.
/*! This lattice is based on two trinomial trees and primarily used
    for the G2 short-rate model.

    \ingroup lattices
*/
public class TreeLattice2D extends TreeLattice {
    protected TrinomialTree tree1_, tree2_;
    private Matrix m_;
    private double rho_;

    public TreeLattice2D(TrinomialTree tree1, TrinomialTree tree2, double correlation) {
        super(tree1.timeGrid(), 9);
        tree1_ = tree1;
        tree2_ = tree2;
        m_ = new Matrix(3, 3);
        rho_ = correlation;

        if (correlation < 0.0) {
            m_.set(0, 0, -1.0);
            m_.set(0, 1, -4.0);
            m_.set(0, 2, 5.0);
            m_.set(1, 0, -4.0);
            m_.set(1, 1, 8.0);
            m_.set(1, 2, -4.0);
            m_.set(2, 0, 5.0);
            m_.set(2, 1, -4.0);
            m_.set(2, 2, -1.0);
        } else {
            m_.set(0, 0, 5.0);
            m_.set(0, 1, -4.0);
            m_.set(0, 2, -1.0);
            m_.set(1, 0, -4.0);
            m_.set(1, 1, 8.0);
            m_.set(1, 2, -4.0);
            m_.set(2, 0, -1.0);
            m_.set(2, 1, -4.0);
            m_.set(2, 2, 5.0);
        }
    }

    public int size(int i) {
        return tree1_.size(i) * tree2_.size(i);
    }

    public int descendant(int i, int index,
                          int branch) {
        int modulo = tree1_.size(i);
        int index1 = index % modulo;
        int index2 = index / modulo;
        int branch1 = branch % 3;
        int branch2 = branch / 3;
        modulo = tree1_.size(i + 1);
        return tree1_.descendant(i, index1, branch1) + tree2_.descendant(i, index2, branch2) * modulo;
    }

    public double probability(int i, int index,
                              int branch) {
        int modulo = tree1_.size(i);

        int index1 = index % modulo;
        int index2 = index / modulo;
        int branch1 = branch % 3;
        int branch2 = branch / 3;

        double prob1 = tree1_.probability(i, index1, branch1);
        double prob2 = tree2_.probability(i, index2, branch2);
        // does the 36 below depend on T::branches?
        return prob1 * prob2 + rho_ * (m_.get(branch1, branch2)) / 36.0;
    }

    @Override
    public Array grid(double t) {
        QL_FAIL("not implemented");
        return null;
    }
}
