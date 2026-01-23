package jquant.processes;

import jquant.Handle;
import jquant.Quote;
import jquant.math.Array;
import jquant.math.distributions.CumulativeNormalDistribution;
import jquant.math.distributions.InverseCumulativePoisson;
import jquant.termstructures.YieldTermStructure;

import static jquant.math.MathUtils.QL_EPSILON;

//! Square-root stochastic-volatility Bates process
/*! This class describes the square root stochastic volatility
    process incl jumps governed by
    \f[
    \begin{array}{rcl}
    dS(t, S)  &=& (r-d-\lambda m) S dt +\sqrt{v} S dW_1 + (e^J - 1) S dN \\
    dv(t, S)  &=& \kappa (\theta - v) dt + \sigma \sqrt{v} dW_2 \\
    dW_1 dW_2 &=& \rho dt \\
    \omega(J) &=& \frac{1}{\sqrt{2\pi \delta^2}}
                  \exp\left[-\frac{(J-\nu)^2}{2\delta^2}\right]
    \end{array}
    \f]

    \ingroup processes
*/
public class BatesProcess extends HestonProcess {
    private double lambda_, delta_, nu_, m_;
    private CumulativeNormalDistribution cumNormalDist_;

    public BatesProcess(final Handle<YieldTermStructure> riskFreeRate,
                        final Handle<YieldTermStructure> dividendYield,
                        final Handle<Quote> s0,
                        double v0, double kappa,
                        double theta, double sigma, double rho,
                        double lambda, double nu, double delta,
                        HestonProcess.Discretization d) {
        super(riskFreeRate, dividendYield, s0, v0, kappa, theta, sigma, rho, d);
        lambda_ = lambda;
        delta_ = delta;
        nu_ = nu;
        m_ = Math.exp(nu + 0.5 * delta * delta) - 1;
        cumNormalDist_ = new CumulativeNormalDistribution();
    }

    @Override
    public Array drift(double t, final Array x) {
        Array retVal = super.drift(t, x);
        retVal.subtractEq(0, lambda_ * m_);
        return retVal;
    }

    @Override
    public Array evolve(double t0, final Array x0,
                        double dt, final Array dw) {

        final int hestonFactors = super.factors();

        double p = cumNormalDist_.value(dw.get(hestonFactors));
        if (p < 0.0)
            p = 0.0;
        else if (p >= 1.0)
            p = 1.0 - QL_EPSILON;

        final double n = new InverseCumulativePoisson(lambda_ * dt).value(p);
        Array retVal = super.evolve(t0, x0, dt, dw);
        retVal.multiplyEq(0, Math.exp(-lambda_ * m_ * dt + nu_ * n + delta_ * Math.sqrt(n) * dw.get(hestonFactors + 1)));
        return retVal;
    }

    @Override
    public int factors() {
        return super.factors() + 2;
    }

    public double lambda() {
        return lambda_;
    }

    public double nu() {
        return nu_;
    }

    public double delta() {
        return delta_;
    }
}
