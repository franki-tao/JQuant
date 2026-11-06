package jquant.pricingengines;

import jquant.Option;
import jquant.math.distributions.CumulativeNormalDistribution;

import static jquant.math.CommonUtil.QL_FAIL;
import static jquant.math.CommonUtil.QL_REQUIRE;
import static jquant.math.MathUtils.*;

/**
 * Black 1976 calculator for a plain-vanilla payoff.
 *
 * <p>Value is the alpha/beta substitution from QuantLib
 * {@code ql/pricingengines/blackcalculator.cpp}:
 * {@code discount * (forward * alpha + strike * beta)}.
 * For a call, {@code alpha = N(d1)} and {@code beta = -N(d2)}.
 * For a put, {@code alpha = N(d1) - 1} and {@code beta = 1 - N(d2)}.
 * Cash-measure ITM probability is {@code N(d2)}.
 *
 * <p>https://github.com/lballabio/QuantLib/blob/master/ql/pricingengines/blackcalculator.cpp
 */
public class BlackCalculator {
    private double strike_;
    private double forward_;
    private double stdDev_;
    private double discount_;
    private double variance_;
    private double d1_;
    private double d2_;
    private double alpha_;
    private double beta_;
    private double dAlphaDd1_;
    private double dBetaDd2_;
    private double nD1_;
    private double cumD1_;
    private double nD2_;
    private double cumD2_;
    private double x_;
    private double dxDs_;
    private double dxDstrike_;

    public BlackCalculator(PlainVanillaPayoff payoff, double forward, double stdDev, double discount) {
        strike_ = payoff.strike();
        forward_ = forward;
        stdDev_ = stdDev;
        discount_ = discount;
        variance_ = stdDev * stdDev;
        initialize(payoff.optionType());
    }

    public BlackCalculator(Option.Type optionType, double strike, double forward,
                           double stdDev, double discount) {
        this(new PlainVanillaPayoff(optionType, strike), forward, stdDev, discount);
    }

    public double value() {
        return discount_ * (forward_ * alpha_ + x_ * beta_);
    }

    public double delta(double spot) {
        QL_REQUIRE(spot > 0.0, "positive spot value required: " + spot + " not allowed");
        if (stdDev_ <= QL_EPSILON) {
            double dForwardDs = forward_ / spot;
            if (close(forward_, strike_)) {
                return discount_ * (alpha_ >= 0.0 ? 0.5 : -0.5) * dForwardDs;
            } else if (forward_ > strike_) {
                return alpha_ >= 0.0 ? discount_ * dForwardDs : 0.0;
            } else {
                return alpha_ >= 0.0 ? 0.0 : discount_ * (-1.0) * dForwardDs;
            }
        }
        double dForwardDs = forward_ / spot;
        double temp = stdDev_ * spot;
        double dAlphaDs = dAlphaDd1_ / temp;
        double dBetaDs = dBetaDd2_ / temp;
        double temp2 = dAlphaDs * forward_ + alpha_ * dForwardDs
                + dBetaDs * x_ + beta_ * dxDs_;
        return discount_ * temp2;
    }

    public double deltaForward() {
        if (stdDev_ <= QL_EPSILON) {
            if (close(forward_, strike_)) {
                return discount_ * (alpha_ >= 0.0 ? 0.5 : -0.5);
            } else if (forward_ > strike_) {
                return alpha_ >= 0.0 ? discount_ : 0.0;
            } else {
                return alpha_ >= 0.0 ? 0.0 : discount_ * (-1.0);
            }
        }
        double temp = stdDev_ * forward_;
        double dAlphaDforward = dAlphaDd1_ / temp;
        double dBetaDforward = dBetaDd2_ / temp;
        double temp2 = dAlphaDforward * forward_ + alpha_ + dBetaDforward * x_;
        return discount_ * temp2;
    }

    public double elasticity(double spot) {
        double val = value();
        double del = delta(spot);
        if (val > QL_EPSILON) {
            return del / val * spot;
        } else if (Math.abs(del) < QL_EPSILON) {
            return 0.0;
        } else if (del > 0.0) {
            return QL_MAX_REAL;
        } else {
            return QL_MIN_REAL;
        }
    }

    public double elasticityForward() {
        double val = value();
        double del = deltaForward();
        if (val > QL_EPSILON) {
            return del / val * forward_;
        } else if (Math.abs(del) < QL_EPSILON) {
            return 0.0;
        } else if (del > 0.0) {
            return QL_MAX_REAL;
        } else {
            return QL_MIN_REAL;
        }
    }

