package jquant.models.marketmodels.models;

import jquant.math.Matrix;
import jquant.math.matrixutilities.MatrixUtil;
import jquant.math.matrixutilities.impl.SalvagingAlgorithm;
import jquant.models.marketmodels.EvolutionDescription;
import jquant.models.marketmodels.MarketModel;
import jquant.models.marketmodels.PiecewiseConstantCorrelation;
import jquant.termstructures.volatility.AbcdFunction;

import java.util.ArrayList;
import java.util.List;

import static jquant.math.CommonUtil.QL_REQUIRE;

/**
 * Official QuantLib {@code ql/models/marketmodels/models/abcdvol.hpp/.cpp} port.
 */
public class AbcdVol extends MarketModel {

    private final int numberOfFactors_;
    private final int numberOfRates_;
    private final int numberOfSteps_;
    private final List<Double> initialRates_;
    private final List<Double> displacements_;
    private final EvolutionDescription evolution_;
    private final List<Matrix> pseudoRoots_ = new ArrayList<>();

    public AbcdVol(double a,
                   double b,
                   double c,
                   double d,
                   final List<Double> ks,
                   final PiecewiseConstantCorrelation corr,
                   final EvolutionDescription evolution,
                   final int numberOfFactors,
                   final List<Double> initialRates,
                   final List<Double> displacements) {
        numberOfFactors_ = numberOfFactors;
        numberOfRates_ = initialRates.size();
        numberOfSteps_ = evolution.evolutionTimes().size();
        initialRates_ = new ArrayList<>(initialRates);
        displacements_ = new ArrayList<>(displacements);
        evolution_ = evolution;
        for (int i = 0; i < numberOfSteps_; ++i)
            pseudoRoots_.add(new Matrix(numberOfRates_, numberOfFactors_));

        final List<Double> rateTimes = evolution.rateTimes();
        QL_REQUIRE(numberOfRates_ == rateTimes.size() - 1,
                "mismatch between number of rates (" + numberOfRates_ +
                        ") and rate times");
        QL_REQUIRE(numberOfRates_ == displacements.size(),
                "mismatch between number of rates (" + numberOfRates_ +
                        ") and displacements (" + displacements.size() + ")");
        QL_REQUIRE(numberOfRates_ == ks.size(),
                "mismatch between number of rates (" + numberOfRates_ +
                        ") and ks (" + ks.size() + ")");
        QL_REQUIRE(numberOfFactors <= numberOfRates_,
                "number of factors (" + numberOfFactors +
                        ") cannot be greater than numberOfRates (" +
                        numberOfRates_ + ")");
        QL_REQUIRE(numberOfFactors > 0,
                "number of factors (" + numberOfFactors +
                        ") must be greater than zero");

        AbcdFunction abcd = new AbcdFunction(a, b, c, d);
        double effStopTime = 0.0;
        final List<Double> corrTimes = corr.times();
        final List<Double> evolTimes = evolution.evolutionTimes();
        Matrix covariance = new Matrix(numberOfRates_, numberOfRates_);
        for (int k = 0, kk = 0; k < numberOfSteps_; ++k) {
            // one covariance per evolution step
            covariance.fill(0.0);

            // there might be more than one correlation matrix
            // in a single evolution step,
            for (; corrTimes.get(kk) < evolTimes.get(k); ++kk) {
                double effStartTime = effStopTime;
                effStopTime = corrTimes.get(kk);
                Matrix corrMatrix = corr.correlation(kk);
                for (int i = 0; i < numberOfRates_; ++i) {
                    for (int j = i; j < numberOfRates_; ++j) {
                        double cov = ks.get(i) * ks.get(j)
                                * abcd.covariance(effStartTime, effStopTime,
                                rateTimes.get(i), rateTimes.get(j));
                        covariance.set(i, j, covariance.get(i, j) + cov * corrMatrix.get(i, j));
                    }
                }
            }
            // last part in the evolution step
            double effStartTime = effStopTime;
            effStopTime = evolTimes.get(k);
            Matrix corrMatrix = corr.correlation(kk);
            for (int i = 0; i < numberOfRates_; ++i) {
                for (int j = i; j < numberOfRates_; ++j) {
                    double cov = ks.get(i) * ks.get(j)
                            * abcd.covariance(effStartTime, effStopTime,
                            rateTimes.get(i), rateTimes.get(j));
                    covariance.set(i, j, covariance.get(i, j) + cov * corrMatrix.get(i, j));
                }
            }
            // no more use for the kk-th correlation matrix
            while (kk < corrTimes.size() && corrTimes.get(kk) <= evolTimes.get(k))
                ++kk;

            // make it symmetric
            for (int i = 0; i < numberOfRates_; ++i) {
                for (int j = i + 1; j < numberOfRates_; ++j)
                    covariance.set(j, i, covariance.get(i, j));
            }

            Matrix pseudoRoot = MatrixUtil.rankReducedSqrt(covariance,
                    numberOfFactors, 1.0,
                    SalvagingAlgorithm.Type.None);
            pseudoRoots_.set(k, pseudoRoot);

            QL_REQUIRE(pseudoRoot.rows() == numberOfRates_,
                    "step " + k
                            + " abcd vol wrong number of rows: "
                            + pseudoRoot.rows()
                            + " instead of " + numberOfRates_);
            QL_REQUIRE(pseudoRoot.cols() == numberOfFactors,
                    "step " + k
                            + " abcd vol wrong number of columns: "
                            + pseudoRoot.cols()
                            + " instead of " + numberOfFactors);
        }
    }

    @Override
    public List<Double> initialRates() {
        return initialRates_;
    }

    @Override
    public List<Double> displacements() {
        return displacements_;
    }

    @Override
    public EvolutionDescription evolution() {
        return evolution_;
    }

    @Override
    public int numberOfRates() {
        return numberOfRates_;
    }

    @Override
    public int numberOfFactors() {
        return numberOfFactors_;
    }

    @Override
    public int numberOfSteps() {
        return numberOfSteps_;
    }

    @Override
    public Matrix pseudoRoot(int i) {
        return pseudoRoots_.get(i);
    }
}
