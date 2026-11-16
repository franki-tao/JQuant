package jquant.termstructures.yield;

import jquant.Handle;
import jquant.math.Function;
import jquant.math.Interpolation;
import jquant.math.interpolations.impl.Interpolator;
import jquant.math.interpolations.impl.Linear;
import jquant.math.interpolations.impl.LogLinear;
import jquant.math.Solver1D;
import jquant.math.solvers1d.Brent;
import jquant.math.solvers1d.FiniteDifferenceNewtonSafe;
import jquant.termstructures.MultiCurveBootstrapProvider;
import jquant.termstructures.YieldTermStructure;
import jquant.time.Calendar;
import jquant.time.Date;
import jquant.time.DayCounter;
import jquant.time.TimeUtils;
import jquant.time.calendars.NullCalendar;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static jquant.math.CommonUtil.QL_FAIL;
import static jquant.math.CommonUtil.QL_REQUIRE;

/**
 * Piecewise yield curve bootstrapped from rate helpers. Nodes are discount
 * factors by default ({@link BootstrapTraits.Discount}, the original behaviour),
 * zero rates ({@link BootstrapTraits.ZeroYield}) or forward rates
 * ({@link BootstrapTraits.ForwardRate}). Log-linear interpolation is local.
 * Log-cubic is global, so every pillar is solved again until the whole node
 * vector stops moving.
 *
 * <p>https://github.com/lballabio/QuantLib/blob/master/ql/termstructures/yield/piecewiseyieldcurve.hpp
 * <p>https://github.com/lballabio/QuantLib/blob/master/ql/termstructures/iterativebootstrap.hpp
 * <p>https://github.com/lballabio/QuantLib/blob/master/ql/termstructures/yield/bootstraptraits.hpp
 * <p>https://github.com/lballabio/QuantLib/blob/master/ql/termstructures/yield/interpolatedforwardcurve.hpp
 */
