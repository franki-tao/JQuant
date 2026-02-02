package jquant.processes;

import jquant.StochasticProcess1D;

import static jquant.math.CommonUtil.QL_REQUIRE;
import static jquant.math.MathUtils.QL_EPSILON;

public class OrnsteinUhlenbeckProcess extends StochasticProcess1D {
    private double x0_;
    private double speed_;
    private double level_;
    private double volatility_;
    // level = 0.0, x0 = 0.0
    public OrnsteinUhlenbeckProcess(double speed, double vol, double x0, double level) {
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

    public double variance(double t, double x, double dt) {
        if (Math.abs(speed_) < Math.sqrt(QL_EPSILON)) {
            // algebraic limit for small speed
            return volatility_ * volatility_ * dt;
        } else {
            return 0.5 * volatility_ * volatility_ / speed_ *
                    (1.0 - Math.exp(-2.0 * speed_ * dt));
        }
    }

    public double stdDeviation(double t, double x0, double dt) {
        return Math.sqrt(variance(t, x0, dt));
    }
}
