package jquant.models.marketmodels;

import java.util.List;

/**
 * Official QuantLib {@code ql/models/marketmodels/pathwisemultiproduct.hpp} port.
 *
 * <p>Market-model pathwise product: like {@link MarketModelMultiProduct} but
 * it also returns the derivative of the payoff with respect to each forward
 * rate.
 */
public abstract class MarketModelPathwiseMultiProduct {

    public static class CashFlow {
        public int timeIndex = 0;
        public List<Double> amount;
    }

    public abstract List<Integer> suggestedNumeraires();

    public abstract EvolutionDescription evolution();

    public abstract List<Double> possibleCashFlowTimes();

    public abstract int numberOfProducts();

    public abstract int maxNumberOfCashFlowsPerProductPerStep();

    public abstract boolean alreadyDeflated();

    /** during simulation put product at start of path */
    public abstract void reset();

    /** return value indicates whether path is finished, true means done */
    public abstract boolean nextTimeStep(
            CurveState currentState,
            List<Integer> numberCashFlowsThisStep,
            List<List<CashFlow>> cashFlowsGenerated);

    /** returns a newly-allocated copy of itself */
    public abstract MarketModelPathwiseMultiProduct copy();
}
