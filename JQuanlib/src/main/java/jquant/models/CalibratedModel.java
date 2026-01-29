package jquant.models;

import jquant.math.Array;
import jquant.math.CommonUtil;
import jquant.math.optimization.*;
import jquant.math.optimization.impl.ConstraintImpl;
import jquant.patterns.Observable;
import jquant.patterns.Observer;

import java.util.ArrayList;
import java.util.List;

import static jquant.math.CommonUtil.QL_REQUIRE;

public abstract class CalibratedModel implements Observer, Observable {
    //! Constraint imposed on arguments
    private static class PrivateConstraint extends Constraint {
        static class Impl extends ConstraintImpl {
            private final List<Parameter> arguments_;

            public Impl(final List<Parameter> arguments) {
                arguments_ = arguments;
            }

            @Override
            public boolean test(final Array params) {
                int k = 0;
                for (Parameter argument : arguments_) {
                    int size = argument.size();
                    Array testParams = new Array(size);
                    for (int j = 0; j < size; j++, k++)
                        testParams.set(j, params.get(k));
                    if (!argument.testParams(testParams))
                        return false;
                }
                return true;
            }

            @Override
            public Array upperBound(final Array params) {
                int k = 0, k2 = 0;
                int totalSize = 0;
                for (Parameter argument : arguments_) {
                    totalSize += argument.size();
                }
                Array result = new Array(totalSize);
                for (Parameter argument : arguments_) {
                    int size = argument.size();
                    Array partialParams = new Array(size);
                    for (int j = 0; j < size; j++, k++)
                        partialParams.set(j, params.get(k));
                    Array tmpBound = argument.constraint().upperBound(partialParams);
                    for (int j = 0; j < size; j++, k2++)
                        result.set(k2, tmpBound.get(j));
                }
                return result;
            }

            @Override
            public Array lowerBound(final Array params) {
                int k = 0, k2 = 0;
                int totalSize = 0;
                for (Parameter argument : arguments_) {
                    totalSize += argument.size();
                }
                Array result = new Array(totalSize);
                for (Parameter argument : arguments_) {
                    int size = argument.size();
                    Array partialParams = new Array(size);
                    for (int j = 0; j < size; j++, k++)
                        partialParams.set(j, params.get(k));
                    Array tmpBound = argument.constraint().lowerBound(partialParams);
                    for (int j = 0; j < size; j++, k2++)
                        result.set(k2, tmpBound.get(j));
                }
                return result;
            }
        }

        public PrivateConstraint(final List<Parameter> arguments) {
            super(new Impl(arguments));
        }
    }

    //! Calibration cost function class
    private static class CalibrationFunction extends CostFunction {
        private CalibratedModel model_;
        private final List<CalibrationHelper> instruments_;
        private List<Double> weights_;
        private final Projection projection_;

        public CalibrationFunction(CalibratedModel model,
                                   final List<CalibrationHelper> h,
                                   List<Double> weights,
                                   final Projection projection) {
            model_ = model;
            instruments_ = h;
            weights_ = weights;
            projection_ = projection;
        }

        @Override
        public double value(final Array params) {
            model_.setParams(projection_.include(params));
            double value = 0.0;
            for (int i = 0; i < instruments_.size(); i++) {
                double diff = instruments_.get(i).calibrationError();
                value += diff * diff * weights_.get(i);
            }
            return Math.sqrt(value);
        }

        @Override
        public Array values(final Array params) {
            model_.setParams(projection_.include(params));
            Array values = new Array(instruments_.size());
            for (int i = 0; i < instruments_.size(); i++) {
                values.set(i, instruments_.get(i).calibrationError()
                        * Math.sqrt(weights_.get(i)));
            }
            return values;
        }

        @Override
        public double finiteDifferenceEpsilon() {
            return 1e-6;
        }
    }

    private static class CalibratedProjection implements Projection {
        public CalibratedProjection(final Array pv, List<Boolean> fp) {
            Projection(pv, fp);
        }
    }

