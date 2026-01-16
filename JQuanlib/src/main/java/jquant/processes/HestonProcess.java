package jquant.processes;

import jquant.Handle;
import jquant.Quote;
import jquant.StochasticProcess;
import jquant.math.Array;
import jquant.math.Matrix;
import jquant.math.distributions.CumulativeNormalDistribution;
import jquant.math.distributions.InverseNonCentralCumulativeChiSquareDistribution;
import jquant.math.integrals.SegmentIntegral;
import jquant.math.solvers1d.Brent;
import jquant.processes.impl.ManualNonCentralChiSquared;
import jquant.termstructures.YieldTermStructure;
import jquant.time.Date;
import jquant.time.Frequency;

import static jquant.Compounding.Continuous;
import static jquant.math.CommonUtil.QL_FAIL;
import static jquant.math.CommonUtil.QL_REQUIRE;
import static jquant.math.MathUtils.M_PI;
import static jquant.math.MathUtils.QL_EPSILON;
import static jquant.processes.HestonProcess.Discretization.*;
import static jquant.processes.impl.ProcessUtil.*;

//! Square-root stochastic-volatility Heston process
/*! This class describes the square root stochastic volatility
    process governed by
    \f[
    \begin{array}{rcl}
    dS(t, S)  &=& \mu S dt + \sqrt{v} S dW_1 \\
    dv(t, S)  &=& \kappa (\theta - v) dt + \sigma \sqrt{v} dW_2 \\
    dW_1 dW_2 &=& \rho dt
    \end{array}
    \f]

    \ingroup processes
*/
public abstract class HestonProcess extends StochasticProcess {
    public enum Discretization {
        PartialTruncation,
        FullTruncation,
        Reflection,
        NonCentralChiSquareVariance,
        QuadraticExponential,
        QuadraticExponentialMartingale,
        BroadieKayaExactSchemeLobatto,
        BroadieKayaExactSchemeLaguerre,
        BroadieKayaExactSchemeTrapezoidal
    }

    private Handle<YieldTermStructure> riskFreeRate_, dividendYield_;
    private Handle<Quote> s0_;
    private double v0_, kappa_, theta_, sigma_, rho_;
    private Discretization discretization_;

    public HestonProcess(Handle<YieldTermStructure> riskFreeRate,
                         Handle<YieldTermStructure> dividendYield,
                         Handle<Quote> s0,
                         double v0,
                         double kappa,
                         double theta,
                         double sigma,
                         double rho,
                         Discretization d) {
        super(new EulerDiscretization());
        riskFreeRate_ = riskFreeRate;
        dividendYield_ = dividendYield;
        s0_ = s0;
        v0_ = v0;
        kappa_ = kappa;
        theta_ = theta;
        sigma_ = sigma;
        rho_ = rho;
        discretization_ = d;
        registerWith(riskFreeRate_.currentLink());
        registerWith(dividendYield_.currentLink());
        registerWith(s0_.currentLink());
    }

    public int size() {
        return 2;
    }

    public int factors() {
        return (discretization_ == BroadieKayaExactSchemeLobatto
                || discretization_ == BroadieKayaExactSchemeTrapezoidal
                || discretization_ == BroadieKayaExactSchemeLaguerre) ? 3 : 2;
    }

    public Array initialValues() {
        Array res = new Array(2);
        res.set(0, s0_.getValue().value());
        res.set(1, v0_);
        return res;
    }

    public Array drift(double t, final Array x) {
        final double vol = (x.get(1) > 0.0) ? Math.sqrt(x.get(1))
                : (discretization_ == Reflection) ? (-Math.sqrt(-x.get(1)))
                : 0.0;
        Array res = new Array(2);
        res.set(0, riskFreeRate_.getValue().forwardRate(t, t, Continuous, Frequency.ANNUAL, false).rate()
                - dividendYield_.getValue().forwardRate(t, t, Continuous, Frequency.ANNUAL, false).rate()
                - 0.5 * vol * vol);
        res.set(1, kappa_ * (theta_ - ((discretization_ == PartialTruncation) ? x.get(1) : vol * vol)));
        return res;
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
        final double vol = (x.get(1) > 0.0) ? Math.sqrt(x.get(1))
                : (discretization_ == Reflection) ? (-Math.sqrt(-x.get(1)))
                : 1e-8; // set vol to (almost) zero but still
        // expose some correlation information
        final double sigma2 = sigma_ * vol;
        final double sqrhov = Math.sqrt(1.0 - rho_ * rho_);
        tmp.set(0, 0, vol);
        tmp.set(0, 1, 0);
        tmp.set(1, 0, rho_ * sigma2);
        tmp.set(1, 1, sqrhov * sigma2);
        return tmp;
    }

