package jquant.pricingengines;

import jquant.math.distributions.CumulativeNormalDistribution;

import static jquant.math.CommonUtil.QL_REQUIRE;

public class BlackFormula {
    public static void checkParameters(double strike,
                                       double forward,
                                       double displacement)
    {
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
                                                    double displacement)
    {
        checkParameters(strike, forward, displacement);
        QL_REQUIRE(stdDev>=0.0,
                "stdDev (" + stdDev + ") must be non-negative");
        QL_REQUIRE(discount>0.0,
                "discount (" + discount + ") must be positive");

        forward = forward + displacement;
        strike = strike + displacement;

        if (stdDev==0.0 || strike==0.0)
            return 0.0;

        double d1 = Math.log(forward/strike)/stdDev + .5*stdDev;
        CumulativeNormalDistribution distribution = new CumulativeNormalDistribution();
        return discount * forward * distribution.derivative(d1);
    }
}
