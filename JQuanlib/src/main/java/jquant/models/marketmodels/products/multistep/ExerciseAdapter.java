package jquant.models.marketmodels.products.multistep;

import jquant.models.marketmodels.CurveState;
import jquant.models.marketmodels.EvolutionDescription;
import jquant.models.marketmodels.MarketModelMultiProduct;
import jquant.models.marketmodels.callability.MarketModelExerciseValue;
import jquant.models.marketmodels.products.MultiProductMultiStep;

import java.util.List;

/**
 * Official QuantLib
 * {@code ql/models/marketmodels/products/multistep/exerciseadapter.hpp/.cpp} port.
 */
public class ExerciseAdapter extends MultiProductMultiStep {

    private final MarketModelExerciseValue exercise_;
    private final int numberOfProducts_;
    private final boolean[] isExerciseTime_;
    private int currentIndex_;

    public ExerciseAdapter(final MarketModelExerciseValue exercise) {
        this(exercise, 1);
    }

    public ExerciseAdapter(final MarketModelExerciseValue exercise,
                           int numberOfProducts) {
        // Official Clone<MarketModelExerciseValue> copies the exercise value.
        super(exercise.evolution().rateTimes());
        exercise_ = exercise.copy();
        numberOfProducts_ = numberOfProducts;
        isExerciseTime_ = exercise.isExerciseTime();
    }

    @Override
    public List<Double> possibleCashFlowTimes() {
        return exercise_.possibleCashFlowTimes();
    }

    @Override
    public int numberOfProducts() {
        return numberOfProducts_;
    }

    @Override
    public EvolutionDescription evolution() {
        return exercise_.evolution();
    }

    @Override
    public int maxNumberOfCashFlowsPerProductPerStep() {
        return 1;
    }

    @Override
    public void reset() {
        exercise_.reset();
        currentIndex_ = 0;
    }

    @Override
    public boolean nextTimeStep(CurveState currentState,
                                List<Integer> numberCashFlowsThisStep,
                                List<List<MarketModelMultiProduct.CashFlow>> generatedCashFlows) {
        for (int i = 0; i < numberCashFlowsThisStep.size(); ++i)
            numberCashFlowsThisStep.set(i, 0);
        boolean done = false;

        exercise_.nextStep(currentState);
        if (isExerciseTime_[currentIndex_]) {
            MarketModelMultiProduct.CashFlow cashflow = exercise_.value(currentState);
            numberCashFlowsThisStep.set(0, 1);
            // Official assigns the CashFlow struct by value; Java objects are
            // mutable, so copy the fields rather than aliasing the source.
            MarketModelMultiProduct.CashFlow target = generatedCashFlows.get(0).get(0);
            target.timeIndex = cashflow.timeIndex;
            target.amount = cashflow.amount;
            done = true;
        }
        ++currentIndex_;
        return done || currentIndex_ == isExerciseTime_.length;
    }

    public MarketModelExerciseValue exerciseValue() {
        return exercise_;
    }

    @Override
    public ExerciseAdapter copy() {
        ExerciseAdapter result = new ExerciseAdapter(exercise_, numberOfProducts_);
        result.currentIndex_ = currentIndex_;
        return result;
    }
}
