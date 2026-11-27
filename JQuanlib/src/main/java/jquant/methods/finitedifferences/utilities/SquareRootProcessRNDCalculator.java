package jquant.methods.finitedifferences.utilities;

import jquant.math.distributions.GammaFunction;
import jquant.math.distributions.IncompleteGamma;
import jquant.math.distributions.InverseNonCentralCumulativeChiSquareDistribution;
import jquant.math.distributions.NonCentralCumulativeChiSquareDistribution;
import jquant.processes.impl.ManualNonCentralChiSquared;

/**
 * Risk neutral terminal density calculator for the square root process.
 *
 * <p>Port of
 * {@code ql/methods/finitedifferences/utilities/squarerootprocessrndcalculator.{hpp,cpp}}.
 */
public class SquareRootProcessRNDCalculator implements RiskNeutralDensityCalculator {

    private final double v0_, kappa_, theta_, sigma_, d_, df_;

    public SquareRootProcessRNDCalculator(double v0, double kappa,
                                          double theta, double sigma) {
        v0_ = v0;
        kappa_ = kappa;
        theta_ = theta;
        sigma_ = sigma;
        d_ = 4.0 * kappa / (sigma * sigma);
        df_ = d_ * theta;
    }

    @Override
    public double pdf(double v, double t) {
        // Same as boost::math::pdf(non_central_chi_squared(df_, ncp), v*k)*k.
        return ManualNonCentralChiSquared.calculate(theta_, kappa_, sigma_, t, v0_, v);
    }

    @Override
    public double cdf(double v, double t) {
        final double e = Math.exp(-kappa_ * t);
        final double k = d_ / (1.0 - e);
        final double ncp = k * v0_ * e;
        return new NonCentralCumulativeChiSquareDistribution(df_, ncp)
                .value(v * k);
    }

    @Override
    public double invcdf(double q, double t) {
        final double e = Math.exp(-kappa_ * t);
        final double k = d_ / (1.0 - e);
        final double ncp = k * v0_ * e;
        return new InverseNonCentralCumulativeChiSquareDistribution(
                df_, ncp, 10000, 1e-12).value(q) / k;
    }

    public double stationary_pdf(double v) {
        final double alpha = 0.5 * df_;
        final double beta = alpha / theta_;
        return Math.pow(beta, alpha) * Math.pow(v, alpha - 1.0)
                * Math.exp(-beta * v - new GammaFunction().logValue(alpha));
    }

    public double stationary_cdf(double v) {
        final double alpha = 0.5 * df_;
        final double beta = alpha / theta_;
        return 1.0 - IncompleteGamma.gammaQ(alpha, beta * v);
    }

    public double stationary_invcdf(double q) {
        final double alpha = 0.5 * df_;
        final double beta = alpha / theta_;
        return IncompleteGamma.gammaQInv(alpha, 1.0 - q) / beta;
    }
}
