package jquant.processes;

import jquant.StochasticProcess1D;
import jquant.math.distributions.CumulativeNormalDistribution;

import static jquant.math.CommonUtil.QL_REQUIRE;

//! CoxIngersollRoss process class
/*! This class describes the CoxIngersollRoss process governed by
    \f[
        dx(t) = k (\theta - x(t)) dt + \sigma \sqrt{x(t)} dW(t).
    \f]

    The process is discretized using the Quadratic Exponential scheme.
    For details see Leif Andersen,
    Efficient Simulation of the Heston Stochastic Volatility Model.

    \ingroup processes
*/
public class CoxIngersollRossProcess extends StochasticProcess1D {
    private double x0_, speed_, level_;
    private double volatility_;

    public CoxIngersollRossProcess(double speed, double vol, double x0, double level) {
        x0_ = x0;
        speed_ = speed;
        level_ = level;
        volatility_ = vol;
        QL_REQUIRE(volatility_ >= 0.0, "negative volatility given");
    }

    public double x0() {
        return x0_;
    }

    public double speed() {
        return speed_;
    }

    public double volatility() {
        return volatility_;
    }

    public double level() {
        return level_;
    }

    public double drift(double t, double x) {
        return speed_ * (level_ - x);
    }

    public double diffusion(double t, double x) {
        return volatility_;
    }

    public double expectation(double t, double x0, double dt) {
        return level_ + (x0 - level_) * Math.exp(-speed_ * dt);
    }

    public double stdDeviation(double t, double x0, double dt) {
        return Math.sqrt(variance(t, x0, dt));
    }

    public double variance(double t0, double x0, double dt) {
        double exponent1 = Math.exp(-speed_ * dt);
        double exponent2 = Math.exp(-2 * speed_ * dt);
        double fraction = (volatility_ * volatility_) / speed_;

        return x0_ * fraction * (exponent1 - exponent2) + level_ * fraction * (1 - exponent1) * (1 - exponent1);
    }

    public double evolve(double t0, double x0, double dt, double dw) {
        double result;

        final double ex = Math.exp(-speed_ * dt);

        final double m = level_ + (x0 - level_) * ex;
        final double s2 = x0 * volatility_ * volatility_ * ex / speed_ * (1 - ex)
                + level_ * volatility_ * volatility_ / (2 * speed_) * (1 - ex) * (1 - ex);
        final double psi = s2 / (m * m);

        if (psi <= 1.5) {
            final double b2 = 2 / psi - 1 + Math.sqrt(2 / psi * (2 / psi - 1));
            final double b = Math.sqrt(b2);
            final double a = m / (1 + b2);

            result = a * (b + dw) * (b + dw);
        } else {
            final double p = (psi - 1) / (psi + 1);
            final double beta = (1 - p) / m;

            final double u = new CumulativeNormalDistribution().value(dw);

            result = ((u <= p) ? 0.0 : (Math.log((1 - p) / (1 - u)) / beta));
        }

        return result;
    }
}
