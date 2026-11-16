package jquant.methods.finitedifferences.schemes;

import jquant.math.Array;

/**
 * Time-stepping scheme used by {@code FdmBackwardSolver}.
 *
 * <p>Mirrors the {@code setStep}/{@code step} interface shared by the
 * QuantLib finite-difference schemes.
 */
public interface FdmScheme {
    void setStep(double dt);

    Array step(Array a, double t);
}
