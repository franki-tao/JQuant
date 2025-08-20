package math.distributions;

import math.MathUtils;

import static math.MathUtils.*;

public class InverseCumulativeNormal {
    private double average = 0;
    private double sigma = 1;

    private final double a1_ = -3.969683028665376e+01;
    private final double a2_ = 2.209460984245205e+02;
    private final double a3_ = -2.759285104469687e+02;
    private final double a4_ = 1.383577518672690e+02;
    private final double a5_ = -3.066479806614716e+01;
    private final double a6_ = 2.506628277459239e+00;

    private final double b1_ = -5.447609879822406e+01;
    private final double b2_ = 1.615858368580409e+02;
    private final double b3_ = -1.556989798598866e+02;
    private final double b4_ = 6.680131188771972e+01;
    private final double b5_ = -1.328068155288572e+01;

    private final double c1_ = -7.784894002430293e-03;
    private final double c2_ = -3.223964580411365e-01;
    private final double c3_ = -2.400758277161838e+00;
    private final double c4_ = -2.549732539343734e+00;
    private final double c5_ = 4.374664141464968e+00;
    private final double c6_ = 2.938163982698783e+00;

    private final double d1_ = 7.784695709041462e-03;
    private final double d2_ = 3.224671290700398e-01;
    private final double d3_ = 2.445134137142996e+00;
    private final double d4_ = 3.754408661907416e+00;
    private final double x_low_ = 0.02425;
    private final double x_high_ = 1.0 - x_low_;

    public InverseCumulativeNormal(double average, double sigma) {
        this.average = average;
        this.sigma = sigma;
    }

    public InverseCumulativeNormal() {
        this.average = 0;
        this.sigma = 1;
    }

    public double value(double x) {
        return average + sigma * standard_value(x);
    }

    public double standard_value(double x) {
        double z;
        if (x < x_low_ || x_high_ < x) {
            z = tail_value(x);
        } else {
            z = x - 0.5;
            double r = z * z;
            z = (((((a1_ * r + a2_) * r + a3_) * r + a4_) * r + a5_) * r + a6_) * z /
                    (((((b1_ * r + b2_) * r + b3_) * r + b4_) * r + b5_) * r + 1.0);
        }

        return z;
    }

    private double tail_value(double x) {
        if (x <= 0.0 || x >= 1.0) {
            // try to recover if due to numerical error
            if (MathUtils.close_enough(x, 1.0)) {
                return QL_MAX_REAL; // largest value available
            } else if (Math.abs(x) < QL_EPSILON) {
                return QL_MIN_REAL; // largest negative value available
            } else {
                throw new IllegalArgumentException("undefined: must be 0 < x < 1");
            }
        }

        double z;
        if (x < x_low_) {
            // Rational approximation for the lower region 0<x<u_low
            z = Math.sqrt(-2.0 * Math.log(x));
            z = (((((c1_ * z + c2_) * z + c3_) * z + c4_) * z + c5_) * z + c6_) /
                    ((((d1_ * z + d2_) * z + d3_) * z + d4_) * z + 1.0);
        } else {
            // Rational approximation for the upper region u_high<x<1
            z = Math.sqrt(-2.0 * Math.log(1.0 - x));
            z = -(((((c1_ * z + c2_) * z + c3_) * z + c4_) * z + c5_) * z + c6_) /
                    ((((d1_ * z + d2_) * z + d3_) * z + d4_) * z + 1.0);
        }

        return z;
    }
}
