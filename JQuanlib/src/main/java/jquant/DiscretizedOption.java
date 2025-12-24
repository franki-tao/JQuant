package jquant;

import jquant.math.Array;

import java.util.List;

import static jquant.math.CommonUtil.QL_FAIL;
import static jquant.math.CommonUtil.QL_REQUIRE;

public abstract class DiscretizedOption extends DiscretizedAsset {
    protected DiscretizedAsset underlying_;
    protected Exercise.Type exerciseType_;
    protected List<Double> exerciseTimes_;

    public DiscretizedOption(DiscretizedAsset underlying, Exercise.Type type, List<Double> exerciseTimes) {
        underlying_ = underlying;
        exerciseType_ = type;
        exerciseTimes_ = exerciseTimes;
    }
    protected void applyExerciseCondition() {
        for (int i=0; i<values_.size(); i++)
            values_.set(i, Math.max(underlying_.values().get(i), values_.get(i)));
    }

    @Override
    public void reset(int size) {
        QL_REQUIRE(method() == underlying_.method(),
                "option and underlying were initialized on " +
                "different methods");
        values_ = new Array(size, 0.0);
        adjustValues();
    }

    @Override
    public List<Double> mandatoryTimes() {
        List<Double> times = underlying_.mandatoryTimes();
        int firstPositiveIndex = -1;
        for (int i = 0; i < exerciseTimes_.size(); i++) {
            if (exerciseTimes_.get(i) >= 0.0) {
                firstPositiveIndex = i;
                break;
            }
        }

        if (firstPositiveIndex != -1) {
            times.addAll(exerciseTimes_.subList(firstPositiveIndex, exerciseTimes_.size()));
        }
        return times;
    }

    @Override
    protected void postAdjustValuesImpl() {
        /* In the real world, with time flowing forward, first
           any payment is settled and only after options can be
           exercised. Here, with time flowing backward, options
           must be exercised before performing the adjustment.
        */
        underlying_.partialRollback(time());
        underlying_.preAdjustValues();
        int i;
        switch (exerciseType_) {
            case American:
                if (time_ >= exerciseTimes_.get(0) && time_ <= exerciseTimes_.get(1))
                    applyExerciseCondition();
                break;
            case Bermudan:
            case European:
                for (i=0; i<exerciseTimes_.size(); i++) {
                    double t = exerciseTimes_.get(i);
                    if (t >= 0.0 && isOnTime(t))
                        applyExerciseCondition();
                }
                break;
            default:
                QL_FAIL("invalid exercise type");
        }
        underlying_.postAdjustValues();
    }
}
