package jquant.models.marketmodels.pathwisegreeks;

import jquant.math.Matrix;
import jquant.models.marketmodels.driftcomputation.LMMDriftCalculator;

import java.util.ArrayList;
import java.util.List;

import static jquant.math.CommonUtil.QL_REQUIRE;

/**
 * Official QuantLib
 * {@code ql/models/marketmodels/pathwisegreeks/ratepseudorootjacobian.hpp/.cpp}
 * port of {@code RatePseudoRootJacobianNumerical}.
 *
 * <p>Classes for computing the derivative of the map taking rates one step to
 * the next with respect to a change in the pseudo-root. Evolution is log
 * Euler.
 */
public class RatePseudoRootJacobianNumerical {

    // this data is always the same
    private final Matrix pseudoRoot_;
    private final int aliveIndex_;
    private final List<Double> taus_;
    private final List<Matrix> pseudoBumped_ = new ArrayList<>();
    private final List<Double> displacements_;
    private final int numberBumps_;
    private final List<LMMDriftCalculator> driftsComputers_ = new ArrayList<>();
    private final int factors_;

    // workspace variables
    private final List<Double> drifts_;
    private final List<Double> bumpedRates_;

    public RatePseudoRootJacobianNumerical(final Matrix pseudoRoot,
                                           int aliveIndex,
                                           int numeraire,
                                           final List<Double> taus,
                                           final List<Matrix> pseudoBumps,
                                           final List<Double> displacements) {
        pseudoRoot_ = copyMatrix(pseudoRoot);
        aliveIndex_ = aliveIndex;
        taus_ = new ArrayList<>(taus);
        displacements_ = new ArrayList<>(displacements);
        numberBumps_ = pseudoBumps.size();
        factors_ = pseudoRoot.cols();
        drifts_ = new ArrayList<>();
        bumpedRates_ = new ArrayList<>();
        for (int i = 0; i < taus.size(); ++i) {
            drifts_.add(0.0);
            bumpedRates_.add(0.0);
        }

        int numberRates = taus.size();

        QL_REQUIRE(pseudoRoot_.rows() == numberRates,
                "pseudoRoot_.rows()<> taus.size()");

        QL_REQUIRE(displacements_.size() == numberRates,
                "displacements_.size()<> taus.size()");

        QL_REQUIRE(drifts_.size() == numberRates,
                "drifts_.size()<> taus.size()");

        for (int i = 0; i < pseudoBumps.size(); ++i) {
            QL_REQUIRE(pseudoBumps.get(i).rows() == numberRates,
                    "pseudoBumps[i].rows()<> taus.size() with i =" + i);

            QL_REQUIRE(pseudoBumps.get(i).cols() == factors_,
                    "pseudoBumps[i].columns()<> factors with i = " + i);

            Matrix pseudo = copyMatrix(pseudoRoot_);
            add(pseudo, pseudoBumps.get(i));
            pseudoBumped_.add(pseudo);
            driftsComputers_.add(new LMMDriftCalculator(pseudo, displacements,
                    taus, numeraire, aliveIndex));
        }
    }

    public void getBumps(final List<Double> oldRates,
                         final List<Double> oneStepDFs, // not used in the numerical implementation
                         final List<Double> newRates,
                         final List<Double> gaussians,
                         Matrix B) {
        int numberRates = taus_.size();

        QL_REQUIRE(B.rows() == numberBumps_,
                "B.rows()<> numberBumps_");

        QL_REQUIRE(B.cols() == taus_.size(),
                "B.columns()<> number of rates");

        for (int i = 0; i < numberBumps_; ++i) {
            final Matrix pseudo = pseudoBumped_.get(i);
            driftsComputers_.get(i).compute(oldRates, drifts_);

            for (int j = 0; j < aliveIndex_; ++j)
                B.set(i, j, 0.0);

            for (int j = aliveIndex_; j < numberRates; ++j) {
                double bumpedRate = Math.log(oldRates.get(j) + displacements_.get(j));

                for (int k = 0; k < factors_; ++k)
                    bumpedRate += -0.5 * pseudo.get(j, k) * pseudo.get(j, k);

                bumpedRate += drifts_.get(j);

                for (int k = 0; k < factors_; ++k)
                    bumpedRate += pseudo.get(j, k) * gaussians.get(k);

                bumpedRate = Math.exp(bumpedRate);
                bumpedRate -= displacements_.get(j);
                B.set(i, j, bumpedRate - newRates.get(j));
            }
        }
    }

    private static void add(Matrix target, final Matrix source) {
        for (int i = 0; i < target.rows(); ++i)
            for (int j = 0; j < target.cols(); ++j)
                target.set(i, j, target.get(i, j) + source.get(i, j));
    }

    private static Matrix copyMatrix(final Matrix source) {
        Matrix result = new Matrix(source.rows(), source.cols(), 0.0);
        for (int i = 0; i < source.rows(); ++i)
            for (int j = 0; j < source.cols(); ++j)
                result.set(i, j, source.get(i, j));
        return result;
    }
}
