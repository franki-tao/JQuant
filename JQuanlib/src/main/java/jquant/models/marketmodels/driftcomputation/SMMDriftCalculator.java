package jquant.models.marketmodels.driftcomputation;

import jquant.math.Matrix;
import jquant.models.marketmodels.curvestates.CoterminalSwapCurveState;

import java.util.ArrayList;
import java.util.List;

import static jquant.math.CommonUtil.QL_REQUIRE;

/**
 * Official QuantLib
 * {@code ql/models/marketmodels/driftcomputation/smmdriftcalculator.hpp/.cpp}
 * port.
 *
 * <p>Drift computation for coterminal swap market models. Returns the drift
 * \f$ \mu \Delta t \f$. See Mark Joshi, Lorenzo Liesch, <i>Effective
 * Implementation Of Generic Market Models</i>.
 */
public class SMMDriftCalculator {

    private final int numberOfRates_;
    private final int numberOfFactors_;
    private final int numeraire_;
    private final int alive_;
    private final List<Double> displacements_;
    private final List<Double> oneOverTaus_;
    private final Matrix C_;
    private final Matrix pseudo_;
    // temporary variables to be added later
    private final List<Double> tmp_;
    private final Matrix wkaj_;  // < W(k) | A(j)/P(n) >
    private final Matrix wkpj_;  // < W(k) | P(j)/P(n) >
    private final Matrix wkajshifted_;

    public SMMDriftCalculator(final Matrix pseudo,
                              final List<Double> displacements,
                              final List<Double> taus,
                              int numeraire,
                              int alive) {
        numberOfRates_ = taus.size();
        numberOfFactors_ = pseudo.cols();
        numeraire_ = numeraire;
        alive_ = alive;
        displacements_ = new ArrayList<>(displacements);
        oneOverTaus_ = new ArrayList<>();
        for (int i = 0; i < taus.size(); ++i)
            oneOverTaus_.add(0.0);
        pseudo_ = pseudo;
        tmp_ = filledList(taus.size(), 0.0);
        // Official Matrix(rows, cols) is zero-filled; Java's two-arg Matrix
        // fills NaN. Zero initialization is required for the last element.
        wkaj_ = new Matrix(numberOfFactors_, pseudo.rows(), 0.0);
        wkpj_ = new Matrix(numberOfFactors_, pseudo.rows() + 1, 0.0);
        wkajshifted_ = new Matrix(numberOfFactors_, pseudo.rows(), 0.0);

        // Check requirements
        QL_REQUIRE(numberOfRates_ > 0, "Dim out of range");
        QL_REQUIRE(displacements.size() == numberOfRates_,
                "Displacements out of range");
        QL_REQUIRE(pseudo.rows() == numberOfRates_,
                "pseudo.rows() not consistent with dim");
        QL_REQUIRE(pseudo.cols() > 0 && pseudo.cols() <= numberOfRates_,
                "pseudo.rows() not consistent with pseudo.columns()");
        QL_REQUIRE(alive < numberOfRates_, "Alive out of bounds");
        QL_REQUIRE(numeraire_ <= numberOfRates_, "Numeraire larger than dim");
        QL_REQUIRE(numeraire_ >= alive, "Numeraire smaller than alive");

        // Precompute 1/taus
        for (int i = 0; i < taus.size(); ++i)
            oneOverTaus_.set(i, 1.0 / taus.get(i));

        // Compute covariance matrix from pseudoroot
        Matrix pT = pseudo_.transpose();
        C_ = pseudo_.multipy(pT);

        // Compute lower and upper extrema for (non reduced) drift calculation
        //for (Size i=alive_; i<numberOfRates_; ++i) {
        //    downs_[i] = std::min(i+1, numeraire_);
        //    ups_[i]   = std::max(i+1, numeraire_);
        //}
    }

    /** Computes the drifts */
    public void compute(final CoterminalSwapCurveState cs,
                        List<Double> drifts) {
        // Compute drifts with factor reduction,
        // using the pseudo square root of the covariance matrix.

        final List<Double> SR = cs.coterminalSwapRates();
        // calculates and stores wkaj_, wkpj1_
        // assuming terminal bond measure
        // eq 5.4-5.7
        final List<Double> taus = cs.rateTaus();
        List<Double> annuities = filledList(numberOfRates_, 0.0);

        for (int j = 0; j < numberOfRates_; ++j) {
            annuities.set(j, cs.coterminalSwapAnnuity(numberOfRates_, j));
        }
        for (int k = 0; k < numberOfFactors_; ++k) {
            // taken care in the constructor
            // wkpj1_[k][numberOfRates_-1]= 0.0;
            // wkaj_[k][numberOfRates_-1] = 0.0;
            for (int j = numberOfRates_ - 2; j >= alive_ - 1; --j) {
                // < W(k) | P(j+1)/P(n) > =
                // = SR(j+1) a(j+1,k) A(j+1) / P(n) + SR(j+1) < W(k) | A(j+1)/P(n) >
                double annuity = annuities.get(j + 1);
                wkpj_.set(k, j + 1,
                        SR.get(j + 1) * (pseudo_.get(j + 1, k) * annuity
                                + wkaj_.get(k, j + 1))
                                + pseudo_.get(j + 1, k) * displacements_.get(j + 1)
                                * annuity);

                if (j >= alive_)
                    wkaj_.set(k, j,
                            wkpj_.get(k, j + 1) * taus.get(j)
                                    + wkaj_.get(k, j + 1));
            }
        }

        double numeraireRatio = cs.discountRatio(numberOfRates_, numeraire_);

        // change to work for general numeraire
        for (int k = 0; k < numberOfFactors_; ++k) {
            // compute < Wk, PN/pn>
            for (int j = alive_; j < numberOfRates_; ++j) {
                wkajshifted_.set(k, j,
                        -wkaj_.get(k, j) / annuities.get(j)
                                + wkpj_.get(k, numeraire_) * numeraireRatio);
            }
        }

        // eq 5.3 (in log coordinates)
        for (int j = alive_; j < numberOfRates_; ++j) {
            drifts.set(j, 0.0);
            for (int k = 0; k < numberOfFactors_; ++k) {
                drifts.set(j, drifts.get(j)
                        + wkajshifted_.get(k, j) * pseudo_.get(j, k));
            }
        }
    }

    private static List<Double> filledList(int n, double value) {
        List<Double> result = new ArrayList<>();
        for (int i = 0; i < n; ++i)
            result.add(value);
        return result;
    }
}
