package jquant.experimental.volatility;

import jquant.math.distributions.IncompleteGamma;

import static jquant.math.CommonUtil.QL_REQUIRE;

/**
 * Port of QuantLib's {@code detail::D0Interpolator} from
 * {@code ql/experimental/volatility/noarbsabr.cpp}.
 *
 * <p>Interpolates the absorption probability {@code d0} on the official
 * grid in {@code (tau, sigmaI, rho, nu, beta)} space, performs the
 * interpolation in {@code phi} space and maps the result back through the
 * inverse regularized incomplete gamma function.</p>
 */
final class D0Interpolator {
    private final double forward_, expiryTime_, alpha_, beta_, nu_, rho_, gamma_;
    private double sigmaI_;

    private static final double[] tauG_ = {
            0.25, 0.5, 0.75, 1.0, 1.25, 1.5, 1.75, 2.0, 2.25, 2.5, 2.75, 3.0,
            3.25, 3.5, 3.75, 4.0, 4.25, 4.5, 4.75, 5.0, 5.25, 5.5, 5.75, 6.0, 6.25,
            6.5, 6.75, 7.0, 7.25, 7.5, 7.75, 8.0, 8.25, 8.5, 8.75, 9.0, 9.25, 9.5,
            9.75, 10.0, 10.25, 10.5, 10.75, 11.0, 11.25, 11.5, 11.75, 12.0, 12.25,
            12.5, 12.75, 13.0, 13.25, 13.5, 13.75, 14.0, 14.25, 14.5, 14.75, 15.0,
            15.25, 15.5, 15.75, 16.0, 16.25, 16.5, 16.75, 17.0, 17.25, 17.5, 17.75,
            18.0, 18.25, 18.5, 18.75, 19.0, 19.25, 19.5, 19.75, 20.0, 20.25, 20.5,
            20.75, 21.0, 21.25, 21.5, 21.75, 22.0, 22.25, 22.5, 22.75, 23.0, 23.25,
            23.5, 23.75, 24.0, 24.25, 24.5, 24.75, 25.0, 25.25, 25.5, 25.75, 26.0,
            26.25, 26.5, 26.75, 27.0, 27.25, 27.5, 27.75, 28.0, 28.25, 28.5, 28.75,
            29.0, 29.25, 29.5, 29.75, 30.0
    };

    private static final double[] sigmaIG_ = {
            1.0, 0.8, 0.7, 0.6, 0.5, 0.45, 0.4, 0.35, 0.3, 0.27, 0.24, 0.21,
            0.18, 0.15, 0.125, 0.1, 0.075, 0.05
    };

    private static final double[] rhoG_ = {
            0.75, 0.50, 0.25, 0.00, -0.25, -0.50, -0.75
    };

    private static final double[] nuG_ = {
            0.1, 0.2, 0.3, 0.4, 0.5, 0.6, 0.7, 0.8
    };

    private static final double[] betaG_ = {
            0.01, 0.1, 0.2, 0.3, 0.4, 0.5, 0.6, 0.7, 0.8, 0.9
    };

    D0Interpolator(double forward, double expiryTime, double alpha,
                   double beta, double nu, double rho) {
        forward_ = forward;
        expiryTime_ = expiryTime;
        alpha_ = alpha;
        beta_ = beta;
        nu_ = nu;
        rho_ = rho;
        gamma_ = 1.0 / (2.0 * (1.0 - beta_));
        sigmaI_ = alpha_ * Math.pow(forward_, beta_ - 1.0);
    }

