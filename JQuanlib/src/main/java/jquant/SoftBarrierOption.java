package jquant;

import jquant.math.Function;
import jquant.math.solvers1d.Brent;
import jquant.pricingengines.GenericEngine;
import jquant.pricingengines.barrier.AnalyticSoftBarrierEngine;
import jquant.processes.BlackScholesMertonProcess;
import jquant.processes.GeneralizedBlackScholesProcess;
import jquant.quotes.SimpleQuote;
import jquant.termstructures.volatility.equityfx.BlackConstantVol;
import jquant.termstructures.volatility.equityfx.BlackVolTermStructure;

import static jquant.math.CommonUtil.QL_FAIL;
import static jquant.math.CommonUtil.QL_REQUIRE;
import static jquant.math.MathUtils.QL_EPSILON;

/**
 * Soft barrier option on a single asset.
 *
 * <p>A soft barrier option gets knocked in/out proportionally over the
 * barrier range instead of being knocked in/out in full at a hard barrier.
 * It is currently only available with European payoff style.
 *
 * <p>Ported from QuantLib {@code ql/instruments/softbarrieroption.{hpp,cpp}}.
 */
public class SoftBarrierOption extends OneAssetOption {

    /** Extra arguments for a soft-barrier option calculation. */
    public static class arguments extends Option.arguments {
        public Barrier.Type barrierType = null;
        public double barrier_lo = Double.NaN;
        public double barrier_hi = Double.NaN;
        // Official SoftBarrierOption::arguments has no validate() override:
        // the checks live in the engine's validateInputs.
    }

    /** Base class for soft-barrier-option engines. */
    public abstract static class engine
            extends GenericEngine<SoftBarrierOption.arguments,
            OneAssetOption.results> {
        protected engine() {
            super(new SoftBarrierOption.arguments(),
                    new OneAssetOption.results());
        }
    }

    protected Barrier.Type barrierType_;
    protected double barrier_lo_;
    protected double barrier_hi_;

    public SoftBarrierOption(Barrier.Type barrierType,
                             double barrier_lo,
                             double barrier_hi,
                             Payoff payoff,
                             Exercise exercise) {
        super(payoff, exercise);
        barrierType_ = barrierType;
        barrier_lo_ = barrier_lo;
        barrier_hi_ = barrier_hi;
    }

    @Override
    public void setupArguments(PricingEngine.arguments args) {
        super.setupArguments(args);

        if (!(args instanceof SoftBarrierOption.arguments moreArgs)) {
            QL_FAIL("wrong argument type");
            return;
        }
        moreArgs.barrierType = barrierType_;
        moreArgs.barrier_lo = barrier_lo_;
        moreArgs.barrier_hi = barrier_hi_;
    }

    public double impliedVolatility(
            double targetValue,
            GeneralizedBlackScholesProcess process) {
        return impliedVolatility(targetValue, process, 1.0e-4, 100,
                1.0e-6, 4.0);
    }

    public double impliedVolatility(
            double targetValue,
            GeneralizedBlackScholesProcess process,
            double accuracy) {
        return impliedVolatility(targetValue, process, accuracy, 100,
                1.0e-6, 4.0);
    }

    public double impliedVolatility(
            double targetValue,
            GeneralizedBlackScholesProcess process,
            double accuracy,
            int maxEvaluations) {
        return impliedVolatility(targetValue, process, accuracy,
                maxEvaluations, 1.0e-6, 4.0);
    }

    /**
     * QuantLib {@code SoftBarrierOption::impliedVolatility}. European options
     * use {@link AnalyticSoftBarrierEngine}; non-European exercise fails, as
     * in the official engine selection.
     *
     * <p>{@code minVol} defaults to {@code 1e-6} in the official interface:
     * zero vol values can make the soft barrier formula produce NaN results.
     */
    public double impliedVolatility(
            double targetValue,
            GeneralizedBlackScholesProcess process,
            double accuracy,
            int maxEvaluations,
            double minVol,
            double maxVol) {
        QL_REQUIRE(!isExpired(), "option expired");

        SimpleQuote volQuote = new SimpleQuote((minVol + maxVol) / 2.0);

        GeneralizedBlackScholesProcess newProcess =
                cloneWithFlatVolatility(process, volQuote);

        // Engines are built-in for the time being.
        PricingEngine solverEngine;
        switch (exercise_.type()) {
            case European:
                solverEngine = new AnalyticSoftBarrierEngine(newProcess);
                break;
            case American:
            case Bermudan:
                QL_FAIL("engine not available for non-European soft barrier option");
                return Double.NaN;
            default:
                QL_FAIL("unknown exercise type");
                return Double.NaN;
        }

        return solveImpliedVolatility(targetValue, solverEngine, volQuote,
                accuracy, maxEvaluations, minVol, maxVol);
    }

    private double solveImpliedVolatility(double targetValue,
                                          PricingEngine solverEngine,
                                          SimpleQuote volQuote,
                                          double accuracy,
                                          int maxEvaluations,
                                          double minVol,
                                          double maxVol) {
        setupArguments(solverEngine.getArguments());
        solverEngine.getArguments().validate();

        Function priceError = x -> {
            volQuote.setValue(x);
            solverEngine.calculate();
            return ((Instrument.results) solverEngine.getResults()).value
                    - targetValue;
        };

        // A price a few ulps above the lower-vol price is already at the
        // bracket floor: no smaller price exists. A price below that floor
        // is left for Brent, which reports that the root is not bracketed.
        double lowerError = priceError.value(minVol);
        double scale = Math.abs(targetValue) > 1.0
                ? Math.abs(targetValue) : 1.0;
        double bound = 1.0e4 * QL_EPSILON * scale;
        if (lowerError >= 0.0 && lowerError <= bound) {
            return minVol;
        }

        Brent solver = new Brent();
        solver.setMaxEvaluations(maxEvaluations);
        return solver.solve(priceError, accuracy, (minVol + maxVol) / 2.0,
                minVol, maxVol);
    }

    /**
     * {@code ImpliedVolatilityHelper::clone}: same state variable, dividend
     * and risk-free curves, with a flat Black volatility driven by
     * {@code volQuote}.
     */
    private static GeneralizedBlackScholesProcess cloneWithFlatVolatility(
            GeneralizedBlackScholesProcess process, SimpleQuote volQuote) {
        BlackVolTermStructure blackVol = process.blackVolatility().getValue();
        BlackConstantVol flatVol = new BlackConstantVol(
                blackVol.referenceDate(),
                blackVol.calendar(),
                new Handle<>(volQuote, true),
                blackVol.dayCounter());
        return new BlackScholesMertonProcess(
                process.stateVariable(),
                process.dividendYield(),
                process.riskFreeRate(),
                new Handle<>(flatVol, true));
    }
}
