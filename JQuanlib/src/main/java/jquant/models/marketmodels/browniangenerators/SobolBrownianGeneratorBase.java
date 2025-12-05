package jquant.models.marketmodels.browniangenerators;

import jquant.math.CommonUtil;
import jquant.methods.montecarlo.BrownianBridge;
import jquant.methods.montecarlo.SampleVector;
import jquant.models.marketmodels.BrownianGenerator;
import jquant.models.marketmodels.browniangenerators.impl.BrownUtil;

import java.util.ArrayList;
import java.util.List;

import static jquant.math.CommonUtil.QL_FAIL;
import static jquant.math.CommonUtil.QL_REQUIRE;

//! Sobol Brownian generator for market-model simulations

/**
 * ! Incremental Brownian generator using a Sobol generator,
 * inverse-cumulative Gaussian method, and Brownian bridging.
 */
public abstract class SobolBrownianGeneratorBase extends BrownianGenerator {
    public enum Ordering {
        Factors,
        /**
         * !< The variates with the best quality will be
         * used for the evolution of the first factor.
         */
        Steps,
        /**
         * !< The variates with the best quality will be
         * used for the largest steps of all factors.
         */
        Diagonal  /**!< A diagonal schema will be used to assign
         the variates with the best quality to the
         most important factors and the largest
         steps. */
    }

    private int factors_, steps_;
    private Ordering ordering_;
    private BrownianBridge bridge_;
    // work variables
    private int lastStep_ = 0;
    private List<List<Integer>> orderedIndices_;
    private List<List<Double>> bridgedVariates_;

    public SobolBrownianGeneratorBase(int factors,
                                      int steps,
                                      Ordering ordering) {
        factors_ = factors;
        steps_ = steps;
        ordering_ = ordering;
        bridge_ = new BrownianBridge(steps);
        orderedIndices_ = new ArrayList<>();
        for (int i = 0; i < factors; i++) {
            orderedIndices_.add(CommonUtil.ArrayInit(steps, 0));
        }
        bridgedVariates_ = new ArrayList<>();
        for (int i = 0; i < factors; i++) {
            bridgedVariates_.add(CommonUtil.ArrayInit(steps, 0d));
        }
        switch (ordering_) {
            case Factors:
                BrownUtil.fillByFactor(orderedIndices_, factors_, steps_);
                break;
            case Steps:
                BrownUtil.fillByStep(orderedIndices_, factors_, steps_);
                break;
            case Diagonal:
                BrownUtil.fillByDiagonal(orderedIndices_, factors_, steps_);
                break;
            default:
                QL_FAIL("unknown ordering");
        }
    }

    public final List<List<Integer>> orderedIndices() {
        return orderedIndices_;
    }

    public List<List<Double>> transform(final List<List<Double>> variates) {
        QL_REQUIRE((variates.size() == factors_ * steps_),
                "inconsistent variate vector");

        final int dim = factors_ * steps_;
        final int nPaths = variates.get(0).size();

        List<List<Double>> retVal = new ArrayList<>();
        for (int i = 0; i < factors_; i++) {
            retVal.add(CommonUtil.ArrayInit(nPaths * steps_, 0d));
        }
        for (int j = 0; j < nPaths; ++j) {
            List<Double> sample = CommonUtil.ArrayInit(steps_ * factors_, 0d);
            for (int k = 0; k < dim; ++k) {
                sample.set(k, variates.get(k).get(j));
            }
            for (int i = 0; i < factors_; ++i) {
                bridge_.transform(make_permutation_iterator(sample, orderedIndices_.get(i)),
                        retVal.get(i), j * steps_);
            }
        }

        return retVal;
    }

    @Override
    public double nextStep(List<Double> output) {
        QL_REQUIRE(output.size() == factors_, "size mismatch");
        QL_REQUIRE(lastStep_ < steps_, "sequence exhausted");
        for (int i = 0; i < factors_; ++i)
            output.set(i, bridgedVariates_.get(i).get(lastStep_));
        ++lastStep_;
        return 1.0;
    }

    @Override
    public double nextPath() {
        final SampleVector sample = nextSequence();
        // Brownian-bridge the variates according to the ordered indices
        for (int i = 0; i < factors_; ++i) {
            bridge_.transform(make_permutation_iterator(sample.value, orderedIndices_.get(i)), bridgedVariates_.get(i), 0);
        }
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

    protected abstract SampleVector nextSequence();

    private List<Double> make_permutation_iterator(List<Double> sp, List<Integer> ids) {
        List<Double> res = new ArrayList<>();
        for (int i : ids) {
            res.add(sp.get(i));
        }
        return res;
    }
}
