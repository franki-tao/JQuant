package jquant.math.interpolations;

import jquant.math.Interpolation;
import jquant.math.interpolations.impl.XABRInterpolationImpl;
import jquant.math.interpolations.impl.ZabrSpecs;
import jquant.math.optimization.EndCriteria;
import jquant.math.optimization.OptimizationMethod;
import jquant.termstructures.volatility.Sarb;
import jquant.termstructures.volatility.ZabrEvaluation;

import java.util.Arrays;
import java.util.List;

/**
 * ZABR smile interpolation between discrete volatility points.
 *
 * <p>Ported from QuantLib
 * {@code ql/math/interpolations/zabrinterpolation.hpp} (the
 * {@code ZabrInterpolation} template).
 */
public class ZabrInterpolation extends Interpolation {

    private final XABRInterpolationImpl tmp;

    /** Official defaults: vega-weighted, default end criteria/optimiser. */
    public ZabrInterpolation(double[] x,
                             double[] y,
                             double t,
                             double forward,
                             double alpha,
                             double beta,
                             double nu,
                             double rho,
                             double gamma,
                             boolean alphaIsFixed,
                             boolean betaIsFixed,
                             boolean nuIsFixed,
                             boolean rhoIsFixed,
                             boolean gammaIsFixed) {
        this(x, y, t, forward, alpha, beta, nu, rho, gamma,
                alphaIsFixed, betaIsFixed, nuIsFixed, rhoIsFixed, gammaIsFixed,
                true, null, null, 0.0020, false, 50,
                ZabrEvaluation.ZabrShortMaturityLognormal);
    }

    public ZabrInterpolation(double[] x,
                             double[] y,
                             double t,
                             double forward,
                             double alpha,
                             double beta,
                             double nu,
                             double rho,
                             double gamma,
                             boolean alphaIsFixed,
                             boolean betaIsFixed,
                             boolean nuIsFixed,
                             boolean rhoIsFixed,
                             boolean gammaIsFixed,
                             boolean vegaWeighted,
                             EndCriteria endCriteria,
                             OptimizationMethod optMethod,
                             double errorAccept,
                             boolean useMaxError,
                             int maxGuesses) {
        this(x, y, t, forward, alpha, beta, nu, rho, gamma,
                alphaIsFixed, betaIsFixed, nuIsFixed, rhoIsFixed, gammaIsFixed,
                vegaWeighted, endCriteria, optMethod, errorAccept,
                useMaxError, maxGuesses,
                ZabrEvaluation.ZabrShortMaturityLognormal);
    }

    public ZabrInterpolation(double[] x,
                             double[] y,
                             double t,
                             double forward,
                             double alpha,
                             double beta,
                             double nu,
                             double rho,
                             double gamma,
                             boolean alphaIsFixed,
                             boolean betaIsFixed,
                             boolean nuIsFixed,
                             boolean rhoIsFixed,
                             boolean gammaIsFixed,
                             boolean vegaWeighted,
                             EndCriteria endCriteria,
                             OptimizationMethod optMethod,
                             double errorAccept,
                             boolean useMaxError,
                             int maxGuesses,
                             ZabrEvaluation evaluation) {
        tmp = new XABRInterpolationImpl(x, y, t, forward,
                Arrays.asList(alpha, beta, nu, rho, gamma),
                Arrays.asList(alphaIsFixed, betaIsFixed, nuIsFixed, rhoIsFixed,
                        gammaIsFixed),
                vegaWeighted, endCriteria, optMethod, errorAccept, useMaxError,
                maxGuesses, List.of(), Sarb.VolatilityType.ShiftedLognormal,
                new ZabrSpecs(evaluation));
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

    public double gamma() {
        return tmp.params_.get(4);
    }

    public double rmsError() {
        return tmp.error_;
    }

    public double maxError() {
        return tmp.maxError_;
    }

    public final List<Double> interpolationWeights() {
        return tmp.weights_;
    }

    public EndCriteria.Type endCriteria() {
        return tmp.XABREndCriteria_;
    }
}
