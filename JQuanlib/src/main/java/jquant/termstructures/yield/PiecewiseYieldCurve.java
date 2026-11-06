package jquant.termstructures.yield;

import jquant.math.Function;
import jquant.math.Interpolation;
import jquant.math.interpolations.impl.LogLinear;
import jquant.math.solvers1d.Brent;
import jquant.termstructures.YieldTermStructure;
import jquant.time.Date;
import jquant.time.DayCounter;
import jquant.time.TimeUtils;
import jquant.time.calendars.NullCalendar;

import java.util.ArrayList;
import java.util.List;

import static jquant.math.CommonUtil.QL_FAIL;
import static jquant.math.CommonUtil.QL_REQUIRE;

/**
 * Log-linear discount curve bootstrapped one pillar at a time.
 * This is {@code PiecewiseYieldCurve<Discount, LogLinear, IterativeBootstrap>}
 * for helpers whose pillar date is the latest relevant date. The interpolated
 * values are discount factors. The reference-date discount is 1.
 *
 * <p>https://github.com/lballabio/QuantLib/blob/master/ql/termstructures/yield/piecewiseyieldcurve.hpp
 * <p>https://github.com/lballabio/QuantLib/blob/master/ql/termstructures/iterativebootstrap.hpp
 * <p>https://github.com/lballabio/QuantLib/blob/master/ql/termstructures/yield/bootstraptraits.hpp
 */
public class PiecewiseYieldCurve extends YieldTermStructure {
    private static final double AVERAGE_RATE = 0.05;
    private static final double MAX_RATE = 1.0;

    private final List<RateHelper> instruments_;
    private final LogLinear interpolator_;
    private final double accuracy_;
    private final List<Date> dates_ = new ArrayList<>();
    private double[] times_;
    private double[] data_;
    private Date maxDate_;
    private Interpolation interpolation_;
    private int bootstrapIterations_;
    private boolean loopRequired;

    public PiecewiseYieldCurve(Date referenceDate,
                               List<RateHelper> instruments,
                               DayCounter dayCounter) {
        this(referenceDate, instruments, dayCounter, new LogLinear(), 1.0e-12);
    }

    public PiecewiseYieldCurve(Date referenceDate,
                               List<RateHelper> instruments,
                               DayCounter dayCounter,
                               LogLinear interpolator,
                               double accuracy) {
        super(referenceDate, new NullCalendar(), dayCounter, new ArrayList<>(), new ArrayList<>());
        QL_REQUIRE(instruments != null && !instruments.isEmpty(), "no bootstrap helpers given");
        QL_REQUIRE(interpolator != null, "null interpolator");
        QL_REQUIRE(accuracy > 0.0, "accuracy must be positive");
        instruments_ = new ArrayList<>(instruments);
        interpolator_ = interpolator;
        accuracy_ = accuracy;
        bootstrap();
    }

    public List<Date> dates() {
        return List.copyOf(dates_);
    }

    /** How many pillar passes the iterative bootstrap used. */
    public int bootstrapIterations() {
        return bootstrapIterations_;
    }

    @Override
    public Date maxDate() {
        return maxDate_;
    }

    @Override
    protected double discountImpl(double t) {
        return interpolation_.value(t, true);
    }

    private void bootstrap() {
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
        QL_REQUIRE(alive + 1 >= new LogLinear().getRequiredPoints(),
                "not enough alive instruments: " + alive + " provided");

        loopRequired = false;
        dates_.clear();
        dates_.add(firstDate);
        times_ = new double[alive + 1];
        data_ = new double[alive + 1];
        times_[0] = timeFromReference(firstDate);
        data_[0] = 1.0;
        Date maxDate = firstDate;
        for (int i = 1, j = firstAlive; j < instruments_.size(); ++i, ++j) {
            RateHelper helper = instruments_.get(j);
            dates_.add(helper.pillarDate());
            times_[i] = timeFromReference(helper.pillarDate());
            data_[i] = 1.0;
            QL_REQUIRE(!TimeUtils.equals(dates_.get(i - 1), dates_.get(i)),
                    "more than one instrument with pillar " + dates_.get(i));
            Date latestRelevantDate = helper.latestRelevantDate();
            QL_REQUIRE(TimeUtils.greater(latestRelevantDate, maxDate),
                    "instrument has latestRelevantDate before or equal to the previous one");
            // Log-linear is local, so a pillar that is also the latest
            // relevant date does not need another pass. A swap whose last
            // floating fixing ends after its maturity pillar does.
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
            double[] previous = null;
            if (loopRequired && validData)
                previous = data_.clone();
            for (int i = 1, j = firstAlive; j < instruments_.size(); ++i, ++j)
                solvePillar(i, j, validData);
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
            QL_REQUIRE(iteration < 100,
                    "convergence not reached after " + iteration + " iterations");
            validData = true;
        }
    }

    private void solvePillar(int i, int j, boolean validData) {
        double dt = times_[i] - times_[i - 1];
        double min;
        if (validData) {
            double smallest = data_[0];
            for (double value : data_)
                smallest = Math.min(smallest, value);
            min = smallest / 2.0;
        } else {
            min = data_[i - 1] * Math.exp(-MAX_RATE * dt);
        }
        double max = data_[i - 1] * Math.exp(MAX_RATE * dt);
        double guess;
        if (validData)
            guess = data_[i];
        else if (i == 1)
            guess = 1.0 / (1.0 + AVERAGE_RATE * times_[1]);
        else
            guess = Math.exp(-(-Math.log(data_[i - 1]) / times_[i - 1]) * times_[i]);
        if (guess >= max)
            guess = max - (max - min) / 5.0;
        else if (guess <= min)
            guess = min + (max - min) / 5.0;

        RateHelper helper = instruments_.get(j);
        int node = i;
        Function error = x -> {
            data_[node] = x;
            interpolation_.update();
            return helper.quoteError();
        };
        Brent solver = new Brent();
        try {
            double discount = solver.solve(error, accuracy_, guess, min, max);
            data_[node] = discount;
            interpolation_.update();
        } catch (IllegalArgumentException e) {
            QL_FAIL("failed at alive instrument " + i
                    + ", pillar " + helper.pillarDate()
                    + ", maturity " + helper.maturityDate()
                    + ", reference date " + dates_.get(0)
                    + ": " + e.getMessage());
        }
    }
}
