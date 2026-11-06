package jquant.pricingengines;

import jquant.Option;
import jquant.math.distributions.CumulativeNormalDistribution;

import static jquant.math.CommonUtil.QL_FAIL;
import static jquant.math.CommonUtil.QL_REQUIRE;
import static jquant.math.MathUtils.*;

/**
 * Normal (Bachelier) calculator for a plain-vanilla payoff.
 *
 * <p>The model uses one variable {@code d = (forward - strike) / stdDev},
 * not Black d1 and d2. QuantLib does not publish that variable. Call value
 * is {@code discount * ((F-K) N(d) + stdDev n(d))}; put value is
 * {@code discount * ((K-F) N(-d) + stdDev n(d))}. Cash and asset ITM
 * probabilities are both {@code N(d)} for a call and {@code N(-d)} for a put.
 *
 * <p>https://github.com/lballabio/QuantLib/blob/master/ql/pricingengines/bacheliercalculator.cpp
 */
public class BachelierCalculator {
    private double strike_;
    private double forward_;
    private double stdDev_;
    private double discount_;
    private double d_;
    private double alpha_;
    private double nD_;
    private double cumD_;

    public BachelierCalculator(PlainVanillaPayoff payoff, double forward, double stdDev, double discount) {
        strike_ = payoff.strike();
        forward_ = forward;
        stdDev_ = stdDev;
        discount_ = discount;
        initialize(payoff.optionType());
    }

    public BachelierCalculator(Option.Type optionType, double strike, double forward,
                               double stdDev, double discount) {
        this(new PlainVanillaPayoff(optionType, strike), forward, stdDev, discount);
    }

    public double value() {
        double intrinsic = forward_ - strike_;
        double timeValue = stdDev_ > QL_EPSILON ? stdDev_ * nD_ : 0.0;
        double result = alpha_ >= 0.0
                ? intrinsic * cumD_ + timeValue
                : -intrinsic * (1.0 - cumD_) + timeValue;
        return discount_ * Math.max(result, 0.0);
    }

    public double delta(double spot) {
        return deltaForward() * (forward_ / spot);
    }

    public double deltaForward() {
        return alpha_ >= 0.0 ? discount_ * cumD_ : discount_ * (cumD_ - 1.0);
    }

    public double elasticity(double spot) {
        return elasticity(value(), delta(spot), spot);
    }

    public double elasticityForward() {
        return elasticity(value(), deltaForward(), forward_);
    }

    public double gamma(double spot) {
        if (stdDev_ <= QL_EPSILON) {
            return 0.0;
        }
        double dForwardDs = forward_ / spot;
        return discount_ * (nD_ / stdDev_) * dForwardDs * dForwardDs;
    }

    public double gammaForward() {
        if (stdDev_ <= QL_EPSILON) {
            return 0.0;
        }
        return discount_ * nD_ / stdDev_;
    }

    public double theta(double spot, double maturity) {
        QL_REQUIRE(maturity >= 0.0, "maturity (" + maturity + ") must be non-negative");
        if (close(maturity, 0.0)) {
            return 0.0;
        }
        return -(Math.log(discount_) * value()
                + Math.log(forward_ / spot) * spot * delta(spot)
                + 0.5 * stdDev_ * stdDev_ * gamma(spot)) / maturity;
    }

    public double vega(double maturity) {
        QL_REQUIRE(maturity >= 0.0, "negative maturity not allowed");
        if (maturity <= QL_EPSILON || stdDev_ <= QL_EPSILON) {
            return 0.0;
        }
        return discount_ * Math.sqrt(maturity) * nD_;
    }

    public double rho(double maturity) {
        QL_REQUIRE(maturity >= 0.0, "negative maturity not allowed");
        return maturity * (deltaForward() * forward_ - value());
    }

    public double dividendRho(double maturity) {
        QL_REQUIRE(maturity >= 0.0, "negative maturity not allowed");
        double deltaFwd = alpha_ >= 0.0 ? cumD_ : (cumD_ - 1.0);
        return -maturity * discount_ * deltaFwd * forward_;
    }

    public double itmCashProbability() {
        return alpha_ >= 0.0 ? cumD_ : 1.0 - cumD_;
    }

    public double itmAssetProbability() {
        return itmCashProbability();
    }

    public double strikeSensitivity() {
        return alpha_ >= 0.0 ? -discount_ * cumD_ : discount_ * (1.0 - cumD_);
    }

    public double strikeGamma() {
        if (stdDev_ <= QL_EPSILON) {
            return 0.0;
        }
        return discount_ * nD_ / stdDev_;
    }

    public double vanna(double maturity) {
        if (maturity <= QL_EPSILON || stdDev_ <= QL_EPSILON) {
            return 0.0;
        }
        return -d_ * nD_ * Math.sqrt(maturity) / stdDev_;
    }

    public double volga(double maturity) {
        if (maturity <= QL_EPSILON || stdDev_ <= QL_EPSILON) {
            return 0.0;
        }
        return (d_ * d_ / stdDev_) * vega(maturity);
    }

    private static double elasticity(double value, double delta, double level) {
        if (value > QL_EPSILON) {
            return delta / value * level;
        } else if (Math.abs(delta) < QL_EPSILON) {
            return 0.0;
        } else if (delta > 0.0) {
            return QL_MAX_REAL;
        } else {
            return QL_MIN_REAL;
        }
    }

    private void initialize(Option.Type optionType) {
        QL_REQUIRE(stdDev_ >= 0.0, "stdDev (" + stdDev_ + ") must be non-negative");
        QL_REQUIRE(discount_ > 0.0, "discount (" + discount_ + ") must be positive");

        if (stdDev_ >= QL_EPSILON) {
            d_ = (forward_ - strike_) / stdDev_;
            CumulativeNormalDistribution phi = new CumulativeNormalDistribution();
            cumD_ = phi.value(d_);
            nD_ = phi.derivative(d_);
        } else if (close(forward_, strike_)) {
            d_ = 0.0;
            cumD_ = 0.5;
            nD_ = M_SQRT_2 * M_1_SQRTPI;
        } else if (forward_ > strike_) {
            d_ = QL_MAX_REAL;
            cumD_ = 1.0;
            nD_ = 0.0;
        } else {
            d_ = QL_MIN_REAL;
            cumD_ = 0.0;
            nD_ = 0.0;
        }

        if (optionType == Option.Type.Call) {
            alpha_ = cumD_;
        } else if (optionType == Option.Type.Put) {
            alpha_ = cumD_ - 1.0;
        } else {
            QL_FAIL("invalid option type");
        }
    }
}
