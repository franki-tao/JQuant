package math;

import java.util.List;

public abstract class Impl {
    public abstract void update();

    public abstract double xMin();

    public abstract double xMax();

    public abstract List<Double> xValues();

    public abstract List<Double> yValues();

    public abstract boolean isInRange(double r);

    public abstract double value(double v);

    public abstract double primitive(double v);

    public abstract double derivative(double v);

    public abstract double secondDerivative(double v);
}
