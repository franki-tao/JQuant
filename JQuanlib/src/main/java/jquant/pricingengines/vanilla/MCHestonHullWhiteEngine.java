package jquant.pricingengines.vanilla;

import jquant.Exercise;
import jquant.Payoff;
import jquant.PricingEngine;
import jquant.TimeGrid;
import jquant.math.Array;
import jquant.math.randomnumbers.InverseCumulativeRsg;
import jquant.math.randomnumbers.RngType;
import jquant.methods.montecarlo.MultiPath;
import jquant.methods.montecarlo.MultiPathGenerator;
import jquant.methods.montecarlo.MultiPathPricer;
import jquant.models.equity.HestonModel;
import jquant.models.shortrate.onefactormodels.HullWhite;
import jquant.processes.HestonProcess;
import jquant.processes.HullWhiteForwardProcess;
import jquant.processes.HybridHestonHullWhiteProcess;

import static jquant.math.CommonUtil.QL_REQUIRE;

/**
 * Monte Carlo vanilla option engine for stochastic interest rates.
 *
 * <p>Port of
 * {@code ql/pricingengines/vanilla/mchestonhullwhiteengine.{hpp,cpp}}.
 * The official {@code RNG} template argument defaults to
 * {@code PseudoRandom}; Java selects it through {@link RngType}.
 */
public class MCHestonHullWhiteEngine extends McVanillaEngine {

    // just to avoid upcasting
    protected final HybridHestonHullWhiteProcess process_;

    public MCHestonHullWhiteEngine(
            final HybridHestonHullWhiteProcess process,
            int timeSteps,
            int timeStepsPerYear,
            boolean antitheticVariate,
            boolean controlVariate,
            int requiredSamples,
            double requiredTolerance,
            int maxSamples,
            long seed) {
        this(process, timeSteps, timeStepsPerYear, antitheticVariate,
                controlVariate, requiredSamples, requiredTolerance,
                maxSamples, seed, RngType.PSEUDO_RANDOM);
    }

    public MCHestonHullWhiteEngine(
            final HybridHestonHullWhiteProcess process,
            int timeSteps,
            int timeStepsPerYear,
            boolean antitheticVariate,
            boolean controlVariate,
            int requiredSamples,
            double requiredTolerance,
            int maxSamples,
            long seed,
            RngType rngType) {
        super(process, timeSteps, timeStepsPerYear,
                false, antitheticVariate, controlVariate,
                requiredSamples, requiredTolerance, maxSamples, seed,
                rngType);
        process_ = process;
    }

    @Override
    public void calculate() {
        super.calculate();

        if (controlVariate_) {
            // control variate might lead to small negative
            // option values for deep OTM options
            results_.value = Math.max(0.0, results_.value);
        }
    }

    @Override
    protected MultiPathPricer multiPathPricer() {
        final Exercise exercise = arguments_.exercise;

        QL_REQUIRE(exercise.type() == Exercise.Type.European,
                "only european exercise is supported");

        final double exerciseTime = process_.time(exercise.lastDate());

        return new HestonHullWhitePathPricer(exerciseTime,
                arguments_.payoff, process_);
    }

    @Override
    protected MultiPathPricer controlMultiPathPricer() {
        final HestonProcess hestonProcess = process_.hestonProcess();

        QL_REQUIRE(hestonProcess != null,
                "first constituent of the joint stochastic process "
                        + "need to be of type HestonProcess");

        final Exercise exercise = arguments_.exercise;

        QL_REQUIRE(exercise.type() == Exercise.Type.European,
                "only european exercise is supported");

        final double exerciseTime = process_.time(exercise.lastDate());

        return new HestonHullWhitePathPricer(exerciseTime,
                arguments_.payoff, process_);
    }

    @Override
    protected PricingEngine controlPricingEngine() {
        final HestonProcess hestonProcess = process_.hestonProcess();

        final HullWhiteForwardProcess hullWhiteProcess =
                process_.hullWhiteProcess();

        final HestonModel hestonModel = new HestonModel(hestonProcess);
        final HullWhite hwModel = new HullWhite(hestonProcess.riskFreeRate(),
                hullWhiteProcess.a(), hullWhiteProcess.sigma());

        return new AnalyticHestonHullWhiteEngine(hestonModel, hwModel, 144);
    }

    @Override
    protected MultiPathGenerator controlMultiPathGenerator() {
        final int dimensions = process_.factors();
        final TimeGrid grid = timeGrid();
        final InverseCumulativeRsg generator = rngType_.makeSequenceGenerator(
                dimensions * (grid.size() - 1), seed_);

        final HybridHestonHullWhiteProcess cvProcess =
                new HybridHestonHullWhiteProcess(process_.hestonProcess(),
                        process_.hullWhiteProcess(), 0.0,
                        process_.discretization());

        return new MultiPathGenerator(cvProcess, grid, generator, false);
    }
}

/**
 * Path pricer for the Heston-Hull-White Monte Carlo engine.
 *
 * <p>Port of {@code HestonHullWhitePathPricer} from
 * {@code ql/pricingengines/vanilla/mchestonhullwhiteengine.{hpp,cpp}}.
 */
class HestonHullWhitePathPricer implements MultiPathPricer {
    private final double exerciseTime_;
    private final Payoff payoff_;
    private final HybridHestonHullWhiteProcess process_;

    HestonHullWhitePathPricer(double exerciseTime,
                              Payoff payoff,
                              HybridHestonHullWhiteProcess process) {
        exerciseTime_ = exerciseTime;
        payoff_ = payoff;
        process_ = process;
    }

    @Override
    public double price(MultiPath path) {
        QL_REQUIRE(path.pathSize() > 0, "the path cannot be empty");

        final Array states = new Array(path.assetNumber());
        for (int j = 0; j < states.size(); ++j) {
            states.set(j, path.get(j).value(path.pathSize() - 1));
        }

        final double df = 1.0 / process_.numeraire(exerciseTime_, states);
        return payoff_.value(states.get(0)) * df;
    }
}
