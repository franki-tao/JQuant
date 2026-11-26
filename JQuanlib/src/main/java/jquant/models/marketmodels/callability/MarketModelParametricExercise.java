package jquant.models.marketmodels.callability;

import jquant.methods.montecarlo.ParametricExercise;

import java.util.List;

/**
 * Official QuantLib
 * {@code ql/models/marketmodels/callability/marketmodelparametricexercise.hpp}
 * port.
 *
 * <p>Official {@code clone()} is expressed in Java as {@link #copy()}.
 */
public abstract class MarketModelParametricExercise
        implements MarketModelNodeDataProvider, ParametricExercise {

    @Override
    public List<Integer> numberOfData() {
        return numberOfVariables();
    }

    public abstract MarketModelParametricExercise copy();
}
