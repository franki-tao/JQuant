package jquant.termstructures.volatility;

import jquant.math.AbcdMathFunction;
import jquant.math.Array;
import jquant.math.CommonUtil;
import jquant.math.distributions.CumulativeNormalDistribution;
import jquant.math.optimization.*;

import java.util.List;

import static jquant.math.CommonUtil.QL_REQUIRE;
import static jquant.math.MathUtils.QL_MIN_REAL;
import static jquant.math.MathUtils.abcdBlackVolatility;
import static jquant.math.optimization.EndCriteria.Type.None;

public class AbcdCalibration {
    public boolean aIsFixed_;
    public boolean bIsFixed_;
    public boolean cIsFixed_;
    public boolean dIsFixed_;

    public double a_, b_, c_, d_;

    public ParametersTransformation transformation_;

    private EndCriteria.Type abcdEndCriteria_;

    private EndCriteria endCriteria_;

    private OptimizationMethod optMethod_;

    private List<Double> weights_;

    private boolean vegaWeighted_;

    private List<Double> times_;
    private List<Double> blackVols_;

    public AbcdCalibration() {
    }

    public AbcdCalibration(
            final List<Double> t,
            final List<Double> blackVols,
            double aGuess, //default -0.06,
            double bGuess, //  0.17,
            double cGuess, //  0.54,
            double dGuess, //  0.17,
            boolean aIsFixed, //  false,
            boolean bIsFixed, //  false,
            boolean cIsFixed, //  false,
            boolean dIsFixed, //  false,
            boolean vegaWeighted, // false,
            EndCriteria endCriteria, // 默认构造函数,下同
            OptimizationMethod method) {
        aIsFixed_ = aIsFixed;
        bIsFixed_ = bIsFixed;
        cIsFixed_ = cIsFixed;
        dIsFixed_ = dIsFixed;
        a_ = aGuess;
        b_ = bGuess;
        c_ = cGuess;
        d_ = dGuess;
        abcdEndCriteria_ = None;
        endCriteria_ = endCriteria;
        optMethod_ = method;
        weights_ = CommonUtil.ArrayInit(blackVols.size(), 1.0 / blackVols.size());
        vegaWeighted_ = vegaWeighted;
        times_ = t;
        blackVols_ = blackVols;
        AbcdMathFunction.validate(aGuess, bGuess, cGuess, dGuess);

        QL_REQUIRE(blackVols.size() == t.size(),
                "mismatch between number of times (" + t.size() +
                        ") and blackVols (" + blackVols.size() + ")");

        // if no optimization method or endCriteria is provided, we provide one
        if (optMethod_ == null) {
            double epsfcn = 1.0e-8;
            double xtol = 1.0e-8;
            double gtol = 1.0e-8;
            boolean useCostFunctionsJacobian = false;
            optMethod_ = new LevenbergMarquardt(epsfcn, xtol, gtol, useCostFunctionsJacobian);
        }
        if (endCriteria_ == null) {
            int maxIterations = 10000;
            int maxStationaryStateIterations = 1000;
            double rootEpsilon = 1.0e-8;
            double functionEpsilon = 0.3e-4;     // Why 0.3e-4 ?
            double gradientNormEpsilon = 0.3e-4; // Why 0.3e-4 ?
            endCriteria_ = new EndCriteria(maxIterations, maxStationaryStateIterations,
                    rootEpsilon, functionEpsilon, gradientNormEpsilon);
        }
    }

    public List<Double> k(List<Double> t, List<Double> blackVols) {
        QL_REQUIRE(blackVols.size() == t.size(),
                "mismatch between number of times (" + t.size() +
                        ") and blackVols (" + blackVols.size() + ")");
        List<Double> k = CommonUtil.ArrayInit(t.size());
        for (int i = 0; i < t.size(); i++) {
            k.set(i, blackVols.get(i) / value(t.get(i)));
        }
        return k;
    }

    public void compute() {
        if (vegaWeighted_) {
            double weightsSum = 0.0;
            for (int i = 0; i < times_.size(); i++) {
                double stdDev = Math.sqrt(blackVols_.get(i) * blackVols_.get(i) * times_.get(i));
                // when strike==forward, the blackFormulaStdDevDerivative becomes
                weights_.set(i, new CumulativeNormalDistribution().derivative(0.5 * stdDev));
                weightsSum += weights_.get(i);
            }
            // weight normalization
            for (int i = 0; i < times_.size(); i++) {
                weights_.set(i, weights_.get(i) / weightsSum);
            }
        }

        // there is nothing to optimize
        if (aIsFixed_ && bIsFixed_ && cIsFixed_ && dIsFixed_) {
            abcdEndCriteria_ = None;
            //error_ = interpolationError();
            //maxError_ = interpolationMaxError();
            return;
        } else {
            AbcdError costFunction = new AbcdError(this);
            transformation_ = new AbcdParametersTransformation();

            Array guess = new Array(4);
            guess.set(0, a_);
            guess.set(1, b_);
            guess.set(2, c_);
            guess.set(3, d_);

            List<Boolean> parameterAreFixed = CommonUtil.ArrayInit(4);
            parameterAreFixed.set(0, aIsFixed_);
            parameterAreFixed.set(1, bIsFixed_);
            parameterAreFixed.set(2, cIsFixed_);
            parameterAreFixed.set(3, dIsFixed_);

            Array inversedTransformatedGuess = new Array(transformation_.inverse(guess));

            ProjectedCostFunction projectedAbcdCostFunction = new ProjectedCostFunction(costFunction,
                    inversedTransformatedGuess, parameterAreFixed);

            Array projectedGuess =
                    new Array(projectedAbcdCostFunction.project(inversedTransformatedGuess));

            NoConstraint constraint = new NoConstraint();
            Problem problem = new Problem(projectedAbcdCostFunction, constraint, projectedGuess);
            abcdEndCriteria_ = optMethod_.minimize(problem, endCriteria_);
            Array projectedResult = new Array(problem.currentValue());
            Array transfResult = new Array(projectedAbcdCostFunction.include(projectedResult));

            Array result = transformation_.direct(transfResult);
            AbcdMathFunction.validate(a_, b_, c_, d_);
            a_ = result.get(0);
            b_ = result.get(1);
            c_ = result.get(2);
            d_ = result.get(3);

        }
    }

    public double value(double x) {
        return abcdBlackVolatility(x, a_, b_, c_, d_);
    }

    public final double error() {
        int n = times_.size();
        double error, squaredError = 0.0;
        for (int i = 0; i < times_.size(); i++) {
            error = (value(times_.get(i)) - blackVols_.get(i));
            squaredError += error * error * weights_.get(i);
        }
        return Math.sqrt(n * squaredError / (n - 1));
    }

    public final double maxError() {
        double error, maxError = QL_MIN_REAL;
        for (int i = 0; i < times_.size(); i++) {
            error = Math.abs(value(times_.get(i)) - blackVols_.get(i));
            maxError = Math.max(maxError, error);
        }
        return maxError;
    }

    public final Array errors() {
        Array results = new Array(times_.size());
        for (int i = 0; i < times_.size(); i++) {
            results.set(i, (value(times_.get(i)) - blackVols_.get(i)) * Math.sqrt(weights_.get(i)));
        }
        return results;
    }

    public final EndCriteria.Type endCriteria() {
        return abcdEndCriteria_;
    }

    public final double a() {
        return a_;
    }

    public final double b() {
        return b_;
    }

    public final double c() {
        return c_;
    }

    public final double d() {
        return d_;
    }
}
