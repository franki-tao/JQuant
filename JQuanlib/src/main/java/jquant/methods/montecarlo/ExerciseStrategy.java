package jquant.methods.montecarlo;

import java.util.List;

/**
 * Official QuantLib {@code ql/methods/montecarlo/exercisestrategy.hpp} port.
 *
 * <p>Official {@code clone()} is expressed in Java as {@link #copy()}.
 */
public interface ExerciseStrategy<State> {

    List<Double> exerciseTimes();

    List<Double> relevantTimes();

    void reset();

    boolean exercise(State currentState);

    void nextStep(State currentState);

    ExerciseStrategy<State> copy();
}
