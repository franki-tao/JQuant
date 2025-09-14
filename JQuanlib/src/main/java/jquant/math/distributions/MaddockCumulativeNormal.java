package jquant.math.distributions;

import org.apache.commons.math3.distribution.NormalDistribution;

public class MaddockCumulativeNormal {
    private double average_;
    private double sigma_;

    public MaddockCumulativeNormal() {
        average_ = 0;
        sigma_ = 1;
    }

    public MaddockCumulativeNormal(double average, double sigma) {
        this.average_ = average;
        this.sigma_ = sigma;
    }

    public double value(double x) {
        NormalDistribution normalDistribution = new NormalDistribution(average_, sigma_);
        return normalDistribution.cumulativeProbability(x);
    }
}
