package jquant.math.optimization;

import jquant.math.Array;

import static jquant.math.CommonUtil.QL_REQUIRE;
import static jquant.math.MathUtils.NULL_REAL;

//! Constrained optimization problem
    /*! \warning The passed CostFunction and Constraint instances are
                 stored by reference.  The user of this class must
                 make sure that they are not destroyed before the
                 Problem instance.
    */
public class Problem {
    //! Unconstrained cost function
    protected CostFunction costFunction_;
    //! Constraint
    protected Constraint constraint_;
    //! current value of the local minimum
    protected Array currentValue_;
    //! function and gradient norm values at the currentValue_ (i.e. the last step)
    protected double functionValue_, squaredNorm_;
    //! number of evaluation of cost function and its gradient
    protected int functionEvaluation_, gradientEvaluation_;

    //! default constructor
    public Problem(CostFunction costFunction, Constraint constraint, Array initialValue) {
        this.costFunction_ = costFunction;
        this.constraint_ = constraint;
        this.currentValue_ = initialValue;
        QL_REQUIRE(!constraint.empty(), "empty constraint given");
    }

    /*! \warning it does not reset the current minumum to any initial value
     */
    public void reset() {
        functionEvaluation_ = gradientEvaluation_ = 0;
        functionValue_ = squaredNorm_ = NULL_REAL;
    }

    //! call cost function computation and increment evaluation counter
    public double value(final Array x) {
        ++functionEvaluation_;
        return costFunction_.value(x);
    }

    //! call cost values computation and increment evaluation counter
    public Array values(final Array x) {
        ++functionEvaluation_;
        return costFunction_.values(x);
    }

    //! call cost function gradient computation and increment
    //  evaluation counter
    public void gradient(Array grad_f, Array x) {
        ++gradientEvaluation_;
        costFunction_.gradient(grad_f, x);
    }

    //! call cost function computation and it gradient
    public double valueAndGradient(Array grad_f, Array x) {
        ++functionEvaluation_;
        ++gradientEvaluation_;
        return costFunction_.valueAndGradient(grad_f, x);
    }

    //! Constraint
    public Constraint constraint() {
        return constraint_;
    }

    //! Cost function
    public CostFunction costFunction() {
        return costFunction_;
    }

    public void setCurrentValue(Array currentValue) {
        currentValue_ = currentValue;
    }

    //! current value of the local minimum
    public final Array currentValue() {
        return new Array(currentValue_.toArray());
    }

    public void setFunctionValue(double functionValue) {
        functionValue_ = functionValue;
    }

    //! value of cost function
    public double functionValue() {
        return functionValue_;
    }

    public void setGradientNormValue(double squaredNorm) {
        squaredNorm_ = squaredNorm;
    }

    //! value of cost function gradient norm
    public double gradientNormValue() {
        return squaredNorm_;
    }

    //! number of evaluation of cost function
    public int functionEvaluation() {
        return functionEvaluation_;
    }

    //! number of evaluation of cost function gradient
    public int gradientEvaluation() {
        return gradientEvaluation_;
    }
}
