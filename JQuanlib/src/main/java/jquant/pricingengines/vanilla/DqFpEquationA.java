package jquant.pricingengines.vanilla;

import jquant.math.Function;
import jquant.math.integrals.Integrator;

import static jquant.math.MathUtils.M_1_SQRTPI;
import static jquant.math.MathUtils.M_SQRT2;
import static jquant.math.MathUtils.M_SQRT_2;
import static jquant.math.MathUtils.M_SQRTPI;
import static jquant.math.MathUtils.QL_EPSILON;
import static jquant.math.MathUtils.close_enough;
import static jquant.math.MathUtils.squared;

/**
 * Fixed-point equation A.
 *
 * <p>Ported from {@code DqFpEquation_A} in
 * {@code ql/pricingengines/vanilla/qdfpamericanengine.cpp}.
 */
final class DqFpEquationA extends DqFpEquation {
    private final double K;

    DqFpEquationA(double K, double r, double q, double vol, Function B,
                  Integrator integrator) {
        super(r, q, vol, B, integrator);
        this.K = K;
    }

    @Override
    double[] f(double tau, double b) {
        final double v = vol * Math.sqrt(tau);

        double N, D;
        if (tau < QL_EPSILON * QL_EPSILON) {
            if (close_enough(b, K)) {
                N = 1 / (M_SQRT2 * M_SQRTPI * v);
                D = N + 0.5;
            } else {
                N = 0.0;
                D = (b > K) ? 1.0 : 0.0;
            }
        } else {
            final double stv = Math.sqrt(tau) / vol;

            double K12, K3;
            if (x_i.length > 0) {
                K12 = K3 = 0.0;

                for (int i = x_i.length - 1; i >= 0; --i) {
                    final double y = x_i[i];
                    final double m = 0.25 * tau * squared(1 + y);
                    final Dpm dpm = d(m, b / B.value(tau - m));

                    K12 += w_i[i] * Math.exp(q * tau - q * m)
                            * (0.5 * tau * (y + 1) * Phi.value(dpm.first())
                            + stv * phi.value(dpm.first()));
                    K3 += w_i[i] * stv * Math.exp(r * tau - r * m)
                            * phi.value(dpm.second());
                }
            } else {
                K12 = integrator.value(y -> {
                    final double m = 0.25 * tau * squared(1 + y);
                    final double df = Math.exp(q * tau - q * m);

                    if (y <= 5 * QL_EPSILON - 1) {
                        if (close_enough(b, B.value(tau - m))) {
                            return df * stv / (M_SQRT2 * M_SQRTPI);
                        } else {
                            return 0.0;
                        }
                    } else {
                        final double dp = d(m, b / B.value(tau - m)).first();
                        return df * (0.5 * tau * (y + 1) * Phi.value(dp)
                                + stv * phi.value(dp));
                    }
                }, -1, 1);

                K3 = integrator.value(y -> {
                    final double m = 0.25 * tau * squared(1 + y);
                    final double df = Math.exp(r * tau - r * m);

                    if (y <= 5 * QL_EPSILON - 1) {
                        if (close_enough(b, B.value(tau - m))) {
                            return df * stv / (M_SQRT2 * M_SQRTPI);
                        } else {
                            return 0.0;
                        }
                    } else {
                        return df * stv
                                * phi.value(d(m, b / B.value(tau - m)).second());
                    }
                }, -1, 1);
            }
            final Dpm dpm = d(tau, b / K);
            N = phi.value(dpm.second()) / v + r * K3;
            D = phi.value(dpm.first()) / v + Phi.value(dpm.first()) + q * K12;
        }

        final double alpha = K * Math.exp(-(r - q) * tau);
        double fv;
        if (tau < QL_EPSILON * QL_EPSILON) {
            if (close_enough(b, K)) {
                fv = alpha;
            } else if (b > K) {
                fv = 0.0;
            } else {
                if (close_enough(q, 0.0)) {
                    fv = alpha * r * ((q < 0) ? -1.0 : 1.0) / QL_EPSILON;
                } else {
                    fv = alpha * r / q;
                }
            }
        } else {
            fv = alpha * N / D;
        }

        return new double[]{N, D, fv};
    }

    @Override
    double[] NDd(double tau, double b) {
        double Dd, Nd;

        if (tau < QL_EPSILON * QL_EPSILON) {
            if (close_enough(b, K)) {
                final double sqTau = Math.sqrt(tau);
                final double vol2 = vol * vol;
                Dd = M_1_SQRTPI * M_SQRT_2
                        * (-(0.5 * vol2 + r - q) / (b * vol * vol2 * sqTau)
                        + 1 / (b * vol * sqTau));
                Nd = M_1_SQRTPI * M_SQRT_2 * (-0.5 * vol2 + r - q)
                        / (b * vol * vol2 * sqTau);
            } else {
                Dd = Nd = 0.0;
            }
        } else {
            final Dpm dpm = d(tau, b / K);

            Dd = -phi.value(dpm.first()) * dpm.first()
                    / (b * vol * vol * tau)
                    + phi.value(dpm.first()) / (b * vol * Math.sqrt(tau));
            Nd = -phi.value(dpm.second()) * dpm.second()
                    / (b * vol * vol * tau);
        }

        return new double[]{Nd, Dd};
    }
}
