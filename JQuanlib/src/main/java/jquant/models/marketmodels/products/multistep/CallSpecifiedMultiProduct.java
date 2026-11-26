package jquant.models.marketmodels.products.multistep;

import jquant.methods.montecarlo.ExerciseStrategy;
import jquant.models.marketmodels.CurveState;
import jquant.models.marketmodels.EvolutionDescription;
import jquant.models.marketmodels.MarketModelMultiProduct;
import jquant.models.marketmodels.Utilities;

import java.util.ArrayList;
import java.util.List;

import static jquant.math.CommonUtil.QL_REQUIRE;

/**
 * Official QuantLib
 * {@code ql/models/marketmodels/products/multistep/callspecifiedmultiproduct.hpp/.cpp} port.
 *
 * <p>The official un-rebated constructor branch, which builds a
 * {@code MarketModelCashRebate}, is not needed by the ported case; Java
 * requires the rebate argument explicitly (no default arguments).
 */
public class CallSpecifiedMultiProduct extends MarketModelMultiProduct {

    private MarketModelMultiProduct underlying_;
    private ExerciseStrategy<CurveState> strategy_;
    private MarketModelMultiProduct rebate_;
    private EvolutionDescription evolution_;
    private List<boolean[]> isPresent_;
    private List<Double> cashFlowTimes_;
    private int rebateOffset_ = 0;
    private boolean wasCalled_ = false;
    private List<Integer> dummyCashFlowsThisStep_;
    private List<List<CashFlow>> dummyCashFlowsGenerated_;
    private int currentIndex_ = 0;
    private boolean callable_ = true;

    public CallSpecifiedMultiProduct(final MarketModelMultiProduct underlying,
                                     final ExerciseStrategy<CurveState> strategy,
                                     MarketModelMultiProduct rebate) {
        QL_REQUIRE(rebate != null, "rebate required");
        // Official Clone<> arguments copy the underlying, strategy and rebate.
        underlying_ = underlying.copy();
        strategy_ = strategy.copy();
        rebate_ = rebate.copy();

        int products = underlying_.numberOfProducts();
        EvolutionDescription d1 = underlying_.evolution();
        List<Double> rateTimes1 = d1.rateTimes();
        List<Double> evolutionTimes1 = d1.evolutionTimes();
        List<Double> exerciseTimes = strategy_.exerciseTimes();

        EvolutionDescription d2 = rebate_.evolution();
        List<Double> rateTimes2 = d2.rateTimes();
        QL_REQUIRE(rateTimes1.size() == rateTimes2.size()
                        && rateTimes1.equals(rateTimes2),
                "incompatible rate times");

        List<List<Double>> allEvolutionTimes = new ArrayList<>(4);
        allEvolutionTimes.add(evolutionTimes1);
        allEvolutionTimes.add(exerciseTimes);
        allEvolutionTimes.add(rebate_.evolution().evolutionTimes());
        allEvolutionTimes.add(strategy_.relevantTimes());

        List<Double> mergedEvolutionTimes = new ArrayList<>();
        isPresent_ = new ArrayList<>();
        Utilities.mergeTimes(allEvolutionTimes, mergedEvolutionTimes, isPresent_);

        // TODO: add relevant rates
        evolution_ = new EvolutionDescription(rateTimes1, mergedEvolutionTimes);

        cashFlowTimes_ = new ArrayList<>(underlying_.possibleCashFlowTimes());
        rebateOffset_ = cashFlowTimes_.size();
        List<Double> rebateTimes = rebate_.possibleCashFlowTimes();
        cashFlowTimes_.addAll(rebateTimes);

        dummyCashFlowsThisStep_ = new ArrayList<>();
        for (int i = 0; i < products; ++i)
            dummyCashFlowsThisStep_.add(0);
        int n = rebate_.maxNumberOfCashFlowsPerProductPerStep();
        dummyCashFlowsGenerated_ = new ArrayList<>();
        for (int i = 0; i < products; ++i) {
            List<CashFlow> cashFlows = new ArrayList<>();
            for (int j = 0; j < n; ++j)
                cashFlows.add(new CashFlow());
            dummyCashFlowsGenerated_.add(cashFlows);
        }
    }

