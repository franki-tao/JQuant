package jquant.models.marketmodels.callability;

import jquant.math.MathUtils;
import jquant.math.statistics.GenericRiskStatistics;
import jquant.methods.montecarlo.ExerciseStrategy;
import jquant.models.marketmodels.AccountingEngine;
import jquant.models.marketmodels.CurveState;
import jquant.models.marketmodels.MarketModelDiscounter;
import jquant.models.marketmodels.MarketModelEvolver;
import jquant.models.marketmodels.MarketModelMultiProduct;
import jquant.models.marketmodels.SequenceStatisticsInc;
import jquant.models.marketmodels.Utilities;
import jquant.models.marketmodels.products.MultiProductComposite;
import jquant.models.marketmodels.products.multistep.CallSpecifiedMultiProduct;
import jquant.models.marketmodels.products.multistep.ExerciseAdapter;

import java.util.ArrayList;
import java.util.List;

/**
 * Official QuantLib
 * {@code ql/models/marketmodels/callability/upperboundengine.hpp/.cpp} port.
 *
 * <p>The nested official {@code DecoratedHedge} helper is ported here.
 */
public class UpperBoundEngine {

    private static class DecoratedHedge extends CallSpecifiedMultiProduct {

        private List<CurveState> savedStates_ = new ArrayList<>();
        private int lastSavedStep_;
        private boolean recording_;
        private List<Integer> numberCashFlowsThisStep_;
        private List<List<MarketModelMultiProduct.CashFlow>> cashFlowsGenerated_;

        DecoratedHedge(final CallSpecifiedMultiProduct product) {
            super(product);

            int N = product.numberOfProducts();
            numberCashFlowsThisStep_ = new ArrayList<>();
            for (int i = 0; i < N; ++i)
                numberCashFlowsThisStep_.add(0);
            cashFlowsGenerated_ = new ArrayList<>();
            for (int i = 0; i < N; ++i) {
                List<MarketModelMultiProduct.CashFlow> cashFlows = new ArrayList<>();
                for (int j = 0; j < product.maxNumberOfCashFlowsPerProductPerStep(); ++j)
                    cashFlows.add(new MarketModelMultiProduct.CashFlow());
                cashFlowsGenerated_.add(cashFlows);
            }

            clear();
        }

        private DecoratedHedge(final DecoratedHedge other) {
            super(other);
            savedStates_ = new ArrayList<>();
            for (CurveState state : other.savedStates_)
                savedStates_.add(state.copy());
            lastSavedStep_ = other.lastSavedStep_;
            recording_ = other.recording_;
            numberCashFlowsThisStep_ = new ArrayList<>(other.numberCashFlowsThisStep_);
            cashFlowsGenerated_ = new ArrayList<>();
            for (List<MarketModelMultiProduct.CashFlow> cashFlows : other.cashFlowsGenerated_) {
                List<MarketModelMultiProduct.CashFlow> cashFlowCopies = new ArrayList<>();
                for (MarketModelMultiProduct.CashFlow cashFlow : cashFlows) {
                    MarketModelMultiProduct.CashFlow cashFlowCopy =
                            new MarketModelMultiProduct.CashFlow();
                    cashFlowCopy.timeIndex = cashFlow.timeIndex;
                    cashFlowCopy.amount = cashFlow.amount;
                    cashFlowCopies.add(cashFlowCopy);
                }
                cashFlowsGenerated_.add(cashFlowCopies);
            }
        }

        @Override
        public void reset() {
            super.reset();
            disableCallability();
            for (int i = 0; i < lastSavedStep_; ++i)
                super.nextTimeStep(savedStates_.get(i),
                        numberCashFlowsThisStep_,
                        cashFlowsGenerated_);
            enableCallability();
        }

        @Override
        public boolean nextTimeStep(CurveState currentState,
                                    List<Integer> numberCashFlowsThisStep,
                                    List<List<MarketModelMultiProduct.CashFlow>> cashFlowsGenerated) {
            if (recording_)
                savedStates_.add(currentState.copy());
            return super.nextTimeStep(currentState,
                    numberCashFlowsThisStep,
                    cashFlowsGenerated);
        }

        @Override
        public MarketModelMultiProduct copy() {
            return new DecoratedHedge(this);
        }

        void save() {
            lastSavedStep_ = savedStates_.size();
        }

        void clear() {
            lastSavedStep_ = 0;
            savedStates_.clear();
            recording_ = true;
        }

        void startRecording() {
            recording_ = true;
        }

        void stopRecording() {
            recording_ = false;
        }
    }

