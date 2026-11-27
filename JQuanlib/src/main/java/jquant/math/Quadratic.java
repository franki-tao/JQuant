package jquant.math;

/**
 * Official QuantLib {@code ql/math/quadratic.hpp/.cpp} port.
 *
 * <p>Quadratic formula helper used by the market-model alpha finder and max
 * homogeneity calibration.
 */
public class Quadratic {

    private final double a_;
    private final double b_;
    private final double c_;

    public Quadratic(double a, double b, double c) {
        a_ = a;
        b_ = b;
        c_ = c;
    }

    public double turningPoint() {
        return -b_ / (2.0 * a_);
    }

    public double valueAtTurningPoint() {
        return value(turningPoint());
    }

    public double value(double x) {
        return x * (x * a_ + b_) + c_;
    }

    public double discriminant() {
        return b_ * b_ - 4 * a_ * c_;
    }

    // return false if roots not real, and give turning point instead
    public boolean roots(ReferencePkg<Double> x, ReferencePkg<Double> y) {
        double d = discriminant();
        if (d < 0) {
            double tp = turningPoint();
            x.setT(tp);
            y.setT(tp);
            return false;
        }
        d = Math.sqrt(d);
        x.setT((-b_ - d) / (2 * a_));
        y.setT((-b_ + d) / (2 * a_));
        return true;
    }
}
