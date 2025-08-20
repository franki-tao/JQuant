package math.matrixutilities;

import math.Array;
import math.CommonUtil;
import math.Matrix;
import math.Pair;

import java.util.*;

import static math.CommonUtil.QL_REQUIRE;
import static math.matrixutilities.TqrEigenDecomposition.EigenVectorCalculation.WithEigenVector;
import static math.matrixutilities.TqrEigenDecomposition.EigenVectorCalculation.WithoutEigenVector;

public class TqrEigenDecomposition {

    public enum EigenVectorCalculation {
        WithEigenVector,
        WithoutEigenVector,
        OnlyFirstRowEigenVector
    }

    public enum ShiftStrategy {
        NoShift,
        Overrelaxation,
        CloseEigenValue
    }

    private int iter_ = 0;
    private Array d_;
    private Matrix ev_;

    public TqrEigenDecomposition(Array diag,
                                 Array sub,
                                 EigenVectorCalculation calc,
                                 ShiftStrategy strategy) {
        this.d_ = diag;
        this.ev_ = new Matrix((calc == WithEigenVector) ? d_.size() :
                (calc == WithoutEigenVector) ? 0 : 1,
                d_.size(),
                0);
        int n = diag.size();
        QL_REQUIRE(n == sub.size() + 1, "Wrong dimensions");
        Array e = new Array(n, 0.0);
        for (int i = 0; i < sub.size(); i++) {
            e.set(i + 1, sub.get(i));
        }
        for (int i = 0; i < ev_.rows(); ++i) {
            ev_.set(i, i, 1d);
        }

        for (int k = n - 1; k >= 1; --k) {
            while (!offDiagIsZero(k, e)) {
                int l = k;
                while (--l > 0 && !offDiagIsZero(l, e)) ; // NOLINT(bugprone-inc-dec-in-conditions)
                iter_++;

                double q = d_.get(l);
                if (strategy != ShiftStrategy.NoShift) {
                    // calculated eigenvalue of 2x2 sub matrix of
                    // ( d_(k-1) e_(k) )
                    // (  e_(k)  d_(k) )
                    // which is closer to d_(k+1).
                    final double t1 = Math.sqrt(
                            0.25 * (d_.get(k) * d_.get(k) + d_.get(k - 1) * d_.get(k - 1))
                                    - 0.5 * d_.get(k - 1) * d_.get(k) + e.get(k) * e.get(k));
                    final double t2 = 0.5 * (d_.get(k) + d_.get(k - 1));

                    final double lambda =
                            (Math.abs(t2 + t1 - d_.get(k)) < Math.abs(t2 - t1 - d_.get(k))) ?
                                    (double) (t2 + t1) : (double) (t2 - t1);

                    if (strategy == ShiftStrategy.CloseEigenValue) {
                        q -= lambda;
                    } else {
                        q -= ((k == n - 1) ? 1.25 : 1.0) * lambda;
                    }
                }

                // the QR transformation
                double sine = 1.0;
                double cosine = 1.0;
                double u = 0.0;

                boolean recoverUnderflow = false;
                for (int i = l + 1; i <= k && !recoverUnderflow; ++i) {
                    final double h = cosine * e.get(i);
                    final double p = sine * e.get(i);

                    e.set(i - 1, Math.sqrt(p * p + q * q));
                    if (e.get(i - 1) != 0.0) {
                        sine = p / e.get(i - 1);
                        cosine = q / e.get(i - 1);

                        final double g = d_.get(i - 1) - u;
                        final double t = (d_.get(i) - g) * sine + 2 * cosine * h;

                        u = sine * t;
                        d_.set(i - 1, g + u);
                        q = cosine * t - h;

                        for (int j = 0; j < ev_.rows(); ++j) {
                            final double tmp = ev_.get(j, i - 1);
                            ev_.set(j, i - 1, sine * ev_.get(j, i) + cosine * tmp);
                            ev_.set(j, i, cosine * ev_.get(j, i) - sine * tmp);
                        }
                    } else {
                        // recover from underflow
                        d_.set(i - 1, d_.get(i - 1) - u);
                        e.set(l, 0);
                        recoverUnderflow = true;
                    }
                }

                if (!recoverUnderflow) {
                    d_.set(k, d_.get(k) - u);
                    e.set(k, q);
                    e.set(l, 0);
                }
            }
        }

        // sort (eigenvalues, eigenvectors),
        // code taken from symmetricSchureDecomposition.cpp
        List<Pair> temp = CommonUtil.ArrayInit(n);
        List<Double> eigenVector = CommonUtil.ArrayInit(ev_.rows());
        for (int i = 0; i < n; i++) {
            if (ev_.rows() > 0) {
                for (int j = 0; j < ev_.rows(); j++) {
                    eigenVector.set(j, ev_.get(j, i));
                }
            }
            temp.set(i, new Pair(d_.get(i), CommonUtil.clone(eigenVector)));
        }
        temp.sort(Comparator.comparingDouble(Pair::getFirst).reversed());
        // first element is positive
        for (int i = 0; i < n; i++) {
            d_.set(i, temp.get(i).getFirst());
            double sign = 1.0;
            if (ev_.rows() > 0 && temp.get(i).getSecond().get(0) < 0.0)
                sign = -1.0;
            for (int j = 0; j < ev_.rows(); ++j) {
                ev_.set(j, i, sign * temp.get(i).getSecond().get(j));
            }
        }
    }

    public Array eigenvalues() {
        return this.d_;
    }

    public Matrix eigenvectors() {
        return this.ev_;
    }

    public int iterations() {
        return this.iter_;
    }

    private boolean offDiagIsZero(int k, Array e) {
        return Math.abs(d_.get(k - 1)) + Math.abs(d_.get(k))
                == Math.abs(d_.get(k - 1)) + Math.abs(d_.get(k)) + Math.abs(e.get(k));
    }


}
