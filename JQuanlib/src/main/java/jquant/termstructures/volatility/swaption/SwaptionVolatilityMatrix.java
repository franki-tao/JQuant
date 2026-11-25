package jquant.termstructures.volatility.swaption;

import jquant.Handle;
import jquant.Quote;
import jquant.math.Matrix;
import jquant.math.interpolations.BilinearInterpolation;
import jquant.math.interpolations.FlatExtrapolator2D;
import jquant.math.interpolations.Interpolation2D;
import jquant.quotes.SimpleQuote;
import jquant.termstructures.SmileSection;
import jquant.termstructures.volatility.FlatSmileSection;
import jquant.termstructures.volatility.Sarb.VolatilityType;
import jquant.time.BusinessDayConvention;
import jquant.time.Calendar;
import jquant.time.Date;
import jquant.time.DayCounter;
import jquant.time.Period;

import java.util.ArrayList;
import java.util.List;

import static jquant.math.CommonUtil.QL_REQUIRE;
import static jquant.math.MathUtils.QL_MAX_REAL;
import static jquant.termstructures.volatility.Sarb.VolatilityType.ShiftedLognormal;

/**
 * At-the-money swaption-volatility matrix.
 *
 * <p>Ported from QuantLib
 * {@code ql/termstructures/volatility/swaption/swaptionvolmatrix.{hpp,cpp}}.
 * The matrix has one row per option date and one column per swap tenor, as
 * official. Default interpolation is bilinear over
 * {@code (swapLength, optionTime)} with X the swap lengths and Y the option
 * times; {@code flatExtrapolation} wraps it in a flat extrapolator.
 *
 * <p>As official, no option date / swap tenor smile-section specialization is
 * defined: the commented-out official method says it would break the CMS test
 * suite. The base date-based smile section therefore falls back to the
 * time-based implementation below, which builds a flat smile at the
 * interpolated ATM volatility.
 *
 * <p>The fixed-matrix constructors create dummy {@link SimpleQuote} handles
 * for the market data, as official, so the generic handle-based calculation
 * path is used by all constructors.
 *
 * <p>https://github.com/lballabio/QuantLib/blob/master/ql/termstructures/volatility/swaption/swaptionvolmatrix.hpp
 * <p>https://github.com/lballabio/QuantLib/blob/master/ql/termstructures/volatility/swaption/swaptionvolmatrix.cpp
 */
public class SwaptionVolatilityMatrix extends SwaptionVolatilityDiscrete {

    private List<List<Handle<Quote>>> volHandles_;
    private double[][] shiftValues_;
    private double[][] volatilities_;
    private double[][] shifts_;
    private Interpolation2D interpolation_;
    private Interpolation2D interpolationShifts_;
    private VolatilityType volatilityType_;

    /** floating reference date, fixed market data */
    public SwaptionVolatilityMatrix(Calendar calendar,
                                    BusinessDayConvention bdc,
                                    List<Period> optionTenors,
                                    List<Period> swapTenors,
                                    Matrix volatilities,
                                    DayCounter dayCounter) {
        this(calendar, bdc, optionTenors, swapTenors, volatilities,
                dayCounter, false, ShiftedLognormal, null);
    }

    /** floating reference date, fixed market data */
    public SwaptionVolatilityMatrix(Calendar calendar,
                                    BusinessDayConvention bdc,
                                    List<Period> optionTenors,
                                    List<Period> swapTenors,
                                    Matrix volatilities,
                                    DayCounter dayCounter,
                                    boolean flatExtrapolation,
                                    VolatilityType type,
                                    Matrix shifts) {
        super(optionTenors, swapTenors, 0, calendar, bdc, dayCounter);

        int volRows = volatilities.rows();
        int volColumns = volatilities.cols();
        volatilityType_ = type;
        checkInputs(volRows, volColumns, shiftRows(shifts), shiftColumns(shifts));

        volHandles_ = new ArrayList<>(volRows);
        for (int i = 0; i < volRows; ++i) {
            List<Handle<Quote>> row = new ArrayList<>(volColumns);
            for (int j = 0; j < volColumns; ++j)
                row.add(new Handle<Quote>(
                        new SimpleQuote(volatilities.get(i, j)), true));
            volHandles_.add(row);
        }

        volatilities_ = new double[nOptionTenors_][nSwapTenors_];
        shifts_ = new double[nOptionTenors_][nSwapTenors_];
        shiftValues_ = shiftValues(shifts);
        interpolate(flatExtrapolation);
    }

    /** fixed reference date, fixed market data */
    public SwaptionVolatilityMatrix(Date referenceDate,
                                    Calendar calendar,
                                    BusinessDayConvention bdc,
                                    List<Period> optionTenors,
                                    List<Period> swapTenors,
                                    Matrix volatilities,
                                    DayCounter dayCounter) {
        this(referenceDate, calendar, bdc, optionTenors, swapTenors,
                volatilities, dayCounter, false, ShiftedLognormal, null);
    }