public class PiecewiseYieldCurve extends YieldTermStructure
        implements MultiCurveBootstrapProvider {
    // Package-visible to LocalBootstrap, as the friend declaration in
    // localbootstrap.hpp allows.
    final List<RateHelper> instruments_;
    final BootstrapTraits traits_;
    final Interpolator interpolator_;
    final double accuracy_;
    final List<Date> dates_ = new ArrayList<>();
    double[] times_;
    double[] data_;
    Date maxDate_;
    Interpolation interpolation_;
    /** Base curve for a multiplicative spread curve; {@code null} otherwise. */
    private final Handle<YieldTermStructure> baseCurve_;
    private final LocalBootstrap localBootstrap_;
    private final GlobalBootstrap globalBootstrap_;
    private final IterativeBootstrap iterativeBootstrap_;
    private int bootstrapIterations_;
    private boolean loopRequired;
    private boolean stale_ = false;
    private boolean bootstrapping_ = false;

    public PiecewiseYieldCurve(Date referenceDate,
                               List<RateHelper> instruments,
                               DayCounter dayCounter) {
        this(referenceDate, instruments, dayCounter, new LogLinear(), 1.0e-12);
    }

    public PiecewiseYieldCurve(Date referenceDate,
                               List<RateHelper> instruments,
                               DayCounter dayCounter,
                               Interpolator interpolator,
                               double accuracy) {
        this(referenceDate, instruments, dayCounter, BootstrapTraits.Discount,
                interpolator, accuracy);
    }

    /**
     * Bootstraps a curve whose nodes follow {@code traits} (discount factors
     * or forward rates), interpolated by {@code interpolator}. This mirrors
     * {@code PiecewiseYieldCurve<Traits, Interpolator>} in QuantLib.
     *
     * <p>https://github.com/lballabio/QuantLib/blob/master/ql/termstructures/yield/piecewiseyieldcurve.hpp
     */
    public PiecewiseYieldCurve(Date referenceDate,
                               List<RateHelper> instruments,
                               DayCounter dayCounter,
                               BootstrapTraits traits,
                               Interpolator interpolator) {
        this(referenceDate, instruments, dayCounter, traits, interpolator, 1.0e-12);
    }

    public PiecewiseYieldCurve(Date referenceDate,
                               List<RateHelper> instruments,
                               DayCounter dayCounter,
                               BootstrapTraits traits,
                               Interpolator interpolator,
                               double accuracy) {
        this(referenceDate, instruments, dayCounter, traits, interpolator, accuracy,
                (LocalBootstrap) null);
    }

    /**
     * Builds the curve with QuantLib's {@code LocalBootstrap} algorithm instead
     * of the iterative one. Only the localised-interpolation solvers (currently
     * {@link jquant.math.interpolations.impl.ConvexMonotone}) are supported.
     *
     * <p>https://github.com/lballabio/QuantLib/blob/master/ql/termstructures/localbootstrap.hpp
     */
    public PiecewiseYieldCurve(Date referenceDate,
                               List<RateHelper> instruments,
                               DayCounter dayCounter,
                               BootstrapTraits traits,
                               Interpolator interpolator,
                               LocalBootstrap bootstrap) {
        this(referenceDate, instruments, dayCounter, traits, interpolator, 1.0e-12, bootstrap);
    }

    /**
     * @param bootstrap {@code null} keeps the existing iterative bootstrap;
     *                  otherwise {@code calculate()} replaces the iterative
     *                  pillar loop.
     */
    public PiecewiseYieldCurve(Date referenceDate,
                               List<RateHelper> instruments,
                               DayCounter dayCounter,
                               BootstrapTraits traits,
                               Interpolator interpolator,
                               double accuracy,
                               LocalBootstrap bootstrap) {
        this(referenceDate, new NullCalendar(), instruments, dayCounter, traits,
                interpolator, accuracy, bootstrap, null, null, null);
    }

    /**
     * Builds the curve with QuantLib's {@code GlobalBootstrap} algorithm
     * instead of the iterative one.
     *
     * <p>https://github.com/lballabio/QuantLib/blob/master/ql/termstructures/globalbootstrap.hpp
     */
    public PiecewiseYieldCurve(Date referenceDate,
                               List<RateHelper> instruments,
                               DayCounter dayCounter,
                               BootstrapTraits traits,
                               Interpolator interpolator,
                               GlobalBootstrap bootstrap) {
        this(referenceDate, instruments, dayCounter, traits, interpolator, 1.0e-12, bootstrap);
    }

    /**
     * @param bootstrap {@code null} keeps the existing iterative bootstrap;
     *                  otherwise {@code calculate()} replaces the iterative
     *                  pillar loop.
     */
    public PiecewiseYieldCurve(Date referenceDate,
                               List<RateHelper> instruments,
                               DayCounter dayCounter,
                               BootstrapTraits traits,
                               Interpolator interpolator,
                               double accuracy,
                               GlobalBootstrap bootstrap) {
        this(referenceDate, new NullCalendar(), instruments, dayCounter, traits,
                interpolator, accuracy, null, bootstrap, null, null);
    }

    /**
     * Builds the curve with an explicit {@link IterativeBootstrap}, as
     * {@code PiecewiseYieldCurve<T, I, IterativeBootstrap>::bootstrap_type()}
     * does in QuantLib.
     *
     * <p>https://github.com/lballabio/QuantLib/blob/master/ql/termstructures/iterativebootstrap.hpp
     */
    public PiecewiseYieldCurve(Date referenceDate,
                               List<RateHelper> instruments,
                               DayCounter dayCounter,
                               BootstrapTraits traits,
                               Interpolator interpolator,
                               IterativeBootstrap bootstrap) {
        this(referenceDate, instruments, dayCounter, traits, interpolator, 1.0e-12, bootstrap);
    }

    public PiecewiseYieldCurve(Date referenceDate,
                               List<RateHelper> instruments,
                               DayCounter dayCounter,
                               BootstrapTraits traits,
                               Interpolator interpolator,
                               double accuracy,
                               IterativeBootstrap bootstrap) {
        this(referenceDate, new NullCalendar(), instruments, dayCounter, traits,
                interpolator, accuracy, null, null, bootstrap, null);
    }

    /**
     * Builds a curve whose nodes are multiplicative spread discounts over a
     * base curve: the reference date, calendar and day counter come from the
     * base curve, and each discount is
     * {@code baseCurve.discount(t) * spreadCurve.discount(t)}.
     *
     * <p>This is the constructor used by {@link PiecewiseSpreadYieldCurve}; it
     * mirrors the protected
     * {@code PiecewiseYieldCurve(instruments, bootstrap, baseCurve, i)}
     * overload of QuantLib.
     *
     * <p>https://github.com/lballabio/QuantLib/blob/master/ql/termstructures/yield/piecewiseyieldcurve.hpp
     */
    protected PiecewiseYieldCurve(Handle<YieldTermStructure> baseCurve,
                                  List<RateHelper> instruments,
                                  BootstrapTraits traits,
                                  Interpolator interpolator,
                                  IterativeBootstrap bootstrap) {
        this(baseReferenceDate(baseCurve), baseCalendar(baseCurve), instruments,
                baseDayCounter(baseCurve), traits, interpolator, 1.0e-12,
                null, null, bootstrap, requireBaseCurve(baseCurve));
    }

    /** @see #PiecewiseYieldCurve(Handle, List, BootstrapTraits, Interpolator, IterativeBootstrap) */
    protected PiecewiseYieldCurve(Handle<YieldTermStructure> baseCurve,
                                  List<RateHelper> instruments,
                                  BootstrapTraits traits,
                                  Interpolator interpolator,
                                  GlobalBootstrap bootstrap) {
        this(baseReferenceDate(baseCurve), baseCalendar(baseCurve), instruments,
                baseDayCounter(baseCurve), traits, interpolator, 1.0e-12,
                null, bootstrap, null, requireBaseCurve(baseCurve));
    }

    private PiecewiseYieldCurve(Date referenceDate,
                                Calendar calendar,
                                List<RateHelper> instruments,
                                DayCounter dayCounter,
                                BootstrapTraits traits,
                                Interpolator interpolator,
                                double accuracy,
                                LocalBootstrap localBootstrap,
                                GlobalBootstrap globalBootstrap,
                                IterativeBootstrap iterativeBootstrap,
                                Handle<YieldTermStructure> baseCurve) {
        super(referenceDate, calendar, dayCounter, new ArrayList<>(), new ArrayList<>());
        // GlobalBootstrap may take all its helpers in additionalHelpers, so the
        // regular helper list can be empty (official IterativeBootstrap and
        // LocalBootstrap still reject that).
        QL_REQUIRE(instruments != null && (!instruments.isEmpty() || globalBootstrap != null),
                "no bootstrap helpers given");
        QL_REQUIRE(traits != null, "null bootstrap traits");
        QL_REQUIRE(interpolator != null, "null interpolator");
        QL_REQUIRE(accuracy > 0.0, "accuracy must be positive");
        instruments_ = new ArrayList<>(instruments);
        traits_ = traits;
        interpolator_ = interpolator;
        accuracy_ = accuracy;
        baseCurve_ = baseCurve;
        if (baseCurve_ != null && !baseCurve_.empty()) {
            // RelativeDerivedYieldTermStructure observes the original curve
            // and inherits its extrapolation behaviour.
            registerWith(baseCurve_.get());
            if (baseCurve_.currentLink().allowsExtrapolation())
                enableExtrapolation();
            else
                disableExtrapolation();
        }
        localBootstrap_ = localBootstrap;
        globalBootstrap_ = globalBootstrap;
        iterativeBootstrap_ = iterativeBootstrap;
        for (RateHelper helper : instruments_) {
            for (jquant.Quote quote : helper.notifications())
                registerWith(quote);
            for (jquant.patterns.Observable observable : helper.notificationObservables())
                registerWith(observable);
        }
        if (globalBootstrap_ != null) {
            // As in QuantLib, setup() does not calculate. Defer the actual
            // bootstrap until the curve is first used, so that a MultiCurve
            // can install itself as the parent bootstrapper first.
            globalBootstrap_.setup(this);
            stale_ = true;
        } else {
            if (localBootstrap_ != null)
                localBootstrap_.setup(this);
            if (iterativeBootstrap_ != null)
                iterativeBootstrap_.setup(this);
            bootstrap();
        }
    }

    @Override
    public MultiCurveBootstrapContributor multiCurveBootstrapContributor() {
        return globalBootstrap_;
    }

    /**
     * Mirrors {@code LazyObject::setCalculated}. A calculated curve is not
     * stale, which prevents a member curve from re-entering a nested
     * bootstrap while the joint multi-curve solve is running.
     */
    void setCalculated(boolean calculated) {
        stale_ = !calculated;
    }

    /** Re-run a deferred bootstrap if the curve is stale. */
    private void calculateIfNeeded() {
        if (stale_ && !bootstrapping_)
            bootstrap();
    }

    @Override
    public void update() {
        // Quote changed. Rebuild on the next discount, and tell instruments
        // now so a cached NPV is not reused.
        stale_ = true;
        if (baseCurve_ != null && !baseCurve_.empty()) {
            // RelativeDerivedYieldTermStructure::setupExtrapolation
            if (baseCurve_.currentLink().allowsExtrapolation())
                enableExtrapolation();
            else
                disableExtrapolation();
        }
        super.update();
    }

    public List<Date> dates() {
        calculateIfNeeded();
        return List.copyOf(dates_);
    }

    public double[] data() {
        calculateIfNeeded();
        return data_;
    }

    public double[] times() {
        calculateIfNeeded();
        return times_;
    }

    /** Date/value pairs, as the official {@code InterpolatedNodes::nodes()}. */
    public List<Map.Entry<Date, Double>> nodes() {
        calculateIfNeeded();
        List<Map.Entry<Date, Double>> nodes = new ArrayList<>(dates_.size());
        for (int i = 0; i < dates_.size(); ++i)
            nodes.add(Map.entry(dates_.get(i), data_[i]));
        return nodes;
    }

    Interpolation interpolation() {
        calculateIfNeeded();
        return interpolation_;
    }

    /** The base curve of a spread curve, or {@code null} for a standalone one. */
    Handle<YieldTermStructure> baseCurveHandle() {
        return baseCurve_;
    }

    /** How many pillar passes the iterative bootstrap used. */
    public int bootstrapIterations() {
        return bootstrapIterations_;
    }

    /** Package-visible {@code moving_} for GlobalBootstrap, as a friend would see. */
    boolean moving() {
        return moving_;
    }

    @Override
    public Date referenceDate() {
        // RelativeDerivedYieldTermStructure: a spread curve uses the reference
        // date of its base curve. During the superclass constructor the field
        // is not assigned yet, so fall back to the explicitly passed date.
        if (baseCurve_ != null)
            return baseCurve_.currentLink().referenceDate();
        return super.referenceDate();
    }

    @Override
    public DayCounter dayCounter() {
        if (baseCurve_ != null)
            return baseCurve_.currentLink().dayCounter();
        return super.dayCounter();
    }

    @Override
    public Calendar calendar() {
        if (baseCurve_ != null)
            return baseCurve_.currentLink().calendar();
        return super.calendar();
    }

    @Override
    public int settlementDays() {
        if (baseCurve_ != null)
            return baseCurve_.currentLink().settlementDays();
        return super.settlementDays();
    }

    @Override
    public Date maxDate() {
        calculateIfNeeded();
        if (baseCurve_ != null) {
            // InterpolatedSpreadDiscountCurve::maxDate:
            //   min(originalCurve_->maxDate(), maxDate_ or dates_.back())
            Date curveMax = maxDate_;
            if (curveMax == null && !dates_.isEmpty())
                curveMax = dates_.get(dates_.size() - 1);
            QL_REQUIRE(curveMax != null, "curve has no dates yet");
            return TimeUtils.min(baseCurve_.currentLink().maxDate(), curveMax);
        }
        return maxDate_;
    }

    @Override
    protected double discountImpl(double t) {
        if (stale_ && !bootstrapping_)
            bootstrap();
        return traits_.discountImpl(this, t);
    }

    private static Handle<YieldTermStructure> requireBaseCurve(Handle<YieldTermStructure> baseCurve) {
        QL_REQUIRE(baseCurve != null, "null base curve handle");
        QL_REQUIRE(!baseCurve.empty(), "empty base curve handle");
        return baseCurve;
    }

    private static Date baseReferenceDate(Handle<YieldTermStructure> baseCurve) {
        return requireBaseCurve(baseCurve).currentLink().referenceDate();
    }

    private static Calendar baseCalendar(Handle<YieldTermStructure> baseCurve) {
        return requireBaseCurve(baseCurve).currentLink().calendar();
    }

    private static DayCounter baseDayCounter(Handle<YieldTermStructure> baseCurve) {
        return requireBaseCurve(baseCurve).currentLink().dayCounter();
    }

    private void bootstrap() {
        // Helpers call discount while the pillars are still moving. A second
        // entry would bootstrap forever on the half-built nodes.
        bootstrapping_ = true;
        try {
            if (globalBootstrap_ != null)
                globalBootstrap_.calculate(this);
            else if (localBootstrap_ != null)
                localBootstrap_.calculate(this);
            else
                bootstrapPillars();
            stale_ = false;
        } finally {
            bootstrapping_ = false;
        }
    }

    private void bootstrapPillars() {
        instruments_.sort((a, b) -> {
            if (TimeUtils.less(a.pillarDate(), b.pillarDate()))
                return -1;
            if (TimeUtils.greater(a.pillarDate(), b.pillarDate()))
                return 1;
            return 0;
        });
        Date firstDate = referenceDate();
        QL_REQUIRE(TimeUtils.greater(instruments_.get(instruments_.size() - 1).pillarDate(), firstDate),
                "all instruments expired");
        int firstAlive = 0;
        while (!TimeUtils.greater(instruments_.get(firstAlive).pillarDate(), firstDate))
            ++firstAlive;
        int alive = instruments_.size() - firstAlive;
        QL_REQUIRE(alive + 1 >= interpolator_.getRequiredPoints(),
                "not enough alive instruments: " + alive + " provided");

        // A global scheme such as a cubic spline changes every node when one
        // pillar moves, so the pass repeats until the discount vector settles.
        loopRequired = interpolator_.global();
        dates_.clear();
        dates_.add(firstDate);
        times_ = new double[alive + 1];
        data_ = new double[alive + 1];
        times_[0] = timeFromReference(firstDate);
        data_[0] = traits_.initialValue(this);
        Date maxDate = firstDate;
        for (int i = 1, j = firstAlive; j < instruments_.size(); ++i, ++j) {
            RateHelper helper = instruments_.get(j);
            dates_.add(helper.pillarDate());
            times_[i] = timeFromReference(helper.pillarDate());
            data_[i] = traits_.initialValue(this);
            QL_REQUIRE(!TimeUtils.equals(dates_.get(i - 1), dates_.get(i)),
                    "more than one instrument with pillar " + dates_.get(i));
            Date latestRelevantDate = helper.latestRelevantDate();
            QL_REQUIRE(TimeUtils.greater(latestRelevantDate, maxDate),
                    "instrument has latestRelevantDate before or equal to the previous one");
            // A local interpolator still needs another pass when a helper's
            // latest relevant date is after its pillar.
            if (TimeUtils.less(dates_.get(i), latestRelevantDate))
                loopRequired = true;
            maxDate = TimeUtils.greater(dates_.get(i), latestRelevantDate)
                    ? dates_.get(i) : latestRelevantDate;
        }
        maxDate_ = maxDate;
        interpolation_ = interpolator_.interpolate(times_, data_);

        for (int j = firstAlive; j < instruments_.size(); ++j)
            instruments_.get(j).setTermStructure(this);

        double accuracy = bootstrapAccuracy();
        boolean validData = false;
        int iteration = 0;
        while (true) {
            iteration++;
            boolean firstPass = !validData;
            double[] previous = null;
            if (loopRequired && validData)
                previous = data_.clone();
            for (int i = 1, j = firstAlive; j < instruments_.size(); ++i, ++j)
                solvePillar(i, j, validData);
            // The first global pass may have used a prefix or a linear stand-in.
            // Later passes update the cubic built on the live discount vector.
            if (interpolator_.global() && firstPass) {
                interpolation_ = interpolator_.interpolate(times_, data_);
                interpolation_.update();
            }
            if (!loopRequired) {
                bootstrapIterations_ = iteration;
                break;
            }
            if (validData) {
                double change = 0.0;
                for (int i = 1; i < data_.length; i++)
                    change = Math.max(change, Math.abs(data_[i] - previous[i]));
                if (change <= accuracy) {
                    bootstrapIterations_ = iteration;
                    break;
                }
            }
            if (iteration >= traits_.maxIterations()) {
                if (iterativeBootstrap_ != null && iterativeBootstrap_.dontThrow()) {
                    bootstrapIterations_ = iteration;
                    break;
                }
                QL_FAIL("convergence not reached after " + iteration + " iterations");
            }
            validData = true;
        }
    }

    private void solvePillar(int i, int j, boolean validData) {
        Double minOverride = iterativeBootstrap_ != null ? iterativeBootstrap_.minValue() : null;
        Double maxOverride = iterativeBootstrap_ != null ? iterativeBootstrap_.maxValue() : null;
        double min = minOverride != null
                ? minOverride : traits_.minValueAfter(i, this, validData);
        double max = maxOverride != null
                ? maxOverride : traits_.maxValueAfter(i, this, validData);
        double guess = traits_.guess(i, this, validData);

        int maxAttempts = iterativeBootstrap_ != null ? iterativeBootstrap_.maxAttempts() : 1;
        double minFactor = iterativeBootstrap_ != null ? iterativeBootstrap_.minFactor() : 2.0;
        double maxFactor = iterativeBootstrap_ != null ? iterativeBootstrap_.maxFactor() : 2.0;

        RateHelper helper = instruments_.get(j);
        int node = i;
        Function error = x -> {
            traits_.updateGuess(data_, x, node);
            refreshInterpolation(node, validData);
            return helper.quoteError();
        };
        // Later global passes stay near the previous pillar, as QuantLib's
        // FiniteDifferenceNewtonSafe does. Brent is used to bracket the first pass.
        Solver1D solver = interpolator_.global() && validData
                ? new FiniteDifferenceNewtonSafe()
                : new Brent();
        if (iterativeBootstrap_ != null)
            solver.setMaxEvaluations(iterativeBootstrap_.maxEvaluations());
        double accuracy = bootstrapAccuracy();

        for (int attempt = 1; ; attempt++) {
            double adjustedGuess = guess;
            if (adjustedGuess >= max)
                adjustedGuess = max - (max - min) / 5.0;
            else if (adjustedGuess <= min)
                adjustedGuess = min + (max - min) / 5.0;
            try {
                double value = solver.solve(error, accuracy, adjustedGuess, min, max);
                traits_.updateGuess(data_, value, node);
                refreshInterpolation(node, validData);
                return;
            } catch (IllegalArgumentException e) {
                // IterativeBootstrap widens the bracket on each retry.
                if (attempt < maxAttempts) {
                    min = min < 0.0 ? min * minFactor : min / minFactor;
                    max = max > 0.0 ? max * maxFactor : max / maxFactor;
                    guess = traits_.guess(i, this, validData);
                    continue;
                }
                if (iterativeBootstrap_ != null && iterativeBootstrap_.dontThrow()) {
                    double value = dontThrowFallback(error, min, max,
                            iterativeBootstrap_.dontThrowSteps());
                    traits_.updateGuess(data_, value, node);
                    refreshInterpolation(node, validData);
                    return;
                }
                QL_FAIL("failed at alive instrument " + i
                        + ", pillar " + helper.pillarDate()
                        + ", maturity " + helper.maturityDate()
                        + ", reference date " + dates_.get(0)
                        + ": " + e.getMessage());
            }
        }
    }

    /**
     * {@code detail::dontThrowFallback} from iterativebootstrap.hpp: the point
     * of the bracket with the smallest absolute helper error.
     */
    private double dontThrowFallback(Function error, double xMin, double xMax, int steps) {
        QL_REQUIRE(xMin < xMax, "Expected xMin to be less than xMax");
        double result = xMin;
        double absError = Math.abs(error.value(xMin));
        double minError = absError;
        double stepSize = (xMax - xMin) / steps;
        for (int i = 0; i < steps; i++) {
            xMin += stepSize;
            absError = Math.abs(error.value(xMin));
            if (absError < minError) {
                result = xMin;
                minError = absError;
            }
        }
        return result;
    }

    /** Bootstrap accuracy: the explicit {@link IterativeBootstrap} override
     *  ({@code Null} by default), otherwise the curve's accuracy. */
    private double bootstrapAccuracy() {
        if (iterativeBootstrap_ != null && iterativeBootstrap_.accuracy() != null)
            return iterativeBootstrap_.accuracy();
        return accuracy_;
    }

    /**
     * A global interpolator is built only on pillars already solved. If that
     * spline cannot be formed yet, the first pass uses a linear stand-in,
     * as {@code IterativeBootstrap} does.
     */
    private void refreshInterpolation(int node, boolean validData) {
        if (!interpolator_.global() || validData) {
            interpolation_.update();
            return;
        }
        double[] x = Arrays.copyOf(times_, node + 1);
        double[] y = Arrays.copyOf(data_, node + 1);
        try {
            interpolation_ = interpolator_.interpolate(x, y);
        } catch (RuntimeException ex) {
            interpolation_ = new Linear().interpolate(x, y);
        }
        interpolation_.update();
    }
}
