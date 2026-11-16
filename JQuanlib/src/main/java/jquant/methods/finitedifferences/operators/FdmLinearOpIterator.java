package jquant.methods.finitedifferences.operators;

/**
 * Iterator over the coordinates of a finite-difference grid.
 *
 * <p>Port of {@code ql/methods/finitedifferences/operators/fdmlinearopiterator.hpp}.
 * As in QuantLib, iteration is expressed through a mutable iterator which is
 * incremented in place; {@link FdmLinearOpLayout#iterator()} wraps it.
 */
public class FdmLinearOpIterator {
    private int index_;
    private final int[] dim_;
    private final int[] coordinates_;

    public FdmLinearOpIterator(int index) {
        this.index_ = index;
        this.dim_ = new int[0];
        this.coordinates_ = new int[0];
    }

    public FdmLinearOpIterator(int[] dim) {
        this.index_ = 0;
        this.dim_ = dim.clone();
        this.coordinates_ = new int[dim_.length];
    }

    public FdmLinearOpIterator(int[] dim, int[] coordinates, int index) {
        this.index_ = index;
        this.dim_ = dim.clone();
        this.coordinates_ = coordinates.clone();
    }

    public void increment() {
        ++index_;
        for (int i = 0; i < dim_.length; ++i) {
            if (++coordinates_[i] == dim_[i]) {
                coordinates_[i] = 0;
            } else {
                break;
            }
        }
    }

    public int index() {
        return index_;
    }

    public int[] coordinates() {
        return coordinates_;
    }
}
