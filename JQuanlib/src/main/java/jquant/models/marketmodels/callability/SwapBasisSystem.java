package jquant.models.marketmodels.callability;

import jquant.models.marketmodels.CurveState;
import jquant.models.marketmodels.EvolutionDescription;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Official QuantLib
 * {@code ql/models/marketmodels/callability/swapbasissystem.hpp/.cpp} port.
 */
public class SwapBasisSystem extends MarketModelBasisSystem {

    private final List<Double> rateTimes_;
    private final List<Double> exerciseTimes_;
    private int currentIndex_;
    private final int[] rateIndex_;
    private final EvolutionDescription evolution_;

    public SwapBasisSystem(final List<Double> rateTimes,
                           final List<Double> exerciseTimes) {
        rateTimes_ = new ArrayList<>(rateTimes);
        exerciseTimes_ = new ArrayList<>(exerciseTimes);
        rateIndex_ = new int[exerciseTimes_.size()];
        evolution_ = new EvolutionDescription(rateTimes_, exerciseTimes_);

        int j = 0;
        for (int i = 0; i < exerciseTimes_.size(); ++i) {
            while (j < rateTimes_.size() && rateTimes_.get(j) < exerciseTimes_.get(i))
                ++j;
            rateIndex_[i] = j;
        }
    }

    private SwapBasisSystem(SwapBasisSystem other) {
        rateTimes_ = new ArrayList<>(other.rateTimes_);
        exerciseTimes_ = new ArrayList<>(other.exerciseTimes_);
        currentIndex_ = other.currentIndex_;
        rateIndex_ = other.rateIndex_.clone();
        evolution_ = other.evolution_;
    }

    @Override
    public int numberOfExercises() {
        return exerciseTimes_.size();
    }

    @Override
    public List<Integer> numberOfFunctions() {
        List<Integer> sizes = new ArrayList<>();
        for (int i = 0; i < exerciseTimes_.size(); ++i)
            sizes.add(3);
        if (rateIndex_[exerciseTimes_.size() - 1] == rateTimes_.size() - 2)
            sizes.set(sizes.size() - 1, 2);
        return sizes;
    }

    @Override
    public EvolutionDescription evolution() {
        return evolution_;
    }

    @Override
    public void nextStep(CurveState currentState) {
        ++currentIndex_;
    }

    @Override
    public void reset() {
        currentIndex_ = 0;
    }

    @Override
    public boolean[] isExerciseTime() {
        boolean[] result = new boolean[exerciseTimes_.size()];
        Arrays.fill(result, true);
        return result;
    }

    @Override
    public void values(CurveState currentState, List<Double> results) {
        int rateIndex = rateIndex_[currentIndex_ - 1];

        // Official reserve(3) / resize(2) followed by two assignments.
        results.clear();
        results.add(1.0);
        results.add(currentState.forwardRate(rateIndex));
        if (rateIndex < rateTimes_.size() - 2)
            results.add(currentState.coterminalSwapRate(rateIndex + 1));
    }

    @Override
    public SwapBasisSystem copy() {
        return new SwapBasisSystem(this);
    }
}
