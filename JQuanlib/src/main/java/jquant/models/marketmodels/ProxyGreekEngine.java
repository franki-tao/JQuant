package jquant.models.marketmodels;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Official QuantLib {@code ql/models/marketmodels/proxygreekengine.hpp/.cpp}
 * port.
 */
public class ProxyGreekEngine {

    private final MarketModelEvolver originalEvolver_;
    private final List<List<ConstrainedEvolver>> constrainedEvolvers_;
    private final List<List<List<Double>>> diffWeights_;
    private final List<Integer> startIndexOfConstraint_;
    private final List<Integer> endIndexOfConstraint_;
    private final MarketModelMultiProduct product_;

    private final double initialNumeraireValue_;
    private final int numberProducts_;

    // workspace
    private final List<Double> constraints_;
    private final boolean[] constraintsActive_;
    private final List<Double> numerairesHeld_;
    private final List<Integer> numberCashFlowsThisStep_;
    private final List<List<MarketModelMultiProduct.CashFlow>> cashFlowsGenerated_;
    private final List<MarketModelDiscounter> discounters_ = new ArrayList<>();

    public ProxyGreekEngine(MarketModelEvolver evolver,
                            List<List<ConstrainedEvolver>> constrainedEvolvers,
                            List<List<List<Double>>> diffWeights,
                            List<Integer> startIndexOfConstraint,
                            List<Integer> endIndexOfConstraint,
                            MarketModelMultiProduct product,
                            double initialNumeraireValue) {
        originalEvolver_ = evolver;
        constrainedEvolvers_ = new ArrayList<>();
        for (List<ConstrainedEvolver> inner : constrainedEvolvers)
            constrainedEvolvers_.add(new ArrayList<>(inner));
        diffWeights_ = new ArrayList<>();
        for (List<List<Double>> middle : diffWeights) {
            List<List<Double>> middleCopy = new ArrayList<>();
            for (List<Double> weights : middle)
                middleCopy.add(new ArrayList<>(weights));
            diffWeights_.add(middleCopy);
        }
        startIndexOfConstraint_ = new ArrayList<>(startIndexOfConstraint);
        endIndexOfConstraint_ = new ArrayList<>(endIndexOfConstraint);
        // Official Clone<MarketModelMultiProduct> copies the product.
        product_ = product.copy();
        initialNumeraireValue_ = initialNumeraireValue;
        numberProducts_ = product_.numberOfProducts();

        numerairesHeld_ = new ArrayList<>();
        numberCashFlowsThisStep_ = new ArrayList<>();
        cashFlowsGenerated_ = new ArrayList<>();
        for (int i = 0; i < numberProducts_; ++i) {
            numerairesHeld_.add(0.0);
            numberCashFlowsThisStep_.add(0);
            List<MarketModelMultiProduct.CashFlow> cashFlows = new ArrayList<>();
            for (int j = 0; j < product_.maxNumberOfCashFlowsPerProductPerStep(); ++j)
                cashFlows.add(new MarketModelMultiProduct.CashFlow());
            cashFlowsGenerated_.add(cashFlows);
        }

        final List<Double> cashFlowTimes = product_.possibleCashFlowTimes();
        final List<Double> rateTimes = product_.evolution().rateTimes();
        for (double cashFlowTime : cashFlowTimes)
            discounters_.add(new MarketModelDiscounter(cashFlowTime, rateTimes));

        final List<Double> evolutionTimes = product_.evolution().evolutionTimes();
        constraints_ = new ArrayList<>();
        for (int i = 0; i < evolutionTimes.size(); ++i)
            constraints_.add(0.0);
        constraintsActive_ = new boolean[evolutionTimes.size()];
    }

    public void singlePathValues(List<Double> values,
                                 List<List<List<Double>>> modifiedValues) {
        singleEvolverValues(originalEvolver_, values, true);
        for (int i = 0; i < constrainedEvolvers_.size(); ++i) {
            for (int j = 0; j < constrainedEvolvers_.get(i).size(); ++j) {
                constrainedEvolvers_.get(i).get(j).setThisConstraint(
                        constraints_, constraintsActive_);
                singleEvolverValues(constrainedEvolvers_.get(i).get(j),
                        modifiedValues.get(i).get(j));
            }
        }
    }