    protected List<Parameter> arguments_;
    protected Constraint constraint_;
    protected EndCriteria.Type shortRateEndCriteria_; // None
    protected Array problemValues_;
    protected int functionEvaluation_;

    public CalibratedModel(int nArguments) {
        arguments_ = CommonUtil.ArrayInit(nArguments);
        constraint_ = new PrivateConstraint(arguments_);
    }

    @Override
    public void update() {
        generateArguments();
        notifyObservers();
    }

    //! Calibrate to a set of market instruments (usually caps/swaptions)
    /*! An additional constraint can be passed which must be
        satisfied in addition to the constraints of the model.
    */
    public void calibrate(
            final List<CalibrationHelper> instruments,
            OptimizationMethod method,
            final EndCriteria endCriteria,
            final Constraint additionalConstraint,
            final List<Double> weights,
            final List<Boolean> fixParameters) {

        QL_REQUIRE(!instruments.isEmpty(), "no instruments provided");

        Constraint c;
        if (additionalConstraint.empty())
            c = constraint_;
        else
            c = new CompositeConstraint(constraint_, additionalConstraint);

        QL_REQUIRE(weights.isEmpty() || weights.size() == instruments.size(),
                "mismatch between number of instruments (" +
                        instruments.size() + ") and weights (" +
                        weights.size() + ")");
        List<Double> w =
                weights.isEmpty() ? CommonUtil.ArrayInit(instruments.size(), 1.0) : weights;

        Array prms = params();
        QL_REQUIRE(fixParameters.isEmpty() || fixParameters.size() == prms.size(),
                "mismatch between number of parameters (" +
                        prms.size() + ") and fixed-parameter specs (" +
                        fixParameters.size() + ")");
        List<Boolean> all = CommonUtil.ArrayInit(prms.size(), false);
        Projection proj = new CalibratedProjection(prms, !fixParameters.isEmpty() ? fixParameters : all);
        CalibrationFunction f = new CalibrationFunction(this, instruments, w, proj);
        ProjectedConstraint pc = new ProjectedConstraint(c, proj);
        Problem prob = new Problem(f, pc, proj.project(prms));
        shortRateEndCriteria_ = method.minimize(prob, endCriteria);
        Array result = new Array(prob.currentValue());
        setParams(proj.include(result));
        problemValues_ = prob.values(result);
        functionEvaluation_ = prob.functionEvaluation();
        notifyObservers();
    }

    public double value(final Array params,
                        final List<CalibrationHelper> instruments) {
        List<Double> w = CommonUtil.ArrayInit(instruments.size(), 1.0);
        Projection p = new CalibratedProjection(params, new ArrayList<>());
        CalibrationFunction f = new CalibrationFunction(this, instruments, w, p);
        return f.value(params);
    }

    public EndCriteria.Type endCriteria() {
        return shortRateEndCriteria_;
    }

    //! Returns the problem values
    public final Array problemValues() {
        return problemValues_;
    }

    public final Constraint constraint() {
        return constraint_;
    }

    public Array params() {
        int size = 0;
        for (Parameter argument : arguments_)
            size += argument.size();
        Array params = new Array(size);
        for (int i = 0, k = 0; i < arguments_.size(); ++i) {
            for (int j = 0; j < arguments_.get(i).size(); ++j, ++k)
                params.set(k, arguments_.get(i).params().get(j));
        }
        return params;
    }

    public void setParams(final Array params) {
        int p = 0;
        for (Parameter argument : arguments_) {
            for (int j = 0; j < argument.size(); ++j, ++p) {
                QL_REQUIRE(p != params.size(), "parameter array too small");
                argument.setParam(j, params().get(p));
            }
        }
        QL_REQUIRE(p == params.size(), "parameter array too big!");
        generateArguments();
        notifyObservers();
    }

    public int functionEvaluation() {
        return functionEvaluation_;
    }

    protected abstract void generateArguments();
}
