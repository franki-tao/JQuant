package jquant.pricingengines;

import jquant.Compounding;
import jquant.processes.GeneralizedBlackScholesProcess;
import jquant.time.Frequency;

/**
 * Default greek calculations.
 *
 * <p>Ported from QuantLib {@code ql/pricingengines/greeks.cpp}.
 */
public final class Greeks {
    private Greeks() {
    }

    /** Default theta calculation for Black-Scholes options. */
    public static double blackScholesTheta(GeneralizedBlackScholesProcess p,
                                           double value, double delta, double gamma) {
        double u = p.stateVariable().getValue().value();
        double r = p.riskFreeRate().getValue()
                .zeroRate(0.0, Compounding.Continuous, Frequency.ANNUAL, false).rate();
        double q = p.dividendYield().getValue()
                .zeroRate(0.0, Compounding.Continuous, Frequency.ANNUAL, false).rate();
        double v = p.localVolatility().getValue().localVol(0.0, u, false);
        return r * value - (r - q) * u * delta - 0.5 * v * v * u * u * gamma;
    }

    /** Default theta-per-day calculation. */
    public static double defaultThetaPerDay(double theta) {
        return theta / 365.0;
    }
}
