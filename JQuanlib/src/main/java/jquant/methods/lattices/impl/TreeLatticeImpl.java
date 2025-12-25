package jquant.methods.lattices.impl;

import jquant.math.Array;

public interface TreeLatticeImpl {
    int size(int index);
    double discount(int i, int j);
    int descendant(int i, int j, int l);
    double probability(int i, int j, int l);
    void stepback(int i, Array values, Array newValues);
    double underlying(int i, int j);
}
