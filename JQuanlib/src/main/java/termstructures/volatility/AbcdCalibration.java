package termstructures.volatility;

import math.AbcdMathFunction;
import math.CommonUtil;
import math.optimization.EndCriteria;
import math.optimization.LevenbergMarquardt;
import math.optimization.OptimizationMethod;
import math.optimization.ParametersTransformation;

import java.util.List;

import static math.CommonUtil.QL_REQUIRE;

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
        abcdEndCriteria_ = EndCriteria.Type.None;
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
        if (endCriteria_==null) {
            int maxIterations = 10000;
            int maxStationaryStateIterations = 1000;
            double rootEpsilon = 1.0e-8;
            double functionEpsilon = 0.3e-4;     // Why 0.3e-4 ?
            double gradientNormEpsilon = 0.3e-4; // Why 0.3e-4 ?
            endCriteria_ = new EndCriteria (maxIterations, maxStationaryStateIterations,
                    rootEpsilon, functionEpsilon, gradientNormEpsilon);
        }
    }
}
