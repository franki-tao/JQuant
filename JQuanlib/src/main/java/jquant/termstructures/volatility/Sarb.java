package jquant.termstructures.volatility;

import java.util.Arrays;

import static java.lang.Math.*;
import static jquant.math.CommonUtil.QL_FAIL;
import static jquant.math.CommonUtil.QL_REQUIRE;
import static jquant.math.MathUtils.QL_EPSILON;
import static jquant.math.MathUtils.close;
import static jquant.math.MathUtils.squared;
import static jquant.termstructures.volatility.Sarb.VolatilityType.Normal;

public class Sarb {
    public enum VolatilityType {ShiftedLognormal, Normal}

    ;

    public static void validateSabrParameters(double alpha,
                                              double beta,
                                              double nu,
                                              double rho) {
        QL_REQUIRE(alpha > 0.0, "alpha must be positive: " +
                alpha + " not allowed");
        QL_REQUIRE(beta >= 0.0 && beta <= 1.0, "beta must be in (0.0, 1.0): " +
                beta + " not allowed");
        QL_REQUIRE(nu >= 0.0, "nu must be non negative: " +
                nu + " not allowed");
        QL_REQUIRE(rho * rho < 1.0, "rho square must be less than one: " +
                rho + " not allowed");
    }

    public static double shiftedSabrVolatility(double strike,
                                               double forward,
                                               double expiryTime,
                                               double alpha,
                                               double beta,
                                               double nu,
                                               double rho,
                                               double shift,
                                               VolatilityType volatilityType) {
        QL_REQUIRE(strike + shift > 0.0, "strike+shift must be positive: "
                + strike + "+" + shift + " not allowed");
        QL_REQUIRE(forward + shift > 0.0, "at the money forward rate + shift must be " +
                "positive: " + forward + " " + shift + " not allowed");
        QL_REQUIRE(expiryTime >= 0.0, "expiry time must be non-negative: "
                + expiryTime + " not allowed");
        validateSabrParameters(alpha, beta, nu, rho);
        return unsafeShiftedSabrVolatility(strike, forward, expiryTime,
                alpha, beta, nu, rho, shift, volatilityType);
    }

    public static double shiftedSabrVolatility(double strike,
                                               double forward,
                                               double expiryTime,
                                               double alpha,
                                               double beta,
                                               double nu,
                                               double rho,
                                               double shift) {
        return shiftedSabrVolatility(strike, forward, expiryTime, alpha, beta, nu, rho,
                shift, VolatilityType.ShiftedLognormal);
    }

    public static double unsafeShiftedSabrVolatility(double strike,
                                                     double forward,
                                                     double expiryTime,
                                                     double alpha,
                                                     double beta,
                                                     double nu,
                                                     double rho,
                                                     double shift,
                                                     VolatilityType volatilityType) {
        if (volatilityType == Normal) {
            return unsafeSabrNormalVolatility(strike + shift, forward + shift, expiryTime, alpha, beta, nu, rho);
        } else {
            return unsafeSabrLogNormalVolatility(strike + shift, forward + shift, expiryTime, alpha, beta, nu, rho);
        }
    }

    public static double unsafeSabrVolatility(
            double strike,
            double forward,
            double expiryTime,
            double alpha,
            double beta,
            double nu,
            double rho,
            VolatilityType volatilityType) {
        if (volatilityType == Normal) {
            return unsafeSabrNormalVolatility(strike, forward, expiryTime, alpha, beta, nu, rho);
        } else {
            return unsafeSabrLogNormalVolatility(strike, forward, expiryTime, alpha, beta, nu, rho);
        }
    }

    public static double unsafeSabrVolatility(double strike,
                                              double forward,
                                              double expiryTime,
                                              double alpha,
                                              double beta,
                                              double nu,
                                              double rho) {
        return unsafeSabrVolatility(strike, forward, expiryTime, alpha, beta, nu, rho,
                VolatilityType.ShiftedLognormal);
    }

