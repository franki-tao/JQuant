package jquant.experimental.volatility;

import jquant.ModifiedBessel;
import jquant.math.integrals.GaussLobattoIntegral;
import jquant.math.solvers1d.Brent;

import static jquant.math.CommonUtil.QL_REQUIRE;
import static jquant.math.MathUtils.QL_MIN_POSITIVE_REAL;

/**
 * Port of QuantLib's experimental no-arbitrage SABR model
 * ({@code ql/experimental/volatility/noarbsabr.{hpp,cpp}}).
 *
 * <p>Reference: Paul Doust, No-arbitrage SABR, The Journal of Computational
 * Finance, Volume 15/Number 3, Spring 2012.</p>
 *
 * <p>Copyright (C) 2014 Peter Caspers.  This port follows the official
 * QuantLib source.</p>
 */
public class NoArbSabrModel {

    // parameter bounds (official namespace detail::NoArbSabrModel)
    public static final double beta_min = 0.01;
    public static final double beta_max = 0.99;
    public static final double expiryTime_max = 30.0;
    public static final double sigmaI_min = 0.05;
    public static final double sigmaI_max = 1.00;
    public static final double nu_min = 0.01;
    public static final double nu_max = 0.80;
    public static final double rho_min = -0.99;
    public static final double rho_max = 0.99;
    // cutoff for phi(d0) / tau
    // if beta = 0.99, d0 is below 1E-14 for bigger values than this
    public static final double phiByTau_cutoff = 124.587;
    // number of mc simulations in tabulated absorption probabilities
    public static final double nsim = 2500000.0;
    // small probability used for extrapolation of beta towards 1
    public static final double tiny_prob = 1E-5;
    // minimum strike used for normal case integration
    public static final double strike_min = 1E-6;
    // accuracy and max iterations for numerical integration
    public static final double i_accuracy = 1E-7;
    public static final int i_max_iterations = 10000;
    // accuracy when adjusting the model forward to match the given forward
    public static final double forward_accuracy = 1E-6;
    // step for searching the model forward in newton algorithm
    public static final double forward_search_step = 0.0010;
    // lower bound for density evaluation
    public static final double density_lower_bound = 1E-50;
    // threshold to identify a zero density
    public static final double density_threshold = 1E-100;

    private final double expiryTime_, externalForward_;
    private final double alpha_, beta_, nu_, rho_;
    private double absProb_, fmin_, fmax_;
    private double forward_, numericalIntegralOverP_;
    private double numericalForward_;
    private final GaussLobattoIntegral integrator_;

    public NoArbSabrModel(final double expiryTime, final double forward,
                          final double alpha, final double beta,
                          final double nu, final double rho) {
        QL_REQUIRE(expiryTime > 0.0 && expiryTime <= expiryTime_max,
                "expiryTime (" + expiryTime + ") out of bounds");
        QL_REQUIRE(forward > 0.0,
                "forward (" + forward + ") must be positive");
        QL_REQUIRE(beta >= beta_min && beta <= beta_max,
                "beta (" + beta + ") out of bounds");
        double sigmaI = alpha * Math.pow(forward, beta - 1.0);
        QL_REQUIRE(sigmaI >= sigmaI_min && sigmaI <= sigmaI_max,
                "sigmaI = alpha*forward^(beta-1.0) (" + sigmaI +
                        ") out of bounds, alpha=" + alpha +
                        " beta=" + beta + " forward=" + forward);
        QL_REQUIRE(nu >= nu_min && nu <= nu_max,
                "nu (" + nu + ") out of bounds");
        QL_REQUIRE(rho >= rho_min && rho <= rho_max,
                "rho (" + rho + ") out of bounds");

        expiryTime_ = expiryTime;
        externalForward_ = forward;
        alpha_ = alpha;
        beta_ = beta;
        nu_ = nu;
        rho_ = rho;
        forward_ = forward;
        numericalForward_ = forward;

        // determine a region sufficient for integration in the normal case
        fmin_ = fmax_ = forward_;
        for (double tmp = p(fmax_);
             tmp > Math.max(i_accuracy / Math.max(1.0, fmax_ - fmin_),
                     density_threshold);
             tmp = p(fmax_)) {
            fmax_ *= 2.0;
        }
        for (double tmp = p(fmin_);
             tmp > Math.max(i_accuracy / Math.max(1.0, fmax_ - fmin_),
                     density_threshold);
             tmp = p(fmin_)) {
            fmin_ *= 0.5;
        }
        fmin_ = Math.max(strike_min, fmin_);

        QL_REQUIRE(fmax_ > fmin_,
                "could not find a reasonable integration domain");

        integrator_ = new GaussLobattoIntegral(i_max_iterations, i_accuracy);

        D0Interpolator d0 = new D0Interpolator(forward_, expiryTime_,
                alpha_, beta_, nu_, rho_);
        absProb_ = d0.value();

        try {
            Brent b = new Brent();
            double start = Math.sqrt(externalForward_ - strike_min);
            double tmp = b.solve(x -> forwardError(x),
                    forward_accuracy, start,
                    Math.min(forward_search_step, start / 2.0));
            forward_ = tmp * tmp + strike_min;
        } catch (IllegalArgumentException e) {
            // fall back to unadjusted forward (QuantLib catches Error&)
            forward_ = externalForward_;
        }

        double d = forwardError(Math.sqrt(forward_ - strike_min));
        numericalForward_ = d + externalForward_;
    }

