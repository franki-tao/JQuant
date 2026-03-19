package jquant.pricingengines;

import jquant.Option;
import jquant.math.Function;
import jquant.math.distributions.CumulativeNormalDistribution;
import jquant.math.distributions.NormalDistribution;
import jquant.math.solvers1d.NewtonSafe;

import static jquant.math.CommonUtil.QL_REQUIRE;
import static jquant.math.MathUtils.*;

public class BlackFormula {
    /*! Black 1976 formula
        \warning instead of volatility it uses standard deviation,
                 i.e. volatility*sqrt(timeToMaturity)
    */
    //default discount = 1.0, displacement = 0.0
    public static double blackFormula(Option.Type optionType,
                                      double strike,
                                      double forward,
                                      double stdDev,
                                      double discount,
                                      double displacement) {
        checkParameters(strike, forward, displacement);
        QL_REQUIRE(stdDev >= 0.0,
                "stdDev (" + stdDev + ") must be non-negative");
        QL_REQUIRE(discount > 0.0,
                "discount (" + discount + ") must be positive");

        int sign = optionType == Option.Type.Put ? 1 : -1;

        if (stdDev == 0.0)
            return Math.max((forward - strike) * sign, (0.0)) * discount;

        forward = forward + displacement;
        strike = strike + displacement;

        // since displacement is non-negative strike==0 iff displacement==0
        // so returning forward*discount is OK
        if (strike == 0.0)
            return (optionType == Option.Type.Call ? (forward * discount) : 0.0);

        double d1 = Math.log(forward / strike) / stdDev + 0.5 * stdDev;
        double d2 = d1 - stdDev;
        CumulativeNormalDistribution phi = new CumulativeNormalDistribution();
        double nd1 = phi.value(sign * d1);
        double nd2 = phi.value(sign * d2);
        double result = discount * sign * (forward * nd1 - strike * nd2);
        QL_REQUIRE(result >= 0.0,
                "negative value (" + result + ") for " +
                        stdDev + " stdDev, " +
                        optionType + " option, " +
                        strike + " strike , " +
                        forward + " forward");
        return result;
    }

    public static double bachelierBlackFormula(Option.Type optionType,
                                               double strike,
                                               double forward,
                                               double stdDev,
                                               double discount) {
        QL_REQUIRE(stdDev >= 0.0,
                "stdDev (" + stdDev + ") must be non-negative");
        QL_REQUIRE(discount > 0.0,
                "discount (" + discount + ") must be positive");
        double d = (forward - strike) * (optionType == Option.Type.Put ? -1 : 1), h = d / stdDev;
        if (stdDev == 0.0)
            return discount * Math.max(d, 0.0);
        CumulativeNormalDistribution phi = new CumulativeNormalDistribution();
        double result = discount * (stdDev * phi.derivative(h) + d * phi.value(h));
        QL_REQUIRE(result >= 0.0,
                "negative value (" + result + ") for " +
                        stdDev + " stdDev, " +
                        optionType + " option, " +
                        strike + " strike , " +
                        forward + " forward");
        return result;
    }

    /**
     * ! Black 1976 formula for  derivative with respect to implied vol, this
     * is basically the vega, but if you want 1% change multiply by 1%
     * discount = 1.0
     * displacement = 0.0
     */
    public static double blackFormulaVolDerivative(double strike,
                                                   double forward,
                                                   double stdDev,
                                                   double expiry,
                                                   double discount,
                                                   double displacement) {
        return blackFormulaStdDevDerivative(strike,
                forward,
                stdDev,
                discount,
                displacement) * Math.sqrt(expiry);
    }

