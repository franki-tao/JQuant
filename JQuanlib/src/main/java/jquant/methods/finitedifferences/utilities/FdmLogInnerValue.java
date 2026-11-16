package jquant.methods.finitedifferences.utilities;

import jquant.Payoff;
import jquant.methods.finitedifferences.meshers.FdmMesher;

/**
 * Inner value for a payoff expressed in terms of {@code exp(x)}.
 *
 * <p>Port of {@code ql/methods/finitedifferences/utilities/fdminnervaluecalculator.cpp}.
 */
public class FdmLogInnerValue extends FdmCellAveragingInnerValue {
    public FdmLogInnerValue(Payoff payoff, FdmMesher mesher, int direction) {
        super(payoff, mesher, direction, Math::exp);
    }
}
