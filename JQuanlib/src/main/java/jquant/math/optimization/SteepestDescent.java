package jquant.math.optimization;

import jquant.math.Array;

/**
 * ! Multi-dimensional steepest-descent class
 * ! User has to provide line-search method and optimization end criteria
 *   search direction \f$ = - f'(x) \f$
 */
public class SteepestDescent extends LineSearchBasedMethod {
    public SteepestDescent(LineSearch lineSearch) {
        super(lineSearch);
    }
    //! \name LineSearchBasedMethod interface
    @Override
    protected Array getUpdatedDirection(Problem P, double gold2, Array gradient) {
        return lineSearch_.lastGradient().mutiply(-1);
    }
}
