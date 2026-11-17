package jquant.methods.finitedifferences.operators;

import java.util.Iterator;
import java.util.NoSuchElementException;

/**
 * Memory layout of a finite-difference linear operator.
 *
 * <p>Port of {@code ql/methods/finitedifferences/operators/fdmlinearoplayout.{hpp,cpp}}.
 */
public class FdmLinearOpLayout implements Iterable<FdmLinearOpIterator> {
    private final int size_;
    private final int[] dim_;
    private final int[] spacing_;

    public FdmLinearOpLayout(int[] dim) {
        this.dim_ = dim.clone();
        this.spacing_ = new int[dim_.length];
        spacing_[0] = 1;
        for (int i = 1; i < dim_.length; ++i) {
            spacing_[i] = spacing_[i - 1] * dim_[i - 1];
        }
        size_ = spacing_[dim_.length - 1] * dim_[dim_.length - 1];
    }

    @Override
    public Iterator<FdmLinearOpIterator> iterator() {
        return new Iterator<>() {
            private final FdmLinearOpIterator iterator = new FdmLinearOpIterator(dim_);
            private int count = 0;

            @Override
            public boolean hasNext() {
                return count < size_;
            }

            @Override
            public FdmLinearOpIterator next() {
                if (!hasNext()) {
                    throw new NoSuchElementException();
                }
                final FdmLinearOpIterator result = iterator;
                ++count;
                if (count < size_) {
                    iterator.increment();
                }
                return result;
            }
        };
    }

    public int[] dim() {
        return dim_;
    }

    public int[] spacing() {
        return spacing_;
    }

    public int size() {
        return size_;
    }

    public int index(int[] coordinates) {
        int result = 0;
        for (int i = 0; i < coordinates.length; ++i) {
            result += coordinates[i] * spacing_[i];
        }
        return result;
    }

    public int neighbourhood(FdmLinearOpIterator iterator, int i, int offset) {
        final int myIndex = iterator.index() - iterator.coordinates()[i] * spacing_[i];

        int coorOffset = iterator.coordinates()[i] + offset;
        if (coorOffset < 0) {
            coorOffset = -coorOffset;
        } else if (coorOffset >= dim_[i]) {
            coorOffset = 2 * (dim_[i] - 1) - coorOffset;
        }
        return myIndex + coorOffset * spacing_[i];
    }

    public int neighbourhood(FdmLinearOpIterator iterator,
                             int i1, int offset1,
                             int i2, int offset2) {
        int myIndex = iterator.index()
                - iterator.coordinates()[i1] * spacing_[i1]
                - iterator.coordinates()[i2] * spacing_[i2];

        int coorOffset1 = iterator.coordinates()[i1] + offset1;
        if (coorOffset1 < 0) {
            coorOffset1 = -coorOffset1;
        } else if (coorOffset1 >= dim_[i1]) {
            coorOffset1 = 2 * (dim_[i1] - 1) - coorOffset1;
        }

        int coorOffset2 = iterator.coordinates()[i2] + offset2;
        if (coorOffset2 < 0) {
            coorOffset2 = -coorOffset2;
        } else if (coorOffset2 >= dim_[i2]) {
            coorOffset2 = 2 * (dim_[i2] - 1) - coorOffset2;
        }

        return myIndex + coorOffset1 * spacing_[i1]
                + coorOffset2 * spacing_[i2];
    }

    public FdmLinearOpIterator iterNeighbourhood(FdmLinearOpIterator iterator,
                                                 int i, int offset) {
        final int[] coordinates = iterator.coordinates().clone();

        int coorOffset = coordinates[i] + offset;
        if (coorOffset < 0) {
            coorOffset = -coorOffset;
        } else if (coorOffset >= dim_[i]) {
            coorOffset = 2 * (dim_[i] - 1) - coorOffset;
        }
        coordinates[i] = coorOffset;

        return new FdmLinearOpIterator(dim_, coordinates, index(coordinates));
    }
}
