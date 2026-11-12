package jquant.termstructures.yield;

import jquant.math.Array;
import jquant.math.optimization.EndCriteria;
import jquant.math.optimization.LevenbergMarquardt;
import jquant.math.optimization.NoConstraint;
import jquant.math.optimization.OptimizationMethod;
import jquant.math.optimization.Problem;
import jquant.math.optimization.SimpleCostFunction;
import jquant.time.Date;
import jquant.time.TimeUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Supplier;

import static jquant.math.CommonUtil.QL_REQUIRE;

/**
 * Global bootstrapper with additional restrictions.
 *
 * <p>This is a port of QuantLib's {@code GlobalBootstrap} single-curve path in
 * {@code ql/termstructures/globalbootstrap.hpp}. Instead of stripping one
 * pillar at a time, it minimizes all rate-helper errors together with an
 * arbitrary number of additional error terms, so additional dates and
 * penalties can constrain the curve globally.
 *
 * <p>The additional dates functor must return a set of additional dates to add
 * to the interpolation grid. These dates must only depend on the global
 * evaluation date.
 *
 * <p>The additional penalties functor must yield at least as many values such
 * that {@code number of (usual, alive) rate helpers + number of additional
 * values >= number of data points - 1} (the data points contain t=0). These
 * values are treated as additional error terms in the optimization. The usual
 * rate helpers return {@code quoteError}; all error terms are equally weighted.
 *
 * <p>The additional helpers are registered with the curve like the usual rate
 * helpers, but no pillar dates or error terms are added for them. Pillars and
 * error terms have to be added by additional dates and additional penalties.
 *
 * <p>https://github.com/lballabio/QuantLib/blob/master/ql/termstructures/globalbootstrap.hpp
 */
public class GlobalBootstrap {
    /** Additional error terms, as a function of (times, curve data). */
    @FunctionalInterface
    public interface AdditionalPenalties {
        Array apply(double[] times, double[] data);
    }

    /** Additional variables to optimize together with the curve values. */
    public interface AdditionalBootstrapVariables {
        /** Initialize the variables to initial guesses and return them. */
        Array initialize(boolean validData);

        /** Update the variables to the given values. */
        void update(Array x);
    }

    /** Initial guess for the whole curve. */
    @FunctionalInterface
    public interface InitialGuessFn {
        Array apply(double[] times, double[] data);
    }

    private PiecewiseYieldCurve ts_;
    /** {@code null} means "use the curve's accuracy", as {@code Null<Real>()}. */
    private final Double accuracy_;
    private OptimizationMethod optimizer_;
    private EndCriteria endCriteria_;
    private final List<RateHelper> additionalHelpers_;
    private List<RateHelper> aliveInstruments_ = new ArrayList<>();
    private List<RateHelper> aliveAdditionalHelpers_ = new ArrayList<>();
    private final Supplier<List<Date>> additionalDates_;
    private AdditionalPenalties additionalPenalties_;
    private Supplier<Array> additionalPenaltiesNoArgs_;
    private final AdditionalBootstrapVariables additionalVariables_;
    private double[] instrumentWeights_;
    private double[] aliveInstrumentWeights_;
    private final InitialGuessFn initialGuessFn_;
    private boolean initialized_ = false;
    private boolean validCurve_ = false;
    /** Set when {@link #initialize()} had to replace the times array. */
    private boolean interpolationDirty_ = false;

    /** Mirrors {@code GlobalBootstrap(Real accuracy = Null<Real>(), ...)}. */
    public GlobalBootstrap() {
        this(null, null, null, new ArrayList<>(), null);
    }

    public GlobalBootstrap(Double accuracy) {
        this(accuracy, null, null, new ArrayList<>(), null);
    }

    public GlobalBootstrap(Double accuracy,
                           OptimizationMethod optimizer,
                           EndCriteria endCriteria) {
        this(accuracy, optimizer, endCriteria, new ArrayList<>(), null);
    }

    public GlobalBootstrap(Double accuracy,
                           OptimizationMethod optimizer,
                           EndCriteria endCriteria,
                           List<Double> instrumentWeights,
                           InitialGuessFn initialGuessFn) {
        this.additionalHelpers_ = new ArrayList<>();
        this.additionalDates_ = null;
        this.additionalVariables_ = null;
        this.initialGuessFn_ = initialGuessFn;
        this.accuracy_ = accuracy;
        this.optimizer_ = optimizer;
        this.endCriteria_ = endCriteria;
        this.instrumentWeights_ = toArray(instrumentWeights);
    }

