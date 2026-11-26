package jquant.models.marketmodels.callability;

import jquant.models.marketmodels.CurveState;
import jquant.models.marketmodels.EvolutionDescription;
import jquant.models.marketmodels.MarketModelMultiProduct;
import jquant.models.marketmodels.Utilities;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static jquant.math.CommonUtil.QL_REQUIRE;

/**
 * Official QuantLib
 * {@code ql/models/marketmodels/callability/nothingexercisevalue.hpp/.cpp} port.
 */
public class NothingExerciseValue implements MarketModelExerciseValue {

    private final List<Double> rateTimes_;
    private boolean[] isExerciseTime_;
    private EvolutionDescription evolution_;
    private final MarketModelMultiProduct.CashFlow cf_ = new MarketModelMultiProduct.CashFlow();
    private int currentIndex_;
    private int numberOfExercises_;

    public NothingExerciseValue(final List<Double> rateTimes) {
        this(rateTimes, null);
    }

    public NothingExerciseValue(final List<Double> rateTimes,
                                boolean[] isExerciseTime) {
        rateTimes_ = new ArrayList<>(rateTimes);
        isExerciseTime_ = isExerciseTime == null ? new boolean[0] : isExerciseTime.clone();
        Utilities.checkIncreasingTimes(rateTimes_);
        QL_REQUIRE(rateTimes_.size() >= 2,
                "Rate times must contain at least two values");
        cf_.amount = 0.0;
        List<Double> evolutionTimes = new ArrayList<>(rateTimes_);
        evolutionTimes.remove(evolutionTimes.size() - 1);
        evolution_ = new EvolutionDescription(rateTimes_, evolutionTimes);
        if (isExerciseTime_.length == 0) {
            isExerciseTime_ = new boolean[rateTimes_.isEmpty() ? 0 : rateTimes_.size() - 1];
            Arrays.fill(isExerciseTime_, true);
        } else {
            QL_REQUIRE(isExerciseTime_.length ==
                            (rateTimes_.isEmpty() ? 0 : rateTimes_.size() - 1),
                    "isExerciseTime (" + isExerciseTime_.length + ") must "
                            + "have same size as rateTimes minus 1 ("
                            + (rateTimes_.isEmpty() ? 0 : rateTimes_.size() - 1) + ")");
        }
        numberOfExercises_ = 0;
        for (boolean exerciseTime : isExerciseTime_)
            if (exerciseTime)
                ++numberOfExercises_;
    }

    @Override
    public int numberOfExercises() {
        return numberOfExercises_;
    }

    @Override
    public EvolutionDescription evolution() {
        return evolution_;
    }

    @Override
    public List<Double> possibleCashFlowTimes() {
        return rateTimes_;
    }

    @Override
    public void reset() {
        currentIndex_ = 0;
    }

    @Override
    public void nextStep(CurveState currentState) {
        cf_.timeIndex = currentIndex_;
        ++currentIndex_;
    }

    @Override
    public boolean[] isExerciseTime() {
        return isExerciseTime_.clone();
    }

    @Override
    public MarketModelMultiProduct.CashFlow value(CurveState currentState) {
        // Official returns the CashFlow struct by value.
        MarketModelMultiProduct.CashFlow result = new MarketModelMultiProduct.CashFlow();
        result.timeIndex = cf_.timeIndex;
        result.amount = cf_.amount;
        return result;
    }

    @Override
    public MarketModelExerciseValue copy() {
        NothingExerciseValue result = new NothingExerciseValue(rateTimes_, isExerciseTime_);
        result.currentIndex_ = currentIndex_;
        result.cf_.timeIndex = cf_.timeIndex;
        result.cf_.amount = cf_.amount;
        return result;
    }
}
