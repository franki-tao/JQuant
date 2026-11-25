package jquant.service.api.option;

import jquant.AmericanExercise;
import jquant.Compounding;
import jquant.EuropeanExercise;
import jquant.EuropeanOption;
import jquant.Exercise;
import jquant.Handle;
import jquant.Option;
import jquant.PricingEngine;
import jquant.Settings;
import jquant.pricingengines.PlainVanillaPayoff;
import jquant.pricingengines.vanilla.AnalyticEuropeanEngine;
import jquant.pricingengines.vanilla.BinomialVanillaEngine;
import jquant.pricingengines.vanilla.FdBlackScholesVanillaEngine;
import jquant.processes.BlackScholesMertonProcess;
import jquant.quotes.SimpleQuote;
import jquant.service.api.option.dto.VanillaOptionRequest;
import jquant.service.api.option.dto.VanillaOptionResponse;
import jquant.service.common.QuantTypeMapper;
import jquant.service.execution.QuantExecutionGuard;
import jquant.termstructures.YieldTermStructure;
import jquant.termstructures.volatility.equityfx.BlackConstantVol;
import jquant.termstructures.volatility.equityfx.BlackVolTermStructure;
import jquant.termstructures.yield.FlatForward;
import jquant.time.Date;
import jquant.time.DayCounter;
import jquant.time.Frequency;
import jquant.time.calendars.NullCalendar;
import jquant.time.daycounters.Actual365Fixed;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.DoubleSupplier;

@Service
public class OptionPricingService {

    private final QuantExecutionGuard executionGuard;

    public OptionPricingService(QuantExecutionGuard executionGuard) {
        this.executionGuard = executionGuard;
    }

    public VanillaOptionResponse compareVanillaEngines(VanillaOptionRequest req) {
        if (!req.maturityDate().isAfter(req.evaluationDate())) {
            throw new IllegalArgumentException("maturityDate must be strictly after evaluationDate");
        }
        return executionGuard.execute(() -> computeComparison(req));
    }

    public List<Map<String, Object>> presets() {
        return List.of(
                Map.of(
                        "id", "eur-atm-call",
                        "name", "European ATM Call (1Y, 22% Vol)",
                        "description", "Compares Analytic BS, CRR, Leisen-Reimer, Joshi, and Finite Differences",
                        "request", new VanillaOptionRequest(
                                "CALL", "EUROPEAN",
                                LocalDate.of(2025, 1, 15), LocalDate.of(2026, 1, 15),
                                100.0, 100.0, 0.045, 0.015, 0.22,
                                200, 100, 100)
                ),
                Map.of(
                        "id", "amr-itm-put",
                        "name", "American Put (Early Exercise Premium)",
                        "description", "High-rate American Put showcasing lattice and FDM engines",
                        "request", new VanillaOptionRequest(
                                "PUT", "AMERICAN",
                                LocalDate.of(2025, 1, 15), LocalDate.of(2026, 1, 15),
                                95.0, 105.0, 0.06, 0.00, 0.28,
                                250, 120, 120)
                ),
                Map.of(
                        "id", "eur-otm-put",
                        "name", "European OTM Put (6M, 35% Vol)",
                        "description", "Tail-risk put option with higher volatility",
                        "request", new VanillaOptionRequest(
                                "PUT", "EUROPEAN",
                                LocalDate.of(2025, 1, 15), LocalDate.of(2025, 7, 15),
                                100.0, 88.0, 0.045, 0.01, 0.35,
                                200, 100, 100)
                )
        );
    }

