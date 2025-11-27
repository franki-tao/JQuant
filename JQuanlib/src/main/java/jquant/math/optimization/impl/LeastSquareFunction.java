package jquant.math.optimization.impl;

import jquant.math.Array;
import jquant.math.Matrix;
import jquant.math.optimization.CostFunction;

import static jquant.math.CommonUtil.DotProduct;
import static jquant.math.CommonUtil.transpose;

/**
 * ! Cost function for least-square problems
 * ! Implements a cost function using the interface provided by
 *   the LeastSquareProblem class.
 */
public class LeastSquareFunction extends CostFunction {
    //! least square problem
    protected LeastSquareProblem lsp_;

    /**
     * ! Default constructor
     * @param lsp lsp
     */
    public LeastSquareFunction(LeastSquareProblem lsp) {
        lsp_ = lsp;
    }
    //! compute value of the least square function
    @Override
    public double value(Array x) {
        // size of target and function to fit vectors
        Array target = new Array(lsp_.size());
        Array fct2fit = new Array(lsp_.size());
        // compute its values
        lsp_.targetAndValue(x, target, fct2fit);
        // do the difference
        Array diff = target.subtract(fct2fit);
        // and compute the scalar product (square of the norm)
        return DotProduct(diff, diff);
    }
    @Override
    public Array values(Array x) {
        // size of target and function to fit vectors
        Array target = new Array(lsp_.size());
        Array fct2fit = new Array(lsp_.size());
        // compute its values
        lsp_.targetAndValue(x, target, fct2fit);
        // do the difference
        Array diff = target.subtract(fct2fit);
        return diff.multiply(diff);
    }
    //! compute vector of derivatives of the least square function
    @Override
    public void gradient(Array grad_f, final Array x) {
        // size of target and function to fit vectors
        Array target = new Array(lsp_.size ());
        Array fct2fit = new Array(lsp_.size ());
        // size of gradient matrix
        Matrix grad_fct2fit = new Matrix(lsp_.size (), x.size ());
        // compute its values
        lsp_.targetValueAndGradient(x, grad_fct2fit, target, fct2fit);
        // do the difference
        Array diff = target.subtract(fct2fit);
        // compute derivative
        grad_f = transpose(grad_fct2fit).mutiply(diff).mutiply(-2.0);
    }
    //! compute value and gradient of the least square function
    @Override
    public double valueAndGradient(Array grad_f, final Array x) {
        // size of target and function to fit vectors
        Array target = new Array(lsp_.size());
        Array fct2fit = new Array(lsp_.size());
        // size of gradient matrix
        Matrix grad_fct2fit = new Matrix(lsp_.size(), x.size());
        // compute its values
        lsp_.targetValueAndGradient(x, grad_fct2fit, target, fct2fit);
        // do the difference
        Array diff = target.subtract(fct2fit);
        // compute derivative
        grad_f = transpose(grad_fct2fit).mutiply(diff).mutiply(-2.0);
        // and compute the scalar product (square of the norm)
        return DotProduct(diff, diff);
    }
}
