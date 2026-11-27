package jquant.pricingengines.vanilla;

import jquant.EuropeanOption;
import jquant.Option;
import jquant.PricingEngine;
import jquant.cashflows.Dividend;
import jquant.math.Point;
import jquant.methods.finitedifferences.meshers.Fdm1dMesher;
import jquant.methods.finitedifferences.meshers.FdmBlackScholesMesher;
import jquant.methods.finitedifferences.meshers.FdmBlackScholesMultiStrikeMesher;
import jquant.methods.finitedifferences.meshers.FdmHestonLocalVolatilityVarianceMesher;
import jquant.methods.finitedifferences.meshers.FdmMesher;
import jquant.methods.finitedifferences.meshers.FdmMesherComposite;
import jquant.methods.finitedifferences.solvers.FdmHestonSolver;
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
import jquant.termstructures.volatility.equityfx.LocalVolTermStructure;
import jquant.time.Date;
import jquant.utilities.FdmQuantoHelper;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static jquant.math.CommonUtil.QL_REQUIRE;
import static jquant.math.MathUtils.NULL_REAL;

/**
 * Finite-differences Heston vanilla option engine.
 *
 * <p>Port of {@code ql/pricingengines/vanilla/fdhestonvanillaengine.{hpp,cpp}}.
 */
public class FdHestonVanillaEngine implements PricingEngine {
    // multiple strikes caching engine
    private static class CachedArgsToResults {
        final Option.arguments first = new Option.arguments();
        final EuropeanOption.results second = new EuropeanOption.results();
    }

    private final HestonModel model_;
    private final List<Dividend> dividends_;
    private final int tGrid_, xGrid_, vGrid_, dampingSteps_;
    private final FdmSchemeDesc schemeDesc_;
    private final LocalVolTermStructure leverageFct_;
    private final FdmQuantoHelper quantoHelper_;
    private final double mixingFactor_;

    private final List<Double> strikes_ = new ArrayList<>();
    private final List<CachedArgsToResults> cachedArgs2results_ = new ArrayList<>();

    private final Option.arguments arguments_ = new Option.arguments();
    private final EuropeanOption.results results_ = new EuropeanOption.results();
    private final Set<Observer> observers_ = new HashSet<>();

    public FdHestonVanillaEngine(final HestonModel model) {
        this(model, 100, 100, 50, 0, FdmSchemeDesc.Hundsdorfer(),
                null, 1.0);
    }

    public FdHestonVanillaEngine(final HestonModel model,
                                 int tGrid, int xGrid, int vGrid) {
        this(model, tGrid, xGrid, vGrid, 0, FdmSchemeDesc.Hundsdorfer(),
                null, 1.0);
    }

    public FdHestonVanillaEngine(final HestonModel model,
                                 int tGrid, int xGrid, int vGrid, int dampingSteps,
                                 final FdmSchemeDesc schemeDesc,
                                 final LocalVolTermStructure leverageFct,
                                 double mixingFactor) {
        this(model, Collections.emptyList(), null, tGrid, xGrid, vGrid,
                dampingSteps, schemeDesc, leverageFct, mixingFactor);
    }

    public FdHestonVanillaEngine(final HestonModel model,
                                 int tGrid, int xGrid, int vGrid, int dampingSteps,
                                 final FdmSchemeDesc schemeDesc,
                                 final LocalVolTermStructure leverageFct) {
        this(model, tGrid, xGrid, vGrid, dampingSteps, schemeDesc,
                leverageFct, 1.0);
    }

    public FdHestonVanillaEngine(final HestonModel model,
                                 final List<Dividend> dividends,
                                 int tGrid, int xGrid, int vGrid, int dampingSteps,
                                 final FdmSchemeDesc schemeDesc,
                                 final LocalVolTermStructure leverageFct,
                                 double mixingFactor) {
        this(model, dividends, null, tGrid, xGrid, vGrid,
                dampingSteps, schemeDesc, leverageFct, mixingFactor);
    }

    public FdHestonVanillaEngine(final HestonModel model,
                                 final FdmQuantoHelper quantoHelper,
                                 int tGrid, int xGrid, int vGrid, int dampingSteps,
                                 final FdmSchemeDesc schemeDesc,
                                 final LocalVolTermStructure leverageFct,
                                 double mixingFactor) {
        this(model, Collections.emptyList(), quantoHelper, tGrid, xGrid, vGrid,
                dampingSteps, schemeDesc, leverageFct, mixingFactor);
    }

