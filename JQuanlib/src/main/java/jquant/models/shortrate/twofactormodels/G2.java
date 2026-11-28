package jquant.models.shortrate.twofactormodels;

import jquant.Compounding;
import jquant.Handle;
import jquant.Option;
import jquant.math.Array;
import jquant.math.optimization.BoundaryConstraint;
import jquant.math.optimization.PositiveConstraint;
import jquant.models.AffineModel;
import jquant.models.ConstantParameter;
import jquant.models.Parameter;
import jquant.models.TermStructureConsistentModel;
import jquant.models.TermStructureFittingParameter;
import jquant.models.shortrate.TwoFactorModel;
import jquant.patterns.Observable;
import jquant.patterns.ObservableSettings;
import jquant.patterns.Observer;
import jquant.processes.OrnsteinUhlenbeckProcess;
import jquant.termstructures.YieldTermStructure;
import jquant.time.Frequency;

import java.util.HashSet;
import java.util.Set;

import static jquant.math.CommonUtil.QL_REQUIRE;
import static jquant.math.MathUtils.QL_EPSILON;
import static jquant.pricingengines.BlackFormula.blackFormula;

/**
 * Two-additive-factor Gaussian model G2++.
 *
 * <p>The short rate is
 * {@code r_t = phi(t) + x_t + y_t}, where
 * {@code dx_t = -a x_t dt + sigma dW1_t} and
 * {@code dy_t = -b y_t dt + eta dW2_t}, with
 * {@code dW1_t dW2_t = rho dt}.
 *
 * <p>Ported from QuantLib
 * {@code ql/models/shortrate/twofactormodels/g2.{hpp,cpp}}.
 */
