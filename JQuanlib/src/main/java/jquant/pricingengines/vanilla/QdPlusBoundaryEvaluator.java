package jquant.pricingengines.vanilla;

import jquant.math.Function;
import jquant.math.distributions.CumulativeNormalDistribution;
import jquant.math.distributions.NormalDistribution;

import static jquant.math.MathUtils.NULL_REAL;
import static jquant.math.MathUtils.QL_EPSILON;
import static jquant.math.MathUtils.close_enough;
import static jquant.math.MathUtils.squared;

/**
 * Exercise-boundary evaluator for the QD+ engine.
 *
 * <p>Ported from {@code QdPlusBoundaryEvaluator} in
 * {@code ql/pricingengines/vanilla/qdplusamericanengine.cpp}.
 */
final class QdPlusBoundaryEvaluator implements Function {
    private final CumulativeNormalDistribution cumNormal_ =
            new CumulativeNormalDistribution();
    private final NormalDistribution normal_ = new NormalDistribution();

    private final double tau, K, sigma, sigma2, v, r, q;
    private final double dr, dq, ddr, omega, lambda, lambdaPrime, alpha, beta;
    private final double xMax, xMin;

    private int nrEvaluations = 0;
    private double sc = NULL_REAL;
    private double dp, dm, Phi_dp, Phi_dm, phi_dp;
    private double npv, theta, charm;

    QdPlusBoundaryEvaluator(double S, double strike, double rf, double dy,
                            double vol, double t, double T) {
        tau = t;
        K = strike;
        sigma = vol;
        sigma2 = sigma * sigma;
        v = sigma * Math.sqrt(tau);
        r = rf;
        q = dy;
        dr = Math.exp(-r * tau);
        dq = Math.exp(-q * tau);
        ddr = (Math.abs(r * tau) > 1e-5)
                ? r / (1 - dr)
                : 1 / (tau * (1 - 0.5 * r * tau * (1 - r * tau / 3)));
        omega = 2 * (r - q) / sigma2;
        lambda = 0.5 * (-(omega - 1)
                - Math.sqrt(squared(omega - 1) + 8 * ddr / sigma2));
        lambdaPrime = 2 * ddr * ddr
                / (sigma2 * Math.sqrt(squared(omega - 1) + 8 * ddr / sigma2));
        alpha = 2 * dr / (sigma2 * (2 * lambda + omega - 1));
        beta = alpha * (ddr + lambdaPrime / (2 * lambda + omega - 1)) - lambda;
        xMax = QdPlusAmericanEngine.xMax(strike, r, q);
        xMin = QL_EPSILON * 1e4 * Math.min(0.5 * (strike + S), xMax);
    }

    @Override
    public double value(double S) {
        ++nrEvaluations;

        if (S != sc) {
            preCalculate(S);
        }

        if (close_enough(K - S, npv)) {
            return (1 - dq * Phi_dp) * S + alpha * theta / dr;
        } else {
            final double c0 = -beta - lambda
                    + alpha * theta / (dr * (K - S - npv));
            return (1 - dq * Phi_dp) * S + (lambda + c0) * (K - S - npv);
        }
    }

    public double derivative(double S) {
        if (S != sc) {
            preCalculate(S);
        }

        return 1 - dq * Phi_dp + dq / v * phi_dp + beta * (1 - dq * Phi_dp)
                + alpha / dr * charm;
    }

    public double fprime2(double S) {
        if (S != sc) {
            preCalculate(S);
        }

        final double gamma = phi_dp * dq / (v * S);
        final double colour = gamma
                * (q + (r - q) * dp / v + (1 - dp * dm) / (2 * tau));

        return dq * (phi_dp / (S * v) - phi_dp * dp / (S * v * v))
                + beta * gamma + alpha / dr * colour;
    }

    public double xmin() {
        return xMin;
    }

    public double xmax() {
        return xMax;
    }

    public int evaluations() {
        return nrEvaluations;
    }

    private void preCalculate(double S) {
        S = Math.max(QL_EPSILON, S);
        sc = S;
        dp = Math.log(S * dq / (K * dr)) / v + 0.5 * v;
        dm = dp - v;
        Phi_dp = cumNormal_.value(-dp);
        Phi_dm = cumNormal_.value(-dm);
        phi_dp = normal_.value(dp);

        npv = dr * K * Phi_dm - S * dq * Phi_dp;
        theta = r * K * dr * Phi_dm - q * S * dq * Phi_dp
                - sigma2 * S / (2 * v) * dq * phi_dp;
        charm = -dq * (phi_dp * ((r - q) / v - dm / (2 * tau))
                + q * Phi_dp);
    }
}
