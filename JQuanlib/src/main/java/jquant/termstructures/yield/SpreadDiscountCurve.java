package jquant.termstructures.yield;

import jquant.Handle;
import jquant.math.interpolations.impl.Interpolator;
import jquant.math.interpolations.impl.LogLinear;
import jquant.termstructures.YieldTermStructure;
import jquant.time.Date;

import java.util.List;

/**
 * Spread yield curve based on log-linear interpolation of discount factors.
 *
 * <p>Log-linear interpolation guarantees piecewise-constant spreads. This is
 * the official typedef
 * {@code SpreadDiscountCurve = InterpolatedSpreadDiscountCurve<LogLinear>}.
 *
 * <p>https://github.com/lballabio/QuantLib/blob/master/ql/termstructures/yield/spreaddiscountcurve.hpp
 */
public class SpreadDiscountCurve extends InterpolatedSpreadDiscountCurve {

    public SpreadDiscountCurve(Handle<YieldTermStructure> baseCurve,
                               List<Date> dates,
                               double[] dfs) {
        super(baseCurve, dates, dfs, new LogLinear());
    }

    public SpreadDiscountCurve(Handle<YieldTermStructure> baseCurve,
                               List<Date> dates,
                               double[] dfs,
                               Interpolator interpolator) {
        super(baseCurve, dates, dfs, interpolator);
    }

    public SpreadDiscountCurve(Handle<YieldTermStructure> baseCurve,
                               List<Date> dates,
                               List<Double> dfs) {
        super(baseCurve, dates, dfs, new LogLinear());
    }

    public SpreadDiscountCurve(Handle<YieldTermStructure> baseCurve,
                               List<Date> dates,
                               List<Double> dfs,
                               Interpolator interpolator) {
        super(baseCurve, dates, dfs, interpolator);
    }
}
