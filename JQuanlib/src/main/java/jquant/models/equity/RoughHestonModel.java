package jquant.models.equity;

import jquant.math.optimization.BoundaryConstraint;
import jquant.math.optimization.PositiveConstraint;
import jquant.models.CalibratedModel;
import jquant.models.ConstantParameter;
import jquant.patterns.Observable;
import jquant.patterns.ObservableSettings;
import jquant.patterns.Observer;
import jquant.processes.HestonProcess;

import java.util.HashSet;
import java.util.Set;

/**
 * Rough Heston model for the stochastic volatility of an asset.
 *
 * <p>Official QuantLib {@code ql/models/equity/roughhestonmodel.{hpp,cpp}}
 * port.
 *
 * <p>References:
 * O. El Euch and M. Rosenbaum, The characteristic function of rough Heston
 * models, Mathematical Finance 29(1), 3-38 (2019).
 *
 * <p>J. Gatheral, T. Jaisson and M. Rosenbaum, Volatility is rough,
 * Quantitative Finance 18(6), 933-949 (2018).
 */
public class RoughHestonModel extends CalibratedModel {

    protected HestonProcess process_;

    private final Set<Observer> observers_ = new HashSet<>();
    private final Set<Observable> registeredObservables_ = new HashSet<>();

    public RoughHestonModel(final HestonProcess process, double hurst) {
        super(6);
        process_ = process;

        // variance mean reversion level
        arguments_.set(0, new ConstantParameter(process.theta(), new PositiveConstraint()));
        // variance mean reversion speed
        arguments_.set(1, new ConstantParameter(process.kappa(), new PositiveConstraint()));
        // volatility of the volatility
        arguments_.set(2, new ConstantParameter(process.sigma(), new PositiveConstraint()));
        // correlation
        arguments_.set(3, new ConstantParameter(process.rho(), new BoundaryConstraint(-1.0, 1.0)));
        // spot variance
        arguments_.set(4, new ConstantParameter(process.v0(), new PositiveConstraint()));
        // Hurst exponent of the variance process
        arguments_.set(5, new ConstantParameter(hurst, new BoundaryConstraint(0.0, 0.5)));

        registerWith(process_.riskFreeRate().get());
        registerWith(process_.dividendYield().get());
        registerWith(process_.s0().get());
    }

    // variance mean reversion level
    public double theta() {
        return arguments_.get(0).value(0.0);
    }

    // variance mean reversion speed
    public double kappa() {
        return arguments_.get(1).value(0.0);
    }

    // volatility of the volatility
    public double sigma() {
        return arguments_.get(2).value(0.0);
    }

    // correlation
    public double rho() {
        return arguments_.get(3).value(0.0);
    }

    // spot variance
    public double v0() {
        return arguments_.get(4).value(0.0);
    }

    // Hurst exponent of the variance process
    public double hurst() {
        return arguments_.get(5).value(0.0);
    }

    // underlying process
    public HestonProcess process() {
        return process_;
    }

    @Override
    protected void generateArguments() {
        process_ = new HestonProcess(process_.riskFreeRate(),
                process_.dividendYield(),
                process_.s0(),
                v0(), kappa(), theta(), sigma(), rho());
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
