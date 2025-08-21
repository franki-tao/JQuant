package math.optimization;

import math.Array;

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
}
