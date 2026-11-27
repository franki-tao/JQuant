package jquant.models.marketmodels.models;

import java.util.List;

import static jquant.math.CommonUtil.QL_REQUIRE;

/**
 * Official QuantLib
 * {@code ql/models/marketmodels/models/piecewiseconstantvariance.hpp/.cpp}
 * port.
 */
public abstract class PiecewiseConstantVariance {

    public abstract List<Double> variances();

    public abstract List<Double> volatilities();

    public abstract List<Double> rateTimes();

    public double variance(int i) {
        QL_REQUIRE(i < variances().size(), "invalid step index");
        return variances().get(i);
    }

    public double volatility(int i) {
        QL_REQUIRE(i < volatilities().size(), "invalid step index");
        return volatilities().get(i);
    }

    public double totalVariance(int i) {
        QL_REQUIRE(i < variances().size(), "invalid step index");
        double total = 0.0;
        for (int j = 0; j <= i; ++j)
            total += variances().get(j);
        return total;
    }

    public double totalVolatility(int i) {
        return Math.sqrt(totalVariance(i) / rateTimes().get(i));
    }
}
