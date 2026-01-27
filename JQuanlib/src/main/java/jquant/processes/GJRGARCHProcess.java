package jquant.processes;

import jquant.Handle;
import jquant.Quote;
import jquant.StochasticProcess;
import jquant.math.Array;
import jquant.math.Matrix;
import jquant.math.distributions.CumulativeNormalDistribution;
import jquant.termstructures.YieldTermStructure;
import jquant.time.Date;
import jquant.time.Frequency;

import static jquant.Compounding.Continuous;
import static jquant.math.CommonUtil.QL_FAIL;
import static jquant.math.MathUtils.M_PI;


//! Stochastic-volatility GJR-GARCH(1,1) process
// parameters supplied should be daily constants
// they are annualized by setting the parameter daysPerYear
/*! This class describes the stochastic volatility
    process governed by
    \f[
    \begin{array}{rcl}
    dS(t, S)  &=& \mu S dt + \sqrt{v} S dW_1 \\
    dv(t, S)  &=& (\omega + (\beta + \alpha * q_{2}
    + \gamma * q_{3} - 1) v) dt + (\alpha \sigma_{12}
    + \gamma \sigma_{13}) v dW_1
    + \sqrt{\alpha^{2} (\sigma^{2}_{2} - \sigma^{2}_{12})
    + \gamma^{2} (\sigma^{2}_{3} - \sigma^{2}_{13})
    + 2 \alpha \gamma (\sigma_{23} - \sigma_{12} \sigma_{13})} v dW_2 \ \
    N = normalCDF(\lambda) \\
    n &=& \exp{-\lambda^{2}/2} / \sqrt{2 \pi} \\
    q_{2} &=& 1 + \lambda^{2} \\
    q_{3} &=& \lambda n + N + \lambda^2 N \\
    \sigma^{2}_{2} = 2 + 4 \lambda^{4} \\
    \sigma^{2}_{3} = \lambda^{3} n + 5 \lambda n + 3N
    + \lambda^{4} N + 6 \lambda^{2} N -\\lambda^{2} n^{2} - N^{2}
    - \lambda^{4} N^{2} - 2 \lambda n N - 2 \lambda^{3} nN
    - 2 \lambda^{2} N^{2} \                 \
    \sigma_{12} = -2 \lambda \\
    \sigma_{13} = -2 n - 2 \lambda N \\
    \sigma_{23} = 2N + \sigma_{12} \sigma_{13} \\
    \end{array}
    \f]

    \ingroup processes
*/
public class GJRGARCHProcess extends StochasticProcess {
    public enum Discretization {
        PartialTruncation, FullTruncation,
        Reflection
    }

    private Handle<YieldTermStructure> riskFreeRate_;
    private Handle<YieldTermStructure> dividendYield_;
    private Handle<Quote> s0_;
    private double v0_;
    private double omega_;
    private double alpha_;
    private double beta_;
    private double gamma_;
    private double lambda_;
    private double daysPerYear_;
    private Discretization discretization_;

    //default daysPerYear = 252.0 / d = FullTruncation
    public GJRGARCHProcess(Handle<YieldTermStructure> riskFreeRate,
                           Handle<YieldTermStructure> dividendYield,
                           Handle<Quote> s0,
                           double v0,
                           double omega,
                           double alpha,
                           double beta,
                           double gamma,
                           double lambda,
                           double daysPerYear,
                           Discretization d) {
        super(new EulerDiscretization());
        riskFreeRate_ = riskFreeRate;
        dividendYield_ = dividendYield;
        s0_ = s0;
        v0_ = v0;
        omega_ = omega;
        alpha_ = alpha;
        beta_ = beta;
        gamma_ = gamma;
        lambda_ = lambda;
        daysPerYear_ = daysPerYear;
        discretization_ = d;
        registerWith(riskFreeRate_.currentLink());
        registerWith(dividendYield_.currentLink());
        registerWith(s0_.currentLink());
    }

    @Override
    public int size() {
        return 2;
    }

    @Override
    public Array initialValues() {
        double[] aa = {s0_.getValue().value(), daysPerYear_ * v0_};
        return new Array(aa);
    }