    public double gamma(double spot) {
        QL_REQUIRE(spot > 0.0, "positive spot value required: " + spot + " not allowed");
        if (stdDev_ <= QL_EPSILON) {
            return 0.0;
        }
        double dForwardDs = forward_ / spot;
        double temp = stdDev_ * spot;
        double dAlphaDs = dAlphaDd1_ / temp;
        double dBetaDs = dBetaDd2_ / temp;
        double d2AlphaDs2 = -dAlphaDs / spot * (1.0 + d1_ / stdDev_);
        double d2BetaDs2 = -dBetaDs / spot * (1.0 + d2_ / stdDev_);
        double temp2 = d2AlphaDs2 * forward_ + 2.0 * dAlphaDs * dForwardDs
                + d2BetaDs2 * x_ + 2.0 * dBetaDs * dxDs_;
        return discount_ * temp2;
    }

    public double gammaForward() {
        if (stdDev_ <= QL_EPSILON) {
            return 0.0;
        }
        double temp = stdDev_ * forward_;
        double dAlphaDforward = dAlphaDd1_ / temp;
        double dBetaDforward = dBetaDd2_ / temp;
        double d2AlphaDforward2 = -dAlphaDforward / forward_ * (1.0 + d1_ / stdDev_);
        double d2BetaDforward2 = -dBetaDforward / forward_ * (1.0 + d2_ / stdDev_);
        double temp2 = d2AlphaDforward2 * forward_ + 2.0 * dAlphaDforward + d2BetaDforward2 * x_;
        return discount_ * temp2;
    }

    public double theta(double spot, double maturity) {
        QL_REQUIRE(maturity >= 0.0, "maturity (" + maturity + ") must be non-negative");
        if (close(maturity, 0.0)) {
            return 0.0;
        }
        return -(Math.log(discount_) * value()
                + Math.log(forward_ / spot) * spot * delta(spot)
                + 0.5 * variance_ * spot * spot * gamma(spot)) / maturity;
    }

    public double thetaPerDay(double spot, double maturity) {
        return theta(spot, maturity) / 365.0;
    }

    public double vega(double maturity) {
        QL_REQUIRE(maturity >= 0.0, "negative maturity not allowed");
        if (stdDev_ <= QL_EPSILON) {
            return 0.0;
        }
        double temp = Math.log(strike_ / forward_) / variance_;
        double dAlphaDsigma = dAlphaDd1_ * (temp + 0.5);
        double dBetaDsigma = dBetaDd2_ * (temp - 0.5);
        double temp2 = dAlphaDsigma * forward_ + dBetaDsigma * x_;
        return discount_ * Math.sqrt(maturity) * temp2;
    }

    public double rho(double maturity) {
        QL_REQUIRE(maturity >= 0.0, "negative maturity not allowed");
        if (stdDev_ <= QL_EPSILON) {
            return maturity * (deltaForward() * forward_ - value());
        }
        double dAlphaDr = dAlphaDd1_ / stdDev_;
        double dBetaDr = dBetaDd2_ / stdDev_;
        double temp = dAlphaDr * forward_ + alpha_ * forward_ + dBetaDr * x_;
        return maturity * (discount_ * temp - value());
    }

    public double dividendRho(double maturity) {
        QL_REQUIRE(maturity >= 0.0, "negative maturity not allowed");
        if (stdDev_ <= QL_EPSILON) {
            double deltaFwd = deltaForward() / discount_;
            return -maturity * discount_ * deltaFwd * forward_;
        }
        double dAlphaDq = -dAlphaDd1_ / stdDev_;
        double dBetaDq = -dBetaDd2_ / stdDev_;
        double temp = dAlphaDq * forward_ - alpha_ * forward_ + dBetaDq * x_;
        return maturity * discount_ * temp;
    }

    /** Bond-measure (monetary) probability of exercise, N(d2). */
    public double itmCashProbability() {
        return cumD2_;
    }

    /** Asset-measure probability of exercise, N(d1). */
    public double itmAssetProbability() {
        return cumD1_;
    }

    public double strikeSensitivity() {
        if (stdDev_ <= QL_EPSILON) {
            if (close(forward_, strike_)) {
                return alpha_ >= 0.0 ? -discount_ * 0.5 : discount_ * 0.5;
            } else if (forward_ > strike_) {
                return alpha_ >= 0.0 ? -discount_ : 0.0;
            } else {
                return alpha_ >= 0.0 ? 0.0 : discount_;
            }
        }
        double temp = stdDev_ * strike_;
        double dAlphaDstrike = -dAlphaDd1_ / temp;
        double dBetaDstrike = -dBetaDd2_ / temp;
        double temp2 = dAlphaDstrike * forward_ + dBetaDstrike * x_ + beta_ * dxDstrike_;
        return discount_ * temp2;
    }