    public Array apply(final Array x0, final Array dx) {
        Array res = new Array(2);
        res.set(0, x0.get(0) * Math.exp(dx.get(0)));
        res.set(1, x0.get(1) + dx.get(1));
        return res;
    }

    public Array evolve(double t0, final Array x0, double dt, final Array dw) {
        Array retVal = new Array(2);
        double vol, vol2, mu, nu, dy;
        final double sdt = Math.sqrt(dt);
        final double sqrhov = Math.sqrt(1.0 - rho_ * rho_);
        switch (discretization_) {
            // For the definition of PartialTruncation, FullTruncation
            // and Reflection  see Lord, R., R. Koekkoek and D. van Dijk (2006),
            // "A Comparison of biased simulation schemes for
            //  stochastic volatility models",
            // Working Paper, Tinbergen Institute
            case PartialTruncation:
                vol = (x0.get(1) > 0.0) ? Math.sqrt(x0.get(1)) : (0.0);
                vol2 = sigma_ * vol;
                mu = riskFreeRate_.getValue().forwardRate(t0, t0 + dt, Continuous, Frequency.ANNUAL, false).rate()
                        - dividendYield_.getValue().forwardRate(t0, t0 + dt, Continuous, Frequency.ANNUAL, false).rate()
                        - 0.5 * vol * vol;
                nu = kappa_ * (theta_ - x0.get(1));

                retVal.set(0, x0.get(0) * Math.exp(mu * dt + vol * dw.get(0) * sdt));
                retVal.set(1, x0.get(1) + nu * dt + vol2 * sdt * (rho_ * dw.get(0) + sqrhov * dw.get(1)));
                break;
            case FullTruncation:
                vol = (x0.get(1) > 0.0) ? Math.sqrt(x0.get(1)) : (0.0);
                vol2 = sigma_ * vol;
                mu = riskFreeRate_.getValue().forwardRate(t0, t0 + dt, Continuous, Frequency.ANNUAL, false).rate()
                        - dividendYield_.getValue().forwardRate(t0, t0 + dt, Continuous, Frequency.ANNUAL, false).rate()
                        - 0.5 * vol * vol;
                nu = kappa_ * (theta_ - vol * vol);

                retVal.set(0, x0.get(0) * Math.exp(mu * dt + vol * dw.get(0) * sdt));
                retVal.set(1, x0.get(1) + nu * dt + vol2 * sdt * (rho_ * dw.get(0) + sqrhov * dw.get(1)));
                break;
            case Reflection:
                vol = Math.sqrt(Math.abs(x0.get(1)));
                vol2 = sigma_ * vol;
                mu = riskFreeRate_.getValue().forwardRate(t0, t0 + dt, Continuous, Frequency.ANNUAL, false).rate()
                        - dividendYield_.getValue().forwardRate(t0, t0 + dt, Continuous, Frequency.ANNUAL, false).rate()
                        - 0.5 * vol * vol;
                nu = kappa_ * (theta_ - vol * vol);

                retVal.set(0, x0.get(0) * Math.exp(mu * dt + vol * dw.get(0) * sdt));
                retVal.set(1, vol * vol
                        + nu * dt + vol2 * sdt * (rho_ * dw.get(0) + sqrhov * dw.get(1)));
                break;
            case NonCentralChiSquareVariance:
                // use Alan Lewis trick to decorrelate the equity and the variance
                // process by using y(t)=x(t)-\frac{rho}{sigma}\nu(t)
                // and Ito's Lemma. Then use exact sampling for the variance
                // process. For further details please read the Wilmott thread
                // "QuantLib code is very high quality"
                vol = (x0.get(1) > 0.0) ? Math.sqrt(x0.get(1)) : (0.0);
                mu = riskFreeRate_.getValue().forwardRate(t0, t0 + dt, Continuous, Frequency.ANNUAL, false).rate()
                        - dividendYield_.getValue().forwardRate(t0, t0 + dt, Continuous, Frequency.ANNUAL, false).rate()
                        - 0.5 * vol * vol;

                retVal.set(1, varianceDistribution(x0.get(1), dw.get(1), dt));
                dy = (mu - rho_ / sigma_ * kappa_
                        * (theta_ - vol * vol)) * dt + vol * sqrhov * dw.get(0) * sdt;

                retVal.set(0, x0.get(0) * Math.exp(dy + rho_ / sigma_ * (retVal.get(1) - x0.get(1))));
                break;
            case QuadraticExponential:
            case QuadraticExponentialMartingale: {
                // for details of the quadratic exponential discretization scheme
                // see Leif Andersen,
                // Efficient Simulation of the Heston Stochastic Volatility Model
                final double ex = Math.exp(-kappa_ * dt);

                final double m = theta_ + (x0.get(1) - theta_) * ex;
                final double s2 = x0.get(1) * sigma_ * sigma_ * ex / kappa_ * (1 - ex)
                        + theta_ * sigma_ * sigma_ / (2 * kappa_) * (1 - ex) * (1 - ex);
                final double psi = s2 / (m * m);

                final double g1 = 0.5;
                final double g2 = 0.5;
                double k0 = -rho_ * kappa_ * theta_ * dt / sigma_;
                final double k1 = g1 * dt * (kappa_ * rho_ / sigma_ - 0.5) - rho_ / sigma_;
                final double k2 = g2 * dt * (kappa_ * rho_ / sigma_ - 0.5) + rho_ / sigma_;
                final double k3 = g1 * dt * (1 - rho_ * rho_);
                final double k4 = g2 * dt * (1 - rho_ * rho_);
                final double A = k2 + 0.5 * k4;

                if (psi < 1.5) {
                    final double b2 = 2 / psi - 1 + Math.sqrt(2 / psi * (2 / psi - 1));
                    final double b = Math.sqrt(b2);
                    final double a = m / (1 + b2);

                    if (discretization_ == QuadraticExponentialMartingale) {
                        // martingale correction
                        QL_REQUIRE(A < 1 / (2 * a), "illegal value");
                        k0 = -A * b2 * a / (1 - 2 * A * a) + 0.5 * Math.log(1 - 2 * A * a)
                                - (k1 + 0.5 * k3) * x0.get(1);
                    }
                    retVal.set(1, a * (b + dw.get(1)) * (b + dw.get(1)));
                } else {
                    final double p = (psi - 1) / (psi + 1);
                    final double beta = (1 - p) / m;

                    final double u = new CumulativeNormalDistribution().value(dw.get(1));

                    if (discretization_ == QuadraticExponentialMartingale) {
                        // martingale correction
                        QL_REQUIRE(A < beta, "illegal value");
                        k0 = -Math.log(p + beta * (1 - p) / (beta - A)) - (k1 + 0.5 * k3) * x0.get(1);
                    }
                    retVal.set(1, ((u <= p) ? (0.0) : Math.log((1 - p) / (1 - u)) / beta));
                }

                mu = riskFreeRate_.getValue().forwardRate(t0, t0 + dt, Continuous, Frequency.ANNUAL, false).rate()
                        - dividendYield_.getValue().forwardRate(t0, t0 + dt, Continuous, Frequency.ANNUAL, false).rate();

                retVal.set(0, x0.get(0) * Math.exp(mu * dt + k0 + k1 * x0.get(1) + k2 * retVal.get(1)
                        + Math.sqrt(k3 * x0.get(1) + k4 * retVal.get(1)) * dw.get(0)));
            }
            break;
            case BroadieKayaExactSchemeLobatto:
            case BroadieKayaExactSchemeLaguerre:
            case BroadieKayaExactSchemeTrapezoidal: {
                final double nu_0 = x0.get(1);
                final double nu_t = varianceDistribution(nu_0, dw.get(1), dt);

                final double x = Math.min(1.0 - QL_EPSILON,
                        Math.max(0.0, new CumulativeNormalDistribution().value(dw.get(2))));

                final double vds = new Brent().solve(xi -> cdf_nu_ds_minus_x( this,xi, nu_0, nu_t, dt, discretization_, x),
                1e-5, theta_ * dt, 0.1 * theta_ * dt);

                final double vdw
                        = (nu_t - nu_0 - kappa_ * theta_ * dt + kappa_ * vds) / sigma_;

                mu = (riskFreeRate_.getValue().forwardRate(t0, t0 + dt, Continuous, Frequency.ANNUAL, false).rate()
                        - dividendYield_.getValue().forwardRate(t0, t0 + dt, Continuous, Frequency.ANNUAL, false).rate()) * dt
                        - 0.5 * vds + rho_ * vdw;

                final double sig = Math.sqrt((1 - rho_ * rho_) * vds);
                final double s = x0.get(0) * Math.exp(mu + sig * dw.get(0));

                retVal.set(0, s);
                retVal.set(1, nu_t);
            }
            break;
            default:
                QL_FAIL("unknown discretization schema");
        }

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

    public final Handle<Quote> s0() {
        return s0_;
    }

    public final Handle<YieldTermStructure> dividendYield() {
        return dividendYield_;
    }

    public final Handle<YieldTermStructure> riskFreeRate() {
        return riskFreeRate_;
    }

    public double time(final Date d) {
        return riskFreeRate_.getValue().dayCounter().yearFraction(
                riskFreeRate_.getValue().referenceDate(), d, new Date(), new Date());
    }

    // probability densitiy function,
    // semi-analytical solution of the Fokker-Planck equation in x=ln(s)
    public double pdf(double x, double v, double t, double eps) {
         final double k = sigma_*sigma_*(1-Math.exp(-kappa_*t))/(4*kappa_);
         final double a = Math.log(  dividendYield_.getValue().discount(t, false)
                / riskFreeRate_.getValue().discount(t, false))
                + rho_/sigma_*(v - v0_ - kappa_*theta_*t);

         final double x0 = Math.log(s0().getValue().value());
        double upper = Math.max(0.1, -(x-x0-a)/(0.5-rho_*kappa_/sigma_)), f=0, df=1;

        while (df > 0.0 || f > 0.1*eps) {
             final double f1 = x-x0-a+upper*(0.5-rho_*kappa_/sigma_);
             final double f2 = -0.5*f1*f1/(upper*(1-rho_*rho_));

            df = 1/Math.sqrt(2*M_PI*(1-rho_*rho_))
                    * ( -0.5/(upper*Math.sqrt(upper))*Math.exp(f2)
                    + 1/Math.sqrt(upper)*Math.exp(f2)*(-0.5/(1-rho_*rho_))
                    *(-1/(upper*upper)*f1*f1
                    + 2/upper*f1*(0.5-rho_*kappa_/sigma_)));

            f = Math.exp(f2)/ Math.sqrt(2*M_PI*(1-rho_*rho_)*upper);
            upper*=1.5;
        }

        upper = 2.0*cornishFisherEps(this, v0_, v, t,1e-3);


        return new SegmentIntegral(100).value(xi -> int_ph(this, a, x, xi, v0_, v, t), QL_EPSILON, upper)
               * ManualNonCentralChiSquared.calculate(theta_, kappa_, sigma_, t, v0_, v);
//        boost::math::pdf(
//                boost::math::non_central_chi_squared_distribution<Real>(
//                4*theta_*kappa_/(sigma_*sigma_),
//                4*kappa_*std::exp(-kappa_*t)
//                /((sigma_*sigma_)*(1-std::exp(-kappa_*t)))*v0_),
//        v/k) / k;
    }

    private double varianceDistribution(double v, double dw, double dt) {
        final double df = 4 * theta_ * kappa_ / (sigma_ * sigma_);
        final double ncp = 4 * kappa_ * Math.exp(-kappa_ * dt)
                / (sigma_ * sigma_ * (1 - Math.exp(-kappa_ * dt))) * v;

        final double p = Math.min(1.0 - QL_EPSILON,
                Math.max(0.0, new CumulativeNormalDistribution().value(dw)));

        return sigma_ * sigma_ * (1 - Math.exp(-kappa_ * dt)) / (4 * kappa_)
                * new InverseNonCentralCumulativeChiSquareDistribution(df, ncp, 100, 1e-8).value(p);
    }
}
