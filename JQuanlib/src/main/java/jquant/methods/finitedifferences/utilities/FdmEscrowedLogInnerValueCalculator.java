package jquant.methods.finitedifferences.utilities;

import jquant.Payoff;
import jquant.methods.finitedifferences.meshers.FdmMesher;
import jquant.methods.finitedifferences.operators.FdmLinearOpIterator;

/**
 * Inner value for the escrowed dividend model.
 *
 * <p>Port of
 * {@code ql/methods/finitedifferences/utilities/fdmescrowedloginnervaluecalculator.{hpp,cpp}}.
 */
public class FdmEscrowedLogInnerValueCalculator
        implements FdmInnerValueCalculator {
    private final EscrowedDividendAdjustment escrowedDividendAdj_;
    private final Payoff payoff_;
    private final FdmMesher mesher_;
    private final int direction_;

    public FdmEscrowedLogInnerValueCalculator(
            EscrowedDividendAdjustment escrowedDividendAdj,
            Payoff payoff,
            FdmMesher mesher,
            int direction) {
        escrowedDividendAdj_ = escrowedDividendAdj;
        payoff_ = payoff;
        mesher_ = mesher;
        direction_ = direction;
    }

    @Override
    public double innerValue(FdmLinearOpIterator iter, double t) {
        final double s_t = Math.exp(mesher_.location(iter, direction_));
        final double spot = s_t
                - escrowedDividendAdj_.dividendAdjustment(t);

        return payoff_.value(spot);
    }

    @Override
    public double avgInnerValue(FdmLinearOpIterator iter, double t) {
        return innerValue(iter, t);
    }
}
