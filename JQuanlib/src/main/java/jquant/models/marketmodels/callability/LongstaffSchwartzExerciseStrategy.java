package jquant.models.marketmodels.callability;

import jquant.methods.montecarlo.ExerciseStrategy;
import jquant.models.marketmodels.CurveState;
import jquant.models.marketmodels.EvolutionDescription;
import jquant.models.marketmodels.MarketModelDiscounter;
import jquant.models.marketmodels.MarketModelMultiProduct;
import jquant.models.marketmodels.Utilities;

import java.util.ArrayList;
import java.util.List;

/**
 * Official QuantLib
 * {@code ql/models/marketmodels/callability/lsstrategy.hpp/.cpp} port.
 */
public class LongstaffSchwartzExerciseStrategy implements ExerciseStrategy<CurveState> {

    private MarketModelBasisSystem basisSystem_;
    private List<List<Double>> basisCoefficients_;
    private MarketModelExerciseValue exercise_;
    private MarketModelExerciseValue control_;
    private List<Integer> numeraires_;
    // work variable
    private int currentIndex_;
    private double principalInNumerairePortfolio_;
    private double newPrincipal_;
    private List<Double> exerciseTimes_ = new ArrayList<>();
    private List<Double> relevantTimes_;
    private boolean[] isBasisTime_;
    private boolean[] isRebateTime_;
    private boolean[] isControlTime_;
    private boolean[] isExerciseTime_;
    private List<MarketModelDiscounter> rebateDiscounters_;
    private List<MarketModelDiscounter> controlDiscounters_;
    private List<List<Double>> basisValues_;
    private int[] exerciseIndex_;

    public LongstaffSchwartzExerciseStrategy(MarketModelBasisSystem basisSystem,
                                             List<List<Double>> basisCoefficients,
                                             final EvolutionDescription evolution,
                                             final List<Integer> numeraires,
                                             MarketModelExerciseValue exercise,
                                             MarketModelExerciseValue control) {
        // Official Clone<> arguments copy the basis system and exercise values.
        basisSystem_ = basisSystem.copy();
        basisCoefficients_ = deepCopy(basisCoefficients);
        exercise_ = exercise.copy();
        control_ = control.copy();
        numeraires_ = new ArrayList<>(numeraires);

        EvolutionDescription.checkCompatibility(evolution, numeraires_);
        relevantTimes_ = new ArrayList<>(evolution.evolutionTimes());

        isBasisTime_ = Utilities.isInSubset(relevantTimes_,
                basisSystem_.evolution().evolutionTimes());
        isRebateTime_ = Utilities.isInSubset(relevantTimes_,
                exercise_.evolution().evolutionTimes());
        isControlTime_ = Utilities.isInSubset(relevantTimes_,
                control_.evolution().evolutionTimes());

        exerciseIndex_ = new int[relevantTimes_.size()];
        isExerciseTime_ = new boolean[relevantTimes_.size()];
        boolean[] v = exercise_.isExerciseTime();
        int exercises = 0;
        int idx = 0;
        for (int i = 0; i < relevantTimes_.size(); ++i) {
            exerciseIndex_[i] = exercises;
            if (isRebateTime_[i]) {
                isExerciseTime_[i] = v[idx++];
                if (isExerciseTime_[i]) {
                    exerciseTimes_.add(relevantTimes_.get(i));
                    ++exercises;
                }
            }
        }

        List<Double> rateTimes = evolution.rateTimes();
        List<Double> rebateTimes = exercise_.possibleCashFlowTimes();
        rebateDiscounters_ = new ArrayList<>();
        for (double rebateTime : rebateTimes)
            rebateDiscounters_.add(new MarketModelDiscounter(rebateTime, rateTimes));

        List<Double> controlTimes = control_.possibleCashFlowTimes();
        controlDiscounters_ = new ArrayList<>();
        for (double controlTime : controlTimes)
            controlDiscounters_.add(new MarketModelDiscounter(controlTime, rateTimes));

        List<Integer> basisSizes = basisSystem_.numberOfFunctions();
        basisValues_ = new ArrayList<>();
        for (int i = 0; i < basisSystem_.numberOfExercises(); ++i) {
            List<Double> values = new ArrayList<>();
            for (int j = 0; j < basisSizes.get(i); ++j)
                values.add(0.0);
            basisValues_.add(values);
        }
    }

