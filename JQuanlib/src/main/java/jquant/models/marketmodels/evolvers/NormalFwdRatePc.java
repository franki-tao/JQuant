package jquant.models.marketmodels.evolvers;

import jquant.math.Matrix;
import jquant.models.marketmodels.BrownianGenerator;
import jquant.models.marketmodels.BrownianGeneratorFactory;
import jquant.models.marketmodels.CurveState;
import jquant.models.marketmodels.EvolutionDescription;
import jquant.models.marketmodels.MarketModel;
import jquant.models.marketmodels.MarketModelEvolver;
import jquant.models.marketmodels.curvestates.LMMCurveState;
import jquant.models.marketmodels.driftcomputation.LMMNormalDriftCalculator;

import java.util.ArrayList;
import java.util.List;

import static jquant.math.CommonUtil.QL_REQUIRE;

/**
 * Official QuantLib {@code ql/models/marketmodels/evolvers/normalfwdratepc.hpp/.cpp} port.
 *
 * <p>Predictor-corrector for the normal (absolute) forward-rate model.
 */
public class NormalFwdRatePc extends MarketModelEvolver {

    // inputs
    private final MarketModel marketModel_;
    private final List<Integer> numeraires_;
    private final int initialStep_;
    private final BrownianGenerator generator_;
    // working variables
    private final int numberOfRates_;
    private final int numberOfFactors_;
    private final LMMCurveState curveState_;
    private int currentStep_;
    private final List<Double> forwards_;
    private final List<Double> initialForwards_;
    private final List<Double> drifts1_;
    private final List<Double> drifts2_;
    private final List<Double> initialDrifts_;
    private final List<Double> brownians_;
    private final List<Double> correlatedBrownians_;
    private final List<Integer> alive_;
    // helper classes
    private final List<LMMNormalDriftCalculator> calculators_ = new ArrayList<>();

    public NormalFwdRatePc(final MarketModel marketModel,
                           final BrownianGeneratorFactory factory,
                           final List<Integer> numeraires) {
        this(marketModel, factory, numeraires, 0);
    }

    public NormalFwdRatePc(final MarketModel marketModel,
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
        initialForwards_ = new ArrayList<>(marketModel.initialRates());
        drifts1_ = zeroList(numberOfRates_);
        drifts2_ = zeroList(numberOfRates_);
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
            calculators_.add(new LMMNormalDriftCalculator(A,
                    marketModel.evolution().rateTaus(),
                    numeraires_.get(j), alive_.get(j)));
        }

        setForwards(marketModel_.initialRates());
    }

    private void setForwards(final List<Double> forwards) {
        QL_REQUIRE(forwards.size() == numberOfRates_,
                "mismatch between forwards and rateTimes");
        for (int i = 0; i < numberOfRates_; ++i)
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
            forwards_.set(i, initialForwards_.get(i));
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

        int alive = alive_.get(currentStep_);
        for (int i = alive; i < numberOfRates_; ++i) {
            forwards_.set(i, forwards_.get(i) + drifts1_.get(i));
            double correlated = 0.0;
            for (int r = 0; r < numberOfFactors_; ++r)
                correlated += A.get(i, r) * brownians_.get(r);
            forwards_.set(i, forwards_.get(i) + correlated);
        }

        // c) recompute drifts D2 using the predicted forwards;
        calculators_.get(currentStep_).compute(forwards_, drifts2_);

        // d) correct forwards using both drifts
        for (int i = alive; i < numberOfRates_; ++i)
            forwards_.set(i,
                    forwards_.get(i) + (drifts2_.get(i) - drifts1_.get(i)) / 2.0);

        // e) update curve state
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
