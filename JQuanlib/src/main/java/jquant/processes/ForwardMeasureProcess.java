package jquant.processes;

import jquant.StochasticProcess;
import jquant.discretization;

//! forward-measure stochastic process
/*! stochastic process whose dynamics are expressed in the forward
    measure.

    \ingroup processes
*/
public abstract class ForwardMeasureProcess extends StochasticProcess {
    protected double T_;
    protected ForwardMeasureProcess() {}
    protected ForwardMeasureProcess(double T) {
        T_ = T;
    }
    protected ForwardMeasureProcess(final discretization discretization) {
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