    public double optionPrice(final double strike) {
        if (p(Math.max(forward_, strike)) < density_threshold)
            return 0.0;
        return (1.0 - absProb_) *
                (integrator_.value(f -> Math.max(f - strike, 0.0) * p(f),
                        strike, Math.max(fmax_, 2.0 * strike)) /
                        numericalIntegralOverP_);
    }

    public double digitalOptionPrice(final double strike) {
        if (strike < QL_MIN_POSITIVE_REAL)
            return 1.0;
        if (p(Math.max(forward_, strike)) < density_threshold)
            return 0.0;
        return (1.0 - absProb_)
                * (integrator_.value(f -> p(f),
                        strike, Math.max(fmax_, 2.0 * strike)) /
                numericalIntegralOverP_);
    }

    public double density(final double strike) {
        return p(strike) * (1 - absProb_) / numericalIntegralOverP_;
    }

    public double forward() {
        return externalForward_;
    }

    public double numericalForward() {
        return numericalForward_;
    }

    public double expiryTime() {
        return expiryTime_;
    }

    public double alpha() {
        return alpha_;
    }

    public double beta() {
        return beta_;
    }

    public double nu() {
        return nu_;
    }

    public double rho() {
        return rho_;
    }

    public double absorptionProbability() {
        return absProb_;
    }

    private double forwardError(final double forward) {
        forward_ = forward * forward + strike_min;
        numericalIntegralOverP_ = integrator_.value(this::p, fmin_, fmax_);
        return optionPrice(0.0) - externalForward_;
    }

    private double p(final double f) {
        if (f < density_lower_bound || forward_ < density_lower_bound)
            return 0.0;

        double fOmB = Math.pow(f, 1.0 - beta_);
        double FOmB = Math.pow(forward_, 1.0 - beta_);

        double zf = fOmB / (alpha_ * (1.0 - beta_));
        double zF = FOmB / (alpha_ * (1.0 - beta_));
        double z = zF - zf;

        // double JzF = sqrt(1.0 - 2.0 * rho_ * nu_ * zF + nu_ * nu_ * zF * zF);
        double Jmzf = Math.sqrt(1.0 + 2.0 * rho_ * nu_ * zf + nu_ * nu_ * zf * zf);
        double Jz = Math.sqrt(1.0 - 2.0 * rho_ * nu_ * z + nu_ * nu_ * z * z);

        double xz = Math.log((Jz - rho_ + nu_ * z) / (1.0 - rho_)) / nu_;
        double Bp_B = beta_ / FOmB;
        // double Bpp_B = beta_ * (2.0 * beta_ - 1.0) / (FOmB * FOmB);
        double kappa1 = 0.125 * nu_ * nu_ * (2.0 - 3.0 * rho_ * rho_) -
                0.25 * rho_ * nu_ * alpha_ * Bp_B;
        // double kappa2 = alpha_ * alpha_ * (0.25 * Bpp_B - 0.375 * Bp_B * Bp_B);
        double gamma = 1.0 / (2.0 * (1.0 - beta_));
        double sqrtOmR = Math.sqrt(1.0 - rho_ * rho_);
        double h = 0.5 * beta_ * rho_ / ((1.0 - beta_) * Jmzf * Jmzf) *
                (nu_ * zf * Math.log(zf * Jz / zF) +
                        (1 + rho_ * nu_ * zf) / sqrtOmR *
                                (Math.atan((nu_ * z - rho_) / sqrtOmR) +
                                        Math.atan(rho_ / sqrtOmR)));

        return Math.pow(Jz, -1.5) / (alpha_ * Math.pow(f, beta_) * expiryTime_) *
                Math.pow(zf, 1.0 - gamma) * Math.pow(zF, gamma) *
                Math.exp(-(xz * xz) / (2.0 * expiryTime_) +
                        (h + kappa1 * expiryTime_)) *
                ModifiedBessel.modifiedBesselFunction_i_exponentiallyWeighted(
                        gamma, zF * zf / expiryTime_);
    }
}
