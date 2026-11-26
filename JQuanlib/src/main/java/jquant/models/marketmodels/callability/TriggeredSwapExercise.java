package jquant.models.marketmodels.callability;

import jquant.models.marketmodels.CurveState;
import jquant.models.marketmodels.EvolutionDescription;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Official QuantLib
 * {@code ql/models/marketmodels/callability/triggeredswapexercise.hpp/.cpp}
 * port.
 */
public class TriggeredSwapExercise extends MarketModelParametricExercise {

    private final List<Double> rateTimes_;
    private final List<Double> exerciseTimes_;
    private final List<Double> strikes_;
    private int currentStep_ = 0;
    private final int[] rateIndex_;
    private final EvolutionDescription evolution_;

    public TriggeredSwapExercise(final List<Double> rateTimes,
                                 final List<Double> exerciseTimes,
                                 List<Double> strikes) {
        rateTimes_ = new ArrayList<>(rateTimes);
        exerciseTimes_ = new ArrayList<>(exerciseTimes);
        strikes_ = new ArrayList<>(strikes);
        rateIndex_ = new int[exerciseTimes_.size()];
        evolution_ = new EvolutionDescription(rateTimes_, exerciseTimes_);

        int j = 0;
        for (int i = 0; i < exerciseTimes_.size(); ++i) {
            while (j < rateTimes_.size()
                    && rateTimes_.get(j) < exerciseTimes_.get(i))
                ++j;
            rateIndex_[i] = j;
        }
    }

    private TriggeredSwapExercise(TriggeredSwapExercise other) {
        rateTimes_ = new ArrayList<>(other.rateTimes_);
        exerciseTimes_ = new ArrayList<>(other.exerciseTimes_);
        strikes_ = new ArrayList<>(other.strikes_);
        currentStep_ = other.currentStep_;
        rateIndex_ = other.rateIndex_.clone();
        evolution_ = other.evolution_;
    }

    // NodeDataProvider interface

    @Override
    public int numberOfExercises() {
        return exerciseTimes_.size();
    }

    @Override
    public EvolutionDescription evolution() {
        return evolution_;
    }

    @Override
    public void nextStep(CurveState currentState) {
        ++currentStep_;
    }

    @Override
    public void reset() {
        currentStep_ = 0;
    }

    @Override
    public boolean[] isExerciseTime() {
        boolean[] result = new boolean[numberOfExercises()];
        Arrays.fill(result, true);
        return result;
    }

    @Override
    public void values(CurveState currentState, List<Double> results) {
        int swapIndex = rateIndex_[currentStep_ - 1];
        results.clear();
        results.add(currentState.coterminalSwapRate(swapIndex));
    }

    // ParametricExercise interface

    @Override
    public List<Integer> numberOfVariables() {
        List<Integer> result = new ArrayList<>();
        for (int i = 0; i < numberOfExercises(); ++i)
            result.add(1);
        return result;
    }

    @Override
    public List<Integer> numberOfParameters() {
        List<Integer> result = new ArrayList<>();
        for (int i = 0; i < numberOfExercises(); ++i)
            result.add(1);
        return result;
    }

    @Override
    public boolean exercise(int exerciseNumber,
                            List<Double> parameters,
                            List<Double> variables) {
        return variables.get(0) >= parameters.get(0);
    }

    @Override
    public void guess(int exerciseIndex, List<Double> parameters) {
        parameters.clear();
        parameters.add(strikes_.get(exerciseIndex));
    }

    @Override
    public TriggeredSwapExercise copy() {
        return new TriggeredSwapExercise(this);
    }
}
