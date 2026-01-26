package jquant.processes;

import jquant.StochasticProcess1D;
import jquant.StochasticProcess1DImpl;

//! forward-measure 1-D stochastic process
/*! 1-D stochastic process whose dynamics are expressed in the
    forward measure.

    \ingroup processes
*/
public abstract class ForwardMeasureProcess1D extends StochasticProcess1D {
    protected double T_;
    protected ForwardMeasureProcess1D() {}
    protected ForwardMeasureProcess1D(double T) {
        T_ = T;
    }
    protected ForwardMeasureProcess1D(final StochasticProcess1DImpl discretization) {
        super(discretization);
    }
    public void setForwardMeasureTime(double T) {
        T_ = T;
        notifyObservers();
    }
    public double getForwardMeasureTime() {
        return T_;
    }
}