    public static double sabrVolatility(double strike,
                                        double forward,
                                        double expiryTime,
                                        double alpha,
                                        double beta,
                                        double nu,
                                        double rho,
                                        VolatilityType volatilityType) {
        QL_REQUIRE(strike > 0.0, "strike must be positive: "
                + strike + " not allowed");
        QL_REQUIRE(forward > 0.0, "at the money forward rate must be "
                + "positive: " + forward + " not allowed");
        QL_REQUIRE(expiryTime >= 0.0, "expiry time must be non-negative: "
                + expiryTime + " not allowed");
        validateSabrParameters(alpha, beta, nu, rho);
        return unsafeSabrVolatility(strike, forward, expiryTime, alpha, beta, nu, rho,
                volatilityType);
    }

    public static double sabrVolatility(double strike,
                                        double forward,
                                        double expiryTime,
                                        double alpha,
                                        double beta,
                                        double nu,
                                        double rho) {
        return sabrVolatility(strike, forward, expiryTime, alpha, beta, nu, rho,
                VolatilityType.ShiftedLognormal);
    }

    public static double unsafeSabrNormalVolatility(
            double strike, double forward, double expiryTime, double alpha, double beta, double nu, double rho) {
        final double oneMinusBeta = 1.0 - beta;
        final double minusBeta = -1.0 * beta;
        final double A = pow(forward * strike, oneMinusBeta);
        final double sqrtA = sqrt(A);
        double logM;
        if (!close(forward, strike))
            logM = log(forward / strike);
        else {
            final double epsilon = (forward - strike) / strike;
            logM = epsilon - .5 * epsilon * epsilon;
        }
        final double z = (nu / alpha) * sqrtA * logM;
        final double B = 1.0 - 2.0 * rho * z + z * z;
        final double C = oneMinusBeta * oneMinusBeta * logM * logM;
        final double D = logM * logM;
        final double tmp = (sqrt(B) + z - rho) / (1.0 - rho);
        final double xx = log(tmp);
        final double E_1 = (1.0 + D / 24.0 + D * D / 1920.0);
        final double E_2 = (1.0 + C / 24.0 + C * C / 1920.0);
        final double E = E_1 / E_2;
        final double d = 1.0 + expiryTime * (minusBeta * (2 - beta) * alpha * alpha / (24.0 * A) +
                0.25 * rho * beta * nu * alpha / sqrtA +
                (2.0 - 3.0 * rho * rho) * (nu * nu / 24.0));

        double multiplier;
        // computations become precise enough if the square of z worth
        // slightly more than the precision machine (hence the m)
        final double m = 10;
        if (Math.abs(z * z) > QL_EPSILON * m)
            multiplier = z / xx;
        else {
            multiplier = 1.0 - 0.5 * rho * z - (3.0 * rho * rho - 2.0) * z * z / 12.0;
        }
        final double F = alpha * pow(forward * strike, beta / 2.0);

        return F * E * multiplier * d;
    }

    public static double unsafeSabrLogNormalVolatility(
            double strike,
            double forward,
            double expiryTime,
            double alpha,
            double beta,
            double nu,
            double rho) {
        final double oneMinusBeta = 1.0 - beta;
        final double A = pow(forward * strike, oneMinusBeta);
        final double sqrtA = sqrt(A);
        double logM;
        if (!close(forward, strike))
            logM = log(forward / strike);
        else {
            final double epsilon = (forward - strike) / strike;
            logM = epsilon - .5 * epsilon * epsilon;
        }
        final double z = (nu / alpha) * sqrtA * logM;
        final double B = 1.0 - 2.0 * rho * z + z * z;
        final double C = oneMinusBeta * oneMinusBeta * logM * logM;
        final double tmp = (sqrt(B) + z - rho) / (1.0 - rho);
        final double xx = log(tmp);
        final double D = sqrtA * (1.0 + C / 24.0 + C * C / 1920.0);
        final double d = 1.0 + expiryTime *
                (oneMinusBeta * oneMinusBeta * alpha * alpha / (24.0 * A)
                        + 0.25 * rho * beta * nu * alpha / sqrtA
                        + (2.0 - 3.0 * rho * rho) * (nu * nu / 24.0));

        double multiplier;
        // computations become precise enough if the square of z worth
        // slightly more than the precision machine (hence the m)
        final double m = 10;
        if (Math.abs(z * z) > QL_EPSILON * m)
            multiplier = z / xx;
        else {
            multiplier = 1.0 - 0.5 * rho * z - (3.0 * rho * rho - 2.0) * z * z / 12.0;
        }
        return (alpha / D) * multiplier * d;
    }