    /**
     * Mirrors {@code GlobalBootstrap(additionalHelpers, additionalDates,
     * AdditionalPenalties, accuracy, ...)}, i.e. the (times, data) -> Array form.
     */
    public GlobalBootstrap(List<RateHelper> additionalHelpers,
                           Supplier<List<Date>> additionalDates,
                           AdditionalPenalties additionalPenalties,
                           Double accuracy,
                           OptimizationMethod optimizer,
                           EndCriteria endCriteria,
                           AdditionalBootstrapVariables additionalVariables,
                           List<Double> instrumentWeights,
                           InitialGuessFn initialGuessFn) {
        this.additionalHelpers_ = additionalHelpers == null
                ? new ArrayList<>() : new ArrayList<>(additionalHelpers);
        this.additionalDates_ = additionalDates;
        this.additionalPenalties_ = additionalPenalties;
        this.additionalVariables_ = additionalVariables;
        this.initialGuessFn_ = initialGuessFn;
        this.accuracy_ = accuracy;
        this.optimizer_ = optimizer;
        this.endCriteria_ = endCriteria;
        this.instrumentWeights_ = toArray(instrumentWeights);
    }

    public GlobalBootstrap(List<RateHelper> additionalHelpers,
                           Supplier<List<Date>> additionalDates,
                           AdditionalPenalties additionalPenalties,
                           double accuracy) {
        this(additionalHelpers, additionalDates, additionalPenalties, accuracy,
                null, null, null, null, null);
    }

    /**
     * Mirrors the second constructor, {@code std::function<Array()>}, i.e. the
     * no-argument form. Both penalty forms are supported.
     */
    public GlobalBootstrap(List<RateHelper> additionalHelpers,
                           Supplier<List<Date>> additionalDates,
                           Supplier<Array> additionalPenaltiesNoArgs,
                           Double accuracy,
                           OptimizationMethod optimizer,
                           EndCriteria endCriteria,
                           AdditionalBootstrapVariables additionalVariables,
                           List<Double> instrumentWeights,
                           InitialGuessFn initialGuessFn) {
        this.additionalHelpers_ = additionalHelpers == null
                ? new ArrayList<>() : new ArrayList<>(additionalHelpers);
        this.additionalDates_ = additionalDates;
        this.additionalPenaltiesNoArgs_ = additionalPenaltiesNoArgs;
        this.additionalVariables_ = additionalVariables;
        this.initialGuessFn_ = initialGuessFn;
        this.accuracy_ = accuracy;
        this.optimizer_ = optimizer;
        this.endCriteria_ = endCriteria;
        this.instrumentWeights_ = toArray(instrumentWeights);
    }

    public GlobalBootstrap(List<RateHelper> additionalHelpers,
                           Supplier<List<Date>> additionalDates,
                           Supplier<Array> additionalPenaltiesNoArgs,
                           double accuracy) {
        this(additionalHelpers, additionalDates, additionalPenaltiesNoArgs, accuracy,
                null, null, null, null, null);
    }

    /**
     * Mirrors {@code GlobalBootstrap::setup}. Registration is idempotent (the
     * observers are held in sets), so repeating it here is harmless.
     */
    public void setup(PiecewiseYieldCurve ts) {
        ts_ = ts;
        for (RateHelper helper : ts_.instruments_)
            for (jquant.Quote quote : helper.notifications())
                ts_.registerWith(quote);
        for (RateHelper helper : additionalHelpers_)
            for (jquant.Quote quote : helper.notifications())
                ts_.registerWith(quote);

        // setup optimizer and EndCriteria
        double accuracy = accuracy_ != null ? accuracy_ : ts_.accuracy_;
        if (optimizer_ == null)
            optimizer_ = new LevenbergMarquardt(accuracy, accuracy, accuracy, false);
        if (endCriteria_ == null)
            endCriteria_ = new EndCriteria(1000, 10, accuracy, accuracy, accuracy);

        // check number of instrument weights
        QL_REQUIRE(instrumentWeights_.length == 0
                        || instrumentWeights_.length == ts_.instruments_.size(),
                "GlobalBootstrap: number of instrument weights ("
                        + instrumentWeights_.length + ") must match number of instruments ("
                        + ts_.instruments_.size() + ")");
        if (instrumentWeights_.length == 0) {
            instrumentWeights_ = new double[ts_.instruments_.size()];
            java.util.Arrays.fill(instrumentWeights_, 1.0);
        }

        // do not initialize yet: instruments could be invalid here
        // but valid later when bootstrapping is actually required
    }

