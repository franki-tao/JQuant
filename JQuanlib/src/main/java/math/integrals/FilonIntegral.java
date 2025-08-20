package math.integrals;

import math.Function;

import java.util.ArrayList;
import java.util.List;

import static java.lang.Math.sin;
import static math.CommonUtil.*;
import static math.MathUtils.NULL_REAL;
import static math.MathUtils.squared;
import static math.integrals.FilonIntegral.Type.Cosine;
import static math.integrals.FilonIntegral.Type.Sine;

//! Integral of a one-dimensional function
    /*! Given a number \f$ N \f$ of intervals, the integral of
        a function \f$ f \f$ between \f$ a \f$ and \f$ b \f$ is
        calculated by means of Filon's sine and cosine integrals
    */

/*! References:
    Abramowitz, M. and Stegun, I. A. (Eds.).
    Handbook of Mathematical Functions with Formulas, Graphs,
    and Mathematical Tables, 9th printing. New York: Dover,
    pp. 890-891, 1972.

    \test the correctness of the result is tested by checking it
          against known good values.
*/
public class FilonIntegral extends Integrator {
    public enum Type {
        Sine,
        Cosine;
    }

    private Type type_;
    private double t_;
    private int intervals_;
    private int n_;

    public FilonIntegral(Type type, double t, int intervals) {
        super(NULL_REAL, intervals);
        type_ = type;
        t_ = t;
        intervals_ = intervals;
        n_ = intervals / 2;
        QL_REQUIRE(intervals_ % 2 == 0, "number of intervals must be even");

    }

    @Override
    protected double integrate(Function f, double a, double b) {
        final double h = (b - a) / (2 * n_);
        List<Double> x = ArrayT(2 * n_ + 1, a, h);
        final double theta = t_ * h;
        final double theta2 = theta * theta;
        final double theta3 = theta2 * theta;

        final double alpha = 1 / theta + sin(2 * theta) / (2 * theta2)
                - 2 * squared(sin(theta)) / theta3;
        final double beta = 2 * ((1 + squared(Math.cos(theta))) / theta2
                - sin(2 * theta) / theta3);
        final double gamma = 4 * (sin(theta) / theta3 - Math.cos(theta) / theta2);

        List<Double> v = new ArrayList<>();
        for (double i : x) {
            v.add(f.value(i));
        }

        Function f1, f2;
        switch (type_) {
            case Cosine:
                f1 = new Function() {
                    @Override
                    public double value(double x) {
                        return Math.sin(x);
                    }
                };
                f2 = new Function() {
                    @Override
                    public double value(double x) {
                        return Math.cos(x);
                    }
                };
                break;
            case Sine:
                f1 = new Function() {
                    @Override
                    public double value(double x) {
                        return Math.cos(x);
                    }
                };
                f2 = new Function() {
                    @Override
                    public double value(double x) {
                        return Math.sin(x);
                    }
                };
                break;
            default:
                QL_FAIL("unknown integration type");
                throw new IllegalArgumentException("error type");
        }
        double c_2n_1 = 0.0;
        double c_2n = v.get(0) * f2.value(t_ * a)
                - 0.5 * (v.get(2 * n_) * f2.value(t_ * b) + v.get(0) * f2.value(t_ * a));

        for (int i = 1; i <= n_; ++i) {
            c_2n += v.get(2 * i) * f2.value(t_ * x.get(2 * i));
            c_2n_1 += v.get(2 * i - 1) * f2.value(t_ * x.get(2 * i - 1));
        }

        return h * (alpha * (v.get(2 * n_) * f1.value(t_ * x.get(2 * n_)) - v.get(0) * f1.value(t_ * x.get(0)))
                * ((type_ == Cosine) ? 1.0 : -1.0)
                + beta * c_2n + gamma * c_2n_1);
    }


    public static void main(String[] args) {
        FilonIntegral integral = new FilonIntegral(Sine, 1.5, 6);
        System.out.println(integral.value(new Function() {
            @Override
            public double value(double x) {
                return x * x;
            }
        }, 0, 1));
    }
}