    public static double blackFormulaImpliedStdDevApproximation(Option.Type optionType,
                                                                double strike,
                                                                double forward,
                                                                double blackPrice,
                                                                double discount,
                                                                double displacement) {
        checkParameters(strike, forward, displacement);
        QL_REQUIRE(blackPrice >= 0.0,
                "blackPrice (" + blackPrice + ") must be non-negative");
        QL_REQUIRE(discount > 0.0,
                "discount (" + discount + ") must be positive");

        double stdDev;
        forward = forward + displacement;
        strike = strike + displacement;
        if (strike == forward)
            // Brenner-Subrahmanyan (1988) and Feinstein (1988) ATM approx.
            stdDev = blackPrice / discount * Math.sqrt(2.0 * M_PI) / forward;
        else {
            // Corrado and Miller extended moneyness approximation
            double moneynessDelta = (optionType == Option.Type.Put ? -1 : 1) * (forward - strike);
            double moneynessDelta_2 = moneynessDelta / 2.0;
            double temp = blackPrice / discount - moneynessDelta_2;
            double moneynessDelta_PI = moneynessDelta * moneynessDelta / M_PI;
            double temp2 = temp * temp - moneynessDelta_PI;
            if (temp2 < 0.0) // approximation breaks down, 2 alternatives:
                // 1. zero it
                temp2 = 0.0;
            // 2. Manaster-Koehler (1982) efficient Newton-Raphson seed
            //return std::fabs(std::log(forward/strike))*std::sqrt(2.0);
            temp2 = Math.sqrt(temp2);
            temp += temp2;
            temp *= Math.sqrt(2.0 * M_PI);
            stdDev = temp / (forward + strike);
        }
        QL_REQUIRE(stdDev >= 0.0,
                "stdDev (" + stdDev + ") must be non-negative");
        return stdDev;
    }

    /*! Black 1976 implied standard deviation,
        i.e. volatility*sqrt(timeToMaturity)
    */
    public static double blackFormulaImpliedStdDev(Option.Type optionType,
                                                   double strike,
                                                   double forward,
                                                   double blackPrice,
                                                   double discount,
                                                   double displacement,
                                                   double guess, // Nan
                                                   double accuracy, // 1e-6
                                                   int maxIterations // 100
    ) {
        checkParameters(strike, forward, displacement);

        QL_REQUIRE(discount > 0.0,
                "discount (" + discount + ") must be positive");

        QL_REQUIRE(blackPrice >= 0.0,
                "option price (" + blackPrice + ") must be non-negative");
        // check the price of the "other" option implied by put-call paity
        double otherOptionPrice = blackPrice - (optionType == Option.Type.Put ? -1 : 1) * (forward - strike) * discount;
        QL_REQUIRE(otherOptionPrice >= 0.0,
                " price (" + otherOptionPrice +
                        ") implied by put-call parity. No solution exists for " +
                        optionType + " strike " + strike +
                        ", forward " + forward +
                        ", price " + blackPrice +
                        ", deflator " + discount);

        // solve for the out-of-the-money option which has
        // greater vega/price ratio, i.e.
        // it is numerically more robust for implied vol calculations
        if (optionType == Option.Type.Put && strike > forward) {
            optionType = Option.Type.Call;
            blackPrice = otherOptionPrice;
        }
        if (optionType == Option.Type.Call && strike < forward) {
            optionType = Option.Type.Put;
            blackPrice = otherOptionPrice;
        }

        strike = strike + displacement;
        forward = forward + displacement;

        if (Double.isNaN(guess))
            guess = blackFormulaImpliedStdDevApproximation(
                    optionType, strike, forward, blackPrice, discount, displacement);
        else
            QL_REQUIRE(guess >= 0.0,
                    "stdDev guess (" + guess + ") must be non-negative");
        BlackImpliedStdDevHelper f = new BlackImpliedStdDevHelper(optionType, strike, forward,
                blackPrice / discount, 0.0);
        NewtonSafe solver = new NewtonSafe();
        solver.setMaxEvaluations(maxIterations);
        double minSdtDev = 0.0, maxStdDev = 24.0; // 24 = 300% * sqrt(60)
        double stdDev = solver.solve(f, accuracy, guess, minSdtDev, maxStdDev);
        QL_REQUIRE(stdDev >= 0.0,
                "stdDev (" + stdDev + ") must be non-negative");
        return stdDev;
    }

    public static double blackFormulaStdDevSecondDerivative(double strike,
                                                            double forward,
                                                            double stdDev,
                                                            double discount,
                                                            double displacement) {
        checkParameters(strike, forward, displacement);
        QL_REQUIRE(stdDev >= 0.0,
                "stdDev (" + stdDev + ") must be non-negative");
        QL_REQUIRE(discount > 0.0,
                "discount (" + discount + ") must be positive");

        forward = forward + displacement;
        strike = strike + displacement;

        if (stdDev == 0.0 || strike == 0.0)
            return 0.0;

        double d1 = Math.log(forward / strike) / stdDev + .5 * stdDev;
        double d1p = -Math.log(forward / strike) / (stdDev * stdDev) + .5;
        return discount * forward *
                new NormalDistribution().derivative(d1) * d1p;
    }

