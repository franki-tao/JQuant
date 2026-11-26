package jquant.models.marketmodels.curvestates;

import jquant.models.marketmodels.CurveState;

import java.util.ArrayList;
import java.util.List;

import static jquant.math.CommonUtil.QL_REQUIRE;

/**
 * Official QuantLib {@code ql/models/marketmodels/curvestates/cmswapcurvestate.hpp/.cpp} port.
 */
public class CMSwapCurveState extends CurveState {

    private int spanningFwds_;
    private int first_;
    private List<Double> discRatios_;
    private List<Double> forwardRates_;
    // fixed number of spanning forwards
    private List<Double> cmSwapRates_;
    private List<Double> cmSwapAnnuities_;
    // irregular number of spanning forwards
    private List<Double> irrCMSwapRates_;
    private List<Double> irrCMSwapAnnuities_;
    private List<Double> cotSwapRates_;
    private List<Double> cotAnnuities_;

    public CMSwapCurveState(final List<Double> rateTimes,
                            int spanningForwards) {
        super(rateTimes);
        spanningFwds_ = spanningForwards;
        first_ = numberOfRates_;
        discRatios_ = new ArrayList<>();
        for (int i = 0; i < numberOfRates_ + 1; ++i)
            discRatios_.add(1.0);
        forwardRates_ = zeroList(numberOfRates_);
        cmSwapRates_ = zeroList(numberOfRates_);
        cmSwapAnnuities_ = filledList(numberOfRates_, rateTaus_.get(numberOfRates_ - 1));
        irrCMSwapRates_ = zeroList(numberOfRates_);
        irrCMSwapAnnuities_ = filledList(numberOfRates_, rateTaus_.get(numberOfRates_ - 1));
        cotSwapRates_ = zeroList(numberOfRates_);
        cotAnnuities_ = filledList(numberOfRates_, rateTaus_.get(numberOfRates_ - 1));
    }

    public void setOnCMSwapRates(final List<Double> rates,
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
            cmSwapRates_.set(i, rates.get(i));
        // ...then calculate discount ratios and annuities

        // assume i+1 and do i
        int oldAnnuityEndIndex = numberOfRates_;
        for (int i = numberOfRates_ - 1; i > first_; --i) {
            // formula 6.1 Joshi Liesch
            int endIndex = Math.min(i + spanningFwds_, numberOfRates_);
            int annuityEndIndex = Math.min(i + spanningFwds_ - 1, numberOfRates_);

            discRatios_.set(i,
                    discRatios_.get(endIndex) +
                            cmSwapRates_.get(i) * cmSwapAnnuities_.get(i));
            cmSwapAnnuities_.set(i - 1,
                    cmSwapAnnuities_.get(i)
                            + discRatios_.get(i) * rateTaus_.get(i - 1));

            if (annuityEndIndex < oldAnnuityEndIndex)
                cmSwapAnnuities_.set(i - 1,
                        cmSwapAnnuities_.get(i - 1)
                                - discRatios_.get(oldAnnuityEndIndex)
                                * rateTaus_.get(oldAnnuityEndIndex - 1));

            oldAnnuityEndIndex = annuityEndIndex;
        }
        int endIndex = Math.min(first_ + spanningFwds_, numberOfRates_);

        discRatios_.set(first_,
                discRatios_.get(endIndex) +
                        cmSwapRates_.get(first_) * cmSwapAnnuities_.get(first_));
    }

    public void setOnCMSwapRates(final List<Double> rates) {
        setOnCMSwapRates(rates, 0);
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
        forwardsFromDiscountRatios(first_, discRatios_, rateTaus_, forwardRates_);
        return forwardRates_.get(i);
    }

    @Override
    public double coterminalSwapAnnuity(int numeraire, int i) {
        QL_REQUIRE(first_ < numberOfRates_, "curve state not initialized yet");
        QL_REQUIRE(numeraire >= first_ && numeraire <= numberOfRates_,
                "invalid numeraire");
        QL_REQUIRE(i >= first_ && i <= numberOfRates_, "invalid index");
        coterminalFromDiscountRatios(first_,
                discRatios_, rateTaus_,
                cotSwapRates_, cotAnnuities_);
        return cotAnnuities_.get(i) / discRatios_.get(numeraire);
    }

    @Override
    public double coterminalSwapRate(int i) {
        QL_REQUIRE(first_ < numberOfRates_, "curve state not initialized yet");
        QL_REQUIRE(i >= first_ && i <= numberOfRates_, "invalid index");
        coterminalFromDiscountRatios(first_,
                discRatios_, rateTaus_,
                cotSwapRates_, cotAnnuities_);
        return cotSwapRates_.get(i);
    }

    @Override
    public double cmSwapAnnuity(int numeraire, int i, int spanningForwards) {
        QL_REQUIRE(first_ < numberOfRates_, "curve state not initialized yet");
        QL_REQUIRE(numeraire >= first_ && numeraire <= numberOfRates_,
                "invalid numeraire");
        QL_REQUIRE(i >= first_ && i <= numberOfRates_, "invalid index");
        if (spanningForwards == spanningFwds_)
            return cmSwapAnnuities_.get(i) / discRatios_.get(numeraire);
        else {
            constantMaturityFromDiscountRatios(spanningForwards, first_,
                    discRatios_, rateTaus_,
                    irrCMSwapRates_,
                    irrCMSwapAnnuities_);
            return irrCMSwapAnnuities_.get(i) / discRatios_.get(numeraire);
        }
    }

    @Override
    public double cmSwapRate(int i, int spanningForwards) {
        QL_REQUIRE(first_ < numberOfRates_, "curve state not initialized yet");
        QL_REQUIRE(i >= first_ && i <= numberOfRates_, "invalid index");
        if (spanningForwards == spanningFwds_)
            return cmSwapRates_.get(i);
        else {
            constantMaturityFromDiscountRatios(spanningForwards, first_,
                    discRatios_, rateTaus_,
                    irrCMSwapRates_,
                    irrCMSwapAnnuities_);
            return irrCMSwapRates_.get(i);
        }
    }

    @Override
    public List<Double> forwardRates() {
        QL_REQUIRE(first_ < numberOfRates_, "curve state not initialized yet");
        forwardsFromDiscountRatios(first_, discRatios_, rateTaus_, forwardRates_);
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
        if (spanningForwards == spanningFwds_)
            return cmSwapRates_;
        else {
            constantMaturityFromDiscountRatios(spanningForwards, first_,
                    discRatios_, rateTaus_,
                    irrCMSwapRates_, irrCMSwapAnnuities_);
            return irrCMSwapRates_;
        }
    }

    @Override
    public CMSwapCurveState copy() {
        CMSwapCurveState result = new CMSwapCurveState(rateTimes_, spanningFwds_);
        result.first_ = first_;
        result.discRatios_ = new ArrayList<>(discRatios_);
        result.forwardRates_ = new ArrayList<>(forwardRates_);
        result.cmSwapRates_ = new ArrayList<>(cmSwapRates_);
        result.cmSwapAnnuities_ = new ArrayList<>(cmSwapAnnuities_);
        result.irrCMSwapRates_ = new ArrayList<>(irrCMSwapRates_);
        result.irrCMSwapAnnuities_ = new ArrayList<>(irrCMSwapAnnuities_);
        result.cotSwapRates_ = new ArrayList<>(cotSwapRates_);
        result.cotAnnuities_ = new ArrayList<>(cotAnnuities_);
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
