package jquant.models.marketmodels.evolvers;

import jquant.math.Matrix;
import jquant.models.marketmodels.BrownianGenerator;
import jquant.models.marketmodels.BrownianGeneratorFactory;
import jquant.models.marketmodels.CurveState;
import jquant.models.marketmodels.EvolutionDescription;
import jquant.models.marketmodels.MarketModel;
import jquant.models.marketmodels.MarketModelEvolver;
import jquant.models.marketmodels.curvestates.CoterminalSwapCurveState;
import jquant.models.marketmodels.driftcomputation.SMMDriftCalculator;

import java.util.ArrayList;
import java.util.List;

import static jquant.math.CommonUtil.QL_REQUIRE;

/**
 * Official QuantLib
 * {@code ql/models/marketmodels/evolvers/lognormalcotswapratepc.hpp/.cpp}
 * port.
 *
 * <p>Predictor-Corrector evolver for a lognormal coterminal swap rate market
 * model.
 */
public class LogNormalCotSwapRatePc extends MarketModelEvolver {

    // inputs
    private final MarketModel marketModel_;
    private final List<Integer> numeraires_;
    private final int initialStep_;
    private BrownianGenerator generator_;
    // fixed variables
    private final List<List<Double>> fixedDrifts_ = new ArrayList<>();
    // working variables
    private final int numberOfRates_;
    private final int numberOfFactors_;
    private final CoterminalSwapCurveState curveState_;
    private int currentStep_;
    private final List<Double> swapRates_;
    private final List<Double> displacements_;
    private final List<Double> logSwapRates_;
    private final List<Double> initialLogSwapRates_;
    private final List<Double> drifts1_;
    private final List<Double> drifts2_;
    private final List<Double> initialDrifts_;
    private final List<Double> brownians_;
    private final List<Double> correlatedBrownians_;
    private final List<Integer> alive_;
    // helper classes
    private final List<SMMDriftCalculator> calculators_ = new ArrayList<>();

    public LogNormalCotSwapRatePc(final MarketModel marketModel,
                                  final BrownianGeneratorFactory factory,
                                  final List<Integer> numeraires,
                                  int initialStep) {
        marketModel_ = marketModel;
        numeraires_ = new ArrayList<>(numeraires);
        initialStep_ = initialStep;
        numberOfRates_ = marketModel.numberOfRates();
        numberOfFactors_ = marketModel_.numberOfFactors();
        curveState_ = new CoterminalSwapCurveState(marketModel.evolution().rateTimes());
        swapRates_ = new ArrayList<>(marketModel.initialRates());
        displacements_ = new ArrayList<>(marketModel.displacements());
        logSwapRates_ = filledList(numberOfRates_, 0.0);
        initialLogSwapRates_ = filledList(numberOfRates_, 0.0);
        drifts1_ = filledList(numberOfRates_, 0.0);
        drifts2_ = filledList(numberOfRates_, 0.0);
        initialDrifts_ = filledList(numberOfRates_, 0.0);
        brownians_ = filledList(numberOfFactors_, 0.0);
        correlatedBrownians_ = filledList(numberOfRates_, 0.0);
        alive_ = new ArrayList<>(marketModel.evolution().firstAliveRate());

        EvolutionDescription.checkCompatibility(marketModel.evolution(), numeraires_);

        int steps = marketModel.evolution().numberOfSteps();

        generator_ = factory.create(numberOfFactors_, steps - initialStep_);

        currentStep_ = initialStep_;

        for (int j = 0; j < steps; ++j) {
            final Matrix A = marketModel_.pseudoRoot(j);
            calculators_.add(new SMMDriftCalculator(A, displacements_,
                    marketModel.evolution().rateTaus(),
                    numeraires.get(j), alive_.get(j)));
            List<Double> fixed = filledList(numberOfRates_, 0.0);
            for (int k = 0; k < numberOfRates_; ++k) {
                double variance = 0.0;
                for (int f = 0; f < A.cols(); ++f)
                    variance += A.get(k, f) * A.get(k, f);
                fixed.set(k, -0.5 * variance);
            }
            fixedDrifts_.add(fixed);
        }

        setCoterminalSwapRates(marketModel_.initialRates());
    }

    @Override
    public List<Integer> numeraires() {
        return numeraires_;
    }

    public void setCoterminalSwapRates(final List<Double> swapRates) {
        QL_REQUIRE(swapRates.size() == numberOfRates_,
                "mismatch between swapRates and rateTimes");
        for (int i = 0; i < numberOfRates_; ++i)
            initialLogSwapRates_.set(i, Math.log(swapRates.get(i)
                    + displacements_.get(i)));
        curveState_.setOnCoterminalSwapRates(swapRates);
        calculators_.get(initialStep_).compute(curveState_, initialDrifts_);
    }

    @Override
    public void setInitialState(CurveState cs) {
        // why??
        CoterminalSwapCurveState cotcs = (CoterminalSwapCurveState) cs;
        final List<Double> swapRates = cotcs.coterminalSwapRates();
        setCoterminalSwapRates(swapRates);
    }

    @Override
    public double startNewPath() {
        currentStep_ = initialStep_;
        for (int i = 0; i < initialLogSwapRates_.size(); ++i)
            logSwapRates_.set(i, initialLogSwapRates_.get(i));
        return generator_.nextPath();
    }

    @Override
    public double advanceStep() {
        // we're going from T1 to T2

        // a) compute drifts D1 at T1;
        if (currentStep_ > initialStep_)
            calculators_.get(currentStep_).compute(curveState_, drifts1_);
        else
            copy(initialDrifts_, drifts1_);

        // b) evolve forwards up to T2 using D1;
        double weight = generator_.nextStep(brownians_);
        final Matrix A = marketModel_.pseudoRoot(currentStep_);
        final List<Double> fixedDrift = fixedDrifts_.get(currentStep_);

        int alive = alive_.get(currentStep_);
        for (int i = alive; i < numberOfRates_; ++i) {
            // official: logSwapRates_[i] += drifts1_[i] + fixedDrift[i];
            logSwapRates_.set(i, logSwapRates_.get(i)
                    + (drifts1_.get(i) + fixedDrift.get(i)));
            double correlated = 0.0;
            for (int f = 0; f < A.cols(); ++f)
                correlated += A.get(i, f) * brownians_.get(f);
            logSwapRates_.set(i, logSwapRates_.get(i) + correlated);
            swapRates_.set(i, Math.exp(logSwapRates_.get(i)) - displacements_.get(i));
        }

        // intermediate curve state update
        curveState_.setOnCoterminalSwapRates(swapRates_);

        // c) recompute drifts D2 using the predicted forwards;
        calculators_.get(currentStep_).compute(curveState_, drifts2_);

        // d) correct forwards using both drifts
        for (int i = alive; i < numberOfRates_; ++i) {
            logSwapRates_.set(i, logSwapRates_.get(i)
                    + (drifts2_.get(i) - drifts1_.get(i)) / 2.0);
            swapRates_.set(i, Math.exp(logSwapRates_.get(i)) - displacements_.get(i));
        }

        // e) update curve state
        curveState_.setOnCoterminalSwapRates(swapRates_);

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

    private static List<Double> filledList(int n, double value) {
        List<Double> result = new ArrayList<>();
        for (int i = 0; i < n; ++i)
            result.add(value);
        return result;
    }

    private static void copy(List<Double> from, List<Double> to) {
        for (int i = 0; i < from.size(); ++i)
            to.set(i, from.get(i));
    }
}
