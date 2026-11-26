package jquant.models.marketmodels.curvestates;

import jquant.models.marketmodels.CurveState;

import java.util.ArrayList;
import java.util.List;

import static jquant.math.CommonUtil.QL_REQUIRE;

/**
 * Official QuantLib {@code ql/models/marketmodels/curvestates/lmmcurvestate.hpp/.cpp} port.
 */
public class LMMCurveState extends CurveState {

    private int first_;
    private List<Double> discRatios_;
    private List<Double> forwardRates_;
    private List<Double> cmSwapRates_;
    private List<Double> cmSwapAnnuities_;
    private List<Double> cotSwapRates_;
    private List<Double> cotAnnuities_;

    private int firstCotAnnuityComped_;

    public LMMCurveState(final List<Double> rateTimes) {
        super(rateTimes);
        first_ = numberOfRates_;
        discRatios_ = new ArrayList<>();
        for (int i = 0; i < numberOfRates_ + 1; ++i)
            discRatios_.add(1.0);
        forwardRates_ = zeroList(numberOfRates_);
        cmSwapRates_ = zeroList(numberOfRates_);
        cmSwapAnnuities_ = filledList(numberOfRates_, rateTaus_.get(numberOfRates_ - 1));
        cotSwapRates_ = zeroList(numberOfRates_);
        cotAnnuities_ = filledList(numberOfRates_, rateTaus_.get(numberOfRates_ - 1));
        firstCotAnnuityComped_ = numberOfRates_;
    }

    public void setOnForwardRates(final List<Double> rates,
                                  int firstValidIndex) {
        QL_REQUIRE(rates.size() == numberOfRates_,
                "rates mismatch: " +
                        numberOfRates_ + " required, " +
                        rates.size() + " provided");
        QL_REQUIRE(firstValidIndex < numberOfRates_,
                "first valid index must be less than " + numberOfRates_ + ": " +
                        firstValidIndex + " not allowed");

        // first copy input...
        first_ = firstValidIndex;
        for (int i = first_; i < numberOfRates_; ++i)
            forwardRates_.set(i, rates.get(i));
        // ...then calculate discount ratios

        for (int i = first_; i < numberOfRates_; ++i)
            discRatios_.set(i + 1,
                    discRatios_.get(i) / (1.0 + forwardRates_.get(i) * rateTaus_.get(i)));

        // lazy evaluation of:
        // - coterminal swap rates/annuities
        // - constant maturity swap rates/annuities

        firstCotAnnuityComped_ = numberOfRates_;
    }

    public void setOnForwardRates(final List<Double> rates) {
        setOnForwardRates(rates, 0);
    }

    public void setOnDiscountRatios(final List<Double> discRatios,
                                    int firstValidIndex) {
        QL_REQUIRE(discRatios.size() == numberOfRates_ + 1,
                "too many discount ratios: " +
                        (numberOfRates_ + 1) + " required, " +
                        discRatios.size() + " provided");
        QL_REQUIRE(firstValidIndex < numberOfRates_,
                "first valid index must be less than " + (numberOfRates_ + 1) + ": " +
                        firstValidIndex + " not allowed");

        // first copy input...
        first_ = firstValidIndex;
        for (int i = first_; i < numberOfRates_ + 1; ++i)
            this.discRatios_.set(i, discRatios.get(i));
        // ...then calculate forwards

        for (int i = first_; i < numberOfRates_; ++i)
            forwardRates_.set(i,
                    (this.discRatios_.get(i) / this.discRatios_.get(i + 1) - 1.0)
                            / rateTaus_.get(i));

        firstCotAnnuityComped_ = numberOfRates_;
    }

    public void setOnDiscountRatios(final List<Double> discRatios) {
        setOnDiscountRatios(discRatios, 0);
    }

    @Override
    public double discountRatio(int i, int j) {
        QL_REQUIRE(first_ < numberOfRates_, "curve state not initialized yet");
        QL_REQUIRE(Math.min(i, j) >= first_, "invalid index");
        QL_REQUIRE(Math.max(i, j) <= numberOfRates_, "invalid index");
        return discRatios_.get(i) / discRatios_.get(j);
    }

    @Override
    public double forwardRate(int i) {
        QL_REQUIRE(first_ < numberOfRates_, "curve state not initialized yet");
        QL_REQUIRE(i >= first_ && i <= numberOfRates_, "invalid index");
        return forwardRates_.get(i);
    }