    @Override
    public Array drift(double t, final Array x) {
        final double N = new CumulativeNormalDistribution().value(lambda_);
        final double n = Math.exp(-lambda_ * lambda_ / 2.0) / Math.sqrt(2 * M_PI);
        final double q2 = 1.0 + lambda_ * lambda_;
        final double q3 = lambda_ * n + N + lambda_ * lambda_ * N;
        final double vol = (x.get(1) > 0.0) ? Math.sqrt(x.get(1))
                : (discretization_ == Discretization.Reflection) ? (-Math.sqrt(-x.get(1)))
                : 0.0;
        double[] aa = {
                riskFreeRate_.getValue().forwardRate(t, t, Continuous, Frequency.ANNUAL, false).rate()
                        - dividendYield_.getValue().forwardRate(t, t, Continuous, Frequency.ANNUAL, false).rate()
                        - 0.5 * vol * vol,
                daysPerYear_ * daysPerYear_ * omega_ + daysPerYear_ * (beta_
                        + alpha_ * q2 + gamma_ * q3 - 1.0) *
                        ((discretization_ == Discretization.PartialTruncation) ? x.get(1) : vol * vol)
        };
        return new Array(aa);
    }

    @Override
    public Matrix diffusion(double t, final Array x) {
        /* the correlation matrix is
           |  1   rho |
           | rho   1  |
           whose square root (which is used here) is
           |  1          0       |
           | rho   std::sqrt(1-rho^2) |
        */
        Matrix tmp = new Matrix(2, 2);
        final double N = new CumulativeNormalDistribution().value(lambda_);
        final double n = Math.exp(-lambda_ * lambda_ / 2.0) / Math.sqrt(2 * M_PI);
        final double sigma2 = 2.0 + 4.0 * lambda_ * lambda_;
        final double q3 = lambda_ * n + N + lambda_ * lambda_ * N;
        final double Eml_e4 = lambda_ * lambda_ * lambda_ * n + 5.0 * lambda_ * n
                + 3.0 * N + lambda_ * lambda_ * lambda_ * lambda_ * N
                + 6.0 * lambda_ * lambda_ * N;
        final double sigma3 = Eml_e4 - q3 * q3;
        final double sigma12 = -2.0 * lambda_;
        final double sigma13 = -2.0 * n - 2 * lambda_ * N;
        final double sigma23 = 2.0 * N + sigma12 * sigma13;
        final double vol = (x.get(1) > 0.0) ? Math.sqrt(x.get(1))
                : (discretization_ == Discretization.Reflection) ? (-Math.sqrt(-x.get(1)))
                : 1e-8; // set vol to (almost) zero but still
        // expose some correlation information
        final double rho1 = Math.sqrt(daysPerYear_) * (alpha_ * sigma12
                + gamma_ * sigma13) * vol * vol;
        final double rho2 = vol * vol * Math.sqrt(daysPerYear_)
                * Math.sqrt(alpha_ * alpha_ * (sigma2 - sigma12 * sigma12)
                + gamma_ * gamma_ * (sigma3 - sigma13 * sigma13)
                + 2.0 * alpha_ * gamma_ * (sigma23 - sigma12 * sigma13));

        // tmp[0][0], tmp[0][1] are the coefficients of dW_1 and dW_2
        // in asset return stochastic process
        tmp.set(0, 0, vol);
        tmp.set(0, 1, 0d);
        tmp.set(1, 0, rho1);
        tmp.set(1, 1, rho2);
        return tmp;
    }

    @Override
    public Array apply(final Array x0, final Array dx) {
        double[] aa = {x0.get(0) * Math.exp(dx.get(0)), x0.get(1) + dx.get(1)};
        return new Array(aa);
    }

