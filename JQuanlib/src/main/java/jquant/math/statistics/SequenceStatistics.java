package jquant.math.statistics;

import jquant.math.Array;
import jquant.math.CommonUtil;
import jquant.math.Matrix;
import jquant.math.statistics.impl.Stat;

import java.util.List;

import static jquant.math.CommonUtil.QL_REQUIRE;

public class SequenceStatistics {
    protected int dimension_ = 0;
    protected List<GenericRiskStatistics> stats_;
    protected List<Double> results_;
    protected Matrix quadraticSum_;

    public SequenceStatistics(int dimension) {
        reset(dimension);
    }

    //! \name inspectors
    //@{
    public int size() {
        return dimension_;
    }

    //@}
    //! \name covariance and correlation
    //@{
    //! returns the covariance Matrix
    public Matrix covariance() {
        double sampleWeight = weightSum();
        QL_REQUIRE(sampleWeight > 0.0,
                "sampleWeight=0, unsufficient");

        double sampleNumber = samples();
        QL_REQUIRE(sampleNumber > 1.0,
                "sample number <=1, unsufficient");

        List<Double> m = mean();
        double inv = 1.0 / sampleWeight;

        Matrix result = quadraticSum_.multiply(inv);
        result = result.subtract(CommonUtil.outerProduct(m, m));
        result = result.multiply(sampleNumber / (sampleNumber - 1.0));
        return result;
    }

    //! returns the correlation Matrix
    public Matrix correlation() {
        Matrix correlation = covariance();
        Array variances = correlation.diagonal();
        for (int i = 0; i < dimension_; i++) {
            for (int j = 0; j < dimension_; j++) {
                if (i == j) {
                    if (variances.get(i) == 0.0) {
                        correlation.set(i, j, 1.0);
                    } else {
                        correlation.multipyEq(i, j, 1.0 / Math.sqrt(variances.get(i) * variances.get(j)));
                    }
                } else {
                    if (variances.get(i) == 0.0 && variances.get(j) == 0) {
                        correlation.set(i, j, 1.0);
                    } else if (variances.get(i) == 0.0 || variances.get(j) == 0.0) {
                        correlation.set(i, j, 0.0);
                    } else {
                        correlation.multipyEq(i, j, 1.0 / Math.sqrt(variances.get(i) * variances.get(j)));
                    }
                }
            } // j for
        } // i for

        return correlation;
    }

    //@}
    //! \name 1-D inspectors lifted from underlying statistics class
    //@{
    public int samples() {
        return (stats_.isEmpty()) ? 0 : stats_.get(0).samples();
    }

    public double weightSum() {
        return (stats_.isEmpty()) ? 0.0 : stats_.get(0).weightSum();
    }

    public List<Double> mean() {
        for (int i = 0; i < results_.size(); i++) {
            results_.set(i, stats_.get(i).mean());
        }
        return results_;
    }

    public List<Double> variance() {
        for (int i = 0; i < results_.size(); i++) {
            results_.set(i, stats_.get(i).variance());
        }
        return results_;
    }

    public List<Double> standardDeviation() {
        for (int i = 0; i < results_.size(); i++) {
            results_.set(i, stats_.get(i).standardDeviation());
        }
        return results_;
    }

    public List<Double> errorEstimate() {
        for (int i = 0; i < results_.size(); i++) {
            results_.set(i, stats_.get(i).errorEstimate());
        }
        return results_;
    }

    public List<Double> skewness() {
        for (int i = 0; i < results_.size(); i++) {
            results_.set(i, stats_.get(i).skewness());
        }
        return results_;
    }

    public List<Double> kurtosis() {
        for (int i = 0; i < results_.size(); i++) {
            results_.set(i, stats_.get(i).kurtosis());
        }
        return results_;
    }

    public List<Double> min() {
        for (int i = 0; i < results_.size(); i++) {
            results_.set(i, stats_.get(i).min());
        }
        return results_;
    }

    public List<Double> max() {
        for (int i = 0; i < results_.size(); i++) {
            results_.set(i, stats_.get(i).max());
        }
        return results_;
    }

    public void add(List<Double> values, double weight) {
        if (dimension_ == 0) {
            // stat wasn't initialized yet
            int dimension = values.size();
            reset(dimension);
        }
        QL_REQUIRE(values.size() == (dimension_),
                "sample size mismatch: " + dimension_ +
                        " required, " + values.size() +
                        " provided");
        quadraticSum_ = quadraticSum_.add(CommonUtil.outerProduct(values, values).multiply(weight));

        for (int i = 0; i < values.size(); i++) {
            stats_.get(i).add(values.get(i), weight);
        }
    }

    public void reset(int dimension) {
        // (re-)initialize
        if (dimension > 0) {
            if (dimension == dimension_) {
                for (int i = 0; i < dimension_; ++i)
                    stats_.get(i).reset();
            } else {
                dimension_ = dimension;
                stats_ = CommonUtil.ArrayInit(dimension);
                results_ = CommonUtil.ArrayInit(dimension, 0d);
            }
            quadraticSum_ = new Matrix(dimension_, dimension_, 0.0);
        } else {
            dimension_ = dimension;
        }
    }

    public GenericRiskStatistics get(int i) {
        return stats_.get(i);
    }
}
