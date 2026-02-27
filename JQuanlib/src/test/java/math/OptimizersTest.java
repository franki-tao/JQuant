package math;

import jquant.math.Array;
import jquant.math.MathUtils;
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
import static org.junit.jupiter.api.Assertions.assertFalse;

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
        bfgs_goldstein
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
                OptimizationMethodType.bfgs
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
        System.out.println("Testing optimizers...");
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
        System.out.println("Testing nested optimizations...");
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

    @Test
    public void testDifferentialEvolution() {
        System.out.println("Testing differential evolution...");
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
                System.out.println(problem.functionValue());
                //assertFalse(Math.abs(problem.functionValue() - minima.get(i)) > 1e-8);
            } else {
                // this case is unstable due to randomness; we're good as
                // long as the result is below 15
                System.out.println(problem.functionValue());
                //assertFalse(problem.functionValue() > 15);
            }
        }
    }
}
