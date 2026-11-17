package jquant.methods.finitedifferences.utilities;

import jquant.Handle;
import jquant.Option;
import jquant.methods.finitedifferences.meshers.FdmMesher;
import jquant.methods.finitedifferences.operators.FdmLinearOpIterator;
import jquant.pricingengines.BlackFormula;
import jquant.pricingengines.PlainVanillaPayoff;
import jquant.termstructures.volatility.equityfx.BlackVolTermStructure;

/**
 * Inner value for a shout option.
 *
 * <p>Port of
 * {@code ql/methods/finitedifferences/utilities/fdmshoutloginnervaluecalculator.{hpp,cpp}}.
 */
public class FdmShoutLogInnerValueCalculator
        implements FdmInnerValueCalculator {
    private final Handle<BlackVolTermStructure> blackVolatility_;
    private final EscrowedDividendAdjustment escrowedDividendAdj_;
    private final double maturity_;
    private final PlainVanillaPayoff payoff_;
    private final FdmMesher mesher_;
    private final int direction_;

    public FdmShoutLogInnerValueCalculator(
            Handle<BlackVolTermStructure> blackVolatility,
            EscrowedDividendAdjustment escrowedDividendAdj,
            double maturity,
            PlainVanillaPayoff payoff,
            FdmMesher mesher,
            int direction) {
        blackVolatility_ = blackVolatility;
        escrowedDividendAdj_ = escrowedDividendAdj;
        maturity_ = maturity;
        payoff_ = payoff;
        mesher_ = mesher;
        direction_ = direction;
    }

    @Override
    public double innerValue(FdmLinearOpIterator iter, double t) {
        final double s_t = Math.exp(mesher_.location(iter, direction_));

        final double qf =
                escrowedDividendAdj_.dividendYield().getValue()
                        .discount(maturity_, false)
                        / escrowedDividendAdj_.dividendYield().getValue()
                        .discount(t, false);

        final double df =
                escrowedDividendAdj_.riskFreeRate().getValue()
                        .discount(maturity_, false)
                        / escrowedDividendAdj_.riskFreeRate().getValue()
                        .discount(t, false);

        final double fwd = s_t * qf / df;
        final double stdDev =
                blackVolatility_.getValue().blackForwardVol(
                        t, maturity_, s_t, false)
                        * Math.sqrt(maturity_ - t);

        final double npv = BlackFormula.blackFormula(
                payoff_.optionType(), s_t, fwd, stdDev, df, 0.0);

        final double spot =
                s_t - escrowedDividendAdj_.dividendAdjustment(t);

        final double intrinsic = (payoff_.optionType() == Option.Type.Call)
                ? spot - payoff_.strike() : payoff_.strike() - spot;

        return Math.max(0.0, npv + intrinsic * df);
    }

    @Override
    public double avgInnerValue(FdmLinearOpIterator iter, double t) {
        return innerValue(iter, t);
    }
}