    public double strikeGamma() {
        if (stdDev_ <= QL_EPSILON) {
            return 0.0;
        }
        double temp = stdDev_ * strike_;
        double dAlphaDstrike = -dAlphaDd1_ / temp;
        double dBetaDstrike = -dBetaDd2_ / temp;
        double d2AlphaD2strike = -dAlphaDstrike / strike_ * (1.0 - d1_ / stdDev_);
        double d2BetaD2strike = -dBetaDstrike / strike_ * (1.0 - d2_ / stdDev_);
        double temp2 = d2AlphaD2strike * forward_ + d2BetaD2strike * x_
                + 2.0 * dBetaDstrike * dxDstrike_;
        return discount_ * temp2;
    }

    public double vanna(double spot, double maturity) {
        QL_REQUIRE(spot > 0.0, "positive spot value required: " + spot + " not allowed");
        QL_REQUIRE(maturity >= 0.0, "negative maturity not allowed");
        if (stdDev_ <= QL_EPSILON) {
            return 0.0;
        }
        return -d2_ / (spot * stdDev_) * vega(maturity);
    }

    public double volga(double maturity) {
        QL_REQUIRE(maturity >= 0.0, "negative maturity not allowed");
        if (stdDev_ <= QL_EPSILON) {
            return 0.0;
        }
        return vega(maturity) * d1_ * d2_ / stdDev_;
    }

    public double alpha() {
        return alpha_;
    }

    public double beta() {
        return beta_;
    }

    private void initialize(Option.Type optionType) {
        QL_REQUIRE(strike_ >= 0.0, "strike (" + strike_ + ") must be non-negative");
        QL_REQUIRE(forward_ > 0.0, "forward (" + forward_ + ") must be positive");
        QL_REQUIRE(stdDev_ >= 0.0, "stdDev (" + stdDev_ + ") must be non-negative");
        QL_REQUIRE(discount_ > 0.0, "discount (" + discount_ + ") must be positive");

        if (stdDev_ >= QL_EPSILON) {
            if (close(strike_, 0.0)) {
                d1_ = QL_MAX_REAL;
                d2_ = QL_MAX_REAL;
                cumD1_ = 1.0;
                cumD2_ = 1.0;
                nD1_ = 0.0;
                nD2_ = 0.0;
            } else {
                d1_ = Math.log(forward_ / strike_) / stdDev_ + 0.5 * stdDev_;
                d2_ = d1_ - stdDev_;
                CumulativeNormalDistribution phi = new CumulativeNormalDistribution();
                cumD1_ = phi.value(d1_);
                cumD2_ = phi.value(d2_);
                nD1_ = phi.derivative(d1_);
                nD2_ = phi.derivative(d2_);
            }
        } else {
            if (close(forward_, strike_)) {
                d1_ = 0.0;
                d2_ = 0.0;
                cumD1_ = 0.5;
                cumD2_ = 0.5;
                nD1_ = M_SQRT_2 * M_1_SQRTPI;
                nD2_ = M_SQRT_2 * M_1_SQRTPI;
            } else if (forward_ > strike_) {
                d1_ = QL_MAX_REAL;
                d2_ = QL_MAX_REAL;
                cumD1_ = 1.0;
                cumD2_ = 1.0;
                nD1_ = 0.0;
                nD2_ = 0.0;
            } else {
                d1_ = QL_MIN_REAL;
                d2_ = QL_MIN_REAL;
                cumD1_ = 0.0;
                cumD2_ = 0.0;
                nD1_ = 0.0;
                nD2_ = 0.0;
            }
        }

        x_ = strike_;
        dxDstrike_ = 1.0;
        dxDs_ = 0.0;

        if (optionType == Option.Type.Call) {
            alpha_ = cumD1_;
            dAlphaDd1_ = nD1_;
            beta_ = -cumD2_;
            dBetaDd2_ = -nD2_;
        } else if (optionType == Option.Type.Put) {
            alpha_ = -1.0 + cumD1_;
            dAlphaDd1_ = nD1_;
            beta_ = 1.0 - cumD2_;
            dBetaDd2_ = -nD2_;
        } else {
            QL_FAIL("invalid option type");
        }
    }
}
