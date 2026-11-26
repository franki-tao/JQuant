package jquant.models.marketmodels.callability;

import jquant.methods.montecarlo.ExerciseStrategy;
import jquant.models.marketmodels.CurveState;

import java.util.ArrayList;
import java.util.List;

/**
 * Official QuantLib
 * {@code ql/models/marketmodels/callability/parametricexerciseadapter.hpp/.cpp}
 * port.
 */
public class ParametricExerciseAdapter implements ExerciseStrategy<CurveState> {

    private MarketModelParametricExercise exercise_;
    private List<List<Double>> parameters_;
    private List<Double> exerciseTimes_ = new ArrayList<>();
    private int currentStep_ = 0;
    private int currentExercise_ = 0;
    private boolean[] isExerciseTime_;
    private List<Integer> numberOfVariables_;
    private List<Double> variables_ = new ArrayList<>();

    public ParametricExerciseAdapter(final MarketModelParametricExercise exercise,
                                     List<List<Double>> parameters) {
        // Official Clone<> argument copies the exercise.
        exercise_ = exercise.copy();
        parameters_ = deepCopy(parameters);
        isExerciseTime_ = exercise.isExerciseTime();
        numberOfVariables_ = new ArrayList<>(exercise.numberOfVariables());

        List<Double> evolutionTimes = exercise_.evolution().evolutionTimes();
        for (int i = 0; i < evolutionTimes.size(); ++i) {
            if (isExerciseTime_[i])
                exerciseTimes_.add(evolutionTimes.get(i));
        }
    }

    private ParametricExerciseAdapter(ParametricExerciseAdapter other) {
        exercise_ = other.exercise_.copy();
        parameters_ = deepCopy(other.parameters_);
        exerciseTimes_ = new ArrayList<>(other.exerciseTimes_);
        currentStep_ = other.currentStep_;
        currentExercise_ = other.currentExercise_;
        isExerciseTime_ = other.isExerciseTime_.clone();
        numberOfVariables_ = new ArrayList<>(other.numberOfVariables_);
        variables_ = new ArrayList<>(other.variables_);
    }

    private static List<List<Double>> deepCopy(List<List<Double>> source) {
        List<List<Double>> result = new ArrayList<>();
        for (List<Double> values : source)
            result.add(new ArrayList<>(values));
        return result;
    }

    private static void resize(List<Double> list, int size) {
        while (list.size() < size)
            list.add(0.0);
        while (list.size() > size)
            list.remove(list.size() - 1);
    }

    @Override
    public List<Double> exerciseTimes() {
        return exerciseTimes_;
    }

    @Override
    public List<Double> relevantTimes() {
        return exercise_.evolution().evolutionTimes();
    }

    @Override
    public void reset() {
        exercise_.reset();
        currentStep_ = 0;
        currentExercise_ = 0;
    }

    @Override
    public void nextStep(CurveState currentState) {
        exercise_.nextStep(currentState);
        if (isExerciseTime_[currentStep_])
            ++currentExercise_;
        ++currentStep_;
    }

    @Override
    public boolean exercise(CurveState currentState) {
        resize(variables_, numberOfVariables_.get(currentExercise_ - 1));
        exercise_.values(currentState, variables_);
        return exercise_.exercise(currentExercise_ - 1,
                parameters_.get(currentExercise_ - 1),
                variables_);
    }

    @Override
    public ParametricExerciseAdapter copy() {
        return new ParametricExerciseAdapter(this);
    }
}