    public static double blackFormulaImpliedStdDevChambers(Option.Type optionType,
                                                           double strike,
                                                           double forward,
                                                           double blackPrice,
                                                           double blackAtmPrice,
                                                           double discount,
                                                           double displacement) {
        checkParameters(strike, forward, displacement);
        QL_REQUIRE(blackPrice >= 0.0,
                "blackPrice (" + blackPrice + ") must be non-negative");
        QL_REQUIRE(blackAtmPrice >= 0.0, "blackAtmPrice ("
                + blackAtmPrice
                + ") must be non-negative");
        QL_REQUIRE(discount > 0.0, "discount (" + discount
                + ") must be positive");

        double stdDev;

        forward = forward + displacement;
        strike = strike + displacement;
        blackPrice /= discount;
        blackAtmPrice /= discount;

        double s0 = M_SQRT2 * M_SQRTPI * blackAtmPrice /
                forward; // Brenner-Subrahmanyam formula
        double priceAtmVol =
                blackFormula(optionType, strike, forward, s0, 1.0, 0.0);
        double dc = blackPrice - priceAtmVol;

        if (close(dc, 0.0)) {
            stdDev = s0;
        } else {
            double d1 =
                    blackFormulaStdDevDerivative(strike, forward, s0, 1.0, 0.0);
            double d2 = blackFormulaStdDevSecondDerivative(strike, forward, s0,
                    1.0, 0.0);
            double ds = 0.0;
            double tmp = d1 * d1 + 2.0 * d2 * dc;
            if (Math.abs(d2) > 1E-10 && tmp >= 0.0)
                ds = (-d1 + Math.sqrt(tmp)) / d2; // second order approximation
            else if (Math.abs(d1) > 1E-10)
                ds = dc / d1; // first order approximation
            stdDev = s0 + ds;
        }

        QL_REQUIRE(stdDev >= 0.0, "stdDev (" + stdDev
                + ") must be non-negative");
        return stdDev;
    }

    public static double phi(final double x) {
        return new org.apache.commons.math3.distribution.NormalDistribution().density(x);
    }

    public static double Phi(double x) {
        return new org.apache.commons.math3.distribution.NormalDistribution().cumulativeProbability(x);
    }

    public static double PhiTilde(final double x) {
        return Phi(x) + phi(x) / x;
    }

    public static double inversePhiTilde(final double PhiTildeStar) {
        QL_REQUIRE(PhiTildeStar < 0.0,
                "inversePhiTilde(" + PhiTildeStar + "): negative argument required");
        double xbar;
        if (PhiTildeStar < -0.001882039271) {
            double g = 1.0 / (PhiTildeStar - 0.5);
            double xibar =
                    (0.032114372355 -
                            g * g *
                                    (0.016969777977 - g * g * (2.6207332461E-3 - 9.6066952861E-5 * g * g))) /
                            (1.0 -
                                    g * g * (0.6635646938 - g * g * (0.14528712196 - 0.010472855461 * g * g)));
            xbar = g * (0.3989422804014326 + xibar * g * g);
        } else {
            double h = Math.sqrt(-Math.log(-PhiTildeStar));
            xbar =
                    (9.4883409779 - h * (9.6320903635 - h * (0.58556997323 + 2.1464093351 * h))) /
                            (1.0 - h * (0.65174820867 + h * (1.5120247828 + 6.6437847132E-5 * h)));
        }
        double q = (PhiTilde(xbar) - PhiTildeStar) / phi(xbar);
        return xbar +
                3.0 * q * xbar * xbar * (2.0 - q * xbar * (2.0 + xbar * xbar)) /
                        (6.0 + q * xbar *
                                (-12.0 +
                                        xbar * (6.0 * q + xbar * (-6.0 + q * xbar * (3.0 + xbar * xbar)))));
    }

