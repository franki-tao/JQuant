package jquant.pricingengines.asian;

import jquant.DiscreteAveragingAsianOption;
import jquant.OneAssetOption;
import jquant.PricingEngine;
import jquant.StochasticProcess;
import jquant.TimeGrid;
import jquant.math.MathUtils;
import jquant.math.randomnumbers.InverseCumulativeRsg;
import jquant.math.randomnumbers.RngType;
import jquant.methods.montecarlo.MultiPathGenerator;
import jquant.methods.montecarlo.PathGenerator;
import jquant.methods.montecarlo.PathPricer;
import jquant.patterns.Observable;
import jquant.patterns.ObservableSettings;
import jquant.patterns.Observer;
import jquant.pricingengines.McSimulation;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

import static jquant.math.CommonUtil.QL_REQUIRE;

/**
 * Pricing engine for discrete average Asians using Monte Carlo simulation.
 *
 * <p>Port of {@code MCDiscreteAveragingAsianEngineBase} from
 * {@code ql/pricingengines/asian/mcdiscreteasianenginebase.hpp}.
 */
public abstract class MCDiscreteAveragingAsianEngineBase
        extends McSimulation implements PricingEngine {
    protected final StochasticProcess process_;
    protected final DiscreteAveragingAsianOption.arguments arguments_ =
            new DiscreteAveragingAsianOption.arguments();
    protected final OneAssetOption.results results_ =
            new OneAssetOption.results();
    private final Set<Observer> observers_ = new HashSet<>();

    protected final int requiredSamples_;
    protected final int maxSamples_;
    protected final int timeSteps_;
    protected final int timeStepsPerYear_;
    protected final double requiredTolerance_;
    protected final boolean brownianBridge_;
    protected final long seed_;
    protected final boolean includeExerciseDate_;
    protected final RngType rngType_;

    protected MCDiscreteAveragingAsianEngineBase(
            StochasticProcess process,
            boolean brownianBridge,
            boolean antitheticVariate,
            boolean controlVariate,
            int requiredSamples,
            double requiredTolerance,
            int maxSamples,
            long seed,
            int timeSteps,
            int timeStepsPerYear,
            boolean includeExerciseDate,
            RngType rngType) {
        super(antitheticVariate, controlVariate);
        process_ = process;
        requiredSamples_ = requiredSamples;
        maxSamples_ = maxSamples;
        timeSteps_ = timeSteps;
        timeStepsPerYear_ = timeStepsPerYear;
        requiredTolerance_ = requiredTolerance;
        brownianBridge_ = brownianBridge;
        seed_ = seed;
        includeExerciseDate_ = includeExerciseDate;
        rngType_ = rngType;

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
        try {
            super.calculate(requiredTolerance_, requiredSamples_, maxSamples_);
        } catch (PastFixingsOnly e) {
            // Ideally, here we could calculate the payoff (which is fully
            // determined) and write it into the results. This would
            // probably need a new virtual method that derived engines
            // should implement.
            throw e;
        }

        results_.value = mcModel_.sampleAccumulator().mean();

        if (controlVariate_) {
            // control variate might lead to small negative option values
            // for deep OTM options
            results_.value = Math.max(0.0, results_.value);
        }

        if (rngType_.allowsErrorEstimate()) {
            results_.errorEstimate = mcModel_.sampleAccumulator().errorEstimate();
        }

        // Allow inspection of the timeGrid via additional results
        results_.additionalResults.put("TimeGrid", timeGrid());
    }

    @Override
    protected TimeGrid timeGrid() {
        final java.util.List<Double> fixingTimes = new ArrayList<>();
        for (jquant.time.Date fixingDate : arguments_.fixingDates) {
            final double t = process_.time(fixingDate);
            if (t >= 0) {
                fixingTimes.add(t);
            }
        }

        if (fixingTimes.isEmpty()
                || (fixingTimes.size() == 1 && fixingTimes.get(0) == 0.0)) {
            throw new PastFixingsOnly();
        }

        // Some models (eg. Heston) might request additional points in
        // the time grid to improve the accuracy of the discretization
        final jquant.time.Date lastExerciseDate =
                arguments_.exercise.lastDate();
        final double t = process_.time(lastExerciseDate);

        if (includeExerciseDate_ && t > fixingTimes.get(fixingTimes.size() - 1)) {
            fixingTimes.add(t);
        }

        if (timeSteps_ != MathUtils.NULL_SIZE) {
            return new TimeGrid(fixingTimes, timeSteps_);
        } else if (timeStepsPerYear_ != MathUtils.NULL_SIZE) {
            return new TimeGrid(fixingTimes,
                    (int) (timeStepsPerYear_ * t));
        }

        return new TimeGrid(fixingTimes);
    }

    @Override
    protected PathGenerator pathGenerator() {
        final int dimensions = process_.factors();
        final TimeGrid grid = timeGrid();
        final InverseCumulativeRsg generator = rngType_.makeSequenceGenerator(
                dimensions * (grid.size() - 1), seed_);
        return new PathGenerator(process_, grid, generator, brownianBridge_);
    }

    @Override
    protected MultiPathGenerator multiPathGenerator() {
        final int dimensions = process_.factors();
        final TimeGrid grid = timeGrid();
        final InverseCumulativeRsg generator = rngType_.makeSequenceGenerator(
                dimensions * (grid.size() - 1), seed_);
        return new MultiPathGenerator(process_, grid, generator,
                brownianBridge_);
    }

    @Override
    protected double controlVariateValue() {
        final PricingEngine controlPE = controlPricingEngine();
        QL_REQUIRE(controlPE != null,
                "engine does not provide control variation pricing engine");

        final DiscreteAveragingAsianOption.arguments controlArguments =
                (DiscreteAveragingAsianOption.arguments) controlPE.getArguments();
        controlArguments.payoff = arguments_.payoff;
        controlArguments.exercise = arguments_.exercise;
        controlArguments.averageType = arguments_.averageType;
        controlArguments.runningAccumulator = arguments_.runningAccumulator;
        controlArguments.pastFixings = arguments_.pastFixings;
        controlArguments.fixingDates =
                new ArrayList<>(arguments_.fixingDates);
        controlPE.calculate();

        final OneAssetOption.results controlResults =
                (OneAssetOption.results) controlPE.getResults();
        return controlResults.value;
    }

    @Override
    public DiscreteAveragingAsianOption.arguments getArguments() {
        return arguments_;
    }

    @Override
    public OneAssetOption.results getResults() {
        return results_;
    }

    @Override
    public void reset() {
        results_.reset();
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

    /** Subclasses provide the path pricer for their payoff type. */
    @Override
    protected PathPricer pathPricer() {
        return null;
    }
}
