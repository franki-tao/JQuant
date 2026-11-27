package jquant.termstructures.volatility.equityfx;

import jquant.Handle;
import jquant.Quote;
import jquant.termstructures.YieldTermStructure;

/**
 * Wrapper around a Dupire local volatility surface, which does not throw an
 * exception if local volatility becomes negative.
 *
 * <p>Port of
 * {@code ql/termstructures/volatility/equityfx/noexceptlocalvolsurface.hpp}.
 */
public class NoExceptLocalVolSurface extends LocalVolSurface {

    private final double illegalLocalVolOverwrite_;

    public NoExceptLocalVolSurface(final Handle<BlackVolTermStructure> blackTS,
                                   final Handle<YieldTermStructure> riskFreeTS,
                                   final Handle<YieldTermStructure> dividendTS,
                                   final Handle<Quote> underlying,
                                   double illegalLocalVolOverwrite) {
        super(blackTS, riskFreeTS, dividendTS, underlying);
        illegalLocalVolOverwrite_ = illegalLocalVolOverwrite;
    }

    public NoExceptLocalVolSurface(final Handle<BlackVolTermStructure> blackTS,
                                   final Handle<YieldTermStructure> riskFreeTS,
                                   final Handle<YieldTermStructure> dividendTS,
                                   double underlying,
                                   double illegalLocalVolOverwrite) {
        super(blackTS, riskFreeTS, dividendTS, underlying);
        illegalLocalVolOverwrite_ = illegalLocalVolOverwrite;
    }

    @Override
    protected double localVolImpl(double t, double s) {
        double vol;
        try {
            vol = super.localVolImpl(t, s);
        } catch (RuntimeException ex) {
            vol = illegalLocalVolOverwrite_;
        }

        return vol;
    }
}
