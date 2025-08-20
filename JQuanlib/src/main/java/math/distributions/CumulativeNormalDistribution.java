package math.distributions;

import math.ErrorFunction;

import static math.MathUtils.*;

public class CumulativeNormalDistribution {
    /*
    Real average_, sigma_;
        NormalDistribution gaussian_;
        ErrorFunction errorFunction_;
     */
    private double average_;
    private double sigma_;

    private NormalDistribution gaussian_;

    private ErrorFunction errorFunction_;

    public CumulativeNormalDistribution() {
        average_ = 0;
        sigma_ = 1;
        gaussian_ = new NormalDistribution();
        errorFunction_ = new ErrorFunction();
    }

    public CumulativeNormalDistribution(double average, double sigma) {
        if (sigma <= 0) {
            throw new IllegalArgumentException("sigma must be greater than 0.0.");
        }
        this.sigma_ = sigma;
        this.average_ = average;
        gaussian_ = new NormalDistribution();
        errorFunction_ = new ErrorFunction();
    }

    public double value(double z) {
        //QL_REQUIRE(!(z >= average_ && 2.0*average_-z > average_),
        //           "not a real number. ");
        z = (z - average_) / sigma_;

        double result = 0.5 * ( 1.0 + errorFunction_.value( z*M_SQRT_2 ) );
        if (result<=1e-8) { //todo: investigate the threshold level
            // Asymptotic expansion for very negative z following (26.2.12)
            // on page 408 in M. Abramowitz and A. Stegun,
            // Pocketbook of Mathematical Functions, ISBN 3-87144818-4.
            double sum=1.0, zsqr=z*z, i=1.0, g=1.0, x, y,
                    a=QL_MAX_REAL, lasta;
            do {
                lasta=a;
                x = (4.0*i-3.0)/zsqr;
                y = x*((4.0*i-1)/zsqr);
                a = g*(x-y);
                sum -= a;
                g *= y;
                ++i;
                a = Math.abs(a);
            } while (lasta>a && a>=Math.abs(sum*QL_EPSILON));
            result = -gaussian_.value(z)/z*sum;
        }
        return result;
    }

    public double derivative(double x) {
        double xn = (x - average_) / sigma_;
        return gaussian_.value(xn) / sigma_;
    }
}
