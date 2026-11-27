package jquant.termstructures.volatility.equityfx;

import jquant.Handle;
import jquant.Option;
import jquant.math.Function;
import jquant.math.integrals.FourierIntegration;
import jquant.math.solvers1d.Brent;
import jquant.models.equity.HestonModel;
import jquant.pricingengines.BlackFormula;
import jquant.pricingengines.PlainVanillaPayoff;
import jquant.pricingengines.vanilla.AnalyticHestonEngine;
import jquant.processes.HestonProcess;
import jquant.time.BusinessDayConvention;
import jquant.time.Date;
import jquant.time.DayCounter;
import jquant.time.calendars.NullCalendar;

/**
 * Black volatility surface backed by a Heston model.
 *
 * <p>Port of
 * {@code ql/termstructures/volatility/equityfx/hestonblackvolsurface.{hpp,cpp}}.
 */
public class HestonBlackVolSurface extends BlackVolTermStructure {

    private final Handle<HestonModel> hestonModel_;
    private final AnalyticHestonEngine.ComplexLogFormula cpxLogFormula_;
    private final FourierIntegration integration_;

    public HestonBlackVolSurface(final Handle<HestonModel> hestonModel) {
        this(hestonModel,
                AnalyticHestonEngine.ComplexLogFormula.AngledContour,
                FourierIntegration.gaussLaguerre(160));
    }

    public HestonBlackVolSurface(
            final Handle<HestonModel> hestonModel,
            AnalyticHestonEngine.ComplexLogFormula cpxLogFormula,
            FourierIntegration integration) {
        super(hestonModel.currentLink().process().riskFreeRate().getValue().referenceDate(),
                new NullCalendar(),
                BusinessDayConvention.FOLLOWING,
                hestonModel.currentLink().process().riskFreeRate().getValue().dayCounter());
        hestonModel_ = hestonModel;
        cpxLogFormula_ = cpxLogFormula;
        integration_ = integration;

        // Handle.get() exposes the link as an Observable.
        registerWith(hestonModel_.get());
    }

    @Override
    public DayCounter dayCounter() {
        return hestonModel_.currentLink().process().riskFreeRate().getValue().dayCounter();
    }

    @Override
    public Date maxDate() {
        return Date.maxDate();
    }

    @Override
    public double minStrike() {
        return 0.0;
    }

    @Override
    public double maxStrike() {
        return Double.MAX_VALUE;
    }

    @Override
    public double atmLevel(double t) {
        final HestonProcess process = hestonModel_.currentLink().process();
        return process.s0().getValue().value()
                * process.dividendYield().getValue().discount(t, true)
                / process.riskFreeRate().getValue().discount(t, true);
    }

    @Override
    protected double blackVarianceImpl(double t, double strike) {
        final double vol = blackVolImpl(t, strike);
        return vol * vol * t;
    }

    @Override
    protected double blackVolImpl(double t, double strike) {
        // Official creates a temporary engine here. Java's AnalyticHestonEngine
        // has no unregisterWithAll(), so the engine's observer registration on
        // the model cannot be undone (see the port notes).
        final AnalyticHestonEngine hestonEngine = new AnalyticHestonEngine(
                hestonModel_.currentLink(), cpxLogFormula_, integration_);

        final HestonProcess process = hestonModel_.currentLink().process();

        final double df = process.riskFreeRate().getValue().discount(t, true);

        final double fwd = process.s0().getValue().value()
                * process.dividendYield().getValue().discount(t, true) / df;

        final PlainVanillaPayoff payoff = new PlainVanillaPayoff(
                fwd > strike ? Option.Type.Put : Option.Type.Call, strike);

        final double npv = hestonEngine.priceVanillaPayoff(payoff, t);

        final double theta = hestonModel_.currentLink().theta();
        if (npv <= 0.0) return Math.sqrt(theta);

        final Brent solver = new Brent();
        solver.setMaxEvaluations(10000);
        final double guess = Math.sqrt(theta);
        // official std::numeric_limits<double>::epsilon()
        final double accuracy = 2.220446049250313e-16;

        return solver.solve(
                (Function) v -> blackValue(
                        payoff.optionType(), strike, fwd, t, v, df, npv),
                accuracy, guess, 0.01);
    }

    private static double blackValue(Option.Type optionType, double strike,
                                     double forward, double maturity,
                                     double vol, double discount, double npv) {
        return BlackFormula.blackFormula(optionType, strike, forward,
                Math.max(0.0, vol) * Math.sqrt(maturity),
                discount, 0.0) - npv;
    }
}
