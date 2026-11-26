package jquant.models.marketmodels.browniangenerators;

import jquant.math.distributions.InverseCumulativeNormal;
import jquant.math.randomnumbers.MersenneTwisterUniformRng;
import jquant.math.randomnumbers.RandomSequenceGenerator;
import jquant.methods.montecarlo.SampleVector;
import jquant.models.marketmodels.BrownianGenerator;

import java.util.List;

import static jquant.math.CommonUtil.QL_REQUIRE;

/**
 * Official QuantLib {@code ql/models/marketmodels/browniangenerators/mtbrowniangenerator.hpp/.cpp} port.
 *
 * <p>Incremental Brownian generator using a Mersenne-twister uniform generator
 * and inverse-cumulative Gaussian method.
 */
public class MTBrownianGenerator extends BrownianGenerator {

    private final int factors_;
    private final int steps_;
    private int lastStep_ = 0;
    private final RandomSequenceGenerator generator_;
    private final InverseCumulativeNormal inverseCumulative_ = new InverseCumulativeNormal();

    public MTBrownianGenerator(int factors, int steps) {
        this(factors, steps, 0);
    }

    public MTBrownianGenerator(int factors, int steps, long seed) {
        factors_ = factors;
        steps_ = steps;
        generator_ = new RandomSequenceGenerator(factors * steps,
                new MersenneTwisterUniformRng(seed));
    }

    @Override
    public double nextStep(List<Double> output) {
        QL_REQUIRE(output.size() == factors_, "size mismatch");
        QL_REQUIRE(lastStep_ < steps_, "uniform sequence exhausted");
        // no copying, just fetching a reference
        final List<Double> currentSequence = generator_.lastSequence().value;
        int start = lastStep_ * factors_;
        int end = (lastStep_ + 1) * factors_;
        for (int i = 0; i < factors_; ++i)
            output.set(i, inverseCumulative_.value(currentSequence.get(start + i)));
        ++lastStep_;
        return 1.0;
    }

    @Override
    public double nextPath() {
        final SampleVector sample = generator_.nextSequence();
        lastStep_ = 0;
        return sample.weight;
    }

    @Override
    public int numberOfFactors() {
        return factors_;
    }

    @Override
    public int numberOfSteps() {
        return steps_;
    }
}
