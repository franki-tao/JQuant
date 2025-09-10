package math.interpolations.impl;

public class QuadraticHelper implements SectionHelper {
    private double xPrev_, xNext_, fPrev_, fNext_, fAverage_, prevPrimitive_;
    private double xScaling_, a_, b_, c_;

    public QuadraticHelper(double xPrev, double xNext,
                           double fPrev, double fNext,
                           double fAverage,
                           double prevPrimitive) {
        xPrev_ = (xPrev);
        xNext_ = (xNext);
        fPrev_ = (fPrev);
        fNext_ = (fNext);
        fAverage_ = (fAverage);
        prevPrimitive_ = (prevPrimitive);
        a_ = 3 * fPrev_ + 3 * fNext_ - 6 * fAverage_;
        b_ = -(4 * fPrev_ + 2 * fNext_ - 6 * fAverage_);
        c_ = fPrev_;
        xScaling_ = xNext_ - xPrev_;
    }

    @Override
    public double value(double x) {
        double xVal = (x-xPrev_)/xScaling_;
        return( a_*xVal*xVal + b_*xVal + c_ );
    }

    @Override
    public double primitive(double x) {
        double xVal = (x-xPrev_)/xScaling_;
        return( prevPrimitive_ + xScaling_ * (a_/3*xVal*xVal + b_/2*xVal + c_) * xVal );
    }

    @Override
    public double fNext() {
        return fNext_;
    }
}
