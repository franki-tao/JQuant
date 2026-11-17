package jquant.pricingengines.vanilla;

import jquant.Option;
import jquant.math.Array;
import jquant.math.Function;
import jquant.math.integrals.Integrator;
import jquant.math.interpolations.ChebyshevInterpolation;
import jquant.pricingengines.BlackCalculator;
import jquant.processes.GeneralizedBlackScholesProcess;

import static jquant.math.CommonUtil.QL_FAIL;
import static jquant.math.MathUtils.QL_EPSILON;
import static jquant.math.MathUtils.squared;

/**
 * High performance/precision American engine based on fixed-point iteration
 * for the exercise boundary.
 *
 * <p>Ported from {@code ql/pricingengines/vanilla/qdfpamericanengine.hpp/.cpp}.
 */
public class QdFpAmericanEngine extends QdPutCallParityEngine {
    public enum FixedPointEquation {FP_A, FP_B, Auto}

    private static final QdFpIterationScheme FAST_SCHEME =
            new QdFpLegendreScheme(7, 2, 7, 27);
    private static final QdFpIterationScheme ACCURATE_SCHEME =
            new QdFpLegendreTanhSinhScheme(25, 5, 13, 1e-8);
    private static final QdFpIterationScheme HIGH_PRECISION_SCHEME =
            new QdFpTanhSinhIterationScheme(10, 30, 1e-10);

    private final QdFpIterationScheme iterationScheme_;
    private final FixedPointEquation fpEquation_;

    public QdFpAmericanEngine(GeneralizedBlackScholesProcess bsProcess) {
        this(bsProcess, accurateScheme(), FixedPointEquation.Auto);
    }

    public QdFpAmericanEngine(GeneralizedBlackScholesProcess bsProcess,
                              QdFpIterationScheme iterationScheme) {
        this(bsProcess, iterationScheme, FixedPointEquation.Auto);
    }

    public QdFpAmericanEngine(GeneralizedBlackScholesProcess bsProcess,
                              QdFpIterationScheme iterationScheme,
                              FixedPointEquation fpEquation) {
        super(bsProcess);
        iterationScheme_ = iterationScheme;
        fpEquation_ = fpEquation;
    }

    public static QdFpIterationScheme fastScheme() {
        return FAST_SCHEME;
    }

    public static QdFpIterationScheme accurateScheme() {
        return ACCURATE_SCHEME;
    }

    public static QdFpIterationScheme highPrecisionScheme() {
        return HIGH_PRECISION_SCHEME;
    }

    @Override
    protected double calculatePut(double S, double K, double r, double q,
                                  double vol, double T) {
        if (r < 0.0 && q < r) {
            QL_FAIL("double-boundary case q<r<0 for a put option is given");
        }

        final double xmax = QdPlusAmericanEngine.xMax(K, r, q);
        final int n = iterationScheme_
                .getNumberOfChebyshevInterpolationNodes();

        final ChebyshevInterpolation interp =
                new QdPlusAmericanEngine(process_, n + 1,
                        QdPlusAmericanEngine.SolverType.Halley, 1e-8)
                        .getPutExerciseBoundary(S, K, r, q, vol, T);

        final Array z = interp.nodes();
        final double[] x = new double[z.size()];
        for (int i = 0; i < x.length; ++i) {
            x[i] = 0.5 * Math.sqrt(T) * (1.0 + z.get(i));
        }

        final Function B = tau -> {
            final double zz = 2 * Math.sqrt(Math.abs(tau) / T) - 1;
            return xmax * Math.exp(-Math.sqrt(
                    Math.max(0.0, interp.value(zz, true))));
        };

        final Function h = fv -> squared(Math.log(fv / xmax));

        final DqFpEquation eqn = (fpEquation_ == FixedPointEquation.FP_A
                || (fpEquation_ == FixedPointEquation.Auto
                    && Math.abs(r - q) < 0.001))
                ? new DqFpEquationA(K, r, q, vol, B,
                        iterationScheme_.getFixedPointIntegrator())
                : new DqFpEquationB(K, r, q, vol, B,
                        iterationScheme_.getFixedPointIntegrator());

        final double[] y = new double[x.length];
        y[0] = 0.0;

        final int nNewton = iterationScheme_
                .getNumberOfJacobiNewtonFixedPointSteps();
        for (int k = 0; k < nNewton; ++k) {
            for (int i = 1; i < x.length; ++i) {
                final double tau = squared(x[i]);
                final double b = B.value(tau);

                final double[] results = eqn.f(tau, b);
                final double N = results[0];
                final double D = results[1];
                final double fv = results[2];

                if (tau < QL_EPSILON) {
                    y[i] = h.value(fv);
                } else {
                    final double[] ndd = eqn.NDd(tau, b);
                    final double Nd = ndd[0];
                    final double Dd = ndd[1];

                    final double fd = K * Math.exp(-(r - q) * tau)
                            * (Nd / D - Dd * N / (D * D));

                    y[i] = h.value(b - (fv - b) / (fd - 1));
                }
            }
            interp.updateY(new Array(y));
        }

        final int nFp = iterationScheme_
                .getNumberOfNaiveFixedPointSteps();
        for (int k = 0; k < nFp; ++k) {
            for (int i = 1; i < x.length; ++i) {
                final double tau = squared(x[i]);
                final double fv = eqn.f(tau, B.value(tau))[2];

                y[i] = h.value(fv);
            }
            interp.updateY(new Array(y));
        }

        final QdPlusAddOnValue aov =
                new QdPlusAddOnValue(T, S, K, r, q, vol, xmax, interp);
        final Integrator boundaryIntegrator =
                iterationScheme_.getExerciseBoundaryToPriceIntegrator();
        final double addOn =
                boundaryIntegrator.value(aov, 0.0, Math.sqrt(T));

        final double europeanValue = new BlackCalculator(Option.Type.Put, K,
                S * Math.exp((r - q) * T),
                vol * Math.sqrt(T), Math.exp(-r * T)).value();

        return Math.max(europeanValue, 0.0) + Math.max(0.0, addOn);
    }
}
