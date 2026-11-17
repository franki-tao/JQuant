package jquant.pricingengines.vanilla;

import jquant.math.Function;
import jquant.math.Interpolation;
import jquant.math.distributions.CumulativeNormalDistribution;

import static jquant.math.MathUtils.QL_EPSILON;
import static jquant.math.MathUtils.close_enough;

/**
 * Early-exercise add-on value for the QD+ engine.
 *
 * <p>Ported from {@code detail::QdPlusAddOnValue} in
 * {@code ql/pricingengines/vanilla/qdplusamericanengine.cpp}.
 */
final class QdPlusAddOnValue implements Function {
    private final double T_, S_, K_, xmax_, r_, q_, vol_;
    private final Interpolation q_z_;
    private final CumulativeNormalDistribution Phi_ =
            new CumulativeNormalDistribution();

    QdPlusAddOnValue(double T, double S, double K, double r, double q,
                     double vol, double xmax, Interpolation q_z) {
        T_ = T;
        S_ = S;
        K_ = K;
        xmax_ = xmax;
        r_ = r;
        q_ = q;
        vol_ = vol;
        q_z_ = q_z;
    }

    @Override
    public double value(double z) {
        final double t = z * z;
        final double qInterp = q_z_.value(
                2 * Math.sqrt(Math.max(0.0, T_ - t) / T_) - 1, true);
        final double b_t = xmax_ * Math.exp(-Math.sqrt(Math.max(0.0, qInterp)));

        final double dr = Math.exp(-r_ * t);
        final double dq = Math.exp(-q_ * t);
        final double v = vol_ * Math.sqrt(t);

        double r;
        if (v >= QL_EPSILON) {
            if (b_t > QL_EPSILON) {
                final double dp = Math.log(S_ * dq / (b_t * dr)) / v + 0.5 * v;
                r = 2 * z * (r_ * K_ * dr * Phi_.value(-dp + v)
                        - q_ * S_ * dq * Phi_.value(-dp));
            } else {
                r = 0.0;
            }
        } else if (close_enough(S_ * dq, b_t * dr)) {
            r = z * (r_ * K_ * dr - q_ * S_ * dq);
        } else if (b_t * dr > S_ * dq) {
            r = 2 * z * (r_ * K_ * dr - q_ * S_ * dq);
        } else {
            r = 0.0;
        }

        return r;
    }
}
