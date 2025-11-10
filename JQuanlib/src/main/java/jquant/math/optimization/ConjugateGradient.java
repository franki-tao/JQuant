package jquant.math.optimization;

import jquant.math.Array;

//! Multi-dimensional Conjugate Gradient class.
/*! Fletcher-Reeves-Polak-Ribiere algorithm
    adapted from Numerical Recipes in C, 2nd edition.

    User has to provide line-search method and optimization end criteria.
    Search direction \f$ d_i = - f'(x_i) + c_i*d_{i-1} \f$
    where \f$ c_i = ||f'(x_i)||^2/||f'(x_{i-1})||^2 \f$
    and \f$ d_1 = - f'(x_1) \f$

    This optimization method requires the knowledge of
    the gradient of the cost function.

    \ingroup optimizers
*/
public class ConjugateGradient extends LineSearchBasedMethod {
    public ConjugateGradient(LineSearch lineSearch) {
        super(lineSearch);
    }

    @Override
    protected Array getUpdatedDirection(Problem P, double gold2, Array gradient) {
        return lineSearch_.lastGradient().mutiply(-1).add(
                lineSearch_.searchDirection().mutiply((P.gradientNormValue() / gold2)));
    }
}
