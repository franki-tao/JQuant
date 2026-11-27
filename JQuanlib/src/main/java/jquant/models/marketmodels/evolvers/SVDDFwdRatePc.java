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
 * Official QuantLib {@code ql/models/marketmodels/evolvers/svddfwdratepc.hpp/.cpp} port.
 *
 * <p>Displaced diffusion LMM with uncorrelated vol process. Called "Shifted
 * BGM" with Heston vol by Brac in "Engineering BGM."
 * Vol process is an external input.
 */
public class SVDDFwdRatePc extends MarketModelEvolver {

    // inputs
    private final MarketModel marketModel_;
    private BrownianGenerator generator_;
    private final MarketModelVolProcess volProcess_;

    private int firstVolatilityFactor_;
    private final int volFactorsPerStep_;

    private final List<Integer> numeraires_;
    private final int initialStep_;

    // fixed variables
    private final List<List<Double>> fixedDrifts_ = new ArrayList<>();
    private final boolean[] isVolVariate_;
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
    private final List<Double> drifts2_;
    private final List<Double> initialDrifts_;
    private final List<Double> allBrownians_;
    private final List<Double> brownians_;
    private final List<Double> volBrownians_;
    private final List<Double> correlatedBrownians_;
    private final List<Integer> alive_;
    // helper classes
    private final List<LMMDriftCalculator> calculators_ = new ArrayList<>();

    public SVDDFwdRatePc(final MarketModel marketModel,
                         final BrownianGeneratorFactory factory,
                         final MarketModelVolProcess volProcess,
                         int firstVolatilityFactor,
                         int volatilityFactorStep,
                         List<Integer> numeraires) {
        this(marketModel, factory, volProcess, firstVolatilityFactor,
                volatilityFactorStep, numeraires, 0);
    }

    public SVDDFwdRatePc(final MarketModel marketModel,
                         final BrownianGeneratorFactory factory,
                         final MarketModelVolProcess volProcess,
                         int firstVolatilityFactor,
                         int volatilityFactorStep,
                         List<Integer> numeraires,
                         int initialStep) {
        marketModel_ = marketModel;
        volProcess_ = volProcess;
        firstVolatilityFactor_ = firstVolatilityFactor;
        volFactorsPerStep_ = volProcess.variatesPerStep();
        numeraires_ = new ArrayList<>(numeraires);
        initialStep_ = initialStep;
        isVolVariate_ = new boolean[volProcess.variatesPerStep()
                + marketModel.numberOfFactors()];
        numberOfRates_ = marketModel.numberOfRates();
        numberOfFactors_ = marketModel.numberOfFactors();
        curveState_ = new LMMCurveState(marketModel.evolution().rateTimes());
        forwards_ = new ArrayList<>(marketModel.initialRates());
        displacements_ = new ArrayList<>(marketModel.displacements());
        logForwards_ = zeroList(numberOfRates_);
        initialLogForwards_ = zeroList(numberOfRates_);
        drifts1_ = zeroList(numberOfRates_);
        drifts2_ = zeroList(numberOfRates_);
        initialDrifts_ = zeroList(numberOfRates_);
        allBrownians_ = zeroList(volProcess.variatesPerStep()
                + marketModel.numberOfFactors());
        brownians_ = zeroList(numberOfFactors_);
        volBrownians_ = zeroList(volProcess.variatesPerStep());
        correlatedBrownians_ = zeroList(numberOfRates_);
        alive_ = new ArrayList<>(marketModel.evolution().firstAliveRate());

        QL_REQUIRE(initialStep == 0, "initial step zero only supported currently. ");
        EvolutionDescription.checkCompatibility(marketModel.evolution(), numeraires);

        int steps = marketModel.evolution().numberOfSteps();

        generator_ = factory.create(numberOfFactors_ + volFactorsPerStep_,
                steps - initialStep_);

        currentStep_ = initialStep_;

        for (int j = 0; j < steps; ++j) {
            final Matrix A = marketModel_.pseudoRoot(j);
            calculators_.add(new LMMDriftCalculator(A, displacements_,
                    marketModel.evolution().rateTaus(),
                    numeraires_.get(j), alive_.get(j)));
            List<Double> fixed = zeroList(numberOfRates_);
            for (int k = 0; k < numberOfRates_; ++k) {
                double variance = 0.0;
                for (int r = 0; r < numberOfFactors_; ++r)
                    variance += A.get(k, r) * A.get(k, r);
                fixed.set(k, -0.5 * variance);
            }
            fixedDrifts_.add(fixed);
        }

        setForwards(marketModel_.initialRates());

        int variatesPerStep = numberOfFactors_ + volFactorsPerStep_;

        firstVolatilityFactor_ = Math.min(firstVolatilityFactor_,
                variatesPerStep - volFactorsPerStep_);

        int volIncrement = (variatesPerStep - firstVolatilityFactor_) / volFactorsPerStep_;

        for (int i = 0; i < volFactorsPerStep_; ++i)
            isVolVariate_[firstVolatilityFactor_ + i * volIncrement] = true;

        // Official constructor also accepts volatilityFactorStep but does not
        // use it. The parameter is kept to mirror the official interface.
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
        volProcess_.nextPath();
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
        double weight = generator_.nextStep(allBrownians_);

        // divide Brownians between vol process and forward process

        for (int i = 0, j = 0, k = 0; i < allBrownians_.size(); ++i)
            if (isVolVariate_[i]) {
                volBrownians_.set(j, allBrownians_.get(i));
                ++j;
            } else {
                brownians_.set(k, allBrownians_.get(i));
                ++k;
            }

        // get sd for step

        double weight2 = volProcess_.nextstep(volBrownians_);
        double sdMultiplier = volProcess_.stepSd();
        double varianceMultiplier = sdMultiplier * sdMultiplier;

        final Matrix A = marketModel_.pseudoRoot(currentStep_);
        final List<Double> fixedDrift = fixedDrifts_.get(currentStep_);

        int alive = alive_.get(currentStep_);
        for (int i = alive; i < numberOfRates_; ++i) {
            logForwards_.set(i, logForwards_.get(i)
                    + varianceMultiplier * (drifts1_.get(i) + fixedDrift.get(i)));
            double correlated = 0.0;
            for (int r = 0; r < numberOfFactors_; ++r)
                correlated += A.get(i, r) * brownians_.get(r);
            logForwards_.set(i, logForwards_.get(i) + sdMultiplier * correlated);
            forwards_.set(i, Math.exp(logForwards_.get(i)) - displacements_.get(i));
        }

        // c) recompute drifts D2 using the predicted forwards;
        calculators_.get(currentStep_).compute(forwards_, drifts2_);

        // d) correct forwards using both drifts
        for (int i = alive; i < numberOfRates_; ++i) {
            logForwards_.set(i, logForwards_.get(i)
                    + varianceMultiplier * (drifts2_.get(i) - drifts1_.get(i)) / 2.0);
            forwards_.set(i, Math.exp(logForwards_.get(i)) - displacements_.get(i));
        }

        // e) update curve state
        curveState_.setOnForwardRates(forwards_);

        ++currentStep_;

        return weight * weight2;
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
