package jquant.math.distributions;

import jquant.math.MathUtils;


public class NormalDistribution {
    private double average_;
    private double sigma_;
    private double normalizationFactor_;
    private double denominator_;
    private double derNormalizationFactor_;

    public NormalDistribution() {
        average_ = 0.0;
        sigma_ = 1.0;
        preprocessing();
    }

    public NormalDistribution(double average, double sigma) {
        if (sigma <= 0) {
            throw new IllegalArgumentException("sigma must be greater than 0.0");
        }
        this.sigma_ = sigma;
        this.average_ = average;
        preprocessing();
    }

    public double value(double x) {
        double deltax = x-average_;
        double exponent = -(deltax*deltax)/denominator_;
        // debian alpha had some strange problem in the very-low range
        return exponent <= -690.0 ? 0.0 :  // exp(x) < 1.0e-300 anyway
                normalizationFactor_*Math.exp(exponent);
    }

    public double derivative(double x) {
        return (this.value(x) * (average_ - x)) / derNormalizationFactor_;
    }

    private void preprocessing() {
        normalizationFactor_ = MathUtils.M_SQRT_2 * MathUtils.M_1_SQRTPI / sigma_;
        derNormalizationFactor_ = sigma_ * sigma_;
        denominator_ = 2.0 * derNormalizationFactor_;
    }

//    public static void main(String[] args) {
//        NormalDistribution normalDistribution = new NormalDistribution();
//        System.out.println(normalDistribution.value(1));
//    }
}
