package jquant.models.marketmodels.pathwisegreeks;

/**
 * Official QuantLib
 * {@code ql/models/marketmodels/pathwisegreeks/bumpinstrumentjacobian.hpp}.
 *
 * <p>Only the nested {@link Cap} struct used by
 * {@code MarketModelTest::testPathwiseVegas} is ported; the rest of
 * {@code VolatilityBumpInstrumentJacobian} is not required by that test.
 */
public class VolatilityBumpInstrumentJacobian {

    public static class Cap {
        public int startIndex_;
        public int endIndex_;
        public double strike_;

        public Cap() {
        }

        public Cap(int startIndex, int endIndex, double strike) {
            startIndex_ = startIndex;
            endIndex_ = endIndex;
            strike_ = strike;
        }
    }
}
