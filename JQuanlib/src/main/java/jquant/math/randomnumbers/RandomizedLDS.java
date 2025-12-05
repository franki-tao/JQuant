package jquant.math.randomnumbers;

import jquant.math.CommonUtil;
import jquant.math.randomnumbers.impl.UsgImpl;
import jquant.methods.montecarlo.SampleVector;

import static jquant.math.CommonUtil.QL_REQUIRE;

//! Randomized (random shift) low-discrepancy sequence
/**!Random-shifts a uniform low-discrepancy sequence of dimension
    \f$ N \f$ by adding (modulo 1 for each coordinate) a pseudo-random
    uniform deviate in \f$ (0, 1)^N. \f$
    It is used for implementing Randomized Quasi Monte Carlo.

    The uniform low discrepancy sequence is supplied by LDS; the
    uniform pseudo-random sequence is supplied by PRS.

    Both class LDS and PRS must implement the following interface:
    \code
        LDS::sample_type LDS::nextSequence() const;
        Size LDS::dimension() const;
    \endcode

    \pre LDS and PRS must have the same dimension \f$ N \f$

    \warning Inverting LDS and PRS is possible, but it doesn't
             make sense.

    \todo implement the other randomization algorithms

    \test correct initialization is tested.
*/
// PRS默认是RandomSequenceGenerator
public class RandomizedLDS<LDS extends UsgImpl, PRS extends UsgImpl> {
    private LDS ldsg_, pristineldsg_; // mutable because nextSequence is const
    private PRS prsg_;
    private int dimension_;
    private SampleVector x, randomizer_;

    public RandomizedLDS(LDS ldsg, PRS prsg) {
        ldsg_ = ldsg;
        pristineldsg_ = ldsg;
        prsg_ = prsg;
        dimension_ = ldsg_.dimension();
        x = new SampleVector(CommonUtil.ArrayInit(dimension_, 0d), 1.0);
        randomizer_ = new SampleVector(CommonUtil.ArrayInit(dimension_, 0d), 1.0);

        QL_REQUIRE(prsg_.dimension() == dimension_,
                "generator mismatch: "
                        + dimension_ + "-dim low discrepancy "
                        + "and " + prsg_.dimension() + "-dim pseudo random");

        randomizer_ = prsg_.nextSequence();
    }

    /**
     * ! returns next sample using a given randomizing vector
     */
    public final SampleVector nextSequence() {
        SampleVector sample = ldsg_.nextSequence();
        x.weight = randomizer_.weight * sample.weight;
        for (int i = 0; i < dimension_; i++) {
            x.value.set(i, randomizer_.value.get(i) + sample.value.get(i));
            if (x.value.get(i) > 1.0)
                x.value.set(i, x.value.get(i) - 1.0);
        }
        return x;
    }

    public final SampleVector lastSequence() {
        return x;
    }

    /**
     * ! update the randomizing vector and re-initialize
     * the low discrepancy generator
     */
    public void nextRandomizer() {
        randomizer_ = prsg_.nextSequence();
        ldsg_ = pristineldsg_;
    }

    public int dimension() {
        return dimension_;
    }
}
