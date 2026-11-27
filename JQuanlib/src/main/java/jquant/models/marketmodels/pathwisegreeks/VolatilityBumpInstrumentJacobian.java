package jquant.models.marketmodels.pathwisegreeks;

import jquant.math.Matrix;

import java.util.ArrayList;
import java.util.List;

import static jquant.math.CommonUtil.QL_REQUIRE;

/**
 * Official QuantLib
 * {@code ql/models/marketmodels/pathwisegreeks/bumpinstrumentjacobian.hpp/.cpp}
 * port.
 *
 * <p>Gives the derivative of each instrument's implied volatility with respect
 * to each bump in a {@link VegaBumpCollection}.
 *
 * <p>This is tested in {@code MarketModelTest::testPathwiseMarketVegas}.
 */
public class VolatilityBumpInstrumentJacobian {

    public static class Swaption {
        public int startIndex_;
        public int endIndex_;

        public Swaption() {
        }

        public Swaption(int startIndex, int endIndex) {
            startIndex_ = startIndex;
            endIndex_ = endIndex;
        }
    }

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

    private final VegaBumpCollection bumps_;
    private final List<Swaption> swaptions_;
    private final List<Cap> caps_;
    private final boolean[] computed_;
    private boolean allComputed_;
    private final List<List<Double>> derivatives_;

    private final List<List<Double>> onePercentBumps_;
    private final Matrix bumpMatrix_;

    public VolatilityBumpInstrumentJacobian(final VegaBumpCollection bumps,
                                            final List<Swaption> swaptions,
                                            final List<Cap> caps) {
        bumps_ = bumps;
        swaptions_ = new ArrayList<>(swaptions);
        caps_ = new ArrayList<>(caps);
        int numberInstruments = swaptions_.size() + caps_.size();
        computed_ = new boolean[numberInstruments];

        derivatives_ = new ArrayList<>();
        onePercentBumps_ = new ArrayList<>();
        for (int i = 0; i < numberInstruments; ++i) {
            List<Double> derivativeRow = new ArrayList<>();
            for (int k = 0; k < bumps.numberBumps(); ++k)
                derivativeRow.add(0.0);
            derivatives_.add(derivativeRow);
            onePercentBumps_.add(new ArrayList<>(derivativeRow));
        }

        bumpMatrix_ = new Matrix(numberInstruments, bumps_.numberBumps(), 0.0);
        allComputed_ = false;
    }

    public VegaBumpCollection getInputBumps() {
        return bumps_;
    }

    public List<Double> derivativesVolatility(int j) {
        QL_REQUIRE(j < swaptions_.size() + caps_.size(),
                "too high index passed to " +
                        "VolatilityBumpInstrumentJacobian::derivativesVolatility");

        if (computed_[j])
            return derivatives_.get(j);

        // official resizes both rows to the number of bumps
        while (derivatives_.get(j).size() < bumps_.numberBumps())
            derivatives_.get(j).add(0.0);
        while (onePercentBumps_.get(j).size() < bumps_.numberBumps())
            onePercentBumps_.get(j).add(0.0);

        double sizesq = 0.0;
        computed_[j] = true;

        int initj = j;

        if (j < swaptions_.size()) { // ok its a swaption
            SwaptionPseudoDerivative thisPseudo =
                    new SwaptionPseudoDerivative(bumps_.associatedModel(),
                            swaptions_.get(j).startIndex_,
                            swaptions_.get(j).endIndex_);

            for (int k = 0; k < bumps_.numberBumps(); ++k) {
                double v = 0.0;

                for (int i = bumps_.allBumps().get(k).stepBegin();
                     i < bumps_.allBumps().get(k).stepEnd(); ++i) {
                    final Matrix fullDerivative = thisPseudo.volatilityDerivative(i);
                    for (int f = bumps_.allBumps().get(k).factorBegin();
                         f < bumps_.allBumps().get(k).factorEnd(); ++f)
                        for (int r = bumps_.allBumps().get(k).rateBegin();
                             r < bumps_.allBumps().get(k).rateEnd(); ++r)
                            v += fullDerivative.get(r, f);
                }

                derivatives_.get(j).set(k, v);
                sizesq += v * v;
            }
        } else { // its a cap
            j -= swaptions_.size();

            // first df shouldn't make any difference
            CapPseudoDerivative thisPseudo =
                    new CapPseudoDerivative(bumps_.associatedModel(),
                            caps_.get(j).strike_,
                            caps_.get(j).startIndex_,
                            caps_.get(j).endIndex_, 1.0);

            for (int k = 0; k < bumps_.numberBumps(); ++k) {
                double v = 0.0;

                for (int i = bumps_.allBumps().get(k).stepBegin();
                     i < bumps_.allBumps().get(k).stepEnd(); ++i) {
                    final Matrix fullDerivative = thisPseudo.volatilityDerivative(i);
                    for (int f = bumps_.allBumps().get(k).factorBegin();
                         f < bumps_.allBumps().get(k).factorEnd(); ++f)
                        for (int r = bumps_.allBumps().get(k).rateBegin();
                             r < bumps_.allBumps().get(k).rateEnd(); ++r)
                            v += fullDerivative.get(r, f);
                }

                sizesq += v * v;

                derivatives_.get(initj).set(k, v);
            }
        }

        for (int k = 0; k < bumps_.numberBumps(); ++k) {
            double value = 0.01 * derivatives_.get(initj).get(k) / sizesq;
            onePercentBumps_.get(initj).set(k, value);
            bumpMatrix_.set(initj, k, value);
        }

        return derivatives_.get(initj);
    }

    /** Vector of smallest size that changes instrument implied vol by 1
     * percent i.e. 0.01 v / &lt;v,v&gt; with v the result of
     * {@link #derivativesVolatility(int)}.
     */
    public List<Double> onePercentBump(int j) {
        derivativesVolatility(j);

        return onePercentBumps_.get(j);
    }

    public Matrix getAllOnePercentBumps() {
        if (!allComputed_)
            for (int i = 0; i < swaptions_.size() + caps_.size(); ++i)
                derivativesVolatility(i);

        allComputed_ = true;

        return bumpMatrix_;
    }
}
