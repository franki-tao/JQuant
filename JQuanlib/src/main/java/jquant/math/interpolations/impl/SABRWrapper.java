package jquant.math.interpolations.impl;

import jquant.termstructures.volatility.Sarb;

import java.util.List;

import static jquant.math.CommonUtil.QL_REQUIRE;
import static jquant.termstructures.volatility.Sarb.shiftedSabrVolatility;
import static jquant.termstructures.volatility.Sarb.validateSabrParameters;

public class SABRWrapper {
    private double t_, forward_;
    private List<Double> params_;
    private double shift_;

    public SABRWrapper(final double t,
                       final double forward,
                       final List<Double> params,
                       final List<Double> addParams) {
        t_ = t;
        forward_ = forward;
        params_ = params;
        shift_ = addParams.isEmpty() ? 0 : addParams.get(0);
        QL_REQUIRE(forward_ + shift_ > 0.0, "forward+shift must be positive: " + forward_ +
                " with shift " + shift_ + " not allowed");
        validateSabrParameters(params.get(0), params.get(1), params.get(2), params.get(3));
    }

    public double volatility(final double x, final Sarb.VolatilityType volatilityType) {
        return shiftedSabrVolatility(x, forward_, t_, params_.get(0), params_.get(1),
                params_.get(2), params_.get(3), shift_, volatilityType);
    }

    public double getT_() {
        return t_;
    }

    public double getForward_() {
        return forward_;
    }

    public List<Double> getParams_() {
        return params_;
    }

    public double getShift_() {
        return shift_;
    }
}
