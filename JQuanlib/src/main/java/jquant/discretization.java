package jquant;

import jquant.math.Array;
import jquant.math.Matrix;

//! discretization of a stochastic process over a given time interval
public interface discretization {
    Array drift(final StochasticProcess p, double t0, final Array x0, double dt);
    Matrix diffusion(final StochasticProcess p, double t0, final Array x0, double dt);
    Matrix covariance(final StochasticProcess p, double t0, final Array x0, double dt);
}