    public void multiplePathValues(SequenceStatisticsInc stats,
                                   List<List<SequenceStatisticsInc>> modifiedStats,
                                   int numberOfPaths) {
        int N = product_.numberOfProducts();

        List<Double> values = new ArrayList<>();
        for (int i = 0; i < N; ++i)
            values.add(0.0);
        List<List<List<Double>>> modifiedValues = new ArrayList<>();
        for (int i = 0; i < constrainedEvolvers_.size(); ++i) {
            List<List<Double>> inner = new ArrayList<>();
            for (int j = 0; j < constrainedEvolvers_.get(i).size(); ++j) {
                List<Double> entry = new ArrayList<>();
                for (int k = 0; k < N; ++k)
                    entry.add(0.0);
                inner.add(entry);
            }
            modifiedValues.add(inner);
        }

        List<Double> results = new ArrayList<>();
        for (int i = 0; i < N; ++i)
            results.add(0.0);

        for (int i = 0; i < numberOfPaths; ++i) {
            singlePathValues(values, modifiedValues);
            stats.add(values, 1.0);

            for (int j = 0; j < diffWeights_.size(); ++j) {
                for (int k = 0; k < diffWeights_.get(j).size(); ++k) {
                    List<Double> weights = diffWeights_.get(j).get(k);
                    for (int l = 0; l < N; ++l) {
                        results.set(l, weights.get(0) * values.get(l));
                        for (int n = 1; n < weights.size(); ++n)
                            results.set(l, results.get(l)
                                    + weights.get(n)
                                    * modifiedValues.get(j).get(n - 1).get(l));
                    }
                    modifiedStats.get(j).get(k).add(results, 1.0);
                }
            }
        }
    }

    private void singleEvolverValues(MarketModelEvolver evolver,
                                     List<Double> values) {
        singleEvolverValues(evolver, values, false);
    }

    private void singleEvolverValues(MarketModelEvolver evolver,
                                     List<Double> values,
                                     boolean storeRates) {
        for (int i = 0; i < numerairesHeld_.size(); ++i)
            numerairesHeld_.set(i, 0.0);
        double weight = evolver.startNewPath();
        product_.reset();
        double principalInNumerairePortfolio = 1.0;

        if (storeRates)
            // Official valarray<bool> scalar assignment: every element false.
            Arrays.fill(constraintsActive_, false);
        //            std::fill(constraintsActive_.begin(),
        //                    constraintsActive_.end(),
        //                  false);

        boolean done;
        do {
            int thisStep = evolver.currentStep();
            weight *= evolver.advanceStep();
            done = product_.nextTimeStep(evolver.currentState(),
                    numberCashFlowsThisStep_,
                    cashFlowsGenerated_);
            if (storeRates) {
                constraints_.set(thisStep, evolver.currentState().swapRate(
                        startIndexOfConstraint_.get(thisStep),
                        endIndexOfConstraint_.get(thisStep)));
                constraintsActive_[thisStep] = true;
            }

            int numeraire = evolver.numeraires().get(thisStep);

            // for each product...
            for (int i = 0; i < numberProducts_; ++i) {
                // ...and each cash flow...
                List<MarketModelMultiProduct.CashFlow> cashflows =
                        cashFlowsGenerated_.get(i);
                for (int j = 0; j < numberCashFlowsThisStep_.get(i); ++j) {
                    // ...convert the cash flow to numeraires.
                    // This is done by calculating the number of
                    // numeraire bonds corresponding to such cash flow...
                    MarketModelDiscounter discounter =
                            discounters_.get(cashflows.get(j).timeIndex);

                    double bonds = cashflows.get(j).amount *
                            discounter.numeraireBonds(evolver.currentState(),
                                    numeraire);

                    // ...and adding the newly bought bonds to the number
                    // of numeraires held.
                    numerairesHeld_.set(i, numerairesHeld_.get(i)
                            + weight * bonds / principalInNumerairePortfolio);
                }
            }

            if (!done) {
                // The numeraire might change between steps. This implies
                // that we might have to convert the numeraire bonds for
                // this step into a corresponding amount of numeraire
                // bonds for the next step. This can be done by changing
                // the principal of the numeraire and updating the number
                // of bonds in the numeraire portfolio accordingly.
                int nextNumeraire = evolver.numeraires().get(thisStep + 1);

                principalInNumerairePortfolio *=
                        evolver.currentState().discountRatio(numeraire,
                                nextNumeraire);
            }

        } while (!done);

        for (int i = 0; i < numerairesHeld_.size(); ++i)
            values.set(i, numerairesHeld_.get(i) * initialNumeraireValue_);
    }
}
