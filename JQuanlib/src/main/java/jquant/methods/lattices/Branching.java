package jquant.methods.lattices;


import jquant.math.CommonUtil;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/* Branching scheme for a trinomial node.  Each node has three
   descendants, with the middle branch linked to the node
   which is closest to the expectation of the variable. */
public class Branching {
    private List<Integer> k_;
    private List<List<Double>> probs_;
    private int kMin_, jMin_, kMax_, jMax_;

    public Branching() {
        probs_ = Arrays.asList(new ArrayList<>(), new ArrayList<>(), new ArrayList<>());

        kMin_ = Integer.MAX_VALUE;
        jMin_ = Integer.MAX_VALUE;
        kMax_ = Integer.MIN_VALUE;
        jMax_ = Integer.MIN_VALUE;
    }

    public int descendant(int index,
                          int branch) {
        return k_.get(index) - jMin_ - 1 + branch;
    }

    public double probability(int index, int branch) {
        return probs_.get(branch).get(index);
    }

    public int size() {
        return jMax_ - jMin_ + 1;
    }

    public int jMin() {
        return jMin_;
    }

    public int jMax() {
        return jMax_;
    }

    public void add(int k, double p1, double p2, double p3) {
        // store
        k_.add(k);
        probs_.get(0).add(p1);
        probs_.get(1).add(p2);
        probs_.get(2).add(p3);
        // maintain invariants
        kMin_ = Math.min(kMin_, k);
        jMin_ = kMin_ - 1;
        kMax_ = Math.max(kMax_, k);
        jMax_ = kMax_ + 1;
    }
}
