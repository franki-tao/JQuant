package jquant.pricingengines.vanilla;

import jquant.Option;
import jquant.math.Function;
import jquant.math.Solver1D;
import jquant.math.integrals.TanhSinhIntegral;
import jquant.math.interpolations.ChebyshevInterpolation;
import jquant.math.solvers1d.Brent;
import jquant.math.solvers1d.Newton;
import jquant.math.solvers1d.Ridder;
import jquant.pricingengines.BlackCalculator;
import jquant.processes.GeneralizedBlackScholesProcess;

import static jquant.math.CommonUtil.QL_FAIL;
import static jquant.math.CommonUtil.QL_REQUIRE;
import static jquant.math.MathUtils.NULL_REAL;
import static jquant.math.MathUtils.NULL_SIZE;
import static jquant.math.MathUtils.QL_EPSILON;
import static jquant.math.MathUtils.close;
import static jquant.math.MathUtils.squared;

/**
 * American engine based on the QD+ approximation to the exercise boundary.
 *
 * <p>Ported from {@code ql/pricingengines/vanilla/qdplusamericanengine.hpp/.cpp}.
 */
public class QdPlusAmericanEngine extends QdPutCallParityEngine {
    public enum SolverType {Brent, Newton, Ridder, Halley, SuperHalley}

    private final int interpolationPoints_;
    private final SolverType solverType_;
    private final double eps_;
    private final int maxIter_;

    public QdPlusAmericanEngine(GeneralizedBlackScholesProcess process) {
        this(process, 8, SolverType.Halley, 1e-6, NULL_SIZE);
    }

    public QdPlusAmericanEngine(GeneralizedBlackScholesProcess process,
                                int interpolationPoints) {
        this(process, interpolationPoints, SolverType.Halley, 1e-6, NULL_SIZE);
    }

    public QdPlusAmericanEngine(GeneralizedBlackScholesProcess process,
                                int interpolationPoints,
                                SolverType solverType, double eps) {
        this(process, interpolationPoints, solverType, eps, NULL_SIZE);
    }

    public QdPlusAmericanEngine(GeneralizedBlackScholesProcess process,
                                int interpolationPoints,
                                SolverType solverType, double eps,
                                int maxIter) {
        super(process);
        interpolationPoints_ = interpolationPoints;
        solverType_ = solverType;
        eps_ = eps;
        maxIter_ = (maxIter == NULL_SIZE)
                ? ((solverType == SolverType.Newton
                    || solverType == SolverType.Brent
                    || solverType == SolverType.Ridder) ? 100 : 10)
                : maxIter;
    }

    /** Result of an exercise-boundary evaluation: number of evaluations and boundary value. */
    public record BoundaryResult(int evaluations, double boundary) {
    }

    private double buildInSolver(QdPlusBoundaryEvaluator eval,
                                 Solver1D solver, double S, double strike,
                                 int maxIter, double guess) {
        solver.setMaxEvaluations(maxIter);
        solver.setLowerBound(eval.xmin());

        final double fxmin = eval.value(eval.xmin());
        double xmax = Math.max(0.5 * (eval.xmax() + S), eval.xmax());
        while (eval.value(xmax) * fxmin > 0.0
                && eval.evaluations() < maxIter_) {
            xmax *= 2;
        }

        if (guess == NULL_REAL) {
            guess = 0.5 * (xmax + S);
        }

        if (guess >= xmax) {
            guess = Math.nextAfter(xmax, -1.0);
        } else if (guess <= eval.xmin()) {
            guess = Math.nextAfter(eval.xmin(), Double.POSITIVE_INFINITY);
        }

        return solver.solve(eval, eps_, guess, eval.xmin(), xmax);
    }

