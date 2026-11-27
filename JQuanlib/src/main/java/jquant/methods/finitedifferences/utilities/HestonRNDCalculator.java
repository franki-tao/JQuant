package jquant.methods.finitedifferences.utilities;

import jquant.math.Function;
import jquant.math.integrals.GaussLobattoIntegral;
import jquant.processes.HestonProcess;
import org.apache.commons.math3.complex.Complex;

import static jquant.math.MathUtils.M_TWOPI;
import static jquant.math.MathUtils.QL_EPSILON;

/**
 * Risk neutral terminal probability density for the Heston model.
 *
 * <p>Port of
 * {@code ql/methods/finitedifferences/utilities/hestonrndcalculator.{hpp,cpp}}.
 *
 * <p>References:
 * The formulas are taken from A. Dragulescu, V. Yakovenko, 2002.
 * Probability distribution of returns in the Heston model
 * with stochastic volatility.
 * http://arxiv.org/pdf/cond-mat/0203046.pdf
 *
 * <p>Java adaptation: the official class derives from
 * {@code RiskNeutralDensityCalculator}, which does not exist in this
 * repository, so this is a standalone class. The official {@code invcdf}
 * method is therefore omitted as well: it requires the unavailable
 * {@code BSMRNDCalculator} and {@code RiskNeutralDensityCalculator.InvCDFHelper}.
 */
public class HestonRNDCalculator {

    private static class HestonParams {
        final double v0, kappa, theta, sigma, rho;

        HestonParams(double v0, double kappa, double theta,
                     double sigma, double rho) {
            this.v0 = v0;
            this.kappa = kappa;
            this.theta = theta;
            this.sigma = sigma;
            this.rho = rho;
        }
    }

    private static HestonParams getHestonParams(final HestonProcess process) {
        return new HestonParams(process.v0(), process.kappa(),
                process.theta(), process.sigma(), process.rho());
    }

    private static Complex gamma(final HestonParams p, double p_x) {
        return new Complex(p.kappa, p.rho * p.sigma * p_x);
    }

    private static Complex omega(final HestonParams p, double p_x) {
        final Complex g = gamma(p, p_x);
        return g.multiply(g)
                .add(new Complex(p.sigma * p.sigma * p_x * p_x,
                        -p.sigma * p.sigma * p_x))
                .sqrt();
    }

    private static class CpxPv_Helper implements Function {
        private final HestonParams p_;
        private final double t_;
        private final double x_, c_inf_;

        CpxPv_Helper(final HestonParams p, double x, double t) {
            p_ = p;
            t_ = t;
            x_ = x;
            c_inf_ = Math.min(10.0, Math.max(0.0001,
                    Math.sqrt(1.0 - p_.rho * p_.rho) / p_.sigma))
                    * (p_.v0 + p_.kappa * p_.theta * t);
        }

        @Override
        public double value(double x) {
            return transformPhi(x).getReal();
        }

        double p0(double p_x) {
            if (p_x < QL_EPSILON) {
                return 0.0;
            }

            final double u_x = Math.max(QL_EPSILON, -Math.log(p_x) / c_inf_);
            return phi(u_x)
                    .divide(new Complex(0.0, p_x * c_inf_ * u_x))
                    .getReal();
        }

        private Complex transformPhi(double x) {
            if (x < QL_EPSILON) {
                return new Complex(0.0, 0.0);
            }

            final double u_x = -Math.log(x) / c_inf_;
            return phi(u_x).divide(x * c_inf_);
        }

        private Complex phi(double p_x) {
            final double sigma2 = p_.sigma * p_.sigma;
            final Complex g = gamma(p_, p_x);
            final Complex o = omega(p_, p_x);
            final Complex gamma = g.subtract(o).divide(g.add(o));

            final Complex ratio = o.multiply(
                    new Complex(1.0, 0.0).add(o.multiply(-t_).exp()))
                    .divide(new Complex(1.0, 0.0).subtract(o.multiply(-t_).exp()));

            final Complex denominator = g.add(ratio);

            final Complex exponent = new Complex(0.0, p_x * x_)
                    .subtract(new Complex(p_.v0 * p_x * p_x, -p_.v0 * p_x)
                            .divide(denominator))
                    .add(new Complex(p_.kappa * p_.theta / sigma2, 0.0)
                            .multiply(g.subtract(o).multiply(t_)
                                    .subtract(new Complex(1.0, 0.0)
                                            .subtract(gamma.multiply(o.multiply(-t_).exp()))
                                            .divide(new Complex(1.0, 0.0).subtract(gamma))
                                            .log()
                                            .multiply(2.0))));

            return exponent.exp().multiply(2.0);
        }
    }

    private final HestonProcess hestonProcess_;
    private final double x0_;
    private final double integrationEps_;
    private final int maxIntegrationIterations_;

    public HestonRNDCalculator(final HestonProcess hestonProcess) {
        this(hestonProcess, 1e-6, 10000);
    }

    public HestonRNDCalculator(final HestonProcess hestonProcess,
                               double integrationEps,
                               int maxIntegrationIterations) {
        hestonProcess_ = hestonProcess;
        x0_ = Math.log(hestonProcess_.s0().getValue().value());
        integrationEps_ = integrationEps;
        maxIntegrationIterations_ = maxIntegrationIterations;
    }

    private double x_t(double x, double t) {
        final double dr = hestonProcess_.riskFreeRate().getValue().discount(t);
        final double dq = hestonProcess_.dividendYield().getValue().discount(t);

        return x - x0_ + Math.log(dr / dq);
    }

    /** x = ln(S) */
    public double pdf(double x, double t) {
        return new GaussLobattoIntegral(
                maxIntegrationIterations_, 0.1 * integrationEps_).value(
                new CpxPv_Helper(getHestonParams(hestonProcess_),
                        x_t(x, t), t),
                0.0, 1.0) / M_TWOPI;
    }

    /** cumulative distribution function Pr(x &lt; X), x = ln(S) */
    public double cdf(double x, double t) {
        final CpxPv_Helper helper = new CpxPv_Helper(
                getHestonParams(hestonProcess_), x_t(x, t), t);

        return new GaussLobattoIntegral(
                maxIntegrationIterations_, 0.1 * integrationEps_).value(
                p_x -> helper.p0(p_x),
                0.0, 1.0) / M_TWOPI + 0.5;
    }
}
