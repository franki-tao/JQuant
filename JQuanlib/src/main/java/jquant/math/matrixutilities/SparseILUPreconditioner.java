package jquant.math.matrixutilities;

import jquant.math.Array;
import jquant.math.CommonUtil;
import jquant.math.matrixutilities.impl.SparseMatrix;

import java.util.*;

import static jquant.math.CommonUtil.QL_REQUIRE;
import static jquant.math.MathUtils.QL_EPSILON;

/*! References:
    Saad, Yousef. 1996, Iterative methods for sparse linear systems,
    http://www-users.cs.umn.edu/~saad/books.html
*/
public class SparseILUPreconditioner {
    private SparseMatrix L_, U_;
    private List<Integer> lBands_, uBands_;

    // default lfil = 1
    public SparseILUPreconditioner(final SparseMatrix A, int lfil) {
        L_ = new SparseMatrix(A.size1(), A.size2());
        U_ = new SparseMatrix(A.size1(), A.size2());

        QL_REQUIRE(A.size1() == A.size2(),
                "sparse ILU preconditioner works only with square matrices");

        for (int i = 0; i < L_.size1(); ++i)
            L_.set(i, i, 1d);

        final int n = A.size1();
        Set<Integer> lBandSet = new TreeSet<>();
        Set<Integer> uBandSet = new TreeSet<>();

        SparseMatrix levs = new SparseMatrix(n, n);
        int lfilp = lfil + 1;

        for (int ii = 0; ii < n; ++ii) {
            Array w = new Array(n);
            for (int k = 0; k < n; ++k) {
                w.set(k, A.get(ii, k));
            }

            List<Integer> levii = CommonUtil.ArrayInit(n, 0);
            for (int i = 0; i < n; ++i) {
                if (w.get(i) > QL_EPSILON || w.get(i) < -1.0 * QL_EPSILON)
                    levii.set(i, 1);
            }
            int jj = -1;
            while (jj < ii) {
                for (int k = jj + 1; k < n; ++k) {
                    if (levii.get(k) != 0) {
                        jj = k;
                        break;
                    }
                }
                if (jj >= ii) {
                    break;
                }
                int jlev = levii.get(jj);
                if (jlev <= lfilp) {
                    List<Integer> nonZeros = new ArrayList<>();
                    List<Double> nonZeroEntries = new ArrayList<>();
                    final double entry = U_.get(jj, jj);
                    if (entry > QL_EPSILON || entry < -1.0 * QL_EPSILON) {
                        nonZeros.add(jj);
                        nonZeroEntries.add(entry);
                    }
                    for (int val : uBandSet) {
                        final double entry1 = U_.get(jj, jj + val);
                        if (entry1 > QL_EPSILON || entry1 < -1.0 * QL_EPSILON) {
                            nonZeros.add(jj + val);
                            nonZeroEntries.add(entry1);
                        }
                    }
                    double fact = w.get(jj);
                    if (!nonZeroEntries.isEmpty()) {
                        fact /= nonZeroEntries.get(0);
                    }
                    for (int k = 0; k < nonZeros.size(); ++k) {
                        final int j = nonZeros.get(k);
                        final int temp = (int) (levs.get(jj, j) + jlev);
                        if (levii.get(j) == 0) {
                            if (temp <= lfilp) {
                                w.set(j, -fact * nonZeroEntries.get(k));
                                levii.set(j, temp);
                            }
                        } else {
                            w.set(j, -fact * nonZeroEntries.get(k));
                            levii.set(j, Math.min(temp, levii.get(j)));
                        }
                    }
                    w.set(jj, fact);
                }
            }
            List<Integer> wNonZeros = new ArrayList<>();
            List<Double> wNonZeroEntries = new ArrayList<>();
            for (int i = 0; i < w.size(); ++i) {
                final double entry = w.get(i);
                if (entry > QL_EPSILON || entry < -1.0 * QL_EPSILON) {
                    wNonZeros.add(i);
                    wNonZeroEntries.add(entry);
                }
            }
            List<Integer> leviiNonZeroEntries = new ArrayList<>();
            for (int entry : levii) {
                if (entry > QL_EPSILON || entry < -1.0 * QL_EPSILON) {
                    leviiNonZeroEntries.add(entry);
                }
            }
            for (int k = 0; k < wNonZeros.size(); ++k) {
                int j = wNonZeros.get(k);
                if (j < ii) {
                    L_.set(ii, j, wNonZeroEntries.get(k));
                    lBandSet.add(ii - j);
                } else {
                    U_.set(ii, j, wNonZeroEntries.get(k));
                    levs.set(ii, j, leviiNonZeroEntries.get(k));
                    if (j - ii > 0) {
                        uBandSet.add(j - ii);
                    }
                }
            }
        }
        lBands_ = new ArrayList<>();
        uBands_ = new ArrayList<>();
        lBands_.addAll(lBandSet);
        uBands_.addAll(uBandSet);
    }

    public final SparseMatrix L() {
        return L_;
    }

    public final SparseMatrix U() {
        return U_;
    }

    public Array apply(final Array b) {
        return backwardSolve(forwardSolve(b));
    }

    private Array forwardSolve(final Array b) {
        int n = b.size();
        Array y = new Array(n, 0.0);
        y.set(0, b.get(0) / L_.get(0, 0));
        for (int i = 1; i <= n - 1; ++i) {
            y.set(i, b.get(i) / L_.get(i, i));
            // y[i] = b[i]/L_(i,i);
            for (int j = lBands_.size() - 1;
                 j >= 0 && i - lBands_.get(j) <= i - 1; --j) {
                final int k = i - lBands_.get(j);
                if (k >= 0)
                    y.subtractEq(i, L_.get(i, k) * y.get(k) / L_.get(i, i));
                // y[i]-=L_(i,k)*y[k]/L_(i,i);
            }
        }
        return y;
    }

    private Array backwardSolve(final Array y) {
        int n = y.size();
        Array x = new Array(n, 0.0);
        x.set(n - 1, y.get(n - 1) / U_.get(n - 1, n - 1));
        // x[n-1] = y[n-1]/U_(n-1,n-1);
        for (int i = n - 2; i >= 0; --i) {
            x.set(i, y.get(i) / U_.get(i, i));
            // x[i] = y[i]/U_(i,i);
            for (int j = 0; j < uBands_.size() && i + uBands_.get(j) <= n - 1; ++j) {
                // x[i] -= U_(i,i+uBands_[j])*x[i+uBands_[j]]/U_(i,i);
                x.subtractEq(i, U_.get(i, i + uBands_.get(j)) * x.get(i + uBands_.get(j)) / U_.get(i, i));
            }
        }
        return x;
    }
}
