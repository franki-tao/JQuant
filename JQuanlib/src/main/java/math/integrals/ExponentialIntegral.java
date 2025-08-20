package math.integrals;

import org.apache.commons.math3.complex.Complex;

import static math.CommonUtil.QL_FAIL;
import static math.CommonUtil.QL_REQUIRE;
import static math.MathUtils.*;


/*
指数积分的计算
 */
/*! References:
    B. Rowe et al: GALSIM: The modular galaxy image simulation toolkit
    https://arxiv.org/abs/1407.7676
    V. Pegoraro, P. Slusallek:
    On the Evaluation of the Complex-Valued Exponential Integral
    https://www.sci.utah.edu/~vpegorar/research/2011_JGT.pdf
*/
public class ExponentialIntegral {
    //正弦指数积分
    public static double Si(double x) {
        if (x < 0)
            return -Si(-x);
        else if (x <= 4.0) {
            final double x2 = x * x;

            return x *
                    (1 + x2 * (-4.54393409816329991e-2 + x2 * (1.15457225751016682e-3
                            + x2 * (-1.41018536821330254e-5 + x2 * (9.43280809438713025e-8
                            + x2 * (-3.53201978997168357e-10 + x2 * (7.08240282274875911e-13
                            - x2 * 6.05338212010422477e-16))))))
                    ) / (
                    1 + x2 * (1.01162145739225565e-2 + x2 * (4.99175116169755106e-5
                            + x2 * (1.55654986308745614e-7 + x2 * (3.28067571055789734e-10
                            + x2 * (4.5049097575386581e-13 + x2 * 3.21107051193712168e-16)))))
            );
        } else {
            return M_PI_2 - exponential_integrals_helper.f(x) * Math.cos(x) -
                    exponential_integrals_helper.g(x) * Math.sin(x);
        }
    }

    public static double Ci(double x) {
        QL_REQUIRE(x >= 0, "x < 0 => Ci(x) = Ci(-x) + i*pi");

        if (x <= 4.0) {
            final double x2 = x * x;

            return M_EULER_MASCHERONI + Math.log(x) +
                    x2 * (-0.25 + x2 * (7.51851524438898291e-3 + x2 * (-1.27528342240267686e-4
                            + x2 * (1.05297363846239184e-6 + x2 * (-4.68889508144848019e-9
                            + x2 * (1.06480802891189243e-11 - x2 * 9.93728488857585407e-15)))))
                    ) / (
                            1 + x2 * (1.1592605689110735e-2 + x2 * (6.72126800814254432e-5
                                    + x2 * (2.55533277086129636e-7 + x2 * (6.97071295760958946e-10
                                    + x2 * (1.38536352772778619e-12 + x2 * (1.89106054713059759e-15
                                    + x2 * 1.39759616731376855e-18))))))
                    );
        } else {
            return
                    exponential_integrals_helper.f(x) * Math.sin(x) - exponential_integrals_helper.g(x) * Math.cos(x);
        }
    }

    private static boolean match(Complex z1, Complex z2, double MAXERROR) {
        Complex d = z1.subtract(z2);
        return Math.abs(d.getReal()) <= MAXERROR * Math.abs(z1.getReal())
                && Math.abs(d.getImaginary()) <= MAXERROR * Math.abs(z1.getImaginary());
    }

    public static Complex Ei(Complex z, Complex acc) {
        if (z.getReal() == 0.0 && z.getImaginary() == 0.0) {
            return new Complex(Double.NEGATIVE_INFINITY);
        }


        double DIST = 4.5;
        double MAX_ERROR = 5.0 * QL_EPSILON;

        final double z_inf = Math.log(0.01 * QL_MAX_REAL) + Math.log(100.0);
        QL_REQUIRE(z.getReal() < z_inf, "argument error ");

        final double z_asym = 2.0 - 1.035 * Math.log(MAX_ERROR);


        final double abs_z = z.abs();


        if (z.getReal() > z_inf) {
            return z.exp().divide(z).add(acc);
        }

        if (abs_z > 1.1 * z_asym) {
            Complex ei = new Complex(0, Math.signum(z.getImaginary() * M_PI)).add(acc);
            Complex s = z.exp().divide(z);
            for (int i = 1; i <= Math.floor(abs_z) + 1; ++i) {
                if (match(ei.add(s), ei, MAX_ERROR)) {
                    return ei.add(s);
                }
                ei = ei.add(s);
                s = s.multiply(i).divide(z);
            }
            QL_FAIL("series conversion issue for Ei(z)");
        }

        if (abs_z > DIST && (z.getReal() < 0 || Math.abs(z.getImaginary()) > DIST)) {
            Complex ei = new Complex(0.0);
            for (int k = 47; k >= 1; --k) {
                ei = new Complex(-k * k).divide(ei.add(2.0 * k + 1.0).subtract(z));
            }
            return new Complex(0, Math.signum(z.getImaginary() * M_PI)).add(acc).subtract(z.exp().divide(ei.subtract(z).add(1)));
        }
        Complex s = new Complex(0);
        Complex sn = new Complex(z.getReal(), z.getImaginary());
        double nn = 1.0;

        int n;
        for (n = 2; n < 1000 && !s.equals(s.add(sn.multiply(nn))); ++n) {
            s = s.add(sn.multiply(nn));
            if ((n & 1) != 0) {
                nn += 1 / (2.0 * (n / 2) + 1); // NOLINT(bugprone-integer-division)
            }
            sn = sn.multiply(z.negate().divide(2 * n));
        }

        QL_REQUIRE(n < 1000, "series conversion issue for Ei(z)");

        Complex r = s.multiply(z.multiply(0.5).exp()).add(z.log()).add(acc.add(M_EULER_MASCHERONI));


        if (z.getImaginary() != 0.0)
            return r;
        else
            return new Complex(r.getReal(), acc.getImaginary());
    }