    private VanillaOptionResponse computeComparison(VanillaOptionRequest req) {
        long totalStart = System.nanoTime();

        Option.Type optionType = parseOptionType(req.optionType());
        boolean isAmerican = parseIsAmerican(req.exerciseStyle());
        Date evalDate = QuantTypeMapper.toQuantDate(req.evaluationDate());
        Date maturityDate = QuantTypeMapper.toQuantDate(req.maturityDate());

        Settings.instance.evaluationDate().equal(evalDate);

        DayCounter dc = new Actual365Fixed();
        double ttm = dc.yearFraction(evalDate, maturityDate, new Date(), new Date());

        SimpleQuote spotQuote = new SimpleQuote(req.spot());
        SimpleQuote qQuote = new SimpleQuote(req.dividendYield());
        SimpleQuote rQuote = new SimpleQuote(req.riskFreeRate());
        SimpleQuote volQuote = new SimpleQuote(req.volatility());

        YieldTermStructure qTS = new FlatForward(
                evalDate, new Handle<>(qQuote, true), dc, Compounding.Continuous, Frequency.ANNUAL);
        YieldTermStructure rTS = new FlatForward(
                evalDate, new Handle<>(rQuote, true), dc, Compounding.Continuous, Frequency.ANNUAL);
        BlackVolTermStructure volTS = new BlackConstantVol(
                evalDate, new NullCalendar(), new Handle<>(volQuote, true), dc);

        BlackScholesMertonProcess process = new BlackScholesMertonProcess(
                new Handle<>(spotQuote, true),
                new Handle<>(qTS, true),
                new Handle<>(rTS, true),
                new Handle<>(volTS, true));

        PlainVanillaPayoff payoff = new PlainVanillaPayoff(optionType, req.strike());
        Exercise exercise = isAmerican
                ? new AmericanExercise(evalDate, maturityDate, false)
                : new EuropeanExercise(maturityDate);

        int steps = req.binomialSteps() != null ? req.binomialSteps() : 200;
        int tGrid = req.fdmTimeGrid() != null ? req.fdmTimeGrid() : 100;
        int xGrid = req.fdmSpaceGrid() != null ? req.fdmSpaceGrid() : 100;

        record EngineSpec(String name, String category, PricingEngine engine) {
        }

        List<EngineSpec> specs = new ArrayList<>();
        if (!isAmerican) {
            specs.add(new EngineSpec("Analytic Black-Scholes", "Analytic", new AnalyticEuropeanEngine(process)));
        }
        specs.add(new EngineSpec("Binomial CRR (" + steps + " steps)", "Lattice",
                new BinomialVanillaEngine(process, steps, BinomialVanillaEngine.TreeType.COX_ROSS_RUBINSTEIN)));
        specs.add(new EngineSpec("Binomial Leisen-Reimer (" + steps + " steps)", "Lattice",
                new BinomialVanillaEngine(process, steps, BinomialVanillaEngine.TreeType.LEISEN_REIMER)));
        specs.add(new EngineSpec("Binomial Joshi (" + steps + " steps)", "Lattice",
                new BinomialVanillaEngine(process, steps, BinomialVanillaEngine.TreeType.JOSHI)));
        specs.add(new EngineSpec("FDM Black-Scholes (" + tGrid + "x" + xGrid + ")", "Finite Differences",
                new FdBlackScholesVanillaEngine(process, tGrid, xGrid)));

        List<VanillaOptionResponse.EngineResult> rawResults = new ArrayList<>();
        for (EngineSpec spec : specs) {
            EuropeanOption option = new EuropeanOption(payoff, exercise);
            option.setPricingEngine(spec.engine());
            long t0 = System.nanoTime();
            double npv = option.NPV();
            Double delta = safeGreek(option::delta);
            Double gamma = safeGreek(option::gamma);
            Double vega = safeGreek(option::vega);
            Double theta = safeGreek(option::theta);
            Double rho = safeGreek(option::rho);
            double elapsedMs = (System.nanoTime() - t0) / 1_000_000.0;

            rawResults.add(new VanillaOptionResponse.EngineResult(
                    spec.name(), spec.category(), npv, 0.0, delta, gamma, vega, theta, rho, elapsedMs));
        }

        // Benchmark: Analytic for European, FDM for American
        VanillaOptionResponse.EngineResult benchmark = isAmerican
                ? rawResults.get(rawResults.size() - 1)
                : rawResults.get(0);
        double benchmarkNpv = benchmark.npv();

        List<VanillaOptionResponse.EngineResult> results = rawResults.stream()
                .map(r -> new VanillaOptionResponse.EngineResult(
                        r.engineName(),
                        r.category(),
                        r.npv(),
                        r.npv() - benchmarkNpv,
                        r.delta(),
                        r.gamma(),
                        r.vega(),
                        r.theta(),
                        r.rho(),
                        r.elapsedMs()))
                .toList();

        // Spot profile sweep (21 points from 0.65 * strike to 1.35 * strike)
        PricingEngine profileEngine = isAmerican
                ? new FdBlackScholesVanillaEngine(process, Math.min(tGrid, 80), Math.min(xGrid, 80))
                : new AnalyticEuropeanEngine(process);
        EuropeanOption profileOption = new EuropeanOption(payoff, exercise);
        profileOption.setPricingEngine(profileEngine);

        List<VanillaOptionResponse.SpotProfilePoint> profile = new ArrayList<>();
        double minSpot = req.strike() * 0.65;
        double maxSpot = req.strike() * 1.35;
        int points = 21;
        for (int i = 0; i < points; i++) {
            double s = minSpot + (maxSpot - minSpot) * i / (points - 1);
            spotQuote.setValue(s);
            double val = profileOption.NPV();
            double intrinsic = optionType == Option.Type.Call
                    ? Math.max(0.0, s - req.strike())
                    : Math.max(0.0, req.strike() - s);
            Double d = safeGreek(profileOption::delta);
            Double g = safeGreek(profileOption::gamma);
            profile.add(new VanillaOptionResponse.SpotProfilePoint(s, val, intrinsic, d, g));
        }
        spotQuote.setValue(req.spot());

        double totalElapsedMs = (System.nanoTime() - totalStart) / 1_000_000.0;

        return new VanillaOptionResponse(
                req.optionType().toUpperCase(),
                req.exerciseStyle().toUpperCase(),
                req.evaluationDate(),
                req.maturityDate(),
                ttm,
                req.spot(),
                req.strike(),
                benchmarkNpv,
                benchmark.engineName(),
                results,
                profile,
                totalElapsedMs);
    }

    private static Option.Type parseOptionType(String type) {
        return switch (type.toUpperCase()) {
            case "CALL" -> Option.Type.Call;
            case "PUT" -> Option.Type.Put;
            default -> throw new IllegalArgumentException("Unsupported optionType: " + type + " (expected CALL or PUT)");
        };
    }

    private static boolean parseIsAmerican(String style) {
        return switch (style.toUpperCase()) {
            case "EUROPEAN" -> false;
            case "AMERICAN" -> true;
            default -> throw new IllegalArgumentException("Unsupported exerciseStyle: " + style + " (expected EUROPEAN or AMERICAN)");
        };
    }

    private static Double safeGreek(DoubleSupplier supplier) {
        try {
            double v = supplier.getAsDouble();
            return Double.isFinite(v) ? v : null;
        } catch (RuntimeException ignored) {
            return null;
        }
    }
}