    private final MarketModelEvolver evolver_;
    private final List<MarketModelEvolver> innerEvolvers_;
    private final MultiProductComposite composite_;
    private final double initialNumeraireValue_;
    private final int underlyingSize_, rebateSize_, hedgeSize_, hedgeRebateSize_;
    private final int underlyingOffset_, rebateOffset_, hedgeOffset_, hedgeRebateOffset_;
    private final int numberOfProducts_;
    private final int numberOfSteps_;
    private final boolean[] isExerciseTime_;

    // workspace
    private final List<Integer> numberCashFlowsThisStep_;
    private final List<List<MarketModelMultiProduct.CashFlow>> cashFlowsGenerated_;
    private final List<MarketModelDiscounter> discounters_ = new ArrayList<>();

    public UpperBoundEngine(MarketModelEvolver evolver,
                            List<MarketModelEvolver> innerEvolvers,
                            final MarketModelMultiProduct underlying,
                            final MarketModelExerciseValue rebate,
                            final MarketModelMultiProduct hedge,
                            final MarketModelExerciseValue hedgeRebate,
                            final ExerciseStrategy<CurveState> hedgeStrategy,
                            double initialNumeraireValue) {
        evolver_ = evolver;
        innerEvolvers_ = new ArrayList<>(innerEvolvers);
        composite_ = new MultiProductComposite();
        initialNumeraireValue_ = initialNumeraireValue;

        composite_.add(underlying);
        composite_.add(new ExerciseAdapter(rebate));
        composite_.add(hedge);
        composite_.add(new ExerciseAdapter(hedgeRebate));
        composite_.add(new DecoratedHedge(new CallSpecifiedMultiProduct(
                hedge, hedgeStrategy, new ExerciseAdapter(hedgeRebate))));
        composite_.finalize();

        underlyingOffset_ = 0;
        underlyingSize_ = underlying.numberOfProducts();
        rebateOffset_ = underlyingSize_;
        rebateSize_ = 1;
        hedgeOffset_ = underlyingSize_ + rebateSize_;
        hedgeSize_ = hedge.numberOfProducts();
        hedgeRebateOffset_ = underlyingSize_ + rebateSize_ + hedgeSize_;
        hedgeRebateSize_ = 1;

        numberOfProducts_ = composite_.numberOfProducts();

        final List<Double> evolutionTimes = composite_.evolution().evolutionTimes();
        numberOfSteps_ = evolutionTimes.size();

        isExerciseTime_ = Utilities.isInSubset(evolutionTimes,
                hedgeStrategy.exerciseTimes());

        numberCashFlowsThisStep_ = new ArrayList<>();
        cashFlowsGenerated_ = new ArrayList<>();
        for (int i = 0; i < numberOfProducts_; ++i) {
            numberCashFlowsThisStep_.add(0);
            List<MarketModelMultiProduct.CashFlow> cashFlows = new ArrayList<>();
            for (int j = 0; j < composite_.maxNumberOfCashFlowsPerProductPerStep(); ++j)
                cashFlows.add(new MarketModelMultiProduct.CashFlow());
            cashFlowsGenerated_.add(cashFlows);
        }

        final List<Double> cashFlowTimes = composite_.possibleCashFlowTimes();
        final List<Double> rateTimes = composite_.evolution().rateTimes();
        for (double cashFlowTime : cashFlowTimes)
            discounters_.add(new MarketModelDiscounter(cashFlowTime, rateTimes));
    }

    public void multiplePathValues(GenericRiskStatistics stats,
                                   int outerPaths,
                                   int innerPaths) {
        for (int i = 0; i < outerPaths; ++i) {
            double[] result = singlePathValue(innerPaths);
            stats.add(result[0], result[1]);
        }
    }

