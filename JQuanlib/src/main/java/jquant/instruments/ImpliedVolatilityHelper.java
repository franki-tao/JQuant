package jquant.instruments;

import jquant.Handle;
import jquant.Instrument;
import jquant.PricingEngine;
import jquant.Quote;
import jquant.math.Function;
import jquant.math.solvers1d.Brent;
import jquant.processes.EulerDiscretization;
import jquant.processes.GeneralizedBlackScholesProcess;
import jquant.quotes.SimpleQuote;
import jquant.termstructures.YieldTermStructure;
import jquant.termstructures.volatility.equityfx.BlackConstantVol;
import jquant.termstructures.volatility.equityfx.BlackVolTermStructure;

import static jquant.math.CommonUtil.QL_REQUIRE;
import static jquant.math.MathUtils.QL_EPSILON;

/**
 * Helper for one-asset implied-volatility calculations.
 *
 * <p>Port of {@code ql/instruments/impliedvolatility.{hpp,cpp}}
 * ({@code QuantLib::detail::ImpliedVolatilityHelper}).
 *
 * <p>The passed engine must be linked to the passed quote (see, e.g.,
 * how an option sets up the engine arguments).
 */
public class ImpliedVolatilityHelper {

    /** Price error as a function of the flat volatility quote. */
    private static final class PriceError implements Function {
        private final PricingEngine engine_;
        private final SimpleQuote vol_;
        private final double targetValue_;
        private final Instrument.results results_;

        PriceError(PricingEngine engine, SimpleQuote vol, double targetValue) {
            engine_ = engine;
            vol_ = vol;
            targetValue_ = targetValue;
            final PricingEngine.results results = engine_.getResults();
            QL_REQUIRE(results instanceof Instrument.results,
                    "pricing engine does not supply needed results");
            results_ = (Instrument.results) results;
        }

        @Override
        public double value(double x) {
            vol_.setValue(x);
            engine_.calculate();
            return results_.value - targetValue_;
        }
    }

    public static double calculate(final Instrument instrument,
                                   final PricingEngine engine,
                                   final SimpleQuote volQuote,
                                   double targetValue,
                                   double accuracy,
                                   int maxEvaluations,
                                   double minVol,
                                   double maxVol) {
        instrument.setupArguments(engine.getArguments());
        engine.getArguments().validate();

        final PriceError f = new PriceError(engine, volQuote, targetValue);

        // close(f, 0) is tolerance squared when the other argument is
        // zero, about 1e-28, so a price that sits a few ulps above the
        // target does not count as a root. An American at exercise is
        // flat in volatility, and the finite-difference price at the
        // lower vol is that flat value plus a rounding error. The lower
        // vol is then a solution: no smaller price exists in the bracket.
        final double lowerError = f.value(minVol);
        final double scale = Math.abs(targetValue) > 1.0
                ? Math.abs(targetValue) : 1.0;
        final double bound = 1.0e4 * QL_EPSILON * scale;
        if (lowerError >= 0.0 && lowerError <= bound) {
            return minVol;
        }

        final Brent solver = new Brent();
        solver.setMaxEvaluations(maxEvaluations);
        final double guess = (minVol + maxVol) / 2.0;
        return solver.solve(f, accuracy, guess, minVol, maxVol);
    }

    /**
     * The returned process equals the passed one, except for the
     * volatility, which is flat and driven by the passed quote.
     */
    public static GeneralizedBlackScholesProcess clone(
            final GeneralizedBlackScholesProcess process,
            final SimpleQuote volQuote) {
        final Handle<Quote> stateVariable = process.stateVariable();
        final Handle<YieldTermStructure> dividendYield = process.dividendYield();
        final Handle<YieldTermStructure> riskFreeRate = process.riskFreeRate();

        final Handle<BlackVolTermStructure> blackVol = process.blackVolatility();
        final Handle<BlackVolTermStructure> volatility =
                new Handle<>(new BlackConstantVol(blackVol.currentLink().referenceDate(),
                        blackVol.currentLink().calendar(),
                        new Handle<Quote>(volQuote, true),
                        blackVol.currentLink().dayCounter()), true);

        return new GeneralizedBlackScholesProcess(stateVariable, dividendYield,
                riskFreeRate, volatility, new EulerDiscretization(), false);
    }
}
