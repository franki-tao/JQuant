package jquant.pricingengines.vanilla;

import jquant.EarlyExercise;
import jquant.EuropeanOption;
import jquant.Exercise;
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
import jquant.methods.finitedifferences.utilities.FdmEscrowedLogInnerValueCalculator;
import jquant.methods.finitedifferences.utilities.FdmInnerValueCalculator;
import jquant.methods.finitedifferences.utilities.FdmLogInnerValue;
import jquant.patterns.Observable;
import jquant.patterns.ObservableSettings;
import jquant.patterns.Observer;
import jquant.pricingengines.StrikedTypePayoff;
import jquant.processes.GeneralizedBlackScholesProcess;
import jquant.termstructures.YieldTermStructure;
import jquant.time.Date;
import jquant.time.TimeUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static jquant.math.CommonUtil.QL_FAIL;
import static jquant.math.CommonUtil.QL_REQUIRE;
import static jquant.math.MathUtils.NULL_REAL;

/**
 * Finite-differences Black-Scholes vanilla option engine.
 *
 * <p>Port of {@code ql/pricingengines/vanilla/fdblackscholesvanillaengine.{hpp,cpp}}.
 * The European/American non-quanto paths with cash dividends are implemented.
 * The cash dividend model defaults to {@code Spot} as in QuantLib.
 */
public class FdBlackScholesVanillaEngine implements PricingEngine {
    public enum CashDividendModel {
        Spot,
        Escrowed
    }

    private final GeneralizedBlackScholesProcess process_;
    private final List<Dividend> dividends_;
    private final int tGrid_, xGrid_, dampingSteps_;
    private final FdmSchemeDesc schemeDesc_;
    private final boolean localVol_;
    private final double illegalLocalVolOverwrite_;
    private final CashDividendModel cashDividendModel_;

    private final Option.arguments arguments_ = new Option.arguments();
    private final EuropeanOption.results results_ = new EuropeanOption.results();
    private final Set<Observer> observers_ = new HashSet<>();

    public FdBlackScholesVanillaEngine(GeneralizedBlackScholesProcess process) {
        this(process, Collections.emptyList(), 100, 100);
    }

    public FdBlackScholesVanillaEngine(GeneralizedBlackScholesProcess process,
                                       int tGrid) {
        this(process, Collections.emptyList(), tGrid, 100);
    }

    public FdBlackScholesVanillaEngine(GeneralizedBlackScholesProcess process,
                                       int tGrid, int xGrid) {
        this(process, Collections.emptyList(), tGrid, xGrid);
    }

    public FdBlackScholesVanillaEngine(GeneralizedBlackScholesProcess process,
                                       List<Dividend> dividends,
                                       int tGrid, int xGrid) {
        this(process, dividends, tGrid, xGrid, 0, FdmSchemeDesc.Douglas(),
                false, -NULL_REAL, CashDividendModel.Spot);
    }

    public FdBlackScholesVanillaEngine(GeneralizedBlackScholesProcess process,
                                       int tGrid, int xGrid, int dampingSteps) {
        this(process, tGrid, xGrid, dampingSteps, FdmSchemeDesc.Douglas());
    }

    public FdBlackScholesVanillaEngine(GeneralizedBlackScholesProcess process,
                                       int tGrid, int xGrid, int dampingSteps,
                                       FdmSchemeDesc schemeDesc) {
        this(process, tGrid, xGrid, dampingSteps, schemeDesc,
                false, -NULL_REAL);
    }

    public FdBlackScholesVanillaEngine(GeneralizedBlackScholesProcess process,
                                       int tGrid, int xGrid, int dampingSteps,
                                       FdmSchemeDesc schemeDesc,
                                       boolean localVol,
                                       double illegalLocalVolOverwrite) {
        this(process, Collections.emptyList(), tGrid, xGrid, dampingSteps,
                schemeDesc, localVol, illegalLocalVolOverwrite,
                CashDividendModel.Spot);
    }

