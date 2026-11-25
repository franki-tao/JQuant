package jquant.termstructures.volatility.swaption;

import jquant.math.interpolations.LinearInterpolation;
import jquant.time.BusinessDayConvention;
import jquant.time.Calendar;
import jquant.time.Date;
import jquant.time.DayCounter;
import jquant.time.Period;
import jquant.time.TimeUnit;
import jquant.time.TimeUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static jquant.math.CommonUtil.QL_REQUIRE;

/**
 * Base class for discrete swaption-volatility structures.
 *
 * <p>Ported from QuantLib
 * {@code ql/termstructures/volatility/swaption/swaptionvoldiscrete.{hpp,cpp}}.
 * Option dates and times are built from the option tenors with the calendar
 * and business-day convention of the structure; swap lengths are built from
 * the swap tenors with {@link SwaptionVolatilityStructure#swapLength(Period)}.
 *
 * <p>Official {@code SwaptionVolatilityDiscrete} also derives from
 * {@code LazyObject}. Java has single inheritance, so the lazy recalculation
 * is reproduced explicitly: {@link #calculate()} calls
 * {@link #performCalculations()} once, and observers of the market data reset
 * the calculated flag through {@link #update()}. This mirrors the explicit
 * lazy handling already used by
 * {@code CapFloorTermVolSurface} in this port.
 *
 * <p>https://github.com/lballabio/QuantLib/blob/master/ql/termstructures/volatility/swaption/swaptionvoldiscrete.hpp
 * <p>https://github.com/lballabio/QuantLib/blob/master/ql/termstructures/volatility/swaption/swaptionvoldiscrete.cpp
 */
public abstract class SwaptionVolatilityDiscrete extends SwaptionVolatilityStructure {

    protected final int nOptionTenors_;
    protected final List<Period> optionTenors_;
    protected final List<Date> optionDates_;
    protected final double[] optionTimes_;
    protected final double[] optionDatesAsReal_;
    protected final double[] optionInterpolatorTimes_;
    protected final double[] optionInterpolatorDatesAsReal_;
    protected LinearInterpolation optionInterpolator_;

    protected final int nSwapTenors_;
    protected final List<Period> swapTenors_;
    protected final double[] swapLengths_;

    protected Date cachedReferenceDate_ = new Date();
    protected boolean calculated_ = false;

    /** floating reference date */
    protected SwaptionVolatilityDiscrete(List<Period> optionTenors,
                                         List<Period> swapTenors,
                                         int settlementDays,
                                         Calendar calendar,
                                         BusinessDayConvention bdc,
                                         DayCounter dc) {
        super(settlementDays, calendar, bdc, dc);

        nOptionTenors_ = optionTenors.size();
        optionTenors_ = new ArrayList<>(optionTenors);
        optionDates_ = new ArrayList<>(
                Collections.nCopies(nOptionTenors_, new Date()));
        optionTimes_ = new double[nOptionTenors_];
        optionDatesAsReal_ = new double[nOptionTenors_];
        optionInterpolatorTimes_ = new double[nOptionTenors_ + 1];
        optionInterpolatorDatesAsReal_ = new double[nOptionTenors_ + 1];

        nSwapTenors_ = swapTenors.size();
        swapTenors_ = new ArrayList<>(swapTenors);
        swapLengths_ = new double[nSwapTenors_];

        checkOptionTenors();
        initializeOptionDatesAndTimes();

        checkSwapTenors();
        initializeSwapLengths();

        optionInterpolator_ = new LinearInterpolation(
                optionInterpolatorTimes_, optionInterpolatorDatesAsReal_);
        optionInterpolator_.update();
        optionInterpolator_.enableExtrapolation();
        cachedReferenceDate_ = referenceDate();
    }

    /** fixed reference date */
    protected SwaptionVolatilityDiscrete(List<Period> optionTenors,
                                         List<Period> swapTenors,
                                         Date referenceDate,
                                         Calendar calendar,
                                         BusinessDayConvention bdc,
                                         DayCounter dc) {
        super(referenceDate, calendar, bdc, dc);

        nOptionTenors_ = optionTenors.size();
        optionTenors_ = new ArrayList<>(optionTenors);
        optionDates_ = new ArrayList<>(
                Collections.nCopies(nOptionTenors_, new Date()));
        optionTimes_ = new double[nOptionTenors_];
        optionDatesAsReal_ = new double[nOptionTenors_];
        optionInterpolatorTimes_ = new double[nOptionTenors_ + 1];
        optionInterpolatorDatesAsReal_ = new double[nOptionTenors_ + 1];

        nSwapTenors_ = swapTenors.size();
        swapTenors_ = new ArrayList<>(swapTenors);
        swapLengths_ = new double[nSwapTenors_];

        checkOptionTenors();
        initializeOptionDatesAndTimes();

        checkSwapTenors();
        initializeSwapLengths();

        optionInterpolator_ = new LinearInterpolation(
                optionInterpolatorTimes_, optionInterpolatorDatesAsReal_);
        optionInterpolator_.update();
        optionInterpolator_.enableExtrapolation();
    }

