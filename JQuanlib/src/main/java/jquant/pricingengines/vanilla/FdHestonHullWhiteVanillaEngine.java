package jquant.pricingengines.vanilla;

import jquant.EuropeanOption;
import jquant.Exercise;
import jquant.Option;
import jquant.PricingEngine;
import jquant.cashflows.Dividend;
import jquant.math.Point;
import jquant.methods.finitedifferences.meshers.Fdm1dMesher;
import jquant.methods.finitedifferences.meshers.FdmBlackScholesMesher;
import jquant.methods.finitedifferences.meshers.FdmBlackScholesMultiStrikeMesher;
import jquant.methods.finitedifferences.meshers.FdmHestonVarianceMesher;
import jquant.methods.finitedifferences.meshers.FdmMesher;
import jquant.methods.finitedifferences.meshers.FdmMesherComposite;
import jquant.methods.finitedifferences.meshers.FdmSimpleProcess1dMesher;
import jquant.methods.finitedifferences.solvers.FdmHestonHullWhiteSolver;
import jquant.methods.finitedifferences.solvers.FdmSchemeDesc;
import jquant.methods.finitedifferences.solvers.FdmSolverDesc;
import jquant.methods.finitedifferences.stepconditions.FdmStepConditionComposite;
import jquant.methods.finitedifferences.utilities.FdmBoundaryConditionSet;
import jquant.methods.finitedifferences.utilities.FdmInnerValueCalculator;
import jquant.methods.finitedifferences.utilities.FdmLogInnerValue;
import jquant.models.equity.HestonModel;
import jquant.patterns.Observable;
import jquant.patterns.ObservableSettings;
import jquant.patterns.Observer;
import jquant.pricingengines.PlainVanillaPayoff;
import jquant.pricingengines.StrikedTypePayoff;
import jquant.processes.HestonProcess;
import jquant.processes.HullWhiteProcess;
import jquant.processes.OrnsteinUhlenbeckProcess;
import jquant.time.Date;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static jquant.math.CommonUtil.QL_REQUIRE;
import static jquant.math.MathUtils.NULL_REAL;
import static jquant.time.Frequency.ANNUAL;

/**
 * Finite-differences Heston-Hull-White vanilla option engine.
 *
 * <p>Port of
 * {@code ql/pricingengines/vanilla/fdhestonhullwhitevanillaengine.{hpp,cpp}}.
 */
public class FdHestonHullWhiteVanillaEngine implements PricingEngine, Observer {

    // multiple strikes caching engine
    private static class CachedArgsToResults {
        final Option.arguments first = new Option.arguments();
        final EuropeanOption.results second = new EuropeanOption.results();
    }

    private final HestonModel model_;
    private final HullWhiteProcess hwProcess_;
    private final List<Dividend> dividends_;
    private final double corrEquityShortRate_;
    private final int tGrid_, xGrid_, vGrid_, rGrid_;
    private final int dampingSteps_;
    private final FdmSchemeDesc schemeDesc_;
    private final boolean controlVariate_;

    private final List<Double> strikes_ = new ArrayList<>();
    private final List<CachedArgsToResults> cachedArgs2results_ = new ArrayList<>();

    // Control-variate engines are reused across calculations so that their
    // observer registrations stay bounded; the official code constructs them
    // anew in every calculate() and destroys them at the end of the method.
    private AnalyticHestonEngine controlAnalyticEngine_;
    private FdHestonVanillaEngine controlFdEngine_;

    private final Option.arguments arguments_ = new Option.arguments();
    private final EuropeanOption.results results_ = new EuropeanOption.results();
    private final Set<Observer> observers_ = new HashSet<>();

    public FdHestonHullWhiteVanillaEngine(final HestonModel model,
                                          final HullWhiteProcess hwProcess,
                                          double corrEquityShortRate) {
        this(model, hwProcess, corrEquityShortRate, 50, 100, 40, 20, 0,
                true, FdmSchemeDesc.Hundsdorfer());
    }

    public FdHestonHullWhiteVanillaEngine(final HestonModel model,
                                          final HullWhiteProcess hwProcess,
                                          double corrEquityShortRate,
                                          int tGrid, int xGrid, int vGrid, int rGrid) {
        this(model, hwProcess, corrEquityShortRate, tGrid, xGrid, vGrid, rGrid,
                0, true, FdmSchemeDesc.Hundsdorfer());
    }

    public FdHestonHullWhiteVanillaEngine(final HestonModel model,
                                          final HullWhiteProcess hwProcess,
                                          double corrEquityShortRate,
                                          int tGrid, int xGrid, int vGrid, int rGrid,
                                          int dampingSteps,
                                          boolean controlVariate) {
        this(model, hwProcess, corrEquityShortRate, tGrid, xGrid, vGrid, rGrid,
                dampingSteps, controlVariate, FdmSchemeDesc.Hundsdorfer());
    }

