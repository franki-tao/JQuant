package jquant.models.marketmodels.models;

import jquant.math.Matrix;
import jquant.models.marketmodels.EvolutionDescription;
import jquant.models.marketmodels.MarketModel;
import jquant.models.marketmodels.Utilities;

import java.util.ArrayList;
import java.util.List;

import static jquant.math.CommonUtil.QL_REQUIRE;

/**
 * Official QuantLib {@code ql/models/marketmodels/models/pseudorootfacade.hpp/.cpp}
 * port, vector-of-matrices constructor only. The
 * {@code PseudoRootFacade(CTSMMCapletCalibration)} constructor is not ported.
 */
public class PseudoRootFacade extends MarketModel {

    private final int numberOfFactors_;
    private final int numberOfRates_;
    private final int numberOfSteps_;
    private final List<Double> initialRates_;
    private final List<Double> displacements_;
    private final EvolutionDescription evolution_;
    private final List<Matrix> covariancePseudoRoots_;

    public PseudoRootFacade(final List<Matrix> covariancePseudoRoots,
                            final List<Double> rateTimes,
                            final List<Double> initialRates,
                            final List<Double> displacements) {
        numberOfFactors_ = covariancePseudoRoots.get(0).cols();
        numberOfRates_ = covariancePseudoRoots.get(0).rows();
        numberOfSteps_ = covariancePseudoRoots.size();
        initialRates_ = new ArrayList<>(initialRates);
        displacements_ = new ArrayList<>(displacements);
        evolution_ = new EvolutionDescription(rateTimes);
        // Official stores covariancePseudoRoots_ by value: deep-copy every root.
        covariancePseudoRoots_ = new ArrayList<>();
        for (Matrix pseudoRoot : covariancePseudoRoots)
            covariancePseudoRoots_.add(copyMatrix(pseudoRoot));

        Utilities.checkIncreasingTimes(rateTimes);
        QL_REQUIRE(rateTimes.size() > 1,
                "Rate times must contain at least two values");
        QL_REQUIRE(numberOfRates_ == rateTimes.size() - 1,
                "mismatch between number of rates (" + numberOfRates_
                        + ") and rate times");
        QL_REQUIRE(numberOfRates_ == displacements.size(),
                "mismatch between number of rates (" + numberOfRates_
                        + ") and displacements (" + displacements.size() + ")");
        QL_REQUIRE(numberOfRates_ <= numberOfFactors_ * numberOfSteps_,
                "number of rates (" + numberOfRates_
                        + ") greater than number of factors (" + numberOfFactors_
                        + ") times number of steps (" + numberOfSteps_ + ")");

        // evolutionTimes are not given for the time being
        QL_REQUIRE(numberOfRates_ == covariancePseudoRoots.size(),
                "number of rates (" + numberOfRates_
                        + ") must be equal to covariancePseudoRoots.size() ("
                        + covariancePseudoRoots.size() + ")");

        for (int k = 0; k < numberOfSteps_; ++k) {
            QL_REQUIRE(this.covariancePseudoRoots_.get(k).rows() == numberOfRates_,
                    "step " + k
                            + ": pseudoRoot has wrong number of rows: "
                            + this.covariancePseudoRoots_.get(k).rows()
                            + " instead of " + numberOfRates_);
            // not really mandatory
            QL_REQUIRE(this.covariancePseudoRoots_.get(k).cols() == numberOfFactors_,
                    "step " + k
                            + ": pseudoRoot has wrong number of columns: "
                            + this.covariancePseudoRoots_.get(k).cols()
                            + " instead of " + numberOfFactors_);
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
        QL_REQUIRE(i < numberOfSteps_,
                "the index " + i + " is invalid: it must be less than "
                        + "number of steps (" + numberOfSteps_ + ")");
        return covariancePseudoRoots_.get(i);
    }

    private static Matrix copyMatrix(final Matrix source) {
        Matrix result = new Matrix(source.rows(), source.cols(), 0.0);
        for (int i = 0; i < source.rows(); ++i)
            for (int j = 0; j < source.cols(); ++j)
                result.set(i, j, source.get(i, j));
        return result;
    }
}
