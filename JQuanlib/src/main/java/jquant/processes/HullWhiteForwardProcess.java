package jquant.processes;

import jquant.Compounding;
import jquant.Handle;
import jquant.termstructures.YieldTermStructure;
import jquant.time.Frequency;

import static jquant.Compounding.Continuous;
import static jquant.math.MathUtils.QL_EPSILON;

//! %Forward Hull-White stochastic process
/*! \ingroup processes */
public class HullWhiteForwardProcess extends ForwardMeasureProcess1D {
    protected OrnsteinUhlenbeckProcess process_;
    protected Handle<YieldTermStructure> h_;
    protected double a_;
    protected double sigma_;

    public HullWhiteForwardProcess(final Handle<YieldTermStructure> h,
                                   double a,
                                   double sigma) {
        process_ = new OrnsteinUhlenbeckProcess(a, sigma,
                h.currentLink().forwardRate(0, 0, Continuous, Frequency.NO_FREQUENCY, false).rate(),
                0);
        h_ = h;
        a_ = a;
        sigma_ = sigma;
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
        return process_.drift(t, x) + alpha_drift - B(t, T_) * sigma_ * sigma_;
    }

    @Override
    public double diffusion(double t, double x) {
        return process_.diffusion(t, x);
    }

    @Override
    public double expectation(double t0, double x0, double dt) {
        return process_.expectation(t0, x0, dt)
                + alpha(t0 + dt) - alpha(t0) * Math.exp(-a_ * dt)
                - M_T(t0, t0 + dt, T_);
    }

    @Override
    public double stdDeviation(double t0, double x0, double dt) {
        return process_.stdDeviation(t0, x0, dt);
    }

    @Override
    public double variance(double t0, double x0, double dt) {
        return process_.variance(t0, x0, dt);
    }

    public double alpha(double t) {
        double alfa = a_ > QL_EPSILON ? ((sigma_ / a_) * (1 - Math.exp(-a_ * t))) : sigma_ * t;
        alfa *= 0.5 * alfa;
        alfa += h_.currentLink().forwardRate(t, t, Continuous, Frequency.NO_FREQUENCY, false).rate();
        return alfa;
    }

    public double M_T(double s, double t, double T) {
        if (a_ > QL_EPSILON) {
            double coeff = (sigma_ * sigma_) / (a_ * a_);
            double exp1 = Math.exp(-a_ * (t - s));
            double exp2 = Math.exp(-a_ * (T - t));
            double exp3 = Math.exp(-a_ * (T + t - 2.0 * s));
            return coeff * (1 - exp1) - 0.5 * coeff * (exp2 - exp3);
        } else {
            // low-a algebraic limit
            double coeff = (sigma_ * sigma_) / 2.0;
            return coeff * (t - s) * (2.0 * T - t - s);
        }
    }

    public double B(double t, double T) {
        return a_ > QL_EPSILON ? (1 / a_ * (1 - Math.exp(-a_ * (T - t)))) : T - t;
    }

    public double a() {
        return a_;
    }

    public double sigma() {
        return sigma_;
    }
}
