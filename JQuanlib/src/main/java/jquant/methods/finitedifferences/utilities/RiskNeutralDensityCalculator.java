package jquant.methods.finitedifferences.utilities;

import jquant.math.solvers1d.Brent;

/**
 * Interface for a single asset risk neutral terminal density calculation.
 *
 * <p>Port of
 * {@code ql/methods/finitedifferences/utilities/riskneutraldensitycalculator.hpp/.cpp}.
 */
public interface RiskNeutralDensityCalculator {

    double pdf(double x, double t);

    double cdf(double x, double t);

    double invcdf(double p, double t);

    /**
     * Inverse-CDF helper, port of the official {@code InvCDFHelper}.
     */
    class InvCDFHelper {
        private final RiskNeutralDensityCalculator calculator_;
        private final double guess_;
        private final double accuracy_;
        private final int maxEvaluations_;
        private final double stepSize_;

        public InvCDFHelper(
                final RiskNeutralDensityCalculator calculator,
                double guess, double accuracy, int maxEvaluations,
                double stepSize) {
            calculator_ = calculator;
            guess_ = guess;
            accuracy_ = accuracy;
            maxEvaluations_ = maxEvaluations;
            stepSize_ = stepSize;
        }

        public InvCDFHelper(
                final RiskNeutralDensityCalculator calculator,
                double guess, double accuracy, int maxEvaluations) {
            this(calculator, guess, accuracy, maxEvaluations, 0.01);
        }

        public double inverseCDF(double p, double t) {
            final Brent solver = new Brent();
            solver.setMaxEvaluations(maxEvaluations_);
            return solver.solve(x -> calculator_.cdf(x, t) - p,
                    accuracy_, guess_, stepSize_);
        }
    }
}
