package jquant.math.interpolations.impl;

import jquant.math.Array;
import jquant.math.CommonUtil;
import jquant.math.optimization.*;
import jquant.math.randomnumbers.HaltonRsg;
import jquant.math.templateImpl;
import jquant.methods.montecarlo.SampleVector;
import jquant.termstructures.volatility.Sarb;

import java.util.Arrays;
import java.util.List;

import static jquant.math.CommonUtil.QL_FAIL;
import static jquant.math.CommonUtil.QL_REQUIRE;
import static jquant.math.MathUtils.*;

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
        updateModelInstance();

        // we should also check that y contains positive values only

        // we must update weights if it is vegaWeighted
        if (vegaWeighted_) {
            double[] x = super.xValue;
            double[] y = super.yValue;
            // std::vector<Real>::iterator w = weights_.begin();
            this.weights_.clear();
            double weightsSum = 0.0;
            for (int i = 0; i < x.length; i++) {
                double stdDev = Math.sqrt(y[i] * y[i] * t_);
                weights_.add(modelTmp.weight(x[i], forward_, stdDev, addParams_));
                weightsSum += weights_.get(i);
            }
            // weight normalization
            for (int i = 0; i < weights_.size(); i++) {
                weights_.set(i, weights_.get(i) / weightsSum);
            }
        }

        // there is nothing to optimize
        if (temp1()) {
            this.error_ = interpolationError();
            this.maxError_ = interpolationMaxError();
            this.XABREndCriteria_ = EndCriteria.Type.None;
            return;
        } else {
            XABRError costFunction = new XABRError(this);

            Array guess = new Array(modelTmp.dimension());
            for (int i = 0; i < guess.size(); ++i)
                guess.set(i, this.params_.get(i));

            int iterations = 0;
            int freeParameters = 0;
            double bestError = QL_MAX_REAL;
            Array bestParameters = new Array(0);
            for (int i = 0; i < modelTmp.dimension(); ++i)
                if (!this.paramIsFixed_.get(i))
                    ++freeParameters;
            HaltonRsg halton = new HaltonRsg(freeParameters, 42, true, false);
            EndCriteria.Type tmpEndCriteria;
            double tmpInterpolationError;

            do {

                if (iterations > 0) {
                    final SampleVector s = halton.nextSequence();
                    modelTmp.guess(guess, this.paramIsFixed_, this.forward_, this.t_, s.value, this.addParams_);
                    for (int i = 0; i < this.paramIsFixed_.size(); ++i)
                        if (this.paramIsFixed_.get(i))
                            guess.set(i, this.params_.get(i));
                }

                Array inversedTransformatedGuess = new Array(modelTmp.inverse(guess, this.paramIsFixed_, this.params_, this.forward_));

                ProjectedCostFunction constrainedXABRError = new ProjectedCostFunction(
                        costFunction, inversedTransformatedGuess,
                        this.paramIsFixed_);

                Array projectedGuess = new Array(constrainedXABRError.project(inversedTransformatedGuess));

                NoConstraint constraint = new NoConstraint();
                Problem problem = new Problem(constrainedXABRError, constraint,
                        projectedGuess);
                tmpEndCriteria = optMethod_.minimize(problem, endCriteria_);
                Array projectedResult = new Array(problem.currentValue());
                Array transfResult = new Array(constrainedXABRError.include(projectedResult));

                Array result = modelTmp.direct(transfResult, this.paramIsFixed_, this.params_, this.forward_);
                tmpInterpolationError = useMaxError_ ? interpolationMaxError()
                        : interpolationError();

                if (tmpInterpolationError < bestError) {
                    bestError = tmpInterpolationError;
                    bestParameters = result;
                    this.XABREndCriteria_ = tmpEndCriteria;
                }

            } while (++iterations < maxGuesses_ &&
                    tmpInterpolationError > errorAccept_);

            for (int i = 0; i < bestParameters.size(); ++i)
                this.params_.set(i, bestParameters.get(i));

            this.error_ = interpolationError();
            this.maxError_ = interpolationMaxError();
        }
    }

    @Override
    public double value(double x) {
        return this.modelInstance_.volatility(x, volatilityType_);
    }

    @Override
    public double primitive(double v) {
        QL_FAIL("XABR primitive not implemented");
        return 0;
    }

    @Override
    public double derivative(double v) {
        QL_FAIL("XABR derivative not implemented");
        return 0;
    }

    @Override
    public double secondDerivative(double v) {
        QL_FAIL("XABR secondDerivative not implemented");
        return 0;
    }

    // calculate total squared weighted difference (L2 norm)
    public double interpolationSquaredError() {
        double error, totalError = 0.0;
        double[] x = xValue;
        double[] y = yValue;
        List<Double> w = weights_;
        for (int i = 0; i < x.length; i++) {
            error = (value(x[i]) - y[i]);
            totalError += error * error * w.get(i);
        }
        return totalError;
    }

    public double interpolationError() {
        int n = xValue.length;
        double squaredError = interpolationSquaredError();
        return Math.sqrt(n * squaredError / (n == 1 ? 1 : (n - 1)));
    }

    public double interpolationMaxError() {
        double error, maxError = QL_MIN_REAL;
        double[] i = xValue;
        double[] j = yValue;
        for (int k = 0; k < i.length; k++) {
            error = Math.abs(value(i[k]) - j[k]);
            maxError = Math.max(maxError, error);
        }
        return maxError;
    }

    // calculate weighted differences
    public Array interpolationErrors() {
        Array results = new Array(xValue.length);
        double[] x = xValue;
        double[] y = yValue;
        List<Double> w = this.weights_;
        for (int i = 0; i < xValue.length; i++) {
            results.set(i, (value(x[i]) - y[i]) * Math.sqrt(w.get(i)));
        }
        return results;
    }

    private boolean temp1() {
        for (boolean b : this.paramIsFixed_) {
            if (!b) return false;
        }
        return true;
    }

}
