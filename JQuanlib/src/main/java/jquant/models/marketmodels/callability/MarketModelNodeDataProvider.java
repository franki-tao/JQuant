package jquant.models.marketmodels.callability;

import jquant.models.marketmodels.CurveState;
import jquant.models.marketmodels.EvolutionDescription;

import java.util.List;

/**
 * Official QuantLib
 * {@code ql/models/marketmodels/callability/nodedataprovider.hpp} port.
 */
public interface MarketModelNodeDataProvider {

    int numberOfExercises();

    // possibly different for each exercise
    List<Integer> numberOfData();

    // including any time at which state should be updated
    EvolutionDescription evolution();

    void nextStep(CurveState currentState);

    void reset();

    // whether or not evolution times are exercise times
    boolean[] isExerciseTime();

    void values(CurveState currentState, List<Double> results);
}
