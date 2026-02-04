package jquant.processes;

import jquant.StochasticProcess1D;
import jquant.StochasticProcess1DImpl;

//! Square-root process class
/*! This class describes a square-root process governed by
    \f[
        dx = a (b - x_t) dt + \sigma \sqrt{x_t} dW_t.
    \f]

    \ingroup processes
*/
public class SquareRootProcess extends StochasticProcess1D {
    private double x0_;
    private double mean_;
    private double speed_;
    private double volatility_;

    public SquareRootProcess(double b, double a, double sigma, double x0,
                             final StochasticProcess1DImpl disc) {
        super(disc);
        x0_ = x0;
        mean_ = b;
        speed_ = a;
        volatility_ = sigma;
    }

    @Override
    public double x0() {
        return x0_;
    }

    @Override
    public double drift(double t, double x) {
        return speed_ * (mean_ - x);
    }

    @Override
    public double diffusion(double t, double x) {
        return volatility_ * Math.sqrt(x);
    }

    public double a() {
        return speed_;
    }

    public double b() {
        return mean_;
    }

    public double sigma() {
        return volatility_;
    }
}
