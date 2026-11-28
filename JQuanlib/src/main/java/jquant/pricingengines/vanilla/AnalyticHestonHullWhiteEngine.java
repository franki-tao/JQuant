package jquant.pricingengines.vanilla;

import jquant.math.integrals.FourierIntegration;
import jquant.models.equity.HestonModel;
import jquant.models.shortrate.onefactormodels.HullWhite;
import jquant.patterns.Observable;
import jquant.patterns.Observer;
import org.apache.commons.math3.complex.Complex;

import static jquant.math.MathUtils.NULL_REAL;
import static jquant.math.MathUtils.QL_EPSILON;

/**
 * Analytic Heston engine including stochastic interest rates.
 *
 * <p>Port of
 * {@code ql/pricingengines/vanilla/analytichestonhullwhiteengine.{hpp,cpp}}.
 * The official class derives from {@code AnalyticHestonEngine} and adds the
 * Hull-White contribution through {@link #addOnTerm}.
 */
public class AnalyticHestonHullWhiteEngine extends AnalyticHestonEngine {

    protected final HullWhite hullWhiteModel_;
    protected double m_;
    private double a_, sigma_;

    public AnalyticHestonHullWhiteEngine(final HestonModel hestonModel,
                                         final HullWhite hullWhiteModel,
                                         int integrationOrder) {
        super(hestonModel, ComplexLogFormula.Gatheral,
                FourierIntegration.gaussLaguerre(integrationOrder));
        hullWhiteModel_ = hullWhiteModel;
        setParameters();
        registerWithHullWhiteModel();
    }

    public AnalyticHestonHullWhiteEngine(final HestonModel hestonModel,
                                         final HullWhite hullWhiteModel,
                                         double relTolerance,
                                         int maxEvaluations) {
        super(hestonModel, ComplexLogFormula.Gatheral,
                FourierIntegration.gaussLobatto(relTolerance, NULL_REAL,
                        maxEvaluations, false));
        hullWhiteModel_ = hullWhiteModel;
        setParameters();
        registerWithHullWhiteModel();
    }

    private void registerWithHullWhiteModel() {
        hullWhiteModel_.registerObserver(new Observer() {
            @Override
            public void registerWith(Observable observable) {
            }

            @Override
            public void unregisterWithAll() {
            }

            @Override
            public void unregisterWith(Observable observable) {
            }

            @Override
            public void update() {
                // Official AnalyticHestonHullWhiteEngine::update():
                // setParameters() followed by AnalyticHestonEngine::update().
                setParameters();
                notifyObservers();
            }
        });
    }

    @Override
    public void calculate() {
        final double t = model_.process().time(arguments_.exercise.lastDate());
        if (a_ * t > Math.pow(QL_EPSILON, 0.25)) {
            m_ = sigma_ * sigma_ / (2 * a_ * a_)
                    * (t + 2 / a_ * Math.exp(-a_ * t)
                    - 1 / (2 * a_) * Math.exp(-2 * a_ * t) - 3 / (2 * a_));
        } else {
            // low-a algebraic limit
            m_ = 0.5 * sigma_ * sigma_ * t * t * t
                    * (1 / 3.0 - 0.25 * a_ * t + 7 / 60.0 * a_ * a_ * t * t);
        }

        super.calculate();
    }

    protected void setParameters() {
        a_ = hullWhiteModel_.a();
        sigma_ = hullWhiteModel_.sigma();
    }

    @Override
    protected Complex addOnTerm(double u, double t, int j) {
        return new Complex(-m_ * u * u, u * (m_ - 2 * m_ * (j - 1)));
    }
}
