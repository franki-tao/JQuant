package jquant.models.marketmodels.correlations;

import jquant.math.Matrix;
import jquant.models.marketmodels.Utilities;

import java.util.List;

import static jquant.math.CommonUtil.QL_REQUIRE;

/**
 * Official QuantLib {@code ql/models/marketmodels/correlations/expcorrelations.hpp/.cpp} port.
 */
public final class ExponentialCorrelations {

    private ExponentialCorrelations() {
    }

    public static Matrix exponentialCorrelations(final List<Double> rateTimes,
                                                 double longTermCorr,
                                                 double beta) {
        return exponentialCorrelations(rateTimes, longTermCorr, beta, 1.0, 0.0);
    }

    public static Matrix exponentialCorrelations(final List<Double> rateTimes,
                                                 double longTermCorr,
                                                 double beta,
                                                 double gamma,
                                                 double time) {
        // preliminary checks
        Utilities.checkIncreasingTimes(rateTimes);
        QL_REQUIRE(longTermCorr <= 1.0 && longTermCorr >= 0.0,
                "Long term correlation (" + longTermCorr +
                        ") outside [0;1] interval");
        QL_REQUIRE(beta >= 0.0,
                "beta (" + beta +
                        ") must be greater than zero");
        QL_REQUIRE(gamma <= 1.0 && gamma >= 0.0,
                "gamma (" + gamma +
                        ") outside [0;1] interval");

        // Calculate correlation matrix
        int nbRows = rateTimes.size() - 1;
        Matrix correlations = new Matrix(nbRows, nbRows, 0.0);
        for (int i = 0; i < nbRows; ++i) {
            // correlation is defined only between
            // (alive) stochastic rates...
            if (time <= rateTimes.get(i)) {
                correlations.set(i, i, 1.0);
                for (int j = 0; j < i; ++j) {
                    if (time <= rateTimes.get(j)) {
                        double value = longTermCorr + (1.0 - longTermCorr) *
                                Math.exp(-beta * Math.abs(
                                        Math.pow(rateTimes.get(i) - time, gamma) -
                                                Math.pow(rateTimes.get(j) - time, gamma)
                                ));
                        correlations.set(i, j, value);
                        correlations.set(j, i, value);
                    }
                }
            }
        }
        return correlations;
    }
}
