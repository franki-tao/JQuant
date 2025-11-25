package jquant.math.optimization;

import jquant.math.Array;
import jquant.math.Matrix;

//! Broyden-Fletcher-Goldfarb-Shanno algorithm
/*! See <http://en.wikipedia.org/wiki/BFGS_method>.

    Adapted from Numerical Recipes in C, 2nd edition.

    User has to provide line-search method and optimization end criteria.
*/
public class BFGS extends LineSearchBasedMethod {
    //@}
    //! inverse of hessian matrix
    private Matrix inverseHessian_;

    public BFGS(LineSearch lineSearch) {
        super(lineSearch);
        inverseHessian_ = new Matrix(0,0);
    }

    //! \name LineSearchBasedMethod interface
    //@{
    @Override
    protected Array getUpdatedDirection(Problem P, double gold2, Array oldGradient) {
        if (inverseHessian_.matrix == null)
        {
            // first time in this update, we create needed structures
            inverseHessian_ = new Matrix(P.currentValue().size(),
                    P.currentValue().size(), 0.);
            for (int i = 0; i < P.currentValue().size(); ++i)
                inverseHessian_.set(i,i,1.);
        }

        Array diffGradient = new Array(0);
        Array diffGradientWithHessianApplied = new Array(P.currentValue().size(), 0.);

        diffGradient = lineSearch_.lastGradient().subtract(oldGradient);
        for (int i = 0; i < P.currentValue().size(); ++i)
            for (int j = 0; j < P.currentValue().size(); ++j)
                diffGradientWithHessianApplied.addEq(i, inverseHessian_.get(i,j) * diffGradient.get(j));

        double fac, fae, fad;
        double sumdg, sumxi;

        fac = fae = sumdg = sumxi = 0.;
        for (int i = 0; i < P.currentValue().size(); ++i)
        {
            fac += diffGradient.get(i) * lineSearch_.searchDirection().get(i);
            fae += diffGradient.get(i) * diffGradientWithHessianApplied.get(i);
            sumdg += Math.pow(diffGradient.get(i), 2.);
            sumxi += Math.pow(lineSearch_.searchDirection().get(i), 2.);
        }

        if (fac > Math.sqrt(1e-8 * sumdg * sumxi))  // skip update if fac not sufficiently positive
        {
            fac = 1.0 / fac;
            fad = 1.0 / fae;

            for (int i = 0; i < P.currentValue().size(); ++i)
                diffGradient.set(i, fac * lineSearch_.searchDirection().get(i) - fad * diffGradientWithHessianApplied.get(i));

            for (int i = 0; i < P.currentValue().size(); ++i)
                for (int j = 0; j < P.currentValue().size(); ++j)
                {
                    inverseHessian_.addEq(i,j,fac * lineSearch_.searchDirection().get(i) * lineSearch_.searchDirection().get(j));
                    inverseHessian_.substractEq(i,j,fad * diffGradientWithHessianApplied.get(i) * diffGradientWithHessianApplied.get(j));
                    inverseHessian_.addEq(i,j, fae * diffGradient.get(i) * diffGradient.get(j));
                }
        }
        //else
        //  throw "BFGS: FAC not sufficiently positive";


        Array direction = new Array(P.currentValue().size());
        for (int i = 0; i < P.currentValue().size(); ++i)
        {
            direction.set(i, 0.0);
            for (int j = 0; j < P.currentValue().size(); ++j)
                direction.subtractEq(i, inverseHessian_.get(i,j) * lineSearch_.lastGradient().get(j));
        }

        return direction;
    }
}
