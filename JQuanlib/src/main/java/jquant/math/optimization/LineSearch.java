package jquant.math.optimization;

import jquant.math.Array;
import jquant.math.ReferencePkg;

import static jquant.math.CommonUtil.QL_FAIL;

//! Base class for line search
// 线搜索基类
public abstract class LineSearch {
    //! current values of the search direction
    protected Array searchDirection_;
    //! new x and its gradient
    protected Array xtd_, gradient_;
    //! cost function value and gradient norm corresponding to xtd_
    protected double qt_ = 0d, qpt_ = 0d;
    //! flag to know if linesearch succeed
    protected boolean succeed_ = true;

    //! Default constructor x=0
    public LineSearch(double x) {
        gradient_ = new Array(0);
        xtd_ = new Array(0);
        searchDirection_ = new Array(0);
    }

    public LineSearch() {
    }

    //! return last x value
    public final Array lastX() {
        return xtd_;
    }

    //! return last cost function value
    public double lastFunctionValue() {
        return qt_;
    }

    //! return last gradient
    public final Array lastGradient() {
        return gradient_;
    }

    //! return square norm of last gradient
    public double lastGradientNorm2() {
        return qpt_;
    }

    public boolean succeed() {
        return succeed_;
    }

    public abstract double value(Problem P, // Optimization problem
                                 ReferencePkg<EndCriteria.Type> ecType,
                                 EndCriteria endCriteria,
                                 double t_ini);

    public double update(Array params,
                         final Array direction,
                         double beta,
                         final Constraint constraint) {
        double diff=beta;
        Array newParams = params.add(direction.mutiply(diff)); // + diff*direction;
        boolean valid = constraint.test(newParams);
        int icount = 0;
        while (!valid) {
            if (icount > 200)
                QL_FAIL("can't update linesearch");
            diff *= 0.5;
            icount ++;
            newParams = params.add(direction.mutiply(diff)); // + diff*direction;
            valid = constraint.test(newParams);
        }
        Array finalParams = params.add(direction.mutiply(diff));
        for (int i = 0; i < params.size(); i++) {
            params.set(i, finalParams.get(i));
        }
        // params += diff*direction;
        return diff;
    }

    //! current value of the search direction
    public Array searchDirection() {
        return searchDirection_;
    }

    public void setSearchDirection_(Array searchDirection_) {
        this.searchDirection_ = searchDirection_;
    }
}
