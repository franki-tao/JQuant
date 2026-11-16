package jquant.math.randomnumbers;

/**
 * Policy selector for the single-variate Monte Carlo engines.
 *
 * <p>Java has no templates, so the official {@code PseudoRandom} and
 * {@code LowDiscrepancy} template arguments of
 * {@code MakeMCEuropeanEngine<RNG>} are represented by this enum.
 */
public enum RngType {
    PSEUDO_RANDOM {
        @Override
        public boolean allowsErrorEstimate() {
            return PseudoRandom.allowsErrorEstimate;
        }

        @Override
        public InverseCumulativeRsg makeSequenceGenerator(int dimension, long seed) {
            return PseudoRandom.make_sequence_generator(dimension, seed);
        }
    },
    LOW_DISCREPANCY {
        @Override
        public boolean allowsErrorEstimate() {
            return LowDiscrepancy.allowsErrorEstimate;
        }

        @Override
        public InverseCumulativeRsg makeSequenceGenerator(int dimension, long seed) {
            return LowDiscrepancy.make_sequence_generator(dimension, seed);
        }
    };

    public abstract boolean allowsErrorEstimate();

    public abstract InverseCumulativeRsg makeSequenceGenerator(int dimension, long seed);
}
