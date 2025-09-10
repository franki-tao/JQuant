package math.interpolations.impl;

public class ConvexMonotone3Helper implements SectionHelper {
    private double xPrev_, xScaling_, gPrev_, gNext_, fAverage_, eta3_, prevPrimitive_;

    public ConvexMonotone3Helper(double xPrev, double xNext,
                                 double gPrev, double gNext,
                                 double fAverage, double eta3,
                                 double prevPrimitive) {
        xPrev_ = xPrev;
        xScaling_ = xNext - xPrev;
        gPrev_ = gPrev;
        gNext_ = gNext;
        fAverage_ = fAverage;
        eta3_ = eta3;
        prevPrimitive_ = prevPrimitive;

    }

    @Override
    public double value(double x) {
        double xVal = (x - xPrev_) / xScaling_;
        if (xVal <= eta3_) {
            return (fAverage_ + gNext_ + (gPrev_ - gNext_) / (eta3_ * eta3_) * (eta3_ - xVal) * (eta3_ - xVal));
        } else {
            return (fAverage_ + gNext_);
        }
    }

    @Override
    public double primitive(double x) {
        double xVal = (x - xPrev_) / xScaling_;
        if (xVal <= eta3_) {
            return (prevPrimitive_ + xScaling_ * (fAverage_ * xVal + gNext_ * xVal + (gPrev_ - gNext_) / (eta3_ * eta3_) *
                    (1.0 / 3.0 * xVal * xVal * xVal - eta3_ * xVal * xVal + eta3_ * eta3_ * xVal)));
        } else {
            return (prevPrimitive_ + xScaling_ * (fAverage_ * xVal + gNext_ * xVal + (gPrev_ - gNext_) / (eta3_ * eta3_) *
                    (1.0 / 3.0 * eta3_ * eta3_ * eta3_)));
        }
    }

    @Override
    public double fNext() {
        return (fAverage_ + gNext_);
    }
}
