package jquant.processes.impl;

import jquant.math.Function;
import jquant.math.distributions.InverseCumulativeNormal;
import jquant.math.integrals.GaussLaguerreIntegration;
import jquant.math.integrals.GaussLobattoIntegral;
import jquant.processes.HestonProcess;
import org.apache.commons.math3.complex.Complex;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static jquant.ModifiedBessel.modifiedBesselFunction_i;
import static jquant.math.CommonUtil.QL_FAIL;
import static jquant.math.MathUtils.*;

public class ProcessUtil {
    // This is the continuous version of a characteristic function
    // for the exact sampling of the Heston process, s. page 8, formula 13,
    // M. Broadie, O. Kaya, Exact Simulation of Stochastic Volatility and
    // other Affine Jump Diffusion Processes
    // http://finmath.stanford.edu/seminars/documents/Broadie.pdf
    //
    // This version does not need a branch correction procedure.
    // For details please see:
    // Roger Lord, "Efficient Pricing Algorithms for exotic Derivatives",
    // http://repub.eur.nl/pub/13917/LordR-Thesis.pdf
    public static Complex Phi(final HestonProcess process, final Complex a, double nu_0, double nu_t, double dt) {
        final double theta = process.theta();
        final double kappa = process.kappa();
        final double sigma = process.sigma();

        final double sigma2 = sigma * sigma;
        final Complex ga = new Complex(0.0, 1.0).multiply(a).multiply(-2 * sigma2).add(kappa * kappa).sqrt();
        final double d = 4 * theta * kappa / sigma2;

        final double nu = 0.5 * d - 1;
        final Complex z = ga.multiply(ga.multiply(-0.5 * dt).exp()).divide(ga.multiply(-dt).exp().multiply(-1).add(1.0));
        final Complex log_z = ga.multiply(-0.5 * dt).add(ga.divide(ga.multiply(-dt).exp().multiply(-1).add(1.0)).log());

        final Complex alpha = ga.multiply(4.0).multiply(ga.multiply(-0.5 * dt).exp()).
                divide(ga.multiply(-dt).exp().multiply(-1).add(1.0).multiply(sigma2));
        final Complex beta =
                new Complex(4.0 * kappa * Math.exp(-0.5 * kappa * dt) / (sigma2 * (1.0 - Math.exp(-kappa * dt))));

        final Complex tmp1 = ga.subtract(kappa).multiply(-0.5 * dt).exp();
        final double tmp2 = (1 - Math.exp(-kappa * dt));
        final Complex tmp3 = ga.multiply(-dt).exp().multiply(-1).add(1.0).multiply(kappa);
        final double tp1 = (nu_0 + nu_t) / sigma2;
        final double tp2 = kappa * (1.0 + Math.exp(-kappa * dt)) / (1.0 - Math.exp(-kappa * dt));
        final Complex tp3 = ga.multiply(ga.multiply(-dt).exp().add(1.0)).divide(ga.multiply(-dt).exp().multiply(-1).add(1.0));
        final Complex tmp4 = new Complex(tp1).multiply(new Complex(tp2).subtract(tp3)).exp();
        final Complex tmp5 = log_z.multiply(nu).exp().divide(z.pow(nu));


        return ga.multiply(tmp1).multiply(tmp2).divide(tmp3).multiply(tmp4).multiply(tmp5)
                .multiply((nu_t > 1e-8)
                        ? modifiedBesselFunction_i(
                        nu, alpha.multiply(Math.sqrt(nu_0 * nu_t)))
                        .divide(modifiedBesselFunction_i(
                                nu, beta.multiply(Math.sqrt(nu_0 * nu_t))))
                        : alpha.divide(beta).pow(nu)
                );
    }

    public static double ch(final HestonProcess process, double x, double u, double nu_0, double nu_t, double dt) {
        return M_2_PI * Math.sin(u * x) / u
                * Phi(process, new Complex(u), nu_0, nu_t, dt).getReal();
    }

    public static double ph(final HestonProcess process,
                            double x, double u, double nu_0, double nu_t, double dt) {
        return M_2_PI * Math.cos(u * x) * Phi(process, new Complex(u), nu_0, nu_t, dt).getReal();
    }