    @Override
    public double coterminalSwapAnnuity(int numeraire, int i) {
        QL_REQUIRE(first_ < numberOfRates_, "curve state not initialized yet");
        QL_REQUIRE(numeraire >= first_ && numeraire <= numberOfRates_,
                "invalid numeraire");
        QL_REQUIRE(i >= first_ && i <= numberOfRates_, "invalid index");

        if (firstCotAnnuityComped_ <= i)
            return cotAnnuities_.get(i) / discRatios_.get(numeraire);

        if (firstCotAnnuityComped_ == numberOfRates_) {
            cotAnnuities_.set(numberOfRates_ - 1,
                    rateTaus_.get(numberOfRates_ - 1) * discRatios_.get(numberOfRates_));
            --firstCotAnnuityComped_;
        }

        for (int j = firstCotAnnuityComped_ - 1; j >= i; --j)
            cotAnnuities_.set(j,
                    cotAnnuities_.get(j + 1) + rateTaus_.get(j) * discRatios_.get(j + 1));

        firstCotAnnuityComped_ = i;

        return cotAnnuities_.get(i) / discRatios_.get(numeraire);
    }

    @Override
    public double coterminalSwapRate(int i) {
        QL_REQUIRE(first_ < numberOfRates_, "curve state not initialized yet");
        QL_REQUIRE(i >= first_ && i <= numberOfRates_, "invalid index");

        return (discRatios_.get(i) / discRatios_.get(numberOfRates_) - 1.0)
                / coterminalSwapAnnuity(numberOfRates_, i);
    }

    @Override
    public double cmSwapAnnuity(int numeraire, int i, int spanningForwards) {
        QL_REQUIRE(first_ < numberOfRates_, "curve state not initialized yet");
        QL_REQUIRE(numeraire >= first_ && numeraire <= numberOfRates_,
                "invalid numeraire");
        QL_REQUIRE(i >= first_ && i <= numberOfRates_, "invalid index");

        // consider lazy evaluation here
        constantMaturityFromDiscountRatios(spanningForwards, first_,
                discRatios_, rateTaus_,
                cmSwapRates_, cmSwapAnnuities_);
        return cmSwapAnnuities_.get(i) / discRatios_.get(numeraire);
    }

    @Override
    public double cmSwapRate(int i, int spanningForwards) {
        QL_REQUIRE(first_ < numberOfRates_, "curve state not initialized yet");
        QL_REQUIRE(i >= first_ && i <= numberOfRates_, "invalid index");

        // consider lazy evaluation here
        constantMaturityFromDiscountRatios(spanningForwards, first_,
                discRatios_, rateTaus_,
                cmSwapRates_, cmSwapAnnuities_);
        return cmSwapRates_.get(i);
    }

    @Override
    public List<Double> forwardRates() {
        QL_REQUIRE(first_ < numberOfRates_, "curve state not initialized yet");
        return forwardRates_;
    }

    @Override
    public List<Double> coterminalSwapRates() {
        QL_REQUIRE(first_ < numberOfRates_, "curve state not initialized yet");
        coterminalFromDiscountRatios(first_,
                discRatios_, rateTaus_,
                cotSwapRates_, cotAnnuities_);
        return cotSwapRates_;
    }

    @Override
    public List<Double> cmSwapRates(int spanningForwards) {
        QL_REQUIRE(first_ < numberOfRates_, "curve state not initialized yet");
        constantMaturityFromDiscountRatios(spanningForwards, first_,
                discRatios_, rateTaus_,
                cmSwapRates_, cmSwapAnnuities_);
        return cmSwapRates_;
    }

    @Override
    public LMMCurveState copy() {
        LMMCurveState result = new LMMCurveState(rateTimes_);
        result.first_ = first_;
        result.discRatios_ = new ArrayList<>(discRatios_);
        result.forwardRates_ = new ArrayList<>(forwardRates_);
        result.cmSwapRates_ = new ArrayList<>(cmSwapRates_);
        result.cmSwapAnnuities_ = new ArrayList<>(cmSwapAnnuities_);
        result.cotSwapRates_ = new ArrayList<>(cotSwapRates_);
        result.cotAnnuities_ = new ArrayList<>(cotAnnuities_);
        result.firstCotAnnuityComped_ = firstCotAnnuityComped_;
        return result;
    }

    private static List<Double> zeroList(int n) {
        List<Double> result = new ArrayList<>();
        for (int i = 0; i < n; ++i)
            result.add(0.0);
        return result;
    }

    private static List<Double> filledList(int n, double value) {
        List<Double> result = new ArrayList<>();
        for (int i = 0; i < n; ++i)
            result.add(value);
        return result;
    }
}
