package jquant.models.shortrate.onefactormodels;

import jquant.Option;
import jquant.math.optimization.NoConstraint;
import jquant.math.optimization.PositiveConstraint;
import jquant.models.ConstantParameter;
import jquant.models.Parameter;
import jquant.models.shortrate.OneFactorAffineModel;
import jquant.models.shortrate.OneFactorModel;
import jquant.processes.OrnsteinUhlenbeckProcess;

import static jquant.math.MathUtils.QL_EPSILON;
import static jquant.pricingengines.BlackFormula.blackFormula;

//! %Vasicek model class
/*! This class implements the Vasicek model defined by
    \f[
        dr_t = a(b - r_t)dt + \sigma dW_t ,
    \f]
    where \f$ a \f$, \f$ b \f$ and \f$ \sigma \f$ are constants;
    a risk premium \f$ \lambda \f$ can also be specified.

    \ingroup shortrate
*/
public abstract class Vasicek extends OneFactorAffineModel {
    //! Short-rate dynamics in the %Vasicek model
    /*! The short-rate follows an Ornstein-Uhlenbeck process with mean
        \f$ b \f$.
    */
    private static class Dynamics extends OneFactorModel.ShortRateDynamics {
        private double b_;

        public Dynamics(double a, double b, double sigma, double r0) {
            super(new OrnsteinUhlenbeckProcess(a, sigma, r0, -b));
            b_ = b;
        }

        @Override
        public double variable(double t, double r) {
            return r - b_;
        }

        @Override
        public double shortRate(double t, double x) {
            return x + b_;
        }
    }

    protected double r0_;
    protected Parameter a_;
    protected Parameter b_;
    protected Parameter sigma_;
    protected Parameter lambda_;

    /**
     * 构造函数
     *
     * @param r0     0.05
     * @param a      0.1
     * @param b      0.05
     * @param sigma  0.01
     * @param lambda 0.0
     */
    public Vasicek(double r0, double a, double b, double sigma, double lambda) {
        super(4);
        r0_ = r0;
        a_ = arguments_.get(0);
        b_ = arguments_.get(1);
        sigma_ = arguments_.get(2);
        lambda_ = arguments_.get(3);
        a_ = new ConstantParameter(a, new PositiveConstraint());
        b_ = new ConstantParameter(b, new NoConstraint());
        sigma_ = new ConstantParameter(sigma, new PositiveConstraint());
        lambda_ = new ConstantParameter(lambda, new NoConstraint());
    }

    public double discountBondOption(Option.Type type, double strike, double maturity, double bondMaturity) {
        double v;
        double _a = a();
        if (Math.abs(maturity) < QL_EPSILON) {
            v = 0.0;
        } else if (_a < Math.sqrt(QL_EPSILON)) {
            v = sigma()*B(maturity, bondMaturity)* Math.sqrt(maturity);
        } else {
            v = sigma()*B(maturity, bondMaturity)*
                    Math.sqrt(0.5*(1.0 - Math.exp(-2.0*_a*maturity))/_a);
        }
        double f = discountBond(0.0, bondMaturity, r0_);
        double k = discountBond(0.0, maturity, r0_)*strike;

        return blackFormula(type, k, f, v, 1.0, 0);
    }

    public OneFactorModel.ShortRateDynamics dynamics() {
        return new Dynamics(a(), b(), sigma(), r0_);
    }

    public double a() {
        return a_.value(0.0);
    }

    public double b() {
        return b_.value(0.0);
    }

    public double lambda() {
        return lambda_.value(0.0);
    }

    public double sigma() {
        return sigma_.value(0.0);
    }

    public double r0() {
        return r0_;
    }

    @Override
    protected double A(double t, double T) {
        double _a = a();
        if (_a < Math.sqrt(QL_EPSILON)) {
            return 0.0;
        } else {
            double sigma2 = sigma() * sigma();
            double bt = B(t, T);
            return Math.exp((b() + lambda() * sigma() / _a
                    - 0.5 * sigma2 / (_a * _a)) * (bt - (T - t))
                    - 0.25 * sigma2 * bt * bt / _a);
        }
    }

    @Override
    protected double B(double t, double T) {
        double _a = a();
        if (_a < Math.sqrt(QL_EPSILON))
            return (T - t);
        else
            return (1.0 - Math.exp(-_a * (T - t))) / _a;
    }
}
