package jquant.termstructures.volatility;

import static java.lang.Math.*;
import static jquant.math.CommonUtil.QL_REQUIRE;
import static jquant.math.MathUtils.QL_EPSILON;
import static jquant.math.MathUtils.close;
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
}
