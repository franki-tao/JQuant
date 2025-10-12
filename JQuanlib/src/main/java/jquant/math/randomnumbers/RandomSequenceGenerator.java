package jquant.math.randomnumbers;

import jquant.math.CommonUtil;
import jquant.math.randomnumbers.impl.RngImpl;
import jquant.methods.montecarlo.SampleReal;
import jquant.methods.montecarlo.SampleVector;

import java.util.List;

import static jquant.math.CommonUtil.QL_REQUIRE;

//! Random sequence generator based on a pseudo-random number generator
/*! Random sequence generator based on a pseudo-random number
    generator RNG.

    Class RNG must implement the following interface:
    \code
        RNG::sample_type RNG::next() const;
    \endcode
    If a client of this class wants to use the nextInt32Sequence method,
    class RNG must also implement
    \code
        unsigned long RNG::nextInt32() const;
    \endcode

    \warning do not use with low-discrepancy sequence generator.
*/
public class RandomSequenceGenerator  {

    enum RNG {
        MersenneTwisterUniformRng
    }
    private int dimensionality_;
    private RngImpl rng_;
    private SampleVector sequence_;
    private List<Long> int32Sequence_;

    public RandomSequenceGenerator(int dimensionality, final RngImpl rng) {
        dimensionality_ = dimensionality;
        rng_ = rng;
        sequence_ = new SampleVector(CommonUtil.ArrayInit(dimensionality), 1.0);
        int32Sequence_ = CommonUtil.ArrayInit(dimensionality);
        QL_REQUIRE(dimensionality>0, "dimensionality must be greater than 0");
    }

    //seed = 0
    public RandomSequenceGenerator(int dimensionality, RNG rng, long seed) {
        dimensionality_ = dimensionality;
        if (rng == RNG.MersenneTwisterUniformRng)
            rng_ = new MersenneTwisterUniformRng(seed);
        sequence_ = new SampleVector(CommonUtil.ArrayInit(dimensionality), 1.0);
        int32Sequence_ = CommonUtil.ArrayInit(dimensionality);
    }

    public SampleVector nextSequence() {
        sequence_.weight = 1.0;
        for (int i=0; i<dimensionality_; i++) {
            SampleReal x = rng_.next();
            // typename RNG::sample_type x(rng_.next());
            sequence_.value.set(i, x.value);
            sequence_.weight  *= x.weight;
        }
        return sequence_;
    }

    public List<Long> nextInt32Sequence() {
        for (int i=0; i<dimensionality_; i++) {
            int32Sequence_.set(i, rng_.nextInt32());
        }
        return int32Sequence_;
    }

    public final SampleVector lastSequence() {
        return sequence_;
    }

    public int dimension() {return dimensionality_;}

}
