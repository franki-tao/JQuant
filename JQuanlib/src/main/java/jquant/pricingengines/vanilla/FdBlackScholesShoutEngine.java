package jquant.pricingengines.vanilla;

import jquant.EuropeanOption;
import jquant.Handle;
import jquant.Option;
import jquant.PricingEngine;
import jquant.cashflows.Dividend;
import jquant.cashflows.FixedDividend;
import jquant.math.Point;
import jquant.methods.finitedifferences.meshers.Fdm1dMesher;
import jquant.methods.finitedifferences.meshers.FdmBlackScholesMesher;
import jquant.methods.finitedifferences.meshers.FdmMesher;
import jquant.methods.finitedifferences.meshers.FdmMesherComposite;
import jquant.methods.finitedifferences.solvers.FdmBlackScholesSolver;
import jquant.methods.finitedifferences.solvers.FdmSchemeDesc;
import jquant.methods.finitedifferences.solvers.FdmSolverDesc;
import jquant.methods.finitedifferences.stepconditions.FdmStepConditionComposite;
import jquant.methods.finitedifferences.utilities.EscrowedDividendAdjustment;
import jquant.methods.finitedifferences.utilities.FdmBoundaryConditionSet;
import jquant.methods.finitedifferences.utilities.FdmShoutLogInnerValueCalculator;
import jquant.patterns.Observable;
import jquant.patterns.ObservableSettings;
import jquant.patterns.Observer;
import jquant.pricingengines.PlainVanillaPayoff;
import jquant.processes.GeneralizedBlackScholesProcess;
import jquant.termstructures.YieldTermStructure;
import jquant.time.Date;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static jquant.math.CommonUtil.QL_REQUIRE;
import static jquant.math.MathUtils.NULL_REAL;

/**
 * Finite-differences Black-Scholes shout option engine.
 *
 * <p>Port of
 * {@code ql/pricingengines/vanilla/fdblackscholesshoutengine.{hpp,cpp}}.
 */
public class FdBlackScholesShoutEngine implements PricingEngine {
    private final GeneralizedBlackScholesProcess process_;
    private final List<Dividend> dividends_;
    private final int tGrid_, xGrid_, dampingSteps_;
    private final FdmSchemeDesc schemeDesc_;

    private final Option.arguments arguments_ = new Option.arguments();
    private final EuropeanOption.results results_ = new EuropeanOption.results();
    private final Set<Observer> observers_ = new HashSet<>();

    public FdBlackScholesShoutEngine(
            GeneralizedBlackScholesProcess process,
            int tGrid, int xGrid, int dampingSteps,
            FdmSchemeDesc schemeDesc) {
        this(process, Collections.emptyList(), tGrid, xGrid, dampingSteps,
                schemeDesc);
    }

    public FdBlackScholesShoutEngine(
            GeneralizedBlackScholesProcess process, int tGrid, int xGrid) {
        this(process, Collections.emptyList(), tGrid, xGrid, 0,
                FdmSchemeDesc.Douglas());
    }

    public FdBlackScholesShoutEngine(
            GeneralizedBlackScholesProcess process, int tGrid) {
        this(process, Collections.emptyList(), tGrid, 100, 0,
                FdmSchemeDesc.Douglas());
    }

    public FdBlackScholesShoutEngine(
            GeneralizedBlackScholesProcess process,
            List<Dividend> dividends,
            int tGrid, int xGrid) {
        this(process, dividends, tGrid, xGrid, 0, FdmSchemeDesc.Douglas());
    }

    public FdBlackScholesShoutEngine(
            GeneralizedBlackScholesProcess process,
            List<Dividend> dividends,
            int tGrid, int xGrid, int dampingSteps,
            FdmSchemeDesc schemeDesc) {
        process_ = process;
        dividends_ = dividends;
        tGrid_ = tGrid;
        xGrid_ = xGrid;
        dampingSteps_ = dampingSteps;
        schemeDesc_ = schemeDesc;

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
        final Date exerciseDate = arguments_.exercise.lastDate();
        final double maturity = process_.time(exerciseDate);
        final YieldTermStructure riskFree =
                process_.riskFreeRate().getValue();
        final Date settlementDate = riskFree.referenceDate();

        final EscrowedDividendAdjustment escrowedDividendAdj =
                new EscrowedDividendAdjustment(
                        dividends_,
                        process_.riskFreeRate(),
                        process_.dividendYield(),
                        process_::time,
                        maturity);

        final double divAdj = escrowedDividendAdj.dividendAdjustment(
                process_.time(settlementDate));

        QL_REQUIRE(process_.x0() + divAdj > 0.0,
                "spot minus dividends becomes negative");

        QL_REQUIRE(arguments_.payoff instanceof PlainVanillaPayoff,
                "non plain vanilla payoff given");
        final PlainVanillaPayoff payoff =
                (PlainVanillaPayoff) arguments_.payoff;

        final List<Dividend> emptyDividendSchedule = Collections.emptyList();

        final Fdm1dMesher equityMesher = new FdmBlackScholesMesher(
                xGrid_, process_, maturity, payoff.strike(),
                Double.NaN, Double.NaN, 0.0001, 1.5,
                new Point<>(payoff.strike(), 0.1),
                emptyDividendSchedule, null, divAdj);

        final FdmMesher mesher = new FdmMesherComposite(equityMesher);

        final FdmShoutLogInnerValueCalculator innerValuecalculator =
                new FdmShoutLogInnerValueCalculator(
                        process_.blackVolatility(),
                        escrowedDividendAdj, maturity, payoff, mesher, 0);

        final List<Dividend> zeroDividendSchedule = new ArrayList<>();
        for (Dividend cf : dividends_) {
            zeroDividendSchedule.add(new FixedDividend(0.0, cf.date()));
        }

        final FdmStepConditionComposite conditions =
                FdmStepConditionComposite.vanillaComposite(
                        zeroDividendSchedule,
                        arguments_.exercise, mesher,
                        innerValuecalculator,
                        riskFree.referenceDate(),
                        riskFree.dayCounter());

        final FdmSolverDesc solverDesc = new FdmSolverDesc(
                mesher, new FdmBoundaryConditionSet(),
                conditions, innerValuecalculator,
                maturity, tGrid_, dampingSteps_);

        final FdmBlackScholesSolver solver = new FdmBlackScholesSolver(
                process_, payoff.strike(), solverDesc, schemeDesc_,
                false, -NULL_REAL, null);

        final double spot = process_.x0() + divAdj;

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
