package jquant.models.marketmodels.callability;

import jquant.methods.montecarlo.NodeData;
import jquant.models.marketmodels.CurveState;
import jquant.models.marketmodels.EvolutionDescription;
import jquant.models.marketmodels.MarketModelDiscounter;
import jquant.models.marketmodels.MarketModelEvolver;
import jquant.models.marketmodels.MarketModelMultiProduct;
import jquant.models.marketmodels.Utilities;

import java.util.ArrayList;
import java.util.List;

import static jquant.math.CommonUtil.QL_REQUIRE;

/**
 * Official QuantLib
 * {@code ql/models/marketmodels/callability/collectnodedata.hpp/.cpp} port.
 */
public final class CollectNodeData {

    private CollectNodeData() {
    }

    public static void collectNodeData(MarketModelEvolver evolver,
                                       MarketModelMultiProduct product,
                                       MarketModelNodeDataProvider dataProvider,
                                       MarketModelExerciseValue rebate,
                                       MarketModelExerciseValue control,
                                       int numberOfPaths,
                                       List<List<NodeData>> collectedData) {

        QL_REQUIRE(product.numberOfProducts() == 1,
                "a single product is required");

        // TODO: check that all objects have compatible evolutions
        // (same rate times; evolution times for product, basis
        // system, rebate and control must be subsets of the passed
        // evolution times; rebate, control and basis system must have
        // the same exercise---not evolution---times)

        List<Integer> numberCashFlowsThisStep = new ArrayList<>();
        numberCashFlowsThisStep.add(0);
        List<List<MarketModelMultiProduct.CashFlow>> cashFlowsGenerated = new ArrayList<>();
        List<MarketModelMultiProduct.CashFlow> cashFlows = new ArrayList<>();
        for (int j = 0; j < product.maxNumberOfCashFlowsPerProductPerStep(); ++j)
            cashFlows.add(new MarketModelMultiProduct.CashFlow());
        cashFlowsGenerated.add(cashFlows);

        List<Double> rateTimes = product.evolution().rateTimes();

        List<Double> cashFlowTimes = product.possibleCashFlowTimes();
        List<Double> rebateTimes = rebate.possibleCashFlowTimes();
        List<Double> controlTimes = control.possibleCashFlowTimes();

        List<MarketModelDiscounter> productDiscounters = new ArrayList<>();
        for (double cashFlowTime : cashFlowTimes)
            productDiscounters.add(new MarketModelDiscounter(cashFlowTime, rateTimes));

        List<MarketModelDiscounter> rebateDiscounters = new ArrayList<>();
        for (double rebateTime : rebateTimes)
            rebateDiscounters.add(new MarketModelDiscounter(rebateTime, rateTimes));

        List<MarketModelDiscounter> controlDiscounters = new ArrayList<>();
        for (double controlTime : controlTimes)
            controlDiscounters.add(new MarketModelDiscounter(controlTime, rateTimes));

        EvolutionDescription evolution = product.evolution();
        List<Integer> numeraires = evolver.numeraires();

        List<Double> evolutionTimes = evolution.evolutionTimes();

        boolean[] isProductTime = Utilities.isInSubset(evolutionTimes,
                product.evolution().evolutionTimes());
        boolean[] isRebateTime = Utilities.isInSubset(evolutionTimes,
                rebate.evolution().evolutionTimes());
        boolean[] isControlTime = Utilities.isInSubset(evolutionTimes,
                control.evolution().evolutionTimes());
        boolean[] isBasisTime = Utilities.isInSubset(evolutionTimes,
                dataProvider.evolution().evolutionTimes());
        boolean[] isExerciseTime = new boolean[evolutionTimes.size()];
        boolean[] v = rebate.isExerciseTime();
        int exercises = 0;
        int idx = 0;
        for (int i = 0; i < evolutionTimes.size(); ++i) {
            if (isRebateTime[i]) {
                if (v[idx++]) {
                    isExerciseTime[i] = true;
                    exercises++;
                }
            }
        }

        collectedData.clear();
        for (int i = 0; i < exercises + 1; ++i) {
            List<NodeData> row = new ArrayList<>();
            for (int j = 0; j < numberOfPaths; ++j)
                row.add(new NodeData());
            collectedData.add(row);
        }

        for (int i = 0; i < numberOfPaths; ++i) {
            evolver.startNewPath();
            product.reset();
            rebate.reset();
            control.reset();
            dataProvider.reset();
            double principalInNumerairePortfolio = 1.0;

            boolean done = false;
            int nextExercise = 0;
            collectedData.get(0).get(i).cumulatedCashFlows = 0.0;
            do {
                int currentStep = evolver.currentStep();
                evolver.advanceStep();
                CurveState currentState = evolver.currentState();
                int numeraire = numeraires.get(currentStep);

                if (isRebateTime[currentStep])
                    rebate.nextStep(currentState);
                if (isControlTime[currentStep])
                    control.nextStep(currentState);
                if (isBasisTime[currentStep])
                    dataProvider.nextStep(currentState);

                if (isExerciseTime[currentStep]) {
                    NodeData data = collectedData.get(nextExercise + 1).get(i);

                    MarketModelMultiProduct.CashFlow exerciseValue =
                            rebate.value(currentState);
                    data.exerciseValue =
                            exerciseValue.amount *
                                    rebateDiscounters.get(exerciseValue.timeIndex)
                                            .numeraireBonds(currentState, numeraire) /
                                    principalInNumerairePortfolio;

                    dataProvider.values(currentState, data.values);

                    MarketModelMultiProduct.CashFlow controlValue =
                            control.value(currentState);
                    data.controlValue =
                            controlValue.amount *
                                    controlDiscounters.get(controlValue.timeIndex)
                                            .numeraireBonds(currentState, numeraire) /
                                    principalInNumerairePortfolio;

                    data.cumulatedCashFlows = 0.0;

                    data.isValid = true;

                    ++nextExercise;
                }

                if (isProductTime[currentStep]) {
                    done = product.nextTimeStep(currentState,
                            numberCashFlowsThisStep,
                            cashFlowsGenerated);

                    for (int j = 0; j < numberCashFlowsThisStep.get(0); ++j) {
                        MarketModelMultiProduct.CashFlow cf = cashFlowsGenerated.get(0).get(j);
                        collectedData.get(nextExercise).get(i).cumulatedCashFlows +=
                                cf.amount *
                                        productDiscounters.get(cf.timeIndex)
                                                .numeraireBonds(currentState, numeraire) /
                                        principalInNumerairePortfolio;
                    }
                }

                if (!done) {
                    int nextNumeraire = numeraires.get(currentStep + 1);
                    principalInNumerairePortfolio *=
                            currentState.discountRatio(numeraire, nextNumeraire);
                }
            }
            while (!done);

            // fill the remaining (un)collected data with nulls
            for (int j = nextExercise; j < exercises; ++j) {
                NodeData data = collectedData.get(j + 1).get(i);
                data.exerciseValue = 0.0;
                data.controlValue = 0.0;
                data.cumulatedCashFlows = 0.0;
                data.isValid = false;
            }
        }
    }
}