    private void initialize() {
        Date firstDate = ts_.traits_.initialDate(ts_);

        // set up alive instruments and weights
        aliveInstruments_ = new ArrayList<>();
        List<Double> aliveWeights = new ArrayList<>();
        for (int i = 0; i < ts_.instruments_.size(); ++i) {
            if (TimeUtils.greater(ts_.instruments_.get(i).pillarDate(), firstDate)) {
                aliveInstruments_.add(ts_.instruments_.get(i));
                aliveWeights.add(instrumentWeights_[i]);
            }
        }
        aliveInstrumentWeights_ = new double[aliveWeights.size()];
        for (int i = 0; i < aliveWeights.size(); ++i)
            aliveInstrumentWeights_[i] = aliveWeights.get(i);

        // set up alive additional helpers
        aliveAdditionalHelpers_ = new ArrayList<>();
        for (RateHelper helper : additionalHelpers_)
            if (TimeUtils.greater(helper.pillarDate(), firstDate))
                aliveAdditionalHelpers_.add(helper);

        // skip expired additional dates
        List<Date> additionalDates = new ArrayList<>();
        if (additionalDates_ != null)
            additionalDates.addAll(additionalDates_.get());
        additionalDates.removeIf(date -> TimeUtils.leq(date, firstDate));

        // calculate dates and times
        List<Date> dates = new ArrayList<>();
        dates.add(firstDate);
        for (RateHelper helper : aliveInstruments_)
            dates.add(helper.pillarDate());
        dates.addAll(additionalDates);
        dates.sort(Comparator.comparingInt(Date::serialNumber));
        List<Date> unique = new ArrayList<>();
        for (Date date : dates)
            if (unique.isEmpty() || !TimeUtils.equals(unique.get(unique.size() - 1), date))
                unique.add(date);
        dates = unique;

        // check if there are enough interpolation points
        QL_REQUIRE(dates.size() >= ts_.interpolator_.getRequiredPoints(),
                "GlobalBootstrap: not enough curve points ("
                        + dates.size() + ") for interpolation requiring at least "
                        + ts_.interpolator_.getRequiredPoints());

        List<Date> oldDates = new ArrayList<>(ts_.dates_);
        ts_.dates_.clear();
        ts_.dates_.addAll(dates);
        boolean datesChanged = !sameDates(oldDates, dates);

        // build times vector
        if (ts_.times_ == null || ts_.times_.length != dates.size()) {
            ts_.times_ = new double[dates.size()];
            interpolationDirty_ = true;
        }
        for (int i = 0; i < dates.size(); ++i)
            ts_.times_[i] = ts_.timeFromReference(dates.get(i));
        if (datesChanged)
            interpolationDirty_ = true;

        // determine maxDate ensuring all instruments and additional helpers are covered
        ts_.maxDate_ = dates.get(dates.size() - 1);
        for (RateHelper helper : aliveInstruments_)
            if (TimeUtils.greater(helper.latestRelevantDate(), ts_.maxDate_))
                ts_.maxDate_ = helper.latestRelevantDate();
        for (RateHelper helper : aliveAdditionalHelpers_)
            if (TimeUtils.greater(helper.latestRelevantDate(), ts_.maxDate_))
                ts_.maxDate_ = helper.latestRelevantDate();

        // set initial guess only if the current curve cannot be used as guess
        if (!validCurve_ || ts_.data_ == null || ts_.data_.length != dates.size()) {
            // ts_.data_[0] is the only relevant item, but reasonable numbers
            // might be needed for the whole data vector because, e.g., of
            // interpolation's early checks
            ts_.data_ = new double[dates.size()];
            java.util.Arrays.fill(ts_.data_, ts_.traits_.initialValue(ts_));
            validCurve_ = false;
        }
        initialized_ = true;
    }

    private static boolean sameDates(List<Date> a, List<Date> b) {
        if (a.size() != b.size())
            return false;
        for (int i = 0; i < a.size(); ++i)
            if (!TimeUtils.equals(a.get(i), b.get(i)))
                return false;
        return true;
    }

