package jquant.methods.finitedifferences.meshers;

import jquant.Handle;
import jquant.math.Function;
import jquant.math.distributions.InverseCumulativeNormal;
import jquant.math.integrals.GaussLobattoIntegral;
import jquant.math.interpolations.LinearInterpolation;
import jquant.processes.HestonProcess;
import jquant.termstructures.YieldTermStructure;
import jquant.termstructures.volatility.equityfx.LocalVolTermStructure;

import static jquant.math.MathUtils.squared;

/**
 * One-dimensional grid mesher for the variance part of the Heston model
 * with a local-volatility leverage function.
 *
 * <p>Port of {@code ql/methods/finitedifferences/meshers/fdmhestonvariancemesher.{hpp,cpp}}.
 */
public class FdmHestonLocalVolatilityVarianceMesher extends Fdm1dMesher {
    private double volaEstimate_;

    public FdmHestonLocalVolatilityVarianceMesher(
            int size,
            final HestonProcess process,
            final LocalVolTermStructure leverageFct,
            double maturity) {
        this(size, process, leverageFct, maturity, 10, 0.0001, 1.0);
    }

    public FdmHestonLocalVolatilityVarianceMesher(
            int size,
            final HestonProcess process,
            final LocalVolTermStructure leverageFct,
            double maturity, int tAvgSteps, double epsilon,
            double mixingFactor) {
        super(size);

        final FdmHestonVarianceMesher mesher = new FdmHestonVarianceMesher(
                size, process, maturity, tAvgSteps, epsilon, mixingFactor);

        for (int i = 0; i < size; ++i) {
            dplus_.set(i, mesher.dplus(i));
            dminus_.set(i, mesher.dminus(i));
            locations_.set(i, mesher.location(i));
        }

        volaEstimate_ = mesher.volaEstimate();

        if (leverageFct != null) {
            // boost::accumulators mean over the leverage observations
            double accSum = 0.0;
            int accCount = 0;

            final double s0 = process.s0().getValue().value();

            accSum += leverageFct.localVol(0.0, s0, true);
            ++accCount;

            final Handle<YieldTermStructure> rTS = process.riskFreeRate();
            final Handle<YieldTermStructure> qTS = process.dividendYield();

            for (int l = 1; l <= tAvgSteps; ++l) {
                final double t = (maturity * l) / tAvgSteps;
                final double vol = volaEstimate_ * (accSum / accCount);

                final double fwd = s0 * qTS.getValue().discount(t, false)
                        / rTS.getValue().discount(t, false);

                final int sAvgSteps = 50;

                double[] u = new double[sAvgSteps];
                double[] sig = new double[sAvgSteps];

                for (int i = 0; i < sAvgSteps; ++i) {
                    u[i] = epsilon + ((1 - 2 * epsilon) / (sAvgSteps - 1)) * i;
                    final double x = new InverseCumulativeNormal().value(u[i]);

                    final double gf = x * vol * Math.sqrt(t);
                    final double f = fwd * Math.exp(gf);

                    sig[i] = squared(leverageFct.localVol(t, f, true));
                }

                final LinearInterpolation interpolatedVolatility =
                        new LinearInterpolation(u, sig);
                final Function fct = x -> Math.sqrt(interpolatedVolatility.value(x, true));

                final double leverageAvg =
                        new GaussLobattoIntegral(10000, 1e-4).value(
                                fct, u[0], u[u.length - 1])
                                / (1 - 2 * epsilon);

                accSum += leverageAvg;
                ++accCount;
            }
            volaEstimate_ *= (accSum / accCount);
        }
    }

    public double volaEstimate() {
        return volaEstimate_;
    }
}
