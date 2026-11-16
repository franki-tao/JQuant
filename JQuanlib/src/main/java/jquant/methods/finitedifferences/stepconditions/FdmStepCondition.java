package jquant.methods.finitedifferences.stepconditions;

import jquant.math.Array;

/**
 * Condition applied at every finite-difference time step.
 *
 * <p>Port of {@code StepCondition<Array>} from
 * {@code ql/methods/finitedifferences/stepcondition.hpp}.
 */
public interface FdmStepCondition {
    void applyTo(Array a, double t);
}