    /*! Exact Bachelier implied volatility

        It is calculated using the analytic implied volatility formula
        of Jaeckel (2017), "Implied Normal Volatility"
    */
    public static double bachelierBlackFormulaImpliedVol(Option.Type optionType,
                                                         double strike,
                                                         double forward,
                                                         double tte,
                                                         double bachelierPrice,
                                                         double discount // 1.0
    ) {

        double theta = optionType == Option.Type.Call ? 1.0 : -1.0;

        // compound bechelierPrice, so that effectively discount = 1

        bachelierPrice /= discount;

        // handle case strike = forward

        if (close_enough(strike, forward)) {
            return bachelierPrice / (Math.sqrt(tte) * phi(0.0));
        }

        // handle case strike != forward

        double timeValue = bachelierPrice - Math.max(theta * (forward - strike), 0.0);

        if (close_enough(timeValue, 0.0))
            return 0.0;

        QL_REQUIRE(timeValue > 0.0, "bachelierBlackFormulaImpliedVolExact");

        double PhiTildeStar = -Math.abs(timeValue / (strike - forward));
        double xstar = inversePhiTilde(PhiTildeStar);

        return Math.abs((strike - forward) / (xstar * Math.sqrt(tte)));
    }

    public static void checkParameters(double strike,
                                       double forward,
                                       double displacement) {
        QL_REQUIRE(displacement >= 0.0, "displacement ("
                + displacement
                + ") must be non-negative");
        QL_REQUIRE(strike + displacement >= 0.0,
                "strike + displacement (" + strike + " + " + displacement
                        + ") must be non-negative");
        QL_REQUIRE(forward + displacement > 0.0, "forward + displacement ("
                + forward + " + "
                + displacement
                + ") must be positive");
    }

    public static double blackFormulaStdDevDerivative(double strike,
                                                      double forward,
                                                      double stdDev,
                                                      double discount,
                                                      double displacement) {
        checkParameters(strike, forward, displacement);
        QL_REQUIRE(stdDev >= 0.0,
                "stdDev (" + stdDev + ") must be non-negative");
        QL_REQUIRE(discount > 0.0,
                "discount (" + discount + ") must be positive");

        forward = forward + displacement;
        strike = strike + displacement;

        if (stdDev == 0.0 || strike == 0.0)
            return 0.0;

        double d1 = Math.log(forward / strike) / stdDev + .5 * stdDev;
        CumulativeNormalDistribution distribution = new CumulativeNormalDistribution();
        return discount * forward * distribution.derivative(d1);
    }

    public static final class BlackImpliedStdDevHelper implements Function {
        private double halfOptionType_;
        private double signedStrike_, signedForward_;
        private double undiscountedBlackPrice_, signedMoneyness_;
        private CumulativeNormalDistribution N_;

        // displacement = 0.0
        public BlackImpliedStdDevHelper(Option.Type optionType,
                                        double strike,
                                        double forward,
                                        double undiscountedBlackPrice,
                                        double displacement) {
            halfOptionType_ = 0.5 * (optionType == Option.Type.Put ? -1 : 1);
            signedStrike_ = (optionType == Option.Type.Put ? -1 : 1) * (strike + displacement);
            signedForward_ = (optionType == Option.Type.Put ? -1 : 1) * (forward + displacement);
            undiscountedBlackPrice_ = undiscountedBlackPrice;
            N_ = new CumulativeNormalDistribution();
            checkParameters(strike, forward, displacement);
            QL_REQUIRE(undiscountedBlackPrice >= 0.0,
                    "undiscounted Black price (" +
                            undiscountedBlackPrice + ") must be non-negative");
            signedMoneyness_ = (optionType == Option.Type.Put ? -1 : 1) *
                    Math.log((forward + displacement) / (strike + displacement));
        }

        @Override
        public double value(double stdDev) {
            QL_REQUIRE(stdDev >= 0.0,
                    "stdDev (" + stdDev + ") must be non-negative");
            if (stdDev == 0.0)
                return Math.max(signedForward_ - signedStrike_, (0.0))
                        - undiscountedBlackPrice_;
            double temp = halfOptionType_ * stdDev;
            double d = signedMoneyness_ / stdDev;
            double signedD1 = d + temp;
            double signedD2 = d - temp;
            double result = signedForward_ * N_.value(signedD1)
                    - signedStrike_ * N_.value(signedD2);
            // numerical inaccuracies can yield a negative answer
            return Math.max((0.0), result) - undiscountedBlackPrice_;
        }

        @Override
        public double derivative(double stdDev) {
            QL_REQUIRE(stdDev >= 0.0,
                    "stdDev (" + stdDev + ") must be non-negative");
            double signedD1 = signedMoneyness_ / stdDev + halfOptionType_ * stdDev;
            return signedForward_ * N_.derivative(signedD1);
        }
    }
}