    public static double int_ph(final HestonProcess process,
                                double a, double x, double y, double nu_0, double nu_t, double t) {
        final GaussLaguerreIntegration gaussLaguerreIntegration = new GaussLaguerreIntegration(128);

        final double rho = process.rho();
        final double kappa = process.kappa();
        final double sigma = process.sigma();
        final double x0 = Math.log(process.s0().getValue().value());

        return gaussLaguerreIntegration.value(u -> ph(process, y, u, nu_0, nu_t, t))
                / Math.sqrt(2 * M_PI * (1 - rho * rho) * y)
                * Math.exp(-0.5 * squared(x - x0 - a + y * (0.5 - rho * kappa / sigma))
                / (y * (1 - rho * rho)));
    }

    public static double pade(double x, List<Double> nominator, List<Double> denominator, int m) {
        double n = 0.0, d = 0.0;
        for (int i = m - 1; i >= 0; --i) {
            n = (n + nominator.get(i)) * x;
            d = (d + denominator.get(i)) * x;
        }
        return (1 + n) / (1 + d);
    }

    // For the definition of the Pade approximation please see e.g.
    // http://wikipedia.org/wiki/Sine_integral#Sine_integral
    public static double Si(double x) {
        if (x <= 4.0) {
            final Double[] n =
                    {-4.54393409816329991e-2, 1.15457225751016682e-3,
                            -1.41018536821330254e-5, 9.43280809438713025e-8,
                            -3.53201978997168357e-10, 7.08240282274875911e-13,
                            -6.05338212010422477e-16};
            final Double[] d =
                    {1.01162145739225565e-2, 4.99175116169755106e-5,
                            1.55654986308745614e-7, 3.28067571055789734e-10,
                            4.5049097575386581e-13, 3.21107051193712168e-16,
                            0.0};

            return x * pade(x * x, Arrays.asList(n), Arrays.asList(d), n.length);
        } else {
            final double y = 1 / (x * x);
            final Double[] fn =
                    {7.44437068161936700618e2, 1.96396372895146869801e5,
                            2.37750310125431834034e7, 1.43073403821274636888e9,
                            4.33736238870432522765e10, 6.40533830574022022911e11,
                            4.20968180571076940208e12, 1.00795182980368574617e13,
                            4.94816688199951963482e12, -4.94701168645415959931e11};
            final Double[] fd =
                    {7.46437068161927678031e2, 1.97865247031583951450e5,
                            2.41535670165126845144e7, 1.47478952192985464958e9,
                            4.58595115847765779830e10, 7.08501308149515401563e11,
                            5.06084464593475076774e12, 1.43468549171581016479e13,
                            1.11535493509914254097e13, 0.0};
            final double f = pade(y, Arrays.asList(fn), Arrays.asList(fd), 10) / x;

            final Double[] gn =
                    {8.1359520115168615e2, 2.35239181626478200e5,
                            3.12557570795778731e7, 2.06297595146763354e9,
                            6.83052205423625007e10, 1.09049528450362786e12,
                            7.57664583257834349e12, 1.81004487464664575e13,
                            6.43291613143049485e12, -1.36517137670871689e12};
            final Double[] gd =
                    {8.19595201151451564e2, 2.40036752835578777e5,
                            3.26026661647090822e7, 2.23355543278099360e9,
                            7.87465017341829930e10, 1.39866710696414565e12,
                            1.17164723371736605e13, 4.01839087307656620e13,
                            3.99653257887490811e13, 0.0};
            final double g = y * pade(y, Arrays.asList(gn), Arrays.asList(gd), 10);

            return M_PI_2 - f * Math.cos(x) - g * Math.sin(x);
        }
    }

