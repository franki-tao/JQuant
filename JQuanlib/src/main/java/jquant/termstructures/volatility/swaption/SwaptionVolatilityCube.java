package jquant.termstructures.volatility.swaption;

import jquant.Handle;
import jquant.Quote;
import jquant.indexes.SwapIndex;
import jquant.termstructures.SmileSection;
import jquant.termstructures.volatility.Sarb.VolatilityType;
import jquant.time.BusinessDayConvention;
import jquant.time.Calendar;
import jquant.time.Date;
import jquant.time.DayCounter;
import jquant.time.Period;
import jquant.time.TimeUtils;

import java.util.ArrayList;
import java.util.List;

import static jquant.math.CommonUtil.QL_REQUIRE;
import static jquant.math.MathUtils.QL_MAX_REAL;

/**
 * Swaption-volatility cube.
 *
 * <p>Ported from QuantLib
 * {@code ql/termstructures/volatility/swaption/swaptionvolcube.{hpp,cpp}}.
 * The structure has no reference date of its own: dates, calendar, day
 * counter and volatility type are those of the ATM volatility structure it
 * is built on. The official constructor enables extrapolation on that shared
 * ATM structure and registers with it; that side effect is preserved.
 *
 * <p>Because Java runs a virtual call from a superclass constructor against
 * the most derived class, the ATM-delegating accessors fall back to the base
 * implementation while {@code atmVol_} is still unset. This matches the
 * official C++ behaviour, where virtual calls during base construction
 * resolve to the base class.
 *
 * <p>https://github.com/lballabio/QuantLib/blob/master/ql/termstructures/volatility/swaption/swaptionvolcube.hpp
 * <p>https://github.com/lballabio/QuantLib/blob/master/ql/termstructures/volatility/swaption/swaptionvolcube.cpp
 */
public abstract class SwaptionVolatilityCube extends SwaptionVolatilityDiscrete {

    protected Handle<SwaptionVolatilityStructure> atmVol_;
    protected final int nStrikes_;
    protected final double[] strikeSpreads_;
    protected final double[] localStrikes_;
    protected final double[] localSmile_;
    protected final List<List<Handle<Quote>>> volSpreads_;
    protected final SwapIndex swapIndexBase_;
    protected final SwapIndex shortSwapIndexBase_;
    protected final boolean vegaWeightedSmileFit_;

    public SwaptionVolatilityCube(
            Handle<SwaptionVolatilityStructure> atmVol,
            List<Period> optionTenors,
            List<Period> swapTenors,
            double[] strikeSpreads,
            List<List<Handle<Quote>>> volSpreads,
            SwapIndex swapIndexBase,
            SwapIndex shortSwapIndexBase,
            boolean vegaWeightedSmileFit) {
        super(optionTenors, swapTenors, 0,
                linked(atmVol).calendar(),
                linked(atmVol).businessDayConvention(),
                linked(atmVol).dayCounter());

        atmVol_ = atmVol;
        nStrikes_ = strikeSpreads.length;
        strikeSpreads_ = strikeSpreads.clone();
        localStrikes_ = new double[nStrikes_];
        localSmile_ = new double[nStrikes_];
        volSpreads_ = copy(volSpreads);
        swapIndexBase_ = swapIndexBase;
        shortSwapIndexBase_ = shortSwapIndexBase;
        vegaWeightedSmileFit_ = vegaWeightedSmileFit;

        QL_REQUIRE(!atmVol_.empty(), "atm vol handle not linked to anything");
        for (int i = 1; i < nStrikes_; ++i)
            QL_REQUIRE(strikeSpreads_[i - 1] < strikeSpreads_[i],
                    "non increasing strike spreads: "
                            + strikeSpreads_[i - 1] + ", "
                            + strikeSpreads_[i]);

        QL_REQUIRE(!volSpreads_.isEmpty(), "empty vol spreads matrix");

        QL_REQUIRE(nOptionTenors_ * nSwapTenors_ == volSpreads_.size(),
                "mismatch between number of option tenors * swap tenors ("
                        + nOptionTenors_ * nSwapTenors_
                        + ") and number of rows (" + volSpreads_.size() + ")");

        for (List<Handle<Quote>> row : volSpreads_)
            QL_REQUIRE(nStrikes_ == row.size(),
                    "mismatch between number of strikes (" + nStrikes_
                            + ") and number of columns (" + row.size()
                            + ") in a row");

        registerWith(atmVol_.get());
        atmVol_.currentLink().enableExtrapolation();

        registerWith(swapIndexBase_);
        registerWith(shortSwapIndexBase_);

        QL_REQUIRE(TimeUtils.leq(shortSwapIndexBase_.tenor(),
                        swapIndexBase_.tenor()),
                "short index tenor (" + shortSwapIndexBase_.tenor()
                        + ") is not less or equal than index tenor ("
                        + swapIndexBase_.tenor() + ")");

        registerWithVolatilitySpread();
    }

