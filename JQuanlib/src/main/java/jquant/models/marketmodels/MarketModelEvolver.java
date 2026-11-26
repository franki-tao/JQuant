package jquant.models.marketmodels;

import java.util.List;

/**
 * Official QuantLib {@code ql/models/marketmodels/evolver.hpp} port.
 */
public abstract class MarketModelEvolver {

    public abstract List<Integer> numeraires();

    public abstract double startNewPath();

    public abstract double advanceStep();

    public abstract int currentStep();

    public abstract CurveState currentState();

    public abstract void setInitialState(CurveState cs);
}
