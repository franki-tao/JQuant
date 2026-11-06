package jquant;

import jquant.math.Function;
import jquant.math.solvers1d.Brent;
import jquant.pricingengines.vanilla.AnalyticEuropeanEngine;
import jquant.processes.BlackScholesMertonProcess;
import jquant.processes.GeneralizedBlackScholesProcess;
import jquant.quotes.SimpleQuote;
import jquant.termstructures.volatility.equityfx.BlackConstantVol;
import jquant.termstructures.volatility.equityfx.BlackVolTermStructure;
import jquant.time.Date;
import jquant.time.TimeUtils;

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

    public EuropeanOption(Payoff payoff, Exercise exercise) {
        super(payoff, exercise);
    }

    @Override
    public boolean isExpired() {
        Date today = Settings.instance.evaluationDate().Date();
        return TimeUtils.less(exercise_.lastDate(), today);
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
        BlackVolTermStructure blackVol = process.blackVolatility().getValue();
        SimpleQuote volQuote = new SimpleQuote((minVol + maxVol) / 2.0);
        BlackConstantVol flatVol = new BlackConstantVol(
                blackVol.referenceDate(),
                blackVol.calendar(),
                new Handle<>(volQuote, true),
                blackVol.dayCounter());
        AnalyticEuropeanEngine solverEngine = new AnalyticEuropeanEngine(
                new BlackScholesMertonProcess(
                        process.stateVariable(),
                        process.dividendYield(),
                        process.riskFreeRate(),
                        new Handle<>(flatVol, true)));
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
    }
}
