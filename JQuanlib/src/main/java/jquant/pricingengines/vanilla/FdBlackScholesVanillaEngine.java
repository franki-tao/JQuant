package jquant.pricingengines.vanilla;

import jquant.EuropeanOption;
import jquant.Option;
import jquant.PricingEngine;
import jquant.cashflows.Dividend;
import jquant.math.Point;
import jquant.methods.finitedifferences.meshers.Fdm1dMesher;
import jquant.methods.finitedifferences.meshers.FdmBlackScholesMesher;
import jquant.methods.finitedifferences.meshers.FdmMesher;
import jquant.methods.finitedifferences.meshers.FdmMesherComposite;
import jquant.methods.finitedifferences.solvers.FdmBlackScholesSolver;
import jquant.methods.finitedifferences.solvers.FdmSchemeDesc;
import jquant.methods.finitedifferences.solvers.FdmSolverDesc;
import jquant.methods.finitedifferences.stepconditions.FdmStepConditionComposite;
import jquant.methods.finitedifferences.utilities.FdmBoundaryConditionSet;
import jquant.methods.finitedifferences.utilities.FdmInnerValueCalculator;
import jquant.methods.finitedifferences.utilities.FdmLogInnerValue;
import jquant.patterns.Observable;
import jquant.patterns.ObservableSettings;
import jquant.patterns.Observer;
import jquant.pricingengines.StrikedTypePayoff;
import jquant.processes.GeneralizedBlackScholesProcess;
import jquant.termstructures.YieldTermStructure;
import jquant.time.Date;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static jquant.math.CommonUtil.QL_REQUIRE;
import static jquant.math.MathUtils.NULL_REAL;

/**
 * Finite-differences Black-Scholes vanilla option engine.
 *
 * <p>Port of {@code ql/pricingengines/vanilla/fdblackscholesvanillaengine.{hpp,cpp}}.
 * The European, no-dividend, non-quanto path is implemented. The cash
 * dividend model defaults to {@code Spot} as in QuantLib.
 */
public class FdBlackScholesVanillaEngine implements PricingEngine {
    public enum CashDividendModel {
        Spot,
        Escrowed
    }

    private final GeneralizedBlackScholesProcess process_;
    private final int tGrid_, xGrid_, dampingSteps_;
    private final FdmSchemeDesc schemeDesc_;
    private final boolean localVol_;
    private final double illegalLocalVolOverwrite_;

    private final Option.arguments arguments_ = new Option.arguments();
    private final EuropeanOption.results results_ = new EuropeanOption.results();
    private final Set<Observer> observers_ = new HashSet<>();

    public FdBlackScholesVanillaEngine(GeneralizedBlackScholesProcess process) {
        this(process, 100, 100);
    }

    public FdBlackScholesVanillaEngine(GeneralizedBlackScholesProcess process,
                                       int tGrid, int xGrid) {
        this(process, tGrid, xGrid, 0, FdmSchemeDesc.Douglas(),
                false, -NULL_REAL);
    }

    public FdBlackScholesVanillaEngine(GeneralizedBlackScholesProcess process,
                                       int tGrid, int xGrid, int dampingSteps,
                                       FdmSchemeDesc schemeDesc,
                                       boolean localVol,
                                       double illegalLocalVolOverwrite) {
        process_ = process;
        tGrid_ = tGrid;
        xGrid_ = xGrid;
        dampingSteps_ = dampingSteps;
        schemeDesc_ = schemeDesc;
        localVol_ = localVol;
        illegalLocalVolOverwrite_ = illegalLocalVolOverwrite;

        results_.additionalResults = new HashMap<>();
        process_.registerObserver(new Observer() {
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
                notifyObservers();
            }
        });
    }

    @Override
    public arguments getArguments() {
        return arguments_;
    }

    @Override
    public results getResults() {
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
        QL_REQUIRE(arguments_.payoff instanceof StrikedTypePayoff,
                "non-striked payoff given");
        final StrikedTypePayoff payoff = (StrikedTypePayoff) arguments_.payoff;

        final Date exerciseDate = arguments_.exercise.lastDate();
        final double maturity = process_.time(exerciseDate);

        // Spot cash dividend model: no escrowed-dividend adjustment.
        final double spotAdjustment = 0.0;
        final List<Dividend> dividendSchedule = Collections.emptyList();

        // 1. Mesher
        final Fdm1dMesher equityMesher = new FdmBlackScholesMesher(
                xGrid_, process_, maturity, payoff.strike(),
                Double.NaN, Double.NaN, 0.0001, 1.5,
                new Point<>(payoff.strike(), 0.1),
                dividendSchedule, null, spotAdjustment);

        final FdmMesher mesher = new FdmMesherComposite(equityMesher);

        // 2. Calculator
        final FdmInnerValueCalculator calculator =
                new FdmLogInnerValue(payoff, mesher, 0);

        // 3. Step conditions
        final YieldTermStructure riskFree = process_.riskFreeRate().getValue();
        final FdmStepConditionComposite conditions =
                FdmStepConditionComposite.vanillaComposite(
                        dividendSchedule, arguments_.exercise, mesher,
                        calculator, riskFree.referenceDate(),
                        riskFree.dayCounter());

        // 4. Boundary conditions
        final FdmBoundaryConditionSet boundaries = new FdmBoundaryConditionSet();

        // 5. Solver
        final FdmSolverDesc solverDesc = new FdmSolverDesc(
                mesher, boundaries, conditions, calculator,
                maturity, tGrid_, dampingSteps_);

        final FdmBlackScholesSolver solver = new FdmBlackScholesSolver(
                process_, payoff.strike(), solverDesc, schemeDesc_,
                localVol_, illegalLocalVolOverwrite_, null);

        final double spot = process_.x0() + spotAdjustment;

        results_.value = solver.valueAt(spot);
        results_.delta = solver.deltaAt(spot);
        results_.gamma = solver.gammaAt(spot);
        results_.theta = solver.thetaAt(spot);

        results_.valuationDate = riskFree.referenceDate();
        results_.errorEstimate = Double.NaN;
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
