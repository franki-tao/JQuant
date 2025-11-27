package jquant.math.optimization.impl;

import jquant.math.Array;
import jquant.math.Matrix;

/**
 * ! Base class for least square problem
 */
public abstract class LeastSquareProblem {
    /**
     * ! size of the problem ie size of target vector
     * @return int
     */
    public abstract int size();

    /**
     * ! compute the target vector and the values of the function to fit
     * @param x x
     * @param target target
     * @param fct2fit fct2fit
     */
    public abstract void targetAndValue(final Array x,
                                        Array target,
                                        Array fct2fit);

    /**
     *! compute the target vector, the values of the function to fit
     *  and the matrix of derivatives
     */
    public abstract void targetValueAndGradient(final Array x,
                                       Matrix grad_fct2fit,
                                       Array target,
                                       Array fct2fit);
}
