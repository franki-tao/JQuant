package math.integrals;

import math.Function;
import math.Function2;
import math.Point;

public class TwoDimensionalIntegral {
    private Integrator integratorX_;
    private Integrator integratorY_;

    public TwoDimensionalIntegral(Integrator integratorX, Integrator integratorY) {
        this.integratorY_ = integratorY;
        this.integratorX_ = integratorX;
    }

    public double value(Function2 f, Point<Double> a, Point<Double> b) {
        Function f1 = new Function() {
            @Override
            public double value(double x) {
                return g(f, x, a.getSecond(), b.getSecond());
            }
        };
        return integratorX_.value(f1, a.getFirst(), b.getFirst());
    }

    private double g(Function2 f, double x, double a, double b) {
        Function f1 = new Function() {
            @Override
            public double value(double y) {
                return f.value(x, y);
            }
        };
        return integratorY_.value(f1, a, b);
    }
}
