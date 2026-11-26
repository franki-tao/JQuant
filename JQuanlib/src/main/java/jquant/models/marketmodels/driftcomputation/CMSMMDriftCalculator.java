package jquant.models.marketmodels.driftcomputation;

import jquant.math.Matrix;
import jquant.models.marketmodels.curvestates.CMSwapCurveState;

import java.util.ArrayList;
import java.util.List;

import static jquant.math.CommonUtil.QL_REQUIRE;

/**
 * Official QuantLib
 * {@code ql/models/marketmodels/driftcomputation/cmsmmdriftcalculator.hpp/.cpp} port.
 *
 * <p>Returns the drift \f$ \mu \Delta t \f$.
 */
public class CMSMMDriftCalculator {

    private final int numberOfRates_;
    private final int numberOfFactors_;
    private final int numeraire_;
    private final int alive_;
    private final List<Double> displacements_;
    private final List<Double> oneOverTaus_;
    private final Matrix C_;
    private final Matrix pseudo_;
    // temporary variables
    private final List<Double> tmp_;
    private final Matrix PjPnWk_; // < Wk, P_{j}/P_n> (k, j)
    private final Matrix wkaj_;    // < Wk , Aj/Pn> (k, j)
    private final Matrix wkajN_;   // < Wk , Aj/PN> (k, j)

    private final List<Integer> downs_;
    private final List<Integer> ups_;
    private final int spanningFwds_;

    public CMSMMDriftCalculator(final Matrix pseudo,
                                final List<Double> displacements,
                                final List<Double> taus,
                                int numeraire,
                                int alive,
                                int spanningFwds) {
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
        // Official Matrix(rows, cols) leaves storage uninitialized; Java's
        // two-arg Matrix fills NaN. Zero like a value-initialized official Matrix.
        PjPnWk_ = new Matrix(numberOfFactors_, 1 + taus.size(), 0.0);
        wkaj_ = new Matrix(numberOfFactors_, taus.size(), 0.0);
        wkajN_ = new Matrix(numberOfFactors_, taus.size(), 0.0);
        downs_ = new ArrayList<>();
        ups_ = new ArrayList<>();
        for (int i = 0; i < taus.size(); ++i) {
            downs_.add(0);
            ups_.add(0);
        }
        spanningFwds_ = spanningFwds;

        // Check requirements
        QL_REQUIRE(numberOfRates_ > 0, "Dim out of range");
        QL_REQUIRE(displacements.size() == numberOfRates_,
                "Displacements out of range");
        QL_REQUIRE(pseudo.rows() == numberOfRates_,
                "pseudo.rows() not consistent with dim");
        QL_REQUIRE(pseudo.cols() > 0 && pseudo.cols() <= numberOfRates_,
                "pseudo.rows() not consistent with pseudo.columns()");
        QL_REQUIRE(alive < numberOfRates_,
                "Alive out of bounds");
        QL_REQUIRE(numeraire_ <= numberOfRates_,
                "Numeraire larger than dim");
        QL_REQUIRE(numeraire_ >= alive_,
                "Numeraire smaller than alive");

        // Precompute 1/taus
        for (int i = 0; i < taus.size(); ++i)
            oneOverTaus_.set(i, 1.0 / taus.get(i));

        // Compute covariance matrix from pseudoroot
        Matrix pT = pseudo_.transpose();
        C_ = pseudo_.multipy(pT);

        // Compute lower and upper extrema for (non reduced) drift calculation
        for (int i = alive_; i < numberOfRates_; ++i) {
            downs_.set(i, Math.min(i + 1, numeraire_));
            ups_.set(i, Math.max(i + 1, numeraire_));
        }
    }

    /** Computes the drifts */
    public void compute(final CMSwapCurveState cs,
                        List<Double> drifts) {
        final List<Double> taus = cs.rateTaus();
        // final bond is numeraire

        // Compute cross variations
        for (int k = 0; k < PjPnWk_.rows(); ++k) {
            PjPnWk_.set(k, numberOfRates_, 0.0);
            wkaj_.set(k, numberOfRates_ - 1, 0.0);

            for (int j = numberOfRates_ - 2;
                 j >= alive_ - 1; --j) {
                double sr = cs.cmSwapRate(j + 1, spanningFwds_);
                int endIndex = Math.min(j + spanningFwds_ + 1, numberOfRates_);
                double first = sr * wkaj_.get(k, j + 1);
                double second = cs.cmSwapAnnuity(numberOfRates_, j + 1, spanningFwds_)
                        * (sr + displacements_.get(j + 1))
                        * pseudo_.get(j + 1, k);
                double third = PjPnWk_.get(k, endIndex);
                PjPnWk_.set(k, j + 1, first + second + third);

                if (j >= alive_) {
                    wkaj_.set(k, j, wkaj_.get(k, j + 1)
                            + PjPnWk_.get(k, j + 1) * taus.get(j));

                    if (j + spanningFwds_ + 1 <= numberOfRates_)
                        wkaj_.set(k, j, wkaj_.get(k, j)
                                - PjPnWk_.get(k, endIndex) * taus.get(endIndex - 1));
                }
            }
        }

        double PnOverPN = cs.discountRatio(numberOfRates_, numeraire_);

        for (int j = alive_; j < numberOfRates_; ++j)
            for (int k = 0; k < numberOfFactors_; ++k)
                wkajN_.set(k, j, wkaj_.get(k, j) * PnOverPN
                        - PjPnWk_.get(k, numeraire_) * PnOverPN
                        * cs.cmSwapAnnuity(numeraire_, j, spanningFwds_));

        for (int j = alive_; j < numberOfRates_; ++j) {
            drifts.set(j, 0.0);
            for (int k = 0; k < numberOfFactors_; ++k) {
                drifts.set(j, drifts.get(j) + pseudo_.get(j, k) * wkajN_.get(k, j));
            }
            drifts.set(j, drifts.get(j)
                    / -cs.cmSwapAnnuity(numeraire_, j, spanningFwds_));
        }
    }

    private static List<Double> filledList(int n, double value) {
        List<Double> result = new ArrayList<>();
        for (int i = 0; i < n; ++i)
            result.add(value);
        return result;
    }
}
