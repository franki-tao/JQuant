package jquant.processes;

import jquant.StochasticProcess;
import jquant.StochasticProcess1D;
import jquant.math.Array;
import jquant.math.Matrix;

import static jquant.math.CommonUtil.transpose;

//! Euler end-point discretization for stochastic processes
/*! \ingroup processes */
public class EndEulerDiscretization {
    /*! Returns an approximation of the drift defined as
        \f$ \mu(t_0 + \Delta t, \mathbf{x}_0) \Delta t \f$.
    */
    public Array drift(final StochasticProcess process,
                       double t0, final Array x0,
                       double dt) {
        return process.drift(t0 + dt, x0).mutiply(dt);
    }

    /*! Returns an approximation of the drift defined as
        \f$ \mu(t_0 + \Delta t, x_0) \Delta t \f$.
    */
    public double drift(final StochasticProcess1D process,
                        double t0, double x0, double dt) {
        return process.drift(t0 + dt, x0) * dt;
    }

    /*! Returns an approximation of the diffusion defined as
        \f$ \sigma(t_0 + \Delta t, \mathbf{x}_0) \sqrt{\Delta t} \f$.
    */
    public Matrix diffusion(final StochasticProcess process,
                            double t0,
                            final Array x0,
                            double dt) {
        return process.diffusion(t0 + dt, x0).multiply(Math.sqrt(dt));
    }

    /*! Returns an approximation of the diffusion defined as
        \f$ \sigma(t_0 + \Delta t, x_0) \sqrt{\Delta t} \f$.
    */
    public double diffusion(final StochasticProcess1D process,
                            double t0, double x0, double dt) {
        return process.diffusion(t0 + dt, x0) * Math.sqrt(dt);
    }

    /*! Returns an approximation of the covariance defined as
        \f$ \sigma(t_0 + \Delta t, \mathbf{x}_0)^2 \Delta t \f$.
    */
    public Matrix covariance(final StochasticProcess process,
                             double t0,
                             final Array x0,
                             double dt) {
        Matrix sigma = process.diffusion(t0 + dt, x0);
        return sigma.multipy(transpose(sigma)).multiply(dt);
    }

    /*! Returns an approximation of the variance defined as
        \f$ \sigma(t_0 + \Delta t, x_0)^2 \Delta t \f$.
    */
    public double variance(final StochasticProcess1D process,
                           double t0, double x0, double dt) {
        double sigma = process.diffusion(t0 + dt, x0);
        return sigma * sigma * dt;
    }
}
