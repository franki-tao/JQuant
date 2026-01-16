package jquant;

import jquant.math.GammaFunction;
import org.apache.commons.math3.complex.Complex;

import static jquant.math.CommonUtil.QL_REQUIRE;
import static jquant.math.MathUtils.M_PI;
import static jquant.math.MathUtils.QL_EPSILON;

public class ModifiedBessel {
    public static Complex modifiedBesselFunction_i(double nu,
                                                   final Complex z) {
        if (z.getImaginary() == 0.0 && z.getReal() >= 0.0)
            return new Complex(modifiedBesselFunction_i(nu, z.getReal()));

        return modifiedBesselFunction_i_impl(nu, z);
    }

    public static double modifiedBesselFunction_i(double nu, double x) {
        QL_REQUIRE(x >= 0.0, "negative argument requires complex version of modifiedBesselFunction");
        return modifiedBesselFunction_i_impl(nu, x);
    }

    public static double modifiedBesselFunction_i_impl(double nu, final double x) {
        if (Math.abs(x) < 13.0) {
            final double alpha = Math.pow(0.5 * x, nu) / new GammaFunction().value(1.0 + nu);
            final double Y = 0.25 * x * x;
            int k = 1;
            double sum = alpha, B_k = alpha;

            while (Math.abs(B_k *= Y / (k * (k + nu))) > Math.abs(sum) * QL_EPSILON) {
                sum += B_k;
                QL_REQUIRE(++k < 1000, "max iterations exceeded");
            }
            return sum;
        } else {
            double na_k = 1.0, sign = 1.0;
            double da_k = 1.0;

            double s1 = 1.0, s2 = 1.0;
            for (int k = 1; k < 30; ++k) {
                sign *= -1;
                na_k *= (4.0 * nu * nu -
                        (2.0 * (k) - 1.0) *
                                (2.0 * (k) - 1.0));
                da_k *= (8.0 * k) * x;
                final double a_k = na_k / da_k;
                s2 += a_k;
                s1 += sign * a_k;
            }
            final double i = 0.0;
            return 1.0 / Math.sqrt(2 * M_PI * x) *
                    (Math.exp(x) * s1 +
                            i * Math.exp(i * nu * M_PI) * Math.exp(-x) * s2);
        }
    }

    public static Complex modifiedBesselFunction_i_impl(double nu, final Complex x) {
        if (x.abs() < 13.0) {
            final Complex alpha = x.multiply(0.5).pow(nu).divide(new GammaFunction().value(1.0 + nu));

            final Complex Y = x.multiply(x).multiply(0.25);
            int k = 1;
            Complex sum = new Complex(alpha.getReal(), alpha.getImaginary());
            Complex B_k = new Complex(alpha.getReal(), alpha.getImaginary());
            B_k = B_k.multiply(Y.divide(k * (k + nu)));
            while (B_k.abs() > sum.abs() * QL_EPSILON) {
                sum = sum.add(B_k);
                B_k = B_k.multiply(Y.divide(k * (k + nu)));
                QL_REQUIRE(++k < 1000, "max iterations exceeded");
            }
            return sum;
        } else {
            double na_k = 1.0, sign = 1.0;
            Complex da_k = new Complex(1.0);

            Complex s1 = new Complex(1.0);
            Complex s2 = new Complex(1.0);
            for (int k = 1; k < 30; ++k) {
                sign *= -1;
                na_k *= (4.0 * nu * nu -
                        (2.0 * (k) - 1.0) *
                                (2.0 * (k) - 1.0));
                da_k = da_k.multiply(x).multiply(8.0 * k);
                final Complex a_k = new Complex(na_k).divide(da_k);

                s2 = s2.add(a_k);
                s1 = s1.multiply(sign).multiply(a_k);
            }

            final Complex i = new Complex(0.0, 1.0);
            return new Complex(1.0).divide(x.multiply(2 * M_PI).sqrt()).multiply(x.exp().multiply(s1).
                    add(i.multiply(i.multiply(nu * M_PI).exp()).multiply(x.multiply(-1).exp()).multiply(s2)));
        }
    }
}