public class G2 extends TwoFactorModel
        implements AffineModel, TermStructureConsistentModel {

    /** Short-rate dynamics in the G2 model. */
    private static class Dynamics extends TwoFactorModel.ShortRateDynamics {
        private final Parameter fitting_;

        public Dynamics(Parameter fitting, double a, double sigma,
                        double b, double eta, double rho) {
            super(new OrnsteinUhlenbeckProcess(a, sigma, 0.0, 0.0),
                    new OrnsteinUhlenbeckProcess(b, eta, 0.0, 0.0),
                    rho);
            fitting_ = fitting;
        }

        @Override
        public double shortRate(double t, double x, double y) {
            return fitting_.value(t) + x + y;
        }
    }

    /**
     * Analytical term-structure fitting parameter {@code phi(t)}.
     */
    private static class FittingParameter extends TermStructureFittingParameter {
        private static class Impl extends Parameter.Impl {
            private final Handle<YieldTermStructure> termStructure_;
            private final double a_, sigma_, b_, eta_, rho_;

            public Impl(Handle<YieldTermStructure> termStructure,
                        double a, double sigma,
                        double b, double eta, double rho) {
                super();
                termStructure_ = termStructure;
                a_ = a;
                sigma_ = sigma;
                b_ = b;
                eta_ = eta;
                rho_ = rho;
            }

            @Override
            public double value(final Array params, double t) {
                final double forward = termStructure_.getValue()
                        .forwardRate(t, t, Compounding.Continuous,
                                Frequency.NO_FREQUENCY, false).rate();
                final double temp1 = sigma_ * (1.0 - Math.exp(-a_ * t)) / a_;
                final double temp2 = eta_ * (1.0 - Math.exp(-b_ * t)) / b_;
                return 0.5 * temp1 * temp1 + 0.5 * temp2 * temp2
                        + rho_ * temp1 * temp2 + forward;
            }
        }

        public FittingParameter(Handle<YieldTermStructure> termStructure,
                                double a, double sigma,
                                double b, double eta, double rho) {
            super(new Impl(termStructure, a, sigma, b, eta, rho));
        }
    }

    private Parameter a_;
    private Parameter sigma_;
    private Parameter b_;
    private Parameter eta_;
    private Parameter rho_;

    private Parameter phi_;

    private final Handle<YieldTermStructure> termStructure_;
    private final Set<Observable> registeredObservables = new HashSet<>();
    private final Set<Observer> observers = new HashSet<>();

    public G2(final Handle<YieldTermStructure> termStructure) {
        this(termStructure, 0.1, 0.01, 0.1, 0.01, -0.75);
    }

    public G2(final Handle<YieldTermStructure> termStructure,
              double a, double sigma, double b, double eta, double rho) {
        super(5);
        a_ = arguments_.get(0);
        sigma_ = arguments_.get(1);
        b_ = arguments_.get(2);
        eta_ = arguments_.get(3);
        rho_ = arguments_.get(4);

        a_ = new ConstantParameter(a, new PositiveConstraint());
        sigma_ = new ConstantParameter(sigma, new PositiveConstraint());
        b_ = new ConstantParameter(b, new PositiveConstraint());
        eta_ = new ConstantParameter(eta, new PositiveConstraint());
        rho_ = new ConstantParameter(rho, new BoundaryConstraint(-1.0, 1.0));

        termStructure_ = termStructure;
        generateArguments();
        registerWith(termStructure_.currentLink());
    }

    @Override
    public TwoFactorModel.ShortRateDynamics dynamics() {
        return new Dynamics(phi_, a(), sigma(), b(), eta(), rho());
    }

    @Override
    public double discountBond(double now, double maturity, Array factors) {
        QL_REQUIRE(factors.size() > 1,
                "g2 model needs two factors to compute discount bond");
        return discountBond(now, maturity, factors.get(0), factors.get(1));
    }

    public double discountBond(double t, double T, double x, double y) {
        return A(t, T) * Math.exp(-B(a(), (T - t)) * x - B(b(), (T - t)) * y);
    }

    @Override
    public double discountBondOption(Option.Type type, double strike,
                                     double maturity, double bondMaturity) {
        final double v = sigmaP(maturity, bondMaturity);
        final double f = termStructure_.currentLink().discount(bondMaturity, false);
        final double k = termStructure_.currentLink().discount(maturity, false) * strike;
        return blackFormula(type, k, f, v, 1.0, 0.0);
    }

    @Override
    public double discount(double t) {
        return termStructure_.currentLink().discount(t, false);
    }

    public double a() {
        return a_.value(0.0);
    }

    public double sigma() {
        return sigma_.value(0.0);
    }

    public double b() {
        return b_.value(0.0);
    }

    public double eta() {
        return eta_.value(0.0);
    }

    public double rho() {
        return rho_.value(0.0);
    }

    @Override
    protected void generateArguments() {
        phi_ = new FittingParameter(termStructure(), a(), sigma(), b(), eta(), rho());
    }

    protected double A(double t, double T) {
        final double discountT = termStructure_.currentLink().discount(T, false);
        final double discountt = termStructure_.currentLink().discount(t, false);
        return discountT / discountt
                * Math.exp(0.5 * (V(T - t) - V(T) + V(t)));
    }

    protected double B(double x, double t) {
        return -Math.expm1(-x * t) / x;
    }

    private double sigmaP(double t, double s) {
        final double temp = 1.0 - Math.exp(-(a() + b()) * t);
        final double temp1 = 1.0 - Math.exp(-a() * (s - t));
        final double temp2 = 1.0 - Math.exp(-b() * (s - t));
        final double a3 = a() * a() * a();
        final double b3 = b() * b() * b();
        final double sigma2 = sigma() * sigma();
        final double eta2 = eta() * eta();
        final double value =
                0.5 * sigma2 * temp1 * temp1 * (1.0 - Math.exp(-2.0 * a() * t)) / a3
                        + 0.5 * eta2 * temp2 * temp2 * (1.0 - Math.exp(-2.0 * b() * t)) / b3
                        + 2.0 * rho() * sigma() * eta() / (a() * b() * (a() + b()))
                        * temp1 * temp2 * temp;
        return Math.sqrt(value);
    }

    private double V(double t) {
        final double u = a() * t;
        final double v = b() * t;
        final double valuex = sigma() * sigma() * varianceKernel(u, u);
        final double valuey = eta() * eta() * varianceKernel(v, v);
        final double value = 2.0 * rho() * sigma() * eta() * varianceKernel(u, v);
        return t * t * t * (valuex + valuey + value);
    }

    // (1 - exp(-x))/x
    private static double expDecayRatio(double x) {
        return -Math.expm1(-x) / x;
    }

    // The integral of s^2 * f(u*s) * f(v*s) between 0 and 1, where f is
    // expDecayRatio.  Its closed form subtracts O(1) terms to get a result
    // that is O(u*v), so it loses all precision when u and v are small and
    // is only used when that is not the case.  G2::V combines this function
    // at (a*t,a*t), (b*t,b*t) and (a*t,b*t).
    private static double varianceKernel(double u, double v) {
        if (u < v) {
            final double temp = u;
            u = v;
            v = temp;
        }

        if (u + v <= 2.0) {
            // series sum_(k>=2) (-1)^k*P_k/(k+1)!, where
            // P_k = ((u+v)^k - u^k - v^k)/(u*v),
            // so that P_2 = 2 and P_(k+1) = (u+v)*P_k + u^(k-1) + v^(k-1)
            final double s = u + v;
            double P = 2.0, uPower = u, vPower = v, factorial = 6.0;
            double sign = 1.0, sum = 0.0;
            for (int k = 2; k < 60; ++k) {
                final double term = sign * P / factorial;
                sum += term;
                if (Math.abs(term) < 0.01 * QL_EPSILON * Math.abs(sum))
                    break;
                P = s * P + uPower + vPower;
                uPower *= u;
                vPower *= v;
                factorial *= k + 2;
                sign = -sign;
            }
            return sum;
        }

        if (v >= 0.5) {
            return (1.0 - expDecayRatio(u) - expDecayRatio(v)
                    + expDecayRatio(u + v)) / (u * v);
        }

        // v is small and u is not.  Write 1 - f(v) as v*omega(v), where
        // omega(v) = (v + expm1(-v))/v^2 = 1/2 - v/6 + v^2/24 - ..., and
        // f(u) - f(u+v) = (u*exp(-u)*expm1(-v) - v*expm1(-u))/(u*(u+v))
        double omega = 0.0, term = 0.5;
        for (int k = 2; k < 40; ++k) {
            omega += term;
            term *= -v / (k + 1);
            if (Math.abs(term) < 0.01 * QL_EPSILON * Math.abs(omega))
                break;
        }
        final double difference =
                (u * Math.exp(-u) * Math.expm1(-v) - v * Math.expm1(-u))
                        / (u * (u + v));
        return (v * omega - difference) / (u * v);
    }

    @Override
    public Handle<YieldTermStructure> termStructure() {
        return termStructure_;
    }

    @Override
    public void registerObserver(Observer o) {
        observers.add(o);
    }

    @Override
    public void unregisterObserver(Observer o) {
        observers.remove(o);
    }

    @Override
    public void notifyObservers() {
        if (!ObservableSettings.getInstance().isUpdatesEnabled()) {
            ObservableSettings.getInstance().registerDeferred(observers);
        } else {
            for (Observer observer : observers) {
                try {
                    observer.update();
                } catch (Exception e) {
                    System.err.println("Error notifying observer: " + e.getMessage());
                }
            }
        }
    }

    @Override
    public void registerWith(Observable observable) {
        if (observable != null) {
            observable.registerObserver(this);
            registeredObservables.add(observable);
        }
    }

    @Override
    public void unregisterWith(Observable o) {
        if (o != null) {
            o.unregisterObserver(this);
            registeredObservables.remove(o);
        }
    }

    @Override
    public void unregisterWithAll() {
        for (Observable o : registeredObservables) {
            o.unregisterObserver(this);
        }
        registeredObservables.clear();
    }
}
