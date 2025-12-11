package jquant.math.statistics;

import jquant.math.distributions.CumulativeNormalDistribution;
import jquant.math.distributions.InverseCumulativeNormal;
import jquant.math.distributions.NormalDistribution;
import jquant.math.statistics.impl.Stat;

import java.util.List;

import static jquant.math.CommonUtil.QL_REQUIRE;

//! Statistics tool for gaussian-assumption risk measures
/*! This class wraps a somewhat generic statistic tool and adds
    a number of gaussian risk measures (e.g.: value-at-risk, expected
    shortfall, etc.) based on the mean and variance provided by
    the underlying statistic tool.
*/
public class GenericGaussianStatistics extends GeneralStatistics {
    public GeneralStatistics stat_;

    public GenericGaussianStatistics(GeneralStatistics stat) {
        stat_ = stat;
    }

    //! \name Gaussian risk measures
    //@{
    /*! returns the downside variance, defined as
        \f[ \frac{N}{N-1} \times \frac{ \sum_{i=1}^{N}
            \theta \times x_i^{2}}{ \sum_{i=1}^{N} w_i} \f],
        where \f$ \theta \f$ = 0 if x > 0 and
        \f$ \theta \f$ =1 if x <0
    */
    public double gaussianDownsideVariance() {
        return gaussianRegret(0.0);
    }

    /*! returns the downside deviation, defined as the
        square root of the downside variance.
    */
    public double gaussianDownsideDeviation() {
        return Math.sqrt(gaussianDownsideVariance());
    }

    /*! returns the variance of observations below target
        \f[ \frac{\sum w_i (min(0, x_i-target))^2 }{\sum w_i}. \f]

        See Dembo, Freeman "The Rules Of Risk", Wiley (2001)
    */
    public double gaussianRegret(double target) {
        double m = stat_.mean();
        double std = stat_.standardDeviation();
        double variance = std * std;
        CumulativeNormalDistribution gIntegral = new CumulativeNormalDistribution(m, std);
        NormalDistribution g = new NormalDistribution(m, std);
        double firstTerm = variance + m * m - 2.0 * target * m + target * target;
        double alfa = gIntegral.value(target);
        double secondTerm = m - target;
        double beta = variance * g.value(target);
        double result = alfa * firstTerm - beta * secondTerm;
        return result / alfa;
    }

    /*! gaussian-assumption y-th percentile, defined as the value x
        such that \f[ y = \frac{1}{\sqrt{2 \pi}}
                                  \int_{-\infty}^{x} \exp (-u^2/2) du \f]
    */
    public double gaussianPercentile(double percentile) {
        QL_REQUIRE(percentile > 0.0,
                "percentile (" + percentile + ") must be > 0.0");
        QL_REQUIRE(percentile < 1.0,
                "percentile (" + percentile + ") must be < 1.0");

        InverseCumulativeNormal gInverse = new InverseCumulativeNormal(stat_.mean(),
                stat_.standardDeviation());
        return gInverse.value(percentile);
    }

    /*! \pre percentile must be in range (0%-100%) extremes excluded */
    public double gaussianTopPercentile(double percentile) {
        return gaussianPercentile(1.0 - percentile);
    }

    //! gaussian-assumption Potential-Upside at a given percentile
    public double gaussianPotentialUpside(double percentile) {
        QL_REQUIRE(percentile < 1.0 && percentile >= 0.9,
                "percentile (" + percentile + ") out of range [0.9, 1)");

        double result = gaussianPercentile(percentile);
        // potential upside must be a gain, i.e., floored at 0.0
        return Math.max(result, 0.0);
    }

    //! gaussian-assumption Value-At-Risk at a given percentile
    public double gaussianValueAtRisk(double percentile) {
        QL_REQUIRE(percentile < 1.0 && percentile >= 0.9,
                "percentile (" + percentile + ") out of range [0.9, 1)");

        double result = gaussianPercentile(1.0 - percentile);
        // VAR must be a loss
        // this means that it has to be MIN(dist(1.0-percentile), 0.0)
        // VAR must also be a positive quantity, so -MIN(*)
        return -Math.min(result, 0.0);
    }

    //! gaussian-assumption Expected Shortfall at a given percentile
    /*! Assuming a gaussian distribution it
        returns the expected loss in case that the loss exceeded
        a VaR threshold,

        \f[ \mathrm{E}\left[ x \;|\; x < \mathrm{VaR}(p) \right], \f]

        that is the average of observations below the
        given percentile \f$ p \f$.
        Also know as conditional value-at-risk.

        See Artzner, Delbaen, Eber and Heath,
        "Coherent measures of risk", Mathematical Finance 9 (1999)
    */
    public double gaussianExpectedShortfall(double percentile) {
        QL_REQUIRE(percentile < 1.0 && percentile >= 0.9,
                "percentile (" + percentile + ") out of range [0.9, 1)");

        double m = this.mean();
        double std = this.standardDeviation();
        InverseCumulativeNormal gInverse = new InverseCumulativeNormal(m, std);
        double var = gInverse.value(1.0 - percentile);
        NormalDistribution g = new NormalDistribution(m, std);
        double result = m - std * std * g.value(var) / (1.0 - percentile);
        // expectedShortfall must be a loss
        // this means that it has to be MIN(result, 0.0)
        // expectedShortfall must also be a positive quantity, so -MIN(*)
        return -Math.min(result, 0.0);
    }

    //! gaussian-assumption Shortfall (observations below target)
    public double gaussianShortfall(double target) {
        CumulativeNormalDistribution gIntegral = new CumulativeNormalDistribution(this.mean(),
                this.standardDeviation());
        return gIntegral.value(target);
    }

    //! gaussian-assumption Average Shortfall (averaged shortfallness)
    public double gaussianAverageShortfall(double target) {
        double m = this.mean();
        double std = this.standardDeviation();
        CumulativeNormalDistribution gIntegral = new CumulativeNormalDistribution(m, std);
        NormalDistribution g = new NormalDistribution(m, std);
        return ((target - m) + std * std * g.value(target) / gIntegral.value(target));
    }

    @Override
    public int samples() {
        return stat_.samples();
    }

    @Override
    public double mean() {
        return stat_.mean();
    }

    @Override
    public double weightSum() {
        return stat_.weightSum();
    }

    @Override
    public double standardDeviation() {
        return stat_.standardDeviation();
    }

    @Override
    public double errorEstimate() {
        return stat_.errorEstimate();
    }

    @Override
    public double skewness() {
        return stat_.skewness();
    }

    @Override
    public double kurtosis() {
        return stat_.kurtosis();
    }

    @Override
    public double min() {
        return stat_.min();
    }

    @Override
    public double max() {
        return stat_.max();
    }

    @Override
    public void add(double value, double weight) {
        stat_.add(value, weight);
    }

    @Override
    public void addSequence(List<Double> arr) {
        stat_.addSequence(arr);
    }

    @Override
    public void addSequence(List<Double> values, List<Double> weights) {
        stat_.addSequence(values, weights);
    }

    @Override
    public void reset() {
        stat_.reset();
    }

    @Override
    public double variance() {
        return stat_.variance();
    }
}
