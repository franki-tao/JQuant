package jquant.termstructures.volatility.capfloor;

import jquant.Handle;
import jquant.Quote;
import jquant.Settings;
import jquant.math.Matrix;
import jquant.math.interpolations.BicubicSpline;
import jquant.quotes.SimpleQuote;
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
 * Cap/floor smile volatility surface.
 *
 * <p>Ported from QuantLib
 * {@code ql/termstructures/volatility/capfloor/capfloortermvolsurface.{hpp,cpp}}.
 *
 * <p>In the official implementation this class inherits from both
 * {@code LazyObject} and {@code CapFloorTermVolatilityStructure}. Java has
 * single inheritance, so the lazy recalculation performed by
 * {@code performCalculations()} is reproduced here explicitly through
 * {@link #calculate()}.
 */
public class CapFloorTermVolSurface extends CapFloorTermVolatilityStructure {

    private final int nOptionTenors_;
    private final List<Period> optionTenors_;
    private final List<Date> optionDates_;
    private final double[] optionTimes_;
    private Date evaluationDate_ = new Date();

    private final int nStrikes_;
    private final double[] strikes_;

    private final List<List<Handle<Quote>>> volHandles_;
    private final double[][] vols_;

    private BicubicSpline interpolation_;
    private boolean calculated_ = false;

    /** Floating reference date, fixed market data. */
    public CapFloorTermVolSurface(int settlementDays,
                                  Calendar calendar,
                                  BusinessDayConvention bdc,
                                  List<Period> optionTenors,
                                  double[] strikes,
                                  Matrix volatilities,
                                  DayCounter dc) {
        super(settlementDays, calendar, bdc, dc);
        nOptionTenors_ = optionTenors.size();
        optionTenors_ = new ArrayList<>(optionTenors);
        optionDates_ = new ArrayList<>(Collections.nCopies(nOptionTenors_, new Date()));
        optionTimes_ = new double[nOptionTenors_];
        nStrikes_ = strikes.length;
        strikes_ = strikes.clone();
        vols_ = matrixToArray(volatilities);
        volHandles_ = new ArrayList<>(vols_.length);
        // fill dummy handles to allow generic handle-based computations later
        for (int i = 0; i < nOptionTenors_; ++i) {
            List<Handle<Quote>> row = new ArrayList<>(nStrikes_);
            for (int j = 0; j < nStrikes_; ++j)
                row.add(new Handle<>(new SimpleQuote(vols_[i][j]), true));
            volHandles_.add(row);
        }
        checkInputs(volatilities);
        initializeOptionDatesAndTimes();
        interpolate();
    }

    /** Fixed reference date, fixed market data. */
    public CapFloorTermVolSurface(Date settlementDate,
                                  Calendar calendar,
                                  BusinessDayConvention bdc,
                                  List<Period> optionTenors,
                                  double[] strikes,
                                  Matrix volatilities,
                                  DayCounter dc) {
        super(settlementDate, calendar, bdc, dc);
        nOptionTenors_ = optionTenors.size();
        optionTenors_ = new ArrayList<>(optionTenors);
        optionDates_ = new ArrayList<>(Collections.nCopies(nOptionTenors_, new Date()));
        optionTimes_ = new double[nOptionTenors_];
        nStrikes_ = strikes.length;
        strikes_ = strikes.clone();
        vols_ = matrixToArray(volatilities);
        volHandles_ = new ArrayList<>(vols_.length);
        for (int i = 0; i < nOptionTenors_; ++i) {
            List<Handle<Quote>> row = new ArrayList<>(nStrikes_);
            for (int j = 0; j < nStrikes_; ++j)
                row.add(new Handle<>(new SimpleQuote(vols_[i][j]), true));
            volHandles_.add(row);
        }
        checkInputs(volatilities);
        initializeOptionDatesAndTimes();
        interpolate();
    }

    /** Floating reference date, floating market data. */
    public CapFloorTermVolSurface(int settlementDays,
                                  Calendar calendar,
                                  BusinessDayConvention bdc,
                                  List<Period> optionTenors,
                                  double[] strikes,
                                  List<List<Handle<Quote>>> vols,
                                  DayCounter dc) {
        super(settlementDays, calendar, bdc, dc);
        nOptionTenors_ = optionTenors.size();
        optionTenors_ = new ArrayList<>(optionTenors);
        optionDates_ = new ArrayList<>(Collections.nCopies(nOptionTenors_, new Date()));
        optionTimes_ = new double[nOptionTenors_];
        nStrikes_ = strikes.length;
        strikes_ = strikes.clone();
        vols_ = new double[vols.size()][strikes.length];
        volHandles_ = new ArrayList<>(vols);
        checkInputs(null);
        initializeOptionDatesAndTimes();
        for (int i = 0; i < nOptionTenors_; ++i)
            QL_REQUIRE(volHandles_.get(i).size() == nStrikes_,
                    "row " + (i + 1) + " of vol handles has size "
                            + volHandles_.get(i).size() + " instead of " + nStrikes_);
        registerWithMarketData();
        for (int i = 0; i < vols_.length; ++i)
            for (int j = 0; j < vols_[i].length; ++j)
                vols_[i][j] = volHandles_.get(i).get(j).currentLink().value();
        interpolate();
    }

    /** Fixed reference date, floating market data. */
    public CapFloorTermVolSurface(Date settlementDate,
                                  Calendar calendar,
                                  BusinessDayConvention bdc,
                                  List<Period> optionTenors,
                                  double[] strikes,
                                  List<List<Handle<Quote>>> vols,
                                  DayCounter dc) {
        super(settlementDate, calendar, bdc, dc);
        nOptionTenors_ = optionTenors.size();
        optionTenors_ = new ArrayList<>(optionTenors);
        optionDates_ = new ArrayList<>(Collections.nCopies(nOptionTenors_, new Date()));
        optionTimes_ = new double[nOptionTenors_];
        nStrikes_ = strikes.length;
        strikes_ = strikes.clone();
        vols_ = new double[vols.size()][strikes.length];
        volHandles_ = new ArrayList<>(vols);
        checkInputs(null);
        initializeOptionDatesAndTimes();
        for (int i = 0; i < nOptionTenors_; ++i)
            QL_REQUIRE(volHandles_.get(i).size() == nStrikes_,
                    "row " + (i + 1) + " of vol handles has size "
                            + volHandles_.get(i).size() + " instead of " + nStrikes_);
        registerWithMarketData();
        for (int i = 0; i < vols_.length; ++i)
            for (int j = 0; j < vols_[i].length; ++j)
                vols_[i][j] = volHandles_.get(i).get(j).currentLink().value();
        interpolate();
    }

    @Override
    public Date maxDate() {
        calculate();
        return optionDateFromTenor(optionTenors_.get(optionTenors_.size() - 1));
    }

    @Override
    public double minStrike() {
        return strikes_[0];
    }

    @Override
    public double maxStrike() {
        return strikes_[strikes_.length - 1];
    }

    /** Mirrors the official {@code TermStructure::update()} plus {@code LazyObject::update()}. */
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
        for (int i = 0; i < nOptionTenors_; ++i)
            for (int j = 0; j < nStrikes_; ++j)
                vols_[i][j] = volHandles_.get(i).get(j).currentLink().value();
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

    public double[] strikes() {
        return strikes_.clone();
    }

    @Override
    protected double volatilityImpl(double t, double strike) {
        calculate();
        return interpolation_.value(strike, t, true);
    }

    private void checkInputs(Matrix volatilities) {
        QL_REQUIRE(!optionTenors_.isEmpty(), "empty option tenor vector");
        QL_REQUIRE(nOptionTenors_ == vols_.length,
                "mismatch between number of option tenors (" + nOptionTenors_
                        + ") and number of volatility rows (" + vols_.length + ")");
        QL_REQUIRE(TimeUtils.greater(optionTenors_.get(0), new Period(0, TimeUnit.DAYS)),
                "negative first option tenor: " + optionTenors_.get(0));
        for (int i = 1; i < nOptionTenors_; ++i)
            QL_REQUIRE(TimeUtils.greater(optionTenors_.get(i), optionTenors_.get(i - 1)),
                    "non increasing option tenor: " + optionTenors_.get(i - 1)
                            + ", " + optionTenors_.get(i));

        QL_REQUIRE(nStrikes_ == vols_[0].length,
                "mismatch between strikes(" + strikes_.length
                        + ") and vol columns (" + vols_[0].length + ")");
        for (int j = 1; j < nStrikes_; ++j)
            QL_REQUIRE(strikes_[j - 1] < strikes_[j],
                    "non increasing strikes: " + strikes_[j - 1] + ", " + strikes_[j]);
    }

    private void initializeOptionDatesAndTimes() {
        for (int i = 0; i < nOptionTenors_; ++i) {
            optionDates_.set(i, optionDateFromTenor(optionTenors_.get(i)));
            optionTimes_[i] = timeFromReference(optionDates_.get(i));
        }
    }

    private void registerWithMarketData() {
        for (int i = 0; i < nOptionTenors_; ++i)
            for (int j = 0; j < nStrikes_; ++j)
                registerWith(volHandles_.get(i).get(j).get());
    }

    private void interpolate() {
        interpolation_ = new BicubicSpline(strikes_, optionTimes_, vols_);
    }

    private static double[][] matrixToArray(Matrix m) {
        double[][] result = new double[m.rows()][m.cols()];
        for (int i = 0; i < m.rows(); ++i)
            for (int j = 0; j < m.cols(); ++j)
                result[i][j] = m.get(i, j);
        return result;
    }
}
