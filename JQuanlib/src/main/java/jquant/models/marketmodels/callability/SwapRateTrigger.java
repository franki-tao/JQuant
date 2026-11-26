package jquant.models.marketmodels.callability;

import jquant.methods.montecarlo.ExerciseStrategy;
import jquant.models.marketmodels.CurveState;
import jquant.models.marketmodels.Utilities;

import java.util.ArrayList;
import java.util.List;

import static jquant.math.CommonUtil.QL_REQUIRE;

/**
 * Official QuantLib
 * {@code ql/models/marketmodels/callability/swapratetrigger.hpp/.cpp} port.
 */
public class SwapRateTrigger implements ExerciseStrategy<CurveState> {

    private final List<Double> rateTimes_;
    private final List<Double> swapTriggers_;
    private final List<Double> exerciseTimes_;
    private final int[] rateIndex_;
    // things that vary in a path
    private int currentIndex_;

    public SwapRateTrigger(final List<Double> rateTimes,
                           List<Double> swapTriggers,
                           final List<Double> exerciseTimes) {
        rateTimes_ = new ArrayList<>(rateTimes);
        swapTriggers_ = new ArrayList<>(swapTriggers);
        exerciseTimes_ = new ArrayList<>(exerciseTimes);
        rateIndex_ = new int[exerciseTimes_.size()];

        Utilities.checkIncreasingTimes(rateTimes_);
        QL_REQUIRE(rateTimes_.size() > 1,
                "Rate times must contain at least two values");
        Utilities.checkIncreasingTimes(exerciseTimes_);
        QL_REQUIRE(swapTriggers_.size() == exerciseTimes_.size(),
                "swapTriggers/exerciseTimes mismatch");

        int j = 0;
        for (int i = 0; i < exerciseTimes_.size(); ++i) {
            while (j < rateTimes_.size() && rateTimes_.get(j) < exerciseTimes_.get(i))
                ++j;
            rateIndex_[i] = j;
        }
    }

    @Override
    public List<Double> exerciseTimes() {
        return exerciseTimes_;
    }

    @Override
    public List<Double> relevantTimes() {
        return exerciseTimes_;
    }

    @Override
    public void reset() {
        currentIndex_ = 0;
    }

    @Override
    public boolean exercise(CurveState currentState) {
        int rateIndex = rateIndex_[currentIndex_ - 1];
        double currentSwapRate = currentState.coterminalSwapRate(rateIndex);
        return swapTriggers_.get(currentIndex_ - 1) < currentSwapRate;
    }

    @Override
    public void nextStep(CurveState currentState) {
        ++currentIndex_;
    }

    @Override
    public SwapRateTrigger copy() {
        SwapRateTrigger result = new SwapRateTrigger(rateTimes_, swapTriggers_, exerciseTimes_);
        result.currentIndex_ = currentIndex_;
        return result;
    }
}
