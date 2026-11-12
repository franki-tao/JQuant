package jquant.termstructures.yield;

import jquant.math.Function;
import jquant.math.Interpolation;
import jquant.math.interpolations.impl.Interpolator;
import jquant.math.interpolations.impl.Linear;
import jquant.math.interpolations.impl.LogLinear;
import jquant.math.Solver1D;
import jquant.math.solvers1d.Brent;
import jquant.math.solvers1d.FiniteDifferenceNewtonSafe;
import jquant.termstructures.YieldTermStructure;
import jquant.time.Date;
import jquant.time.DayCounter;
import jquant.time.TimeUtils;
import jquant.time.calendars.NullCalendar;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

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
public class PiecewiseYieldCurve extends YieldTermStructure {
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
    private final LocalBootstrap localBootstrap_;
    private final GlobalBootstrap globalBootstrap_;
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
        this(referenceDate, instruments, dayCounter, traits, interpolator, accuracy, bootstrap, null);
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
        this(referenceDate, instruments, dayCounter, traits, interpolator, accuracy, null, bootstrap);
    }

    private PiecewiseYieldCurve(Date referenceDate,
                                List<RateHelper> instruments,
                                DayCounter dayCounter,
                                BootstrapTraits traits,
                                Interpolator interpolator,
                                double accuracy,
                                LocalBootstrap localBootstrap,
                                GlobalBootstrap globalBootstrap) {
        super(referenceDate, new NullCalendar(), dayCounter, new ArrayList<>(), new ArrayList<>());
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
        localBootstrap_ = localBootstrap;
        globalBootstrap_ = globalBootstrap;
        for (RateHelper helper : instruments_) {
            for (jquant.Quote quote : helper.notifications())
                registerWith(quote);
        }
        if (globalBootstrap_ != null)
            globalBootstrap_.setup(this);
        else if (localBootstrap_ != null)
            localBootstrap_.setup(this);
        bootstrap();
    }

    @Override
    public void update() {
        // Quote changed. Rebuild on the next discount, and tell instruments
        // now so a cached NPV is not reused.
        stale_ = true;
        super.update();
    }

    public List<Date> dates() {
        return List.copyOf(dates_);
    }

    double[] data() {
        return data_;
    }

    double[] times() {
        return times_;
    }

    Interpolation interpolation() {
        return interpolation_;
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
    public Date maxDate() {
        return maxDate_;
    }

    @Override
    protected double discountImpl(double t) {
        if (stale_ && !bootstrapping_)
            bootstrap();
        return traits_.discountImpl(this, t);
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
                if (change <= accuracy_) {
                    bootstrapIterations_ = iteration;
                    break;
                }
            }
            QL_REQUIRE(iteration < traits_.maxIterations(),
                    "convergence not reached after " + iteration + " iterations");
            validData = true;
        }
    }

    private void solvePillar(int i, int j, boolean validData) {
        double min = traits_.minValueAfter(i, this, validData);
        double max = traits_.maxValueAfter(i, this, validData);
        double guess = traits_.guess(i, this, validData);
        if (guess >= max)
            guess = max - (max - min) / 5.0;
        else if (guess <= min)
            guess = min + (max - min) / 5.0;

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
        try {
            double value = solver.solve(error, accuracy_, guess, min, max);
            traits_.updateGuess(data_, value, node);
            refreshInterpolation(node, validData);
        } catch (IllegalArgumentException e) {
            QL_FAIL("failed at alive instrument " + i
                    + ", pillar " + helper.pillarDate()
                    + ", maturity " + helper.maturityDate()
                    + ", reference date " + dates_.get(0)
                    + ": " + e.getMessage());
        }
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
