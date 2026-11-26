package jquant.models.marketmodels;

import java.util.List;

/**
 * Official QuantLib {@code ql/models/marketmodels/multiproduct.hpp} port.
 *
 * <p>The official {@code clone()} is expressed in Java as {@link #copy()}.
 */
public abstract class MarketModelMultiProduct {

    public static class CashFlow {
        public int timeIndex;
        public double amount;
    }

    public abstract List<Integer> suggestedNumeraires();

    public abstract EvolutionDescription evolution();

    public abstract List<Double> possibleCashFlowTimes();

    public abstract int numberOfProducts();

    public abstract int maxNumberOfCashFlowsPerProductPerStep();

    /** during simulation put product at start of path */
    public abstract void reset();

    /** return value indicates whether path is finished, true means done */
    public abstract boolean nextTimeStep(CurveState currentState,
                                         List<Integer> numberCashFlowsThisStep,
                                         List<List<CashFlow>> cashFlowsGenerated);

    /** returns a newly-allocated copy of itself */
    public abstract MarketModelMultiProduct copy();
}
