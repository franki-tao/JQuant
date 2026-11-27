package jquant.models.equity;

import jquant.Handle;
import jquant.Quote;
import jquant.TimeGrid;
import jquant.math.optimization.PositiveConstraint;
import jquant.models.CalibratedModel;
import jquant.models.ConstantParameter;
import jquant.models.Parameter;
import jquant.patterns.Observable;
import jquant.patterns.ObservableSettings;
import jquant.patterns.Observer;
import jquant.termstructures.YieldTermStructure;

import java.util.HashSet;
import java.util.Set;

/**
 * Piecewise time dependent Heston model.
 *
 * <p>Port of {@code ql/models/equity/piecewisetimedependenthestonmodel.{hpp,cpp}}.
 *
 * <p>References:
 * Heston, Steven L., 1993. A Closed-Form Solution for Options with
 * Stochastic Volatility with Applications to Bond and Currency Options.
 * The Review of Financial Studies, Volume 6, Issue 2, 327-343.
 *
 * <p>A. Elices, Models with time-dependent parameters using transform
 * methods: application to Heston's model,
 * http://arxiv.org/pdf/0708.2020
 */
public class PiecewiseTimeDependentHestonModel extends CalibratedModel {

    protected final Handle<Quote> s0_;
    protected final Handle<YieldTermStructure> riskFreeRate_;
    protected final Handle<YieldTermStructure> dividendYield_;
    protected final TimeGrid timeGrid_;

    private final Set<Observer> observers_ = new HashSet<>();
    private final Set<Observable> registeredObservables_ = new HashSet<>();

    public PiecewiseTimeDependentHestonModel(
            final Handle<YieldTermStructure> riskFreeRate,
            final Handle<YieldTermStructure> dividendYield,
            final Handle<Quote> s0,
            double v0,
            final Parameter theta,
            final Parameter kappa,
            final Parameter sigma,
            final Parameter rho,
            TimeGrid timeGrid) {
        super(5);
        s0_ = s0;
        riskFreeRate_ = riskFreeRate;
        dividendYield_ = dividendYield;
        timeGrid_ = timeGrid;

        // Official assigns the four passed parameters directly (no clone);
        // only v0 is wrapped into a ConstantParameter.
        arguments_.set(0, theta);
        arguments_.set(1, kappa);
        arguments_.set(2, sigma);
        arguments_.set(3, rho);
        arguments_.set(4, new ConstantParameter(v0, new PositiveConstraint()));

        registerWith(s0_.currentLink());
        registerWith(riskFreeRate_.currentLink());
        registerWith(dividendYield_.currentLink());
    }

    // variance mean version level
    public double theta(double t) {
        return arguments_.get(0).value(t);
    }

    // variance mean reversion speed
    public double kappa(double t) {
        return arguments_.get(1).value(t);
    }

    // volatility of the volatility
    public double sigma(double t) {
        return arguments_.get(2).value(t);
    }

    // correlation
    public double rho(double t) {
        return arguments_.get(3).value(t);
    }

    // spot variance
    public double v0() {
        return arguments_.get(4).value(0.0);
    }

    // spot
    public double s0() {
        return s0_.getValue().value();
    }

    public TimeGrid timeGrid() {
        return timeGrid_;
    }

    public Handle<YieldTermStructure> dividendYield() {
        return dividendYield_;
    }

    public Handle<YieldTermStructure> riskFreeRate() {
        return riskFreeRate_;
    }

    @Override
    protected void generateArguments() {
        // Nothing to rebuild: the model is given directly by its parameters.
    }

    @Override
    public void registerObserver(Observer o) {
        observers_.add(o);
    }

    @Override
    public void unregisterObserver(Observer o) {
        observers_.remove(o);
    }

    @Override
    public void notifyObservers() {
        if (!ObservableSettings.getInstance().isUpdatesEnabled()) {
            ObservableSettings.getInstance().registerDeferred(observers_);
        } else {
            for (Observer observer : Set.copyOf(observers_)) {
                observer.update();
            }
        }
    }

    @Override
    public void registerWith(Observable observable) {
        if (observable != null) {
            observable.registerObserver(this);
            registeredObservables_.add(observable);
        }
    }

    @Override
    public void unregisterWith(Observable observable) {
        if (observable != null) {
            observable.unregisterObserver(this);
            registeredObservables_.remove(observable);
        }
    }

    @Override
    public void unregisterWithAll() {
        for (Observable observable : registeredObservables_) {
            observable.unregisterObserver(this);
        }
        registeredObservables_.clear();
    }
}