    public FdHestonVanillaEngine(final HestonModel model,
                                 final List<Dividend> dividends,
                                 final FdmQuantoHelper quantoHelper,
                                 int tGrid, int xGrid, int vGrid, int dampingSteps,
                                 final FdmSchemeDesc schemeDesc,
                                 final LocalVolTermStructure leverageFct,
                                 double mixingFactor) {
        model_ = model;
        dividends_ = new ArrayList<>(dividends);
        tGrid_ = tGrid;
        xGrid_ = xGrid;
        vGrid_ = vGrid;
        dampingSteps_ = dampingSteps;
        schemeDesc_ = schemeDesc;
        leverageFct_ = leverageFct;
        quantoHelper_ = quantoHelper;
        mixingFactor_ = mixingFactor;

        results_.additionalResults = new HashMap<>();
        model_.registerObserver(new Observer() {
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
                // Official FdHestonVanillaEngine::update() clears the
                // multiple-strikes cache before forwarding the notification.
                cachedArgs2results_.clear();
                notifyObservers();
            }
        });
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
        // cache lookup for precalculated results
        for (CachedArgsToResults cachedArgs2result : cachedArgs2results_) {
            if (cachedArgs2result.first.exercise.type() == arguments_.exercise.type()
                    && cachedArgs2result.first.exercise.dates().equals(arguments_.exercise.dates())) {
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

        final HestonProcess process = model_.process();

        final FdmHestonSolver solver = new FdmHestonSolver(
                process, getSolverDesc(1.5), schemeDesc_,
                quantoHelper_, leverageFct_, mixingFactor_);

        final double v0 = process.v0();
        final double spot = process.s0().getValue().value();

        results_.value = solver.valueAt(spot, v0);
        results_.delta = solver.deltaAt(spot, v0);
        results_.gamma = solver.gammaAt(spot, v0);
        results_.theta = solver.thetaAt(spot, v0);

        cachedArgs2results_.clear();
        final StrikedTypePayoff payoff = (StrikedTypePayoff) arguments_.payoff;
        for (Double strike : strikes_) {
            final CachedArgsToResults cached = new CachedArgsToResults();
            cached.first.exercise = arguments_.exercise;
            cached.first.payoff = new PlainVanillaPayoff(
                    payoff.optionType(), strike);
            final double d = payoff.strike() / strike;

            cached.second.value = solver.valueAt(spot * d, v0) / d;
            cached.second.delta = solver.deltaAt(spot * d, v0);
            cached.second.gamma = solver.gammaAt(spot * d, v0) * d;
            cached.second.theta = solver.thetaAt(spot * d, v0) / d;

            cachedArgs2results_.add(cached);
        }
    }

    // helper method for Heston like engines
    public FdmSolverDesc getSolverDesc(double equityScaleFactor) {
        // 1. Mesher
        final HestonProcess process = model_.process();
        final double maturity = process.time(arguments_.exercise.lastDate());

        // 1.1 The variance mesher
        final int tGridMin = 5;
        final int tGridAvgSteps = Math.max(tGridMin, tGrid_ / 50);
        final FdmHestonLocalVolatilityVarianceMesher vMesher =
                new FdmHestonLocalVolatilityVarianceMesher(
                        vGrid_, process, leverageFct_, maturity, tGridAvgSteps,
                        0.0001, mixingFactor_);

        final double avgVolaEstimate = vMesher.volaEstimate();

        // 1.2 The equity mesher
        QL_REQUIRE(arguments_.payoff instanceof StrikedTypePayoff,
                "non-striked payoff given");
        final StrikedTypePayoff payoff = (StrikedTypePayoff) arguments_.payoff;

        Fdm1dMesher equityMesher;
        if (strikes_.isEmpty()) {
            equityMesher = new FdmBlackScholesMesher(
                    xGrid_,
                    FdmBlackScholesMesher.processHelper(
                            process.s0(), process.dividendYield(),
                            process.riskFreeRate(), avgVolaEstimate),
                    maturity, payoff.strike(),
                    NULL_REAL, NULL_REAL, 0.0001, 2.0,
                    new Point<>(payoff.strike(), 0.1),
                    dividends_, quantoHelper_, 0.0);
        } else {
            QL_REQUIRE(dividends_.isEmpty(),
                    "multiple strikes engine does not work with discrete dividends");
            equityMesher = new FdmBlackScholesMultiStrikeMesher(
                    xGrid_,
                    FdmBlackScholesMesher.processHelper(
                            process.s0(), process.dividendYield(),
                            process.riskFreeRate(), avgVolaEstimate),
                    maturity, strikes_, 0.0001, 1.5,
                    new Point<>(payoff.strike(), 0.075));
        }

        final FdmMesher mesher = new FdmMesherComposite(
                Arrays.asList(equityMesher, vMesher));

        // 2. Calculator
        final FdmInnerValueCalculator calculator =
                new FdmLogInnerValue(arguments_.payoff, mesher, 0);

        // 3. Step conditions
        final FdmStepConditionComposite conditions =
                FdmStepConditionComposite.vanillaComposite(
                        dividends_, arguments_.exercise, mesher, calculator,
                        process.riskFreeRate().getValue().referenceDate(),
                        process.riskFreeRate().getValue().dayCounter());

        // 4. Boundary conditions
        final FdmBoundaryConditionSet boundaries = new FdmBoundaryConditionSet();

        // 5. Solver
        return new FdmSolverDesc(mesher, boundaries, conditions,
                calculator, maturity, tGrid_, dampingSteps_);
    }

    // multiple strikes caching engine
    public void enableMultipleStrikesCaching(final List<Double> strikes) {
        strikes_.clear();
        strikes_.addAll(strikes);
        cachedArgs2results_.clear();
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
}
