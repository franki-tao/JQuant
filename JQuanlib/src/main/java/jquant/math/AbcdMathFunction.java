package jquant.math;

import java.util.List;

import static jquant.math.CommonUtil.QL_REQUIRE;
import static jquant.math.MathUtils.QL_MAX_REAL;

//! %Abcd functional form
    /*! \f[ f(t) = [ a + b*t ] e^{-c*t} + d \f]
        following Rebonato's notation. */
public class AbcdMathFunction {

    /* 默认
    Real a = 0.002,
    Real b = 0.001,
    Real c = 0.16,
    Real d = 0.0005

     */
    public AbcdMathFunction(double aa, double bb, double cc, double dd) {
        a_ = aa;
        b_ = bb;
        c_ = cc;
        d_ = dd;
        abcd_ = CommonUtil.ArrayInit(4);
        dabcd_ = CommonUtil.ArrayInit(4);
        abcd_.set(0, a_);
        abcd_.set(1, b_);
        abcd_.set(2, c_);
        abcd_.set(3, d_);
        initialize_();
    }
    public AbcdMathFunction() {
        a_ = 0.002;
        b_ = 0.001;
        c_ = 0.16;
        d_ = 0.0005;
        abcd_ = CommonUtil.ArrayInit(4);
        dabcd_ = CommonUtil.ArrayInit(4);
        abcd_.set(0, a_);
        abcd_.set(1, b_);
        abcd_.set(2, c_);
        abcd_.set(3, d_);
        initialize_();
    }


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

    //! function value at time t: \f[ f(t) \f]
    public double value(double t) {
        //return (a_ + b_*t)*std::exp(-c_*t) + d_;
        return t < 0 ? 0.0 : ((a_ + b_ * t) * Math.exp(-c_ * t) + d_);
    }

    //! time at which the function reaches maximum (if any)
    public double maximumLocation() {
        if (b_ == 0.0) {
            if (a_ >= 0.0)
                return 0.0;
            else
                return QL_MAX_REAL;
        }

        // stationary point
        // TODO check if minimum
        // TODO check if maximum at +inf
        double zeroFirstDerivative = 1.0 / c_ - a_ / b_;
        return (Math.max(zeroFirstDerivative, 0.0));
    }

    //! maximum value of the function
    public double maximumValue() {
        if (b_ == 0.0 || a_ <= 0.0)
            return d_;
        return maximumLocation();
    }

    //! function value at time +inf: \f[ f(\inf) \f]
    public double longTermValue() {
        return d_;
    }

    /*! first derivative of the function at time t
        \f[ f'(t) = [ (b-c*a) + (-c*b)*t) ] e^{-c*t} \f] */
    public final double derivative(double t) {
        return t < 0 ? 0.0 : ((da_ + db_ * t) * Math.exp(-c_ * t));
    }

    /*! indefinite integral of the function at time t
        \f[ \int f(t)dt = [ (-a/c-b/c^2) + (-b/c)*t ] e^{-c*t} + d*t \f] */
    public final double primitive(double t) {
        //return (pa_ + pb_*t)*std::exp(-c_*t) + d_*t + K_;
        return t < 0 ? 0.0 : ((pa_ + pb_ * t) * Math.exp(-c_ * t) + d_ * t + K_);
    }

    /*! definite integral of the function between t1 and t2
        \f[ \int_{t1}^{t2} f(t)dt \f] */
    public final double definiteIntegral(double t1, double t2) {
        return primitive(t2) - primitive(t1);
    }

    /*! Inspectors */
    public final double a() {
        return a_;
    }

    public final double b() {
        return b_;
    }

    public final double c() {
        return c_;
    }

    public final double d() {
        return d_;
    }

    public List<Double> coefficients() {
        return abcd_;
    }

    public List<Double> derivativeCoefficients() {
        return dabcd_;
    }
    // the primitive is not abcd

    /*! coefficients of a AbcdMathFunction defined as definite
        integral on a rolling window of length tau, with tau = t2-t */
    public List<Double> definiteIntegralCoefficients(double t,
                                                     double t2) {
        double dt = t2 - t;
        double expcdt = Math.exp(-c_ * dt);
        List<Double> result = CommonUtil.ArrayInit(4);
        result.set(0, diacplusbcc_ - (diacplusbcc_ + dibc_ * dt) * expcdt);
        result.set(1, dibc_ * (1.0 - expcdt));
        result.set(2, c_);
        result.set(3, d_ * dt);
        return result;
    }

    /*! coefficients of a AbcdMathFunction defined as definite
        derivative on a rolling window of length tau, with tau = t2-t */
    public List<Double> definiteDerivativeCoefficients(double t,
                                                       double t2) {
        double dt = t2 - t;
        double expcdt = Math.exp(-c_*dt);
        List<Double> result = CommonUtil.ArrayInit(4);
        result.set(1, b_*c_/(1.0-expcdt));
        result.set(0, a_*c_ - b_ + result.get(1)*dt*expcdt);
        result.set(0, result.get(0)/(1.0-expcdt));
        result.set(2, c_);
        result.set(3, d_/dt);
        return result;
    }

    protected double a_, b_, c_, d_;

    private void initialize_() {
        validate(a_, b_, c_, d_);
        da_ = b_ - c_ * a_;
        db_ = -c_ * b_;
        dabcd_.set(0, da_);
        dabcd_.set(1, db_);
        dabcd_.set(2, c_);
        dabcd_.set(3, 0d);

        pa_ = -(a_ + b_ / c_) / c_;
        pb_ = -b_ / c_;
        K_ = 0.0;

        dibc_ = b_ / c_;
        diacplusbcc_ = a_ / c_ + dibc_ / c_;
    }

    private List<Double> abcd_;
    private List<Double> dabcd_;
    private double da_, db_;
    private double pa_, pb_, K_;

    private double dibc_, diacplusbcc_;
}
