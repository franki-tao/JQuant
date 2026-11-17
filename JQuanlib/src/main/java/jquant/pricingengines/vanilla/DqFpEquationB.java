package jquant.pricingengines.vanilla;

import jquant.math.Function;
import jquant.math.integrals.Integrator;

import static jquant.math.MathUtils.QL_EPSILON;
import static jquant.math.MathUtils.close_enough;
import static jquant.math.MathUtils.squared;

/**
 * Fixed-point equation B.
 *
 * <p>Ported from {@code DqFpEquation_B} in
 * {@code ql/pricingengines/vanilla/qdfpamericanengine.cpp}.
 */
final class DqFpEquationB extends DqFpEquation {
    private final double K;

    DqFpEquationB(double K, double r, double q, double vol, Function B,
                  Integrator integrator) {
        super(r, q, vol, B, integrator);
        this.K = K;
    }

    @Override
    double[] f(double tau, double b) {
        double N, D;
        if (tau < QL_EPSILON * QL_EPSILON) {
            if (close_enough(b, K)) {
                N = D = 0.5;
            } else if (b < K) {
                N = D = 0.0;
            } else {
                N = D = 1.0;
            }
        } else {
            double ni, di;
            if (x_i.length > 0) {
                final double c = 0.5 * tau;

                ni = di = 0.0;
                for (int i = x_i.length - 1; i >= 0; --i) {
                    final double u = c * x_i[i] + c;
                    final Dpm dpm = d(tau - u, b / B.value(u));
                    ni += w_i[i] * Math.exp(r * u) * Phi.value(dpm.second());
                    di += w_i[i] * Math.exp(q * u) * Phi.value(dpm.first());
                }
                ni *= c;
                di *= c;
            } else {
                ni = integrator.value(u -> {
                    final double df = Math.exp(r * u);
                    if (u >= tau * (1 - 5 * QL_EPSILON)) {
                        if (close_enough(b, B.value(u))) {
                            return 0.5 * df;
                        } else {
                            return df * ((b < B.value(u)) ? 0.0 : 1.0);
                        }
                    } else {
                        return df * Phi.value(d(tau - u, b / B.value(u)).second());
                    }
                }, 0, tau);
                di = integrator.value(u -> {
                    final double df = Math.exp(q * u);
                    if (u >= tau * (1 - 5 * QL_EPSILON)) {
                        if (close_enough(b, B.value(u))) {
                            return 0.5 * df;
                        } else {
                            return df * ((b < B.value(u)) ? 0.0 : 1.0);
                        }
                    } else {
                        return df * Phi.value(d(tau - u, b / B.value(u)).first());
                    }
                }, 0, tau);
            }

            final Dpm dpm = d(tau, b / K);

            N = Phi.value(dpm.second()) + r * ni;
            D = Phi.value(dpm.first()) + q * di;
        }

        double fv;
        final double alpha = K * Math.exp(-(r - q) * tau);
        if (tau < QL_EPSILON * QL_EPSILON) {
            if (close_enough(b, K) || b > K) {
                fv = alpha;
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
        final Dpm dpm = d(tau, b / K);
        return new double[]{
                phi.value(dpm.second()) / (b * vol * Math.sqrt(tau)),
                phi.value(dpm.first()) / (b * vol * Math.sqrt(tau))
        };
    }
}
