package jquant.termstructures.yield;

import jquant.Quote;
import jquant.math.Array;
import jquant.math.interpolations.ConvexMonotoneInterpolation;
import jquant.math.interpolations.impl.ConvexMonotone;
import jquant.math.optimization.Constraint;
import jquant.math.optimization.EndCriteria;
import jquant.math.optimization.LevenbergMarquardt;
import jquant.math.optimization.NoConstraint;
import jquant.math.optimization.PositiveConstraint;
import jquant.math.optimization.Problem;
import jquant.math.optimization.SimpleCostFunction;
import jquant.time.Date;
import jquant.time.TimeUtils;

import static jquant.math.CommonUtil.QL_REQUIRE;

/**
 * Localised-term-structure bootstrapper for most curve types.
 *
 * <p>This is a direct port of QuantLib's {@code LocalBootstrap} in
 * {@code ql/termstructures/localbootstrap.hpp}. Unlike
 * {@link jquant.termstructures.yield.PiecewiseYieldCurve}'s iterative
 * bootstrap, each interpolated segment is solved with a local approximation,
 * so the risk profile stays localised even for a global interpolation method.
 * It is particularly suited to {@link ConvexMonotone}.
 *
 * <p>https://github.com/lballabio/QuantLib/blob/master/ql/termstructures/localbootstrap.hpp
 */
public class LocalBootstrap {
    private boolean validCurve_ = false;
    private final int localisation_;
    private final boolean forcePositive_;
    /** {@code null} means "use the curve's accuracy", as {@code Null<Real>()}. */
    private final Double accuracy_;

    public LocalBootstrap() {
        this(2, true, null);
    }

    public LocalBootstrap(int localisation,
                          boolean forcePositive,
                          Double accuracy) {
        this.localisation_ = localisation;
        this.forcePositive_ = forcePositive;
        this.accuracy_ = accuracy;
    }

    /**
     * Mirrors {@code LocalBootstrap::setup}. Registration is idempotent (the
     * observers are held in sets), so repeating it here is harmless.
     */
    public void setup(PiecewiseYieldCurve ts) {
        int n = ts.instruments_.size();
        QL_REQUIRE(n >= ts.interpolator_.getRequiredPoints(),
                "not enough instruments: " + n + " provided, " +
                        ts.interpolator_.getRequiredPoints() + " required");
        QL_REQUIRE(n > localisation_,
                "not enough instruments: " + n + " provided, " +
                        localisation_ + " required.");
        QL_REQUIRE(ts.interpolator_ instanceof ConvexMonotone,
                "LocalBootstrap requires a ConvexMonotone interpolator");

        for (RateHelper helper : ts.instruments_) {
            for (Quote quote : helper.notifications())
                ts.registerWith(quote);
        }
    }

