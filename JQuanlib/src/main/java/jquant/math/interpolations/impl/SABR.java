package jquant.math.interpolations.impl;

import jquant.math.Interpolation;
import jquant.math.interpolations.SABRInterpolation;
import jquant.math.optimization.EndCriteria;
import jquant.math.optimization.OptimizationMethod;

import static jquant.termstructures.volatility.Sarb.VolatilityType.ShiftedLognormal;

public class SABR extends Interpolator {
    private double t_;
    private double forward_;
    private double alpha_, beta_, nu_, rho_;
    private boolean alphaIsFixed_, betaIsFixed_, nuIsFixed_, rhoIsFixed_;
    private boolean vegaWeighted_;
    private EndCriteria endCriteria_;
    private OptimizationMethod optMethod_;
    private double errorAccept_;
    private boolean useMaxError_;
    private int maxGuesses_;
    private double shift_;

    public static final boolean global = true;

    /**
     * @param t            t
     * @param forward      forward
     * @param alpha        alpha
     * @param beta         beta
     * @param nu           nu
     * @param rho          rho
     * @param alphaIsFixed alphaIsFixed
     * @param betaIsFixed  betaIsFixed
     * @param nuIsFixed    nuIsFixed
     * @param rhoIsFixed   rhoIsFixed
     * @param vegaWeighted vegaWeighted false
     * @param endCriteria  ext::shared_ptr<EndCriteria>()
     * @param optMethod    ext::shared_ptr<OptimizationMethod>()
     * @param errorAccept  0.0020
     * @param useMaxError  false
     * @param maxGuesses   50
     * @param shift        0
     */
    public SABR(double t,
                double forward,
                double alpha,
                double beta,
                double nu,
                double rho,
                boolean alphaIsFixed,
                boolean betaIsFixed,
                boolean nuIsFixed,
                boolean rhoIsFixed,
                boolean vegaWeighted,
                EndCriteria endCriteria,
                OptimizationMethod optMethod,
                double errorAccept,
                boolean useMaxError,
                int maxGuesses,
                double shift) {
        t_ = (t);
        forward_ = (forward);
        alpha_ = (alpha);
        beta_ = (beta);
        nu_ = (nu);
        rho_ = (rho);
        alphaIsFixed_ = (alphaIsFixed);
        betaIsFixed_ = (betaIsFixed);
        nuIsFixed_ = (nuIsFixed);
        rhoIsFixed_ = (rhoIsFixed);
        vegaWeighted_ = (vegaWeighted);
        endCriteria_ = (endCriteria);
        optMethod_ = (optMethod);
        errorAccept_ = (errorAccept);
        useMaxError_ = (useMaxError);
        maxGuesses_ = (maxGuesses);
        shift_ = (shift);
    }

    @Override
    public Interpolation interpolate(double[] x, double[] y) {
        return new SABRInterpolation(x, y, t_, forward_, alpha_,
                beta_, nu_, rho_, alphaIsFixed_, betaIsFixed_,
                nuIsFixed_, rhoIsFixed_, vegaWeighted_,
                endCriteria_, optMethod_, errorAccept_,
                useMaxError_, maxGuesses_, shift_, ShiftedLognormal);
    }

    @Override
    public int getRequiredPoints() {
        return 1;
    }
}
