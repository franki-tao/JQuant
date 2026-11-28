package jquant.math.ode;

import jquant.math.distributions.GammaFunction;
import org.apache.commons.math3.complex.Complex;

import static jquant.math.CommonUtil.QL_REQUIRE;

/**
 * Fractional Adams-Bashforth-Moulton predictor-corrector scheme.
 *
 * <p>Official QuantLib {@code ql/math/ode/fractionaladams.hpp} port. The
 * official class is a template instantiated with {@code Real} and
 * {@code std::complex<Real>}; the two Java overloads of {@link #solve}
 * correspond to those two instantiations and keep the official predictor and
 * corrector weights and gamma factors.
 *
 * <p>Solves the fractional-order initial value problem
 * {@code D^alpha y(t) = f(t, y(t))}, {@code y(0) = y0},
 * {@code alpha in (0, 1]}, where {@code D^alpha} denotes the Caputo
 * fractional derivative. For {@code alpha = 1} the scheme reduces to the
 * classical one-step Adams-Bashforth-Moulton method. The memory of the
 * fractional kernel makes the cost {@code O(N^2)} in the number of time
 * steps.
 *
 * <p>References: K. Diethelm, N.J. Ford, A.D. Freed, A Predictor-Corrector
 * Approach for the Numerical Solution of Fractional Differential Equations,
 * Nonlinear Dynamics 29, 3-22 (2002).
 */
public class FractionalAdams {

    /** Official {@code std::function<Real(Real, const Real&)>}. */
    @FunctionalInterface
    public interface DoubleOdeFct {
        double value(double t, double y);
    }

    /** Official {@code std::function<complex<Real>(Real, const complex<Real>&)>}. */
    @FunctionalInterface
    public interface ComplexOdeFct {
        Complex value(double t, Complex y);
    }

    /** Powers and quadrature weights shared by both instantiations. */
    private static final class GridWeights {
        final double dt, dtAlpha, gamma1, gamma2;
        final double[] alphaPowers, alphaPlusOnePowers;
        final double[] predictorWeights, correctorWeights;

        GridWeights(double alpha, double tMax, int steps) {
            dt = tMax / steps;
            dtAlpha = Math.pow(dt, alpha);
            gamma1 = new GammaFunction().value(alpha + 1.0);
            gamma2 = new GammaFunction().value(alpha + 2.0);

            // powers k^alpha and k^(alpha + 1) for the quadrature weights
            alphaPowers = new double[steps + 2];
            alphaPlusOnePowers = new double[steps + 2];
            for (int k = 0; k <= steps + 1; ++k) {
                alphaPowers[k] = Math.pow((double) k, alpha);
                alphaPlusOnePowers[k] = alphaPowers[k] * k;
            }

            // predictor weights b_k = (k + 1)^alpha - k^alpha and corrector
            // weights c_k = (k + 2)^(alpha + 1) + k^(alpha + 1)
            //              - 2(k + 1)^(alpha + 1), both depending only on the
            // distance k = n - j to the current step
            predictorWeights = new double[steps];
            correctorWeights = new double[steps];
            for (int k = 0; k < steps; ++k) {
                predictorWeights[k] = alphaPowers[k + 1] - alphaPowers[k];
                correctorWeights[k] = alphaPlusOnePowers[k + 2]
                        + alphaPlusOnePowers[k]
                        - 2.0 * alphaPlusOnePowers[k + 1];
            }
        }
    }

    private final double alpha_;

    public FractionalAdams(double alpha) {
        QL_REQUIRE(alpha > 0.0 && alpha <= 1.0,
                "fractional order alpha (" + alpha + ") must be in (0, 1]");
        alpha_ = alpha;
    }

