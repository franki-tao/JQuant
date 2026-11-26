package jquant.models.marketmodels.evolvers;

import jquant.math.Matrix;
import jquant.math.distributions.CumulativeNormalDistribution;
import jquant.models.marketmodels.BrownianGenerator;
import jquant.models.marketmodels.BrownianGeneratorFactory;
import jquant.models.marketmodels.ConstrainedEvolver;
import jquant.models.marketmodels.CurveState;
import jquant.models.marketmodels.EvolutionDescription;
import jquant.models.marketmodels.MarketModel;
import jquant.models.marketmodels.curvestates.LMMCurveState;
import jquant.models.marketmodels.driftcomputation.LMMDriftCalculator;

import java.util.ArrayList;
import java.util.List;

import static jquant.math.CommonUtil.QL_REQUIRE;

/**
 * Official QuantLib
 * {@code ql/models/marketmodels/evolvers/lognormalfwdrateeulerconstrained.hpp/.cpp}
 * port.
 *
 * <p>Euler stepping with the Fries-Joshi proxy-simulation constraint.
 */
public class LogNormalFwdRateEulerConstrained extends ConstrainedEvolver {

    // inputs
    private final MarketModel marketModel_;
    private final List<Integer> numeraires_;
    private final int initialStep_;
    private final BrownianGenerator generator_;

    private List<Integer> startIndexOfSwapRate_ = new ArrayList<>();
    private List<Integer> endIndexOfSwapRate_ = new ArrayList<>();

    // often changing inputs
    private List<Double> rateConstraints_ = new ArrayList<>();
    private boolean[] isConstraintActive_ = new boolean[0];

    // fixed variables
    private final List<List<Double>> fixedDrifts_ = new ArrayList<>();
    private final List<List<Double>> variances_ = new ArrayList<>();

    // working variables
    private final List<List<Double>> covariances_ = new ArrayList<>();
    private final int numberOfRates_;
    private final int numberOfFactors_;
    private final LMMCurveState curveState_;
    private int currentStep_;
    private final List<Double> forwards_;
    private final List<Double> displacements_;
    private final List<Double> logForwards_;
    private final List<Double> initialLogForwards_;
    private final List<Double> drifts1_;
    private final List<Double> initialDrifts_;
    private final List<Double> brownians_;
    private final List<Double> correlatedBrownians_;
    private final List<Integer> alive_;
    // helper classes
    private final List<LMMDriftCalculator> calculators_ = new ArrayList<>();

    public LogNormalFwdRateEulerConstrained(final MarketModel marketModel,
                                            final BrownianGeneratorFactory factory,
                                            final List<Integer> numeraires) {
        this(marketModel, factory, numeraires, 0);
    }

    public LogNormalFwdRateEulerConstrained(final MarketModel marketModel,
                                            final BrownianGeneratorFactory factory,
                                            final List<Integer> numeraires,
                                            int initialStep) {
        marketModel_ = marketModel;
        numeraires_ = new ArrayList<>(numeraires);
        initialStep_ = initialStep;
        numberOfRates_ = marketModel.numberOfRates();
        numberOfFactors_ = marketModel_.numberOfFactors();
        curveState_ = new LMMCurveState(marketModel.evolution().rateTimes());
        forwards_ = new ArrayList<>(marketModel.initialRates());
        displacements_ = new ArrayList<>(marketModel.displacements());
        logForwards_ = zeroList(numberOfRates_);
        initialLogForwards_ = zeroList(numberOfRates_);
        drifts1_ = zeroList(numberOfRates_);
        initialDrifts_ = zeroList(numberOfRates_);
        brownians_ = zeroList(numberOfFactors_);
        correlatedBrownians_ = zeroList(numberOfRates_);
        alive_ = new ArrayList<>(marketModel.evolution().firstAliveRate());

        EvolutionDescription.checkCompatibility(marketModel.evolution(), numeraires);

        int steps = marketModel.evolution().numberOfSteps();

        generator_ = factory.create(numberOfFactors_, steps - initialStep_);
        currentStep_ = initialStep_;

        for (int j = 0; j < steps; ++j) {
            final Matrix A = marketModel_.pseudoRoot(j);
            calculators_.add(new LMMDriftCalculator(A, displacements_,
                    marketModel.evolution().rateTaus(),
                    numeraires_.get(j), alive_.get(j)));
            List<Double> fixed = zeroList(numberOfRates_);
            List<Double> variances = zeroList(numberOfRates_);
            for (int k = 0; k < numberOfRates_; ++k) {
                double variance = 0.0;
                for (int r = 0; r < numberOfFactors_; ++r)
                    variance += A.get(k, r) * A.get(k, r);
                variances.set(k, variance);
                fixed.set(k, -0.5 * variance);
            }
            variances_.add(variances);
            fixedDrifts_.add(fixed);
        }

        setForwards(marketModel_.initialRates());
    }

    private void setForwards(final List<Double> forwards) {
        QL_REQUIRE(forwards.size() == numberOfRates_,
                "mismatch between forwards and rateTimes");
        for (int i = 0; i < numberOfRates_; ++i)
            initialLogForwards_.set(i, Math.log(forwards.get(i) + displacements_.get(i)));
        calculators_.get(initialStep_).compute(forwards, initialDrifts_);
    }

    @Override
    public void setInitialState(CurveState cs) {
        setForwards(cs.forwardRates());
    }