    private static final class SabrFlochKennedyVolatility {
        final double F, alpha, beta, nu, rho, t;

        SabrFlochKennedyVolatility(double F, double alpha, double beta,
                                   double nu, double rho, double t) {
            this.F = F;
            this.alpha = alpha;
            this.beta = beta;
            this.nu = nu;
            this.rho = rho;
            this.t = t;
        }

        double y(double k) {
            return -1.0 / (1.0 - beta) * (pow(F, 1 - beta) - pow(k, 1 - beta));
        }

        double Dint(double k) {
            return 1 / nu * log((sqrt(1 + 2 * rho * nu / alpha * y(k)
                    + squared(nu / alpha * y(k)))
                    - rho - nu / alpha * y(k)) / (1 - rho));
        }

        double D(double k) {
            return sqrt(alpha * alpha + 2 * alpha * rho * nu * y(k)
                    + squared(nu * y(k))) * pow(k, beta);
        }

        double omega0(double k) {
            return log(F / k) / Dint(k);
        }

        double value(double k) {
            final double m = F / k;
            if (m > 1.0025 || m < 0.9975) {
                return omega0(k) * (1 + 0.25 * rho * nu * alpha
                        * (pow(k, beta) - pow(F, beta)) / (k - F) * t)
                        - omega0(k) / squared(Dint(k)) * (log(
                        omega0(k)) + 0.5 * log((F * k / (D(F) * D(k))))) * t;
            } else {
                return taylorExpansion(k);
            }
        }

        double taylorExpansion(double k) {
            final double F2 = F * F;
            final double alpha2 = alpha * alpha;
            final double rho2 = rho * rho;
            return
                    (alpha * pow(F, -3 + beta) * (alpha2 * squared(-1 + beta) * pow(F, 2 * beta) * t + 6 * alpha * beta * nu * pow(F, 1 + beta) * rho * t +
                            F2 * (24 + nu * nu * (2 - 3 * rho2) * t))) / 24.0 +
                            (3 * alpha2 * alpha * pow(-1 + beta, 3) * pow(F, 3 * beta) * t +
                                    3 * alpha2 * (-1 + beta) * (-1 + 5 * beta) * nu * pow(F, 1 + 2 * beta) * rho * t + nu * F2 * F * rho * (24 + nu * nu * (-4 + 3 * rho2) * t) +
                                    alpha * pow(F, 2 + beta) * (24 * (-1 + beta) + nu * nu * (2 * (-1 + beta) + 3 * (1 + beta) * rho2) * t)) / (48.0 * F2 * F2) * (k - F) +
                            (pow(F, -5 - beta) * (alpha2 * alpha2 * pow(-1 + beta, 3) * (-209 + 119 * beta) * pow(F, 4 * beta) * t + 30 * alpha2 * alpha * (-1 + beta) * (9 + beta * (-37 + 18 * beta)) * nu * pow(F, 1 + 3 * beta) * rho * t -
                                    30 * alpha * nu * pow(F, 3 + beta) * rho * (24 + nu * nu * (-4 * (1 + beta) + 3 * (1 + 2 * beta) * rho2) * t) +
                                    10 * alpha2 * pow(F, 2 + 2 * beta) * (24 * (-4 + beta) * (-1 + beta) + nu * nu * (2 * (-1 + beta) * (-7 + 4 * beta) + 3 * (-4 + beta * (-7 + 5 * beta)) * rho2) * t) +
                                    nu * nu * F2 * F2 * (480 - 720 * rho2 + nu * nu * (-64 + 75 * rho2 * (4 - 3 * rho2)) * t))) / (2880 * alpha) * (k - F) * (k - F);
        }
    }

    public static double sabrFlochKennedyVolatility(double strike,
                                                    double forward,
                                                    double expiryTime,
                                                    double alpha,
                                                    double beta,
                                                    double nu,
                                                    double rho) {
        final SabrFlochKennedyVolatility v =
                new SabrFlochKennedyVolatility(forward, alpha, beta, nu, rho, expiryTime);

        return v.value(strike);
    }

