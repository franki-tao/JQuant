package jquant.termstructures.volatility.capfloor;

import jquant.Handle;
import jquant.Quote;
import jquant.Settings;
import jquant.math.interpolations.CubicInterpolation;
import jquant.quotes.SimpleQuote;
import jquant.time.BusinessDayConvention;
import jquant.time.Calendar;
import jquant.time.Date;
import jquant.time.DayCounter;
import jquant.time.Period;
import jquant.time.TimeUnit;
import jquant.time.TimeUtils;
import jquant.time.daycounters.Actual365Fixed;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static jquant.math.CommonUtil.QL_REQUIRE;
import static jquant.math.MathUtils.QL_MAX_REAL;
import static jquant.math.MathUtils.QL_MIN_REAL;
import static jquant.math.interpolations.CubicInterpolation.BoundaryCondition.SecondDerivative;
import static jquant.math.interpolations.CubicInterpolation.DerivativeApprox.Spline;

/**
 * Cap/floor at-the-money term-volatility vector.
 *
 * <p>Ported from QuantLib
 * {@code ql/termstructures/volatility/capfloor/capfloortermvolcurve.{hpp,cpp}}.
 *
 * <p>In the official implementation this class inherits from both
 * {@code LazyObject} and {@code CapFloorTermVolatilityStructure}. Java has
 * single inheritance, so the lazy recalculation performed by
 * {@code performCalculations()} is reproduced here explicitly through
 * {@link #calculate()}.
 */
public class CapFloorTermVolCurve extends CapFloorTermVolatilityStructure {

    private final int nOptionTenors_;
    private final List<Period> optionTenors_;
    private final List<Date> optionDates_;
    private final double[] optionTimes_;
    private Date evaluationDate_ = new Date();

    private final List<Handle<Quote>> volHandles_;
    private final double[] vols_;

    private CubicInterpolation interpolation_;
    private boolean calculated_ = false;

    /** Floating reference date, fixed market data. */
    public CapFloorTermVolCurve(int settlementDays,
                                Calendar calendar,
                                BusinessDayConvention bdc,
                                List<Period> optionTenors,
                                double[] vols,
                                DayCounter dc) {
        super(settlementDays, calendar, bdc, dc);
        nOptionTenors_ = optionTenors.size();
        optionTenors_ = new ArrayList<>(optionTenors);
        optionDates_ = new ArrayList<>(Collections.nCopies(nOptionTenors_, new Date()));
        optionTimes_ = new double[nOptionTenors_];
        vols_ = vols.clone();
        volHandles_ = new ArrayList<>(vols.length);
        // fill dummy handles to allow generic handle-based computations later
        for (int i = 0; i < nOptionTenors_; ++i)
            volHandles_.add(new Handle<>(new SimpleQuote(vols_[i]), true));
        checkInputs();
        initializeOptionDatesAndTimes();
        interpolate();
    }

    /** Fixed reference date, fixed market data. */
    public CapFloorTermVolCurve(Date settlementDate,
                                Calendar calendar,
                                BusinessDayConvention bdc,
                                List<Period> optionTenors,
                                double[] vols,
                                DayCounter dc) {
        super(settlementDate, calendar, bdc, dc);
        nOptionTenors_ = optionTenors.size();
        optionTenors_ = new ArrayList<>(optionTenors);
        optionDates_ = new ArrayList<>(Collections.nCopies(nOptionTenors_, new Date()));
        optionTimes_ = new double[nOptionTenors_];
        vols_ = vols.clone();
        volHandles_ = new ArrayList<>(vols.length);
        for (int i = 0; i < nOptionTenors_; ++i)
            volHandles_.add(new Handle<>(new SimpleQuote(vols_[i]), true));
        checkInputs();
        initializeOptionDatesAndTimes();
        interpolate();
    }

