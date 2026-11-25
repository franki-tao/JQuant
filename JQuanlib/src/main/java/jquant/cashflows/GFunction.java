package jquant.cashflows;

import jquant.math.Function;

/**
 * G function used by Hagan's static-replication CMS pricers.
 *
 * <p>Ported from QuantLib {@code ql/cashflows/conundrumpricer.hpp}
 * ({@code GFunction}). Official {@code operator()(x)} is
 * {@link #value(double)} here.
 */
public abstract class GFunction implements Function {

    @Override
    public abstract double value(double x);

    /** Official {@code GFunction::firstDerivative}. */
    public abstract double firstDerivative(double x);

    /** Official {@code GFunction::secondDerivative}. */
    @Override
    public abstract double secondDerivative(double x);
}
