package jquant.math.matrixutilities;

import jquant.math.Array;
import jquant.math.CommonUtil;
import jquant.math.Matrix;

import java.util.Arrays;

import static jquant.math.MathUtils.QL_EPSILON;
import static jquant.math.matrixutilities.MatrixUtil.hypot;

public class SVD {
    private Matrix U_, V_;
    private Array s_;
    private int m_, n_;
    private boolean transpose_;

    public SVD(final Matrix M) {
        Matrix A = new Matrix(0, 0, 0);
        /* The implementation requires that rows > columns.
           If this is not the case, we decompose M^T instead.
           Swapping the resulting U and V gives the desired
           result for M as

           M^T = U S V^T           (decomposition of M^T)

           M = (U S V^T)^T         (transpose)

           M = (V^T^T S^T U^T)     ((AB)^T = B^T A^T)

           M = V S U^T             (idempotence of transposition,
                                    symmetry of diagonal matrix S)
        */
        if (M.rows() >= M.cols()) {
            A = M;
            transpose_ = false;
        } else {
            A = M.transpose();
            transpose_ = true;
        }

        m_ = A.rows();
        n_ = A.cols();

        // we're sure that m_ >= n_

        s_ = new Array(n_);
        U_ = new Matrix(m_, n_, 0.0);
        V_ = new Matrix(n_, n_, Double.NaN);
        Array e = new Array(n_);
        Array work = new Array(m_);
        int i, j, k;

        // Reduce A to bidiagonal form, storing the diagonal elements
        // in s and the super-diagonal elements in e.

        int nct = Math.min(m_ - 1, n_);
        int nrt = Math.max(0, n_ - 2);
        for (k = 0; k < Math.max(nct, nrt); k++) {
            if (k < nct) {

                // Compute the transformation for the k-th column and
                // place the k-th diagonal in s[k].
                // Compute 2-norm of k-th column without under/overflow.
                s_.set(k, 0);
                for (i = k; i < m_; i++) {
                    s_.set(k, hypot(s_.get(k), A.get(i, k)));
                }
                if (s_.get(k) != 0.0) {
                    if (A.get(k, k) < 0.0) {
                        s_.set(k, -s_.get(k));
                    }
                    for (i = k; i < m_; i++) {
                        A.multipyEq(i, k, 1.0 / s_.get(k));
                    }
                    A.substractEq(k, k, -1.0);
                }
                s_.set(k, -s_.get(k));
            }
            for (j = k + 1; j < n_; j++) {
                if ((k < nct) && (s_.get(k) != 0.0)) {

                    // Apply the transformation.

                    double t = 0;
                    for (i = k; i < m_; i++) {
                        t += A.get(i, k) * A.get(i, j);
                    }
                    t = -t / A.get(k, k);
                    for (i = k; i < m_; i++) {
                        A.substractEq(i, j, -t * A.get(i, k));
                    }
                }

                // Place the k-th row of A into e for the
                // subsequent calculation of the row transformation.
                e.set(j, A.get(k, j));
            }
            if (k < nct) {

                // Place the transformation in U for subsequent back
                // multiplication.

                for (i = k; i < m_; i++) {
                    U_.set(i, k, A.get(i, k));
                }
            }
            if (k < nrt) {

                // Compute the k-th row transformation and place the
                // k-th super-diagonal in e[k].
                // Compute 2-norm without under/overflow.
                e.set(k, 0);
                for (i = k + 1; i < n_; i++) {
                    e.set(k, hypot(e.get(k), e.get(i)));
                }
                if (e.get(k) != 0.0) {
                    if (e.get(k + 1) < 0.0) {
                        e.set(k, -e.get(k));
                    }
                    for (i = k + 1; i < n_; i++) {
                        e.multiplyEq(i, 1.0 / e.get(k));
                    }
                    e.addEq(k + 1, 1.0);
                }
                e.set(k, -e.get(k));
                if ((k + 1 < m_) && (e.get(k) != 0.0)) {

                    // Apply the transformation.

                    for (i = k + 1; i < m_; i++) {
                        work.set(i, 0d);
                    }
                    for (j = k + 1; j < n_; j++) {
                        for (i = k + 1; i < m_; i++) {
                            work.addEq(i, e.get(j) * A.get(i, j));
                        }
                    }
                    for (j = k + 1; j < n_; j++) {
                        double t = -e.get(j) / e.get(k + 1);
                        for (i = k + 1; i < m_; i++) {
                            A.addEq(i, j, t * work.get(i));
                        }
                    }
                }

                // Place the transformation in V for subsequent
                // back multiplication.

                for (i = k + 1; i < n_; i++) {
                    V_.set(i, k, e.get(i));
                }
            }
        }

        // Set up the final bidiagonal matrix or order n.

        if (nct < n_) {
            s_.set(nct, A.get(nct, nct));
        }
        if (nrt + 1 < n_) {
            e.set(nrt, A.get(nrt, n_ - 1));
        }
        e.set(n_ - 1, 0.0);

        // generate U

        for (j = nct; j < n_; j++) {
            for (i = 0; i < m_; i++) {
                U_.set(i, j, 0.0);
            }
            U_.set(j, j, 1.0);
        }
        for (k = nct - 1; k >= 0; --k) {
            if (s_.get(k) != 0.0) {
                for (j = k + 1; j < n_; ++j) {
                    double t = 0;
                    for (i = k; i < m_; i++) {
                        t += U_.get(i, k) * U_.get(i, j);
                    }
                    t = -t / U_.get(k, k);
                    for (i = k; i < m_; i++) {
                        U_.addEq(i, j, t * U_.get(i, k));
                    }
                }
                for (i = k; i < m_; i++) {
                    U_.multipyEq(i, k, -1);
                }
                U_.set(k, k, 1.0 + U_.get(k, k));
                for (i = 0; i < k - 1; i++) {
                    U_.set(i, k, 0.0);
                }
            } else {
                for (i = 0; i < m_; i++) {
                    U_.set(i, k, 0.0);
                }
                U_.set(k, k, 1.0);
            }
        }

        // generate V

        for (k = n_ - 1; k >= 0; --k) {
            if ((k < nrt) && (e.get(k) != 0.0)) {
                for (j = k + 1; j < n_; ++j) {
                    double t = 0;
                    for (i = k + 1; i < n_; i++) {
                        t += V_.get(i, k) * V_.get(i, j);
                    }
                    t = -t / V_.get(k + 1, k);
                    for (i = k + 1; i < n_; i++) {
                        V_.addEq(i, j, t * V_.get(i, k));
                    }
                }
            }
            for (i = 0; i < n_; i++) {
                V_.set(i, k, 0.0);
            }
            V_.set(k, k, 1.0);
        }

        // Main iteration loop for the singular values.

        int p = n_, pp = p - 1;
        int iter = 0;
        double eps = Math.pow(2.0, -52.0);
        while (p > 0) {
            int kase;
            // Here is where a test for too many iterations would go.

            // This section of the program inspects for
            // negligible elements in the s and e arrays.  On
            // completion the variables kase and k are set as follows.

            // kase = 1     if s(p) and e[k-1] are negligible and k<p
            // kase = 2     if s(k) is negligible and k<p
            // kase = 3     if e[k-1] is negligible, k<p, and
            //              s(k), ..., s(p) are not negligible (qr step).
            // kase = 4     if e(p-1) is negligible (convergence).

            for (k = p - 2; k >= -1; --k) {
                if (k == -1) {
                    break;
                }
                if (Math.abs(e.get(k)) <= eps * (Math.abs(s_.get(k)) +
                        Math.abs(s_.get(k + 1)))) {
                    e.set(k, 0.0);
                    break;
                }
            }
            if (k == p - 2) {
                kase = 4;
            } else {
                int ks;
                for (ks = p - 1; ks >= k; --ks) {
                    if (ks == k) {
                        break;
                    }
                    double t = (ks != p ? (Math.abs(e.get(ks))) : 0.0) +
                            (ks != k + 1 ? (Math.abs(e.get(ks - 1))) : 0.);
                    if (Math.abs(s_.get(ks)) <= eps * t) {
                        s_.set(ks, 0.0);
                        break;
                    }
                }
                if (ks == k) {
                    kase = 3;
                } else if (ks == p - 1) {
                    kase = 1;
                } else {
                    kase = 2;
                    k = ks;
                }
            }
            k++;

            // Perform the task indicated by kase.

            switch (kase) { // NOLINT(bugprone-switch-missing-default-case)

                // Deflate negligible s(p).

                case 1: {
                    double f = e.get(p - 2);
                    e.set(p - 2, 0.0);
                    for (j = p - 2; j >= k; --j) {
                        double t = hypot(s_.get(j), f);
                        double cs = s_.get(j) / t;
                        double sn = f / t;
                        s_.set(j, t);
                        if (j != k) {
                            f = -sn * e.get(j - 1);
                            e.set(j - 1, cs * e.get(j - 1));
                        }
                        for (i = 0; i < n_; i++) {
                            t = cs * V_.get(i, j) + sn * V_.get(i, p - 1);
                            V_.set(i, p - 1, -sn * V_.get(i, j) + cs * V_.get(i, p - 1));
                            V_.set(i, j, t);
                        }
                    }
                }
                break;

                // Split at negligible s(k).

                case 2: {
                    double f = e.get(k - 1);
                    e.set(k - 1, 0.0);
                    for (j = k; j < p; j++) {
                        double t = hypot(s_.get(j), f);
                        double cs = s_.get(j) / t;
                        double sn = f / t;
                        s_.set(j, t);
                        f = -sn * e.get(j);
                        e.set(j, cs * e.get(j));
                        for (i = 0; i < m_; i++) {
                            t = cs * U_.get(i, j) + sn * U_.get(i, k - 1);
                            U_.set(i, k - 1, -sn * U_.get(i, j) + cs * U_.get(i, k - 1));
                            U_.set(i, j, t);
                        }
                    }
                }
                break;

                // Perform one qr step.

                case 3: {

                    // Calculate the shift.
                    double scale = CommonUtil.maxVal(Arrays.asList(Math.abs(s_.get(p - 1)),
                            Math.abs(s_.get(p - 2)),
                            Math.abs(e.get(p - 2)),
                            Math.abs(s_.get(k)),
                            Math.abs(e.get(k))));
                    double sp = s_.get(p - 1) / scale;
                    double spm1 = s_.get(p - 2) / scale;
                    double epm1 = e.get(p - 2) / scale;
                    double sk = s_.get(k) / scale;
                    double ek = e.get(k) / scale;
                    double b = ((spm1 + sp) * (spm1 - sp) + epm1 * epm1) / 2.0;
                    double c = (sp * epm1) * (sp * epm1);
                    double shift = 0.0;
                    if ((b != 0.0) || (c != 0.0)) {
                        shift = Math.sqrt(b * b + c);
                        if (b < 0.0) {
                            shift = -shift;
                        }
                        shift = c / (b + shift);
                    }
                    double f = (sk + sp) * (sk - sp) + shift;
                    double g = sk * ek;

                    // Chase zeros.

                    for (j = k; j < p - 1; j++) {
                        double t = hypot(f, g);
                        double cs = f / t;
                        double sn = g / t;
                        if (j != k) {
                            e.set(j - 1, t);
                        }
                        f = cs * s_.get(j) + sn * e.get(j);
                        e.set(j, cs * e.get(j) - sn * s_.get(j));
                        g = sn * s_.get(j + 1);
                        s_.set(j + 1, cs * s_.get(j + 1));
                        for (i = 0; i < n_; i++) {
                            t = cs * V_.get(i, j) + sn * V_.get(i, j + 1);
                            V_.set(i, j + 1, -sn * V_.get(i, j) + cs * V_.get(i, j + 1));
                            V_.set(i, j, t);
                        }
                        t = hypot(f, g);
                        cs = f / t;
                        sn = g / t;
                        s_.set(j, t);
                        f = cs * e.get(j) + sn * s_.get(j + 1);
                        s_.set(j + 1, -sn * e.get(j) + cs * s_.get(j + 1));
                        g = sn * e.get(j + 1);
                        e.set(j + 1, cs * e.get(j + 1));
                        if (j < m_ - 1) {
                            for (i = 0; i < m_; i++) {
                                t = cs * U_.get(i, j) + sn * U_.get(i, j + 1);
                                U_.set(i, j + 1, -sn * U_.get(i, j) + cs * U_.get(i, j + 1));
                                U_.set(i, j, t);
                            }
                        }
                    }
                    e.set(p - 2, f);
                    iter = iter + 1;
                }
                break;

                // Convergence.

                case 4: {

                    // Make the singular values positive.

                    if (s_.get(k) <= 0.0) {
                        s_.set(k, s_.get(k) < 0.0 ? -s_.get(k) : 0.0);
                        for (i = 0; i <= pp; i++) {
                            V_.set(i, k, -V_.get(i, k));
                        }
                    }

                    // Order the singular values.

                    while (k < pp) {
                        if (s_.get(k) >= s_.get(k + 1)) {
                            break;
                        }
                        s_.swap(k, k + 1);
                        if (k < n_ - 1) {
                            for (i = 0; i < n_; i++) {
                                V_.swap(i, k, i, k + 1);
                            }
                        }
                        if (k < m_ - 1) {
                            for (i = 0; i < m_; i++) {
                                U_.swap(i, k, i, k + 1);
                            }
                        }
                        k++;
                    }
                    iter = 0;
                    --p;
                }
                break;
            }
        }
    }

