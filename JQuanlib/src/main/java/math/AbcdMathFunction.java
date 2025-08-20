package math;

import static math.CommonUtil.QL_REQUIRE;

public class AbcdMathFunction {
    public static void validate(double a,
                                double b,
                                double c,
                                double d) {
        QL_REQUIRE(c > 0, "c (" + c + ") must be positive");
        QL_REQUIRE(d >= 0, "d (" + d + ") must be non negative");
        QL_REQUIRE(a + d >= 0,
                "a+d (" + a + "+" + d + ") must be non negative");

        if (b >= 0.0)
            return;

        // the one and only stationary point...
        double zeroFirstDerivative = 1.0 / c - a / b;
        if (zeroFirstDerivative >= 0.0) {
            // ... is a minimum
            // must be abcd(zeroFirstDerivative)>=0
            QL_REQUIRE(b >= -(d * c) / Math.exp(c * a / b - 1.0),
                    "b (" + b + ") less than " +
                            -(d * c) / Math.exp(c * a / b - 1.0) + ": negative function" +
                            " value at stationary point " + zeroFirstDerivative);
        }
    }
}
