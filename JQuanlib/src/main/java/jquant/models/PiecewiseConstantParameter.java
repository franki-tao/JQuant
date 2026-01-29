package jquant.models;

import jquant.math.Array;
import jquant.math.CommonUtil;
import jquant.math.optimization.Constraint;

import java.util.List;

//! Piecewise-constant parameter
/*! \f$ a(t) = a_i if t_{i-1} \geq t < t_i \f$.
    This kind of parameter is usually used to enhance the fitting of a
    model
*/
public class PiecewiseConstantParameter extends Parameter {
    private static class Impl extends Parameter.Impl {
        private List<Double> times_;

        public Impl(List<Double> times) {
            times_ = times;
        }

        @Override
        public double value(Array params, double t) {
            int i = CommonUtil.upper_bound(times_, t);
            return params.get(i);
        }
    }

    // constraint = NoConstraint()
    public PiecewiseConstantParameter(final List<Double> times, final Constraint constraint) {
        super(times.size() + 1, new PiecewiseConstantParameter.Impl(times), constraint);
    }
}