    /**
     * Solution values on the uniform grid {@code {0, dt, ..., tMax}} for a
     * real-valued ODE. Official {@code solve} with {@code T = Real}.
     */
    public double[] solve(DoubleOdeFct ode, double y0, double tMax, int steps) {
        final GridWeights w = weights(tMax, steps);

        final double[] y = new double[steps + 1];
        final double[] f = new double[steps + 1];
        y[0] = y0;
        f[0] = ode.value(0.0, y0);

        for (int n = 0; n < steps; ++n) {
            final double tNext = (n + 1) * w.dt;

            // corrector weight of f_0:
            // n^(alpha + 1) - (n - alpha)(n + 1)^alpha
            final double a0 = w.alphaPlusOnePowers[n]
                    - (n - alpha_) * w.alphaPowers[n + 1];

            double predictorSum = w.predictorWeights[n] * f[0];
            double correctorSum = a0 * f[0];

            for (int j = 1; j <= n; ++j) {
                final double fj = f[j];
                predictorSum += w.predictorWeights[n - j] * fj;
                correctorSum += w.correctorWeights[n - j] * fj;
            }

            final double yP = y0 + w.dtAlpha / w.gamma1 * predictorSum;
            correctorSum += ode.value(tNext, yP);

            y[n + 1] = y0 + w.dtAlpha / w.gamma2 * correctorSum;
            f[n + 1] = ode.value(tNext, y[n + 1]);
        }

        return y;
    }

    /**
     * Solution values on the uniform grid {@code {0, dt, ..., tMax}} for a
     * complex-valued ODE. Official {@code solve} with
     * {@code T = std::complex<Real>}.
     */
    public Complex[] solve(ComplexOdeFct ode, Complex y0, double tMax, int steps) {
        final GridWeights w = weights(tMax, steps);

        final double y0Re = y0.getReal();
        final double y0Im = y0.getImaginary();

        final double[] yRe = new double[steps + 1];
        final double[] yIm = new double[steps + 1];
        final double[] fRe = new double[steps + 1];
        final double[] fIm = new double[steps + 1];

        yRe[0] = y0Re;
        yIm[0] = y0Im;
        final Complex f0 = ode.value(0.0, y0);
        fRe[0] = f0.getReal();
        fIm[0] = f0.getImaginary();

        for (int n = 0; n < steps; ++n) {
            final double tNext = (n + 1) * w.dt;

            final double a0 = w.alphaPlusOnePowers[n]
                    - (n - alpha_) * w.alphaPowers[n + 1];

            double predictorRe = w.predictorWeights[n] * fRe[0];
            double predictorIm = w.predictorWeights[n] * fIm[0];
            double correctorRe = a0 * fRe[0];
            double correctorIm = a0 * fIm[0];

            for (int j = 1; j <= n; ++j) {
                final double p = w.predictorWeights[n - j];
                final double c = w.correctorWeights[n - j];
                predictorRe += p * fRe[j];
                predictorIm += p * fIm[j];
                correctorRe += c * fRe[j];
                correctorIm += c * fIm[j];
            }

            final double factorP = w.dtAlpha / w.gamma1;
            final Complex fPredicted = ode.value(tNext, new Complex(
                    y0Re + factorP * predictorRe,
                    y0Im + factorP * predictorIm));
            correctorRe += fPredicted.getReal();
            correctorIm += fPredicted.getImaginary();

            final double factorC = w.dtAlpha / w.gamma2;
            yRe[n + 1] = y0Re + factorC * correctorRe;
            yIm[n + 1] = y0Im + factorC * correctorIm;

            final Complex fn = ode.value(tNext,
                    new Complex(yRe[n + 1], yIm[n + 1]));
            fRe[n + 1] = fn.getReal();
            fIm[n + 1] = fn.getImaginary();
        }

        final Complex[] y = new Complex[steps + 1];
        for (int i = 0; i <= steps; ++i) {
            y[i] = new Complex(yRe[i], yIm[i]);
        }
        return y;
    }

    private GridWeights weights(double tMax, int steps) {
        QL_REQUIRE(tMax > 0.0, "tMax (" + tMax + ") must be positive");
        QL_REQUIRE(steps > 0, "at least one step required");
        return new GridWeights(alpha_, tMax, steps);
    }
}
