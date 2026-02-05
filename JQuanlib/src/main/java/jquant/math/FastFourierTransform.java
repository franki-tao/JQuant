package jquant.math;

import org.apache.commons.math3.complex.Complex;

import java.util.List;

import static jquant.math.CommonUtil.QL_REQUIRE;
import static jquant.math.MathUtils.M_LN2;
import static jquant.math.MathUtils.M_PI;

//! FFT implementation
public class FastFourierTransform {
    private List<Double> cs_;
    private List<Double> sn_;

    public FastFourierTransform(int order) {
        cs_ = CommonUtil.ArrayInit(order);
        sn_ = CommonUtil.ArrayInit(order);
        int m = 1 << order;
        cs_.set(order - 1, Math.cos(2 * M_PI / m));
        sn_.set(order - 1, Math.sin(2 * M_PI / m));
        for (int i = order - 1; i > 0; --i) {
            cs_.set(i - 1, cs_.get(i) * cs_.get(i) - sn_.get(i) * sn_.get(i));
            sn_.set(i - 1, 2 * sn_.get(i) * cs_.get(i));
        }
    }

    //! the minimum order required for the given input size
    public static int min_order(int inputSize) {
        return (int) Math.ceil(Math.log(inputSize) / M_LN2);
    }

    //! The required size for the output vector
    public int output_size() {
        return 1 << cs_.size();
    }

    //! FFT transform.
    /*! The output sequence must be allocated by the user */
    public void transform(List<Double> val, List<Complex> out) {
        transform_impl(val, out, false);
    }

    //! Inverse FFT transform.
    /*! The output sequence must be allocated by the user. */
    public void inverse_transform(List<Double> val, List<Complex> out) {
        transform_impl(val, out, true);
    }

    private void transform_impl(List<Double> val, List<Complex> out, boolean inverse) {
        final int order = cs_.size();
        final int N = 1 << order;
        int i = 0;
        for (; i < val.size(); i++) {
            out.set(bit_reverse(i, order), new Complex(val.get(i)));
        }
        QL_REQUIRE(i <= N, "FFT order is too small");
        for (int s = 1; s <= order; ++s) {
            int m = 1 << s;
            Complex w = new Complex(1.0);
            Complex wm = new Complex(cs_.get(s - 1), inverse ? sn_.get(s - 1) : -sn_.get(s - 1));
            for (int j = 0; j < m / 2; ++j) {
                for (int k = j; k < N; k += m) {
                    Complex t = w.multiply(out.get(k + m / 2));
                    Complex u = out.get(k);
                    out.set(k, u.add(t));
                    out.set(k + m/2, u.subtract(t));
                }
                w = w.multiply(wm);
            }
        }
    }

    private static int bit_reverse(int x, int order) {
        int n = 0;
        for (int i = 0; i < order; ++i) {
            n <<= 1;
            n |= (x & 1);
            x >>= 1;
        }
        return n;
    }
}
