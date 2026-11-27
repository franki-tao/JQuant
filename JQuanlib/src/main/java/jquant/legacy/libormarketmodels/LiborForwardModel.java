package jquant.legacy.libormarketmodels;

import jquant.Option;
import jquant.indexes.IborIndex;
import jquant.math.Array;
import jquant.math.CommonUtil;
import jquant.math.Matrix;
import jquant.models.AffineModel;
import jquant.models.CalibratedModel;
import jquant.models.Parameter;
import jquant.patterns.Observable;
import jquant.patterns.ObservableSettings;
import jquant.patterns.Observer;
import jquant.pricingengines.BlackFormula;
import jquant.termstructures.volatility.swaption.SwaptionVolatilityMatrix;
import jquant.time.BusinessDayConvention;
import jquant.time.Date;
import jquant.time.Period;
import jquant.time.TimeUtils;
import jquant.time.calendars.NullCalendar;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static jquant.math.CommonUtil.QL_REQUIRE;
import static jquant.math.MathUtils.QL_EPSILON;

/**
 * Libor forward model including exact cap pricing and Rebonato's approximate
 * swaption volatility matrix.
 *
 * <p>Official QuantLib
 * {@code ql/legacy/libormarketmodels/liborforwardmodel.hpp/.cpp} port.
 */
public class LiborForwardModel extends CalibratedModel implements AffineModel {

    private final List<Double> f_;
    private final List<Double> accrualPeriod_;

    private final LfmCovarianceProxy covarProxy_;
    private final LiborForwardModelProcess process_;

    private SwaptionVolatilityMatrix swaptionVola_;

    private final Set<Observer> observers_ = new HashSet<>();
    private final Set<Observable> registeredObservables_ = new HashSet<>();

    public LiborForwardModel(final LiborForwardModelProcess process,
                             final LmVolatilityModel volaModel,
                             final LmCorrelationModel corrModel) {
        super(volaModel.params().size() + corrModel.params().size());

        f_ = CommonUtil.ArrayInit(process.size(), 0.0);
        accrualPeriod_ = CommonUtil.ArrayInit(process.size(), 0.0);
        covarProxy_ = new LfmCovarianceProxy(volaModel, corrModel);
        process_ = process;

        final int k = volaModel.params().size();
        for (int i = 0; i < k; ++i)
            arguments_.set(i, volaModel.params().get(i));
        for (int i = 0; i < corrModel.params().size(); ++i)
            arguments_.set(k + i, corrModel.params().get(i));

        for (int i = 0; i < process.size(); ++i) {
            accrualPeriod_.set(i,
                    process.accrualEndTimes().get(i)
                            - process.accrualStartTimes().get(i));
            f_.set(i, 1.0 / (1.0 + accrualPeriod_.get(i)
                    * process.initialValues().get(i)));
        }
    }

    @Override
    public void setParams(final Array params) {
        super.setParams(params);

        final int k = covarProxy_.volatilityModel().params().size();

        covarProxy_.volatilityModel().setParams(
                new ArrayList<>(arguments_.subList(0, k)));
        covarProxy_.correlationModel().setParams(
                new ArrayList<>(arguments_.subList(k, arguments_.size())));

        swaptionVola_ = null;
    }

    @Override
    public double discountBondOption(Option.Type type,
                                     double strike, double maturity,
                                     double bondMaturity) {
        final List<Double> accrualStartTimes = process_.accrualStartTimes();
        final List<Double> accrualEndTimes = process_.accrualEndTimes();

        QL_REQUIRE(accrualStartTimes.get(0) <= maturity
                        && accrualStartTimes.get(accrualStartTimes.size() - 1) >= maturity,
                "capet maturity does not fit to the process");

        final int i = CommonUtil.lower_bound(accrualStartTimes, maturity);

        QL_REQUIRE(i < process_.size()
                        && Math.abs(maturity - accrualStartTimes.get(i))
                        < 100 * QL_EPSILON
                        && Math.abs(bondMaturity - accrualEndTimes.get(i))
                        < 100 * QL_EPSILON,
                "irregular fixings are not (yet) supported");

        final double tenor = accrualEndTimes.get(i) - accrualStartTimes.get(i);
        final double forward = process_.initialValues().get(i);
        final double capRate = (1.0 / strike - 1.0) / tenor;
        final double var = covarProxy_.integratedCovariance(
                i, i, process_.fixingTimes().get(i));
        final double dis = process_.index().forwardingTermStructure()
                .currentLink().discount(bondMaturity);

        final double black = BlackFormula.blackFormula(
                type == Option.Type.Put ? Option.Type.Call : Option.Type.Put,
                capRate, forward, Math.sqrt(var), 1.0, 0.0);

        final double npv = dis * tenor * black;

        return npv / (1.0 + capRate * tenor);
    }

