package jquant.models.marketmodels.pathwisegreeks;

import jquant.math.Matrix;

import java.util.ArrayList;
import java.util.List;

import static jquant.math.CommonUtil.QL_REQUIRE;

/**
 * Official QuantLib
 * {@code ql/models/marketmodels/pathwisegreeks/ratepseudorootjacobian.hpp/.cpp}
 * port of {@code RatePseudoRootJacobianAllElements}.
 *
 * <p>Computes all the derivatives of the rates with respect to each
 * pseudo-root element, analytically. Evolution is log Euler.
 */
public class RatePseudoRootJacobianAllElements {

    // this data does not change after construction
    private final Matrix pseudoRoot_;
    private final int aliveIndex_;
    private final List<Double> taus_;
    private final List<Double> displacements_;
    private final int factors_;

    // workspace
    private final Matrix e_;
    private final List<Double> ratios_;

    public RatePseudoRootJacobianAllElements(final Matrix pseudoRoot,
                                             int aliveIndex,
                                             int numeraire,
                                             final List<Double> taus,
                                             final List<Double> displacements) {
        pseudoRoot_ = copyMatrix(pseudoRoot);
        aliveIndex_ = aliveIndex;
        taus_ = new ArrayList<>(taus);
        displacements_ = new ArrayList<>(displacements);
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
    }

    public void getBumps(final List<Double> oldRates,
                         final List<Double> discountRatios,
                         final List<Double> newRates,
                         final List<Double> gaussians,
                         List<Matrix> B) {
        int numberRates = taus_.size();

        QL_REQUIRE(B.size() == numberRates,
                "we need B.size() which is " + B.size()
                        + " to equal numberRates which is " + numberRates);
        for (Matrix b : B)
            QL_REQUIRE(b.cols() == factors_ && b.rows() == numberRates,
                    "we need B[j].rows() which is "
                            + b.rows() + " to equal numberRates which is " + numberRates
                            + " and B[j].columns() which is " + b.cols()
                            + " to be equal to factors which is " + factors_);

        for (int j = aliveIndex_; j < numberRates; ++j)
            ratios_.set(j, (oldRates.get(j) + displacements_.get(j))
                    * discountRatios.get(j + 1));

        for (int f = 0; f < factors_; ++f) {
            e_.set(aliveIndex_, f, 0.0);

            for (int j = aliveIndex_ + 1; j < numberRates; ++j)
                e_.set(j, f, e_.get(j - 1, f)
                        + ratios_.get(j - 1) * pseudoRoot_.get(j - 1, f));
        }

        // nullify B for rates that have already reset
        for (int j = 0; j < aliveIndex_; ++j)
            for (int k = 0; k < numberRates; ++k)
                for (int f = 0; f < factors_; ++f)
                    B.get(j).set(k, f, 0.0);

        for (int f = 0; f < factors_; ++f)
            for (int j = aliveIndex_; j < numberRates; ++j) {
                for (int k = aliveIndex_; k < j; ++k)
                    B.get(j).set(k, f,
                            newRates.get(j) * ratios_.get(k) * taus_.get(k)
                                    * pseudoRoot_.get(j, f));

                double tmp = 2 * ratios_.get(j) * taus_.get(j) * pseudoRoot_.get(j, f);
                tmp -= pseudoRoot_.get(j, f);
                tmp += e_.get(j, f) * taus_.get(j);
                tmp += gaussians.get(f);
                tmp *= (newRates.get(j) + displacements_.get(j));

                B.get(j).set(j, f, tmp);

                for (int k = 0; k < aliveIndex_; ++k)
                    B.get(j).set(k, f, 0.0);

                for (int k = j + 1; k < numberRates; ++k)
                    B.get(j).set(k, f, 0.0);
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
