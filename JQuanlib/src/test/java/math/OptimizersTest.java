package math;

import jquant.math.Array;
import jquant.math.MathUtils;
import jquant.math.Matrix;
import jquant.math.optimization.*;
import jquant.math.optimization.impl.Configuration;
import jquant.math.randomnumbers.MersenneTwisterUniformRng;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static jquant.math.CommonUtil.*;
import static jquant.math.optimization.DifferentialEvolution.CrossoverType.Normal;
import static jquant.math.optimization.DifferentialEvolution.Strategy.BestMemberWithJitter;
import static jquant.math.optimization.DifferentialEvolution.Strategy.Rand1SelfadaptiveWithRotation;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class OptimizersTest {
    private static class OneDimensionalPolynomialDegreeN extends CostFunction {
        private Array coefficients_;
        private int polynomialDegree_;

        public OneDimensionalPolynomialDegreeN(final Array coefficients) {
            coefficients_ = new Array(coefficients);
            polynomialDegree_ = coefficients_.size() - 1;
        }

        @Override
        public double value(final Array x) {
            QL_REQUIRE(x.size() == 1, "independent variable must be 1 dimensional");
            double y = 0;
            for (int i = 0; i <= polynomialDegree_; ++i)
                y += coefficients_.get(i) * Math.pow(x.get(0), i);
            return y;
        }

        @Override
        public Array values(final Array x) {
            QL_REQUIRE(x.size() == 1, "independent variable must be 1 dimensional");
            return new Array(1, value(x));
        }
    }

    private static class NamedOptimizationMethod {
        public OptimizationMethod optimizationMethod;
        public String name;
    }

    private static class FirstDeJong extends CostFunction {
        @Override
        public Array values(final Array x) {
            return new Array(x.size(), value(x));
        }

        @Override
        public double value(final Array x) {
            return DotProduct(x, x);
        }
    }

    private static class SecondDeJong extends CostFunction {
        @Override
        public Array values(final Array x) {
            return new Array(x.size(), value(x));
        }

        @Override
        public double value(final Array x) {
            return 100.0 * (x.get(0) * x.get(0) - x.get(1)) * (x.get(0) * x.get(0) - x.get(1))
                    + (1.0 - x.get(0)) * (1.0 - x.get(0));
        }
    }

    private static class ModThirdDeJong extends CostFunction {
        @Override
        public Array values(final Array x) {
            return new Array(x.size(), value(x));
        }

        @Override
        public double value(final Array x) {
            double fx = 0.0;
            for (int i = 0; i < x.size(); i++) {
                fx += Math.floor(x.get(i)) * Math.floor(x.get(i));
            }
            return fx;
        }
    }

    private static class ModFourthDeJong extends CostFunction {
        public MersenneTwisterUniformRng uniformRng_;

        public ModFourthDeJong() {
            uniformRng_ = new MersenneTwisterUniformRng(4711);
        }

        @Override
        public Array values(final Array x) {
            return new Array(x.size(), value(x));
        }

        @Override
        public double value(final Array x) {
            double fx = 0.0;
            for (int i=0; i<x.size(); ++i) {
                fx += (i+1.0)*Math.pow(x.get(i),4.0) + uniformRng_.nextReal();
            }
            return fx;
        }
    }

    private static class Griewangk extends CostFunction {
        @Override
        public Array values(final Array x) {
            return new Array(x.size(), value(x));
        }
        @Override
        public double value(final Array x) {
            double fx = 0.0;
            for (int i = 0; i < x.size(); i++) {
                fx += x.get(i) * x.get(i) / 4000.0;
            }
            double p = 1.0;
            for (int i = 0; i < x.size(); ++i) {
                p *= Math.cos(x.get(i) / Math.sqrt(i + 1.0));
            }
            return fx - p + 1.0;
        }
    }

    private static List<CostFunction> costFunctions_ = new ArrayList<>();
    private static List<Constraint> constraints_ = new ArrayList<>();
    private static List<Array> initialValues_ = new ArrayList<>();
    private static List<Integer> maxIterations_ = new ArrayList<>(), maxStationaryStateIterations_ = new ArrayList<>();
    private static List<Double> rootEpsilons_ = new ArrayList<>();
    private static List<Double> functionEpsilons_ = new ArrayList<>();
    private static List<Double> gradientNormEpsilons_ = new ArrayList<>();
    private static List<EndCriteria> endCriterias_ = new ArrayList<>();
    private static List<List<NamedOptimizationMethod>> optimizationMethods_ = new ArrayList<>();
    private static List<Array> xMinExpected_ = new ArrayList<>(), yMinExpected_ = new ArrayList<>();

    // The goal of this cost function is simply to call another optimization inside
    // in order to test nested optimizations
    private static class OptimizationBasedCostFunction extends CostFunction {
        @Override
        public double value(final Array arr) {
            return 1.0;
        }

        @Override
        public Array values(final Array arr) {
            // dummy nested optimization
            Array coefficients = new Array(3, 1.0);
            OneDimensionalPolynomialDegreeN oneDimensionalPolynomialDegreeN = new OneDimensionalPolynomialDegreeN(coefficients);
            NoConstraint constraint = new NoConstraint();
            Array initialValues = new Array(1, 100.0);
            Problem problem = new Problem(oneDimensionalPolynomialDegreeN, constraint,
                    initialValues);
            LevenbergMarquardt optimizationMethod = new LevenbergMarquardt(1.0e-8, 1.0e-8, 1.0e-8, false);
            //Simplex optimizationMethod(0.1);
            //ConjugateGradient optimizationMethod;
            //SteepestDescent optimizationMethod;
            EndCriteria endCriteria = new EndCriteria(1000, 100, 1e-5, 1e-5, 1e-5);
            optimizationMethod.minimize(problem, endCriteria);
            // return dummy result
            return new Array(1, 0);
        }
    }

    private static enum OptimizationMethodType {
        simplex,
        levenbergMarquardt,
        levenbergMarquardt2,
        conjugateGradient,
        conjugateGradient_goldstein,
        steepestDescent,
        steepestDescent_goldstein,
        bfgs,
        bfgs_goldstein,
        lbfgsb
    }

    private static String optimizationMethodTypeToString(OptimizationMethodType type) {
        switch (type) {
            case simplex:
                return "Simplex";
            case levenbergMarquardt:
                return "Levenberg Marquardt";
            case levenbergMarquardt2:
                return "Levenberg Marquardt (cost function's jacbobian)";
            case conjugateGradient:
                return "Conjugate Gradient";
            case steepestDescent:
                return "Steepest Descent";
            case bfgs:
                return "BFGS";
            case lbfgsb:
                return "L-BFGS-B";
            case conjugateGradient_goldstein:
                return "Conjugate Gradient (Goldstein line search)";
            case steepestDescent_goldstein:
                return "Steepest Descent (Goldstein line search)";
            case bfgs_goldstein:
                return "BFGS (Goldstein line search)";
            default:
                QL_FAIL("unknown OptimizationMethod type");
        }
        return "";
    }

    private static OptimizationMethod makeOptimizationMethod(
            OptimizationMethodType optimizationMethodType,
            double simplexLambda,
            double levenbergMarquardtEpsfcn,
            double levenbergMarquardtXtol,
            double levenbergMarquardtGtol) {
        switch (optimizationMethodType) {
            case simplex:
                return new Simplex(simplexLambda);
            case levenbergMarquardt:
                return new LevenbergMarquardt(levenbergMarquardtEpsfcn,
                        levenbergMarquardtXtol,
                        levenbergMarquardtGtol, false);
            case levenbergMarquardt2:
                return new LevenbergMarquardt(levenbergMarquardtEpsfcn,
                        levenbergMarquardtXtol,
                        levenbergMarquardtGtol,
                        true);
            case conjugateGradient:
                return new ConjugateGradient(new ArmijoLineSearch(1.0e-8, 0.05, 0.65));
            case steepestDescent:
                return new SteepestDescent(new ArmijoLineSearch(1.0e-8, 0.05, 0.65));
            case bfgs:
                return new BFGS(new ArmijoLineSearch(1.0e-8, 0.05, 0.65));
            case lbfgsb:
                return new LBFGSB();
            case conjugateGradient_goldstein:
                return new ConjugateGradient(new GoldsteinLineSearch(1.0e-8, 0.05, 0.65, 1.5));
            case steepestDescent_goldstein:
                return new SteepestDescent(new GoldsteinLineSearch(1.0e-8, 0.05, 0.65, 1.5));
            case bfgs_goldstein:
                return new BFGS(new GoldsteinLineSearch(1.0e-8, 0.05, 0.65, 1.5));
            default:
                QL_FAIL("unknown OptimizationMethod type");
        }
        return null;
    }

    private static List<NamedOptimizationMethod> makeOptimizationMethods(
            final List<OptimizationMethodType> optimizationMethodTypes,
            double simplexLambda,
            double levenbergMarquardtEpsfcn,
            double levenbergMarquardtXtol,
            double levenbergMarquardtGtol) {
        List<NamedOptimizationMethod> results = new ArrayList<>();
        for (OptimizationMethodType optimizationMethodType : optimizationMethodTypes) {
            NamedOptimizationMethod namedOptimizationMethod = new NamedOptimizationMethod();
            namedOptimizationMethod.optimizationMethod = makeOptimizationMethod(
                    optimizationMethodType, simplexLambda, levenbergMarquardtEpsfcn,
                    levenbergMarquardtXtol, levenbergMarquardtGtol);
            namedOptimizationMethod.name = optimizationMethodTypeToString(optimizationMethodType);
            results.add(namedOptimizationMethod);
        }
        return results;
    }

    private static double maxDifference(final Array a, final Array b) {
        Array diff = a.subtract(b);
        double maxDiff = 0.0;
        for (int i = 0; i < diff.size(); ++i)
            maxDiff = Math.max(maxDiff, Math.abs(diff.get(i)));
        return maxDiff;
    }

    // Set up, for each cost function, all the ingredients for optimization:
    // constraint, initial guess, end criteria, optimization methods.
    private static void setup() {

        // Cost function n. 1: 1D polynomial of degree 2 (parabolic function y=a*x^2+b*x+c)
        final double a = 1;   // required a > 0
        final double b = 1;
        final double c = 1;
        Array coefficients = new Array(3);
        coefficients.set(0, c);
        coefficients.set(1, b);
        coefficients.set(2, a);
        costFunctions_.add(new OneDimensionalPolynomialDegreeN(coefficients));
        // Set constraint for optimizers: unconstrained problem
        constraints_.add(new NoConstraint());
        // Set initial guess for optimizer
        Array initialValue = new Array(1);
        initialValue.set(0, -100);
        initialValues_.add(initialValue);
        // Set end criteria for optimizer
        maxIterations_.add(10000);                // maxIterations
        maxStationaryStateIterations_.add(100);   // MaxStationaryStateIterations
        rootEpsilons_.add(1e-8);                  // rootEpsilon
        functionEpsilons_.add(1e-8);              // functionEpsilon
        gradientNormEpsilons_.add(1e-8);          // gradientNormEpsilon
        endCriterias_.add(new EndCriteria(
                maxIterations_.get(maxIterations_.size() - 1), maxStationaryStateIterations_.get(maxStationaryStateIterations_.size() - 1),
                rootEpsilons_.get(rootEpsilons_.size() - 1), functionEpsilons_.get(functionEpsilons_.size() - 1),
                gradientNormEpsilons_.get(gradientNormEpsilons_.size() - 1)));
        // Set optimization methods for optimizer
        List<OptimizationMethodType> optimizationMethodTypes = Arrays.asList(
                OptimizationMethodType.simplex,
                OptimizationMethodType.levenbergMarquardt,
                OptimizationMethodType.levenbergMarquardt2,
                OptimizationMethodType.conjugateGradient,
                OptimizationMethodType.bfgs,
                OptimizationMethodType.lbfgsb //, steepestDescent
        );
        double simplexLambda = 0.1;                   // characteristic search length for simplex
        double levenbergMarquardtEpsfcn = 1.0e-8;     // parameters specific for Levenberg-Marquardt
        double levenbergMarquardtXtol = 1.0e-8;     //
        double levenbergMarquardtGtol = 1.0e-8;     //
        optimizationMethods_.add(makeOptimizationMethods(
                optimizationMethodTypes,
                simplexLambda, levenbergMarquardtEpsfcn, levenbergMarquardtXtol,
                levenbergMarquardtGtol));
        // Set expected results for optimizer
        Array xMinExpected = new Array(1), yMinExpected = new Array(1);
        xMinExpected.set(0, -b / (2.0 * a));
        yMinExpected.set(0, -(b * b - 4.0 * a * c) / (4.0 * a));
        xMinExpected_.add(xMinExpected);
        yMinExpected_.add(yMinExpected);
    }

    @Test
    public void test() {
        setup();
        // Loop over problems (currently there is only 1 problem)
        for (int i = 0; i < costFunctions_.size(); ++i) {
            Problem problem = new Problem(costFunctions_.get(i), constraints_.get(i),
                    initialValues_.get(i));
            Array initialValues = problem.currentValue();
            // Loop over optimizers
            for (int j = 0; j < (optimizationMethods_.get(i)).size(); ++j) {
                double rootEpsilon = endCriterias_.get(i).rootEpsilon();
                int endCriteriaTests = 1;
                // Loop over rootEpsilon
                for (int k = 0; k < endCriteriaTests; ++k) {
                    problem.setCurrentValue(initialValues);
                    EndCriteria endCriteria = new EndCriteria(
                            endCriterias_.get(i).maxIterations(),
                            endCriterias_.get(i).maxStationaryStateIterations(),
                            rootEpsilon,
                            endCriterias_.get(i).functionEpsilon(),
                            endCriterias_.get(i).gradientNormEpsilon());
                    rootEpsilon *= .1;
                    EndCriteria.Type endCriteriaResult =
                            optimizationMethods_.get(i).get(j).optimizationMethod.minimize(
                                    problem, endCriteria);
                    Array xMinCalculated = problem.currentValue();
                    Array yMinCalculated = problem.values(xMinCalculated);

                    // Check optimization results vs known solution
                    boolean completed;
                    switch (endCriteriaResult) {
                        case None:
                        case MaxIterations:
                        case Unknown:
                            completed = false;
                            break;
                        default:
                            completed = true;
                    }

                    double xError = maxDifference(xMinCalculated, xMinExpected_.get(i));
                    double yError = maxDifference(yMinCalculated, yMinExpected_.get(i));
                    boolean correct = (xError <= endCriteria.rootEpsilon() ||
                            yError <= endCriteria.functionEpsilon());
                    assertFalse((!completed) || (!correct));
                }
            }
        }
    }

    @Test
    public void nestedOptimizationTest() {
        OptimizationBasedCostFunction optimizationBasedCostFunction = new OptimizationBasedCostFunction();
        NoConstraint constraint = new NoConstraint();
        Array initialValues = new Array(1, 0.0);
        Problem problem = new Problem(optimizationBasedCostFunction, constraint,
                initialValues);
        LevenbergMarquardt optimizationMethod = new LevenbergMarquardt(1e-8, 1e-8, 1e-8, false);
        //Simplex optimizationMethod(0.1);
        //ConjugateGradient optimizationMethod;
        //SteepestDescent optimizationMethod;
        EndCriteria endCriteria = new EndCriteria(1000, 100, 1e-5, 1e-5, 1e-5);
        optimizationMethod.minimize(problem, endCriteria);
    }

    // ---------------------------------------------------------------
    // Helpers and cases for the official L-BFGS-B tests
    // (test-suite/optimizers.cpp: RosenbrockFunction, WeightedQuadratic,
    // WeightedQuadraticValueOnly, projectedGradientNorm, testLBFGSB,
    // testLBFGSBActiveBounds, testLBFGSBCoverage).
    // ---------------------------------------------------------------

    private static Array array(double... values) {
        return new Array(values);
    }

    // Extended Rosenbrock function with analytic gradient; global minimum 0 at 1
    private static class RosenbrockFunction extends CostFunction {
        @Override
        public double value(final Array x) {
            double f = 0.0;
            for (int i = 0; i + 1 < x.size(); ++i)
                f += 100.0 * Math.pow(x.get(i + 1) - x.get(i) * x.get(i), 2)
                        + Math.pow(1.0 - x.get(i), 2);
            return f;
        }

        @Override
        public Array values(final Array x) {
            return new Array(1, value(x));
        }

        @Override
        public void gradient(Array grad, final Array x) {
            for (int i = 0; i < grad.size(); ++i)
                grad.set(i, 0.0);
            for (int i = 0; i + 1 < x.size(); ++i) {
                grad.set(i, grad.get(i)
                        - 400.0 * x.get(i) * (x.get(i + 1) - x.get(i) * x.get(i))
                        - 2.0 * (1.0 - x.get(i)));
                grad.set(i + 1, grad.get(i + 1)
                        + 200.0 * (x.get(i + 1) - x.get(i) * x.get(i)));
            }
        }

        @Override
        public double valueAndGradient(Array grad, final Array x) {
            gradient(grad, x);
            return value(x);
        }
    }

    // Separable quadratic sum_i {w_i (x_i - c_i)^2}; unconstrained minimum c.
    private static class WeightedQuadratic extends CostFunction {
        private final Array center_, weight_;

        public WeightedQuadratic(final Array center, final Array weight) {
            center_ = new Array(center);
            weight_ = new Array(weight);
        }

        @Override
        public double value(final Array x) {
            double f = 0.0;
            for (int i = 0; i < x.size(); ++i)
                f += weight_.get(i) * Math.pow(x.get(i) - center_.get(i), 2);
            return f;
        }

        @Override
        public Array values(final Array x) {
            return new Array(1, value(x));
        }

        @Override
        public void gradient(Array grad, final Array x) {
            for (int i = 0; i < x.size(); ++i)
                grad.set(i, 2.0 * weight_.get(i) * (x.get(i) - center_.get(i)));
        }

        @Override
        public double valueAndGradient(Array grad, final Array x) {
            gradient(grad, x);
            return value(x);
        }
    }

    // Same separable quadratic but with no analytic gradient, forcing the
    // optimizer onto the base-class central-difference gradient path.
    private static class WeightedQuadraticValueOnly extends CostFunction {
        private final Array center_, weight_;

        public WeightedQuadraticValueOnly(final Array center, final Array weight) {
            center_ = new Array(center);
            weight_ = new Array(weight);
        }

        @Override
        public double value(final Array x) {
            double f = 0.0;
            for (int i = 0; i < x.size(); ++i)
                f += weight_.get(i) * Math.pow(x.get(i) - center_.get(i), 2);
            return f;
        }

        @Override
        public Array values(final Array x) {
            return new Array(1, value(x));
        }
    }

    // infinity norm of the projected gradient P(x - g, l, u) - x, the
    // quantity that vanishes at a KKT point of a box-constrained problem.
    private static double projectedGradientNorm(final Array x, final Array g,
                                                final Array lo, final Array hi) {
        double norm = 0.0;
        for (int i = 0; i < x.size(); ++i) {
            double proj = Math.min(Math.max(x.get(i) - g.get(i), lo.get(i)),
                    hi.get(i)) - x.get(i);
            norm = Math.max(norm, Math.abs(proj));
        }
        return norm;
    }

    @Test
    public void testLBFGSB() {
        // Reference values were produced with
        // scipy.optimize.minimize(method='L-BFGS-B').
        EndCriteria endCriteria = new EndCriteria(1000, 100, 1e-12, 1e-12, 1e-10);

        // Unconstrained equivalence: Rosenbrock in 2 and 10 dimensions must
        // reach the analytic minimum 1, as plain (L-)BFGS does.
        for (int n : new int[]{2, 10}) {
            RosenbrockFunction f = new RosenbrockFunction();
            NoConstraint c = new NoConstraint();
            Array x0 = new Array(n, -1.0);
            Problem problem = new Problem(f, c, x0);
            LBFGSB optimizer = new LBFGSB(10, 1e-10, 1e1 * MathUtils.QL_EPSILON);
            optimizer.minimize(problem, endCriteria);
            Array x = problem.currentValue();
            Array expected = new Array(n, 1.0);
            double xError = maxDifference(x, expected);
            assertFalse(xError > 1e-4,
                    "L-BFGS-B on unconstrained Rosenbrock-" + n + "D"
                            + "\n    calculated: " + x
                            + "\n    expected:   " + expected
                            + "\n    x error:    " + xError);
            assertFalse(problem.functionValue() > 1e-8,
                    "L-BFGS-B Rosenbrock-" + n + "D f = "
                            + problem.functionValue() + " (expected ~0)");
        }

        final Array center = array(3.0, -2.0, 0.5);
        final Array weight = array(1.0, 4.0, 0.25);

        // Interior minimum: with wide bounds enclosing the unconstrained
        // optimum the result must equal the unconstrained minimizer.
        {
            WeightedQuadratic f = new WeightedQuadratic(center, weight);
            Array lo = new Array(3, -10.0), hi = new Array(3, 10.0);
            NonhomogeneousBoundaryConstraint c =
                    new NonhomogeneousBoundaryConstraint(lo, hi);
            Array x0 = new Array(3, 0.0);
            Problem problem = new Problem(f, c, x0);
            LBFGSB optimizer = new LBFGSB(10, 1e-10, 1e1 * MathUtils.QL_EPSILON);
            optimizer.minimize(problem, endCriteria);
            double xError = maxDifference(problem.currentValue(), center);
            assertFalse(xError > 1e-6,
                    "L-BFGS-B interior-bound quadratic"
                            + "\n    calculated: " + problem.currentValue()
                            + "\n    expected:   " + center
                            + "\n    x error:    " + xError);
        }

        // Active-bound minimum: the box [0,1]^3 clips the unconstrained
        // optimum (3,-2,0.5), so the solution must sit on the boundary at
        // (1, 0, 0.5) with a vanishing projected gradient.
        {
            WeightedQuadratic f = new WeightedQuadratic(center, weight);
            Array lo = new Array(3, 0.0), hi = new Array(3, 1.0);
            NonhomogeneousBoundaryConstraint c =
                    new NonhomogeneousBoundaryConstraint(lo, hi);
            Array x0 = new Array(3, 0.5);
            Problem problem = new Problem(f, c, x0);
            LBFGSB optimizer = new LBFGSB();
            EndCriteria.Type ret = optimizer.minimize(problem, endCriteria);
            // the operative stop must be the projected-gradient (KKT) test, not
            // the function-reduction fallback
            assertEquals(EndCriteria.Type.ZeroGradientNorm, ret,
                    "L-BFGS-B active-bound quadratic ended with " + ret
                            + " (expected ZeroGradientNorm)");
            Array x = problem.currentValue();
            Array expected = array(1.0, 0.0, 0.5);
            double xError = maxDifference(x, expected);
            assertFalse(xError > 1e-7,
                    "L-BFGS-B active-bound quadratic"
                            + "\n    calculated: " + x
                            + "\n    expected:   " + expected
                            + "\n    x error:    " + xError);
            Array g = new Array(3);
            f.gradient(g, x);
            double pg = projectedGradientNorm(x, g, lo, hi);
            assertFalse(pg > 1e-6,
                    "L-BFGS-B active-bound projected gradient = " + pg
                            + " (expected ~0)");
        }

        // Bound-constrained Rosenbrock: the box [-2,0.5]^2 clips the optimum;
        // SciPy's L-BFGS-B converges to (0.5, 0.25) on the boundary.
        {
            RosenbrockFunction f = new RosenbrockFunction();
            Array lo = new Array(2, -2.0), hi = new Array(2, 0.5);
            NonhomogeneousBoundaryConstraint c =
                    new NonhomogeneousBoundaryConstraint(lo, hi);
            Array x0 = new Array(2, -1.0);
            Problem problem = new Problem(f, c, x0);
            LBFGSB optimizer = new LBFGSB(10, 1e-10, 1e1 * MathUtils.QL_EPSILON);
            optimizer.minimize(problem, endCriteria);
            Array x = problem.currentValue();
            Array expected = array(0.5, 0.25);
            double xError = maxDifference(x, expected);
            assertFalse(xError > 1e-5,
                    "L-BFGS-B bound-constrained Rosenbrock"
                            + "\n    calculated: " + x
                            + "\n    expected:   " + expected
                            + "\n    x error:    " + xError);
            Array g = new Array(2);
            f.gradient(g, x);
            double pg = projectedGradientNorm(x, g, lo, hi);
            assertFalse(pg > 1e-6,
                    "L-BFGS-B bound-constrained Rosenbrock projected gradient = "
                            + pg + " (expected ~0)");
        }
    }

    @Test
    public void testLBFGSBActiveBounds() {
        EndCriteria endCriteria = new EndCriteria(1000, 100, 1e-12, 1e-12, 1e-10);

        // All bounds active at a corner: the unconstrained optimum (5,5,5)
        // lies outside [0,1]^3, so every coordinate is pinned at its upper
        // bound and the solution is the corner (1,1,1) with a vanishing
        // projected gradient. Exercises the empty free-set subspace path.
        {
            WeightedQuadratic f = new WeightedQuadratic(
                    array(5.0, 5.0, 5.0), array(1.0, 1.0, 1.0));
            Array lo = new Array(3, 0.0), hi = new Array(3, 1.0);
            NonhomogeneousBoundaryConstraint c =
                    new NonhomogeneousBoundaryConstraint(lo, hi);
            Array x0 = new Array(3, 0.5);
            Problem problem = new Problem(f, c, x0);
            LBFGSB optimizer = new LBFGSB(10, 1e-10, 1e1 * MathUtils.QL_EPSILON);
            optimizer.minimize(problem, endCriteria);
            Array x = problem.currentValue();
            Array expected = new Array(3, 1.0);
            double xError = maxDifference(x, expected);
            Array g = new Array(3);
            f.gradient(g, x);
            double pg = projectedGradientNorm(x, g, lo, hi);
            assertFalse(xError > 1e-8 || pg > 1e-8,
                    "L-BFGS-B all-active corner"
                            + "\n    calculated: " + x
                            + "\n    expected:   " + expected
                            + "\n    x error:    " + xError
                            + "\n    proj. grad: " + pg);
        }

        // Two simultaneously active bounds reached through distinct
        // breakpoints.
        {
            WeightedQuadratic f = new WeightedQuadratic(
                    array(10.0, 10.0), array(1.0, 100.0));
            Array lo = new Array(2, 0.0), hi = new Array(2, 1.0);
            NonhomogeneousBoundaryConstraint c =
                    new NonhomogeneousBoundaryConstraint(lo, hi);
            Array x0 = array(0.9, 0.1);
            Problem problem = new Problem(f, c, x0);
            LBFGSB optimizer = new LBFGSB(10, 1e-10, 1e1 * MathUtils.QL_EPSILON);
            optimizer.minimize(problem, endCriteria);
            Array x = problem.currentValue();
            Array expected = array(1.0, 1.0);
            double xError = maxDifference(x, expected);
            Array g = new Array(2);
            f.gradient(g, x);
            double pg = projectedGradientNorm(x, g, lo, hi);
            assertFalse(xError > 1e-8 || pg > 1e-8,
                    "L-BFGS-B two-active-bound quadratic"
                            + "\n    calculated: " + x
                            + "\n    expected:   " + expected
                            + "\n    x error:    " + xError
                            + "\n    proj. grad: " + pg);
        }

        // Single variable (n = 1) with an active bound: the minimum of
        // (x-5)^2 over [0,1] is the boundary point x = 1.
        {
            WeightedQuadratic f = new WeightedQuadratic(array(5.0), array(1.0));
            Array lo = new Array(1, 0.0), hi = new Array(1, 1.0);
            NonhomogeneousBoundaryConstraint c =
                    new NonhomogeneousBoundaryConstraint(lo, hi);
            Array x0 = new Array(1, 0.5);
            Problem problem = new Problem(f, c, x0);
            LBFGSB optimizer = new LBFGSB(10, 1e-10, 1e1 * MathUtils.QL_EPSILON);
            optimizer.minimize(problem, endCriteria);
            Array x = problem.currentValue();
            Array g = new Array(1);
            f.gradient(g, x);
            double xError = maxDifference(x, array(1.0));
            double pg = projectedGradientNorm(x, g, lo, hi);
            assertFalse(xError > 1e-8 || pg > 1e-8,
                    "L-BFGS-B n=1 active bound"
                            + "\n    calculated: " + x
                            + "\n    x error:    " + xError
                            + "\n    proj. grad: " + pg);
        }

        // Pinned coordinate (lower == upper): the third variable is frozen
        // at 0.25 while the first two reach their unconstrained optima.
        {
            WeightedQuadratic f = new WeightedQuadratic(
                    array(3.0, -2.0, 0.5), array(1.0, 4.0, 0.25));
            Array lo = array(-10.0, -10.0, 0.25);
            Array hi = array(10.0, 10.0, 0.25);
            NonhomogeneousBoundaryConstraint c =
                    new NonhomogeneousBoundaryConstraint(lo, hi);
            Array x0 = new Array(3, 0.0);
            Problem problem = new Problem(f, c, x0);
            LBFGSB optimizer = new LBFGSB(10, 1e-10, 1e1 * MathUtils.QL_EPSILON);
            optimizer.minimize(problem, endCriteria);
            Array x = problem.currentValue();
            Array expected = array(3.0, -2.0, 0.25);
            double xError = maxDifference(x, expected);
            assertFalse(xError > 1e-6,
                    "L-BFGS-B pinned coordinate (lower==upper)"
                            + "\n    calculated: " + x
                            + "\n    expected:   " + expected
                            + "\n    x error:    " + xError);
        }
    }

    @Test
    public void testLBFGSBCoverage() {
        EndCriteria endCriteria = new EndCriteria(1000, 100, 1e-12, 1e-12, 1e-10);

        // Limited memory smaller than the dimension (m < n).
        {
            final int n = 20;
            RosenbrockFunction f = new RosenbrockFunction();
            NoConstraint c = new NoConstraint();
            Array x0 = new Array(n, -1.0);
            Problem problem = new Problem(f, c, x0);
            LBFGSB optimizer = new LBFGSB(3, 1e-8, 1e1 * MathUtils.QL_EPSILON);
            optimizer.minimize(problem, endCriteria);
            Array x = problem.currentValue();
            Array expected = new Array(n, 1.0);
            double xError = maxDifference(x, expected);
            assertFalse(xError > 1e-4,
                    "L-BFGS-B small-memory Rosenbrock-" + n + "D (m=3)"
                            + "\n    x error: " + xError);
            assertFalse(problem.functionValue() > 1e-8,
                    "L-BFGS-B small-memory Rosenbrock f = "
                            + problem.functionValue() + " (expected ~0)");
        }

        // Infeasible start point: x0 lies outside the box and must be
        // clipped into [l,u] before optimization.
        {
            WeightedQuadratic f = new WeightedQuadratic(
                    array(3.0, -2.0, 0.5), array(1.0, 4.0, 0.25));
            Array lo = new Array(3, 0.0), hi = new Array(3, 1.0);
            NonhomogeneousBoundaryConstraint c =
                    new NonhomogeneousBoundaryConstraint(lo, hi);
            Array x0 = new Array(3, 5.0); // outside [0,1]^3
            Problem problem = new Problem(f, c, x0);
            LBFGSB optimizer = new LBFGSB(10, 1e-10, 1e1 * MathUtils.QL_EPSILON);
            optimizer.minimize(problem, endCriteria);
            Array x = problem.currentValue();
            Array expected = array(1.0, 0.0, 0.5);
            double xError = maxDifference(x, expected);
            assertFalse(xError > 1e-7,
                    "L-BFGS-B infeasible start"
                            + "\n    calculated: " + x
                            + "\n    expected:   " + expected
                            + "\n    x error:    " + xError);
        }

        // Finite-difference gradient with active bounds.
        {
            WeightedQuadraticValueOnly f = new WeightedQuadraticValueOnly(
                    array(3.0, -2.0, 0.7), array(1.0, 4.0, 0.25));
            Array lo = new Array(3, 0.0), hi = new Array(3, 1.0);
            NonhomogeneousBoundaryConstraint c =
                    new NonhomogeneousBoundaryConstraint(lo, hi);
            Array x0 = new Array(3, 0.5);
            Problem problem = new Problem(f, c, x0);
            LBFGSB optimizer = new LBFGSB(10, 1e-6, 1e7 * MathUtils.QL_EPSILON);
            optimizer.minimize(problem, endCriteria);
            Array x = problem.currentValue();
            Array expected = array(1.0, 0.0, 0.7);
            double xError = maxDifference(x, expected);
            assertFalse(xError > 1e-5,
                    "L-BFGS-B finite-difference gradient with bounds"
                            + "\n    calculated: " + x
                            + "\n    expected:   " + expected
                            + "\n    x error:    " + xError);
        }
    }

    @Test
    public void testDifferentialEvolution() {
        /* Note:
         *
         * The "ModFourthDeJong" doesn't have a well defined optimum because
         * of its noisy part. It just has to be <= 15 in our example.
         * The concrete value might differ for a different input and
         * different random numbers.
         *
         * The "Griewangk" function is an example where the adaptive
         * version of DifferentialEvolution turns out to be more successful.
         */

        Configuration conf =
               new Configuration()
                .withStepsizeWeight(0.4)
                .withBounds(true)
                .withCrossoverProbability(0.35)
                .withPopulationMembers(500)
                .withStrategy(BestMemberWithJitter)
                .withCrossoverType(Normal)
                .withAdaptiveCrossover(true)
                .withSeed(3242);
        DifferentialEvolution deOptim = new DifferentialEvolution(conf);

        Configuration conf2 =
                new Configuration()
                .withStepsizeWeight(1.8)
                .withBounds(true)
                .withCrossoverProbability(0.9)
                .withPopulationMembers(1000)
                .withStrategy(Rand1SelfadaptiveWithRotation)
                .withCrossoverType(Normal)
                .withAdaptiveCrossover(true)
                .withSeed(3242);
        DifferentialEvolution deOptim2 = new DifferentialEvolution(conf2);

        List<DifferentialEvolution > diffEvolOptimisers = Arrays.asList(deOptim,deOptim,deOptim,deOptim,deOptim2);

        List<CostFunction> costFunctions = Arrays.asList(
                new FirstDeJong(),
                new SecondDeJong(),
                new ModThirdDeJong(),
                new ModFourthDeJong(),
                new Griewangk());

        List<BoundaryConstraint> constraints = Arrays.asList(
                new BoundaryConstraint(-10.0, 10.0),
                new BoundaryConstraint(-10.0, 10.0),
                new BoundaryConstraint(-10.0, 10.0),
                new BoundaryConstraint(-10.0, 10.0),
                new BoundaryConstraint(-600.0, 600.0)
        );
        List<Array> initialValues = Arrays.asList(
                new Array(3,5.0),
                new Array(2, 5.0),
                new Array(5, 5.0),
                new Array(30,5.0),
                new Array(10,100.0)

        );

        List<EndCriteria> endCriteria = Arrays.asList(
                new EndCriteria(100, 10, 1e-10, 1e-8, MathUtils.NULL_REAL),
                new EndCriteria(100, 10, 1e-10, 1e-8, MathUtils.NULL_REAL),
                new EndCriteria(100, 10, 1e-10, 1e-8, MathUtils.NULL_REAL),
                new EndCriteria(500, 100, 1e-10, 1e-8, MathUtils.NULL_REAL),
                new EndCriteria(1000, 800, 1e-12, 1e-10, MathUtils.NULL_REAL)
        );

        List<Double> minima = Arrays.asList(
                0.0,
                0.0,
                0.0,
                10.9639796558,
                0.0);

        for (int i = 0; i < costFunctions.size(); ++i) {
            Problem problem = new Problem(costFunctions.get(i), constraints.get(i), initialValues.get(i));
            diffEvolOptimisers.get(i).minimize(problem, endCriteria.get(i));

            if (i != 3) {
                // stable
                assertFalse(Math.abs(problem.functionValue() - minima.get(i)) > 1e-8,
                        "costFunction # " + i
                                + "\ncalculated: " + problem.functionValue()
                                + "\nexpected:   " + minima.get(i));
            } else {
                // this case is unstable due to randomness; we're good as
                // long as the result is below 15
                assertFalse(problem.functionValue() > 15,
                        "costFunction # " + i
                                + "\ncalculated: " + problem.functionValue()
                                + "\nexpected:   less than 15");
            }
        }
    }

    // ---------------------------------------------------------------
    // Helpers and cases for the official CMA-ES test
    // (test-suite/optimizers.cpp: Rastrigin, QuadraticForm, testCMAES).
    // ---------------------------------------------------------------

    private static class Rastrigin extends CostFunction {
        @Override
        public double value(final Array x) {
            double fx = 10.0 * x.size();

            for (int i = 0; i < x.size(); ++i) {
                final double xi = x.get(i);
                fx += xi * xi - 10.0 * Math.cos(2.0 * Math.PI * xi);
            }

            return fx;
        }

        @Override
        public Array values(final Array x) {
            return new Array(x.size(), value(x));
        }
    }

    private static class QuadraticForm extends CostFunction {
        private final Matrix m_;

        public QuadraticForm(final Matrix m) {
            m_ = new Matrix(m.rows(), m.cols(), 0.0);
            for (int i = 0; i < m.rows(); ++i)
                for (int j = 0; j < m.cols(); ++j)
                    m_.set(i, j, m.get(i, j));
        }

        @Override
        public double value(final Array x) {
            return DotProduct(x, m_.mutiply(x));
        }

        @Override
        public Array values(final Array x) {
            return new Array(x.size(), value(x));
        }
    }

    @Test
    public void testCMAES() {
        // Convex convergence: shifted, ill-conditioned separable quadratics
        // (2D, 10D) must reach the analytic minimum value 0.
        {
            EndCriteria endCriteria = new EndCriteria(
                    5000, 100, 1e-14, 1e-14, MathUtils.NULL_REAL);
            for (int n : new int[]{2, 10}) {
                Array center = new Array(n), weight = new Array(n);

                for (int i = 0; i < n; ++i) {
                    center.set(i, 1.0 + 0.5 * i);
                    weight.set(i, Math.pow(10.0, 2.0 * i / (double) n));
                }

                WeightedQuadratic f = new WeightedQuadratic(center, weight);
                NoConstraint c = new NoConstraint();
                Array x0 = new Array(n, 0.0);
                Problem problem = new Problem(f, c, x0);
                Cmaes optimizer = new Cmaes(
                        new Cmaes.Configuration().withSigma(1.0).withSeed(42));

                optimizer.minimize(problem, endCriteria);

                assertFalse(problem.functionValue() > 1e-8,
                        "CMA-ES convex quadratic-" + n + "D f = "
                                + problem.functionValue()
                                + " (expected < 1e-8)");
            }
        }

        // Non-convex convergence: Rosenbrock (2D, 10D) must reach the
        // minimizer (1,...,1) with f approximately 0.
        {
            EndCriteria endCriteria = new EndCriteria(
                    20000, 300, 1e-14, 1e-14, MathUtils.NULL_REAL);
            for (int n : new int[]{2, 10}) {
                RosenbrockFunction f = new RosenbrockFunction();
                NoConstraint c = new NoConstraint();
                Array x0 = new Array(n, 0.0);
                Cmaes.Configuration cfg = new Cmaes.Configuration()
                        .withSigma(0.5).withSeed(123);

                if (n >= 10)
                    cfg.withPopulationSize(20); // larger population for 10D

                Problem problem = new Problem(f, c, x0);
                new Cmaes(cfg).minimize(problem, endCriteria);
                Array expected = new Array(n, 1.0);

                double xError = maxDifference(problem.currentValue(), expected);
                assertFalse(problem.functionValue() > 1e-8 || xError > 1e-4,
                        "CMA-ES Rosenbrock-" + n + "D"
                                + "\n    f       = " + problem.functionValue()
                                + "\n    x error = " + xError);
            }
        }

        // Multimodal global search: Rastrigin 5D from a start away from the
        // origin must reach the global minimum (not merely a local one).
        {
            final int n = 5;
            Rastrigin f = new Rastrigin();
            BoundaryConstraint c = new BoundaryConstraint(-5.12, 5.12);
            Array x0 = new Array(n, 3.0);
            EndCriteria endCriteria = new EndCriteria(
                    2000, 200, 1e-12, 1e-12, MathUtils.NULL_REAL);

            // Large population lets CMA-ES cross the local-minimum lattice
            // to the global basin.
            Cmaes.Configuration cfg = new Cmaes.Configuration()
                    .withSigma(4.0).withPopulationSize(150).withSeed(1);
            Problem cmaesProblem = new Problem(f, c, x0);
            new Cmaes(cfg).minimize(cmaesProblem, endCriteria);

            double cmaesValue = cmaesProblem.functionValue();
            assertFalse(cmaesValue > 1e-4,
                    "CMA-ES Rastrigin-5D f = " + cmaesValue
                            + " (expected < 1e-4)");
        }

        // Rotational/affine invariance: an ill-conditioned quadratic x^T A x
        // and its rotation by an orthogonal Q must be solved in a comparable
        // evaluation budget.
        {
            Matrix A = new Matrix(2, 2, 0.0);
            A.set(0, 0, 1.0);
            A.set(1, 1, 30.0);

            double theta = 0.7;
            Matrix Q = new Matrix(2, 2, 0.0);
            Q.set(0, 0, Math.cos(theta));
            Q.set(0, 1, -Math.sin(theta));
            Q.set(1, 0, Math.sin(theta));
            Q.set(1, 1, Math.cos(theta));
            Matrix Arot = Q.transpose().multipy(A).multipy(Q);

            QuadraticForm f = new QuadraticForm(A);
            QuadraticForm fRot = new QuadraticForm(Arot);
            NoConstraint c = new NoConstraint();

            Array x0 = array(2.0, 2.0);
            Array x0Rot = Q.transpose().mutiply(x0); // s.t. Q * x0Rot = x0

            EndCriteria endCriteria = new EndCriteria(
                    5000, 100, 1e-14, 1e-14, MathUtils.NULL_REAL);
            Cmaes.Configuration cfg = new Cmaes.Configuration()
                    .withSigma(1.0).withSeed(99);

            Problem pA = new Problem(f, c, x0);
            new Cmaes(cfg).minimize(pA, endCriteria);

            Problem pRot = new Problem(fRot, c, x0Rot);
            new Cmaes(cfg).minimize(pRot, endCriteria);

            assertFalse(pA.functionValue() > 1e-8 || pRot.functionValue() > 1e-8,
                    "CMA-ES invariance: values " + pA.functionValue()
                            + " and " + pRot.functionValue()
                            + " (expected < 1e-8)");

            double evalsA = pA.functionEvaluation();
            double evalsRot = pRot.functionEvaluation();
            double ratio = Math.max(evalsA, evalsRot) / Math.min(evalsA, evalsRot);
            assertFalse(ratio > 2.0,
                    "CMA-ES rotational invariance: eval budgets differ by "
                            + ratio + "x (" + evalsA + " vs " + evalsRot + ")");
        }

        // Determinism check: runs must be bit-identical.
        {
            WeightedQuadratic f = new WeightedQuadratic(
                    array(1.0, -2.0, 0.5), array(1.0, 3.0, 7.0));
            NoConstraint c = new NoConstraint();
            Array x0 = new Array(3, 0.0);
            EndCriteria endCriteria = new EndCriteria(
                    500, 50, 1e-12, 1e-12, MathUtils.NULL_REAL);
            Cmaes.Configuration cfg = new Cmaes.Configuration()
                    .withSigma(1.0).withSeed(2024);

            Problem p1 = new Problem(f, c, x0);
            new Cmaes(cfg).minimize(p1, endCriteria);
            Problem p2 = new Problem(f, c, x0);
            new Cmaes(cfg).minimize(p2, endCriteria);

            assertFalse(p1.functionValue() != p2.functionValue(),
                    "CMA-ES not reproducible: " + p1.functionValue()
                            + " != " + p2.functionValue());
        }

        // Box bounds: unconstrained minimum (3,-2) lies outside [0, 1]^2.
        // The returned point must be feasible and sit at the constrained
        // minimum (1,0).
        {
            WeightedQuadratic f = new WeightedQuadratic(
                    array(3.0, -2.0), array(1.0, 1.0));
            BoundaryConstraint c = new BoundaryConstraint(0.0, 1.0);
            Array x0 = new Array(2, 0.5);
            EndCriteria endCriteria = new EndCriteria(
                    3000, 100, 1e-14, 1e-14, MathUtils.NULL_REAL);
            Cmaes.Configuration cfg = new Cmaes.Configuration()
                    .withSigma(0.3).withSeed(5);
            Problem problem = new Problem(f, c, x0);

            new Cmaes(cfg).minimize(problem, endCriteria);

            Array x = problem.currentValue();

            Array expected = array(1.0, 0.0);
            assertTrue(c.test(x),
                    "CMA-ES box-bounds returned infeasible point " + x);
            double xError = maxDifference(x, expected);
            assertFalse(xError > 1e-5,
                    "CMA-ES box-bounds"
                            + "\n    calculated: " + x
                            + "\n    expected:   " + expected
                            + "\n    x error:    " + xError);
        }

        // Ill-conditioned covariance: weights spanning twelve decades drive
        // the factorization of C over roughly six orders of magnitude in
        // scale.
        {
            final int n = 6;
            Array center = new Array(n, 0.0), weight = new Array(n);
            for (int i = 0; i < n; ++i)
                weight.set(i, Math.pow(10.0, 12.0 * i / (n - 1.0)));

            WeightedQuadratic f = new WeightedQuadratic(center, weight);
            NoConstraint c = new NoConstraint();
            Array x0 = new Array(n, 1.0);
            EndCriteria endCriteria = new EndCriteria(
                    5000, 200, 1e-16, 1e-16, MathUtils.NULL_REAL);
            Problem problem = new Problem(f, c, x0);
            Cmaes optimizer = new Cmaes(
                    new Cmaes.Configuration().withSigma(1.0).withSeed(11));

            optimizer.minimize(problem, endCriteria);

            assertFalse(problem.functionValue() > 1e-8,
                    "CMA-ES condition-1e12 f = " + problem.functionValue()
                            + " (expected < 1e-8)");
        }

        // Rank-deficient objective: the last coordinate carries zero weight,
        // so C collapses in that direction. The search must stay usable and
        // stop on a termination criterion rather than throwing or going
        // non-finite.
        {
            final int n = 5;
            Array center = new Array(n, 0.0), weight = new Array(n, 1.0);
            weight.set(n - 1, 0.0);

            WeightedQuadratic f = new WeightedQuadratic(center, weight);
            NoConstraint c = new NoConstraint();
            Array x0 = new Array(n, 1.0);
            EndCriteria endCriteria = new EndCriteria(
                    5000, 200, 1e-16, 1e-16, MathUtils.NULL_REAL);
            Problem problem = new Problem(f, c, x0);
            Cmaes optimizer = new Cmaes(
                    new Cmaes.Configuration().withSigma(0.5).withSeed(13));

            EndCriteria.Type ecType = optimizer.minimize(problem, endCriteria);

            assertFalse(problem.functionValue() > 1e-8,
                    "CMA-ES rank-deficient f = " + problem.functionValue()
                            + " (expected < 1e-8)");
            assertFalse(ecType == EndCriteria.Type.None,
                    "CMA-ES rank-deficient did not terminate on a criterion");
        }

        // Rotated ill-conditioned quadratic. Unlike the separable case above,
        // the rotation correlates every coordinate, which reduces the
        // smallest Cholesky pivot below its floor multiple times per run.
        {
            final int n = 4;
            Matrix Q = new Matrix(n, n, 0.0);
            for (int i = 0; i < n; ++i)
                Q.set(i, i, 1.0);

            // fixed Givens rotations; no RNG, so the test is reproducible
            final int[] p = {0, 1, 2, 0};
            final int[] q = {1, 2, 3, 3};
            final double[] angle = {0.7, 1.1, 0.4, 0.9};
            for (int k = 0; k < 4; ++k) {
                Matrix G = new Matrix(n, n, 0.0);
                for (int i = 0; i < n; ++i)
                    G.set(i, i, 1.0);
                G.set(p[k], p[k], Math.cos(angle[k]));
                G.set(p[k], q[k], -Math.sin(angle[k]));
                G.set(q[k], p[k], Math.sin(angle[k]));
                G.set(q[k], q[k], Math.cos(angle[k]));
                Q = G.multipy(Q);
            }

            Matrix D = new Matrix(n, n, 0.0);
            for (int i = 0; i < n; ++i)
                D.set(i, i, Math.pow(1e12, (double) i / (n - 1)));

            QuadraticForm f = new QuadraticForm(
                    Q.transpose().multipy(D).multipy(Q));
            NoConstraint c = new NoConstraint();
            Array x0 = new Array(n, 1.0);
            EndCriteria endCriteria = new EndCriteria(
                    3000, 500, 1e-18, 1e-18, MathUtils.NULL_REAL);
            Problem problem = new Problem(f, c, x0);
            Cmaes optimizer = new Cmaes(
                    new Cmaes.Configuration().withSigma(0.5).withSeed(17));

            optimizer.minimize(problem, endCriteria);

            assertFalse(problem.functionValue() > 1e-8,
                    "CMA-ES rotated ill-conditioned f = "
                            + problem.functionValue()
                            + " (expected < 1e-8)");
        }
    }

    // ---------------------------------------------------------------
    // Helpers and cases for the official Hybrid Simulated Annealing tests
    // (test-suite/optimizers.cpp: StationarySampler, ImprovingSampler,
    // RejectAllMoves, AcceptAllMoves, StagedCooling, ConstantTemperature,
    // MultidimensionalQuadratic, runHybridAnnealing and the three
    // testHybridSimulatedAnnealing* cases).
    // ---------------------------------------------------------------

    private static class StationarySampler implements HybridSimulatedAnnealing.Sampler {
        @Override
        public void sample(Array newPoint, Array currentPoint, Array temp) {
            for (int i = 0; i < currentPoint.size(); ++i)
                newPoint.set(i, currentPoint.get(i));
        }
    }

    private static class ImprovingSampler implements HybridSimulatedAnnealing.Sampler {
        @Override
        public void sample(Array newPoint, Array currentPoint, Array temp) {
            for (int i = 0; i < currentPoint.size(); ++i)
                newPoint.set(i, currentPoint.get(i) / 2.0);
        }
    }

    private static class RejectAllMoves implements HybridSimulatedAnnealing.Probability {
        @Override
        public boolean accept(double currentValue, double newValue, Array temp) {
            return false;
        }
    }

    private static class AcceptAllMoves implements HybridSimulatedAnnealing.Probability {
        @Override
        public boolean accept(double currentValue, double newValue, Array temp) {
            return true;
        }
    }

    private static class StagedCooling implements HybridSimulatedAnnealing.Temperature {
        @Override
        public void apply(Array newTemperature, Array currentTemperature, Array steps) {
            for (int i = 0; i < newTemperature.size(); ++i)
                newTemperature.set(i, 0.0);
            if (steps.get(0) <= 2.0)
                newTemperature.set(1, 0.5);
        }
    }

    private static class ConstantTemperature implements HybridSimulatedAnnealing.Temperature {
        @Override
        public void apply(Array newTemperature, Array currentTemperature, Array steps) {
            for (int i = 0; i < newTemperature.size(); ++i)
                newTemperature.set(i, currentTemperature.get(i));
        }
    }

    private static class MultidimensionalQuadratic extends CostFunction {
        @Override
        public double value(final Array x) {
            return DotProduct(x, x);
        }

        @Override
        public Array values(final Array x) {
            return new Array(1, value(x));
        }
    }

    private static class HybridAnnealingResult {
        public final EndCriteria.Type endCriteriaType;
        public final int functionEvaluation;

        public HybridAnnealingResult(EndCriteria.Type endCriteriaType,
                                     int functionEvaluation) {
            this.endCriteriaType = endCriteriaType;
            this.functionEvaluation = functionEvaluation;
        }
    }

    private static HybridAnnealingResult runHybridAnnealing(
            HybridSimulatedAnnealing.Sampler sampler,
            HybridSimulatedAnnealing.Probability probability,
            HybridSimulatedAnnealing.Temperature temperature,
            int maxIterations,
            int maxStationaryStateIterations) {
        MultidimensionalQuadratic costFunction = new MultidimensionalQuadratic();
        NoConstraint constraint = new NoConstraint();
        Problem problem = new Problem(costFunction, constraint, new Array(2, 1.0));
        EndCriteria endCriteria = new EndCriteria(maxIterations,
                maxStationaryStateIterations, 1e-8, 1e-8, 1e-8);

        HybridSimulatedAnnealing annealing = new HybridSimulatedAnnealing(
                sampler, probability, temperature,
                new HybridSimulatedAnnealingFunctors.ReannealingTrivial(),
                1.0, 0.5, 0,
                HybridSimulatedAnnealing.ResetScheme.NoResetScheme, 0, null,
                HybridSimulatedAnnealing.LocalOptimizeScheme.NoLocalOptimize);

        EndCriteria.Type result = annealing.minimize(problem, endCriteria);
        return new HybridAnnealingResult(result, problem.functionEvaluation());
    }

    @Test
    public void testHybridSimulatedAnnealingTemperatureTermination() {
        HybridAnnealingResult result = runHybridAnnealing(
                new StationarySampler(), new RejectAllMoves(),
                new StagedCooling(), 100, 50);

        assertEquals(EndCriteria.Type.None, result.endCriteriaType);
        assertEquals(3, result.functionEvaluation);
    }

    @Test
    public void testHybridSimulatedAnnealingIterationTermination() {
        HybridAnnealingResult result = runHybridAnnealing(
                new ImprovingSampler(), new AcceptAllMoves(),
                new ConstantTemperature(), 3, 2);

        assertEquals(EndCriteria.Type.MaxIterations, result.endCriteriaType);
        assertEquals(4, result.functionEvaluation);
    }

    @Test
    public void testHybridSimulatedAnnealingStationaryTermination() {
        HybridAnnealingResult result = runHybridAnnealing(
                new StationarySampler(), new RejectAllMoves(),
                new ConstantTemperature(), 100, 2);

        assertEquals(EndCriteria.Type.StationaryPoint, result.endCriteriaType);
        assertEquals(3, result.functionEvaluation);
    }
}
