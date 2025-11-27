package jquant.math.optimization;

import jquant.math.Array;

import static jquant.math.CommonUtil.DotProduct;
import static jquant.math.MathUtils.close_enough;

public class GoldsteinLineSearch extends LineSearch {
    private double alpha_, beta_;
    private double extrapolation_;

    /**
     * ! Default constructor
     *
     * @param eps           eps = 1e-8
     * @param alpha         alpha = 0.05
     * @param beta          beta = 0.65
     * @param extrapolation extrapolation = 1.5
     */
    public GoldsteinLineSearch(double eps,
                               double alpha,
                               double beta,
                               double extrapolation) {
        super(eps);
        alpha_ = alpha;
        beta_ = beta;
        extrapolation_ = extrapolation;
    }

    //! Perform line search
    @Override
    public double value(Problem P, EndCriteria.Type ecType, EndCriteria endCriteria, double t_ini) {
        Constraint constraint = P.constraint();
        succeed_ = true;
        boolean maxIter = false;
        double t = t_ini;
        int loopNumber = 0;

        double q0 = P.functionValue();
        double qp0 = P.gradientNormValue();

        double tl = 0.0;
        double tr = 0.0;

        qt_ = q0;
        qpt_ = (gradient_.empty()) ? qp0 : -DotProduct(gradient_, searchDirection_);

        // Initialize gradient
        gradient_ = new Array(P.currentValue().size());
        // Compute new point
        xtd_ = P.currentValue();
        t = update(xtd_, searchDirection_, t, constraint);
        // Compute function value at the new point
        qt_ = P.value(xtd_);

        while ((qt_ - q0) < -beta_ * t * qpt_ || (qt_ - q0) > -alpha_ * t * qpt_) {
            if ((qt_ - q0) > -alpha_ * t * qpt_)
                tr = t;
            else
                tl = t;
            ++loopNumber;

            // calculate the new step
            if (close_enough(tr, 0.0))
                t *= extrapolation_;
            else
                t = (tl + tr) / 2.0;

            // New point value
            xtd_ = P.currentValue();
            t = update(xtd_, searchDirection_, t, constraint);

            // Compute function value at the new point
            qt_ = P.value(xtd_);
            P.gradient(gradient_, xtd_);
            // and it squared norm
            maxIter = endCriteria.checkMaxIterations(loopNumber, ecType);

            if (maxIter)
                break;
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
