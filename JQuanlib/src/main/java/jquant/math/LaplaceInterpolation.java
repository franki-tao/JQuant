package jquant.math;

import jquant.math.matrixutilities.BiCGstab;
import jquant.methods.finitedifferences.meshers.Fdm1dMesher;
import jquant.methods.finitedifferences.meshers.FdmMesherComposite;
import jquant.methods.finitedifferences.meshers.Predefined1dMesher;
import jquant.methods.finitedifferences.operators.FdmLinearOpIterator;
import jquant.methods.finitedifferences.operators.FdmLinearOpLayout;
import jquant.methods.finitedifferences.operators.SecondDerivativeOp;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import static jquant.math.CommonUtil.QL_REQUIRE;
import static jquant.math.MathUtils.NULL_REAL;

/**
 * Reconstruction of missing values using Laplace interpolation.
 *
 * <p>Port of {@code ql/experimental/math/laplaceinterpolation.{hpp,cpp}}.
 * An arbitrary number of dimensions {@code n >= 1} and non-equidistant grids
 * are supported. For {@code n = 1} the method is identical to linear
 * interpolation with flat extrapolation. Reference: Numerical Recipes,
 * 3rd edition, ch. 3.8.
 *
 * <p>Missing values are encoded as {@link MathUtils#NULL_REAL}.
 */
public class LaplaceInterpolation {

    private final Function<int[], Double> y_;
    private final List<List<Double>> x_;
    private final boolean[] coordinateIncluded_;
    private final int numberOfCoordinatesIncluded_;
    private final FdmLinearOpLayout layout_;
    private final double[] interpolatedValues_;

    public LaplaceInterpolation(Function<int[], Double> y, List<List<Double>> x) {
        this(y, x, 1E-6, 10);
    }

    public LaplaceInterpolation(Function<int[], Double> y, List<List<Double>> x,
                                double relTol, int maxIterMultiplier) {
        y_ = y;
        x_ = x;

        // set up the mesher

        final List<Integer> dim = new ArrayList<>();
        coordinateIncluded_ = new boolean[x_.size()];
        for (int i = 0; i < x_.size(); ++i) {
            coordinateIncluded_[i] = x_.get(i).size() > 1;
            if (coordinateIncluded_[i])
                dim.add(x_.get(i).size());
        }

        numberOfCoordinatesIncluded_ = dim.size();

        if (numberOfCoordinatesIncluded_ == 0) {
            layout_ = null;
            interpolatedValues_ = null;
            return;
        }

        QL_REQUIRE(!dim.isEmpty(), "LaplaceInterpolation: singular point or no points given");

        final int[] dimArray = new int[dim.size()];
        for (int i = 0; i < dim.size(); ++i)
            dimArray[i] = dim.get(i);
        layout_ = new FdmLinearOpLayout(dimArray);

        final List<Fdm1dMesher> meshers = new ArrayList<>();
        for (List<Double> i : x_)
            if (i.size() > 1)
                meshers.add(new Predefined1dMesher(i));

        final FdmMesherComposite mesher = new FdmMesherComposite(layout_, meshers);

        // set up the Laplace operator and convert it to matrix

        final List<SecondDerivativeOp> map = new ArrayList<>();
        for (int direction = 0; direction < layout_.dim().length; ++direction)
            if (layout_.dim()[direction] > 1)
                map.add(new SecondDerivativeOp(direction, mesher));

        Matrix op = null;
        for (SecondDerivativeOp m : map) {
            final Matrix mMatrix = m.toMatrix();
            op = (op == null) ? mMatrix : op.add(mMatrix);
        }

        // set up the linear system to solve

        final int N = layout_.size();

        final double[][] g = new double[N][N];
        final double[] rhs = new double[N];
        final double[] guess = new double[N];
        double guessTmp = 0.0;

        int count = 0;
        final double[] cornerH = new double[dimArray.length];
        final int[] cornerNeighbourIndex = new int[dimArray.length];
        for (FdmLinearOpIterator pos : layout_) {
            final int[] coord = pos.coordinates();
            final double val = y_.apply(numberOfCoordinatesIncluded_ == x_.size()
                    ? coord : fullCoordinates(coord));

            if (val == NULL_REAL) {
                boolean isCorner = true;
                for (int d = 0; d < dimArray.length && isCorner; ++d) {
                    if (coord[d] == 0) {
                        cornerH[d] = meshers.get(d).dplus(0);
                        cornerNeighbourIndex[d] = 1;
                    } else if (coord[d] == layout_.dim()[d] - 1) {
                        cornerH[d] = meshers.get(d).dminus(dimArray[d] - 1);
                        cornerNeighbourIndex[d] = dimArray[d] - 2;
                    } else {
                        isCorner = false;
                    }
                }
                if (isCorner) {
                    // handling of the "corners", all second derivatives are zero
                    // in the operator; this directly generalizes
                    // Numerical Recipes, 3rd ed, eq. 3.8.6
                    double sumCornerH = 0.0;
                    for (double h : cornerH)
                        sumCornerH += h;
                    for (int j = 0; j < dimArray.length; ++j) {
                        final int[] coordJ = coord.clone();
                        coordJ[j] = cornerNeighbourIndex[j];
                        double weight = 0.0;
                        for (int i = 0; i < dimArray.length; ++i) {
                            if (i != j)
                                weight += cornerH[i];
                        }
                        weight = dimArray.length == 1 ? 1.0 : weight / sumCornerH;
                        g[count][layout_.index(coordJ)] = -weight;
                    }
                    g[count][count] = 1.0;
                } else {
                    // point with at least one dimension with a non-trivial
                    // second derivative
                    for (int col = 0; col < N; ++col)
                        g[count][col] = op.get(count, col);
                }
                rhs[count] = 0.0;
                guess[count] = guessTmp;
            } else {
                g[count][count] = 1.0;
                rhs[count] = val;
                guess[count] = guessTmp = val;
            }
            ++count;
        }

        final FunctionN fA = v -> {
            final Array result = new Array(N);
            for (int i = 0; i < N; ++i) {
                double sum = 0.0;
                for (int j = 0; j < N; ++j)
                    sum += g[i][j] * v.get(j);
                result.set(i, sum);
            }
            return result;
        };

        interpolatedValues_ = new BiCGstab(fA, maxIterMultiplier * N, relTol, null)
                .solve(new Array(rhs), new Array(guess)).x.toArray();
    }

