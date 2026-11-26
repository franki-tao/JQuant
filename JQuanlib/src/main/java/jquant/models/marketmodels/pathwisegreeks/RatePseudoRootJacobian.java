package jquant.models.marketmodels.pathwisegreeks;

import jquant.math.Matrix;

import java.util.ArrayList;
import java.util.List;

import static jquant.math.CommonUtil.QL_REQUIRE;

/**
 * Official QuantLib
 * {@code ql/models/marketmodels/pathwisegreeks/ratepseudorootjacobian.hpp/.cpp}
 * port of {@code RatePseudoRootJacobian}.
 *
 * <p>Computes the derivative of the map taking rates one step to the next with
 * respect to a change in the pseudo-root, analytically. Evolution is log
 * Euler.
 */
public class RatePseudoRootJacobian {

    // this data does not change after construction
    private final Matrix pseudoRoot_;
    private final int aliveIndex_;
    private final List<Double> taus_;
    private final List<Matrix> pseudoBumps_;
    private final List<Double> displacements_;
    private final int numberBumps_;
    private final int factors_;

    // workspace variables
    private final List<Matrix> allDerivatives_ = new ArrayList<>();
    private final Matrix e_;
    private final List<Double> ratios_;

    public RatePseudoRootJacobian(final Matrix pseudoRoot,
                                  int aliveIndex,
                                  int numeraire,
                                  final List<Double> taus,
                                  final List<Matrix> pseudoBumps,
                                  final List<Double> displacements) {
        pseudoRoot_ = copyMatrix(pseudoRoot);
        aliveIndex_ = aliveIndex;
        taus_ = new ArrayList<>(taus);
        pseudoBumps_ = new ArrayList<>();
        for (Matrix bump : pseudoBumps)
            pseudoBumps_.add(copyMatrix(bump));
        displacements_ = new ArrayList<>(displacements);
        numberBumps_ = pseudoBumps.size();
        factors_ = pseudoRoot.cols();
        e_ = new Matrix(pseudoRoot.rows(), pseudoRoot.cols(), 0.0);
        ratios_ = new ArrayList<>();
        for (int i = 0; i < taus.size(); ++i)
            ratios_.add(0.0);

        int numberRates = taus.size();

        QL_REQUIRE(aliveIndex == numeraire,
                "we can do only do discretely compounding MM acount so aliveIndex must equal numeraire");

        QL_REQUIRE(pseudoRoot_.rows() == numberRates,
                "pseudoRoot_.rows()<> taus.size()");

        QL_REQUIRE(displacements_.size() == numberRates,
                "displacements_.size()<> taus.size()");

        for (int i = 0; i < pseudoBumps.size(); ++i) {
            QL_REQUIRE(pseudoBumps.get(i).rows() == numberRates,
                    "pseudoBumps[i].rows()<> taus.size() with i =" + i);

            QL_REQUIRE(pseudoBumps.get(i).cols() == factors_,
                    "pseudoBumps[i].columns()<> factors with i = " + i);
        }

        for (int i = 0; i < numberRates; ++i)
            allDerivatives_.add(new Matrix(numberRates, factors_, 0.0));
    }

    public void getBumps(final List<Double> oldRates,
                         final List<Double> discountRatios,
                         final List<Double> newRates,
                         final List<Double> gaussians,
                         Matrix B) {
        int numberRates = taus_.size();

        QL_REQUIRE(B.rows() == numberBumps_,
                "we need B.rows() which is " + B.rows()
                        + " to equal numberBumps_ which is " + numberBumps_);
        QL_REQUIRE(B.cols() == numberRates,
                "we need B.columns() which is " + B.cols()
                        + " to equal numberRates which is " + numberRates);

        for (int j = aliveIndex_; j < numberRates; ++j)
            ratios_.set(j, (oldRates.get(j) + displacements_.get(j))
                    * discountRatios.get(j + 1));

        for (int f = 0; f < factors_; ++f) {
            e_.set(aliveIndex_, f, 0.0);

            for (int j = aliveIndex_ + 1; j < numberRates; ++j)
                e_.set(j, f, e_.get(j - 1, f)
                        + ratios_.get(j - 1) * pseudoRoot_.get(j - 1, f));
        }

        for (int f = 0; f < factors_; ++f)
            for (int j = aliveIndex_; j < numberRates; ++j) {
                for (int k = aliveIndex_; k < j; ++k)
                    allDerivatives_.get(j).set(k, f,
                            newRates.get(j) * ratios_.get(k) * taus_.get(k)
                                    * pseudoRoot_.get(j, f));

                // GG don't seem to have the 2, this term is miniscule in any case
                double tmp = //2*
                        2 * ratios_.get(j) * taus_.get(j) * pseudoRoot_.get(j, f);
                tmp -= pseudoRoot_.get(j, f);
                tmp += e_.get(j, f) * taus_.get(j);
                tmp += gaussians.get(f);
                tmp *= (newRates.get(j) + displacements_.get(j));

                allDerivatives_.get(j).set(j, f, tmp);

                for (int k = j + 1; k < numberRates; ++k)
                    allDerivatives_.get(j).set(k, f, 0.0);
            }

        for (int i = 0; i < numberBumps_; ++i) {
            int j = 0;

            for (; j < aliveIndex_; ++j)
                B.set(i, j, 0.0);
            for (; j < numberRates; ++j) {
                double sum = 0.0;

                for (int k = aliveIndex_; k < numberRates; ++k)
                    for (int f = 0; f < factors_; ++f)
                        sum += pseudoBumps_.get(i).get(k, f)
                                * allDerivatives_.get(j).get(k, f);
                B.set(i, j, sum);
            }
        }
    }

    private static Matrix copyMatrix(final Matrix source) {
        Matrix result = new Matrix(source.rows(), source.cols(), 0.0);
        for (int i = 0; i < source.rows(); ++i)
            for (int j = 0; j < source.cols(); ++j)
                result.set(i, j, source.get(i, j));
        return result;
    }
}
