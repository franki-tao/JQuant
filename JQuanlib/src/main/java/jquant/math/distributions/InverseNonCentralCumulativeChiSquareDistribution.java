package jquant.math.distributions;

import jquant.math.Function;
import jquant.math.solvers1d.Brent;

public class InverseNonCentralCumulativeChiSquareDistribution {
    private NonCentralCumulativeChiSquareDistribution nonCentralDist_;
    private double guess_;
    private int maxEvaluations_;
    private double accuracy_;

    public InverseNonCentralCumulativeChiSquareDistribution(double df,
                                                            double ncp,
                                                            int maxEvaluations,
                                                            double accuracy) {
        nonCentralDist_ = new NonCentralCumulativeChiSquareDistribution(df, ncp);
        guess_ = df + ncp;
        maxEvaluations_ = maxEvaluations;
        accuracy_ = accuracy;
    }

    public InverseNonCentralCumulativeChiSquareDistribution(double df,
                                                            double ncp
    ) {
        nonCentralDist_ = new NonCentralCumulativeChiSquareDistribution(df, ncp);
        guess_ = df + ncp;
        maxEvaluations_ = 10;
        accuracy_ = 1e-8;
    }

    public double value(double x) {
        // first find the right side of the interval
        double upper = guess_;
        int evaluations = maxEvaluations_;
        while (nonCentralDist_.value(upper) < x && evaluations > 0) {
            upper *= 2.0;
            --evaluations;
        }

        // use a Brent solver for the rest
        Brent solver = new Brent();
        solver.setMaxEvaluations(evaluations);
        return solver.solve(new Function() {
                                @Override
                                public double value(double y) {
                                    return nonCentralDist_.value(y) - x;
                                }
                            }, accuracy_, 0.75 * upper,
                (evaluations == maxEvaluations_) ? 0.0 : (0.5 * upper), upper);
    }

    public static void main(String[] args) {
        InverseNonCentralCumulativeChiSquareDistribution distribution = new InverseNonCentralCumulativeChiSquareDistribution(1,1);
        System.out.println(distribution.value(0.1));
    }
}
