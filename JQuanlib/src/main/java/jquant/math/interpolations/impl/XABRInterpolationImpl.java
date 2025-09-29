package jquant.math.interpolations.impl;

import jquant.math.CommonUtil;
import jquant.math.optimization.EndCriteria;
import jquant.math.optimization.LevenbergMarquardt;
import jquant.math.optimization.NoConstraint;
import jquant.math.optimization.OptimizationMethod;
import jquant.math.templateImpl;
import jquant.termstructures.volatility.Sarb;

import java.util.List;

import static jquant.math.CommonUtil.QL_REQUIRE;
import static jquant.math.MathUtils.NULL_REAL;

public class XABRInterpolationImpl extends templateImpl {

    /*--XABRCoeffHolder params--*/
    /*! Expiry, Forward */
    public double t_;
    public double forward_;
    /*! Parameters */
    public List<Double> params_;
    public List<Boolean> paramIsFixed_;
    public List<Double> weights_;
    /*! Interpolation results */
    public double error_, maxError_;
    public EndCriteria.Type XABREndCriteria_;
    /*! Model instance (if required) */
    public SABRWrapper modelInstance_;

    public SarbModel modelTmp;

    /*! additional parameters */
    public List<Double> addParams_;

    private EndCriteria endCriteria_;
    private OptimizationMethod optMethod_;
    private double errorAccept_;
    private boolean useMaxError_;
    private int maxGuesses_;
    private boolean vegaWeighted_;
    private NoConstraint constraint_;
    private Sarb.VolatilityType volatilityType_;

    //volatilityType = VolatilityType::ShiftedLognormal
    public XABRInterpolationImpl(double[] x, double[] y,
                                 double t,
                                 double forward,
                                 List<Double> params,
                                 List<Boolean> paramIsFixed,
                                 boolean vegaWeighted,
                                 EndCriteria endCriteria,
                                 OptimizationMethod optMethod,
                                 double errorAccept,
                                 boolean useMaxError,
                                 int maxGuesses,
                                 List<Double> addParams,
                                 Sarb.VolatilityType volatilityType,
                                 SarbModel model) {
        super(x, y, 1);
        modelTmp = model;
        t_ = t;
        forward_ = forward;
        params_ = params;
        paramIsFixed_ = CommonUtil.ArrayInit(paramIsFixed.size(), false);
        error_ = NULL_REAL;
        maxError_ = NULL_REAL;
        addParams_ = addParams;
        QL_REQUIRE(t > 0.0, "expiry time must be positive: " + t
                + " not allowed");
        QL_REQUIRE(params.size() == model.dimension(),
                "wrong number of parameters (" + params.size()
                        + "), should be "
                        + model.dimension());
        QL_REQUIRE(paramIsFixed.size() == model.dimension(),
                "wrong number of fixed parameters flags ("
                        + paramIsFixed.size() + "), should be "
                        + model.dimension());
        for (int i = 0; i < params.size(); ++i) {
            if (params.get(i) != NULL_REAL) {
                paramIsFixed_.set(i, paramIsFixed.get(i));
            }
        }
        model.defaultValues(params_, paramIsFixed_, forward_, t_, addParams_);
        updateModelInstance();
        endCriteria_ = endCriteria;
        optMethod_ = optMethod;
        errorAccept_ = errorAccept;
        useMaxError_ = useMaxError;
        maxGuesses_ = maxGuesses;
        vegaWeighted_ = vegaWeighted;
        volatilityType_ = volatilityType;
        // if no optimization method or endCriteria is provided, we provide one
        if (optMethod_ == null)
            optMethod_ = new LevenbergMarquardt(1e-8, 1e-8, 1e-8, false);
        // optMethod_ = ext::shared_ptr<OptimizationMethod>(new
        //    Simplex(0.01));
        if (endCriteria_ == null) {
            endCriteria_ = new EndCriteria(60000, 100, 1e-8, 1e-8, 1e-8);
        }
        this.weights_ = CommonUtil.ArrayInit(x.length, 1d / x.length);

    }

    public void updateModelInstance() {
        modelInstance_ = modelTmp.instance(t_, forward_, params_, addParams_);
    }

    @Override
    public void update() {

    }

    @Override
    public double value(double v) {
        return 0;
    }

    @Override
    public double primitive(double v) {
        return 0;
    }

    @Override
    public double derivative(double v) {
        return 0;
    }

    @Override
    public double secondDerivative(double v) {
        return 0;
    }
}
