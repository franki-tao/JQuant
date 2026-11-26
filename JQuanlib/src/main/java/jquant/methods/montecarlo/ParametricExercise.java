package jquant.methods.montecarlo;

import java.util.List;

/**
 * Official QuantLib
 * {@code ql/methods/montecarlo/parametricexercise.hpp} port.
 */
public interface ParametricExercise {

    // possibly different for each exercise
    List<Integer> numberOfVariables();

    List<Integer> numberOfParameters();

    boolean exercise(int exerciseNumber,
                     List<Double> parameters,
                     List<Double> variables);

    void guess(int exerciseNumber,
               List<Double> parameters);
}