    @Override
    public Array evolve(double t0, final Array x0, double dt, final Array dw) {
        Array retVal = new Array(2);
        double vol, mu, nu;

        final double sdt = Math.sqrt(dt);
        final double N = new CumulativeNormalDistribution().value(lambda_);
        final double n = Math.exp(-lambda_ * lambda_ / 2.0) / Math.sqrt(2 * M_PI);
        final double sigma2 = 2.0 + 4.0 * lambda_ * lambda_;
        final double q2 = 1.0 + lambda_ * lambda_;
        final double q3 = lambda_ * n + N + lambda_ * lambda_ * N;
        final double Eml_e4 = lambda_ * lambda_ * lambda_ * n + 5.0 * lambda_ * n
                + 3.0 * N + lambda_ * lambda_ * lambda_ * lambda_ * N
                + 6.0 * lambda_ * lambda_ * N;
        final double sigma3 = Eml_e4 - q3 * q3;
        final double sigma12 = -2.0 * lambda_;
        final double sigma13 = -2.0 * n - 2 * lambda_ * N;
        final double sigma23 = 2.0 * N + sigma12 * sigma13;
        final double rho1 = Math.sqrt(daysPerYear_) * (alpha_ * sigma12 + gamma_ * sigma13);
        final double rho2 = Math.sqrt(daysPerYear_)
                * Math.sqrt(alpha_ * alpha_ * (sigma2 - sigma12 * sigma12)
                + gamma_ * gamma_ * (sigma3 - sigma13 * sigma13)
                + 2.0 * alpha_ * gamma_ * (sigma23 - sigma12 * sigma13));

        switch (discretization_) {
            // For the definition of PartialTruncation, FullTruncation
            // and Reflection  see Lord, R., R. Koekkoek and D. van Dijk (2006),
            // "A Comparison of biased simulation schemes for
            //  stochastic volatility models",
            // Working Paper, Tinbergen Institute
            case PartialTruncation:
                vol = (x0.get(1) > 0.0) ? (Math.sqrt(x0.get(1))) : 0.0;
                mu = riskFreeRate_.getValue().forwardRate(t0, t0 + dt, Continuous, Frequency.ANNUAL, false).rate()
                        - dividendYield_.getValue().forwardRate(t0, t0 + dt, Continuous, Frequency.ANNUAL, false).rate()
                        - 0.5 * vol * vol;
                nu = daysPerYear_ * daysPerYear_ * omega_
                        + daysPerYear_ * (beta_ + alpha_ * q2 + gamma_ * q3 - 1.0) * x0.get(1);
                retVal.set(0, x0.get(0) * Math.exp(mu * dt + vol * dw.get(0) * sdt));
                retVal.set(1, x0.get(1) + nu * dt + sdt * vol * vol * (rho1 * dw.get(0) + rho2 * dw.get(1)));
                break;
            case FullTruncation:
                vol = (x0.get(1) > 0.0) ? (Math.sqrt(x0.get(1))) : 0.0;
                mu = riskFreeRate_.getValue().forwardRate(t0, t0 + dt, Continuous, Frequency.ANNUAL, false).rate()
                        - dividendYield_.getValue().forwardRate(t0, t0 + dt, Continuous, Frequency.ANNUAL, false).rate()
                        - 0.5 * vol * vol;
                nu = daysPerYear_ * daysPerYear_ * omega_
                        + daysPerYear_ * (beta_ + alpha_ * q2 + gamma_ * q3 - 1.0) * vol * vol;
                retVal.set(0, x0.get(0) * Math.exp(mu * dt + vol * dw.get(0) * sdt));
                retVal.set(1, x0.get(1) + nu * dt + sdt * vol * vol * (rho1 * dw.get(0) + rho2 * dw.get(1)));
                break;
            case Reflection:
                vol = Math.sqrt(Math.abs(x0.get(1)));
                mu = riskFreeRate_.getValue().forwardRate(t0, t0 + dt, Continuous, Frequency.ANNUAL, false).rate()
                        - dividendYield_.getValue().forwardRate(t0, t0 + dt, Continuous, Frequency.ANNUAL, false).rate()
                        - 0.5 * vol * vol;
                nu = daysPerYear_ * daysPerYear_ * omega_
                        + daysPerYear_ * (beta_ + alpha_ * q2 + gamma_ * q3 - 1.0) * vol * vol;

                retVal.set(0, x0.get(0) * Math.exp(mu * dt + vol * dw.get(0) * sdt));
                retVal.set(1, vol * vol + nu * dt + sdt * vol * vol * (rho1 * dw.get(0) + rho2 * dw.get(1)));
                break;
            default:
                QL_FAIL("unknown discretization schema");
        }

        return retVal;
    }

    public double v0() {
        return v0_;
    }

    public double lambda() {
        return lambda_;
    }

    public double omega() {
        return omega_;
    }

    public double alpha() {
        return alpha_;
    }

    public double beta() {
        return beta_;
    }

    public double gamma() {
        return gamma_;
    }

    public double daysPerYear() {
        return daysPerYear_;
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

    @Override
    public double time(final Date d) {
        return riskFreeRate_.getValue().dayCounter().yearFraction(riskFreeRate_.getValue().referenceDate(), d,
                new Date(), new Date());
    }
}
