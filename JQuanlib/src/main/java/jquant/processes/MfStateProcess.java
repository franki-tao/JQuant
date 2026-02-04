package jquant.processes;

import jquant.StochasticProcess1D;
import jquant.math.Array;

import static jquant.math.CommonUtil.QL_REQUIRE;
import static jquant.math.MathUtils.QL_EPSILON;

//! Markov functional state process class
/*! This class describes the process governed by
    \f[ dx = \sigma(t) e^{at} dW(t) \f]
    \ingroup processes
*/
public class MfStateProcess extends StochasticProcess1D {
    private double reversion_;
    private boolean reversionZero_;
    private final Array times_;
    private final Array vols_;

    public MfStateProcess(double reversion, final Array times, final Array vols) {
        reversion_ = reversion;
        times_ = times;
        vols_ = vols;
        if (reversion_ < QL_EPSILON && -reversion_ < QL_EPSILON)
            reversionZero_ = true;
        QL_REQUIRE(times.size() == vols.size() - 1,
                "number of volatilities ("
                        + vols.size() + ") compared to number of times ("
                        + times_.size() + " must be bigger by one");
        for (int i = 0; i < (times.size()) - 1; i++)
            QL_REQUIRE(times.get(i) < times.get(i + 1), "times must be increasing ("
                    + times.get(i) + "@" + i
                    + " , " + times.get(i + 1)
                    + "@" + (i + 1) + ")");
        for (int i = 0; i < vols.size(); i++)
            QL_REQUIRE(vols.get(i) >= 0.0, "volatilities must be non negative ("
                    + vols.get(i) + "@" + i + ")");
    }

    @Override
    public double x0() {
        return 0.0;
    }

    @Override
    public double drift(double t, double x) {
        return 0.0;
    }

    @Override
    public double diffusion(double t, double x) {
        int i = times_.upperIndex(t);
        return vols_.get(i);
    }

    @Override
    public double expectation(double t, double x0, double dt) {
        return x0;
    }

    @Override
    public double stdDeviation(double t, double x0, double dt) {
        return Math.sqrt(variance(t, x0, dt));
    }

    @Override
    public double variance(double t, double x, double dt) {

        if (dt < QL_EPSILON)
            return 0.0;
        if (times_.empty())
            return reversionZero_ ? dt
                    : 1.0 / (2.0 * reversion_) *
                    (Math.exp(2.0 * reversion_ * (t + dt)) -
                            Math.exp(2.0 * reversion_ * t));

        int i = times_.upperIndex(t);
        int j = times_.upperIndex(t + dt);

        double v = 0.0;

        for (int k = i; k < j; k++) {
            if (reversionZero_)
                v += vols_.get(k) * vols_.get(k) *
                        (times_.get(k) - Math.max(k > 0 ? times_.get(k - 1) : 0.0, t));
            else
                v += 1.0 / (2.0 * reversion_) * vols_.get(k) * vols_.get(k) *
                        (Math.exp(2.0 * reversion_ * times_.get(k)) -
                                Math.exp(2.0 * reversion_ *
                                        Math.max(k > 0 ? times_.get(k - 1) : 0.0, t)));
        }

        if (reversionZero_)
            v += vols_.get(j) * vols_.get(j) *
                    (t + dt - Math.max(j > 0 ? times_.get(j - 1) : 0.0, t));
        else
            v += 1.0 / (2.0 * reversion_) * vols_.get(j) * vols_.get(j) *
                    (Math.exp(2.0 * reversion_ * (t + dt)) -
                            Math.exp(2.0 * reversion_ *
                                    (Math.max(j > 0 ? times_.get(j - 1) : 0.0, t))));

        return v;
    }
}
