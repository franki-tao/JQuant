package jquant.pricingengines.vanilla;

import jquant.EuropeanOption;
import jquant.Option;
import jquant.PricingEngine;
import jquant.cashflows.Dividend;
import jquant.methods.finitedifferences.solvers.FdmSchemeDesc;
import jquant.patterns.ObservableSettings;
import jquant.patterns.Observer;
import jquant.pricingengines.StrikedTypePayoff;
import jquant.processes.CoxIngersollRossProcess;
import jquant.processes.GeneralizedBlackScholesProcess;
import jquant.time.Date;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static jquant.math.CommonUtil.QL_FAIL;
import static jquant.math.CommonUtil.QL_REQUIRE;

/**
 * Finite-differences CIR vanilla option engine.
 *
 * <p>Port of {@code ql/pricingengines/vanilla/fdcirvanillaengine.{hpp,cpp}}.
 * The official early payoff check is ported; {@code FdmCIRSolver} and the
 * short-rate/equity meshers used after it are not ported.
 */
public class FdCIRVanillaEngine implements PricingEngine {
    private final GeneralizedBlackScholesProcess bsProcess_;
    private final CoxIngersollRossProcess cirProcess_;
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
                tGrid, xGrid, rGrid, dampingSteps, rho, schemeDesc);
    }

    public FdCIRVanillaEngine(final CoxIngersollRossProcess cirProcess,
                              final GeneralizedBlackScholesProcess bsProcess,
                              final List<Dividend> dividends,
                              int tGrid, int xGrid, int rGrid, int dampingSteps,
                              double rho,
                              final FdmSchemeDesc schemeDesc) {
        bsProcess_ = bsProcess;
        cirProcess_ = cirProcess;
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

    @Override
    public void calculate() {
        final StrikedTypePayoff payoff =
                (arguments_.payoff instanceof StrikedTypePayoff)
                        ? (StrikedTypePayoff) arguments_.payoff : null;
        QL_REQUIRE(payoff != null, "non-striked payoff given");

        // The official implementation builds an FdmCIRSolver from
        // getSolverDesc(1.5). FdmCIRSolver (and FdmSimpleProcess1dMesher)
        // are not ported here.
        QL_FAIL("FdmCIRSolver is not ported");
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