    // ------------------------------------------------------------------
    // TermStructure interface
    // ------------------------------------------------------------------

    @Override
    public DayCounter dayCounter() {
        return atmVol_ == null ? super.dayCounter()
                : atmVol_.currentLink().dayCounter();
    }

    @Override
    public Date maxDate() {
        return atmVol_.currentLink().maxDate().copy();
    }

    @Override
    public double maxTime() {
        return atmVol_.currentLink().maxTime();
    }

    @Override
    public Date referenceDate() {
        return atmVol_ == null ? super.referenceDate()
                : atmVol_.currentLink().referenceDate().copy();
    }

    @Override
    public Calendar calendar() {
        return atmVol_ == null ? super.calendar()
                : atmVol_.currentLink().calendar();
    }

    @Override
    public int settlementDays() {
        return atmVol_ == null ? super.settlementDays()
                : atmVol_.currentLink().settlementDays();
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
        return atmVol_.currentLink().maxSwapTenor().clone();
    }

    @Override
    public VolatilityType volatilityType() {
        return atmVol_.currentLink().volatilityType();
    }

    // ------------------------------------------------------------------
    // Other inspectors
    // ------------------------------------------------------------------

    public double atmStrike(Date optionDate, Period swapTenor) {
        SwapIndex base = TimeUtils.greater(swapTenor,
                shortSwapIndexBase_.tenor())
                ? swapIndexBase_ : shortSwapIndexBase_;
        return base.clone(swapTenor).fixing(optionDate, false);
    }

    public double atmStrike(Period optionTenor, Period swapTenor) {
        return atmStrike(optionDateFromTenor(optionTenor), swapTenor);
    }

    public Handle<SwaptionVolatilityStructure> atmVol() {
        return atmVol_;
    }

    public double[] strikeSpreads() {
        return strikeSpreads_.clone();
    }

    public List<List<Handle<Quote>>> volSpreads() {
        return volSpreads_;
    }

    public SwapIndex swapIndexBase() {
        return swapIndexBase_;
    }

    public SwapIndex shortSwapIndexBase() {
        return shortSwapIndexBase_;
    }

    public boolean vegaWeightedSmileFit() {
        return vegaWeightedSmileFit_;
    }

    // ------------------------------------------------------------------
    // LazyObject interface
    // ------------------------------------------------------------------

    @Override
    protected void performCalculations() {
        QL_REQUIRE(nStrikes_ >= requiredNumberOfStrikes(),
                "too few strikes (" + nStrikes_ + ") required are at least "
                        + requiredNumberOfStrikes());
        super.performCalculations();
    }

    // ------------------------------------------------------------------
    // SwaptionVolatilityStructure interface
    // ------------------------------------------------------------------

    protected int requiredNumberOfStrikes() {
        return 2;
    }

    @Override
    protected double volatilityImpl(double optionTime,
                                    double swapLength,
                                    double strike) {
        return smileSectionImpl(optionTime, swapLength).volatility(strike);
    }

    @Override
    protected double volatilityImpl(Date optionDate,
                                    Period swapTenor,
                                    double strike) {
        return smileSectionImpl(optionDate, swapTenor).volatility(strike);
    }

    @Override
    protected double shiftImpl(double optionTime, double swapLength) {
        return atmVol_.currentLink().shift(optionTime, swapLength);
    }

    protected void registerWithVolatilitySpread() {
        for (int i = 0; i < nStrikes_; ++i)
            for (int j = 0; j < nOptionTenors_; ++j)
                for (int k = 0; k < nSwapTenors_; ++k)
                    registerWith(volSpreads_.get(j * nSwapTenors_ + k)
                            .get(i).get());
    }

    private static SwaptionVolatilityStructure linked(
            Handle<SwaptionVolatilityStructure> handle) {
        QL_REQUIRE(!handle.empty(), "atm vol handle not linked to anything");
        return handle.currentLink();
    }

    private static List<List<Handle<Quote>>> copy(
            List<List<Handle<Quote>>> rows) {
        List<List<Handle<Quote>>> result = new ArrayList<>(rows.size());
        for (List<Handle<Quote>> row : rows)
            result.add(new ArrayList<>(row));
        return result;
    }
}
