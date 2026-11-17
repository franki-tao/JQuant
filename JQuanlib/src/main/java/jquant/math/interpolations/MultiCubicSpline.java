package jquant.math.interpolations;

import java.util.List;

import static jquant.math.CommonUtil.QL_REQUIRE;

/**
 * N-dimensional cubic spline interpolation between discrete points.
 *
 * <p>Port of {@code ql/math/interpolations/multicubicspline.hpp}. The
 * official implementation uses template recursion over the dimension; here
 * the same recursion is carried out at run time. As in QuantLib, the values
 * are given as a table which is flattened in row-major order (the last
 * dimension varies fastest).
 *
 * <p>As in the official implementation, interpolation on the boundary
 * surface of the N-dimensional region is not supported.
 */
public class MultiCubicSpline {

    private final int n_;
    private final int[] dim_;
    private final int[] spacing_;
    private final List<List<Double>> grid_;
    private final double[] y_;
    private final double[] y2_;
    private final boolean[] allowExtrapolation_;

    private final double[][] d_;
    private final double[][] d2_;

    private final double[] a_, b_, a2_, b2_;
    private final int[] i_;

    // scratch buffers, one set per recursion level
    private final double[][] gathered_;
    private final double[][] splineY2_;
    private final double[][] splineV_;

    /**
     * @param grid the grid points for each dimension
     * @param y    the values on the grid, flattened in row-major order
     */
    public MultiCubicSpline(List<List<Double>> grid, double[] y) {
        this(grid, y, null);
    }

    /**
     * @param grid               the grid points for each dimension
     * @param y                  the values on the grid, flattened in row-major order
     * @param allowExtrapolation per-dimension extrapolation flags
     */
    public MultiCubicSpline(List<List<Double>> grid, double[] y,
                            boolean[] allowExtrapolation) {
        grid_ = grid;
        n_ = grid.size();
        QL_REQUIRE(n_ > 0, "no grid given");

        dim_ = new int[n_];
        spacing_ = new int[n_];
        long size = 1L;
        for (int j = 0; j < n_; ++j) {
            dim_[j] = grid.get(j).size();
            size *= dim_[j];
        }
        QL_REQUIRE(size == y.length,
                "data size (" + y.length + ") does not match grid size (" + size + ")");

        spacing_[n_ - 1] = 1;
        for (int j = n_ - 2; j >= 0; --j)
            spacing_[j] = spacing_[j + 1] * dim_[j + 1];

        y_ = y.clone();
        y2_ = new double[y_.length];

        if (allowExtrapolation == null || allowExtrapolation.length == 0) {
            allowExtrapolation_ = new boolean[n_];
        } else {
            QL_REQUIRE(allowExtrapolation.length == n_,
                    "extrapolation flags size does not match grid dimension");
            allowExtrapolation_ = allowExtrapolation.clone();
        }

        d_ = new double[n_][];
        d2_ = new double[n_][];
        setSharedIncrements();

        // precompute the second derivatives along the innermost dimension
        // for every combination of the outer coordinates, as done by the
        // recursive c_spline constructor
        final int last = dim_[n_ - 1];
        final double[] yy = new double[last];
        final double[] yy2 = new double[last];
        final double[] vv = new double[last];
        for (int offset = 0; offset < y_.length; offset += last) {
            System.arraycopy(y_, offset, yy, 0, last);
            baseCubicSpline(d_[n_ - 1], d2_[n_ - 1], yy, yy2, vv);
            System.arraycopy(yy2, 0, y2_, offset, last);
        }

        a_ = new double[n_];
        b_ = new double[n_];
        a2_ = new double[n_];
        b2_ = new double[n_];
        i_ = new int[n_];

        gathered_ = new double[Math.max(n_ - 1, 0)][];
        splineY2_ = new double[Math.max(n_ - 1, 0)][];
        splineV_ = new double[Math.max(n_ - 1, 0)][];
        for (int j = 0; j < n_ - 1; ++j) {
            gathered_[j] = new double[dim_[j]];
            splineY2_[j] = new double[dim_[j]];
            splineV_[j] = new double[dim_[j]];
        }
    }

    /**
     * Returns the interpolated value at the given point.
     */
    public double value(double[] x) {
        QL_REQUIRE(x.length == n_,
                "expected " + n_ + " coordinates, got " + x.length);
        setSharedCoefficients(x);
        return evaluate(0, x, 0);
    }

