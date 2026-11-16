package jquant.math.randomnumbers;

import jquant.math.distributions.InverseCumulativeNormal;

/**
 * Low-discrepancy sequence generation policy.
 *
 * <p>Java counterpart of the {@code LowDiscrepancy} trait in
 * {@code ql/math/randomnumbers/rngtraits.hpp}: a Sobol sequence fed
 * through the inverse cumulative normal distribution. Official
 * defaults are seed 0, Jaeckel direction integers and Gray code.
 */
public final class LowDiscrepancy {
    private LowDiscrepancy() {
    }

    /** Low-discrepancy sequences do not provide an error estimate. */
    public static final boolean allowsErrorEstimate = false;

    public static InverseCumulativeRsg make_sequence_generator(int dimension, long seed) {
        SobolRsg ursg = new SobolRsg(dimension, seed,
                SobolRsg.DirectionIntegers.Jaeckel, true);
        return new InverseCumulativeRsg(ursg, new InverseCumulativeNormal());
    }
}