    protected CallSpecifiedMultiProduct(CallSpecifiedMultiProduct other) {
        underlying_ = other.underlying_.copy();
        strategy_ = other.strategy_.copy();
        rebate_ = other.rebate_.copy();
        evolution_ = other.evolution_;
        isPresent_ = new ArrayList<>();
        for (boolean[] present : other.isPresent_)
            isPresent_.add(present == null ? null : present.clone());
        cashFlowTimes_ = new ArrayList<>(other.cashFlowTimes_);
        rebateOffset_ = other.rebateOffset_;
        wasCalled_ = other.wasCalled_;
        dummyCashFlowsThisStep_ = new ArrayList<>(other.dummyCashFlowsThisStep_);
        dummyCashFlowsGenerated_ = copyCashFlows(other.dummyCashFlowsGenerated_);
        currentIndex_ = other.currentIndex_;
        callable_ = other.callable_;
    }

    private static List<List<CashFlow>> copyCashFlows(List<List<CashFlow>> source) {
        List<List<CashFlow>> result = new ArrayList<>();
        for (List<CashFlow> cashFlows : source) {
            List<CashFlow> cashFlowCopies = new ArrayList<>();
            for (CashFlow cashFlow : cashFlows) {
                CashFlow cashFlowCopy = new CashFlow();
                cashFlowCopy.timeIndex = cashFlow.timeIndex;
                cashFlowCopy.amount = cashFlow.amount;
                cashFlowCopies.add(cashFlowCopy);
            }
            result.add(cashFlowCopies);
        }
        return result;
    }

    @Override
    public List<Integer> suggestedNumeraires() {
        return underlying_.suggestedNumeraires();
    }

    @Override
    public EvolutionDescription evolution() {
        return evolution_;
    }

    @Override
    public List<Double> possibleCashFlowTimes() {
        return cashFlowTimes_;
    }

    @Override
    public int numberOfProducts() {
        return underlying_.numberOfProducts();
    }

    @Override
    public int maxNumberOfCashFlowsPerProductPerStep() {
        return Math.max(underlying_.maxNumberOfCashFlowsPerProductPerStep(),
                rebate_.maxNumberOfCashFlowsPerProductPerStep());
    }

    @Override
    public void reset() {
        underlying_.reset();
        rebate_.reset();
        strategy_.reset();
        currentIndex_ = 0;
        wasCalled_ = false;
    }

    @Override
    public boolean nextTimeStep(CurveState currentState,
                                List<Integer> numberCashFlowsThisStep,
                                List<List<CashFlow>> cashFlowsGenerated) {
        boolean isUnderlyingTime = isPresent_.get(0)[currentIndex_];
        boolean isExerciseTime = isPresent_.get(1)[currentIndex_];
        boolean isRebateTime = isPresent_.get(2)[currentIndex_];
        boolean isStrategyRelevantTime = isPresent_.get(3)[currentIndex_];

        boolean done = false;

        if (!wasCalled_ && isStrategyRelevantTime)
            strategy_.nextStep(currentState);

        if (!wasCalled_ && isExerciseTime && callable_)
            wasCalled_ = strategy_.exercise(currentState);

        if (wasCalled_) {
            if (isRebateTime) {
                done = rebate_.nextTimeStep(currentState,
                        numberCashFlowsThisStep,
                        cashFlowsGenerated);
                for (int i = 0; i < numberCashFlowsThisStep.size(); ++i)
                    for (int j = 0; j < numberCashFlowsThisStep.get(i); ++j)
                        cashFlowsGenerated.get(i).get(j).timeIndex += rebateOffset_;
            }
        } else {
            if (isRebateTime)
                rebate_.nextTimeStep(currentState,
                        dummyCashFlowsThisStep_,
                        dummyCashFlowsGenerated_);
            if (isUnderlyingTime)
                done = underlying_.nextTimeStep(currentState,
                        numberCashFlowsThisStep,
                        cashFlowsGenerated);
        }

        ++currentIndex_;
        return done || currentIndex_ == evolution_.evolutionTimes().size();
    }

    @Override
    public MarketModelMultiProduct copy() {
        return new CallSpecifiedMultiProduct(this);
    }

    public MarketModelMultiProduct underlying() {
        return underlying_;
    }

    public ExerciseStrategy<CurveState> strategy() {
        return strategy_;
    }

    public MarketModelMultiProduct rebate() {
        return rebate_;
    }

    public void enableCallability() {
        callable_ = true;
    }

    public void disableCallability() {
        callable_ = false;
    }
}