    private int[] projectedCoordinates(int[] coordinates) {
        final List<Integer> tmp = new ArrayList<>();
        for (int i = 0; i < coordinates.length; ++i) {
            if (coordinateIncluded_[i])
                tmp.add(coordinates[i]);
        }
        final int[] result = new int[tmp.size()];
        for (int i = 0; i < tmp.size(); ++i)
            result[i] = tmp.get(i);
        return result;
    }

    private int[] fullCoordinates(int[] projectedCoordinates) {
        final int[] tmp = new int[coordinateIncluded_.length];
        for (int i = 0, count = 0; i < coordinateIncluded_.length; ++i) {
            if (coordinateIncluded_[i])
                tmp[i] = projectedCoordinates[count++];
        }
        return tmp;
    }

    /**
     * Returns the interpolated value at the given coordinates.
     */
    public double value(int[] coordinates) {
        QL_REQUIRE(coordinates.length == x_.size(),
                "LaplaceInterpolation::operator(): expected " + x_.size()
                        + " coordinates, got " + coordinates.length);
        if (numberOfCoordinatesIncluded_ == 0) {
            final double val = y_.apply(coordinates);
            return val == NULL_REAL ? 0.0 : val;
        }
        return interpolatedValues_[layout_.index(numberOfCoordinatesIncluded_ == x_.size()
                ? coordinates : projectedCoordinates(coordinates))];
    }

    /**
     * Convenience function that Laplace-interpolates null values in a matrix.
     * If the x or y grid or both are not given, an equidistant grid is assumed.
     */
    public static void laplaceInterpolation(Matrix A) {
        laplaceInterpolation(A, new double[0], new double[0], 1E-6, 10);
    }

    public static void laplaceInterpolation(Matrix A, double[] x, double[] y) {
        laplaceInterpolation(A, x, y, 1E-6, 10);
    }

    public static void laplaceInterpolation(Matrix A, double[] x, double[] y,
                                            double relTol, int maxIterMultiplier) {
        final List<List<Double>> tmp = new ArrayList<>();
        tmp.add(boxed(y));
        tmp.add(boxed(x));

        if (y.length == 0) {
            final List<Double> grid = new ArrayList<>();
            for (int i = 0; i < A.rows(); ++i)
                grid.add((double) i);
            tmp.set(0, grid);
        }

        if (x.length == 0) {
            final List<Double> grid = new ArrayList<>();
            for (int j = 0; j < A.cols(); ++j)
                grid.add((double) j);
            tmp.set(1, grid);
        }

        final LaplaceInterpolation interpolation = new LaplaceInterpolation(
                coordinates -> A.get(coordinates[0], coordinates[1]),
                tmp, relTol, maxIterMultiplier);

        for (int i = 0; i < A.rows(); ++i) {
            for (int j = 0; j < A.cols(); ++j) {
                if (A.get(i, j) == NULL_REAL)
                    A.set(i, j, interpolation.value(new int[]{i, j}));
            }
        }
    }

    private static List<Double> boxed(double[] values) {
        final List<Double> result = new ArrayList<>(values.length);
        for (double v : values)
            result.add(v);
        return result;
    }
}
