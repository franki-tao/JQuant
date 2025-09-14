package jquant.termstructures.volatility;

import jquant.math.AbcdMathFunction;

import static jquant.math.CommonUtil.QL_REQUIRE;
import static jquant.math.MathUtils.close;

public class AbcdFunction extends AbcdMathFunction {
    public AbcdFunction(double a, double b, double c, double d) {
        super(a, b, c, d);
    }

    //! maximum value of the volatility function
    public final double maximumVolatility() {
        return super.maximumValue();
    }

    //! volatility function value at time 0: \f[ f(0) \f]
    public final double shortTermVolatility() {
        return super.value(0.0);
    }

    //! volatility function value at time +inf: \f[ f(\inf) \f]
    public final double longTermVolatility() {
        return super.longTermValue();
    }

    /*! instantaneous covariance function at time t between T-fixing and
        S-fixing rates \f[ f(T-t)f(S-t) \f] */
    public final double covariance(double t, double T, double S) {
        return super.value(T - t) * super.value(S - t);
    }

    /*! integral of the instantaneous covariance function between
        time t1 and t2 for T-fixing and S-fixing rates
        \f[ \int_{t1}^{t2} f(T-t)f(S-t)dt \f] */
    public final double covariance(double t1, double t2, double T, double S) {
        QL_REQUIRE(t1 <= t2,
                "integrations bounds (" + t1 +
                        "," + t2 + ") are in reverse order");
        double cutOff = Math.min(S, T);
        if (t1 >= cutOff) {
            return 0.0;
        } else {
            cutOff = Math.min(t2, cutOff);
            return primitive(cutOff, T, S) - primitive(t1, T, S);
        }
    }

    /*! average volatility in [tMin,tMax] of T-fixing rate:
       \f[ \sqrt{ \frac{\int_{tMin}^{tMax} f^2(T-u)du}{tMax-tMin} } \f] */
    public final double volatility(double tMin, double tMax, double T) {
        if (tMax==tMin)
            return instantaneousVolatility(tMax, T);
        QL_REQUIRE(tMax>tMin, "tMax must be > tMin");
        return Math.sqrt(variance(tMin, tMax, T)/(tMax-tMin));
    }

    /*! variance between tMin and tMax of T-fixing rate:
        \f[ \frac{\int_{tMin}^{tMax} f^2(T-u)du}{tMax-tMin} \f] */
    public final double variance(double tMin, double tMax, double T) {
        return covariance(tMin, tMax, T, T);
    }


    // INSTANTANEOUS
        /*! instantaneous volatility at time t of the T-fixing rate:
            \f[ f(T-t) \f] */
    public final double instantaneousVolatility(double u, double T) {
        return Math.sqrt(instantaneousVariance(u, T));
    }

    /*! instantaneous variance at time t of T-fixing rate:
        \f[ f(T-t)f(T-t) \f] */
    public final double instantaneousVariance(double u, double T) {
        return instantaneousCovariance(u, T, T);
    }

    /*! instantaneous covariance at time t between T and S fixing rates:
        \f[ f(T-u)f(S-u) \f] */
    public final double instantaneousCovariance(double u, double T, double S) {
        return super.value(T-u)*super.value(S-u);
    }

    // PRIMITIVE
        /*! indefinite integral of the instantaneous covariance function at
            time t between T-fixing and S-fixing rates
            \f[ \int f(T-t)f(S-t)dt \f] */
    public final double primitive(double t, double T, double S) {
        if (T < t || S < t) return 0.0;

        if (close(c_, 0.0)) {
            double v = a_ + d_;
            return t * (v * v + v * b_ * S + v * b_ * T - v * b_ * t + b_ * b_ * S * T - 0.5 * b_ * b_ * t * (S + T) + b_ * b_ * t * t / 3.0);
        }

        double k1 = Math.exp(c_ * t), k2 = Math.exp(c_ * S), k3 = Math.exp(c_ * T);

        return (b_ * b_ * (-1 - 2 * c_ * c_ * S * T - c_ * (S + T)
                + k1 * k1 * (1 + c_ * (S + T - 2 * t) + 2 * c_ * c_ * (S - t) * (T - t)))
                + 2 * c_ * c_ * (2 * d_ * a_ * (k2 + k3) * (k1 - 1)
                + a_ * a_ * (k1 * k1 - 1) + 2 * c_ * d_ * d_ * k2 * k3 * t)
                + 2 * b_ * c_ * (a_ * (-1 - c_ * (S + T) + k1 * k1 * (1 + c_ * (S + T - 2 * t)))
                - 2 * d_ * (k3 * (1 + c_ * S) + k2 * (1 + c_ * T)
                - k1 * k3 * (1 + c_ * (S - t))
                - k1 * k2 * (1 + c_ * (T - t)))
        )
        ) / (4 * c_ * c_ * c_ * k2 * k3);
    }
}
