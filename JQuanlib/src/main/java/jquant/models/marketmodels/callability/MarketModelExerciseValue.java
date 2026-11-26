package jquant.models.marketmodels.callability;

import jquant.models.marketmodels.CurveState;
import jquant.models.marketmodels.EvolutionDescription;
import jquant.models.marketmodels.MarketModelMultiProduct;

import java.util.List;

/**
 * Official QuantLib {@code ql/models/marketmodels/callability/exercisevalue.hpp} port.
 *
 * <p>Official {@code clone()} is expressed in Java as {@link #copy()}.
 */
public interface MarketModelExerciseValue {

    int numberOfExercises();

    EvolutionDescription evolution();

    List<Double> possibleCashFlowTimes();

    void nextStep(CurveState currentState);

    void reset();

    boolean[] isExerciseTime();

    MarketModelMultiProduct.CashFlow value(CurveState currentState);

    MarketModelExerciseValue copy();
}
