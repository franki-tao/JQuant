package jquant.math;

import jquant.math.distributions.CumulativeNormalDistribution;
import jquant.math.distributions.NormalDistribution;

import static jquant.math.MathUtils.M_SQRT2;
import static jquant.math.MathUtils.M_SQRTPI;

//! Gaussian kernel function
public class GaussianKernel implements KernelFunction {
    private final NormalDistribution nd_;
    private final CumulativeNormalDistribution cnd_;
    // normFact is sqrt(2*pi)
    private final double normFact_ = M_SQRT2 * M_SQRTPI;

    public GaussianKernel(double average, double sigma) {
        nd_ = new NormalDistribution(average, sigma);
        cnd_ = new CumulativeNormalDistribution(average, sigma);
    }

    @Override
    public double value(double x) {
        return nd_.value(x) * normFact_;
    }

    public double derivative(double x) {
        return nd_.derivative(x) * normFact_;
    }

    public double primitive(double x) {
        return cnd_.value(x) * normFact_;
    }
}