    public static double cornishFisherEps(final HestonProcess process,
                                          double nu_0, double nu_t, double dt, double eps) {
        // use moment generating function to get the
        // first,second, third and fourth moment of the distribution
        final double d = 1e-2;
        final double p2 = Phi(process, new Complex(0, -2 * d),
                nu_0, nu_t, dt).getReal();
        final double p1 = Phi(process, new Complex(0, -d),
                nu_0, nu_t, dt).getReal();
        final double p0 = Phi(process, new Complex(0, 0),
                nu_0, nu_t, dt).getReal();
        final double pm1 = Phi(process, new Complex(0, d),
                nu_0, nu_t, dt).getReal();
        final double pm2 = Phi(process, new Complex(0, 2 * d),
                nu_0, nu_t, dt).getReal();

        final double avg = (pm2 - 8 * pm1 + 8 * p1 - p2) / (12 * d);
        final double m2 = (-pm2 + 16 * pm1 - 30 * p0 + 16 * p1 - p2) / (12 * d * d);
        final double var = m2 - avg * avg;
        final double stdDev = Math.sqrt(var);

        final double m3 = (-0.5 * pm2 + pm1 - p1 + 0.5 * p2) / (d * d * d);
        final double skew
                = (m3 - 3 * var * avg - avg * avg * avg) / (var * stdDev);

        final double m4 = (pm2 - 4 * pm1 + 6 * p0 - 4 * p1 + p2) / (d * d * d * d);
        final double kurt
                = (m4 - 4 * m3 * avg + 6 * m2 * avg * avg - 3 * avg * avg * avg * avg)
                / (var * var);

        // Cornish-Fisher relation to come up with an improved
        // estimate of 1-F(u_\eps) < \eps
        final double q = new InverseCumulativeNormal().value(1 - eps);
        final double w = q + (q * q - 1) / 6 * skew + (q * q * q - 3 * q) / 24 * (kurt - 3)
                - (2 * q * q * q - 5 * q) / 36 * skew * skew;

        return avg + w * stdDev;
    }

    public static double cdf_nu_ds(final HestonProcess process,
                                   double x, double nu_0, double nu_t, double dt,
                                   HestonProcess.Discretization discretization) {
        final double eps = 1e-4;
        final double u_eps = Math.min(100.0,
                Math.max(0.1, cornishFisherEps(process, nu_0, nu_t, dt, eps)));

        switch (discretization) {
            case BroadieKayaExactSchemeLaguerre: {
                final GaussLaguerreIntegration gaussLaguerreIntegration = new GaussLaguerreIntegration(128);

                // get the upper bound for the integration
                double upper = u_eps / 2.0;
                while ((Phi(process, new Complex(upper), nu_0, nu_t, dt).divide(upper).abs())
                        > eps) upper *= 2.0;

                return (x < upper)
                        ? Math.max(0.0, Math.min(1.0,
                        gaussLaguerreIntegration.value(u -> ch(process, x, u, nu_0, nu_t, dt)))) : (1.0);
            }
            case BroadieKayaExactSchemeLobatto: {
                // get the upper bound for the integration
                double upper = u_eps / 2.0;
                while ((Phi(process, new Complex(upper), nu_0, nu_t, dt).divide(upper).abs()) > eps)
                    upper *= 2.0;

                return (x < upper)
                        ? Math.max(0.0, Math.min(1.0,
                        new GaussLobattoIntegral(NULL_SIZE, eps).value(xi -> ch(process, x, xi, nu_0, nu_t, dt), QL_EPSILON, upper))) : (1.0);
            }
            case BroadieKayaExactSchemeTrapezoidal: {
                final double h = 0.05;

                double si = Si(0.5 * h * x);
                double s = M_2_PI * si;
                Complex f;
                int j = 0;
                do {
                    ++j;
                    final double u = h * j;
                    final double si_n = Si(x * (u + 0.5 * h));

                    f = Phi(process, new Complex(u), nu_0, nu_t, dt);
                    s += M_2_PI * f.getReal() * (si_n - si);
                    si = si_n;
                }
                while (M_2_PI * f.abs() / j > eps);

                return s;
            }
            default:
                QL_FAIL("unknown integration method");
        }
        return Double.NaN;
    }

    public static double cdf_nu_ds_minus_x(final HestonProcess process, double x, double nu_0,
                                           double nu_t, double dt,
                                           HestonProcess.Discretization discretization,
                                           double x0) {
        return cdf_nu_ds(process, x, nu_0, nu_t, dt, discretization) - x0;
    }
}
