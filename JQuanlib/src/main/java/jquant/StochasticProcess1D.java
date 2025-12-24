package jquant;

import jquant.math.Array;
import jquant.math.Matrix;

import static jquant.math.CommonUtil.QL_REQUIRE;

//! 1-dimensional stochastic process
/*! This class describes a stochastic process governed by
    \f[
        dx_t = \mu(t, x_t)dt + \sigma(t, x_t)dW_t.
    \f]
*/
public abstract class StochasticProcess1D extends StochasticProcess {
    protected StochasticProcess1DImpl discretization_;
    public StochasticProcess1D() {}
    public StochasticProcess1D(StochasticProcess1DImpl discretization) {
        discretization_ = discretization;
    }
    //! \name 1-D stochastic process interface
    //@{
    //! returns the initial value of the state variable
    public abstract double x0();
    //! returns the drift part of the equation, i.e. \f$ \mu(t, x_t) \f$
    public abstract double drift(double t, double x);
    /*! \brief returns the diffusion part of the equation, i.e.
        \f$ \sigma(t, x_t) \f$
    */
    public abstract double diffusion(double t, double x);
    /*! returns the expectation
        \f$ E(x_{t_0 + \Delta t} | x_{t_0} = x_0) \f$
        of the process after a time interval \f$ \Delta t \f$
        according to the given discretization. This method can be
        overridden in derived classes which want to hard-code a
        particular discretization.
    */
    public double expectation(double t0, double x0, double dt) {
        return apply(x0, discretization_.drift(this, t0, x0, dt));
    }
    /*! returns the standard deviation
        \f$ S(x_{t_0 + \Delta t} | x_{t_0} = x_0) \f$
        of the process after a time interval \f$ \Delta t \f$
        according to the given discretization. This method can be
        overridden in derived classes which want to hard-code a
        particular discretization.
    */
    public double stdDeviation(double t0, double x0, double dt) {
        return discretization_.diffusion(this, t0, x0, dt);
    }
    /*! returns the variance
        \f$ V(x_{t_0 + \Delta t} | x_{t_0} = x_0) \f$
        of the process after a time interval \f$ \Delta t \f$
        according to the given discretization. This method can be
        overridden in derived classes which want to hard-code a
        particular discretization.
    */
    public double variance(double t0, double x0, double dt) {
        return discretization_.variance(this, t0, x0, dt);
    }
    /*! returns the asset value after a time interval \f$ \Delta t
        \f$ according to the given discretization. By default, it
        returns
        \f[
        E(x_0,t_0,\Delta t) + S(x_0,t_0,\Delta t) \cdot \Delta w
        \f]
        where \f$ E \f$ is the expectation and \f$ S \f$ the
        standard deviation.
    */
    public double evolve(double t0, double x0, double dt, double dw) {
        return apply(expectation(t0,x0,dt), stdDeviation(t0,x0,dt)*dw);
    }
    /*! applies a change to the asset value. By default, it
        returns \f$ x + \Delta x \f$.
    */
    public double apply(double x0, double dx) {
        return x0 + dx;
    }

    public int size() {
        return 1;
    }

    public Array initialValues() {
        return new Array(1, x0());
    }

    public Array drift(double t, final Array x) {
        QL_REQUIRE(x.size() == 1, "1-D array required");
        return new Array(1, drift(t, x.get(0)));
    }

    public Matrix diffusion(double t, final Array x) {
        QL_REQUIRE(x.size() == 1, "1-D array required");
        return new Matrix(1,1,diffusion(1,x.get(0)));
    }

    public Array expectation(double t0, final Array x0, double dt) {
        QL_REQUIRE(x0.size() == 1, "1-D array required");
        return new Array(1, expectation(t0, x0.get(0), dt));
    }

    public Matrix stdDeviation(double t0, final Array x0, double dt) {
        QL_REQUIRE(x0.size() == 1, "1-D array required");
        return new Matrix(1,1, stdDeviation(t0, x0.get(0), dt));
    }

    public Matrix covariance(double t0, final Array x0, double dt) {
        QL_REQUIRE(x0.size() == 1, "1-D array required");
        return new Matrix(1,1, variance(t0, x0.get(0), dt));
    }
    public Array evolve(double t0, final Array x0, double dt, final Array dw) {
        QL_REQUIRE(x0.size() == 1, "1-D array required");
        QL_REQUIRE(dw.size() == 1, "1-D array required");
        return new Array(1, evolve(t0, x0.get(0), dt, dw.get(0)));
    }
    public Array apply(final Array x0,final Array dx) {
        QL_REQUIRE(x0.size() == 1, "1-D array required");
        QL_REQUIRE(dx.size() == 1, "1-D array required");
        return new Array(1, apply(x0.get(0), dx.get(0)));
    }
}
