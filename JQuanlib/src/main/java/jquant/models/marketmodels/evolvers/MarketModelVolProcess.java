package jquant.models.marketmodels.evolvers;

import java.util.List;

/**
 * Official QuantLib {@code ql/models/marketmodels/evolvers/marketmodelvolprocess.hpp} port.
 *
 * <p>Abstract interface for an external variance process used by the
 * stochastic-volatility displaced-diffusion market-model evolver.
 */
public abstract class MarketModelVolProcess {

    public abstract int variatesPerStep();

    public abstract int numberSteps();

    public abstract void nextPath();

    public abstract double nextstep(List<Double> variates);

    public abstract double stepSd();

    public abstract List<Double> stateVariables();

    public abstract int numberStateVariables();
}
