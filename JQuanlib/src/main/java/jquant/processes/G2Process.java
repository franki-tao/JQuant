package jquant.processes;

import jquant.StochasticProcess;
import jquant.math.Array;
import jquant.math.Matrix;

import static jquant.math.CommonUtil.transpose;

//! %G2 stochastic process
/*! \ingroup processes */
public class G2Process extends StochasticProcess {
    private double x0_ = 0.0;
    private double y0_ = 0.0;
    private double a_;
    private double sigma_;
    private double b_;
    private double eta_;
    private double rho_;
    private OrnsteinUhlenbeckProcess xProcess_;
    private OrnsteinUhlenbeckProcess yProcess_;

    public G2Process(double a, double sigma, double b, double eta, double rho) {
        a_ = a;
        sigma_ = sigma;
        b_ = b;
        eta_ = eta;
        rho_ = rho;
        xProcess_ = new OrnsteinUhlenbeckProcess(a, sigma, 0, 0);
        yProcess_ = new OrnsteinUhlenbeckProcess(b, eta, 0, 0);
    }

    public int size() {
        return 2;
    }

    public Array initialValues() {
        double[] aa = {x0_, y0_};
        return new Array(aa);
    }

    public Array drift(double t, final Array x) {
        double[] aa = {xProcess_.drift(t, x.get(0)), yProcess_.drift(t, x.get(1))};
        return new Array(aa);
    }

    public Matrix diffusion(double t, final Array x) {
        /* the correlation matrix is
           |  1   rho |
           | rho   1  |
           whose square root (which is used here) is
           |  1          0       |
           | rho   sqrt(1-rho^2) |
        */
        Matrix tmp = new Matrix(2, 2);
        double sigma1 = sigma_;
        double sigma2 = eta_;
        tmp.set(0, 0, sigma1);
        tmp.set(0, 1, 0.0);
        tmp.set(1, 0, rho_ * sigma1);
        tmp.set(1, 1, Math.sqrt(1.0 - rho_ * rho_) * sigma2);
        return tmp;
    }

    public Array expectation(double t0, final Array x0, double dt) {
        double[] aa = {xProcess_.expectation(t0, x0.get(0), dt), yProcess_.expectation(t0, x0.get(1), dt)};
        return new Array(aa);
    }

    public Matrix stdDeviation(double t0, final Array x0, double dt) {
        /* the correlation matrix is
           |  1   rho |
           | rho   1  |
           whose square root (which is used here) is
           |  1          0       |
           | rho   sqrt(1-rho^2) |
        */
        Matrix tmp = new Matrix(2, 2);
        double sigma1 = xProcess_.stdDeviation(t0, x0.get(0), dt);
        double sigma2 = yProcess_.stdDeviation(t0, x0.get(1), dt);
        double expa = Math.exp(-a_ * dt), expb = Math.exp(-b_ * dt);
        double H = (rho_ * sigma_ * eta_) / (a_ + b_) * (1 - expa * expb);
        double den =
                (0.5 * sigma_ * eta_) * Math.sqrt((1 - expa * expa) * (1 - expb * expb) / (a_ * b_));
        double newRho = H / den;
        tmp.set(0, 0, sigma1);
        tmp.set(0, 1, 0d);
        tmp.set(1, 0, newRho * sigma2);
        tmp.set(1, 1, Math.sqrt(1.0 - newRho * newRho) * sigma2);
        return tmp;
    }

    public Matrix covariance(double t0, final Array x0, double dt) {
        Matrix sigma = stdDeviation(t0, x0, dt);
        return sigma.multipy(transpose(sigma));
    }

    public double x0() {
        return x0_;
    }

    public double y0() {
        return y0_;
    }

    public double a() {
        return a_;
    }

    public double sigma() {
        return sigma_;
    }

    public double b() {
        return b_;
    }

    public double eta() {
        return eta_;
    }

    public double rho() {
        return rho_;
    }
}