    private LongstaffSchwartzExerciseStrategy(LongstaffSchwartzExerciseStrategy other) {
        basisSystem_ = other.basisSystem_.copy();
        basisCoefficients_ = deepCopy(other.basisCoefficients_);
        exercise_ = other.exercise_.copy();
        control_ = other.control_.copy();
        numeraires_ = new ArrayList<>(other.numeraires_);
        currentIndex_ = other.currentIndex_;
        principalInNumerairePortfolio_ = other.principalInNumerairePortfolio_;
        newPrincipal_ = other.newPrincipal_;
        exerciseTimes_ = new ArrayList<>(other.exerciseTimes_);
        relevantTimes_ = new ArrayList<>(other.relevantTimes_);
        isBasisTime_ = other.isBasisTime_.clone();
        isRebateTime_ = other.isRebateTime_.clone();
        isControlTime_ = other.isControlTime_.clone();
        isExerciseTime_ = other.isExerciseTime_.clone();
        rebateDiscounters_ = new ArrayList<>(other.rebateDiscounters_);
        controlDiscounters_ = new ArrayList<>(other.controlDiscounters_);
        basisValues_ = deepCopy(other.basisValues_);
        exerciseIndex_ = other.exerciseIndex_.clone();
    }

    private static List<List<Double>> deepCopy(List<List<Double>> source) {
        List<List<Double>> result = new ArrayList<>();
        for (List<Double> values : source)
            result.add(new ArrayList<>(values));
        return result;
    }

    @Override
    public List<Double> exerciseTimes() {
        return exerciseTimes_;
    }

    @Override
    public List<Double> relevantTimes() {
        return relevantTimes_;
    }

    @Override
    public void reset() {
        exercise_.reset();
        control_.reset();
        basisSystem_.reset();
        currentIndex_ = 0;
        principalInNumerairePortfolio_ = 1.0;
        newPrincipal_ = 1.0;
    }

    @Override
    public boolean exercise(CurveState currentState) {
        int exerciseIndex = exerciseIndex_[currentIndex_ - 1];

        MarketModelMultiProduct.CashFlow exerciseCF = exercise_.value(currentState);
        double exerciseValue = exerciseCF.amount *
                rebateDiscounters_.get(exerciseCF.timeIndex)
                        .numeraireBonds(currentState,
                                numeraires_.get(currentIndex_ - 1)) /
                principalInNumerairePortfolio_;

        MarketModelMultiProduct.CashFlow controlCF = control_.value(currentState);
        double controlValue = controlCF.amount *
                controlDiscounters_.get(controlCF.timeIndex)
                        .numeraireBonds(currentState,
                                numeraires_.get(currentIndex_ - 1)) /
                principalInNumerairePortfolio_;

        basisSystem_.values(currentState,
                basisValues_.get(exerciseIndex));

        final List<Double> alphas = basisCoefficients_.get(exerciseIndex);
        double continuationValue = controlValue;
        for (int i = 0; i < alphas.size(); ++i)
            continuationValue += alphas.get(i) * basisValues_.get(exerciseIndex).get(i);

        return exerciseValue >= continuationValue;
    }

    @Override
    public void nextStep(CurveState currentState) {
        principalInNumerairePortfolio_ = newPrincipal_;

        if (isRebateTime_[currentIndex_])
            exercise_.nextStep(currentState);
        if (isControlTime_[currentIndex_])
            control_.nextStep(currentState);
        if (isBasisTime_[currentIndex_])
            basisSystem_.nextStep(currentState);

        if (currentIndex_ < numeraires_.size() - 1) {
            int numeraire = numeraires_.get(currentIndex_);
            int nextNumeraire = numeraires_.get(currentIndex_ + 1);
            newPrincipal_ *= currentState.discountRatio(numeraire, nextNumeraire);
        }

        ++currentIndex_;
    }

    @Override
    public LongstaffSchwartzExerciseStrategy copy() {
        return new LongstaffSchwartzExerciseStrategy(this);
    }
}
