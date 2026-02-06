package jquant.math.optimization;

import jquant.math.Array;
import jquant.math.ReferencePkg;

import static jquant.math.CommonUtil.DotProduct;

//! Armijo line search.
/*! Let \f$ \alpha \f$ and \f$ \beta \f$ be 2 scalars in \f$ [0,1]
   \f$.  Let \f$ x \f$ be the current value of the unknown, \f$ d
   \f$ the search direction and \f$ t \f$ the step. Let \f$ f \f$
   be the function to minimize.  The line search stops when \f$ t
   \f$ verifies
   \f[ f(x + t \cdot d) - f(x) \leq -\alpha t f'(x+t \cdot d) \f]
   and
   \f[ f(x+\frac{t}{\beta} \cdot d) - f(x) > -\frac{\alpha}{\beta}
       t f'(x+t \cdot d) \f]

   (see Polak, Algorithms and consistent approximations, Optimization,
   volume 124 of Applied Mathematical Sciences, Springer-Verlag, NY,
   1997)
*/
public class ArmijoLineSearch extends LineSearch {
    private double alpha_, beta_;
    //! Default constructor

    /**
     * Real eps = 1e-8,
     * Real alpha = 0.05,
     * Real beta = 0.65
     */
    public ArmijoLineSearch(double eps, double alpha, double beta) {
        super(eps);
        alpha_ = alpha;
        beta_ = beta;
    }

    //! Perform line search
    @Override
    public double value(Problem P, ReferencePkg<EndCriteria.Type> ecType, EndCriteria endCriteria, double t_ini) {
        //OptimizationMethod& method = P.method();
        Constraint constraint = P.constraint();
        succeed_ = true;
        boolean maxIter = false;
        double qtold, t = t_ini;
        int loopNumber = 0;

        double q0 = P.functionValue();
        double qp0 = P.gradientNormValue();

        qt_ = q0;
        qpt_ = (gradient_.empty()) ? qp0 : -DotProduct(gradient_, searchDirection_);

        // Initialize gradient
        gradient_ = new Array(P.currentValue().size());
        // Compute new point
        xtd_ = P.currentValue();
        t = update(xtd_, searchDirection_, t, constraint);
        // Compute function value at the new point
        qt_ = P.value(xtd_);

        // Enter in the loop if the criterion is not satisfied
        if ((qt_ - q0) > -alpha_ * t * qpt_) {
            do {
                loopNumber++;
                // Decrease step
                t *= beta_;
                // Store old value of the function
                qtold = qt_;
                // New point value
                xtd_ = P.currentValue();
                t = update(xtd_, searchDirection_, t, constraint);

                // Compute function value at the new point
                qt_ = P.value(xtd_);
                P.gradient(gradient_, xtd_);
                // and it squared norm
                maxIter = endCriteria.checkMaxIterations(loopNumber, ecType);
            } while (
                    (((qt_ - q0) > (-alpha_ * t * qpt_)) ||
                            ((qtold - q0) <= (-alpha_ * t * qpt_ / beta_))) &&
                            (!maxIter));
        }

        if (maxIter)
            succeed_ = false;

        // Compute new gradient
        P.gradient(gradient_, xtd_);
        // and it squared norm
        qpt_ = DotProduct(gradient_, gradient_);

        // Return new step value
        return t;
    }
}
