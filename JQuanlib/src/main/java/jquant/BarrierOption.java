package jquant;

import jquant.cashflows.Dividend;
import jquant.math.Function;
import jquant.math.solvers1d.Brent;
import jquant.pricingengines.GenericEngine;
import jquant.pricingengines.barrier.AnalyticBarrierEngine;
import jquant.processes.BlackScholesMertonProcess;
import jquant.processes.GeneralizedBlackScholesProcess;
import jquant.quotes.SimpleQuote;
import jquant.termstructures.volatility.equityfx.BlackConstantVol;
import jquant.termstructures.volatility.equityfx.BlackVolTermStructure;

import java.util.Collections;
import java.util.List;

import static jquant.math.CommonUtil.QL_FAIL;
import static jquant.math.CommonUtil.QL_REQUIRE;
import static jquant.math.MathUtils.QL_EPSILON;

/**
 * Barrier option on a single asset.
 *
 * <p>Ported from QuantLib {@code ql/instruments/barrieroption.{hpp,cpp}}.
 */
public class BarrierOption extends OneAssetOption {

    /** Extra arguments for a barrier option calculation. */
    public static class arguments extends Option.arguments {
        public Barrier.Type barrierType = null;
        public Double barrier = Double.NaN;
        public Double rebate = Double.NaN;

        @Override
        public void validate() {
            super.validate();

            if (barrierType == null) {
                QL_FAIL("unknown type");
            }
            switch (barrierType) {
                case DownIn:
                case UpIn:
                case DownOut:
                case UpOut:
                    break;
                default:
                    QL_FAIL("unknown type");
            }

            QL_REQUIRE(!Double.isNaN(barrier), "no barrier given");
            QL_REQUIRE(!Double.isNaN(rebate), "no rebate given");
        }
    }

    /** Base class for barrier-option engines. */
    public abstract static class engine
            extends GenericEngine<BarrierOption.arguments,
            OneAssetOption.results> {
        protected engine() {
            super(new BarrierOption.arguments(), new OneAssetOption.results());
        }

        /**
         * Official strict comparison: a down barrier is triggered when the
         * underlying is below it, an up barrier when above it. Equality is
         * not triggered.
         */
        protected boolean triggered(double underlying) {
            switch (arguments_.barrierType) {
                case DownIn:
                case DownOut:
                    return underlying < arguments_.barrier;
                case UpIn:
                case UpOut:
                    return underlying > arguments_.barrier;
                default:
                    QL_FAIL("unknown type");
                    return false;
            }
        }
    }

    protected Barrier.Type barrierType_;
    protected double barrier_;
    protected double rebate_;

    public BarrierOption(Barrier.Type barrierType,
                         double barrier,
                         double rebate,
                         Payoff payoff,
                         Exercise exercise) {
        super(payoff, exercise);
        barrierType_ = barrierType;
        barrier_ = barrier;
        rebate_ = rebate;
    }

    @Override
    public void setupArguments(PricingEngine.arguments args) {
        super.setupArguments(args);

        if (!(args instanceof BarrierOption.arguments moreArgs)) {
            QL_FAIL("wrong argument type");
            return;
        }
        moreArgs.barrierType = barrierType_;
        moreArgs.barrier = barrier_;
        moreArgs.rebate = rebate_;
    }

    /**
     * Black volatility that reprices {@code targetValue}; the no-dividend
     * overload delegates to the dividend-schedule one with an empty schedule,
     * as in the official implementation.
     */
    public double impliedVolatility(
            double targetValue,
            GeneralizedBlackScholesProcess process) {
        return impliedVolatility(targetValue, process, 1.0e-4, 100,
                1.0e-7, 4.0);
    }

    public double impliedVolatility(
            double targetValue,
            GeneralizedBlackScholesProcess process,
            double accuracy) {
        return impliedVolatility(targetValue, process, accuracy, 100,
                1.0e-7, 4.0);
    }

    public double impliedVolatility(
            double targetValue,
            GeneralizedBlackScholesProcess process,
            double accuracy,
            int maxEvaluations) {
        return impliedVolatility(targetValue, process, accuracy,
                maxEvaluations, 1.0e-7, 4.0);
    }

    /** QuantLib {@code BarrierOption::impliedVolatility} without dividends. */
    public double impliedVolatility(
            double targetValue,
            GeneralizedBlackScholesProcess process,
            double accuracy,
            int maxEvaluations,
            double minVol,
            double maxVol) {
        return impliedVolatility(targetValue, process,
                Collections.emptyList(), accuracy, maxEvaluations,
                minVol, maxVol);
    }

    public double impliedVolatility(
            double targetValue,
            GeneralizedBlackScholesProcess process,
            List<? extends Dividend> dividends) {
        return impliedVolatility(targetValue, process, dividends,
                1.0e-4, 100, 1.0e-7, 4.0);
    }

    public double impliedVolatility(
            double targetValue,
            GeneralizedBlackScholesProcess process,
            List<? extends Dividend> dividends,
            double accuracy,
            int maxEvaluations) {
        return impliedVolatility(targetValue, process, dividends,
                accuracy, maxEvaluations, 1.0e-7, 4.0);
    }

    /**
     * QuantLib {@code BarrierOption::impliedVolatility} with a discrete
     * dividend schedule. European options with an empty schedule use
     * {@link AnalyticBarrierEngine}; the dividend path and non-European
     * exercise are not implemented, as in the official engine selection.
     */
    public double impliedVolatility(
            double targetValue,
            GeneralizedBlackScholesProcess process,
            List<? extends Dividend> dividends,
            double accuracy,
            int maxEvaluations,
            double minVol,
            double maxVol) {
        QL_REQUIRE(process != null, "no process given");
        QL_REQUIRE(!isExpired(), "option expired");

        SimpleQuote volQuote = new SimpleQuote((minVol + maxVol) / 2.0);
        GeneralizedBlackScholesProcess newProcess =
                cloneWithFlatVolatility(process, volQuote);

        // Engines are built-in for the time being.
        PricingEngine solverEngine;
        switch (exercise_.type()) {
            case European:
                if (dividends == null || dividends.isEmpty()) {
                    solverEngine = new AnalyticBarrierEngine(newProcess);
                } else {
                    QL_FAIL("implied-volatility calculation for barrier "
                            + "options with dividends is not implemented yet");
                    return Double.NaN;
                }
                break;
            case American:
            case Bermudan:
                QL_FAIL("engine not available for non-European barrier option");
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
