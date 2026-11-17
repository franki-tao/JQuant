package jquant;

import jquant.cashflows.Dividend;
import jquant.math.Function;
import jquant.math.solvers1d.Brent;
import jquant.pricingengines.vanilla.AnalyticDividendEuropeanEngine;
import jquant.pricingengines.vanilla.AnalyticEuropeanEngine;
import jquant.pricingengines.vanilla.FdBlackScholesVanillaEngine;
import jquant.processes.BlackScholesMertonProcess;
import jquant.processes.GeneralizedBlackScholesProcess;
import jquant.quotes.SimpleQuote;
import jquant.termstructures.volatility.equityfx.BlackConstantVol;
import jquant.termstructures.volatility.equityfx.BlackVolTermStructure;
import jquant.time.Date;
import jquant.time.TimeUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static jquant.math.CommonUtil.QL_FAIL;
import static jquant.math.CommonUtil.QL_REQUIRE;
import static jquant.math.MathUtils.QL_EPSILON;

/**
 * European option priced by a {@link PricingEngine}. Greeks are read from
 * {@link results} when the engine provides them.
 */
public class EuropeanOption extends Option {
    private double delta_ = Double.NaN;
    private double gamma_ = Double.NaN;
    private double theta_ = Double.NaN;
    private double thetaPerDay_ = Double.NaN;
    private double vega_ = Double.NaN;
    private double rho_ = Double.NaN;
    private double dividendRho_ = Double.NaN;
    private double elasticity_ = Double.NaN;
    private double strikeSensitivity_ = Double.NaN;

    public EuropeanOption(Payoff payoff, Exercise exercise) {
        super(payoff, exercise);
    }

    @Override
    public boolean isExpired() {
        Date today = Settings.instance.evaluationDate().Date();
        // QuantLib Event::hasOccurred with the default reference date.
        return Settings.instance.includeReferenceDateEvents()
                ? TimeUtils.less(exercise_.lastDate(), today)
                : TimeUtils.leq(exercise_.lastDate(), today);
    }

    public double delta() {
        calculate();
        QL_REQUIRE(!Double.isNaN(delta_), "delta not provided");
        return delta_;
    }

    public double gamma() {
        calculate();
        QL_REQUIRE(!Double.isNaN(gamma_), "gamma not provided");
        return gamma_;
    }

    public double theta() {
        calculate();
        QL_REQUIRE(!Double.isNaN(theta_), "theta not provided");
        return theta_;
    }

    public double thetaPerDay() {
        calculate();
        QL_REQUIRE(!Double.isNaN(thetaPerDay_), "thetaPerDay not provided");
        return thetaPerDay_;
    }

    public double vega() {
        calculate();
        QL_REQUIRE(!Double.isNaN(vega_), "vega not provided");
        return vega_;
    }

    public double rho() {
        calculate();
        QL_REQUIRE(!Double.isNaN(rho_), "rho not provided");
        return rho_;
    }

    public double dividendRho() {
        calculate();
        QL_REQUIRE(!Double.isNaN(dividendRho_), "dividendRho not provided");
        return dividendRho_;
    }

    public double elasticity() {
        calculate();
        QL_REQUIRE(!Double.isNaN(elasticity_), "elasticity not provided");
        return elasticity_;
    }

    public double strikeSensitivity() {
        calculate();
        QL_REQUIRE(!Double.isNaN(strikeSensitivity_), "strikeSensitivity not provided");
        return strikeSensitivity_;
    }

    /**
     * Black volatility that reprices {@code targetValue} with a cloned process.
     * Accuracy, evaluation limit and the volatility bracket follow
     * {@code VanillaOption::impliedVolatility}: {@code 1e-4}, {@code 100},
     * {@code 1e-7} and {@code 4}.
     */
    public double impliedVolatility(double targetValue, AnalyticEuropeanEngine engine) {
        return impliedVolatility(targetValue, engine, 1.0e-4, 100, 1.0e-7, 4.0);
    }