    private Array setupCostFunction() {
        // We might have to call initialize even if the curve is initialized and
        // not moving, just because helpers might be date relative and change
        // with evaluation date change.
        if (!initialized_ || ts_.moving())
            initialize();

        // setup helpers
        for (RateHelper helper : aliveInstruments_) {
            QL_REQUIRE(helper.quote().getValue().isValid(),
                    "instrument (maturity: " + helper.maturityDate()
                            + ", pillar: " + helper.pillarDate()
                            + ") has an invalid quote");
            helper.setTermStructure(ts_);
        }

        // setup additional helpers
        for (RateHelper helper : aliveAdditionalHelpers_) {
            QL_REQUIRE(helper.quote().getValue().isValid(),
                    "additional instrument (maturity: " + helper.maturityDate()
                            + ") has an invalid quote");
            helper.setTermStructure(ts_);
        }

        // setup interpolation
        if (!validCurve_ || interpolationDirty_) {
            ts_.interpolation_ = ts_.interpolator_.interpolate(ts_.times_, ts_.data_);
            interpolationDirty_ = false;
        }

        // Initial guess. We have guesses for the curve values first
        // (numberPillars), followed by guesses for the additional variables.
        Array guess = makeInitialGuess();
        if (additionalVariables_ != null) {
            Array additionalGuesses = additionalVariables_.initialize(validCurve_);
            int oldSize = guess.size();
            Array extended = new Array(oldSize + additionalGuesses.size());
            for (int i = 0; i < oldSize; ++i)
                extended.set(i, guess.get(i));
            for (int i = 0; i < additionalGuesses.size(); ++i)
                extended.set(oldSize + i, additionalGuesses.get(i));
            guess = extended;
        }
        return guess;
    }

    private Array makeInitialGuess() {
        Array guess;
        boolean needsTransform = true;
        if (initialGuessFn_ != null) {
            double[] noData = new double[0];
            guess = initialGuessFn_.apply(ts_.times_, validCurve_ ? ts_.data_ : noData);
        } else {
            BootstrapTraits.GlobalGuess result =
                    ts_.traits_.globalGuess(ts_, validCurve_);
            guess = result.guess;
            needsTransform = result.needsTransform;
        }
        if (ts_.traits_.hasTransform() && needsTransform) {
            for (int i = 0; i < guess.size(); ++i)
                guess.set(i, ts_.traits_.transformInverse(guess.get(i), i + 1, ts_));
        }
        return guess;
    }

    private void setCostFunctionArgument(Array x) {
        // x has the same layout as guess above: the first numberPillars values
        // go into the curve, while the rest are new values for the additional
        // variables.
        for (int i = 0; i < ts_.times_.length - 1; ++i) {
            double value = x.get(i);
            if (ts_.traits_.hasTransform())
                value = ts_.traits_.transformDirect(value, i + 1, ts_);
            ts_.traits_.updateGuess(ts_.data_, value, i + 1);
        }
        ts_.interpolation_.update();
        if (additionalVariables_ != null) {
            Array additional = new Array(x.size() - (ts_.times_.length - 1));
            for (int i = 0; i < additional.size(); ++i)
                additional.set(i, x.get(ts_.times_.length - 1 + i));
            additionalVariables_.update(additional);
        }
    }

    private Array evaluateCostFunction() {
        Array additionalErrors;
        if (additionalPenalties_ != null)
            additionalErrors = additionalPenalties_.apply(ts_.times_, ts_.data_);
        else if (additionalPenaltiesNoArgs_ != null)
            additionalErrors = additionalPenaltiesNoArgs_.get();
        else
            additionalErrors = new Array(0);

        Array result = new Array(aliveInstruments_.size() + additionalErrors.size());
        for (int i = 0; i < aliveInstruments_.size(); ++i)
            result.set(i, aliveInstruments_.get(i).quoteError() * aliveInstrumentWeights_[i]);
        for (int i = 0; i < additionalErrors.size(); ++i)
            result.set(aliveInstruments_.size() + i, additionalErrors.get(i));
        return result;
    }

    /**
     * Mirrors {@code GlobalBootstrap::calculate} for a single curve.
     *
     * <p>Multi-curve joint bootstrap is not implemented; there is no
     * {@code MultiCurveBootstrap} parent in this port.
     */
    public void calculate() {
        Array guess = setupCostFunction();

        NoConstraint noConstraint = new NoConstraint();

        SimpleCostFunction costFunction = new SimpleCostFunction(x -> {
            setCostFunctionArgument(x);
            return evaluateCostFunction();
        });

        Problem problem = new Problem(costFunction, noConstraint, guess);
        EndCriteria.Type endType = optimizer_.minimize(problem, endCriteria_);
        QL_REQUIRE(EndCriteria.succeeded(endType),
                "global bootstrap failed to minimize to required accuracy: " + endType);
        validCurve_ = true;
    }

    /** Called by {@link PiecewiseYieldCurve} (mirrors the C++ {@code calculate(this)}). */
    public void calculate(PiecewiseYieldCurve ts) {
        if (ts_ == null)
            setup(ts);
        calculate();
    }

    private static double[] toArray(List<Double> values) {
        if (values == null)
            return new double[0];
        double[] result = new double[values.size()];
        for (int i = 0; i < result.length; ++i)
            result[i] = values.get(i);
        return result;
    }
}