    private static double sign(double x) {
        return x > 0.0 ? 1.0 : (x < 0.0 ? -1.0 : 0.0);
    }

    /**
     * Real roots of {@code a*x^3 + b*x^2 + c*x + d = 0}, following
     * boost::math::tools::cubic_roots.  The three values are returned in
     * non-decreasing order; if there is only one real root (or the
     * polynomial degenerates to a quadratic), the second and third entries
     * are NaN.
     */
    private static double[] cubicRoots(double a, double b, double c, double d) {
        final double nan = Double.NaN;
        if (a == 0.0) {
            if (b == 0.0) {
                if (c == 0.0) {
                    return new double[]{nan, nan, nan};
                }
                return new double[]{-d / c, nan, nan};
            }
            final double discriminant = c * c - 4.0 * b * d;
            if (discriminant < 0.0) {
                return new double[]{nan, nan, nan};
            }
            final double sqrtDisc = sqrt(discriminant);
            final double r1 = (-c - sqrtDisc) / (2.0 * b);
            final double r2 = (-c + sqrtDisc) / (2.0 * b);
            return new double[]{min(r1, r2), max(r1, r2), nan};
        }

        final double p = b / a;
        final double q = c / a;
        final double r = d / a;

        final double Q = (p * p - 3.0 * q) / 9.0;
        final double R = (2.0 * p * p * p - 9.0 * p * q + 27.0 * r) / 54.0;

        if (R * R < Q * Q * Q) {
            final double theta = acos(R / sqrt(Q * Q * Q));
            final double sqrtQ = sqrt(Q);
            final double[] roots = {
                    -2.0 * sqrtQ * cos(theta / 3.0) - p / 3.0,
                    -2.0 * sqrtQ * cos((theta + 2.0 * PI) / 3.0) - p / 3.0,
                    -2.0 * sqrtQ * cos((theta - 2.0 * PI) / 3.0) - p / 3.0
            };
            Arrays.sort(roots);
            return roots;
        } else if (R * R > Q * Q * Q) {
            final double sqrtTerm = sqrt(R * R - Q * Q * Q);
            final double A = -sign(R) * cbrt(abs(R) + sqrtTerm);
            final double B = A == 0.0 ? 0.0 : Q / A;
            return new double[]{A + B - p / 3.0, nan, nan};
        } else {
            // R*R == Q*Q*Q: at least two coincident roots
            if (R == 0.0) {
                final double root = -p / 3.0;
                return new double[]{root, root, root};
            }
            final double u = cbrt(R);
            final double[] roots = {
                    2.0 * u - p / 3.0,
                    -u - p / 3.0,
                    -u - p / 3.0
            };
            Arrays.sort(roots);
            return roots;
        }
    }

    private static double smallestPositiveRoot(double c1, double c2,
                                               double c3, double c4) {
        final double[] roots = cubicRoots(c1, c2, c3, c4);
        if (Double.isNaN(roots[2])) {
            // single root (or two equal ones), check that it's positive
            QL_REQUIRE(roots[0] > 0.0, "no positive root");
            return roots[0];
        } else {
            // three roots in non-decreasing order, return the first positive one
            QL_REQUIRE(roots[2] > 0.0, "no positive root");
            return roots[0] > 0.0 ? roots[0] : (roots[1] > 0.0 ? roots[1] : roots[2]);
        }
    }

