package math.distributions;

import static math.MathUtils.squared;

public class NonCentralCumulativeChiSquareSankaranApprox {
    private double df_;
    private double ncp_;

    public NonCentralCumulativeChiSquareSankaranApprox(double df, double ncp) {
        this.df_ = df;
        this.ncp_ = ncp;
    }

    public double value(double x) {
        double h = 1 - 2 * (df_ + ncp_) * (df_ + 3 * ncp_) / (3 * squared(df_ + 2 * ncp_));
        double p = (df_ + 2 * ncp_) / squared(df_ + ncp_);
        double m = (h - 1) * (1 - 3 * h);

        double u =
                (Math.pow(x / (df_ + ncp_), h) - (1 + h * p * (h - 1 - 0.5 * (2 - h) * m * p))) /
                        (h * Math.sqrt(2 * p) * (1 + 0.5 * m * p));

        return new CumulativeNormalDistribution().value(u);
    }
}