    @Override
    public void setConstraintType(
            final List<Integer> startIndexOfSwapRate,
            final List<Integer> endIndexOfSwapRate) {
        QL_REQUIRE(startIndexOfSwapRate.size() == numeraires_.size(),
                "Size mismatch in constraint specification.");
        QL_REQUIRE(endIndexOfSwapRate.size() == numeraires_.size(),
                "Size mismatch in constraint specification.");

        startIndexOfSwapRate_ = new ArrayList<>(startIndexOfSwapRate);
        endIndexOfSwapRate_ = new ArrayList<>(endIndexOfSwapRate);

        covariances_.clear();

        List<Double> covariances = zeroList(numberOfRates_);

        for (int i = 0; i < startIndexOfSwapRate_.size(); ++i) {
            QL_REQUIRE(startIndexOfSwapRate_.get(i) + 1 == endIndexOfSwapRate_.get(i),
                    "constrained euler currently only implemented for forward rates");

            // Official uses pseudoRoot(currentStep_) here, not pseudoRoot(i).
            final Matrix A = marketModel_.pseudoRoot(currentStep_);

            for (int j = 0; j < numberOfRates_; ++j) {
                double cov = 0.0;
                for (int k = 0; k < numberOfFactors_; ++k)
                    cov += A.get(startIndexOfSwapRate_.get(i), k) * A.get(j, k);
                covariances.set(j, cov);
            }
            covariances_.add(new ArrayList<>(covariances));
        }
    }

    @Override
    public void setThisConstraint(
            final List<Double> rateConstraints,
            final boolean[] isConstraintActive) {
        QL_REQUIRE(rateConstraints.size() == numeraires_.size(),
                "wrong number of constraints specified");

        QL_REQUIRE(isConstraintActive.length == numeraires_.size(),
                "wrong number of isConstraintActive specified");

        rateConstraints_ = new ArrayList<>(rateConstraints);
        isConstraintActive_ = isConstraintActive.clone();

        for (int i = 0; i < rateConstraints_.size(); i++)
            rateConstraints_.set(i,
                    Math.log(rateConstraints_.get(i) + displacements_.get(i)));
    }

    @Override
    public double startNewPath() {
        currentStep_ = initialStep_;
        for (int i = 0; i < numberOfRates_; ++i)
            logForwards_.set(i, initialLogForwards_.get(i));
        return generator_.nextPath();
    }

    @Override
    public double advanceStep() {
        // we're going from T1 to T2

        // a) compute drifts D1 at T1;
        if (currentStep_ > initialStep_) {
            calculators_.get(currentStep_).compute(forwards_, drifts1_);
        } else {
            for (int i = 0; i < numberOfRates_; ++i)
                drifts1_.set(i, initialDrifts_.get(i));
        }

        // b) evolve forwards up to T2 using D1;
        double weight = generator_.nextStep(brownians_);
        final Matrix A = marketModel_.pseudoRoot(currentStep_);
        final List<Double> fixedDrift = fixedDrifts_.get(currentStep_);

        int alive = alive_.get(currentStep_);
        for (int i = alive; i < numberOfRates_; ++i) {
            logForwards_.set(i,
                    logForwards_.get(i) + drifts1_.get(i) + fixedDrift.get(i));
            double correlated = 0.0;
            for (int r = 0; r < numberOfFactors_; ++r)
                correlated += A.get(i, r) * brownians_.get(r);
            logForwards_.set(i, logForwards_.get(i) + correlated);
        }

        // check constraint active
        if (isConstraintActive_[currentStep_]) {
            int index = startIndexOfSwapRate_.get(currentStep_);

            // compute error
            double requiredShift = rateConstraints_.get(currentStep_)
                    - logForwards_.get(index);

            double multiplier = requiredShift / variances_.get(currentStep_).get(index);

            // now shift each rate by multiplier * weighting of index rate
            // across the step
            for (int i = alive; i < numberOfRates_; ++i) {
                // we only need a small part of cov matrix
                logForwards_.set(i, logForwards_.get(i)
                        + multiplier * covariances_.get(currentStep_).get(i));
            }

            // now we need to calculate the LR shift, we have moved Gaussian Z_k
            // by multiplier * a_{index,k}; divide original density by density
            // of shifted normal
            double weightsEffect = 1.0;

            CumulativeNormalDistribution phi = new CumulativeNormalDistribution();
            for (int k = 0; k < numberOfFactors_; ++k) {
                double shift = multiplier * A.get(index, k);
                double originalDensity = phi.derivative(brownians_.get(k) + shift);
                // the density of the draw after changes in original measure
                double newDensity = phi.derivative(brownians_.get(k));
                // the density of the draw after changes in new measure,
                // shifts cancel
                weightsEffect *= originalDensity / newDensity;
            }

            weight *= weightsEffect;
        }

        for (int i = alive; i < numberOfRates_; ++i)
            forwards_.set(i, Math.exp(logForwards_.get(i)) - displacements_.get(i));

        // c) update curve state
        curveState_.setOnForwardRates(forwards_);

        ++currentStep_;

        return weight;
    }

    @Override
    public int currentStep() {
        return currentStep_;
    }

    @Override
    public CurveState currentState() {
        return curveState_;
    }

    @Override
    public List<Integer> numeraires() {
        return numeraires_;
    }

    private static List<Double> zeroList(int n) {
        List<Double> result = new ArrayList<>(n);
        for (int i = 0; i < n; ++i)
            result.add(0.0);
        return result;
    }
}