    protected Array w_0(int alpha, int beta) {
        Array omega = new Array(beta + 1, 0.0);
        QL_REQUIRE(alpha < beta, "alpha needs to be smaller than beta");

        double s = 0.0;
        for (int k = alpha + 1; k <= beta; ++k) {
            double b = accrualPeriod_.get(k);
            for (int j = alpha + 1; j <= k; ++j)
                b *= f_.get(j);
            s += b;
        }

        for (int i = alpha + 1; i <= beta; ++i) {
            double a = accrualPeriod_.get(i);
            for (int j = alpha + 1; j <= i; ++j)
                a *= f_.get(j);
            omega.set(i, a / s);
        }

        return omega;
    }

    public double S_0(int alpha, int beta) {
        final Array w = w_0(alpha, beta);
        final Array f = process_.initialValues();

        double fwdRate = 0.0;
        for (int i = alpha + 1; i <= beta; ++i)
            fwdRate += w.get(i) * f.get(i);

        return fwdRate;
    }

    public SwaptionVolatilityMatrix getSwaptionVolatilityMatrix() {
        if (swaptionVola_ != null)
            return swaptionVola_;

        final IborIndex index = process_.index();
        final Date today = process_.fixingDates().get(0);

        final int size = process_.size() / 2;
        Matrix volatilities = new Matrix(size, size, 0.0);

        Date[] exercises = new Date[size];
        for (int i = 0; i < size; ++i)
            exercises[i] = process_.fixingDates().get(i + 1).copy();

        List<Period> lengths = new ArrayList<>(size);
        for (int i = 0; i < size; ++i)
            lengths.add(TimeUtils.multiply(i + 1, index.tenor()));

        final Array f = process_.initialValues();
        for (int k = 0; k < size; ++k) {
            final int alpha = k;
            final double tAlpha = process_.fixingTimes().get(alpha + 1);

            Matrix var = new Matrix(size, size, 0.0);
            for (int i = alpha + 1; i <= k + size; ++i) {
                for (int j = i; j <= k + size; ++j) {
                    final double value = covarProxy_.integratedCovariance(i, j, tAlpha);
                    var.set(i - alpha - 1, j - alpha - 1, value);
                    var.set(j - alpha - 1, i - alpha - 1, value);
                }
            }

            for (int l = 1; l <= size; ++l) {
                final int beta = l + k;
                final Array w = w_0(alpha, beta);

                double sum = 0.0;
                for (int i = alpha + 1; i <= beta; ++i) {
                    for (int j = alpha + 1; j <= beta; ++j) {
                        sum += w.get(i) * w.get(j) * f.get(i) * f.get(j)
                                * var.get(i - alpha - 1, j - alpha - 1);
                    }
                }
                volatilities.set(k, l - 1,
                        Math.sqrt(sum / tAlpha) / S_0(alpha, beta));
            }
        }

        swaptionVola_ = new SwaptionVolatilityMatrix(
                today, new NullCalendar(), BusinessDayConvention.FOLLOWING,
                exercises, lengths, volatilities, index.dayCounter());
        return swaptionVola_;
    }

    @Override
    public double discount(double t) {
        return process_.index().forwardingTermStructure()
                .currentLink().discount(t);
    }

    @Override
    public double discountBond(double now, double maturity, Array factors) {
        return discount(maturity);
    }

    @Override
    protected void generateArguments() {
    }

    // Observable / Observer plumbing (CalibratedModel leaves these to concrete
    // models in this Java port; see HestonModel).
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
            for (Observer observer : Set.copyOf(observers_))
                observer.update();
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
        for (Observable observable : registeredObservables_)
            observable.unregisterObserver(this);
        registeredObservables_.clear();
    }
}