    public FdHestonHullWhiteVanillaEngine(final HestonModel model,
                                          final HullWhiteProcess hwProcess,
                                          double corrEquityShortRate,
                                          int tGrid, int xGrid, int vGrid, int rGrid,
                                          int dampingSteps,
                                          boolean controlVariate,
                                          final FdmSchemeDesc schemeDesc) {
        this(model, hwProcess, Collections.emptyList(), corrEquityShortRate,
                tGrid, xGrid, vGrid, rGrid, dampingSteps, controlVariate,
                schemeDesc);
    }

    public FdHestonHullWhiteVanillaEngine(final HestonModel model,
                                          final HullWhiteProcess hwProcess,
                                          final List<Dividend> dividends,
                                          double corrEquityShortRate,
                                          int tGrid, int xGrid, int vGrid, int rGrid,
                                          int dampingSteps,
                                          boolean controlVariate,
                                          final FdmSchemeDesc schemeDesc) {
        model_ = model;
        hwProcess_ = hwProcess;
        dividends_ = new ArrayList<>(dividends);
        corrEquityShortRate_ = corrEquityShortRate;
        tGrid_ = tGrid;
        xGrid_ = xGrid;
        vGrid_ = vGrid;
        rGrid_ = rGrid;
        dampingSteps_ = dampingSteps;
        schemeDesc_ = schemeDesc;
        controlVariate_ = controlVariate;

        results_.additionalResults = new HashMap<>();
        model_.registerObserver(this);
    }

    @Override
    public Option.arguments getArguments() {
        return arguments_;
    }

    @Override
    public EuropeanOption.results getResults() {
        return results_;
    }

    @Override
    public void reset() {
        results_.value = Double.NaN;
        results_.errorEstimate = Double.NaN;
        results_.valuationDate = new Date();
        results_.delta = Double.NaN;
        results_.gamma = Double.NaN;
        results_.theta = Double.NaN;
        results_.thetaPerDay = Double.NaN;
        results_.vega = Double.NaN;
        results_.rho = Double.NaN;
        results_.dividendRho = Double.NaN;
        results_.elasticity = Double.NaN;
        if (results_.additionalResults != null) {
            results_.additionalResults.clear();
        }
    }

