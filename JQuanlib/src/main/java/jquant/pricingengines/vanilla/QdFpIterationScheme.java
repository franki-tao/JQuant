package jquant.pricingengines.vanilla;

import jquant.math.integrals.Integrator;

/**
 * Iteration scheme for the QD fixed-point American engine.
 *
 * <p>Ported from {@code QdFpIterationScheme} in
 * {@code ql/pricingengines/vanilla/qdfpamericanengine.hpp}.
 */
public interface QdFpIterationScheme {
    int getNumberOfChebyshevInterpolationNodes();

    int getNumberOfNaiveFixedPointSteps();

    int getNumberOfJacobiNewtonFixedPointSteps();

    Integrator getFixedPointIntegrator();

    Integrator getExerciseBoundaryToPriceIntegrator();
}