    public void calculate(PiecewiseYieldCurve ts) {
        // QuantLib sets this false at the start of calculate(), so the
        // "reuse the current curve as guess" branch below is never taken.
        validCurve_ = false;
        int nInsts = ts.instruments_.size();

        // ensure rate helpers are sorted
        ts.instruments_.sort((a, b) -> {
            if (TimeUtils.less(a.pillarDate(), b.pillarDate()))
                return -1;
            if (TimeUtils.greater(a.pillarDate(), b.pillarDate()))
                return 1;
            return 0;
        });

        // check that there are no instruments with the same maturity
        for (int i = 1; i < nInsts; ++i) {
            Date m1 = ts.instruments_.get(i - 1).pillarDate();
            Date m2 = ts.instruments_.get(i).pillarDate();
            QL_REQUIRE(!TimeUtils.equals(m1, m2),
                    "two instruments have the same pillar date (" + m1 + ")");
        }

        // check that there are no instruments with invalid quote
        for (int i = 0; i < nInsts; ++i) {
            RateHelper helper = ts.instruments_.get(i);
            QL_REQUIRE(helper.quote().getValue().isValid(),
                    (i + 1) + " instrument (maturity: " + helper.maturityDate()
                            + ", pillar: " + helper.pillarDate()
                            + ") has an invalid quote");
        }

        // setup instruments
        for (RateHelper helper : ts.instruments_)
            helper.setTermStructure(ts);

        // set initial guess only if the current curve cannot be used as guess
        if (validCurve_) {
            QL_REQUIRE(ts.data_.length == nInsts + 1,
                    "dimension mismatch: expected " + (nInsts + 1)
                            + ", actual " + ts.data_.length);
        } else {
            ts.data_ = new double[nInsts + 1];
            ts.data_[0] = ts.traits_.initialValue(ts);
        }

        // calculate dates and times
        ts.dates_.clear();
        ts.times_ = new double[nInsts + 1];
        Date initialDate = ts.referenceDate();
        ts.dates_.add(initialDate);
        ts.times_[0] = ts.timeFromReference(initialDate);
        for (int i = 0; i < nInsts; ++i) {
            RateHelper helper = ts.instruments_.get(i);
            ts.dates_.add(helper.pillarDate());
            ts.times_[i + 1] = ts.timeFromReference(helper.pillarDate());
            if (!validCurve_)
                ts.data_[i + 1] = ts.data_[i];
        }
        // The base curve's maxDate() is dates_.back() until set otherwise;
        // helpers query discount factors during the solve.
        ts.maxDate_ = ts.dates_.get(nInsts);

        double accuracy = accuracy_ != null ? accuracy_ : ts.accuracy_;

        LevenbergMarquardt solver = new LevenbergMarquardt(accuracy,
                accuracy,
                accuracy,
                false);
        EndCriteria endCriteria = new EndCriteria(100, 10, 0.00, accuracy, 0.00);
        Constraint solverConstraint = forcePositive_
                ? new PositiveConstraint()
                : new NoConstraint();

        // now start the bootstrapping.
        int iInst = localisation_ - 1;

        int dataAdjust = ConvexMonotone.dataSizeAdjustment;
        ConvexMonotone interpolator = (ConvexMonotone) ts.interpolator_;

        do {
            final int initialDataPt = iInst + 1 - localisation_ + dataAdjust;
            Array startArray = new Array(localisation_ + 1 - dataAdjust);
            for (int j = 0; j < startArray.size() - 1; ++j)
                startArray.set(j, ts.data_[initialDataPt + j]);

            // Here we are extending the interpolation a point at a time...
            // but the local interpolator can make an approximation for the
            // final localisation period. e.g. if the localisation is 2, then
            // the first section of the curve will be solved using the first 2
            // instruments... with the local interpolator making suitable
            // boundary conditions.
            int length = iInst + 2;
            ConvexMonotoneInterpolation prevInterpolation =
                    ts.interpolation_ instanceof ConvexMonotoneInterpolation
                            ? (ConvexMonotoneInterpolation) ts.interpolation_
                            : null;
            ts.interpolation_ = interpolator.localInterpolate(
                    ts.times_, ts.data_, length, localisation_,
                    prevInterpolation, nInsts + 1);

            if (iInst >= localisation_) {
                startArray.set(localisation_ - dataAdjust,
                        ts.traits_.guess(iInst, ts, false));
            } else {
                startArray.set(localisation_ - dataAdjust, ts.data_[0]);
            }

            final int helperEnd = iInst + 1;
            SimpleCostFunction currentCost = new SimpleCostFunction(x -> {
                for (int i = 0; i < x.size(); ++i)
                    ts.traits_.updateGuess(ts.data_, x.get(i), initialDataPt + i);
                ts.interpolation_.update();

                Array penalties = new Array(localisation_);
                for (int i = 0; i < localisation_; ++i)
                    penalties.set(i,
                            ts.instruments_.get(helperEnd - localisation_ + i).quoteError());
                return penalties;
            });

            Problem toSolve = new Problem(currentCost, solverConstraint, startArray);

            EndCriteria.Type endType = solver.minimize(toSolve, endCriteria);

            // QuantLib's LevenbergMarquardt::minimize finishes by evaluating
            // the cost function at the solution, which writes the final guess
            // through Traits::updateGuess. This port's CostFunction::value
            // does not call values(), so apply the final solution explicitly.
            Array solution = toSolve.currentValue();
            for (int i = 0; i < solution.size(); ++i)
                ts.traits_.updateGuess(ts.data_, solution.get(i), initialDataPt + i);
            ts.interpolation_.update();

            // check the end criteria
            QL_REQUIRE(EndCriteria.succeeded(endType),
                    "Unable to strip yieldcurve to required accuracy: " + endType);
            ++iInst;
        } while (iInst < nInsts);

        validCurve_ = true;
    }
}
