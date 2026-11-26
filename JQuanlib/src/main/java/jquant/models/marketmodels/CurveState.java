package jquant.models.marketmodels;

import java.util.List;

import static jquant.math.CommonUtil.QL_REQUIRE;

/**
 * Official QuantLib {@code ql/models/marketmodels/curvestate.hpp/.cpp} port.
 */
public abstract class CurveState {

    protected int numberOfRates_;
    protected List<Double> rateTimes_;
    protected List<Double> rateTaus_;

    protected CurveState(final List<Double> rateTimes) {
        numberOfRates_ = rateTimes.isEmpty() ? 0 : rateTimes.size() - 1;
        rateTimes_ = rateTimes;
        rateTaus_ = new java.util.ArrayList<>();
        Utilities.checkIncreasingTimesAndCalculateTaus(rateTimes_, rateTaus_);
    }

    /** Inspectors */
    public int numberOfRates() {
        return numberOfRates_;
    }

    public List<Double> rateTimes() {
        return rateTimes_;
    }

    public List<Double> rateTaus() {
        return rateTaus_;
    }

    public abstract double discountRatio(int i, int j);

    public abstract double forwardRate(int i);

    public abstract double coterminalSwapAnnuity(int numeraire, int i);

    public abstract double coterminalSwapRate(int i);

    public abstract double cmSwapAnnuity(int numeraire, int i, int spanningForwards);

    public abstract double cmSwapRate(int i, int spanningForwards);

    public abstract List<Double> forwardRates();

    public abstract List<Double> coterminalSwapRates();

    public abstract List<Double> cmSwapRates(int spanningForwards);

    public double swapRate(int begin, int end) {
        QL_REQUIRE(end > begin, "empty range specified");
        QL_REQUIRE(end <= numberOfRates_, "taus/end mismatch");

        double sum = 0.0;
        for (int i = begin; i < end; ++i)
            sum += rateTaus_.get(i) * discountRatio(i + 1, numberOfRates_);

        return (discountRatio(begin, numberOfRates_) - discountRatio(end, numberOfRates_)) / sum;
    }

    public abstract CurveState copy();

    // helpers

    public static void forwardsFromDiscountRatios(final int firstValidIndex,
                                                  final List<Double> ds,
                                                  final List<Double> taus,
                                                  List<Double> fwds) {
        QL_REQUIRE(taus.size() == fwds.size(),
                "taus.size()!=fwds.size()");
        QL_REQUIRE(ds.size() == fwds.size() + 1,
                "ds.size()!=fwds.size()+1");

        for (int i = firstValidIndex; i < fwds.size(); ++i)
            fwds.set(i, (ds.get(i) - ds.get(i + 1)) / (ds.get(i + 1) * taus.get(i)));
    }

    public static void coterminalFromDiscountRatios(
            final int firstValidIndex,
            final List<Double> discountFactors,
            final List<Double> taus,
            List<Double> cotSwapRates,
            List<Double> cotSwapAnnuities) {
        int nCotSwapRates = cotSwapRates.size();
        QL_REQUIRE(taus.size() == nCotSwapRates,
                "taus.size()!=cotSwapRates.size()");
        QL_REQUIRE(cotSwapAnnuities.size() == nCotSwapRates,
                "cotSwapAnnuities.size()!=cotSwapRates.size()");
        QL_REQUIRE(discountFactors.size() == nCotSwapRates + 1,
                "discountFactors.size()!=cotSwapRates.size()+1");

        cotSwapAnnuities.set(nCotSwapRates - 1,
                taus.get(nCotSwapRates - 1) * discountFactors.get(nCotSwapRates));
        cotSwapRates.set(nCotSwapRates - 1,
                (discountFactors.get(nCotSwapRates - 1) - discountFactors.get(nCotSwapRates))
                        / cotSwapAnnuities.get(nCotSwapRates - 1));

        for (int i = nCotSwapRates - 1; i > firstValidIndex; --i) {
            cotSwapAnnuities.set(i - 1,
                    cotSwapAnnuities.get(i) + taus.get(i - 1) * discountFactors.get(i));
            cotSwapRates.set(i - 1,
                    (discountFactors.get(i - 1) - discountFactors.get(nCotSwapRates))
                            / cotSwapAnnuities.get(i - 1));
        }
    }

    public static void constantMaturityFromDiscountRatios(
            final int spanningForwards,
            final int firstValidIndex,
            final List<Double> ds,
            final List<Double> taus,
            List<Double> constMatSwapRates,
            List<Double> constMatSwapAnnuities) {
        int nConstMatSwapRates = constMatSwapRates.size();
        QL_REQUIRE(taus.size() == nConstMatSwapRates,
                "taus.size()!=nConstMatSwapRates");
        QL_REQUIRE(constMatSwapAnnuities.size() == nConstMatSwapRates,
                "constMatSwapAnnuities.size()!=nConstMatSwapRates");
        QL_REQUIRE(ds.size() == nConstMatSwapRates + 1,
                "ds.size()!=nConstMatSwapRates+1");
        // compute the first cmsrate and cmsannuity
        constMatSwapAnnuities.set(firstValidIndex, 0.0);
        int lastIndex = Math.min(firstValidIndex + spanningForwards, nConstMatSwapRates);
        double annuity = 0.0;
        for (int i = firstValidIndex; i < lastIndex; ++i) {
            annuity += taus.get(i) * ds.get(i + 1);
        }
        constMatSwapAnnuities.set(firstValidIndex, annuity);
        constMatSwapRates.set(firstValidIndex,
                (ds.get(firstValidIndex) - ds.get(lastIndex))
                        / constMatSwapAnnuities.get(firstValidIndex));
        int oldLastIndex = lastIndex;

        // compute all the other cmas rates and cms annuities
        for (int i = firstValidIndex + 1; i < nConstMatSwapRates; ++i) {
            lastIndex = Math.min(i + spanningForwards, nConstMatSwapRates);
            constMatSwapAnnuities.set(i,
                    constMatSwapAnnuities.get(i - 1) - taus.get(i - 1) * ds.get(i));
            if (lastIndex != oldLastIndex)
                constMatSwapAnnuities.set(i,
                        constMatSwapAnnuities.get(i)
                                + taus.get(lastIndex - 1) * ds.get(lastIndex));
            constMatSwapRates.set(i,
                    (ds.get(i) - ds.get(lastIndex))
                            / constMatSwapAnnuities.get(i));
            oldLastIndex = lastIndex;
        }
    }
}
