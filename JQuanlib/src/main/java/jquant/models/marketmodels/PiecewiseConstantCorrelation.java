package jquant.models.marketmodels;

import jquant.math.Matrix;

import java.util.List;

import static jquant.math.CommonUtil.QL_REQUIRE;

/**
 * Official QuantLib {@code ql/models/marketmodels/piecewiseconstantcorrelation.hpp} port.
 */
public abstract class PiecewiseConstantCorrelation {

    public abstract List<Double> times();

    public abstract List<Double> rateTimes();

    public abstract List<Matrix> correlations();

    public Matrix correlation(int i) {
        List<Matrix> results = correlations();
        QL_REQUIRE(i < results.size(),
                "index (" + i +
                        ") must be less than correlations vector size (" +
                        results.size() + ")");
        return results.get(i);
    }

    public abstract int numberOfRates();
}
