package jquant.math.interpolations.impl;

import jquant.termstructures.volatility.Sarb;

/**
 * Minimal interface implemented by the model instances plugged into
 * {@link XABRInterpolationImpl}.  In QuantLib this role is played by the
 * SABR/NoArbSABR wrapper typedef (for no-arbitrage SABR the smile section
 * itself is the wrapper).
 */
public interface XABRModel {
    double volatility(double x, Sarb.VolatilityType volatilityType);
}