    public FdBlackScholesVanillaEngine(GeneralizedBlackScholesProcess process,
                                       List<Dividend> dividends,
                                       int tGrid, int xGrid, int dampingSteps,
                                       FdmSchemeDesc schemeDesc,
                                       boolean localVol,
                                       double illegalLocalVolOverwrite,
                                       CashDividendModel cashDividendModel) {
        process_ = process;
        dividends_ = dividends;
        tGrid_ = tGrid;
        xGrid_ = xGrid;
        dampingSteps_ = dampingSteps;
        schemeDesc_ = schemeDesc;
        localVol_ = localVol;
        illegalLocalVolOverwrite_ = illegalLocalVolOverwrite;
        cashDividendModel_ = cashDividendModel;

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
        final YieldTermStructure riskFree = process_.riskFreeRate().getValue();
        final Date settlementDate = riskFree.referenceDate();

        // 0. Cash dividend model
        double spotAdjustment = 0.0;
        List<Dividend> dividendSchedule = Collections.emptyList();
        EscrowedDividendAdjustment escrowedDivAdj = null;

        switch (cashDividendModel_) {
          case Spot:
            dividendSchedule = dividends_;
            break;
          case Escrowed:
            if (arguments_.exercise.type() != Exercise.Type.European) {
                // add dividend dates as stopping times
                final List<Dividend> stoppingSchedule = new ArrayList<>();
                for (Dividend cf : dividends_) {
                    stoppingSchedule.add(new FixedDividend(0.0, cf.date()));
                }
                dividendSchedule = stoppingSchedule;
            }

            escrowedDivAdj = new EscrowedDividendAdjustment(
                    dividends_,
                    process_.riskFreeRate(),
                    process_.dividendYield(),
                    d -> process_.time(d),
                    maturity);

            spotAdjustment = escrowedDivAdj.dividendAdjustment(
                    process_.time(settlementDate));

            QL_REQUIRE(process_.x0() + spotAdjustment > 0.0,
                    "spot minus dividends becomes negative");
            break;
          default:
            QL_FAIL("unknown cash dividend model");
        }

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

        final FdmInnerValueCalculator earlyExerciseCalculator;
        switch (cashDividendModel_) {
          case Spot:
            earlyExerciseCalculator = calculator;
            break;
          case Escrowed:
            earlyExerciseCalculator = new FdmEscrowedLogInnerValueCalculator(
                    escrowedDivAdj, payoff, mesher, 0);
            break;
          default:
            QL_FAIL("unknown cash dividend model");
            earlyExerciseCalculator = calculator;
        }

        // 3. Step conditions
        final FdmStepConditionComposite conditions =
                FdmStepConditionComposite.vanillaComposite(
                        dividendSchedule, arguments_.exercise, mesher,
                        earlyExerciseCalculator, riskFree.referenceDate(),
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

        // The step condition enforces the exercise on grid nodes. The monotonic
        // cubic interpolant can still undershoot the payoff between nodes, so
        // floor only when the holder can exercise on the reference date and the
        // payoff is paid then. Greeks stay on the interpolant.
        final Exercise exercise = arguments_.exercise;
        boolean exercisable = false;
        switch (exercise.type()) {
          case American:
            exercisable = TimeUtils.leq(exercise.date(0), settlementDate)
                          && TimeUtils.leq(settlementDate, exercise.lastDate());
            break;
          case Bermudan:
            for (Date d : exercise.dates()) {
                if (TimeUtils.equals(d, settlementDate)) {
                    exercisable = true;
                    break;
                }
            }
            break;
          case European:
            break;
          default:
            QL_FAIL("exercise type is not supported");
        }
        if (exercisable && exercise instanceof EarlyExercise
                && ((EarlyExercise) exercise).payoffAtExpiry()) {
            exercisable = false;
        }
        if (exercisable) {
            final double exerciseValue = payoff.value(process_.x0());
            if (results_.value < exerciseValue)
                results_.value = exerciseValue;
        }

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
