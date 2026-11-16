package jquant.termstructures.yield;

import jquant.Handle;
import jquant.math.interpolations.impl.Interpolator;
import jquant.termstructures.YieldTermStructure;

import java.util.List;

/**
 * Piecewise spread yield term structure.
 *
 * <p>This curve is bootstrapped on rate helpers exactly like a
 * {@link PiecewiseYieldCurve}, but its nodes are multiplicative spread
 * discount factors over a base curve. The discount at time {@code t} is
 * {@code baseCurve.discount(t) * spreadCurve.discount(t)}; the bootstrap
 * traits are QuantLib's {@code detail::SpreadTraits<Discount>}, i.e. the
 * ordinary Discount traits applied to spread discount factors.
 *
 * <p>This is the Java counterpart of QuantLib's
 * {@code PiecewiseSpreadYieldCurve<Traits, Interpolator, Bootstrap>}. The
 * template parameters are represented by the explicit {@link Interpolator} and
 * the {@link IterativeBootstrap} / {@link GlobalBootstrap} constructor
 * overloads.
 *
 * <p>https://github.com/lballabio/QuantLib/blob/master/ql/termstructures/yield/piecewisespreadyieldcurve.hpp
 * <p>https://github.com/lballabio/QuantLib/blob/master/ql/termstructures/yield/spreadbootstraptraits.hpp
 */
public class PiecewiseSpreadYieldCurve extends PiecewiseYieldCurve {

    /**
     * Builds the spread curve with the default {@code IterativeBootstrap}.
     *
     * @param baseCurve   handle to the curve the spread is applied to
     * @param instruments swap helpers whose quotes are spread-curve fair rates
     * @param interpolator interpolation over the spread discount nodes
     */
    public PiecewiseSpreadYieldCurve(Handle<YieldTermStructure> baseCurve,
                                     List<RateHelper> instruments,
                                     Interpolator interpolator) {
        this(baseCurve, instruments, interpolator, (IterativeBootstrap) null);
    }

    public PiecewiseSpreadYieldCurve(Handle<YieldTermStructure> baseCurve,
                                     List<RateHelper> instruments,
                                     Interpolator interpolator,
                                     IterativeBootstrap bootstrap) {
        super(baseCurve, instruments, BootstrapTraits.SpreadDiscount,
                interpolator, bootstrap);
    }

    public PiecewiseSpreadYieldCurve(Handle<YieldTermStructure> baseCurve,
                                     List<RateHelper> instruments,
                                     Interpolator interpolator,
                                     GlobalBootstrap bootstrap) {
        super(baseCurve, instruments, BootstrapTraits.SpreadDiscount,
                interpolator, bootstrap);
    }

    /** The base curve of this multiplicative spread curve. */
    public Handle<YieldTermStructure> baseCurve() {
        return baseCurveHandle();
    }
}
