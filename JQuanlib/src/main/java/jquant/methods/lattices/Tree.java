package jquant.methods.lattices;

import jquant.patterns.CuriouslyRecurringTemplate;

//! %Tree approximating a single-factor diffusion
/*! Derived classes must implement the following interface:
    \code
    public:
      Real underlying(Size i, Size index) const;
      Size size(Size i) const;
      Size descendant(Size i, Size index, Size branch) const;
      Real probability(Size i, Size index, Size branch) const;
    \endcode
    and provide a public enumeration
    \code
    enum { branches = N };
    \endcode
    where N is a suitable constant (2 for binomial, 3 for trinomial...)

    \ingroup lattices
*/
public abstract class Tree<T extends Tree<T>> extends CuriouslyRecurringTemplate<T> {
    private final int columns;

    protected Tree(int columns) {
        this.columns = columns;
    }

    public int getColumns() {
        return columns;
    }
}
