package jquant.pricingengines.vanilla;

import jquant.math.Function;
import jquant.math.distributions.CumulativeNormalDistribution;
import jquant.math.distributions.NormalDistribution;
import jquant.math.integrals.GaussLegendreIntegrator;
import jquant.math.integrals.Integrator;

/**
 * Base class for the fixed-point equations of the QD engine.
 *
 * <p>Ported from {@code DqFpEquation} in
 * {@code ql/pricingengines/vanilla/qdfpamericanengine.cpp}.
 */
abstract class DqFpEquation {
    protected final double r, q;
    protected final double vol;
    protected final Function B;
    protected final Integrator integrator;

    protected final NormalDistribution phi = new NormalDistribution();
    protected final CumulativeNormalDistribution Phi =
            new CumulativeNormalDistribution();

    protected final double[] x_i;
    protected final double[] w_i;

    protected record Dpm(double first, double second) {
    }

    DqFpEquation(double r, double q, double vol, Function B,
                 Integrator integrator) {
        this.r = r;
        this.q = q;
        this.vol = vol;
        this.B = B;
        this.integrator = integrator;

        if (integrator instanceof GaussLegendreIntegrator legendreIntegrator) {
            x_i = legendreIntegrator.getIntegration().x().toArray();
            w_i = legendreIntegrator.getIntegration().weights().toArray();
        } else {
            x_i = new double[0];
            w_i = new double[0];
        }
    }

    protected Dpm d(double t, double z) {
        final double v = vol * Math.sqrt(t);
        final double m = (Math.log(z) + (r - q) * t) / v + 0.5 * v;

        return new Dpm(m, m - v);
    }

    /** Returns {@code (Nd, Dd)}. */
    abstract double[] NDd(double tau, double b);

    /** Returns {@code (N, D, fv)}. */
    abstract double[] f(double tau, double b);
}
