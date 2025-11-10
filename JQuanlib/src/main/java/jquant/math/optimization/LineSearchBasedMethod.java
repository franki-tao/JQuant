package jquant.math.optimization;

import jquant.math.Array;
import jquant.math.ReferencePkg;

import static jquant.math.CommonUtil.DotProduct;
import static jquant.math.MathUtils.QL_EPSILON;

public abstract class LineSearchBasedMethod extends OptimizationMethod {
    //! line search
    protected LineSearch lineSearch_;

    //default ArmijoLineSearch
    public LineSearchBasedMethod(LineSearch lineSearch) {
        lineSearch_ = lineSearch;
        if (lineSearch == null) {
            lineSearch_ = new ArmijoLineSearch(1e-8, 0.05, 0.65);
        }
    }

    @Override
    public EndCriteria.Type minimize(Problem P, EndCriteria endCriteria) {
        // Initializations
        double ftol = endCriteria.functionEpsilon();
        int maxStationaryStateIterations_
                = endCriteria.maxStationaryStateIterations();
        EndCriteria.Type ecType = EndCriteria.Type.None;   // reset end criteria
        P.reset();                                      // reset problem
        Array x_ = P.currentValue();              // store the starting point
        int iterationNumber_ = 0;
        // dimension line search
        lineSearch_.setSearchDirection_(new Array(x_.size()));
        // lineSearch_.searchDirection() = Array(x_.size());
        boolean done = false;

        // function and squared norm of gradient values;
        double fnew, fold, gold2;
        double fdiff;
        // classical initial value for line-search step
        double t = 1.0;
        // Set gradient g at the size of the optimization problem
        // search direction
        int sz = lineSearch_.searchDirection().size();
        Array prevGradient = new Array(sz);
        Array d = new Array(sz);
        Array sddiff = new Array(sz);
        Array direction = new Array(sz);
        // Initialize cost function, gradient prevGradient and search direction
        P.setFunctionValue(P.valueAndGradient(prevGradient, x_));
        P.setGradientNormValue(DotProduct(prevGradient, prevGradient));
        lineSearch_.setSearchDirection_(prevGradient.mutiply(-1));
        // lineSearch_->searchDirection() = -prevGradient;

        boolean first_time = true;
        // Loop over iterations
        do {
            // Linesearch
            if (!first_time)
                prevGradient = lineSearch_.lastGradient();
            t = (lineSearch_).value(P, ecType, endCriteria, t);
            // don't throw: it can fail just because maxIterations exceeded
            //QL_REQUIRE(lineSearch_->succeed(), "line-search failed!");
            if (lineSearch_.succeed()) {
                // Updates

                // New point
                x_ = lineSearch_.lastX();
                // New function value
                fold = P.functionValue();
                P.setFunctionValue(lineSearch_.lastFunctionValue());
                // New gradient and search direction vectors

                // orthogonalization coef
                gold2 = P.gradientNormValue();
                P.setGradientNormValue(lineSearch_.lastGradientNorm2());

                // conjugate gradient search direction
                direction = getUpdatedDirection(P, gold2, prevGradient);

                sddiff = direction.subtract(lineSearch_.searchDirection());
                lineSearch_.setSearchDirection_(direction);
                // lineSearch_ -> searchDirection() = direction;
                // Now compute accuracy and check end criteria
                // Numerical Recipes exit strategy on fx (see NR in C++, p.423)
                fnew = P.functionValue();
                fdiff = 2.0 * Math.abs (fnew - fold) /
                        (Math.abs (fnew) + Math.abs (fold) + QL_EPSILON);
                if (fdiff < ftol || endCriteria.checkMaxIterations(iterationNumber_, ecType)) {
                    ReferencePkg<Integer> mssi = new ReferencePkg<>(maxStationaryStateIterations_);
                    endCriteria.checkStationaryFunctionValue(0.0, 0.0,
                            mssi, ecType);
                    maxStationaryStateIterations_ = mssi.getT();
                    endCriteria.checkMaxIterations(iterationNumber_, ecType);
                    return ecType;
                }
                P.setCurrentValue(x_);      // update problem current value
                ++iterationNumber_;         // Increase iteration number
                first_time = false;
            } else {
                done = true;
            }
        } while (!done);
        P.setCurrentValue(x_);
        return ecType;
    }
    //! computes the new search direction
    protected abstract Array getUpdatedDirection(final Problem P,
                                                 double gold2,
                                                 final Array gradient);

}
