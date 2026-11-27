package jquant.models.marketmodels.models;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static jquant.math.CommonUtil.QL_REQUIRE;

/**
 * Official QuantLib
 * {@code ql/models/marketmodels/models/volatilityinterpolationspecifierabcd.hpp/.cpp}
 * port (class {@code VolatilityInterpolationSpecifierabcd}).
 */
public class VolatilityInterpolationSpecifierAbcd extends VolatilityInterpolationSpecifier {

    private final int period_;
    private final int offset_;

    private final List<PiecewiseConstantVariance> interpolatedVariances_;
    private final List<PiecewiseConstantVariance> originalVariances_;
    private final List<PiecewiseConstantAbcdVariance> originalABCDVariances_ = new ArrayList<>();
    private final List<PiecewiseConstantAbcdVariance> originalABCDVariancesScaled_ = new ArrayList<>();
    private double lastCapletVol_;
    private final List<Double> timesForSmallRates_;
    private List<Double> scalingFactors_;

    private final int noBigRates_;
    private final int noSmallRates_;

    public VolatilityInterpolationSpecifierAbcd(
            int period,
            int offset,
            List<PiecewiseConstantAbcdVariance> originalVariances,
            List<Double> timesForSmallRates) {
        this(period, offset, originalVariances, timesForSmallRates, 0.0);
    }

    public VolatilityInterpolationSpecifierAbcd(
            int period,
            int offset,
            List<PiecewiseConstantAbcdVariance> originalVariances,
            List<Double> timesForSmallRates,
            double lastCapletVol) {
        period_ = period;
        offset_ = offset;
        noBigRates_ = originalVariances.size();
        noSmallRates_ = timesForSmallRates.size() - 1;
        interpolatedVariances_ = new ArrayList<>(
                Collections.nCopies(noSmallRates_, (PiecewiseConstantVariance) null));
        originalVariances_ = new ArrayList<>(
                Collections.nCopies(noBigRates_, (PiecewiseConstantVariance) null));
        for (PiecewiseConstantAbcdVariance variance : originalVariances) {
            originalABCDVariances_.add(new PiecewiseConstantAbcdVariance(variance));
            originalABCDVariancesScaled_.add(new PiecewiseConstantAbcdVariance(variance));
        }
        lastCapletVol_ = lastCapletVol;
        timesForSmallRates_ = new ArrayList<>(timesForSmallRates);
        scalingFactors_ = new ArrayList<>(Collections.nCopies(noBigRates_, 1.0));

        QL_REQUIRE((noSmallRates_ - offset) / period == noBigRates_,
                "size mismatch in VolatilityInterpolationSpecifierabcd");

        for (int i = 0; i < noBigRates_; ++i)
            for (int j = 0; j < originalVariances.get(i).rateTimes().size(); ++j)
                QL_REQUIRE(originalVariances.get(i).rateTimes().get(j)
                                .equals(timesForSmallRates.get(offset + j * period)),
                        "rate times in variances passed in don't match small times in VolatilityInterpolationSpecifierabcd");

        if (lastCapletVol_ == 0.0)
            lastCapletVol_ = originalABCDVariances_.get(noBigRates_ - 1)
                    .totalVolatility(noBigRates_ - 1);

        // change type of array to PiecewiseConstantVariance for client, from PiecewiseConstantAbcdVariance
        for (int i = 0; i < noBigRates_; ++i)
            originalVariances_.set(i,
                    new PiecewiseConstantAbcdVariance(originalABCDVariances_.get(i)));

        recompute();
    }

    @Override
    public void setScalingFactors(List<Double> scales) {
        QL_REQUIRE(scalingFactors_.size() == scales.size(),
                "inappropriate number of scales passed in to VolatilityInterpolationSpecifierabcd::setScalingFactors ");
        scalingFactors_ = new ArrayList<>(scales);
        recompute();
    }

    @Override
    public void setLastCapletVol(double vol) {
        lastCapletVol_ = vol;
        recompute();
    }

    @Override
    public List<PiecewiseConstantVariance> interpolatedVariances() {
        return interpolatedVariances_;
    }

    @Override
    public List<PiecewiseConstantVariance> originalVariances() {
        return originalVariances_;
    }

    @Override
    public int getPeriod() {
        return period_;
    }

    @Override
    public int getOffset() {
        return offset_;
    }

    @Override
    public int getNoBigRates() {
        return noBigRates_;
    }

    @Override
    public int getNoSmallRates() {
        return noSmallRates_;
    }

    private void recompute() {
        for (int i = 0; i < noBigRates_; ++i) {
            double[] abcd = originalABCDVariances_.get(i).getABCD();
            double a = abcd[0];
            double b = abcd[1];
            double c = abcd[2];
            double d = abcd[3];
            a *= scalingFactors_.get(i);
            b *= scalingFactors_.get(i);
            // c is not scaled
            d *= scalingFactors_.get(i);

            originalABCDVariancesScaled_.set(i,
                    new PiecewiseConstantAbcdVariance(a, b, c, d, i,
                            originalABCDVariances_.get(i).rateTimes()));
        }

        // three cases:
        // before offset,
        // between offset and last big rate,
        // and after last big rate

        // before offset
        {
            double[] abcd = originalABCDVariancesScaled_.get(0).getABCD();
            double a = abcd[0];
            double b = abcd[1];
            double c = abcd[2];
            double d = abcd[3];

            for (int i = 0; i < offset_; ++i)
                interpolatedVariances_.set(i,
                        new PiecewiseConstantAbcdVariance(a, b, c, d, i,
                                timesForSmallRates_));
        }

        // in between rates
        for (int j = 0; j < noBigRates_ - 1; ++j) {
            double[] abcd0 = originalABCDVariancesScaled_.get(j).getABCD();
            double[] abcd1 = originalABCDVariancesScaled_.get(j + 1).getABCD();
            double a0 = abcd0[0], b0 = abcd0[1], c0 = abcd0[2], d0 = abcd0[3];
            double a1 = abcd1[0], b1 = abcd1[1], c1 = abcd1[2], d1 = abcd1[3];
            double a = 0.5 * (a0 + a1);
            double b = 0.5 * (b0 + b1);
            double c = 0.5 * (c0 + c1);
            double d = 0.5 * (d0 + d1);

            for (int i = 0; i < period_; ++i)
                interpolatedVariances_.set(i + j * period_ + offset_,
                        new PiecewiseConstantAbcdVariance(a, b, c, d,
                                i + j * period_, timesForSmallRates_));
        }

        {
            double[] abcd = originalABCDVariancesScaled_.get(noBigRates_ - 1).getABCD();
            double a = abcd[0];
            double b = abcd[1];
            double c = abcd[2];
            double d = abcd[3];

            for (int i = offset_ + (noBigRates_ - 1) * period_; i < noSmallRates_; ++i)
                interpolatedVariances_.set(i,
                        new PiecewiseConstantAbcdVariance(a, b, c, d, i,
                                timesForSmallRates_));

            // very last rate is special as we must match the caplet vol
            double vol = interpolatedVariances_.get(noSmallRates_ - 1)
                    .totalVolatility(noSmallRates_ - 1);

            double scale = lastCapletVol_ / vol;
            a *= scale;
            b *= scale;
            d *= scale;
            interpolatedVariances_.set(noSmallRates_ - 1,
                    new PiecewiseConstantAbcdVariance(a, b, c, d,
                            noSmallRates_ - 1, timesForSmallRates_));
        }
    }
}
