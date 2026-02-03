package jquant.processes;

import jquant.StochasticProcess;
import jquant.math.Array;
import jquant.math.Matrix;
import jquant.models.shortrate.onefactormodels.HullWhite;
import jquant.time.Date;
import jquant.time.Frequency;

import static jquant.Compounding.Continuous;
import static jquant.math.CommonUtil.QL_FAIL;
import static jquant.math.CommonUtil.QL_REQUIRE;
import static jquant.math.MathUtils.QL_EPSILON;
import static jquant.processes.HybridHestonHullWhiteProcess.Discretization.BSMHullWhite;
import static jquant.processes.HybridHestonHullWhiteProcess.Discretization.Euler;

//! Hybrid Heston Hull-White stochastic process
/*! This class implements a three factor Heston Hull-White model

    \bug This class was not tested enough to guarantee
         its functionality... work in progress

    \ingroup processes
*/
public abstract class HybridHestonHullWhiteProcess extends StochasticProcess {
    public enum Discretization {Euler, BSMHullWhite}

    protected HestonProcess hestonProcess_;
    protected HullWhiteForwardProcess hullWhiteProcess_;
    //model is used to calculate P(t,T)
    protected HullWhite hullWhiteModel_;
    protected double corrEquityShortRate_;
    protected Discretization discretization_;
    protected double maxRho_;
    protected double T_;
    protected double endDiscount_;

    // discretization = BSMHullWhite
    public HybridHestonHullWhiteProcess(final HestonProcess hestonProcess,
                                        final HullWhiteForwardProcess hullWhiteProcess,
                                        double corrEquityShortRate,
                                        HybridHestonHullWhiteProcess.Discretization discretization) {
        hestonProcess_ = hestonProcess;
        hullWhiteProcess_ = hullWhiteProcess;
        hullWhiteModel_ = new HullWhite(hestonProcess.riskFreeRate(),
                hullWhiteProcess.a(),
                hullWhiteProcess.sigma());
        corrEquityShortRate_ = corrEquityShortRate;
        discretization_ = discretization;
        /* reserve for rounding errors */
        maxRho_ = Math.sqrt(1 - hestonProcess.rho() * hestonProcess.rho()) - Math.sqrt(QL_EPSILON);
        T_ = hullWhiteProcess.getForwardMeasureTime();
        endDiscount_ = hestonProcess.riskFreeRate().currentLink().discount(T_, false);
        QL_REQUIRE(corrEquityShortRate * corrEquityShortRate
                        + hestonProcess.rho() * hestonProcess.rho() <= 1.0,
                "correlation matrix is not positive definite");
        QL_REQUIRE(hullWhiteProcess.sigma() > 0.0,
                "positive vol of Hull White process is required");
    }

    @Override
    public int size() {
        return 3;
    }

    @Override
    public Array initialValues() {
        double[] aa = {hestonProcess_.s0().currentLink().value(),
                hestonProcess_.v0(),
                hullWhiteProcess_.x0()};
        return new Array(aa);
    }

    @Override
    public Array drift(double t, final Array x) {
        double[] x0 = {x.get(0), x.get(1)};
        Array y0 = hestonProcess_.drift(t, new Array(x0));
        double[] aa = {y0.get(0), y0.get(1), hullWhiteProcess_.drift(t, x.get(2))};
        return new Array(aa);
    }

    @Override
    public Matrix diffusion(double t, final Array x) {
        Matrix retVal = new Matrix(3, 3);

        Array xt = new Array(2);
        xt.set(0, x.get(0));
        xt.set(1, x.get(1));
        Matrix m = hestonProcess_.diffusion(t, xt);
        retVal.set(0, 0, m.get(0, 0));
        retVal.set(0, 1, 0.0);
        retVal.set(0, 2, 0.0);
        retVal.set(1, 0, m.get(1, 0));
        retVal.set(1, 1, m.get(1, 1));
        retVal.set(1, 2, 0.0);
        final double sigma = hullWhiteProcess_.sigma();
        retVal.set(2, 0, corrEquityShortRate_ * sigma);
        retVal.set(2, 1, -retVal.get(2, 0) * retVal.get(1, 0) / retVal.get(1, 1));
        retVal.set(2, 2, Math.sqrt(sigma * sigma - retVal.get(2, 1) * retVal.get(2, 1)
                - retVal.get(2, 0) * retVal.get(2, 0)));
        return retVal;
    }

    @Override
    public Array apply(final Array x0, final Array dx) {
        double[] xt = {x0.get(0), x0.get(1)};
        double[] dxt = {dx.get(0), dx.get(1)};
        Array yt = hestonProcess_.apply(new Array(xt), new Array(dxt));
        double[] aa = {yt.get(0), yt.get(1), hullWhiteProcess_.apply(x0.get(2), dx.get(2))};
        return new Array(aa);
    }

