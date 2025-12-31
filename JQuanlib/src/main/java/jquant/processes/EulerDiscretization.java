package jquant.processes;

import jquant.StochasticProcess;
import jquant.StochasticProcess1D;
import jquant.StochasticProcess1DImpl;
import jquant.discretization;
import jquant.math.Array;
import jquant.math.Matrix;

import static jquant.math.CommonUtil.transpose;

public class EulerDiscretization implements discretization, StochasticProcess1DImpl {
    /*! Returns an approximation of the drift defined as
        \f$ \mu(t_0, x_0) \Delta t \f$.
    */
    @Override
    public double drift(StochasticProcess1D s, double t0, double x0, double dt) {
        return s.drift(t0, x0) * dt;
    }

    /*! Returns an approximation of the diffusion defined as
        \f$ \sigma(t_0, x_0) \sqrt{\Delta t} \f$.
    */
    @Override
    public double diffusion(StochasticProcess1D s, double t0, double x0, double dt) {
        return s.diffusion(t0, x0) * Math.sqrt(dt);
    }

    /*! Returns an approximation of the variance defined as
        \f$ \sigma(t_0, x_0)^2 \Delta t \f$.
    */
    @Override
    public double variance(StochasticProcess1D s, double t0, double x0, double dt) {
        double sigma = s.diffusion(t0, x0);
        return sigma * sigma * dt;
    }

    /*! Returns an approximation of the drift defined as
        \f$ \mu(t_0, \mathbf{x}_0) \Delta t \f$.
    */
    @Override
    public Array drift(StochasticProcess p, double t0, Array x0, double dt) {
        return p.drift(t0, x0).mutiply(dt);
    }

    /*! Returns an approximation of the diffusion defined as
        \f$ \sigma(t_0, \mathbf{x}_0) \sqrt{\Delta t} \f$.
    */
    @Override
    public Matrix diffusion(StochasticProcess p, double t0, Array x0, double dt) {
        return p.diffusion(t0, x0).multiply(Math.sqrt(dt));
    }

    /*! Returns an approximation of the covariance defined as
        \f$ \sigma(t_0, \mathbf{x}_0)^2 \Delta t \f$.
    */
    @Override
    public Matrix covariance(StochasticProcess p, double t0, Array x0, double dt) {
        Matrix sigma = p.diffusion(t0, x0);
        return sigma.multipy(transpose(sigma)).multiply(dt);
    }
}