    public double[] singlePathValue(int innerPaths) {
        DecoratedHedge callable = (DecoratedHedge) composite_.item(4);
        final ExerciseStrategy<CurveState> strategy = callable.strategy();

        double maximumValue = MathUtils.QL_MIN_REAL;
        double numerairesHeld = 0.0;
        double weight = evolver_.startNewPath();
        callable.clear();
        composite_.reset();
        callable.disableCallability();
        double principalInNumerairePortfolio = 1.0;
        int exercise = 0;

        for (int k = 0; k < numberOfSteps_; ++k) {
            weight *= evolver_.advanceStep();

            composite_.nextTimeStep(evolver_.currentState(),
                    numberCashFlowsThisStep_,
                    cashFlowsGenerated_);

            // First, we accumulate cash flows from both the underlying...
            double underlyingCashFlows =
                    collectCashFlows(k,
                            principalInNumerairePortfolio,
                            underlyingOffset_,
                            underlyingOffset_ + underlyingSize_);

            // ...and the hedge
            double hedgeCashFlows =
                    collectCashFlows(k,
                            principalInNumerairePortfolio,
                            hedgeOffset_,
                            hedgeOffset_ + hedgeSize_);

            // we do the same for the rebates. Warning: this relies on
            // the fact that on each exercise date an ExerciseAdapter
            // generates a cash-flow equal to the exercise value
            double rebateCashFlow =
                    collectCashFlows(k,
                            principalInNumerairePortfolio,
                            rebateOffset_,
                            rebateOffset_ + rebateSize_);

            double hedgeRebateCashFlow =
                    collectCashFlows(k,
                            principalInNumerairePortfolio,
                            hedgeRebateOffset_,
                            hedgeRebateOffset_ + hedgeRebateSize_);

            numerairesHeld += underlyingCashFlows - hedgeCashFlows;

            // Second, we do the upper-bound thing
            if (isExerciseTime_[k]) {
                double unexercisedHedgeValue = 0.0;

                if (k != numberOfSteps_ - 1) {
                    // Here, we setup the relevant inner evolver and
                    // the decorated callable hedge such that their
                    // reset() method brings them to the current point
                    // rather than the beginning of the path.
                    MarketModelEvolver currentEvolver = innerEvolvers_.get(exercise++);
                    currentEvolver.setInitialState(evolver_.currentState());

                    callable.stopRecording();
                    callable.enableCallability();
                    callable.save();

                    // This allows us to write:
                    AccountingEngine engine = new AccountingEngine(currentEvolver, callable,
                            1.0); // this causes the result
                                  // to be in numeraire units
                    SequenceStatisticsInc innerStats =
                            new SequenceStatisticsInc(callable.numberOfProducts());
                    engine.multiplePathValues(innerStats, innerPaths);

                    final List<Double> values = innerStats.mean();
                    double sum = 0.0;
                    for (double value : values)
                        sum += value;
                    unexercisedHedgeValue = sum / principalInNumerairePortfolio;

                    callable.disableCallability();
                    callable.startRecording();
                }

                // Now, we can calculate the total value of our hedged
                // portfolio...
                double portfolioValue = numerairesHeld;
                if (strategy.exercise(evolver_.currentState())) {
                    // get the rebates...
                    portfolioValue += rebateCashFlow - hedgeRebateCashFlow;
                    // ...and reinvest to rehedge
                    numerairesHeld += unexercisedHedgeValue - hedgeRebateCashFlow;
                } else {
                    portfolioValue += rebateCashFlow - unexercisedHedgeValue;
                }

                // ...and use it to update the maximum value
                maximumValue = Math.max(maximumValue, portfolioValue);
            }

            // Lastly, we do the homework for next step (if any)
            if (k < numberOfSteps_ - 1) {
                // The numeraire might change between steps. This implies
                // that we might have to convert the numeraire bonds for
                // this step into a corresponding amount of numeraire
                // bonds for the next step. This can be done by changing
                // the principal of the numeraire and updating the number
                // of bonds in the numeraire portfolio accordingly.
                int numeraire = evolver_.numeraires().get(k);
                int nextNumeraire = evolver_.numeraires().get(k + 1);

                principalInNumerairePortfolio *=
                        evolver_.currentState().discountRatio(numeraire,
                                nextNumeraire);
            }
        }

        // finally, we update the maximum with the total accumulated
        // cash flows (in case we never exercised)
        maximumValue = Math.max(maximumValue, numerairesHeld);

        // all done; we just convert the result back to cash
        maximumValue *= initialNumeraireValue_;

        return new double[]{maximumValue, weight};
    }

    private double collectCashFlows(int currentStep,
                                    double principalInNumerairePortfolio,
                                    int beginProduct,
                                    int endProduct) {
        int numeraire = evolver_.numeraires().get(currentStep);

        double numeraireUnits = 0.0;
        // For each product in range...
        for (int i = beginProduct; i < endProduct; ++i) {
            // ...and for each cash flow...
            final List<MarketModelMultiProduct.CashFlow> cashflows =
                    cashFlowsGenerated_.get(i);
            for (int j = 0; j < numberCashFlowsThisStep_.get(i); ++j) {
                // ...convert the cash flow to numeraires. This is
                // done by calculating the number of numeraire bonds
                // corresponding to such cash flow...
                final MarketModelDiscounter discounter =
                        discounters_.get(cashflows.get(j).timeIndex);
                // ...and adding the newly bought bonds to the total
                numeraireUnits += cashflows.get(j).amount *
                        discounter.numeraireBonds(evolver_.currentState(),
                                numeraire);
            }
        }
        return numeraireUnits / principalInNumerairePortfolio;
    }
}
