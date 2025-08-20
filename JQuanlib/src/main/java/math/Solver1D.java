package math;

import static math.CommonUtil.QL_FAIL;
import static math.CommonUtil.QL_REQUIRE;
import static math.MathUtils.*;

public abstract class Solver1D {
    protected double root_;
    protected double xMin_;
    protected double xMax_;
    protected double fxMin_;
    protected double fxMax_;
    protected int maxEvaluations_ = MAX_FUNCTION_EVALUATIONS;
    protected int evaluationNumber_;
    private double lowerBound_;
    private double upperBound_;
    private boolean lowerBoundEnforced_ = false;
    private boolean upperBoundEnforced_ = false;

    public abstract double solveImpl(Function f, double xAccuracy);

    public double solve(Function f, double accuracy,
                        double guess,
                        double step) {
        QL_REQUIRE(accuracy > 0.0,
                "accuracy must be positive");
        // check whether we really want to use epsilon
        accuracy = Math.max(accuracy, QL_EPSILON);

        double growthFactor = 1.6;
        int flipflop = -1;

        root_ = guess;
        fxMax_ = f.value(root_);

        // monotonically crescent bias, as in optionValue(volatility)
        if (close(fxMax_, 0.0))
            return root_;
        else if (fxMax_ > 0.0) {
            xMin_ = enforceBounds_(root_ - step);
            fxMin_ = f.value(xMin_);
            xMax_ = root_;
        } else {
            xMin_ = root_;
            fxMin_ = fxMax_;
            xMax_ = enforceBounds_(root_ + step);
            fxMax_ = f.value(xMax_);
        }

        evaluationNumber_ = 2;
        while (evaluationNumber_ <= maxEvaluations_) {
            if (fxMin_ * fxMax_ <= 0.0) {
                if (close(fxMin_, 0.0))
                    return xMin_;
                if (close(fxMax_, 0.0))
                    return xMax_;
                root_ = (xMax_ + xMin_) / 2.0;
                return solveImpl(f, accuracy);
            }
            if (Math.abs(fxMin_) < Math.abs(fxMax_)) {
                xMin_ = enforceBounds_(xMin_ + growthFactor * (xMin_ - xMax_));
                fxMin_ = f.value(xMin_);
            } else if (Math.abs(fxMin_) > Math.abs(fxMax_)) {
                xMax_ = enforceBounds_(xMax_ + growthFactor * (xMax_ - xMin_));
                fxMax_ = f.value(xMax_);
            } else if (flipflop == -1) {
                xMin_ = enforceBounds_(xMin_ + growthFactor * (xMin_ - xMax_));
                fxMin_ = f.value(xMin_);
                evaluationNumber_++;
                flipflop = 1;
            } else if (flipflop == 1) {
                xMax_ = enforceBounds_(xMax_ + growthFactor * (xMax_ - xMin_));
                fxMax_ = f.value(xMax_);
                flipflop = -1;
            }
            evaluationNumber_++;
        }

        QL_FAIL("unable to bracket root in");
        return 0;
    }

    public double solve(Function f,
                        double accuracy,
                        double guess,
                        double xMin,
                        double xMax) {
        QL_REQUIRE(accuracy > 0.0,
                "accuracy must be positive");
        // check whether we really want to use epsilon
        accuracy = Math.max(accuracy, QL_EPSILON);

        xMin_ = xMin;
        xMax_ = xMax;

        QL_REQUIRE(xMin_ < xMax_,
                "invalid range: xMin_  >= xMax_ ");
        QL_REQUIRE(!lowerBoundEnforced_ || xMin_ >= lowerBound_,
                "xMin_ < enforced low bound ");
        QL_REQUIRE(!upperBoundEnforced_ || xMax_ <= upperBound_,
                "xMax_  > enforced hi bound ");

        fxMin_ = f.value(xMin_);
        if (close(fxMin_, 0.0))
            return xMin_;

        fxMax_ = f.value(xMax_);
        if (close(fxMax_, 0.0))
            return xMax_;

        evaluationNumber_ = 2;

        QL_REQUIRE(fxMin_ * fxMax_ < 0.0,
                "root not bracketed");

        QL_REQUIRE(guess > xMin_,
                "guess  < xMin_ ");
        QL_REQUIRE(guess < xMax_,
                "guess  > xMax_ ");

        root_ = guess;

        return solveImpl(f, accuracy);
    }

    public void setMaxEvaluations(int maxEvaluations_) {
        this.maxEvaluations_ = maxEvaluations_;
    }

    public void setLowerBound(double lowerBound_) {
        this.lowerBound_ = lowerBound_;
    }

    public void setUpperBound(double upperBound_) {
        this.upperBound_ = upperBound_;
    }

    private double enforceBounds_(double x) {
        if (lowerBoundEnforced_ && x < lowerBound_)
            return lowerBound_;
        if (upperBoundEnforced_ && x > upperBound_)
            return upperBound_;
        return x;
    }
}
