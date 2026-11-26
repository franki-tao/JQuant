package jquant.models.marketmodels;

import jquant.math.Matrix;

import java.util.ArrayList;
import java.util.List;

import static jquant.math.CommonUtil.QL_REQUIRE;

/**
 * Official QuantLib {@code ql/models/marketmodels/marketmodel.hpp/.cpp} port.
 *
 * <p>For each time step, generates the pseudo-square root of the covariance
 * matrix for that time step.
 */
public abstract class MarketModel {

    private List<Matrix> covariance_ = new ArrayList<>();
    private List<Matrix> totalCovariance_ = new ArrayList<>();

    public abstract List<Double> initialRates();

    public abstract List<Double> displacements();

    public abstract EvolutionDescription evolution();

    public abstract int numberOfRates();

    public abstract int numberOfFactors();

    public abstract int numberOfSteps();

    public abstract Matrix pseudoRoot(int i);

    public Matrix covariance(int i) {
        if (covariance_.isEmpty()) {
            for (int j = 0; j < numberOfSteps(); ++j)
                covariance_.add(pseudoRoot(j).multipy(pseudoRoot(j).transpose()));
        }
        QL_REQUIRE(i < covariance_.size(),
                "i (" + i +
                        ") must be less than covariance_.size() (" + covariance_.size() + ")");
        return covariance_.get(i);
    }

    public Matrix totalCovariance(int endIndex) {
        if (totalCovariance_.isEmpty()) {
            for (int j = 0; j < numberOfSteps(); ++j)
                totalCovariance_.add(null);
            // call to covariance(0) triggers calculation, if necessary
            totalCovariance_.set(0, covariance(0));
            for (int j = 1; j < numberOfSteps(); ++j)
                totalCovariance_.set(j,
                        totalCovariance_.get(j - 1).add(covariance_.get(j)));
        }
        QL_REQUIRE(endIndex < covariance_.size(),
                "endIndex (" + endIndex +
                        ") must be less than covariance_.size() (" + covariance_.size() + ")");
        return totalCovariance_.get(endIndex);
    }

    public List<Double> timeDependentVolatility(int i) {
        QL_REQUIRE(i < numberOfRates(),
                "index (" + i + ") must less than number of rates (" +
                        numberOfRates() + ")");

        List<Double> result = new ArrayList<>();
        List<Double> evolutionTime = evolution().evolutionTimes();

        double lastTime = 0.0;
        for (int j = 0; j < numberOfSteps(); ++j) {
            double tau = evolutionTime.get(j) - lastTime;
            double thisVariance = covariance(j).get(i, i);
            double thisVol = Math.sqrt(thisVariance / tau);
            result.add(thisVol);
            lastTime = evolutionTime.get(j);
        }
        return result;
    }
}
