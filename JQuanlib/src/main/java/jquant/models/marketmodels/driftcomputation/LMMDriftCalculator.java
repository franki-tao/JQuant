package jquant.models.marketmodels.driftcomputation;

import jquant.math.Matrix;
import jquant.models.marketmodels.curvestates.LMMCurveState;

import java.util.ArrayList;
import java.util.List;

import static jquant.math.CommonUtil.QL_REQUIRE;

/**
 * Official QuantLib {@code ql/models/marketmodels/driftcomputation/lmmdriftcalculator.hpp/.cpp} port.
 *
 * <p>Drift computation for log-normal Libor market models. Returns the drift
 * {@code mu dt}. See Mark Joshi, <i>Rapid Computation of Drifts in a
 * Reduced Factor Libor Market Model</i>, Wilmott Magazine, May 2003.
 */
public class LMMDriftCalculator {

    private final int numberOfRates_;
    private final int numberOfFactors_;
    private final boolean isFullFactor_;
    private final int numeraire_;
    private final int alive_;
    private final List<Double> displacements_;
    private final List<Double> oneOverTaus_ = new ArrayList<>();
    private final Matrix C_;
    private final Matrix pseudo_;
    // temporary variables
    private final List<Double> tmp_;
    private final Matrix e_;
    private final int[] downs_;
    private final int[] ups_;

    public LMMDriftCalculator(final Matrix pseudo,
                              final List<Double> displacements,
                              final List<Double> taus,
                              int numeraire,
                              int alive) {
        numberOfRates_ = taus.size();
        numberOfFactors_ = pseudo.cols();
        isFullFactor_ = numberOfFactors_ == numberOfRates_;
        numeraire_ = numeraire;
        alive_ = alive;
        displacements_ = new ArrayList<>(displacements);
        for (int i = 0; i < taus.size(); ++i)
            oneOverTaus_.add(0.0);
        pseudo_ = pseudo;
        tmp_ = new ArrayList<>();
        for (int i = 0; i < taus.size(); ++i)
            tmp_.add(0.0);
        e_ = new Matrix(pseudo_.cols(), pseudo_.rows(), 0.0);
        downs_ = new int[taus.size()];
        ups_ = new int[taus.size()];

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
        for (int i = alive_; i < numberOfRates_; ++i) {
            downs_[i] = Math.min(i + 1, numeraire_);
            ups_[i] = Math.max(i + 1, numeraire_);
        }
    }

    public void compute(final LMMCurveState cs, List<Double> drifts) {
        compute(cs.forwardRates(), drifts);
    }

    public void compute(final List<Double> fwds, List<Double> drifts) {
        QL_REQUIRE(fwds.size() == numberOfRates_, "numberOfRates <> dim");
        QL_REQUIRE(drifts.size() == numberOfRates_, "drifts.size() <> dim");

        if (isFullFactor_)
            computePlain(fwds, drifts);
        else
            computeReduced(fwds, drifts);
    }

    public void computePlain(final LMMCurveState cs, List<Double> drifts) {
        computePlain(cs.forwardRates(), drifts);
    }

    public void computePlain(final List<Double> forwards, List<Double> drifts) {
        // Compute drifts without factor reduction,
        // using directly the covariance matrix.

        // Precompute forwards factor
        for (int i = alive_; i < numberOfRates_; ++i)
            tmp_.set(i, (forwards.get(i) + displacements_.get(i))
                    / (oneOverTaus_.get(i) + forwards.get(i)));

        // Compute drifts
        for (int i = alive_; i < numberOfRates_; ++i) {
            double drift = 0.0;
            for (int k = downs_[i]; k < ups_[i]; ++k)
                drift += tmp_.get(k) * C_.get(i, k);
            drifts.set(i, drift);
            if (numeraire_ > i + 1)
                drifts.set(i, -drifts.get(i));
        }
    }

    public void computeReduced(final LMMCurveState cs, List<Double> drifts) {
        computeReduced(cs.forwardRates(), drifts);
    }

    public void computeReduced(final List<Double> forwards, List<Double> drifts) {
        // Compute drifts with factor reduction,
        // using the pseudo square root of the covariance matrix.

        // Precompute forwards factor
        for (int i = alive_; i < numberOfRates_; ++i)
            tmp_.set(i, (forwards.get(i) + displacements_.get(i))
                    / (oneOverTaus_.get(i) + forwards.get(i)));

        // Enforce initialization
        for (int r = 0; r < numberOfFactors_; ++r)
            e_.set(r, Math.max(0, numeraire_ - 1), 0.0);

        // Now compute drifts: take the numeraire P_N (numeraire_=N)
        // as the reference point, divide the summation into 3 steps,
        // et impera:

        // 1st step: the drift corresponding to the numeraire P_N is zero.
        // (if N=0 no drift is null, if N=numberOfRates_ the last drift is null).
        if (numeraire_ > 0) drifts.set(numeraire_ - 1, 0.0);

        // 2nd step: then, move backward from N-2 (included) back to
        // alive (included) (if N=0 jumps to 3rd step, if N=numberOfRates_ the
        // e_[r][N-1] are correctly initialized):
        for (int i = numeraire_ - 2; i >= alive_; --i) {
            drifts.set(i, 0.0);
            for (int r = 0; r < numberOfFactors_; ++r) {
                e_.set(r, i, e_.get(r, i + 1) + tmp_.get(i + 1) * pseudo_.get(i + 1, r));
                drifts.set(i, drifts.get(i) - e_.get(r, i) * pseudo_.get(i, r));
            }
        }

        // 3rd step: now, move forward from N (included) up to n (excluded)
        // (if N=0 this is the only relevant computation):
        for (int i = numeraire_; i < numberOfRates_; ++i) {
            drifts.set(i, 0.0);
            for (int r = 0; r < numberOfFactors_; ++r) {
                if (i == 0)
                    e_.set(r, i, tmp_.get(i) * pseudo_.get(i, r));
                else
                    e_.set(r, i, e_.get(r, i - 1) + tmp_.get(i) * pseudo_.get(i, r));
                drifts.set(i, drifts.get(i) + e_.get(r, i) * pseudo_.get(i, r));
            }
        }
    }
}
