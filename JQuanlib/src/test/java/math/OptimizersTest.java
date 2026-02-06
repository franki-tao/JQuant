package math;

import jquant.math.Array;
import jquant.math.optimization.*;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static jquant.math.CommonUtil.QL_FAIL;
import static jquant.math.CommonUtil.QL_REQUIRE;
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

    private static List<CostFunction> costFunctions_ = new ArrayList<>();
    private static List<Constraint> constraints_ = new ArrayList<>();
    private static List<Array> initialValues_ = new ArrayList<>();
    private static List<Integer> maxIterations_ = new ArrayList<>(), maxStationaryStateIterations_=new ArrayList<>();
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
        endCriterias_.add(new EndCriteria (
                        maxIterations_.get(maxIterations_.size()-1), maxStationaryStateIterations_.get(maxStationaryStateIterations_.size()-1),
                rootEpsilons_.get(rootEpsilons_.size()-1), functionEpsilons_.get(functionEpsilons_.size()-1),
                gradientNormEpsilons_.get(gradientNormEpsilons_.size()-1)));
        // Set optimization methods for optimizer
        List< OptimizationMethodType> optimizationMethodTypes = Arrays.asList(
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
        for (int i=0; i<costFunctions_.size(); ++i) {
            Problem problem = new Problem(costFunctions_.get(i), constraints_.get(i),
                    initialValues_.get(i));
            Array initialValues = problem.currentValue();
            // Loop over optimizers
            for (int j=0; j<(optimizationMethods_.get(i)).size(); ++j) {
                double rootEpsilon = endCriterias_.get(i).rootEpsilon();
                int endCriteriaTests = 1;
                // Loop over rootEpsilon
                for (int k=0; k<endCriteriaTests; ++k) {
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

                    double xError = maxDifference(xMinCalculated,xMinExpected_.get(i));
                    double yError = maxDifference(yMinCalculated,yMinExpected_.get(i));
                    boolean correct = (xError <= endCriteria.rootEpsilon() ||
                            yError <= endCriteria.functionEpsilon());
                    System.out.println((!completed) || (!correct));
                    System.out.println(xError+", "+yError);
                    assertFalse((!completed) || (!correct));
                }
            }
        }
    }
}