    public final Matrix U() {
        return transpose_ ? V_ : U_;
    }

    public final Matrix V() {
        return transpose_ ? U_ : V_;
    }

    public final Array singularValues() {
        return s_;
    }

    public Matrix S() {
        Matrix S = new Matrix(n_,n_,Double.NaN);
        for (int i = 0; i < n_; i++) {
            for (int j = 0; j < n_; j++) {
                S.set(i,j,0.0);
            }
            S.set(i,i,s_.get(i));
        }
        return S;
    }

    public double norm2() {
        return s_.get(0);
    }

    public double cond() {
        return s_.get(0)/s_.get(n_-1);
    }

    public int rank() {
        double tol = m_*s_.get(0)* QL_EPSILON;
        int r = 0;
        for (int i=0; i<s_.size(); i++) {
            if(s_.get(i) > tol) {
                r++;
            }
        }
        return r;
    }

    public Array solveFor(final Array b) {
        Matrix W = new Matrix(n_, n_, 0.0);
        int numericalRank = this.rank();
        for (int i=0; i<numericalRank; i++)
            W.set(i,i,1.0/s_.get(i));
        Matrix inverse = V().multipy(W).multipy(U().transpose());
        return inverse.mutiply(b);
    }

    public static void main(String[] args) {
        System.out.println("=== 演示 SVD 分解 ===");

        // 示例 1: 分解一个 3x2 矩阵
        System.out.println("\n1. 矩阵分解示例:");
        double[][] values = {{1, 2}, {3, 4}, {5, 6}};
        Matrix M = new Matrix(values);
        System.out.println("原始矩阵 M:");
        System.out.println(M);

        SVD svd = new SVD(M);

        Matrix U = svd.U();
        Matrix S = svd.S();
        Matrix V = svd.V();

        System.out.println("分解结果:");
        System.out.println("U:");
        System.out.println(U);
        System.out.println("S (奇异值矩阵):");
        System.out.println(S);
        System.out.println("V:");
        System.out.println(V);

        System.out.println("奇异值: " + svd.singularValues());
        System.out.println("矩阵的秩: " + svd.rank());
        System.out.println("矩阵的 2-范数: " + svd.norm2());
        System.out.println("矩阵的条件数: " + svd.cond());

        // 验证 M ≈ U * S * V^T
        System.out.println("\n验证 M ≈ U * S * V^T:");
        Matrix reconstructedM = U.multipy(S).multipy(V.transpose());
        System.out.println("重构矩阵 U*S*V^T:");
        System.out.println(reconstructedM);
        // 你会看到重构的矩阵与原矩阵非常接近

        // 示例 2: 求解线性方程组 M * x = b
        System.out.println("\n2. 求解线性方程组示例 (最小二乘解):");
        // 使用上面的矩阵 M (3x2)
        // 由于 M 不是方阵，我们求最小二乘解
        double[] bValues = {1, 2, 3};
        Array b = new Array(bValues);
        System.out.println("方程组 M * x = b, 其中 b = " + b);

        Array x = svd.solveFor(b);
        System.out.println("最小二乘解 x = " + x);

        // 验证解的正确性: M * x 应该接近 b
        System.out.println("验证: M * x = " + M.mutiply(x));
        System.out.println("原始 b = " + b);
    }
}