    @Override
    public void calculate() {
        // 1. cache lookup for precalculated results
        for (CachedArgsToResults cachedArgs2result : cachedArgs2results_) {
            if (cachedArgs2result.first.exercise.type() == arguments_.exercise.type()
                    && cachedArgs2result.first.exercise.dates()
                            .equals(arguments_.exercise.dates())) {
                if (arguments_.payoff instanceof PlainVanillaPayoff p1
                        && cachedArgs2result.first.payoff instanceof PlainVanillaPayoff p2
                        && p1.strike() == p2.strike()
                        && p1.optionType() == p2.optionType()) {
                    QL_REQUIRE(dividends_.isEmpty(),
                            "multiple strikes engine does not work with discrete dividends");
                    results_.value = cachedArgs2result.second.value;
                    results_.delta = cachedArgs2result.second.delta;
                    results_.gamma = cachedArgs2result.second.gamma;
                    results_.theta = cachedArgs2result.second.theta;
                    return;
                }
            }
        }

        // 2. Mesher
        final HestonProcess hestonProcess = model_.process();
        final double maturity = hestonProcess.time(arguments_.exercise.lastDate());

        // 2.1 The variance mesher
        final int tGridMin = 5;
        final FdmHestonVarianceMesher varianceMesher =
                new FdmHestonVarianceMesher(vGrid_, hestonProcess, maturity,
                        Math.max(tGridMin, tGrid_ / 50), 0.0001, 1.0);

        // 2.2 The equity mesher
        QL_REQUIRE(arguments_.payoff instanceof StrikedTypePayoff,
                "wrong payoff type given");
        final StrikedTypePayoff payoff = (StrikedTypePayoff) arguments_.payoff;

        final Fdm1dMesher equityMesher;
        if (strikes_.isEmpty()) {
            equityMesher = new FdmBlackScholesMesher(
                    xGrid_,
                    FdmBlackScholesMesher.processHelper(
                            hestonProcess.s0(), hestonProcess.dividendYield(),
                            hestonProcess.riskFreeRate(),
                            varianceMesher.volaEstimate()),
                    maturity, payoff.strike(),
                    NULL_REAL, NULL_REAL, 0.0001, 1.5,
                    new Point<>(payoff.strike(), 0.1),
                    dividends_, null, 0.0);
        } else {
            QL_REQUIRE(dividends_.isEmpty(),
                    "multiple strikes engine does not work with discrete dividends");
            equityMesher = new FdmBlackScholesMultiStrikeMesher(
                    xGrid_,
                    FdmBlackScholesMesher.processHelper(
                            hestonProcess.s0(), hestonProcess.dividendYield(),
                            hestonProcess.riskFreeRate(),
                            varianceMesher.volaEstimate()),
                    maturity, strikes_, 0.0001, 1.5,
                    new Point<>(payoff.strike(), 0.075));
        }

        // 2.3 The short rate mesher
        final OrnsteinUhlenbeckProcess ouProcess =
                new OrnsteinUhlenbeckProcess(hwProcess_.a(), hwProcess_.sigma(),
                        0.0, 0.0);
        final Fdm1dMesher shortRateMesher =
                new FdmSimpleProcess1dMesher(rGrid_, ouProcess, maturity);

        final FdmMesher mesher = new FdmMesherComposite(
                Arrays.asList(equityMesher, varianceMesher, shortRateMesher));

        // 3. Calculator
        final FdmInnerValueCalculator calculator =
                new FdmLogInnerValue(arguments_.payoff, mesher, 0);

        // 4. Step conditions
        final FdmStepConditionComposite conditions =
                FdmStepConditionComposite.vanillaComposite(
                        dividends_, arguments_.exercise, mesher, calculator,
                        hestonProcess.riskFreeRate().currentLink().referenceDate(),
                        hestonProcess.riskFreeRate().currentLink().dayCounter());

        // 5. Boundary conditions
        final FdmBoundaryConditionSet boundaries = new FdmBoundaryConditionSet();

        // 6. Solver
        final FdmSolverDesc solverDesc = new FdmSolverDesc(mesher, boundaries,
                conditions, calculator, maturity, tGrid_, dampingSteps_);

        final FdmHestonHullWhiteSolver solver =
                new FdmHestonHullWhiteSolver(hestonProcess, hwProcess_,
                        corrEquityShortRate_, solverDesc, schemeDesc_);

        final double spot = hestonProcess.s0().currentLink().value();
        final double v0 = hestonProcess.v0();
        results_.value = solver.valueAt(spot, v0, 0);
        results_.delta = solver.deltaAt(spot, v0, 0, spot * 0.01);
        results_.gamma = solver.gammaAt(spot, v0, 0, spot * 0.01);
        results_.theta = solver.thetaAt(spot, v0, 0);

        cachedArgs2results_.clear();
        for (Double strike : strikes_) {
            final CachedArgsToResults cached = new CachedArgsToResults();
            cached.first.exercise = arguments_.exercise;
            cached.first.payoff = new PlainVanillaPayoff(
                    payoff.optionType(), strike);
            final double d = payoff.strike() / strike;

            cached.second.value = solver.valueAt(spot * d, v0, 0) / d;
            cached.second.delta = solver.deltaAt(spot * d, v0, 0, spot * d * 0.01);
            cached.second.gamma = solver.gammaAt(spot * d, v0, 0, spot * d * 0.01) * d;
            cached.second.theta = solver.thetaAt(spot * d, v0, 0) / d;

            cachedArgs2results_.add(cached);
        }

        if (controlVariate_) {
            if (controlAnalyticEngine_ == null) {
                controlAnalyticEngine_ = new AnalyticHestonEngine(model_, 164);
            }
            if (controlFdEngine_ == null) {
                controlFdEngine_ = new FdHestonVanillaEngine(model_, tGrid_,
                        xGrid_, vGrid_, dampingSteps_, schemeDesc_, null);
            }
            controlFdEngine_.enableMultipleStrikesCaching(strikes_);

            final Exercise exercise =
                    new jquant.EuropeanExercise(arguments_.exercise.lastDate());

            final EuropeanOption option = new EuropeanOption(payoff, exercise);
            try {
                option.setPricingEngine(controlAnalyticEngine_);
                double analyticNPV = option.NPV();

                option.setPricingEngine(controlFdEngine_);
                double fdNPV = option.NPV();

                results_.value += analyticNPV - fdNPV;
                for (int i = 0; i < strikes_.size(); ++i) {
                    final EuropeanOption controlVariateOption =
                            new EuropeanOption(new PlainVanillaPayoff(
                                    payoff.optionType(), strikes_.get(i)), exercise);
                    try {
                        controlVariateOption.setPricingEngine(controlAnalyticEngine_);
                        analyticNPV = controlVariateOption.NPV();

                        controlVariateOption.setPricingEngine(controlFdEngine_);
                        fdNPV = controlVariateOption.NPV();
                        cachedArgs2results_.get(i).second.value += analyticNPV - fdNPV;
                    } finally {
                        controlVariateOption.setPricingEngine(null);
                        controlVariateOption.unregisterWithAll();
                    }
                }
            } finally {
                option.setPricingEngine(null);
                option.unregisterWithAll();
            }
        }
    }

    // multiple strikes caching engine
    public void enableMultipleStrikesCaching(final List<Double> strikes) {
        strikes_.clear();
        strikes_.addAll(strikes);
        update();
    }

    @Override
    public void update() {
        cachedArgs2results_.clear();
        notifyObservers();
    }

    @Override
    public void registerObserver(Observer observer) {
        observers_.add(observer);
    }

    @Override
    public void unregisterObserver(Observer observer) {
        observers_.remove(observer);
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
        }
    }

    @Override
    public void unregisterWith(Observable observable) {
        if (observable != null) {
            observable.unregisterObserver(this);
        }
    }

    @Override
    public void unregisterWithAll() {
        model_.unregisterObserver(this);
    }
}
