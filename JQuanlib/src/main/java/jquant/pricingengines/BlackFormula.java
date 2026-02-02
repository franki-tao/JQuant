package jquant.pricingengines;

import jquant.Option;
import jquant.math.distributions.CumulativeNormalDistribution;

import static jquant.math.CommonUtil.QL_REQUIRE;

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
}
