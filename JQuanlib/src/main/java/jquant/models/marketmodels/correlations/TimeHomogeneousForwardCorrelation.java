package jquant.models.marketmodels.correlations;

import jquant.math.Matrix;
import jquant.models.marketmodels.PiecewiseConstantCorrelation;
import jquant.models.marketmodels.Utilities;

import java.util.ArrayList;
import java.util.List;

import static jquant.math.CommonUtil.QL_REQUIRE;

/**
 * Official QuantLib
 * {@code ql/models/marketmodels/correlations/timehomogeneousforwardcorrelation.hpp/.cpp} port.
 */
public class TimeHomogeneousForwardCorrelation extends PiecewiseConstantCorrelation {

    private int numberOfRates_;
    private Matrix fwdCorrelation_;
    private List<Double> rateTimes_;
    private List<Double> times_;
    private List<Matrix> correlations_;

    public TimeHomogeneousForwardCorrelation(final Matrix fwdCorrelation,
                                             final List<Double> rateTimes) {
        numberOfRates_ = rateTimes.isEmpty() ? 0 : rateTimes.size() - 1;
        fwdCorrelation_ = fwdCorrelation;
        rateTimes_ = new ArrayList<>(rateTimes);
        times_ = new ArrayList<>();

        Utilities.checkIncreasingTimes(rateTimes);
        QL_REQUIRE(numberOfRates_ >= 1,
                "Rate times must contain at least two values");
        QL_REQUIRE(numberOfRates_ == fwdCorrelation.rows(),
                "mismatch between number of rates (" + numberOfRates_ +
                        ") and fwdCorrelation rows (" + fwdCorrelation.rows() + ")");
        QL_REQUIRE(numberOfRates_ == fwdCorrelation.cols(),
                "mismatch between number of rates (" + numberOfRates_ +
                        ") and fwdCorrelation columns (" + fwdCorrelation.cols() + ")");

        times_.addAll(rateTimes_.subList(0, rateTimes_.size() - 1));
        correlations_ = evolvedMatrices(fwdCorrelation_);
    }

    public static List<Matrix> evolvedMatrices(final Matrix fwdCorrelation) {
        int numberOfRates = fwdCorrelation.rows();
        List<Matrix> correlations = new ArrayList<>();
        for (int k = 0; k < numberOfRates; ++k)
            correlations.add(new Matrix(numberOfRates, numberOfRates, 0.0));
        for (int k = 0; k < correlations.size(); ++k) {
            // proper diagonal values
            for (int i = k; i < numberOfRates; ++i)
                correlations.get(k).set(i, i, 1.0);
            // copy only time homogeneous values
            for (int i = k; i < numberOfRates; ++i) {
                for (int j = k; j < i; ++j) {
                    double value = fwdCorrelation.get(i - k, j - k);
                    correlations.get(k).set(i, j, value);
                    correlations.get(k).set(j, i, value);
                }
            }
        }
        return correlations;
    }

    @Override
    public List<Double> times() {
        return times_;
    }

    @Override
    public List<Double> rateTimes() {
        return rateTimes_;
    }

    @Override
    public List<Matrix> correlations() {
        return correlations_;
    }

    @Override
    public int numberOfRates() {
        return numberOfRates_;
    }
}