    double value() {
        // we do not need to check the indices here, because this is already
        // done in the NoArbSabrModel constructor

        int tauInd = upperBound(tauG_, expiryTime_);
        if (tauInd == tauG_.length)
            --tauInd; // tau at upper bound
        double expiryTimeTmp = expiryTime_;
        if (tauInd == 0) {
            ++tauInd;
            expiryTimeTmp = tauG_[0];
        }
        double tauL = (expiryTimeTmp - tauG_[tauInd - 1]) /
                (tauG_[tauInd] - tauG_[tauInd - 1]);

        // sigmaIG_ is decreasing; upper_bound over its reverse
        int sigmaIInd = sigmaIInd(sigmaI_);
        if (sigmaIInd == 0)
            ++sigmaIInd; // sigmaI at upper bound
        double sigmaIL = (sigmaI_ - sigmaIG_[sigmaIInd - 1]) /
                (sigmaIG_[sigmaIInd] - sigmaIG_[sigmaIInd - 1]);

        // rhoG_ is decreasing; upper_bound over its reverse
        int rhoInd = rhoInd(rho_);
        if (rhoInd == 0)
            rhoInd++;
        if (rhoInd == rhoG_.length)
            rhoInd--;
        double rhoL = (rho_ - rhoG_[rhoInd - 1]) /
                (rhoG_[rhoInd] - rhoG_[rhoInd - 1]);

        // for nu = 0 we know phi = 0.5*z_F^2
        int nuInd = upperBound(nuG_, nu_);
        if (nuInd == nuG_.length)
            --nuInd; // nu at upper bound
        double tmpNuG = nuInd > 0 ? nuG_[nuInd - 1] : 0.0;
        double nuL = (nu_ - tmpNuG) / (nuG_[nuInd] - tmpNuG);

        // for beta = 1 we know phi = 0.0
        int betaInd = upperBound(betaG_, beta_);
        double tmpBetaG;
        if (betaInd == betaG_.length)
            tmpBetaG = 1.0;
        else
            tmpBetaG = betaG_[betaInd];
        double betaL = (beta_ - betaG_[betaInd - 1]) /
                (tmpBetaG - betaG_[betaInd - 1]);

        final int[] sabrabsprob = SabrAbsProbTable.data();
        double phiRes = 0.0;
        for (int iTau = -1; iTau <= 0; ++iTau) {
            for (int iSigma = -1; iSigma <= 0; ++iSigma) {
                for (int iRho = -1; iRho <= 0; ++iRho) {
                    for (int iNu = -1; iNu <= 0; ++iNu) {
                        for (int iBeta = -1; iBeta <= 0; ++iBeta) {
                            double phiTmp;
                            if (iNu == -1 && nuInd == 0) {
                                phiTmp = 0.5 /
                                        (sigmaI_ * sigmaI_ * (1.0 - beta_) *
                                                (1.0 - beta_)); // this is 0.5*z_F^2, see above
                            } else {
                                if (iBeta == 0 && betaInd == betaG_.length) {
                                    phiTmp = phi(NoArbSabrModel.tiny_prob);
                                } else {
                                    int ind = tauInd + iTau +
                                            (sigmaIInd + iSigma +
                                                    (rhoInd + iRho +
                                                            (nuInd + iNu + ((betaInd + iBeta) *
                                                                    nuG_.length)) *
                                                                    rhoG_.length) *
                                                            sigmaIG_.length) *
                                                    tauG_.length;
                                    QL_REQUIRE(ind >= 0 && ind < 1209600,
                                            "absorption matrix index (" + ind + ") invalid");
                                    phiTmp = phi((sabrabsprob[ind] & 0xFFFFFFFFL) /
                                            NoArbSabrModel.nsim);
                                }
                            }
                            phiRes += phiTmp * (iTau == -1 ? (1.0 - tauL) : tauL) *
                                    (iSigma == -1 ? (1.0 - sigmaIL) : sigmaIL) *
                                    (iRho == -1 ? (1.0 - rhoL) : rhoL) *
                                    (iNu == -1 ? (1.0 - nuL) : nuL) *
                                    (iBeta == -1 ? (1.0 - betaL) : betaL);
                        }
                    }
                }
            }
        }
        return d0(phiRes);
    }

    private double phi(double d0) {
        if (d0 < 1e-14)
            return NoArbSabrModel.phiByTau_cutoff * expiryTime_;
        return IncompleteGamma.gammaQInv(gamma_, d0) * expiryTime_;
    }

    private double d0(double phi) {
        return IncompleteGamma.gammaQ(gamma_, Math.max(0.0, phi / expiryTime_));
    }

    /** std::upper_bound for an increasing array. */
    private static int upperBound(double[] values, double x) {
        int lo = 0, hi = values.length;
        while (lo < hi) {
            int mid = (lo + hi) >>> 1;
            if (values[mid] <= x)
                lo = mid + 1;
            else
                hi = mid;
        }
        return lo;
    }

    /**
     * Emulates
     * {@code size - (upper_bound(rbegin, rend, x) - rbegin)} for the
     * decreasing {@link #sigmaIG_} array.
     */
    private static int sigmaIInd(double x) {
        int idx = -1;
        for (int i = 0; i < sigmaIG_.length; ++i)
            if (sigmaIG_[i] > x)
                idx = i;
        return idx + 1;
    }

    /** Same as {@link #sigmaIInd(double)} but for {@link #rhoG_}. */
    private static int rhoInd(double x) {
        int idx = -1;
        for (int i = 0; i < rhoG_.length; ++i)
            if (rhoG_[i] > x)
                idx = i;
        return idx + 1;
    }
}
