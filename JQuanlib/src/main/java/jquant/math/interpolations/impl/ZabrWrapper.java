package jquant.math.interpolations.impl;

import jquant.termstructures.volatility.Sarb;
import jquant.termstructures.volatility.ZabrEvaluation;
import jquant.termstructures.volatility.ZabrSmileSection;

import java.util.List;

/**
 * Adapter exposing a {@link ZabrSmileSection} as an {@link XABRModel} for
 * {@link XABRInterpolationImpl}. The official {@code ZabrSpecs::instance}
 * returns the smile section itself; this wrapper keeps one section instance
 * alive (and therefore one finite-difference setup) while the optimiser asks
 * for volatilities, exactly as the official interpolation does.
 */
public class ZabrWrapper implements XABRModel {

    private final ZabrSmileSection section_;

    public ZabrWrapper(double t,
                       double forward,
                       List<Double> params,
                       ZabrEvaluation evaluation) {
        final double[] p = new double[params.size()];
        for (int i = 0; i < p.length; ++i)
            p[i] = params.get(i);
        section_ = new ZabrSmileSection(t, forward, p, evaluation,
                new double[0], 5);
    }

    @Override
    public double volatility(double x, Sarb.VolatilityType volatilityType) {
        // The ZABR smile section always uses its own native representation;
        // the ATM volatility type is ignored by the official model.
        return section_.volatility(x);
    }

    public ZabrSmileSection section() {
        return section_;
    }
}
