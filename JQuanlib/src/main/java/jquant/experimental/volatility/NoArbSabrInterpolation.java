package jquant.experimental.volatility;

import jquant.math.Interpolation;
import jquant.math.interpolations.impl.XABRInterpolationImpl;
import jquant.math.optimization.EndCriteria;
import jquant.math.optimization.OptimizationMethod;
import jquant.termstructures.volatility.Sarb;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static jquant.math.CommonUtil.QL_REQUIRE;

/**
 * Port of QuantLib's {@code NoArbSabrInterpolation}
 * ({@code ql/experimental/volatility/noarbsabrinterpolation.hpp}).
 */
public class NoArbSabrInterpolation extends Interpolation {
    private final XABRInterpolationImpl tmp;

    public NoArbSabrInterpolation(double[] x, double[] y,
                                  double t, double forward,
                                  double alpha, double beta, double nu, double rho,
                                  boolean alphaIsFixed, boolean betaIsFixed,
                                  boolean nuIsFixed, boolean rhoIsFixed) {
        this(x, y, t, forward, alpha, beta, nu, rho,
                alphaIsFixed, betaIsFixed, nuIsFixed, rhoIsFixed, true);
    }

    public NoArbSabrInterpolation(double[] x, double[] y,
                                  double t, double forward,
                                  double alpha, double beta, double nu, double rho,
                                  boolean alphaIsFixed, boolean betaIsFixed,
                                  boolean nuIsFixed, boolean rhoIsFixed,
                                  boolean vegaWeighted) {
        this(x, y, t, forward, alpha, beta, nu, rho,
                alphaIsFixed, betaIsFixed, nuIsFixed, rhoIsFixed, vegaWeighted,
                null, null, 0.0020);
    }

    public NoArbSabrInterpolation(double[] x, double[] y,
                                  double t, double forward,
                                  double alpha, double beta, double nu, double rho,
                                  boolean alphaIsFixed, boolean betaIsFixed,
                                  boolean nuIsFixed, boolean rhoIsFixed,
                                  boolean vegaWeighted,
                                  EndCriteria endCriteria,
                                  OptimizationMethod optMethod,
                                  double errorAccept) {
        this(x, y, t, forward, alpha, beta, nu, rho,
                alphaIsFixed, betaIsFixed, nuIsFixed, rhoIsFixed, vegaWeighted,
                endCriteria, optMethod, errorAccept, false, 50, 0.0);
    }

    public NoArbSabrInterpolation(double[] x, double[] y,
                                  double t, double forward,
                                  double alpha, double beta, double nu, double rho,
                                  boolean alphaIsFixed, boolean betaIsFixed,
                                  boolean nuIsFixed, boolean rhoIsFixed,
                                  boolean vegaWeighted,
                                  EndCriteria endCriteria,
                                  OptimizationMethod optMethod,
                                  double errorAccept,
                                  boolean useMaxError,
                                  int maxGuesses,
                                  double shift) {
        QL_REQUIRE(shift == 0.0,
                "NoArbSabrInterpolation for non zero shift not implemented");
        tmp = new XABRInterpolationImpl(x, y, t, forward,
                Arrays.asList(alpha, beta, nu, rho),
                Arrays.asList(alphaIsFixed, betaIsFixed, nuIsFixed, rhoIsFixed),
                vegaWeighted, endCriteria, optMethod, errorAccept, useMaxError,
                maxGuesses, new ArrayList<>(), Sarb.VolatilityType.ShiftedLognormal,
                new NoArbSabrSpecs());
        impl_ = tmp;
    }

    public double expiry() {
        return tmp.t_;
    }

    public double forward() {
        return tmp.forward_;
    }

    public double alpha() {
        return tmp.params_.get(0);
    }

    public double beta() {
        return tmp.params_.get(1);
    }

    public double nu() {
        return tmp.params_.get(2);
    }

    public double rho() {
        return tmp.params_.get(3);
    }

    public double rmsError() {
        return tmp.error_;
    }

    public double maxError() {
        return tmp.maxError_;
    }

    public List<Double> interpolationWeights() {
        return tmp.weights_;
    }

    public EndCriteria.Type endCriteria() {
        return tmp.XABREndCriteria_;
    }
}
