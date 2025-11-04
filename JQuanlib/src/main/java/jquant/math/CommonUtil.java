package jquant.math;

import jquant.math.optimization.impl.QrFacParams;
import jquant.math.optimization.impl.Qrsolv;
import jquant.math.optimization.impl.QrsolvParams;
import org.apache.commons.math3.linear.Array2DRowRealMatrix;
import org.apache.commons.math3.linear.ArrayRealVector;
import org.apache.commons.math3.linear.RealMatrix;
import org.apache.commons.math3.linear.RealVector;

import java.util.*;

import static jquant.math.MathUtils.QL_EPSILON;
import static jquant.math.optimization.impl.MinPack.qrfac;

public class CommonUtil {

    public static void QL_REQUIRE(boolean flag, String error_info) {
        if (!flag) {
            throw new IllegalArgumentException(error_info);
        }
    }

    public static void QL_FAIL(String s) {
        throw new IllegalArgumentException(s);
    }

    public static void QL_ASSERT(boolean flag, String error_info) {
        if (!flag) {
            throw new IllegalArgumentException(error_info);
        }
    }

    public static <T> List<T> ArraySet(T... a) {
        List<T> result = new ArrayList<>();
        Collections.addAll(result, a);
        return result;
    }

    public static <T> List<T> ArrayInit(int n) {
        List<T> result = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            result.add(null);
        }
        return result;
    }

    public static <T> List<T> ArrayInit(int n, T t) {
        List<T> result = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            result.add(t);
        }
        return result;
    }

    public static <T extends Number> boolean All_of(List<T> arr, Compare<T> compare) {
        for (T t : arr) {
            if (!compare.call(t)) {
                return false;
            }
        }
        return true;
    }

    public static <T> List<T> resize(List<T> arr, int n, T t) {
        // 处理边界情况
        if (n < 0) {
            throw new IllegalArgumentException("n不能为负数: " + n);
        }
        if (arr == null) {
            return new ArrayList<>(Collections.nCopies(n, null));
        }

        int size = arr.size();
        // 如果大小相等，直接返回新的ArrayList避免外部修改影响
        if (size == n) {
            return new ArrayList<>(arr);
        }

        List<T> result = new ArrayList<>(n); // 预分配容量

        if (size > n) {
            // 截断：使用subList获取前n个元素
            result.addAll(arr.subList(0, n));
        } else {
            // 扩容：先添加所有元素，再补充null
            result.addAll(arr);
            // 添加剩余需要的null元素
            result.addAll(Collections.nCopies(n - size, t));
        }

        return result;
    }

    public static List<Double> ArrayT(int size, double start, double increase) {
        List<Double> result = new ArrayList<>();
        for (int i = 0; i < size; i++) {
            result.add(start + increase * i);
        }
        return result;
    }

    public static <T> List<T> ArrayToList(T[] arr) {
        List<T> res = new ArrayList<>();
        Collections.addAll(res, arr);
        return res;
    }

    public static <T> List<T> clone(List<T> array) {
        return new ArrayList<>(array);
    }

    public static <T> Vector<T> clone(Vector<T> array) {
        return new Vector<>(array);
    }

    public static Matrix transpose(Matrix m) {
        return m.transpose();
    }

    public static double[] toArray(List<Double> list) {
        double[] res = new double[list.size()];
        for (int i = 0; i < list.size(); i++) {
            res[i] = list.get(i);
        }
        return res;
    }

    public static double maxVal(List<Double> list) {
        Optional<Double> max = list.stream().max(Comparator.naturalOrder());
        if (max.isPresent()) {
            return max.get();
        } else {
            throw new IllegalArgumentException(list.toString());
        }
    }

    public static double minVal(List<Double> list) {
        Optional<Double> min = list.stream().min(Comparator.naturalOrder());
        if (min.isPresent()) {
            return min.get();
        }  else {
            throw new IllegalArgumentException(list.toString());
        }
    }

    public static double Norm2(Array v) {
        return Math.sqrt(DotProduct(v, v));
    }

    public static double DotProduct(Array v1, Array v2) {
        QL_REQUIRE(v1.size() == v2.size(),
                "arrays with different sizes (" + v1.size() + ", "
                        + v2.size() + ") cannot be multiplied");
        double res = 0;
        for (int i = 0; i < v1.size(); i++) {
            res += v1.get(i) * v2.get(i);
        }
        return res;
    }

    public static int lowerBound(double[] x, double bis) {
        int index = 0;
        while (x[index] < bis) {
            index += 1;
        }
        return index;
    }

    public static Matrix inverse(Matrix m) {
        return m.inverse();
    }

    public static Array Multiply(Matrix m, Array a) {
        return new Array(m.matrix.preMultiply(a.realVector));
    }

    public static double min(double... a) {
        if (a == null || a.length == 0) {
            throw new IllegalArgumentException("At least one argument must be provided.");
        }

        double minValue = a[0];
        for (int i = 1; i < a.length; i++) {
            if (a[i] < minValue) {
                minValue = a[i];
            }
        }
        return minValue;
    }

    public static List<Integer> qrDecomposition(final Matrix M,
                                                Matrix q,
                                                Matrix r,
                                                boolean pivot) {
        Matrix mT = transpose(M);
        final int m = M.rows();
        final int n = M.cols();

        int[] lipvt = new int[n];
        double[] rdiag = new double[n];
        double[] wa = new double[n];

        QrFacParams params = new QrFacParams(m, n, mT.toArray(), 0, (pivot) ? 1 : 0, lipvt, n, rdiag, rdiag, wa);
        qrfac(params);
        mT.ArraytoMatrix(params.a);
        if (r.cols() != n || r.rows() != n)
            r = new Matrix(n, n, 0);

        for (int i = 0; i < n; ++i) {
            for (int j = 0; j < i; j++) {
                r.set(i, j, 0);
            }
//            std::fill (r.row_begin(i), r.row_begin(i) + i, 0.0);
            r.set(i, i, params.rdiag[i]);
            if (i < m) {
                for (int j = i + 1; j < mT.cols(); j++) {
                    r.set(i, j, mT.get(i, j));
                }
                // std::copy (mT.column_begin(i) + i + 1, mT.column_end(i), r.row_begin(i) + i + 1);
            } else {
                for (int j = i + 1; j < r.cols(); j++) {
                    r.set(i, j, 0);
                }
                //std::fill (r.row_begin(i) + i + 1, r.row_end(i), 0.0);
            }
        }

        if (q.rows() != m || q.cols() != n)
            q = new Matrix(m, n, 0);

        if (m > n) {
            //默认已经是0
            //std::fill (q.begin(), q.end(), 0.0);

            int u = Math.min(n, m);
            for (int i = 0; i < u; ++i)
                q.set(i, i, 1d);

            Array v = new Array(m);
            for (int i = u - 1; i >= 0; --i) {
                if (Math.abs(mT.get(i, i)) > QL_EPSILON) {
                    final double tau = 1.0 / mT.get(i, i);
                    for (int j = 0; j < i; j++) {
                        v.set(j, 0d);
                    }
                    // std::fill (v.begin(), v.begin() + i, 0.0);
                    for (int j = i; j < mT.cols(); j++) {
                        v.set(j, mT.get(i, j));
                    }
                    // std::copy (mT.row_begin(i) + i, mT.row_end(i), v.begin() + i);

                    Array w = new Array(n, 0.0);
                    for (int l = 0; l < n; ++l) {
                        double tmp = 0d;
                        for (int j = i; j < v.size(); j++) {
                            tmp += v.get(j) * q.get(j, l);
                        }
                        w.addEq(l, tmp);
                    }
                    // w[l] += std::inner_product (v.begin() + i, v.end(), q.column_begin(l) + i, Real(0.0));

                    for (int k = i; k < m; ++k) {
                        final double a = tau * v.get(k);
                        for (int l = 0; l < n; ++l)
                            q.set(k, l, q.get(k, l) - a * w.get(l));
                        //q[k][l] -= a * w[l];
                    }
                }
            }
        } else {
            Array w = new Array(m);
            for (int k = 0; k < m; ++k) {
                for (int i = 0; i < w.size(); i++) {
                    w.set(i, 0d);
                }
                // std::fill (w.begin(), w.end(), 0.0);
                w.set(k, 1d);

                for (int j = 0; j < Math.min(n, m); ++j) {
                    final double t3 = mT.get(j, j);
                    if (t3 != 0.0) {
                        double tmp = 0d;
                        for (int i = j; i < mT.cols(); i++) {
                            tmp += mT.get(j, i) * w.get(i);
                        }
                        final double t = tmp / t3;
                        // const Real t = std::inner_product (mT.row_begin(j) + j, mT.row_end(j), w.begin() + j, Real(0.0))/t3;
                        for (int i = j; i < m; ++i) {
                            w.set(i, w.get(i) - mT.get(j, i) * t);
                            // w[i] -= mT[j][i] * t;
                        }
                    }
                    q.set(k, j, w.get(j));
                    // q[k][j] = w[j];
                }
                for (int i = Math.min(n,m); i < q.cols(); i++) {
                    q.set(k, i, 0d);
                }
                // std::fill (q.row_begin(k) + std::min (n, m),q.row_end(k), 0.0);
            }
        }
        List<Integer> ipvt = CommonUtil.ArrayInit(n);
        // std::vector < Size > ipvt(n);

        if (pivot) {
            for (int i = 0; i < n; i++) {
                ipvt.set(i, lipvt[i]);
            }
            // std::copy (lipvt.get(), lipvt.get() + n, ipvt.begin());
        } else {
            for (int i = 0; i < n; ++i)
                ipvt.set(i, i);
                // ipvt[i] = i;
        }

        return ipvt;
    }

    public static Array qrSolve(final Matrix a, final Array b,
                                boolean pivot, final Array d) {
        final int m = a.rows();
        final int n = a.cols();

        QL_REQUIRE(b.size() == m, "dimensions of A and b don't match");
        QL_REQUIRE(d.size() == n || d.empty(),
                "dimensions of A and d don't match");

        Matrix q = new Matrix(m, n, 0);
        Matrix r = new Matrix(n, n, 0);

        List<Integer> lipvt = qrDecomposition(a, q, r, pivot);


        int[] ipvt = new int[n];
        for (int i = 0; i < lipvt.size(); i++) {
            ipvt[i] = lipvt.get(i);
        }
        // std::copy (lipvt.begin(), lipvt.end(), ipvt.get());

        Matrix rT = transpose(r);

        double[] sdiag = new double[n];
        double[] wa = new double[n];

        Array ld = new Array(n, 0.0);
        if (!d.empty()) {
            for (int i = 0; i < d.size(); i++) {
                ld.set(i, d.get(i));
            }
            // std::copy (d.begin(), d.end(), ld.begin());
        }

        Array x = new Array(n);
        Array qtb = transpose(q).mutiply(b);
        //Array qtb = transpose(q) * b;
        QrsolvParams params = new QrsolvParams(n, rT.toArray(), n, ipvt, ld.toArray(), qtb.toArray(), x.toArray(), sdiag, wa);
        Qrsolv qrsolv = new Qrsolv(params);
        qrsolv.run();
//        MINPACK::qrsolv (n, rT.begin(), n, ipvt.get(),
//                ld.begin(), qtb.begin(),
//                x.begin(), sdiag.get(), wa.get());

        return new Array(params.x);
    }

    public static Array Abs(Array arr) {
        Array res = new Array(arr.size());
        for (int i = 0; i < arr.size(); i++) {
            res.set(i, Math.abs(arr.get(i)));
        }
        return res;
    }

    public static void main(String[] args) {
        List<Integer> a = Arrays.asList(1, 2, 3);
        List<Integer> c = a;
        List<Integer> b = clone(a);
        a.set(1, 100);
//        System.out.println(b.get(1));
//        System.out.println(c.get(1));
        List<Double> doubles = ArrayT(10, 1, 2);
        for (int i = 0; i < 10; i++) {
            System.out.println(doubles.get(i));
        }
        double[][] mm = {{1,2,3,0},{4,5,6,0},{7,8,9,0}};
        RealMatrix matrix = new Array2DRowRealMatrix(mm);
        double[] aa = {1,1,1,1};
        RealVector vector = new ArrayRealVector(aa);
        System.out.println(matrix.transpose().preMultiply(vector));
        
        double[] y = {1,2};
        double[][] m = {{1,2,3},{4,5,6}};
        Array array = qrSolve(new Matrix(m), new Array(y), true, new Array(0));
        for (int i = 0; i < array.size(); i++) {
            System.out.println(array.get(i));
        }
    }

}
