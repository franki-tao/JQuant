package jquant.processes;

import jquant.StochasticProcess1D;

//! Geometric brownian-motion process
/*! This class describes the stochastic process governed by
    \f[
        dS(t, S)= \mu S dt + \sigma S dW_t.
    \f]

    \ingroup processes
*/
public abstract class GeometricBrownianMotionProcess extends StochasticProcess1D {
    protected double initialValue_;
    protected double mue_;
    protected double sigma_;

    public GeometricBrownianMotionProcess(double initialValue,
                                          double mue,
                                          double sigma) {
        super(new EulerDiscretization());
        initialValue_ = initialValue;
        mue_ = mue;
        sigma_ = sigma;
    }

    @Override
    public double x0() {
        return initialValue_;
    }

    @Override
    public double drift(double t, double x) {
        return mue_ * x;
    }

    @Override
    public double diffusion(double t, double x) {
        return sigma_ * x;
    }
}