    /**
     * Initial guess for SABR calibration; returns
     * {@code {alpha, beta, nu, rho}}.
     */
    public static double[] sabrGuess(double k_m, double vol_m,
                                     double k_0, double vol_0,
                                     double k_p, double vol_p,
                                     double forward,
                                     double expiryTime,
                                     double beta,
                                     double shift,
                                     VolatilityType volatilityType) {
        // same variable names as in the equations for ease of reference:
        final double f = forward, b = shift, T = expiryTime;

        // change to log-moneyness
        final double z_m = log((k_m + b) / (f + b));
        final double z_0 = log((k_0 + b) / (f + b));
        final double z_p = log((k_p + b) / (f + b));

        // calculate atm, skew, curvature
        final double w_m = 1 / ((z_m - z_0) * (z_m - z_p)); // eq. (42) in the paper
        final double w_0 = 1 / ((z_0 - z_m) * (z_0 - z_p)); // eq. (43)
        final double w_p = 1 / ((z_p - z_m) * (z_p - z_0)); // eq. (44)

        final double sigma_0 = z_0 * z_p * w_m * vol_m + z_m * z_p * w_0 * vol_0 + z_m * z_0 * w_p * vol_p;         // (39)
        final double sigma_1 = -(z_0 + z_p) * w_m * vol_m - (z_m + z_p) * w_0 * vol_0 - (z_m + z_0) * w_p * vol_p; // (40)
        final double sigma_2 = 2 * w_m * vol_m + 2 * w_0 * vol_0 + 2 * w_p * vol_p;                                 // (41)

        switch (volatilityType) {
            case ShiftedLognormal: {
                // equations (32)
                double alpha;
                final double nu2 =
                        3 * sigma_0 * sigma_2
                                - 0.5 * squared(1 - beta) * sigma_0 * sigma_0
                                + 1.5 * squared(2 * sigma_1 + (1 - beta) * sigma_0);
                final double nu, rho;
                if (nu2 > 0.0) {
                    nu = sqrt(nu2);
                    rho = (1 / nu) * (2 * sigma_1 + (1 - beta) * sigma_0);
                } else {
                    rho = sign(2 * sigma_1 + (1 - beta) * sigma_0);
                    nu = (1 / rho) * (2 * sigma_1 + (1 - beta) * sigma_0);
                }

                // coefficients of the polynomial in equation (33)
                final double c1 = squared(1 - beta) * T / (24 * pow(f + b, 2 - 2 * beta));
                final double c2 = rho * beta * nu * T / (4 * pow(f + b, 1 - beta));
                final double c3 = 1 + ((2 - 3 * rho * rho) / 24) * nu * nu * T;
                final double c4 = -sigma_0 * pow(f + b, 1 - beta);

                alpha = sigma_0 * pow(f + b, 1.0 - beta);
                try {
                    alpha = smallestPositiveRoot(c1, c2, c3, c4);
                } catch (IllegalArgumentException e) {
                    // keep the initial estimate, as in the official code
                }

                return new double[]{alpha, beta, nu, rho};
            }
            case Normal: {
                // equations (37)
                double alpha;
                final double nu2 = squared(1 / (f + b)) * (
                        3 * sigma_0 * sigma_2
                                - 0.5 * (beta * beta + beta) * (sigma_0 * sigma_0)
                                - 3 * sigma_0 * (sigma_1 - 0.5 * beta * sigma_0)
                                + 1.5 * squared(2 * sigma_1 - beta * sigma_0)
                );
                final double nu, rho;
                if (nu2 > 0.0) {
                    nu = sqrt(nu2);
                    rho = (1 / (nu * (f + b))) * (2 * sigma_1 - beta * sigma_0);
                } else {
                    rho = sign((1 / (f + b)) * (2 * sigma_1 - beta * sigma_0));
                    nu = (1 / (rho * (f + b))) * (2 * sigma_1 - beta * sigma_0);
                }

                // coefficients of the polynomial in equation (38)
                final double c1 = (beta * beta - 2 * beta) * T / (24 * pow(f + b, 2 - 2 * beta));
                final double c2 = rho * beta * nu * T / (4 * pow(f + b, 1 - beta));
                final double c3 = 1 + ((2 - 3 * rho * rho) / 24) * nu * nu * T;
                final double c4 = -sigma_0 * pow(f + b, -beta);

                alpha = sigma_0 * pow(f + b, -beta);
                try {
                    alpha = smallestPositiveRoot(c1, c2, c3, c4);
                } catch (IllegalArgumentException e) {
                    // keep the initial estimate, as in the official code
                }

                return new double[]{alpha, beta, nu, rho};
            }
            default:
                QL_FAIL("unknown volatility type: " + volatilityType);
                throw new IllegalStateException("unreachable");
        }
    }
}
