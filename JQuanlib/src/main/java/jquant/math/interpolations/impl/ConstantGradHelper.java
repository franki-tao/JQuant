package jquant.math.interpolations.impl;

public class ConstantGradHelper implements SectionHelper {
    private double fPrev_, prevPrimitive_, xPrev_, fGrad_, fNext_;

    public ConstantGradHelper(double fPrev, double prevPrimitive,
                              double xPrev, double xNext, double fNext) {
        fPrev_ = (fPrev);
        prevPrimitive_ = (prevPrimitive);
        xPrev_ = (xPrev);
        fGrad_ = ((fNext - fPrev) / (xNext - xPrev));
        fNext_ = (fNext);
    }

    @Override
    public double value(double x) {
        return (fPrev_ + (x - xPrev_) * fGrad_);
    }

    @Override
    public double primitive(double x) {
        return (prevPrimitive_ + (x - xPrev_) * (fPrev_ + 0.5 * (x - xPrev_) * fGrad_));
    }

    @Override
    public double fNext() {
        return fNext_;
    }
}
