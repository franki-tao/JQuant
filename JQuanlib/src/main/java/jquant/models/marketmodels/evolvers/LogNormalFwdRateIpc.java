package jquant.models.marketmodels.evolvers;

import jquant.math.Matrix;
import jquant.models.marketmodels.BrownianGenerator;
import jquant.models.marketmodels.BrownianGeneratorFactory;
import jquant.models.marketmodels.CurveState;
import jquant.models.marketmodels.EvolutionDescription;
import jquant.models.marketmodels.MarketModel;
import jquant.models.marketmodels.MarketModelEvolver;
import jquant.models.marketmodels.curvestates.LMMCurveState;
import jquant.models.marketmodels.driftcomputation.LMMDriftCalculator;

import java.util.ArrayList;
import java.util.List;

import static jquant.math.CommonUtil.QL_REQUIRE;

/**
 * Official QuantLib {@code ql/models/marketmodels/evolvers/lognormalfwdrateipc.hpp/.cpp} port.
 *
 * <p>Iterative Predictor-Corrector.
 */
public class LogNormalFwdRateIpc extends MarketModelEvolver {

    // inputs
    private final MarketModel marketModel_;
    private final List<Integer> numeraires_;
    private final int initialStep_;
    private final BrownianGenerator generator_;
    // fixed variables
    private final List<List<Double>> fixedDrifts_ = new ArrayList<>();
    // working variables
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
    private final List<Double> g_;
    private final List<Double> brownians_;
    private final List<Double> correlatedBrownians_;
    private final List<Double> rateTaus_;
    private final List<Integer> alive_;
    // helper classes
    private final List<LMMDriftCalculator> calculators_ = new ArrayList<>();

    public LogNormalFwdRateIpc(final MarketModel marketModel,
                               final BrownianGeneratorFactory factory,
                               final List<Integer> numeraires) {
        this(marketModel, factory, numeraires, 0);
    }

    public LogNormalFwdRateIpc(final MarketModel marketModel,
                               final BrownianGeneratorFactory factory,
                               final List<Integer> numeraires,
                               int initialStep) {
        marketModel_ = marketModel;
        numeraires_ = new ArrayList<>(numeraires);
        initialStep_ = initialStep;
        numberOfRates_ = marketModel.numberOfRates();
        numberOfFactors_ = marketModel.numberOfFactors();
        curveState_ = new LMMCurveState(marketModel.evolution().rateTimes());
        forwards_ = new ArrayList<>(marketModel.initialRates());
        displacements_ = new ArrayList<>(marketModel.displacements());
        logForwards_ = zeroList(numberOfRates_);
        initialLogForwards_ = zeroList(numberOfRates_);
        drifts1_ = zeroList(numberOfRates_);
        initialDrifts_ = zeroList(numberOfRates_);
        g_ = zeroList(numberOfRates_);
        brownians_ = zeroList(numberOfFactors_);
        correlatedBrownians_ = zeroList(numberOfRates_);
        rateTaus_ = new ArrayList<>(marketModel.evolution().rateTaus());
        alive_ = new ArrayList<>(marketModel.evolution().firstAliveRate());

        EvolutionDescription.checkCompatibility(marketModel.evolution(), numeraires);
        QL_REQUIRE(EvolutionDescription.isInTerminalMeasure(marketModel.evolution(), numeraires),
                "terminal measure required for ipc ");

        int steps = marketModel.evolution().numberOfSteps();

        generator_ = factory.create(numberOfFactors_, steps - initialStep_);

        currentStep_ = initialStep_;

        for (int j = 0; j < steps; ++j) {
            final Matrix A = marketModel.pseudoRoot(j);
            calculators_.add(new LMMDriftCalculator(A, displacements_,
                    marketModel.evolution().rateTaus(),
                    numeraires_.get(j), alive_.get(j)));
            final Matrix C = marketModel.covariance(j);
            List<Double> fixed = zeroList(numberOfRates_);
            for (int k = 0; k < numberOfRates_; ++k) {
                double variance = C.get(k, k);
                fixed.set(k, -0.5 * variance);
            }
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
    public double startNewPath() {
        currentStep_ = initialStep_;
        for (int i = 0; i < numberOfRates_; ++i)
            logForwards_.set(i, initialLogForwards_.get(i));
        return generator_.nextPath();
    }

    @Override
    public double advanceStep() {
        // we're going from T1 to T2:

        // a) compute drifts D1 at T1;
        if (currentStep_ > initialStep_) {
            calculators_.get(currentStep_).computePlain(forwards_, drifts1_);
        } else {
            for (int i = 0; i < numberOfRates_; ++i)
                drifts1_.set(i, initialDrifts_.get(i));
        }

        double weight = generator_.nextStep(brownians_);
        final Matrix A = marketModel_.pseudoRoot(currentStep_);
        final Matrix C = marketModel_.covariance(currentStep_);
        final List<Double> fixedDrift = fixedDrifts_.get(currentStep_);

        int alive = alive_.get(currentStep_);
        for (int i = numberOfRates_ - 1; i >= alive; --i) {
            double drifts2 = 0.0;
            for (int j = i + 1; j < numberOfRates_; ++j)
                drifts2 -= g_.get(j) * C.get(i, j);
            logForwards_.set(i,
                    logForwards_.get(i) + 0.5 * (drifts1_.get(i) + drifts2) + fixedDrift.get(i));
            double correlated = 0.0;
            for (int r = 0; r < numberOfFactors_; ++r)
                correlated += A.get(i, r) * brownians_.get(r);
            logForwards_.set(i, logForwards_.get(i) + correlated);
            forwards_.set(i, Math.exp(logForwards_.get(i)) - displacements_.get(i));
            g_.set(i, rateTaus_.get(i) * (forwards_.get(i) + displacements_.get(i))
                    / (1.0 + rateTaus_.get(i) * forwards_.get(i)));
        }

        // update curve state
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
