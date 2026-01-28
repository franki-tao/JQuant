package jquant.processes;

import jquant.Compounding;
import jquant.Handle;
import jquant.StochasticProcess1D;
import jquant.termstructures.YieldTermStructure;
import jquant.time.Frequency;

import static jquant.Compounding.Continuous;
import static jquant.math.CommonUtil.QL_REQUIRE;
import static jquant.math.MathUtils.QL_EPSILON;

//! Hull-White stochastic process
/*! \ingroup processes */
public abstract class HullWhiteProcess extends StochasticProcess1D {
    protected OrnsteinUhlenbeckProcess process_;
    protected Handle<YieldTermStructure> h_;
    protected double a_;
    protected double sigma_;

    public HullWhiteProcess(final Handle<YieldTermStructure> h, double a, double sigma) {
        process_ = new OrnsteinUhlenbeckProcess(a, sigma,
                h.currentLink().forwardRate(0, 0, Continuous, Frequency.NO_FREQUENCY, false).rate(),
                0.0);
        h_ = h;
        a_ = a;
        sigma_ = sigma;
        QL_REQUIRE(a_ >= 0.0, "negative a given");
        QL_REQUIRE(sigma_ >= 0.0, "negative sigma given");
    }

    @Override
    public double x0() {
        return process_.x0();
    }

    @Override
    public double drift(double t, double x) {
        double alpha_drift = sigma_ * sigma_ / (2 * a_) * (1 - Math.exp(-2 * a_ * t));
        double shift = 0.0001;
        double f = h_.currentLink().forwardRate(t, t, Continuous, Frequency.NO_FREQUENCY, false).rate();
        double fup = h_.currentLink().forwardRate(t + shift, t + shift, Continuous, Frequency.NO_FREQUENCY, false).rate();
        double f_prime = (fup - f) / shift;
        alpha_drift += a_ * f + f_prime;
        return process_.drift(t, x) + alpha_drift;
    }

    @Override
    public double diffusion(double t, double x) {
        return process_.diffusion(t, x);
    }

    @Override
    public double expectation(double t0, double x0, double dt) {
        return process_.expectation(t0, x0, dt)
                + alpha(t0 + dt) - alpha(t0) * Math.exp(-a_ * dt);
    }

    @Override
    public double stdDeviation(double t0, double x0, double dt) {
        return process_.stdDeviation(t0, x0, dt);
    }

    @Override
    public double variance(double t0, double x0, double dt) {
        return process_.variance(t0, x0, dt);
    }

    public double a() {
        return a_;
    }

    public double sigma() {
        return sigma_;
    }

    public double alpha(double t) {
        double alfa = a_ > QL_EPSILON ?
                ((sigma_ / a_) * (1 - Math.exp(-a_ * t))) :
                sigma_ * t;
        alfa *= 0.5 * alfa;
        alfa += h_.currentLink().forwardRate(t, t, Continuous, Frequency.NO_FREQUENCY, false).rate();
        return alfa;
    }
}
