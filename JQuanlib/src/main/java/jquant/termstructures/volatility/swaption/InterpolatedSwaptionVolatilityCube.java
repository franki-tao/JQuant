package jquant.termstructures.volatility.swaption;

import jquant.Handle;
import jquant.Quote;
import jquant.indexes.SwapIndex;
import jquant.math.Matrix;
import jquant.math.Rounding;
import jquant.math.interpolations.BilinearInterpolation;
import jquant.math.interpolations.Interpolation2D;
import jquant.termstructures.SmileSection;
import jquant.termstructures.volatility.InterpolatedSmileSection;
import jquant.time.BusinessDayConvention;
import jquant.time.Date;
import jquant.time.Period;
import jquant.time.TimeUnit;
import jquant.time.TimeUtils;
import jquant.time.daycounters.Actual365Fixed;

import java.util.List;

/**
 * Interpolated swaption volatility cube (fit-later-interpolate-early).
 *
 * <p>Ported from QuantLib
 * {@code ql/termstructures/volatility/swaption/interpolatedswaptionvolatilitycube.{hpp,cpp}}.
 * The quoted volatility spreads are interpolated bilinearly over
 * {@code (swap length, option time)}; the smile for a given option is then
 * linear in strike on the standard deviations
 * {@code sqrt(t) * (atmVol + spread)}.
 *
 * <p>https://github.com/lballabio/QuantLib/blob/master/ql/termstructures/volatility/swaption/interpolatedswaptionvolatilitycube.hpp
 */
public class InterpolatedSwaptionVolatilityCube extends SwaptionVolatilityCube {

    private final Interpolation2D[] volSpreadsInterpolator_;
    private final double[][][] volSpreadsMatrix_;

    public InterpolatedSwaptionVolatilityCube(
            Handle<SwaptionVolatilityStructure> atmVolStructure,
            List<Period> optionTenors,
            List<Period> swapTenors,
            double[] strikeSpreads,
            List<List<Handle<Quote>>> volSpreads,
            SwapIndex swapIndexBase,
            SwapIndex shortSwapIndexBase,
            boolean vegaWeightedSmileFit) {
        super(atmVolStructure, optionTenors, swapTenors, strikeSpreads,
                volSpreads, swapIndexBase, shortSwapIndexBase,
                vegaWeightedSmileFit);
        volSpreadsInterpolator_ = new Interpolation2D[nStrikes_];
        volSpreadsMatrix_ = new double[nStrikes_][nOptionTenors_][nSwapTenors_];
    }

    @Override
    protected void performCalculations() {
        super.performCalculations();

        //! set volSpreadsMatrix_ by volSpreads_ quotes
        for (int i = 0; i < nStrikes_; ++i)
            for (int j = 0; j < nOptionTenors_; ++j)
                for (int k = 0; k < nSwapTenors_; ++k)
                    volSpreadsMatrix_[i][j][k] = volSpreads_
                            .get(j * nSwapTenors_ + k).get(i)
                            .currentLink().value();

        //! create volSpreadsInterpolator_
        for (int i = 0; i < nStrikes_; ++i) {
            // X is the swap length, Y is the option time, as official.
            BilinearInterpolation bilinear = new BilinearInterpolation(
                    swapLengths_, optionTimes_, volSpreadsMatrix_[i]);
            bilinear.enableExtrapolation();
            volSpreadsInterpolator_[i] = bilinear;
        }
    }

    /** Quoted volatility-spread matrix for one strike. */
    public Matrix volSpreads(int i) {
        calculate();
        double[][] copy = new double[nOptionTenors_][nSwapTenors_];
        for (int j = 0; j < nOptionTenors_; ++j)
            System.arraycopy(volSpreadsMatrix_[i][j], 0, copy[j], 0,
                    nSwapTenors_);
        return new Matrix(copy);
    }

    @Override
    protected SmileSection smileSectionImpl(double optionTime,
                                            double swapLength) {
        calculate();

        Date optionDate = optionDateFromTime(optionTime);
        Rounding rounder = new Rounding(0, Rounding.Type.Closest, 5);
        Period swapTenor = new Period(
                (int) rounder.value(swapLength * 12.0), TimeUnit.MONTHS);
        // ensure that option date is valid fixing date
        optionDate = TimeUtils.greater(swapTenor,
                shortSwapIndexBase_.tenor())
                ? swapIndexBase_.fixingCalendar().adjust(optionDate,
                        BusinessDayConvention.FOLLOWING)
                : shortSwapIndexBase_.fixingCalendar().adjust(optionDate,
                        BusinessDayConvention.FOLLOWING);
        return smileSectionImpl(optionDate, swapTenor);
    }

    @Override
    protected SmileSection smileSectionImpl(Date optionDate,
                                            Period swapTenor) {
        calculate();

        double atmForward = atmStrike(optionDate, swapTenor);
        double atmVol = atmVol_.currentLink().volatility(optionDate,
                swapTenor, atmForward);
        double optionTime = timeFromReference(optionDate);
        double exerciseTimeSqrt = Math.sqrt(optionTime);
        double[] strikes = new double[nStrikes_];
        double[] stdDevs = new double[nStrikes_];
        double length = swapLength(swapTenor);
        for (int i = 0; i < nStrikes_; ++i) {
            strikes[i] = atmForward + strikeSpreads_[i];
            stdDevs[i] = exerciseTimeSqrt
                    * (atmVol + volSpreadsInterpolator_[i].value(
                            length, optionTime, true));
        }
        double shift = atmVol_.currentLink().shift(optionTime, length);
        return new InterpolatedSmileSection(optionTime, strikes, stdDevs,
                atmForward, new Actual365Fixed(), volatilityType(), shift);
    }
}