    /** Floating reference date, floating market data. */
    public CapFloorTermVolCurve(int settlementDays,
                                Calendar calendar,
                                BusinessDayConvention bdc,
                                List<Period> optionTenors,
                                List<Handle<Quote>> vols,
                                DayCounter dc) {
        super(settlementDays, calendar, bdc, dc);
        nOptionTenors_ = optionTenors.size();
        optionTenors_ = new ArrayList<>(optionTenors);
        optionDates_ = new ArrayList<>(Collections.nCopies(nOptionTenors_, new Date()));
        optionTimes_ = new double[nOptionTenors_];
        vols_ = new double[vols.size()];
        volHandles_ = new ArrayList<>(vols);
        checkInputs();
        initializeOptionDatesAndTimes();
        registerWithMarketData();
        interpolate();
    }

    /** Fixed reference date, floating market data. */
    public CapFloorTermVolCurve(Date settlementDate,
                                Calendar calendar,
                                BusinessDayConvention bdc,
                                List<Period> optionTenors,
                                List<Handle<Quote>> vols,
                                DayCounter dc) {
        super(settlementDate, calendar, bdc, dc);
        nOptionTenors_ = optionTenors.size();
        optionTenors_ = new ArrayList<>(optionTenors);
        optionDates_ = new ArrayList<>(Collections.nCopies(nOptionTenors_, new Date()));
        optionTimes_ = new double[nOptionTenors_];
        vols_ = new double[vols.size()];
        volHandles_ = new ArrayList<>(vols);
        checkInputs();
        initializeOptionDatesAndTimes();
        registerWithMarketData();
        interpolate();
    }

    @Override
    public Date maxDate() {
        calculate();
        return optionDateFromTenor(optionTenors_.get(optionTenors_.size() - 1));
    }

    @Override
    public double minStrike() {
        return QL_MIN_REAL;
    }

    @Override
    public double maxStrike() {
        return QL_MAX_REAL;
    }

    /**
     * Mirrors the official {@code LazyObject::update()} plus
     * {@code TermStructure::update()} pair.
     */
    @Override
    public void update() {
        // recalculate dates if necessary...
        if (moving_) {
            Date d = Settings.instance.evaluationDate().Date();
            if (TimeUtils.neq(evaluationDate_, d)) {
                evaluationDate_ = d;
                initializeOptionDatesAndTimes();
            }
        }
        super.update();
        calculated_ = false;
    }

    /** Mirrors the official {@code LazyObject::calculate()}. */
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

    /** Mirrors the official {@code LazyObject::performCalculations()}. */
    protected void performCalculations() {
        for (int i = 0; i < vols_.length; ++i)
            vols_[i] = volHandles_.get(i).currentLink().value();
        interpolation_.update();
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

    @Override
    protected double volatilityImpl(double length, double strike) {
        calculate();
        return interpolation_.value(length, true);
    }

    private void checkInputs() {
        QL_REQUIRE(!optionTenors_.isEmpty(), "empty option tenor vector");
        QL_REQUIRE(nOptionTenors_ == vols_.length,
                "mismatch between number of option tenors (" + nOptionTenors_
                        + ") and number of volatilities (" + vols_.length + ")");
        QL_REQUIRE(TimeUtils.greater(optionTenors_.get(0), new Period(0, TimeUnit.DAYS)),
                "negative first option tenor: " + optionTenors_.get(0));
        for (int i = 1; i < nOptionTenors_; ++i)
            QL_REQUIRE(TimeUtils.greater(optionTenors_.get(i), optionTenors_.get(i - 1)),
                    "non increasing option tenor: " + optionTenors_.get(i - 1)
                            + ", " + optionTenors_.get(i));
    }

    private void initializeOptionDatesAndTimes() {
        for (int i = 0; i < nOptionTenors_; ++i) {
            optionDates_.set(i, optionDateFromTenor(optionTenors_.get(i)));
            optionTimes_[i] = timeFromReference(optionDates_.get(i));
        }
    }

    private void registerWithMarketData() {
        for (Handle<Quote> volHandle : volHandles_)
            registerWith(volHandle.get());
    }

    private void interpolate() {
        interpolation_ = new CubicInterpolation(optionTimes_, vols_,
                Spline, false,
                SecondDerivative, 0.0,
                SecondDerivative, 0.0);
    }
}
