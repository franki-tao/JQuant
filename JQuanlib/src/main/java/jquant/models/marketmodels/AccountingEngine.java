package jquant.models.marketmodels;

import java.util.ArrayList;
import java.util.List;

/**
 * Official QuantLib {@code ql/models/marketmodels/accountingengine.hpp/.cpp} port.
 */
public class AccountingEngine {

    private final MarketModelEvolver evolver_;
    private final MarketModelMultiProduct product_;

    private final double initialNumeraireValue_;
    private final int numberProducts_;

    // workspace
    private final List<Double> numerairesHeld_;
    private final List<Integer> numberCashFlowsThisStep_;
    private final List<List<MarketModelMultiProduct.CashFlow>> cashFlowsGenerated_;
    private final List<MarketModelDiscounter> discounters_ = new ArrayList<>();

    public AccountingEngine(MarketModelEvolver evolver,
                            MarketModelMultiProduct product,
                            double initialNumeraireValue) {
        evolver_ = evolver;
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
    }

    public void multiplePathValues(SequenceStatisticsInc stats,
                                   int numberOfPaths) {
        List<Double> values = new ArrayList<>();
        for (int i = 0; i < product_.numberOfProducts(); ++i)
            values.add(0.0);
        for (int i = 0; i < numberOfPaths; ++i) {
            double weight = singlePathValues(values);
            stats.add(values, weight);
        }
    }

    private double singlePathValues(List<Double> values) {
        for (int i = 0; i < numerairesHeld_.size(); ++i)
            numerairesHeld_.set(i, 0.0);
        double weight = evolver_.startNewPath();
        product_.reset();
        double principalInNumerairePortfolio = 1.0;

        boolean done;
        do {
            int thisStep = evolver_.currentStep();
            weight *= evolver_.advanceStep();
            done = product_.nextTimeStep(evolver_.currentState(),
                    numberCashFlowsThisStep_,
                    cashFlowsGenerated_);
            int numeraire = evolver_.numeraires().get(thisStep);

            // for each product...
            for (int i = 0; i < numberProducts_; ++i) {
                // ...and each cash flow...
                List<MarketModelMultiProduct.CashFlow> cashflows = cashFlowsGenerated_.get(i);
                for (int j = 0; j < numberCashFlowsThisStep_.get(i); ++j) {
                    // ...convert the cash flow to numeraires.
                    MarketModelDiscounter discounter =
                            discounters_.get(cashflows.get(j).timeIndex);

                    double bonds = cashflows.get(j).amount *
                            discounter.numeraireBonds(evolver_.currentState(), numeraire);

                    numerairesHeld_.set(i,
                            numerairesHeld_.get(i) + bonds / principalInNumerairePortfolio);
                }
            }

            if (!done) {
                // The numeraire might change between steps...
                int nextNumeraire = evolver_.numeraires().get(thisStep + 1);

                principalInNumerairePortfolio *=
                        evolver_.currentState().discountRatio(numeraire, nextNumeraire);
            }

        } while (!done);

        for (int i = 0; i < numerairesHeld_.size(); ++i)
            values.set(i, numerairesHeld_.get(i) * initialNumeraireValue_);

        return weight;
    }
}