    @Override
    public Array evolve(double t0, final Array x0, double dt, final Array dw) {
        final double r = x0.get(2);
        final double a = hullWhiteProcess_.a();
        final double sigma = hullWhiteProcess_.sigma();
        final double rho = corrEquityShortRate_;
        final double xi = hestonProcess_.rho();
        final double eta = (x0.get(1) > 0.0) ? (Math.sqrt(x0.get(1))) : 0.0;
        final double s = t0;
        final double t = t0 + dt;
        final double T = T_;
        final double dy = hestonProcess_.dividendYield().currentLink().forwardRate(s, t, Continuous,
                Frequency.NO_FREQUENCY, false).rate();

        final double df = Math.log(hestonProcess_.riskFreeRate().getValue().discount(t, false)
                / hestonProcess_.riskFreeRate().currentLink().discount(s, false));

        final double eaT = Math.exp(-a * T);
        final double eat = Math.exp(-a * t);
        final double eas = Math.exp(-a * s);
        final double iat = 1.0 / eat;
        final double ias = 1.0 / eas;

        final double m1 = -(dy + 0.5 * eta * eta) * dt - df;

        final double m2 = -rho * sigma * eta / a * (dt - 1 / a * eaT * (iat - ias));

        final double m3 = (r - hullWhiteProcess_.alpha(s)) * hullWhiteProcess_.B(s, t);

        final double m4 = sigma * sigma / (2 * a * a)
                * (dt + 2 / a * (eat - eas) - 1 / (2 * a) * (eat * eat - eas * eas));

        final double m5 = -sigma * sigma / (a * a)
                * (dt - 1 / a * (1 - eat * ias) - 1 / (2 * a) * eaT * (iat - 2 * ias + eat * ias * ias));

        final double mu = m1 + m2 + m3 + m4 + m5;

        Array retVal = new Array(3);

        final double eta2 = hestonProcess_.sigma() * eta;
        final double nu = hestonProcess_.kappa() * (hestonProcess_.theta() - eta * eta);

        retVal.set(1, x0.get(1) + nu * dt + eta2 * Math.sqrt(dt)
                * (xi * dw.get(0) + Math.sqrt(1 - xi * xi) * dw.get(1)));

        if (discretization_ == BSMHullWhite) {
            final double v1 = eta * eta * dt
                    + sigma * sigma / (a * a) * (dt - 2 / a * (1 - eat * ias)
                    + 1 / (2 * a) * (1 - eat * eat * ias * ias))
                    + 2 * sigma * eta / a * rho * (dt - 1 / a * (1 - eat * ias));
            final double v2 = hullWhiteProcess_.variance(t0, r, dt);
            final double v12 = (1 - eat * ias) * (sigma * eta / a * rho + sigma * sigma / (a * a))
                    - sigma * sigma / (2 * a * a) * (1 - eat * eat * ias * ias);

            QL_REQUIRE(v1 > 0.0 && v2 > 0.0, "zero or negative variance given");

            // terminal rho must be between -maxRho and +maxRho
            final double rhoT
                    = Math.min(maxRho_, Math.max(-maxRho_, v12 / Math.sqrt(v1 * v2)));
            QL_REQUIRE(rhoT <= 1.0 && rhoT >= -1.0
                            && 1 - rhoT * rhoT / (1 - xi * xi) >= 0.0,
                    "invalid terminal correlation");

            final double dw_0 = dw.get(0);
            final double dw_2 = rhoT * dw.get(0) - rhoT * xi / Math.sqrt(1 - xi * xi) * dw.get(1)
                    + Math.sqrt(1 - rhoT * rhoT / (1 - xi * xi)) * dw.get(2);

            retVal.set(2, hullWhiteProcess_.evolve(t0, r, dt, dw_2));

            final double vol = Math.sqrt(v1) * dw_0;
            retVal.set(0, x0.get(0) * Math.exp(mu + vol));
        } else if (discretization_ == Euler) {
            final double dw_2 = rho * dw.get(0) - rho * xi / Math.sqrt(1 - xi * xi) * dw.get(1)
                    + Math.sqrt(1 - rho * rho / (1 - xi * xi)) * dw.get(2);

            retVal.set(2, hullWhiteProcess_.evolve(t0, r, dt, dw_2));

            final double vol = eta * Math.sqrt(dt) * dw.get(0);
            retVal.set(0, x0.get(0) * Math.exp(mu + vol));
        } else
            QL_FAIL("unknown discretization scheme");

        return retVal;
    }

    public double numeraire(double t, final Array x) {
        return hullWhiteModel_.discountBond(t, T_, x.get(2)) / endDiscount_;
    }

    public final HestonProcess hestonProcess() {
        return hestonProcess_;
    }

    public final HullWhiteForwardProcess hullWhiteProcess() {
        return hullWhiteProcess_;
    }

    public double eta() {
        return corrEquityShortRate_;
    }

    @Override
    public double time(final Date date) {
        return hestonProcess_.time(date);
    }

    public Discretization discretization() {
        return discretization_;
    }

    @Override
    public void update() {
        endDiscount_ = hestonProcess_.riskFreeRate().currentLink().discount(T_, false);
    }
}
