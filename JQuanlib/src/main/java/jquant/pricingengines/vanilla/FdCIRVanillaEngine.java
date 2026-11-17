package jquant.pricingengines.vanilla;

import jquant.EuropeanOption;
import jquant.Option;
import jquant.PricingEngine;
import jquant.cashflows.Dividend;
import jquant.math.Point;
import jquant.methods.finitedifferences.meshers.FdmBlackScholesMesher;
import jquant.methods.finitedifferences.meshers.FdmMesherComposite;
import jquant.methods.finitedifferences.meshers.FdmSimpleProcess1dMesher;
import jquant.methods.finitedifferences.solvers.FdmCIRSolver;
import jquant.methods.finitedifferences.solvers.FdmSchemeDesc;
import jquant.methods.finitedifferences.solvers.FdmSolverDesc;
import jquant.methods.finitedifferences.stepconditions.FdmStepConditionComposite;
import jquant.methods.finitedifferences.utilities.FdmBoundaryConditionSet;
import jquant.methods.finitedifferences.utilities.FdmInnerValueCalculator;
import jquant.methods.finitedifferences.utilities.FdmLogInnerValue;
import jquant.patterns.ObservableSettings;
import jquant.patterns.Observer;
import jquant.pricingengines.StrikedTypePayoff;
import jquant.processes.CoxIngersollRossProcess;
import jquant.processes.GeneralizedBlackScholesProcess;
import jquant.time.Date;
import jquant.utilities.FdmQuantoHelper;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static jquant.math.CommonUtil.QL_REQUIRE;
import static jquant.math.MathUtils.NULL_REAL;

/**
 * Finite-differences CIR vanilla option engine.
 *
 * <p>Port of {@code ql/pricingengines/vanilla/fdcirvanillaengine.{hpp,cpp}}.
 */
public class FdCIRVanillaEngine implements PricingEngine {
    private final GeneralizedBlackScholesProcess bsProcess_;
    private final CoxIngersollRossProcess cirProcess_;
    private final FdmQuantoHelper quantoHelper_;
    private final List<Dividend> dividends_;
    private final int tGrid_, xGrid_, rGrid_, dampingSteps_;
    private final double rho_;
    private final FdmSchemeDesc schemeDesc_;

    private final Option.arguments arguments_ = new Option.arguments();
    private final EuropeanOption.results results_ = new EuropeanOption.results();
    private final Set<Observer> observers_ = new HashSet<>();

    public FdCIRVanillaEngine(final CoxIngersollRossProcess cirProcess,
                              final GeneralizedBlackScholesProcess bsProcess,
                              int tGrid, int xGrid, int rGrid, int dampingSteps,
                              double rho,
                              final FdmSchemeDesc schemeDesc) {
        this(cirProcess, bsProcess, Collections.emptyList(),
                tGrid, xGrid, rGrid, dampingSteps, rho, schemeDesc, null);
    }

    public FdCIRVanillaEngine(final CoxIngersollRossProcess cirProcess,
                              final GeneralizedBlackScholesProcess bsProcess,
                              int tGrid, int xGrid, int rGrid, int dampingSteps,
                              double rho,
                              final FdmSchemeDesc schemeDesc,
                              final FdmQuantoHelper quantoHelper) {
        this(cirProcess, bsProcess, Collections.emptyList(),
                tGrid, xGrid, rGrid, dampingSteps, rho, schemeDesc, quantoHelper);
    }

    public FdCIRVanillaEngine(final CoxIngersollRossProcess cirProcess,
                              final GeneralizedBlackScholesProcess bsProcess,
                              final List<Dividend> dividends,
                              int tGrid, int xGrid, int rGrid, int dampingSteps,
                              double rho,
                              final FdmSchemeDesc schemeDesc) {
        this(cirProcess, bsProcess, dividends,
                tGrid, xGrid, rGrid, dampingSteps, rho, schemeDesc, null);
    }

    public FdCIRVanillaEngine(final CoxIngersollRossProcess cirProcess,
                              final GeneralizedBlackScholesProcess bsProcess,
                              final List<Dividend> dividends,
                              int tGrid, int xGrid, int rGrid, int dampingSteps,
                              double rho,
                              final FdmSchemeDesc schemeDesc,
                              final FdmQuantoHelper quantoHelper) {
        bsProcess_ = bsProcess;
        cirProcess_ = cirProcess;
        quantoHelper_ = quantoHelper;
        dividends_ = dividends;
        tGrid_ = tGrid;
        xGrid_ = xGrid;
        rGrid_ = rGrid;
        dampingSteps_ = dampingSteps;
        rho_ = rho;
        schemeDesc_ = schemeDesc;

        results_.additionalResults = new HashMap<>();
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

    public FdmSolverDesc getSolverDesc(double equityScaleFactor) {
        final StrikedTypePayoff payoff =
                (arguments_.payoff instanceof StrikedTypePayoff)
                        ? (StrikedTypePayoff) arguments_.payoff : null;
        QL_REQUIRE(payoff != null, "non-striked payoff given");
        final double maturity = bsProcess_.time(arguments_.exercise.lastDate());

        // The short rate mesher
        final FdmSimpleProcess1dMesher shortRateMesher =
                new FdmSimpleProcess1dMesher(rGrid_, cirProcess_, maturity, tGrid_);

        // The equity mesher
        final FdmBlackScholesMesher equityMesher = new FdmBlackScholesMesher(
                xGrid_, bsProcess_, maturity, payoff.strike(),
                NULL_REAL, NULL_REAL, 0.0001, 1.5,
                new Point<>(payoff.strike(), 0.1),
                dividends_, quantoHelper_,
                0.0);

        final FdmMesherComposite mesher = new FdmMesherComposite(
                java.util.Arrays.asList(equityMesher, shortRateMesher));

        // Calculator
        final FdmInnerValueCalculator calculator =
                new FdmLogInnerValue(arguments_.payoff, mesher, 0);

        // Step conditions
        final FdmStepConditionComposite conditions =
                FdmStepConditionComposite.vanillaComposite(
                        dividends_, arguments_.exercise,
                        mesher, calculator,
                        bsProcess_.riskFreeRate().currentLink().referenceDate(),
                        bsProcess_.riskFreeRate().currentLink().dayCounter());

        // Boundary conditions
        final FdmBoundaryConditionSet boundaries = new FdmBoundaryConditionSet();

        // Solver
        return new FdmSolverDesc(mesher, boundaries, conditions,
                calculator, maturity, tGrid_, dampingSteps_);
    }

    @Override
    public void calculate() {
        final StrikedTypePayoff payoff =
                (arguments_.payoff instanceof StrikedTypePayoff)
                        ? (StrikedTypePayoff) arguments_.payoff : null;
        QL_REQUIRE(payoff != null, "non-striked payoff given");

        final FdmCIRSolver solver = new FdmCIRSolver(
                cirProcess_, bsProcess_, getSolverDesc(1.5), schemeDesc_,
                rho_, payoff.strike());

        final double r0 = cirProcess_.x0();
        final double spot = bsProcess_.x0();

        results_.value = solver.valueAt(spot, r0);
        results_.delta = solver.deltaAt(spot, r0);
        results_.gamma = solver.gammaAt(spot, r0);
        results_.theta = solver.thetaAt(spot, r0);
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
