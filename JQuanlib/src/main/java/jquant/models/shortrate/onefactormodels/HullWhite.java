package jquant.models.shortrate.onefactormodels;

import jquant.*;
import jquant.math.Array;
import jquant.math.CommonUtil;
import jquant.math.Function;
import jquant.methods.lattices.TrinomialTree;
import jquant.models.NullParameter;
import jquant.models.Parameter;
import jquant.models.TermStructureConsistentModel;
import jquant.models.TermStructureFittingParameter;
import jquant.models.shortrate.OneFactorModel;
import jquant.patterns.Observable;
import jquant.patterns.ObservableSettings;
import jquant.patterns.Observer;
import jquant.processes.OrnsteinUhlenbeckProcess;
import jquant.termstructures.YieldTermStructure;
import jquant.time.Frequency;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static jquant.Compounding.Continuous;
import static jquant.math.CommonUtil.QL_REQUIRE;
import static jquant.math.MathUtils.QL_EPSILON;
import static jquant.pricingengines.BlackFormula.blackFormula;

//! Single-factor Hull-White (extended %Vasicek) model class.
/*! This class implements the standard single-factor Hull-White model
    defined by
    \f[
        dr_t = (\theta(t) - \alpha r_t)dt + \sigma dW_t
    \f]
    where \f$ \alpha \f$ and \f$ \sigma \f$ are constants.

    \test calibration results are tested against cached values

    \bug When the term structure is relinked, the r0 parameter of
         the underlying Vasicek model is not updated.

    \ingroup shortrate
*/
public class HullWhite extends Vasicek implements TermStructureConsistentModel {
    //! Short-rate dynamics in the Hull-White model
    /*! The short-rate is here
        \f[
            r_t = \varphi(t) + x_t
        \f]
        where \f$ \varphi(t) \f$ is the deterministic time-dependent
        parameter used for term-structure fitting and \f$ x_t \f$ is the
        state variable following an Ornstein-Uhlenbeck process.
    */
    private static class Dynamics extends OneFactorModel.ShortRateDynamics {
        private Parameter fitting_;

        public Dynamics(Parameter fitting, double a, double sigma) {
            super(new OrnsteinUhlenbeckProcess(a, sigma, 0d, 0d));
            fitting_ = fitting;
        }

        public double variable(double t, double r) {
            return r - fitting_.value(t);
        }

        public double shortRate(double t, double x) {
            return x + fitting_.value(t);
        }
    }

    //! Analytical term-structure fitting parameter \f$ \varphi(t) \f$.
    /*! \f$ \varphi(t) \f$ is analytically defined by
        \f[
            \varphi(t) = f(t) + \frac{1}{2}[\frac{\sigma(1-e^{-at})}{a}]^2,
        \f]
        where \f$ f(t) \f$ is the instantaneous forward rate at \f$ t \f$.
    */
    private static class FittingParameter extends TermStructureFittingParameter {
        private static class Impl extends Parameter.Impl {
            private Handle<YieldTermStructure> termStructure_;
            private double a_, sigma_;

            public Impl(Handle<YieldTermStructure> termStructure, double a, double sigma) {
                super();
                termStructure_ = termStructure;
                a_ = a;
                sigma_ = sigma;
            }

            @Override
            public double value(final Array a, double t) {
                double forwardRate =
                        termStructure_.getValue().forwardRate(t, t, Continuous, Frequency.NO_FREQUENCY, false).rate();
                double temp = a_ < Math.sqrt(QL_EPSILON) ?
                        (sigma_ * t) :
                        (sigma_ * (1.0 - Math.exp(-a_ * t)) / a_);
                return (forwardRate + 0.5 * temp * temp);
            }
        }

        public FittingParameter(final Handle<YieldTermStructure> termStructure, double a, double sigma) {
            super(new Impl(termStructure, a, sigma));
        }
    }

    private Parameter phi_;
    private Handle<YieldTermStructure> termStructure_;
    private final Set<Observable> registeredObservables = new HashSet<>();
    private final Set<Observer> observers = new HashSet<>();

    // a = 0.1, sigma = 0.01
    public HullWhite(final Handle<YieldTermStructure> termStructure, double a, double sigma) {
        super(termStructure.currentLink().forwardRate(0.0, 0.0, Continuous, Frequency.NO_FREQUENCY, false).rate(),
                a, 0d, sigma, 0d);
        termStructure_ = termStructure;
        b_ = new NullParameter();
        lambda_ = new NullParameter();
        generateArguments();
        registerWith(termStructure.currentLink());
    }

    @Override
    public Lattice tree(final TimeGrid grid) {
        TermStructureFittingParameter phi = new TermStructureFittingParameter(termStructure());
        ShortRateDynamics numericDynamics = new Dynamics(phi, a(), sigma());
        TrinomialTree trinomial = new TrinomialTree(numericDynamics.process(), grid, false);
        ShortRateTree numericTree = new ShortRateTree(trinomial, numericDynamics, grid);

        //typedef TermStructureFittingParameter::NumericalImpl NumericalImpl;
        TermStructureFittingParameter.NumericalImpl impl = (TermStructureFittingParameter.NumericalImpl) phi.implementation();
        impl.reset();
        for (int i = 0; i < (grid.size() - 1); i++) {
            double discountBond = termStructure().getValue().discount(grid.get(i + 1), false);
            final Array statePrices = numericTree.statePrices(i);
            int size = numericTree.size(i);
            double dt = numericTree.timeGrid().dt(i);
            double dx = trinomial.dx(i);
            double x = trinomial.underlying(i, 0);
            double value = 0.0;
            for (int j = 0; j < size; j++) {
                value += statePrices.get(j) * Math.exp(-x * dt);
                x += dx;
            }
            value = Math.log(value / discountBond) / dt;
            impl.set(grid.get(i), value);
        }
        return numericTree;
    }