    public BoundaryResult putExerciseBoundaryAtTau(
            double S, double K, double r, double q,
            double vol, double T, double tau) {

        if (tau < QL_EPSILON) {
            return new BoundaryResult(0, xMax(K, r, q));
        }

        final QdPlusBoundaryEvaluator eval =
                new QdPlusBoundaryEvaluator(S, K, r, q, vol, tau, T);

        double x;
        switch (solverType_) {
            case Brent:
                x = buildInSolver(eval, new Brent(), S, K, maxIter_, NULL_REAL);
                break;
            case Newton:
                x = buildInSolver(eval, new Newton(), S, K, maxIter_, NULL_REAL);
                break;
            case Ridder:
                x = buildInSolver(eval, new Ridder(), S, K, maxIter_, NULL_REAL);
                break;
            case Halley:
            case SuperHalley: {
                boolean resultCloseEnough;
                x = eval.xmax();
                double xOld, fx;
                final double xmin = eval.xmin();

                do {
                    xOld = x;
                    fx = eval.value(x);
                    final double fPrime = eval.derivative(x);
                    final double lf = fx * eval.fprime2(x) / (fPrime * fPrime);
                    final double step = (solverType_ == SolverType.Halley)
                            ? 1 / (1 - 0.5 * lf) * fx / fPrime
                            : (1 + 0.5 * lf / (1 - lf)) * fx / fPrime;

                    x = Math.max(xmin, x - step);
                    resultCloseEnough = Math.abs(x - xOld) < 0.5 * eps_;
                }
                while (!resultCloseEnough && eval.evaluations() < maxIter_);

                if (!resultCloseEnough && !close(Math.abs(fx), 0.0)) {
                    x = buildInSolver(eval, new Brent(), S, K,
                            10 * maxIter_, x);
                }
                break;
            }
            default:
                QL_FAIL("unknown solver type");
                x = Double.NaN;
        }

        return new BoundaryResult(eval.evaluations(), x);
    }

    public ChebyshevInterpolation getPutExerciseBoundary(
            double S, double K, double r, double q, double vol, double T) {

        final double xmax = xMax(K, r, q);

        return new ChebyshevInterpolation(interpolationPoints_,
                z -> {
                    final double x_sq = 0.25 * T * squared(1 + z);
                    return squared(Math.log(
                            this.putExerciseBoundaryAtTau(S, K, r, q, vol, T, x_sq)
                                    .boundary() / xmax));
                },
                ChebyshevInterpolation.PointsType.SecondKind);
    }

    @Override
    protected double calculatePut(double S, double K, double r, double q,
                                  double vol, double T) {
        if (r < 0.0 && q < r) {
            QL_FAIL("double-boundary case q<r<0 for a put option is given");
        }

        final ChebyshevInterpolation q_z =
                getPutExerciseBoundary(S, K, r, q, vol, T);

        final double xmax = xMax(K, r, q);
        final QdPlusAddOnValue aov =
                new QdPlusAddOnValue(T, S, K, r, q, vol, xmax, q_z);

        final double addOn =
                new TanhSinhIntegral(eps_).value(aov, 0.0, Math.sqrt(T));

        QL_REQUIRE(addOn > -10 * eps_,
                "negative early exercise value " + addOn);

        final double europeanValue = Math.max(0.0,
                new BlackCalculator(Option.Type.Put, K,
                        S * Math.exp((r - q) * T),
                        vol * Math.sqrt(T), Math.exp(-r * T)).value());

        return europeanValue + Math.max(0.0, addOn);
    }

    public static double xMax(double K, double r, double q) {
        // Table 2 from Leif Andersen, Mark Lake (2021)
        // "Fast American Option Pricing: The Double-Boundary Case"

        if (r > 0.0 && q > 0.0) {
            return K * Math.min(1.0, r / q);
        } else if (r > 0.0 && q <= 0.0) {
            return K;
        } else if (r == 0.0 && q < 0.0) {
            return K;
        } else if (r == 0.0 && q >= 0.0) {
            return 0.0; // European case
        } else if (r < 0.0 && q >= 0.0) {
            return 0.0; // European case
        } else if (r < 0.0 && q < r) {
            return K; // double boundary case
        } else if (r < 0.0 && r <= q && q < 0.0) {
            return 0; // European case
        } else {
            QL_FAIL("internal error");
            return Double.NaN;
        }
    }
}
