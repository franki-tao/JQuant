package math.distributions;

import math.Factorial;

import static math.CommonUtil.QL_REQUIRE;
import static math.MathUtils.QL_MAX_REAL;

public class InverseCumulativePoisson {
    private double lambda_;

    public InverseCumulativePoisson(double lambda) {
        QL_REQUIRE(lambda > 0.0, "lambda must be positive");
        this.lambda_ = lambda;
    }

    public InverseCumulativePoisson() {
        lambda_ = 1;
    }

    public double value(double x) {
        QL_REQUIRE(x >= 0.0 && x <= 1.0,
                "Inverse cumulative Poisson distribution is "+
                "only defined on the interval [0,1]");

        if (x == 1.0)
            return QL_MAX_REAL;

        double sum = 0.0;
        int index = 0;
        while (x > sum) {
            sum += calcSummand(index);
            index++;
        }

        return (double) (index-1);
    }

    private double calcSummand(int index) {
        return Math.exp(-lambda_) * Math.pow(lambda_, index) /
        Factorial.get(index);
    }
}
