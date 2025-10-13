package jquant.math.interpolations;

import jquant.math.Interpolation;
import jquant.math.interpolations.impl.SABRSpecs;
import jquant.math.interpolations.impl.XABRInterpolationImpl;
import jquant.math.optimization.EndCriteria;
import jquant.math.optimization.OptimizationMethod;
import jquant.termstructures.volatility.Sarb;

import java.util.Arrays;
import java.util.List;

public class SABRInterpolation extends Interpolation {
    private XABRInterpolationImpl tmp;

    /**
     *
     * @param x x strikes
     * @param y y volatilities
     * @param t t option expiry
     * @param forward forward
     * @param alpha alpha
     * @param beta beta
     * @param nu nu
     * @param rho rho
     * @param alphaIsFixed alphaIsFixed
     * @param betaIsFixed betaIsFixed
     * @param nuIsFixed nuIsFixed
     * @param rhoIsFixed rgoIsFixed
     * @param vegaWeighted vegaWeighted true
     * @param endCriteria endCriteria ext::shared_ptr<EndCriteria>()
     * @param optMethod optMethod ext::shared_ptr<OptimizationMethod>()
     * @param errorAccept errorError 0.0020
     * @param useMaxError useMaxError false
     * @param maxGuesses maxGuesses 50
     * @param shift shift 0
     * @param volatilityType volatilityType VolatilityType::ShiftedLognormal
     */
    public SABRInterpolation(double[] x, double[] y,
                             double t,
                             double forward, double alpha, double beta, double nu,
                             double rho, boolean alphaIsFixed, boolean betaIsFixed,
                             boolean nuIsFixed, boolean rhoIsFixed, boolean vegaWeighted,
                             EndCriteria endCriteria,
                             OptimizationMethod optMethod,
                             double errorAccept,
                             boolean useMaxError,
                             int maxGuesses, double shift,
                             Sarb.VolatilityType volatilityType) {
        tmp = new XABRInterpolationImpl(x, y, t, forward,
                Arrays.asList(alpha, beta, nu, rho),
                Arrays.asList(alphaIsFixed, betaIsFixed, nuIsFixed, rhoIsFixed),
                vegaWeighted, endCriteria, optMethod, errorAccept, useMaxError,
                maxGuesses, List.of(shift),volatilityType, new SABRSpecs());
        impl_ = tmp;
    }

    public double expiry() { return tmp.t_; }
    public double forward() { return tmp.forward_; }
    public double alpha() { return tmp.params_.get(0); }
    public double beta() { return tmp.params_.get(1); }
    public double nu() { return tmp.params_.get(2); }
    public double rho() { return tmp.params_.get(3); }
    public double rmsError() { return tmp.error_; }
    public double maxError() { return tmp.maxError_; }
    public final List<Double> interpolationWeights() {
        return tmp.weights_;
    }
    public EndCriteria.Type endCriteria() { return tmp.XABREndCriteria_; }

}
