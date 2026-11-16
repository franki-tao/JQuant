package jquant.math.randomnumbers;

import jquant.math.distributions.InverseCumulativeNormal;

/**
 * Random-number generation policies.
 *
 * <p>Java counterpart of the {@code PseudoRandom} and
 * {@code LowDiscrepancy} traits in
 * {@code ql/math/randomnumbers/rngtraits.hpp}. Instances are described
 * by {@link RngType}.
 */
public final class PseudoRandom {
    private PseudoRandom() {
    }

    /** Whether the sequence allows a Monte Carlo error estimate. */
    public static final boolean allowsErrorEstimate = true;

    public static InverseCumulativeRsg make_sequence_generator(int dimension, long seed) {
        RandomSequenceGenerator ursg = new RandomSequenceGenerator(
                dimension, new MersenneTwisterUniformRng(seed));
        return new InverseCumulativeRsg(ursg, new InverseCumulativeNormal());
    }
}
