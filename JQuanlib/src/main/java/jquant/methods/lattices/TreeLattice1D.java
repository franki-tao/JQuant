package jquant.methods.lattices;

import jquant.TimeGrid;
import jquant.math.Array;

//! One-dimensional tree-based lattice.
/*! Derived classes must implement the following interface:
    \code
    Real underlying(Size i, Size index) const;
    \endcode

    \ingroup lattices */
public class TreeLattice1D extends TreeLattice {
    public TreeLattice1D(TimeGrid timeGrid, int n) {
        super(timeGrid, n);
    }

    @Override
    public Array grid(double t) {
        int i = this.timeGrid().index(t);
        Array grid = new Array(impl_.size(i));
        for (int j = 0; j < grid.size(); j++) {
            grid.set(j, impl_.underlying(i, j));
        }
        return grid;
    }

    public double underlying(int i, int index) {
        return impl_.underlying(i, index);
    }
}