    /** fixed reference date, fixed market data */
    public SwaptionVolatilityMatrix(Date referenceDate,
                                    Calendar calendar,
                                    BusinessDayConvention bdc,
                                    List<Period> optionTenors,
                                    List<Period> swapTenors,
                                    Matrix volatilities,
                                    DayCounter dayCounter,
                                    boolean flatExtrapolation,
                                    VolatilityType type,
                                    Matrix shifts) {
        super(optionTenors, swapTenors, referenceDate, calendar, bdc,
                dayCounter);

        int volRows = volatilities.rows();
        int volColumns = volatilities.cols();
        volatilityType_ = type;
        checkInputs(volRows, volColumns, shiftRows(shifts), shiftColumns(shifts));

        volHandles_ = new ArrayList<>(volRows);
        for (int i = 0; i < volRows; ++i) {
            List<Handle<Quote>> row = new ArrayList<>(volColumns);
            for (int j = 0; j < volColumns; ++j)
                row.add(new Handle<Quote>(
                        new SimpleQuote(volatilities.get(i, j)), true));
            volHandles_.add(row);
        }

        volatilities_ = new double[nOptionTenors_][nSwapTenors_];
        shifts_ = new double[nOptionTenors_][nSwapTenors_];
        shiftValues_ = shiftValues(shifts);
        interpolate(flatExtrapolation);
    }

    /** floating reference date, floating market data */
    public SwaptionVolatilityMatrix(Calendar calendar,
                                    BusinessDayConvention bdc,
                                    List<Period> optionTenors,
                                    List<Period> swapTenors,
                                    List<List<Handle<Quote>>> vols,
                                    DayCounter dayCounter) {
        this(calendar, bdc, optionTenors, swapTenors, vols, dayCounter,
                false, ShiftedLognormal, null);
    }

    /** floating reference date, floating market data */
    public SwaptionVolatilityMatrix(Calendar calendar,
                                    BusinessDayConvention bdc,
                                    List<Period> optionTenors,
                                    List<Period> swapTenors,
                                    List<List<Handle<Quote>>> vols,
                                    DayCounter dayCounter,
                                    boolean flatExtrapolation,
                                    VolatilityType type,
                                    Matrix shifts) {
        super(optionTenors, swapTenors, 0, calendar, bdc, dayCounter);

        int volRows = vols.size();
        int volColumns = vols.isEmpty() ? 0 : vols.get(0).size();
        volatilityType_ = type;
        checkInputs(volRows, volColumns, shiftRows(shifts), shiftColumns(shifts));

        volHandles_ = new ArrayList<>(volRows);
        for (List<Handle<Quote>> row : vols)
            volHandles_.add(new ArrayList<>(row));

        volatilities_ = new double[nOptionTenors_][nSwapTenors_];
        shifts_ = new double[nOptionTenors_][nSwapTenors_];
        shiftValues_ = shiftValues(shifts);
        registerWithMarketData();
        interpolate(flatExtrapolation);
    }

    /** fixed reference date, floating market data */
    public SwaptionVolatilityMatrix(Date referenceDate,
                                    Calendar calendar,
                                    BusinessDayConvention bdc,
                                    List<Period> optionTenors,
                                    List<Period> swapTenors,
                                    List<List<Handle<Quote>>> vols,
                                    DayCounter dayCounter) {
        this(referenceDate, calendar, bdc, optionTenors, swapTenors, vols,
                dayCounter, false, ShiftedLognormal, null);
    }

    /** fixed reference date, floating market data */
    public SwaptionVolatilityMatrix(Date referenceDate,
                                    Calendar calendar,
                                    BusinessDayConvention bdc,
                                    List<Period> optionTenors,
                                    List<Period> swapTenors,
                                    List<List<Handle<Quote>>> vols,
                                    DayCounter dayCounter,
                                    boolean flatExtrapolation,
                                    VolatilityType type,
                                    Matrix shifts) {
        super(optionTenors, swapTenors, referenceDate, calendar, bdc,
                dayCounter);

        int volRows = vols.size();
        int volColumns = vols.isEmpty() ? 0 : vols.get(0).size();
        volatilityType_ = type;
        checkInputs(volRows, volColumns, shiftRows(shifts), shiftColumns(shifts));

        volHandles_ = new ArrayList<>(volRows);
        for (List<Handle<Quote>> row : vols)
            volHandles_.add(new ArrayList<>(row));

        volatilities_ = new double[nOptionTenors_][nSwapTenors_];
        shifts_ = new double[nOptionTenors_][nSwapTenors_];
        shiftValues_ = shiftValues(shifts);
        registerWithMarketData();
        interpolate(flatExtrapolation);
    }