    public static Complex Ei(Complex z) {
        return Ei(z, new Complex(0));
    }

    public static Complex E1(Complex z) {
        if (z.getImaginary() < 0.0) {
            return Ei(z.negate(), new Complex(0.0, -M_PI)).negate();
        } else if (z.getImaginary() > 0.0 || z.getReal() < 0.0) {
            return Ei(z.negate(), new Complex(0.0, M_PI)).negate();
        } else {
            return Ei(z.negate()).negate();
        }
    }

    public static Complex Si(Complex z) {
        if (z.abs() <= 0.2) {
            Complex s = new Complex(0);
            Complex nn = new Complex(z.getReal(), z.getImaginary());
            int k;
            for (k = 2; k < 100 && !s.equals(s.add(nn)); ++k) {
                s = s.add(nn);
                nn = nn.multiply(z.multiply(z.negate()).divide(((2.0 * k - 2) * (2 * k - 1) * (2 * k - 1)) * (2.0 * k - 3)));
                // nn *= -z*z/((2.0*k-2)*(2*k-1)*(2*k-1))*(2.0*k-3);
            }
            QL_REQUIRE(k < 100, "series conversion issue for Si(z)");

            return s;
        } else {
            Complex i = new Complex(0.0, 1.0);

            return i.multiply(0.5).multiply(E1(i.negate().multiply(z)).subtract(E1(i.multiply(z))).
                    subtract(new Complex(0.0, ((z.getReal() >= 0 && z.getImaginary() >= 0)
                            || (z.getReal() > 0 && z.getImaginary() < 0)) ? M_PI : -M_PI)));
        }
    }

    public static Complex Ci(Complex z) {
        Complex i = new Complex(0.0, 1.0);

        Complex acc = new Complex(0.0);
        if (z.getReal() < 0.0 && z.getImaginary() >= 0.0) {
            acc = acc.add(new Complex(0, M_PI));
        } else if (z.getReal() <= 0.0 && z.getImaginary() <= 0.0) {
            acc = acc.add(new Complex(0, -M_PI));
        }

        return (E1(z.multiply(i.negate())).add(E1(i.multiply(z)))).multiply(-0.5).add(acc);
    }

    public final static class exponential_integrals_helper {
        public static double f(double x) {
            final double x2 = 1.0 / (x * x);

            return (
                    1 + x2 * (7.44437068161936700618e2 + x2 * (1.96396372895146869801e5
                            + x2 * (2.37750310125431834034e7 + x2 * (1.43073403821274636888e9
                            + x2 * (4.33736238870432522765e10 + x2 * (6.40533830574022022911e11
                            + x2 * (4.20968180571076940208e12 + x2 * (1.00795182980368574617e13
                            + x2 * (4.94816688199951963482e12 - x2 * 4.94701168645415959931e11)))))))))
            ) / (x * (
                    1 + x2 * (7.46437068161927678031e2 + x2 * (1.97865247031583951450e5
                            + x2 * (2.41535670165126845144e7 + x2 * (1.47478952192985464958e9
                            + x2 * (4.58595115847765779830e10 + x2 * (7.08501308149515401563e11
                            + x2 * (5.06084464593475076774e12 + x2 * (1.43468549171581016479e13
                            + x2 * 1.11535493509914254097e13))))))))
            ));
        }

        public static double g(double x) {
            final double x2 = 1.0 / (x * x);

            return x2 * (
                    1 + x2 * (8.1359520115168615e2 + x2 * (2.35239181626478200e5
                            + x2 * (3.12557570795778731e7 + x2 * (2.06297595146763354e9
                            + x2 * (6.83052205423625007e10 + x2 * (1.09049528450362786e12
                            + x2 * (7.57664583257834349e12 + x2 * (1.81004487464664575e13
                            + x2 * (6.43291613143049485e12 - x2 * 1.36517137670871689e12)))))))))
            ) / (
                    1 + x2 * (8.19595201151451564e2 + x2 * (2.40036752835578777e5
                            + x2 * (3.26026661647090822e7 + x2 * (2.23355543278099360e9
                            + x2 * (7.87465017341829930e10 + x2 * (1.39866710696414565e12
                            + x2 * (1.17164723371736605e13 + x2 * (4.01839087307656620e13
                            + x2 * 3.99653257887490811e13))))))))
            );
        }
    }

    public static void main(String[] args) {
        System.out.println(ExponentialIntegral.Si(0.5));
        System.out.println(ExponentialIntegral.Ci(0.5));
        System.out.println(ExponentialIntegral.Si(new Complex(1.5, -3)));
        System.out.println(ExponentialIntegral.Ci(new Complex(1.5, -3)));
        System.out.println(ExponentialIntegral.E1(new Complex(1.5, -3)));
        System.out.println(ExponentialIntegral.Ei(new Complex(1.5, -3)));
    }
}
