package jquant.models.marketmodels.correlations;

import jquant.math.Matrix;
import jquant.models.marketmodels.PiecewiseConstantCorrelation;
import jquant.models.marketmodels.Utilities;

import java.util.ArrayList;
import java.util.List;

import static jquant.math.CommonUtil.QL_REQUIRE;

/**
 * Official QuantLib
 * {@code ql/models/marketmodels/correlations/expcorrelations.hpp/.cpp} port,
 * class {@code ExponentialForwardCorrelation}.
 */
public class ExponentialForwardCorrelation extends PiecewiseConstantCorrelation {

    private final int numberOfRates_;
    private final double longTermCorr_;
    private final double beta_;
    private final double gamma_;
    private final List<Double> rateTimes_;
    private final List<Double> times_;
    private final List<Matrix> correlations_;

    public ExponentialForwardCorrelation(final List<Double> rateTimes,
                                         double longTermCorr,
                                         double beta) {
        this(rateTimes, longTermCorr, beta, 1.0, new ArrayList<>());
    }

    public ExponentialForwardCorrelation(final List<Double> rateTimes,
                                         double longTermCorr,
                                         double beta,
                                         double gamma,
                                         List<Double> times) {
        numberOfRates_ = rateTimes.isEmpty() ? 0 : rateTimes.size() - 1;
        longTermCorr_ = longTermCorr;
        beta_ = beta;
        gamma_ = gamma;
        rateTimes_ = new ArrayList<>(rateTimes);
        times_ = new ArrayList<>(times);

        QL_REQUIRE(numberOfRates_ > 1,
                "Rate times must contain at least two values");

        Utilities.checkIncreasingTimes(rateTimes_);

        // corrTimes must include all rateTimes but the last
        if (times_.isEmpty()) {
            for (int i = 0; i < rateTimes_.size() - 1; ++i)
                times_.add(rateTimes_.get(i));
        } else {
            Utilities.checkIncreasingTimes(times_);
        }

        if (Math.abs(gamma_ - 1.0) <= 1e-12) {
            List<Double> temp = new ArrayList<>(
                    rateTimes_.subList(0, rateTimes_.size() - 1));
            QL_REQUIRE(times_.equals(temp),
                    "corr times " + times_ +
                            " must be equal to (all) rate times (but the last) " +
                            temp);
            Matrix c = ExponentialCorrelations.exponentialCorrelations(
                    rateTimes_, longTermCorr_, beta_, 1.0, 0.0);
            correlations_ = TimeHomogeneousForwardCorrelation.evolvedMatrices(c);
        } else {
            // FIXME should check here that all rateTimes but the last
            // are included in rateTimes
            QL_REQUIRE(times_.get(times_.size() - 1) <= rateTimes_.get(numberOfRates_),
                    "last corr time " + times_.get(times_.size() - 1) +
                            "is after next-to-last rate time " +
                            rateTimes_.get(numberOfRates_));
            correlations_ = new ArrayList<>();
            double time = times_.get(0) / 2.0;
            correlations_.add(ExponentialCorrelations.exponentialCorrelations(
                    rateTimes_, longTermCorr_, beta_, gamma_, time));
            for (int k = 1; k < times_.size(); ++k) {
                time = (times_.get(k) + times_.get(k - 1)) / 2.0;
                correlations_.add(ExponentialCorrelations.exponentialCorrelations(
                        rateTimes_, longTermCorr_, beta_, gamma_, time));
            }
        }
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
