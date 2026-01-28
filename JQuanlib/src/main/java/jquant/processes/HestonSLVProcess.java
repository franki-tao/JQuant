package jquant.processes;

import jquant.Handle;
import jquant.Quote;
import jquant.StochasticProcess;
import jquant.math.Array;
import jquant.math.Matrix;
import jquant.math.distributions.CumulativeNormalDistribution;
import jquant.termstructures.YieldTermStructure;
import jquant.termstructures.volatility.equityfx.LocalVolTermStructure;
import jquant.time.Date;
import jquant.time.Frequency;

import static jquant.Compounding.Continuous;

public class HestonSLVProcess extends StochasticProcess {
    private double kappa_;
    private double theta_;
    private double sigma_;
    private double rho_;
    private double v0_;
    private double mixingFactor_;
    private double mixedSigma_;

    private HestonProcess hestonProcess_;
    private LocalVolTermStructure leverageFct_;

    //mixingFactor = 1.0
    public HestonSLVProcess(final HestonProcess hestonProcess,
                            LocalVolTermStructure leverageFct,
                            final double mixingFactor) {
        mixingFactor_ = mixingFactor;
        hestonProcess_ = hestonProcess;
        leverageFct_ = leverageFct;
        registerWith(hestonProcess);
        setParameters();
    }

    @Override
    public int size() {
        return 2;
    }

    @Override
    public int factors() {
        return 2;
    }

    @Override
    public void update() {
        setParameters();
        super.update();
    }

    @Override
    public Array initialValues() {
        return hestonProcess_.initialValues();
    }

    @Override
    public Array apply(final Array x0, final Array dx) {
        return hestonProcess_.apply(x0, dx);
    }

    @Override
    public Array drift(double t, final Array x) {
        Array tmp = new Array(2);

        final double vol =
                Math.max(1e-8, Math.sqrt(x.get(1)) * leverageFct_.localVol(t, x.get(0), true));

        tmp.set(0, riskFreeRate().getValue().forwardRate(t, t, Continuous, Frequency.ANNUAL, false).rate()
                - dividendYield().getValue().forwardRate(t, t, Continuous, Frequency.ANNUAL, false).rate()
                - 0.5 * vol * vol);

        tmp.set(1, kappa_ * (theta_ - x.get(1)));

        return tmp;
    }

    @Override
    public Matrix diffusion(double t, final Array x) {
        final double vol =
                Math.max(1e-8, Math.sqrt(x.get(1)) * leverageFct_.localVol(t, x.get(0), true));

        final double sigma2 = mixedSigma_ * Math.sqrt(x.get(1));
        final double sqrhov = Math.sqrt(1.0 - rho_ * rho_);

        Matrix tmp = new Matrix(2, 2);
        tmp.set(0, 0, vol);
        tmp.set(0, 1, 0.0);
        tmp.set(1, 0, rho_ * sigma2);
        tmp.set(1, 1, sqrhov * sigma2);

        return tmp;
    }

    @Override
    public Array evolve(double t0, final Array x0, double dt, final Array dw) {
        Array retVal = new Array(2);
        final double ex = Math.exp(-kappa_ * dt);

        final double m = theta_ + (x0.get(1) - theta_) * ex;
        final double s2 = x0.get(1) * mixedSigma_ * mixedSigma_ * ex / kappa_ * (1 - ex)
                + theta_ * mixedSigma_ * mixedSigma_ / (2 * kappa_) * (1 - ex) * (1 - ex);
        final double psi = s2 / (m * m);

        if (psi < 1.5) {
            final double b2 = 2 / psi - 1 + Math.sqrt(2 / psi * (2 / psi - 1));
            final double b = Math.sqrt(b2);
            final double a = m / (1 + b2);
            retVal.set(1, a * (b + dw.get(1)) * (b + dw.get(1)));
        } else {
            final double p = (psi - 1) / (psi + 1);
            final double beta = (1 - p) / m;
            final double u = new CumulativeNormalDistribution().value(dw.get(1));
            retVal.set(1, ((u <= p) ? (0.0) : Math.log((1 - p) / (1 - u)) / beta));
        }

        final double mu = riskFreeRate().getValue().forwardRate(t0, t0 + dt, Continuous, Frequency.ANNUAL, false).rate()
                - dividendYield().getValue().forwardRate(t0, t0 + dt, Continuous, Frequency.ANNUAL, false).rate();

        final double rho1 = Math.sqrt(1 - rho_ * rho_);

        final double l_0 = leverageFct_.localVol(t0, x0.get(0), true);
        final double v_0 = 0.5 * (x0.get(1) + retVal.get(1)) * l_0 * l_0;

        retVal.set(0, x0.get(0) * Math.exp(mu * dt - 0.5 * v_0 * dt
                + rho_ / mixedSigma_ * l_0 * (retVal.get(1) - kappa_ * theta_ * dt
                + 0.5 * (x0.get(1) + retVal.get(1)) * kappa_ * dt - x0.get(1))
                + rho1 * Math.sqrt(v_0 * dt) * dw.get(0)));
        return retVal;
    }

    public double v0() {
        return v0_;
    }

    public double rho() {
        return rho_;
    }

    public double kappa() {
        return kappa_;
    }

    public double theta() {
        return theta_;
    }

    public double sigma() {
        return sigma_;
    }

    public double mixingFactor() {
        return mixingFactor_;
    }

    public LocalVolTermStructure leverageFct() {
        return leverageFct_;
    }

    public final Handle<Quote> s0() {
        return hestonProcess_.s0();
    }

    public final Handle<YieldTermStructure> dividendYield() {
        return hestonProcess_.dividendYield();
    }

    public final Handle<YieldTermStructure> riskFreeRate() {
        return hestonProcess_.riskFreeRate();
    }

    @Override
    public double time(final Date d) {
        return hestonProcess_.time(d);
    }

    private void setParameters() {
        v0_ = hestonProcess_.v0();
        kappa_ = hestonProcess_.kappa();
        theta_ = hestonProcess_.theta();
        sigma_ = hestonProcess_.sigma();
        rho_ = hestonProcess_.rho();
        mixedSigma_ = mixingFactor_ * sigma_;
    }
}