    /**
     * Fixed reference date, explicit option dates.
     * Official takes {@code vector<Date>}; Java uses an array so the
     * signature does not clash by erasure with the option-tenor
     * {@code List<Period>} constructor.
     */
    protected SwaptionVolatilityDiscrete(Date[] optionDates,
                                         List<Period> swapTenors,
                                         Date referenceDate,
                                         Calendar calendar,
                                         BusinessDayConvention bdc,
                                         DayCounter dc) {
        super(referenceDate, calendar, bdc, dc);

        nOptionTenors_ = optionDates.length;
        optionTenors_ = new ArrayList<>();
        optionDates_ = new ArrayList<>(nOptionTenors_);
        for (Date d : optionDates)
            optionDates_.add(d.copy());
        optionTimes_ = new double[nOptionTenors_];
        optionDatesAsReal_ = new double[nOptionTenors_];
        optionInterpolatorTimes_ = new double[nOptionTenors_ + 1];
        optionInterpolatorDatesAsReal_ = new double[nOptionTenors_ + 1];

        nSwapTenors_ = swapTenors.size();
        swapTenors_ = new ArrayList<>(swapTenors);
        swapLengths_ = new double[nSwapTenors_];

        checkOptionDates(referenceDate);
        initializeOptionTimes();

        checkSwapTenors();
        initializeSwapLengths();

        // Official swaptionvoldiscrete.cpp only initializes the option times
        // in this constructor; the date nodes of the interpolator keep their
        // default (zero) values. Reproduced as official.
        optionInterpolator_ = new LinearInterpolation(
                optionInterpolatorTimes_, optionInterpolatorDatesAsReal_);
        optionInterpolator_.update();
        optionInterpolator_.enableExtrapolation();
    }

    public List<Period> optionTenors() {
        return Collections.unmodifiableList(optionTenors_);
    }

    public List<Date> optionDates() {
        calculate();
        return Collections.unmodifiableList(optionDates_);
    }

    public double[] optionTimes() {
        calculate();
        return optionTimes_.clone();
    }

    public List<Period> swapTenors() {
        return Collections.unmodifiableList(swapTenors_);
    }

    public double[] swapLengths() {
        calculate();
        return swapLengths_.clone();
    }

    /**
     * Option date corresponding to an option time, by linear interpolation of
     * the option-date serial numbers over the option times. As official, the
     * interpolator carries an extra node at time zero mapped to the reference
     * date and extrapolates linearly.
     */
    public Date optionDateFromTime(double optionTime) {
        calculate();
        return new Date((int) optionInterpolator_.value(optionTime, true));
    }

    /**
     * Mirrors the official {@code LazyObject::calculate()}.
     */
    public void calculate() {
        if (!calculated_) {
            calculated_ = true;
            try {
                performCalculations();
            } catch (RuntimeException e) {
                calculated_ = false;
                throw e;
            }
        }
    }

    public boolean isCalculated() {
        return calculated_;
    }

    /**
     * Mirrors the official {@code LazyObject::performCalculations()}.
     * Derived structures update their market-data caches after calling this.
     */
    protected void performCalculations() {
        // recalculate dates if necessary...
        if (moving_) {
            Date reference = referenceDate();
            if (TimeUtils.neq(cachedReferenceDate_, reference)) {
                cachedReferenceDate_ = reference;
                initializeOptionDatesAndTimes();
                initializeSwapLengths();
                optionInterpolator_.update();
            }
        }
    }

    /**
     * Mirrors the official {@code TermStructure::update()} followed by
     * {@code LazyObject::update()}.
     */
    @Override
    public void update() {
        super.update();
        calculated_ = false;
    }

    private void checkOptionTenors() {
        QL_REQUIRE(TimeUtils.greater(optionTenors_.get(0),
                        new Period(0, TimeUnit.DAYS)),
                "first option tenor is negative (" + optionTenors_.get(0) + ")");
        for (int i = 1; i < nOptionTenors_; ++i)
            QL_REQUIRE(TimeUtils.greater(optionTenors_.get(i),
                            optionTenors_.get(i - 1)),
                    "non increasing option tenor: " + optionTenors_.get(i - 1)
                            + ", " + optionTenors_.get(i));
    }

    private void checkOptionDates(Date reference) {
        QL_REQUIRE(TimeUtils.greater(optionDates_.get(0), reference),
                "first option date (" + optionDates_.get(0)
                        + ") must be greater than reference date ("
                        + reference + ")");
        for (int i = 1; i < nOptionTenors_; ++i)
            QL_REQUIRE(TimeUtils.greater(optionDates_.get(i),
                            optionDates_.get(i - 1)),
                    "non increasing option dates: " + optionDates_.get(i - 1)
                            + ", " + optionDates_.get(i));
    }

    private void checkSwapTenors() {
        QL_REQUIRE(TimeUtils.greater(swapTenors_.get(0),
                        new Period(0, TimeUnit.DAYS)),
                "first swap tenor is negative (" + swapTenors_.get(0) + ")");
        for (int i = 1; i < nSwapTenors_; ++i)
            QL_REQUIRE(TimeUtils.greater(swapTenors_.get(i),
                            swapTenors_.get(i - 1)),
                    "non increasing swap tenor: " + swapTenors_.get(i - 1)
                            + ", " + swapTenors_.get(i));
    }

    private void initializeOptionDatesAndTimes() {
        optionInterpolatorDatesAsReal_[0] = referenceDate().serialNumber();
        for (int i = 0; i < nOptionTenors_; ++i) {
            optionDates_.set(i, optionDateFromTenor(optionTenors_.get(i)));
            optionDatesAsReal_[i] = optionInterpolatorDatesAsReal_[i + 1] =
                    optionDates_.get(i).serialNumber();
        }
        initializeOptionTimes();
    }

    private void initializeOptionTimes() {
        optionInterpolatorTimes_[0] = 0.0;
        for (int i = 0; i < nOptionTenors_; ++i) {
            optionTimes_[i] = timeFromReference(optionDates_.get(i));
            optionInterpolatorTimes_[i + 1] = optionTimes_[i];
        }
    }

    private void initializeSwapLengths() {
        for (int i = 0; i < nSwapTenors_; ++i)
            swapLengths_[i] = swapLength(swapTenors_.get(i));
    }
}