    /**
     * Black volatility that reprices {@code targetValue}, using the
     * QuantLib {@code VanillaOption::impliedVolatility} default accuracy,
     * evaluation limit and bracket ({@code 1e-4}, {@code 100},
     * {@code 1e-7}, {@code 4}).
     */
    public double impliedVolatility(double targetValue, GeneralizedBlackScholesProcess process) {
        return impliedVolatility(targetValue, process, 1.0e-4, 100, 1.0e-7, 4.0);
    }

    /**
     * Black volatility that reprices {@code targetValue}, cloning
     * {@code process} exactly as {@code ImpliedVolatilityHelper::clone}
     * does in {@code ql/instruments/impliedvolatility.cpp}.
     */
    public double impliedVolatility(double targetValue,
                                    GeneralizedBlackScholesProcess process,
                                    double accuracy, int maxEvaluations) {
        return impliedVolatility(targetValue, process, accuracy, maxEvaluations,
                1.0e-7, 4.0);
    }

    /**
     * Solves {@code cloned-process price - targetValue = 0} with Brent.
     *
     * <p>The spot, dividend and risk-free handles of {@code process} are
     * shared. Volatility is a new constant curve that copies the original
     * reference date, calendar and day counter and is driven by a temporary
     * quote. The quote passed into the original curve is never written, so
     * the original process is not notified and a failed solve leaves the
     * market where it was.
     */
    public double impliedVolatility(double targetValue,
                                    GeneralizedBlackScholesProcess process,
                                    double accuracy, int maxEvaluations,
                                    double minVol, double maxVol) {
        // VanillaOption::impliedVolatility delegates to the dividend-schedule
        // overload with an empty schedule.
        return impliedVolatility(targetValue, process, Collections.emptyList(),
                accuracy, maxEvaluations, minVol, maxVol);
    }

    /**
     * Black volatility that reprices {@code targetValue}, with QuantLib
     * defaults for accuracy, evaluation limit and bracket.
     */
    public double impliedVolatility(double targetValue,
                                    GeneralizedBlackScholesProcess process,
                                    List<? extends Dividend> dividends) {
        return impliedVolatility(targetValue, process, dividends,
                1.0e-4, 100, 1.0e-7, 4.0);
    }

    public double impliedVolatility(double targetValue,
                                    GeneralizedBlackScholesProcess process,
                                    List<? extends Dividend> dividends,
                                    double accuracy, int maxEvaluations) {
        return impliedVolatility(targetValue, process, dividends,
                accuracy, maxEvaluations, 1.0e-7, 4.0);
    }

    /**
     * QuantLib {@code VanillaOption::impliedVolatility} with a discrete
     * dividend schedule. European options use {@link AnalyticEuropeanEngine}
     * for an empty schedule and {@link AnalyticDividendEuropeanEngine}
     * otherwise; American and Bermudan exercise use
     * {@link FdBlackScholesVanillaEngine}, as in the official implementation.
     */
    public double impliedVolatility(double targetValue,
                                    GeneralizedBlackScholesProcess process,
                                    List<? extends Dividend> dividends,
                                    double accuracy, int maxEvaluations,
                                    double minVol, double maxVol) {
        QL_REQUIRE(process != null, "no process given");
        QL_REQUIRE(!isExpired(), "option expired");
        QL_REQUIRE(minVol >= 0.0, "minimum volatility must be non-negative");
        QL_REQUIRE(maxEvaluations > 0, "maximum number of evaluations must be positive");
        SimpleQuote volQuote = new SimpleQuote((minVol + maxVol) / 2.0);
        GeneralizedBlackScholesProcess newProcess =
                cloneWithFlatVolatility(process, volQuote);

        PricingEngine solverEngine;
        switch (exercise_.type()) {
            case European:
                solverEngine = (dividends == null || dividends.isEmpty())
                        ? new AnalyticEuropeanEngine(newProcess)
                        : new AnalyticDividendEuropeanEngine(newProcess, dividends);
                break;
            case American:
            case Bermudan:
                List<Dividend> schedule = (dividends == null)
                        ? Collections.emptyList()
                        : new ArrayList<>(dividends);
                solverEngine = new FdBlackScholesVanillaEngine(
                        newProcess, schedule, 100, 100);
                break;
            default:
                QL_FAIL("unknown exercise type");
                return Double.NaN;
        }
        return solveImpliedVolatility(targetValue, solverEngine, volQuote,
                accuracy, maxEvaluations, minVol, maxVol);
    }

