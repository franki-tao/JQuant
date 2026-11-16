package jquant.methods.finitedifferences.utilities;

import jquant.methods.finitedifferences.operators.FdmLinearOpIterator;

/**
 * Layer of abstraction used to calculate the inner value.
 *
 * <p>Port of {@code ql/methods/finitedifferences/utilities/fdminnervaluecalculator.hpp}.
 */
public interface FdmInnerValueCalculator {
    double innerValue(FdmLinearOpIterator iter, double t);

    double avgInnerValue(FdmLinearOpIterator iter, double t);
}
