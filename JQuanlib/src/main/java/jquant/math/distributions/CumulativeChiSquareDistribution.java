package jquant.math.distributions;

public class CumulativeChiSquareDistribution {
    private double df_;

    public CumulativeChiSquareDistribution(double df) {
        this.df_ = df;
    }

    public double value(double x) {
        CumulativeGammaDistribution cumulativeGammaDistribution = new CumulativeGammaDistribution(0.5*df_);
        return cumulativeGammaDistribution.value(0.5*x);
    }
}
