package math.interpolations.impl;

public class EverywhereConstantHelper implements SectionHelper {

    private double value_;
    private double prevPrimitive_;
    private double xPrev_;

    public EverywhereConstantHelper(double value, double prevPrimitive, double xPrev) {
        value_ = value;
        prevPrimitive_ = prevPrimitive;
        xPrev_ = xPrev;
    }

    @Override
    public double value(double x) {
        return value_;
    }

    @Override
    public double primitive(double x) {
        return prevPrimitive_ + (x - xPrev_) * value_;
    }

    @Override
    public double fNext() {
        return value_;
    }
}