    @Override
    public ShortRateDynamics dynamics() {
        return new Dynamics(phi_, a(), sigma());
    }

    @Override
    public double discountBondOption(Option.Type type, double strike,
                                     double maturity,
                                     double bondMaturity) {

        double _a = a();
        double v;
        if (_a < Math.sqrt(QL_EPSILON)) {
            v = sigma() * B(maturity, bondMaturity) * Math.sqrt(maturity);
        } else {
            v = sigma() * B(maturity, bondMaturity) *
                    Math.sqrt(0.5 * (1.0 - Math.exp(-2.0 * _a * maturity)) / _a);
        }
        double f = termStructure().currentLink().discount(bondMaturity, false);
        double k = termStructure().currentLink().discount(maturity, false) * strike;

        return blackFormula(type, k, f, v, 1.0, 0d);
    }

    @Override
    public double discountBondOption(Option.Type type, double strike,
                                     double maturity, double bondStart,
                                     double bondMaturity) {

        double _a = a();
        double v;
        if (_a < Math.sqrt(QL_EPSILON)) {
            v = sigma() * B(bondStart, bondMaturity) * Math.sqrt(maturity);
        } else {
            double c = Math.exp(-2.0 * _a * (bondStart - maturity))
                    - Math.exp(-2.0 * _a * bondStart)
                    - 2.0 * (Math.exp(-_a * (bondStart + bondMaturity - 2.0 * maturity))
                    - Math.exp(-_a * (bondStart + bondMaturity)))
                    + Math.exp(-2.0 * _a * (bondMaturity - maturity))
                    - Math.exp(-2.0 * _a * bondMaturity);
            // The above should always be positive, but due to
            // numerical errors it can be a very small negative number.
            // We floor it at 0 to avoid NaNs.
            v = sigma() / (_a * Math.sqrt(2.0 * _a)) * Math.sqrt(Math.max(c, 0.0));
        }
        double f = termStructure().currentLink().discount(bondMaturity, false);
        double k = termStructure().currentLink().discount(bondStart, false) * strike;

        return blackFormula(type, k, f, v, 1.0, 0.0);
    }

    /*! Futures convexity bias (i.e., the difference between
        futures implied rate and forward rate) calculated as in
        G. Kirikos, D. Novak, "Convexity Conundrums", Risk
        Magazine, March 1997.

        \note t and T should be expressed in yearfraction using
              deposit day counter, F_quoted is futures' market price.
    */
    public static double convexityBias(double futuresPrice,
                                       double t,
                                       double T,
                                       double sigma,
                                       double a) {
        QL_REQUIRE(futuresPrice >= 0.0,
                "negative futures price (" + futuresPrice + ") not allowed");
        QL_REQUIRE(t >= 0.0,
                "negative t (" + t + ") not allowed");
        QL_REQUIRE(T >= t,
                "T (" + T + ") must not be less than t (" + t + ")");
        QL_REQUIRE(sigma >= 0.0,
                "negative sigma (" + sigma + ") not allowed");
        QL_REQUIRE(a >= 0.0,
                "negative a (" + a + ") not allowed");

        Function temp = (x) -> {
            return a < QL_EPSILON ? x : (1.0 - Math.exp(-a * x)) / a;
        };

        double deltaT = (T - t);
        double tempDeltaT = temp.value(deltaT);
        double halfSigmaSquare = sigma * sigma / 2.0;

        // lambda adjusts for the fact that the underlying is an interest rate
        double lambda = temp.value(2.0 * t) * tempDeltaT;

        double tempT = temp.value(t);

        // phi is the MtM adjustment
        double phi = tempT * tempT;

        // the adjustment
        double z = halfSigmaSquare * (lambda + phi);

        double futureRate = (100.0 - futuresPrice) / 100.0;
        return deltaT < QL_EPSILON ? z : (1.0 - Math.exp(-z * tempDeltaT)) * (futureRate + 1.0 / deltaT);
    }

    public static List<Boolean> FixedReversion() {
        return Arrays.asList(true, false);
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

    @Override
    protected void generateArguments() {
        phi_ = new FittingParameter(termStructure(), a(), sigma());
    }

    @Override
    protected double A(double t, double T) {
        double discount1 = termStructure().getValue().discount(t, false);
        double discount2 = termStructure().getValue().discount(T, false);
        double forward = termStructure().getValue().forwardRate(t, t,
                Continuous, Frequency.NO_FREQUENCY, false).rate();
        double temp = sigma() * B(t, T);
        double value = B(t, T) * forward - 0.25 * temp * temp * B(0.0, 2.0 * t);
        return Math.exp(value) * discount2 / discount1;
    }
}