    /**
     * Solves {@code engine price - targetValue = 0} with Brent.
     *
     * <p>The spot, dividend and risk-free handles of {@code engine} are shared.
     * Volatility is a new constant curve that copies the original reference
     * date, calendar and day counter and is driven by a temporary quote. The
     * quote passed into the original curve is never written, so a failed solve
     * leaves the market where it was. The temporary engine is the one that
     * prices each trial volatility.
     */
    public double impliedVolatility(double targetValue, AnalyticEuropeanEngine engine,
                                    double accuracy, int maxEvaluations,
                                    double minVol, double maxVol) {
        QL_REQUIRE(engine != null, "no pricing engine given");
        QL_REQUIRE(!isExpired(), "option expired");
        QL_REQUIRE(minVol >= 0.0, "minimum volatility must be non-negative");
        QL_REQUIRE(maxEvaluations > 0, "maximum number of evaluations must be positive");
        GeneralizedBlackScholesProcess process = engine.process();
        QL_REQUIRE(process != null, "no process given");
        SimpleQuote volQuote = new SimpleQuote((minVol + maxVol) / 2.0);
        AnalyticEuropeanEngine solverEngine = new AnalyticEuropeanEngine(
                cloneWithFlatVolatility(process, volQuote));
        return solveImpliedVolatility(targetValue, solverEngine, volQuote,
                accuracy, maxEvaluations, minVol, maxVol);
    }

    private double solveImpliedVolatility(double targetValue,
                                          PricingEngine solverEngine,
                                          SimpleQuote volQuote,
                                          double accuracy, int maxEvaluations,
                                          double minVol, double maxVol) {
        setupArguments(solverEngine.getArguments());
        solverEngine.getArguments().validate();

        Function priceError = x -> {
            volQuote.setValue(x);
            solverEngine.calculate();
            return ((Instrument.results) solverEngine.getResults()).value - targetValue;
        };

        // A price a few ulps above the lower-vol price is already at the
        // bracket floor: no smaller price exists. A price below that floor
        // is left for Brent, which reports that the root is not bracketed.
        double lowerError = priceError.value(minVol);
        double scale = Math.abs(targetValue) > 1.0 ? Math.abs(targetValue) : 1.0;
        double bound = 1.0e4 * QL_EPSILON * scale;
        if (lowerError >= 0.0 && lowerError <= bound)
            return minVol;

        Brent solver = new Brent();
        solver.setMaxEvaluations(maxEvaluations);
        return solver.solve(priceError, accuracy, (minVol + maxVol) / 2.0, minVol, maxVol);
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

    @Override
    public void fetchResults(PricingEngine.results r) {
        super.fetchResults(r);
        if (r instanceof results rr) {
            delta_ = rr.delta;
            gamma_ = rr.gamma;
            theta_ = rr.theta;
            thetaPerDay_ = rr.thetaPerDay;
            vega_ = rr.vega;
            rho_ = rr.rho;
            dividendRho_ = rr.dividendRho;
            elasticity_ = rr.elasticity;
            strikeSensitivity_ = rr.strikeSensitivity;
        }
    }

    public static class results extends Instrument.results {
        public double delta = Double.NaN;
        public double gamma = Double.NaN;
        public double theta = Double.NaN;
        public double thetaPerDay = Double.NaN;
        public double vega = Double.NaN;
        public double rho = Double.NaN;
        public double dividendRho = Double.NaN;
        public double elasticity = Double.NaN;
        public double strikeSensitivity = Double.NaN;
    }
}
