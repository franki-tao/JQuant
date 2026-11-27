package jquant.pricingengines.vanilla;

import jquant.EuropeanOption;
import jquant.Option;
import jquant.PricingEngine;
import jquant.StochasticProcess;
import jquant.StochasticProcess1D;
import jquant.TimeGrid;
import jquant.math.MathUtils;
import jquant.math.randomnumbers.InverseCumulativeRsg;
import jquant.math.randomnumbers.RngType;
import jquant.patterns.Observable;
import jquant.patterns.ObservableSettings;
import jquant.patterns.Observer;
import jquant.pricingengines.McSimulation;
import jquant.time.Date;
import jquant.methods.montecarlo.MultiPathGenerator;
import jquant.methods.montecarlo.PathGenerator;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;

import static jquant.math.CommonUtil.QL_FAIL;
import static jquant.math.CommonUtil.QL_REQUIRE;

/**
 * Pricing engine for vanilla options using Monte Carlo simulation.
 *
 * <p>Port of QuantLib
 * {@code ql/pricingengines/vanilla/mcvanillaengine.hpp}. The official
 * {@code MC} template parameter ({@code SingleVariate} or
 * {@code MultiVariate}) is represented by the kind of process supplied:
 * a {@link StochasticProcess1D} uses a single-path
 * {@link PathGenerator}, any other {@link StochasticProcess} uses a
 * {@link MultiPathGenerator}. The RNG policy is selected with
 * {@link RngType}.
 */
public abstract class McVanillaEngine extends McSimulation implements PricingEngine {
    protected final StochasticProcess process_;
    protected final Option.arguments arguments_ = new Option.arguments();
    protected final EuropeanOption.results results_ = new EuropeanOption.results();
    private final Set<Observer> observers_ = new HashSet<>();

    protected int timeSteps_;
    protected int timeStepsPerYear_;
    protected int requiredSamples_;
    protected int maxSamples_;
    protected double requiredTolerance_;
    protected boolean brownianBridge_;
    protected long seed_;
    protected final RngType rngType_;

    protected McVanillaEngine(StochasticProcess process,
                              int timeSteps,
                              int timeStepsPerYear,
                              boolean brownianBridge,
                              boolean antitheticVariate,
                              boolean controlVariate,
                              int requiredSamples,
                              double requiredTolerance,
                              int maxSamples,
                              long seed,
                              RngType rngType) {
        super(antitheticVariate, controlVariate);
        process_ = process;
        timeSteps_ = timeSteps;
        timeStepsPerYear_ = timeStepsPerYear;
        requiredSamples_ = requiredSamples;
        maxSamples_ = maxSamples;
        requiredTolerance_ = requiredTolerance;
        brownianBridge_ = brownianBridge;
        seed_ = seed;
        rngType_ = rngType;

        QL_REQUIRE(timeSteps != MathUtils.NULL_SIZE
                        || timeStepsPerYear != MathUtils.NULL_SIZE,
                "no time steps provided");
        QL_REQUIRE(timeSteps == MathUtils.NULL_SIZE
                        || timeStepsPerYear == MathUtils.NULL_SIZE,
                "both time steps and time steps per year were provided");
        QL_REQUIRE(timeSteps != 0,
                "timeSteps must be positive, " + timeSteps + " not allowed");
        QL_REQUIRE(timeStepsPerYear != 0,
                "timeStepsPerYear must be positive, " + timeStepsPerYear
                        + " not allowed");

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
    public void calculate() {
        super.calculate(requiredTolerance_, requiredSamples_, maxSamples_);
        results_.value = mcModel_.sampleAccumulator().mean();
        if (rngType_.allowsErrorEstimate())
            results_.errorEstimate = mcModel_.sampleAccumulator().errorEstimate();
    }

    @Override
    protected TimeGrid timeGrid() {
        Date lastExerciseDate = arguments_.exercise.lastDate();
        double t = process_.time(lastExerciseDate);
        if (timeSteps_ != MathUtils.NULL_SIZE) {
            return new TimeGrid(t, timeSteps_);
        } else if (timeStepsPerYear_ != MathUtils.NULL_SIZE) {
            int steps = (int) (timeStepsPerYear_ * t);
            return new TimeGrid(t, Math.max(steps, 1));
        } else {
            QL_FAIL("time steps not specified");
            return null;
        }
    }

    @Override
    protected PathGenerator pathGenerator() {
        QL_REQUIRE(process_ instanceof StochasticProcess1D,
                "1-D process required");
        int dimensions = process_.factors();
        TimeGrid grid = timeGrid();
        InverseCumulativeRsg generator = rngType_.makeSequenceGenerator(
                dimensions * (grid.size() - 1), seed_);
        return new PathGenerator(process_, grid, generator, brownianBridge_);
    }

    @Override
    protected MultiPathGenerator multiPathGenerator() {
        int dimensions = process_.factors();
        TimeGrid grid = timeGrid();
        InverseCumulativeRsg generator = rngType_.makeSequenceGenerator(
                dimensions * (grid.size() - 1), seed_);
        return new MultiPathGenerator(process_, grid, generator,
                brownianBridge_);
    }

    public StochasticProcess process() {
        return process_;
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
    public void registerObserver(Observer o) {
        observers_.add(o);
    }

    @Override
    public void unregisterObserver(Observer o) {
        observers_.remove(o);
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