    private double evaluate(int level, double[] x, int offset) {
        if (level == n_ - 1) {
            final int i = i_[level];
            return baseCubicSplint(a_[level], b_[level], a2_[level], b2_[level],
                    i, y_, offset, y2_, offset, dim_[level]);
        }

        final int m = dim_[level];
        final double[] values = gathered_[level];
        for (int j = 0; j < m; ++j)
            values[j] = evaluate(level + 1, x, offset + j * spacing_[level]);

        final double[] values2 = splineY2_[level];
        final double[] scratch = splineV_[level];
        baseCubicSpline(d_[level], d2_[level], values, values2, scratch);

        return baseCubicSplint(a_[level], b_[level], a2_[level], b2_[level],
                i_[level], values, 0, values2, 0, m);
    }

    private static double baseCubicSplint(double a, double b, double a2, double b2,
                                          int i,
                                          double[] y, int yOffset,
                                          double[] y2, int y2Offset,
                                          int size) {
        // For a point on the upper boundary the official implementation sets
        // b = b2 = 0; avoid reading one element past the end in that case.
        if (i + 1 < size) {
            return a * y[yOffset + i] + b * y[yOffset + i + 1]
                    + a2 * y2[y2Offset + i] + b2 * y2[y2Offset + i + 1];
        }
        return a * y[yOffset + i] + a2 * y2[y2Offset + i];
    }

    private static void baseCubicSpline(double[] d, double[] d2,
                                        double[] y, double[] y2, double[] v) {
        final int dim = d.length;
        int j = 1, k = 2, l = 3;

        // w and u are the slots y2[dim] and y2[0] in the official code
        y2[dim] = (y[1] - y[0]) / d[0];
        y2[0] = (y[2] - y[1]) / d[1];
        y2[1] = -d[1] / d2[0];
        v[1] = 6.0 * (y2[0] - y2[dim]) / d2[0];

        for (; k < dim; y2[0] = y2[dim], j = k, k = l, ++l) {
            y2[dim] = (y[l] - y[k]) / d[k];
            y2[0] = (y2[0] - y2[dim]) * 6.0;

            double t = -y2[j];
            t *= d[j];
            t -= d2[j];
            y2[k] = d[k] / t;

            y2[0] += d[j] * v[j];
            v[k] = y2[0] / t;
        }
        y2[0] = 0.0;
        y2[dim] = 0.0;
        while (k != 0) {
            y2[k - 1] = y2[k - 1] * y2[l - 1] + v[k - 1];
            --k;
            --l;
        }
    }

    private void setSharedIncrements() {
        for (int j = 0; j < n_; ++j) {
            final List<Double> v = grid_.get(j);
            final int dim = v.size() - 1;
            int k = 0;
            if (dim > 2) {
                d_[j] = new double[dim];
                d2_[j] = new double[dim - 1];
                for (; k < dim; ++k) {
                    final double diff = v.get(k + 1) - v.get(k);
                    if (diff <= 0.0)
                        break;
                    d_[j][k] = diff;
                    if (k != 0)
                        d2_[j][k - 1] = 2.0 * (v.get(k + 1) - v.get(k - 1));
                }
            }
            QL_REQUIRE(dim >= 3,
                    "Dimension " + j + " : not enough points for interpolation");
            QL_REQUIRE(k >= dim, "Dimension " + j + " : invalid data");
        }
    }

    private void setSharedCoefficients(double[] x) {
        for (int j = 0; j < n_; ++j) {
            final List<Double> v = grid_.get(j);
            final int sz = v.size() - 1;
            int k = i_[j];
            final double xj = x[j];

            if (xj < v.get(0) || xj >= v.get(sz)) {
                QL_REQUIRE(allowExtrapolation_[j],
                        "Dimension " + j + ": extrapolation is not allowed.");
                a_[j] = 1.0;
                a2_[j] = 0.0;
                b_[j] = 0.0;
                b2_[j] = 0.0;
                k = xj < v.get(0) ? 0 : sz;
            } else {
                if (!(v.get(k) <= xj && xj < v.get(k + 1)))
                    k = upperBound(v, xj) - 1;

                final double h = v.get(k + 1) - v.get(k);
                a_[j] = (v.get(k + 1) - xj) / h;
                b_[j] = (xj - v.get(k)) / h;
                a2_[j] = (a_[j] * a_[j] * a_[j] - a_[j]) * h * h / 6.0;
                b2_[j] = (b_[j] * b_[j] * b_[j] - b_[j]) * h * h / 6.0;
            }
            i_[j] = k;
        }
    }

    private static int upperBound(List<Double> v, double x) {
        int lo = 0;
        int hi = v.size();
        while (lo < hi) {
            final int mid = (lo + hi) >>> 1;
            if (v.get(mid) <= x)
                lo = mid + 1;
            else
                hi = mid;
        }
        return lo;
    }
}
