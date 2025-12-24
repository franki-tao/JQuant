package jquant;

import jquant.math.Array;
import jquant.math.Matrix;

//! discretization of a stochastic process over a given time interval
public abstract class discretization {
    public abstract Array drift(final StochasticProcess p, double t0, final Array x0, double dt);
    public abstract Matrix diffusion(final StochasticProcess p, double t0, final Array x0, double dt);
    public abstract Matrix covariance(final StochasticProcess p, double t0, final Array x0, double dt);
}
