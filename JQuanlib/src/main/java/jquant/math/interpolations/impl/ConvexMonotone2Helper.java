package jquant.math.interpolations.impl;

public class ConvexMonotone2Helper implements SectionHelper {
    private double xPrev_, xScaling_, gPrev_, gNext_, fAverage_, eta2_, prevPrimitive_;

    public ConvexMonotone2Helper(double xPrev, double xNext,
                                 double gPrev, double gNext,
                                 double fAverage, double eta2,
                                 double prevPrimitive) {
        xPrev_ = xPrev;
        xScaling_ = xNext-xPrev;
        gPrev_ = gPrev;
        gNext_ = gNext;
        fAverage_ = fAverage;
        eta2_ = eta2;
        prevPrimitive_ = prevPrimitive;
    }

    @Override
    public double value(double x) {
        double xVal = (x - xPrev_) / xScaling_;
        if (xVal <= eta2_) {
            return (fAverage_ + gPrev_);
        } else {
            return (fAverage_ + gPrev_ + (gNext_ - gPrev_) / ((1 - eta2_) * (1 - eta2_)) * (xVal - eta2_) * (xVal - eta2_));
        }
    }

    @Override
    public double primitive(double x) {
        double xVal = (x - xPrev_) / xScaling_;
        if (xVal <= eta2_) {
            return (prevPrimitive_ + xScaling_ * (fAverage_ * xVal + gPrev_ * xVal));
        } else {
            return (prevPrimitive_ + xScaling_ * (fAverage_ * xVal + gPrev_ * xVal + (gNext_ - gPrev_) / ((1 - eta2_) * (1 - eta2_)) *
                    (1.0 / 3.0 * (xVal * xVal * xVal - eta2_ * eta2_ * eta2_) - eta2_ * xVal * xVal + eta2_ * eta2_ * xVal)));
        }
    }

    @Override
    public double fNext() {
        return (fAverage_ + gNext_);
    }
}