    @Override
    public Date maxDate() {
        calculate();
        return optionDates_.get(nOptionTenors_ - 1).copy();
    }

    @Override
    public double minStrike() {
        return -QL_MAX_REAL;
    }

    @Override
    public double maxStrike() {
        return QL_MAX_REAL;
    }

    @Override
    public Period maxSwapTenor() {
        return swapTenors_.get(nSwapTenors_ - 1).clone();
    }

    @Override
    public VolatilityType volatilityType() {
        return volatilityType_;
    }

    /** Mirrors the official {@code LazyObject::performCalculations()}. */
    @Override
    protected void performCalculations() {
        super.performCalculations();

        for (int i = 0; i < nOptionTenors_; ++i) {
            for (int j = 0; j < nSwapTenors_; ++j) {
                volatilities_[i][j] = volHandles_.get(i).get(j)
                        .currentLink().value();
                shifts_[i][j] = shiftValues_[i][j];
            }
        }
    }

    @Override
    protected double volatilityImpl(double optionTime,
                                    double swapLength,
                                    double strike) {
        calculate();
        // X is the swap length, Y is the option time, as official.
        return interpolation_.value(swapLength, optionTime, true);
    }

    @Override
    protected double shiftImpl(double optionTime, double swapLength) {
        calculate();
        return interpolationShifts_.value(swapLength, optionTime, true);
    }

    // The official source explicitly comments that defining
    // smileSectionImpl(Date, Period) here would break the CMS test suite.
    // Do not add that specialization; the base date-based default calls the
    // time-based method below.
    @Override
    protected SmileSection smileSectionImpl(double optionTime,
                                            double swapLength) {
        // dummy strike
        double atmVol = volatilityImpl(optionTime, swapLength, 0.05);
        return new FlatSmileSection(optionTime, atmVol, dayCounter(),
                Double.NaN, volatilityType(),
                shift(optionTime, swapLength, true));
    }

    private void checkInputs(int volRows,
                             int volColumns,
                             int shiftRows,
                             int shiftColumns) {
        QL_REQUIRE(nOptionTenors_ == volRows,
                "mismatch between number of option dates (" + nOptionTenors_
                        + ") and number of rows (" + volRows
                        + ") in the vol matrix");
        QL_REQUIRE(nSwapTenors_ == volColumns,
                "mismatch between number of swap tenors (" + nSwapTenors_
                        + ") and number of columns (" + volColumns
                        + ") in the vol matrix");

        if (shiftRows == 0 && shiftColumns == 0) {
            shiftRows = volRows;
            shiftColumns = volColumns;
        }

        QL_REQUIRE(nOptionTenors_ == shiftRows,
                "mismatch between number of option dates (" + nOptionTenors_
                        + ") and number of rows (" + shiftRows
                        + ") in the shift matrix");
        QL_REQUIRE(nSwapTenors_ == shiftColumns,
                "mismatch between number of swap tenors (" + nSwapTenors_
                        + ") and number of columns (" + shiftColumns
                        + ") in the shift matrix");
    }

    private void registerWithMarketData() {
        for (List<Handle<Quote>> row : volHandles_)
            for (Handle<Quote> handle : row)
                // Register the Link, not the current quote, so that a relink
                // is observed too (see PR101).
                registerWith(handle.get());
    }

    private double[][] shiftValues(Matrix shifts) {
        double[][] values = new double[nOptionTenors_][nSwapTenors_];
        if (!isEmpty(shifts)) {
            for (int i = 0; i < nOptionTenors_; ++i)
                for (int j = 0; j < nSwapTenors_; ++j)
                    values[i][j] = shifts.get(i, j);
        }
        return values;
    }

    private void interpolate(boolean flatExtrapolation) {
        BilinearInterpolation bilinear = new BilinearInterpolation(
                swapLengths_, optionTimes_, volatilities_);
        interpolation_ = flatExtrapolation
                ? new FlatExtrapolator2D(bilinear) : bilinear;

        BilinearInterpolation bilinearShifts = new BilinearInterpolation(
                swapLengths_, optionTimes_, shifts_);
        interpolationShifts_ = flatExtrapolation
                ? new FlatExtrapolator2D(bilinearShifts) : bilinearShifts;
    }

    private static int shiftRows(Matrix shifts) {
        return isEmpty(shifts) ? 0 : shifts.rows();
    }

    private static int shiftColumns(Matrix shifts) {
        return isEmpty(shifts) ? 0 : shifts.cols();
    }

    private static boolean isEmpty(Matrix m) {
        // Java Matrix represents an empty matrix with a null underlying
        // matrix; a null reference is the Java equivalent of the official
        // default-constructed Matrix().
        return m == null || m.matrix == null || m.rows() == 0 || m.cols() == 0;
    }
}
