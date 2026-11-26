package jquant.termstructures.volatility.swaption;

import jquant.CashFlow;
import jquant.Handle;
import jquant.Quote;
import jquant.cashflows.CashFlows;
import jquant.cashflows.CmsCouponPricer;
import jquant.cashflows.MeanRevertingPricer;
import jquant.indexes.IborIndex;
import jquant.indexes.SwapIndex;
import jquant.instruments.MakeCms;
import jquant.instruments.Swap;
import jquant.math.Array;
import jquant.math.Matrix;
import jquant.patterns.LazyObject;
import jquant.quotes.SimpleQuote;
import jquant.termstructures.YieldTermStructure;
import jquant.time.Date;
import jquant.time.Period;
import jquant.time.TimeUnit;
import jquant.time.TimeUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static jquant.math.CommonUtil.QL_REQUIRE;
import static jquant.math.MathUtils.NULL_REAL;

/**
 * Set of CMS quotes.
 *
 * <p>Ported from QuantLib {@code ql/termstructures/volatility/swaption/cmsmarket.hpp}
 * and {@code cmsmarket.cpp} at SHA {@code cf74c9f}. The market/model split of
 * the spot and forward CMS leg NPVs, the weighted error surface and the
 * {@link #browse()} diagnostic matrix are those of the official class.
 *
 * <p>Registration follows the official {@code registerWith(Handle)} calls:
 * Java registration uses {@link Handle#get()} (the observable {@code Link}),
 * never {@code currentLink()} (the current surface).
 */
public class CmsMarket extends LazyObject {

    //! \name LazyObject interface
    //@{
    @Override
    public void update() {
        super.update();
    }
    //@}

    public CmsMarket(List<Period> swapLengths,
                     List<SwapIndex> swapIndexes,
                     IborIndex iborIndex,
                     List<List<Handle<Quote>>> bidAskSpreads,
                     List<CmsCouponPricer> pricers,
                     Handle<YieldTermStructure> discountingTS) {
        swapLengths_ = new ArrayList<>(swapLengths);
        swapIndexes_ = new ArrayList<>(swapIndexes);
        iborIndex_ = iborIndex;
        bidAskSpreads_ = new ArrayList<>(bidAskSpreads.size());
        for (List<Handle<Quote>> row : bidAskSpreads)
            bidAskSpreads_.add(new ArrayList<>(row));
        pricers_ = new ArrayList<>(pricers);
        discTS_ = discountingTS;

        nExercise_ = swapLengths_.size();
        nSwapIndexes_ = swapIndexes_.size();
        swapTenors_ = new ArrayList<>(nSwapIndexes_);

        spotFloatLegNPV_ = new Matrix(nExercise_, nSwapIndexes_, 0.0);
        spotFloatLegBPS_ = new Matrix(nExercise_, nSwapIndexes_, 0.0);

        mktBidSpreads_ = new Matrix(nExercise_, nSwapIndexes_, 0.0);
        mktAskSpreads_ = new Matrix(nExercise_, nSwapIndexes_, 0.0);

        mktSpreads_ = new Matrix(nExercise_, nSwapIndexes_, 0.0);
        mdlSpreads_ = new Matrix(nExercise_, nSwapIndexes_, 0.0);
        errSpreads_ = new Matrix(nExercise_, nSwapIndexes_, 0.0);

        mktSpotCmsLegNPV_ = new Matrix(nExercise_, nSwapIndexes_, 0.0);
        mdlSpotCmsLegNPV_ = new Matrix(nExercise_, nSwapIndexes_, 0.0);
        errSpotCmsLegNPV_ = new Matrix(nExercise_, nSwapIndexes_, 0.0);

        mktFwdCmsLegNPV_ = new Matrix(nExercise_, nSwapIndexes_, 0.0);
        mdlFwdCmsLegNPV_ = new Matrix(nExercise_, nSwapIndexes_, 0.0);
        errFwdCmsLegNPV_ = new Matrix(nExercise_, nSwapIndexes_, 0.0);

        spotSwaps_ = new ArrayList<>(nExercise_);
        fwdSwaps_ = new ArrayList<>(nExercise_);

        QL_REQUIRE(2 * nSwapIndexes_ == bidAskSpreads.get(0).size(),
                "2*nSwapIndexes_ (" + 2 * nSwapIndexes_
                        + ") != bidAskSpreads columns() ("
                        + bidAskSpreads.get(0).size() + ")");
        QL_REQUIRE(nExercise_ == bidAskSpreads.size(),
                "nExercise_ (" + nExercise_ + ") != bidAskSpreads rows() ("
                        + bidAskSpreads.size() + ")");
        QL_REQUIRE(nSwapIndexes_ == pricers.size(),
                "nSwapIndexes_ (" + nSwapIndexes_ + ") != pricers ("
                        + pricers_.size() + ")");

        for (int j = 0; j < nSwapIndexes_; ++j) {
            swapTenors_.add(swapIndexes_.get(j).tenor());
            // pricers
            registerWith(pricers_.get(j));
            for (int i = 0; i < nExercise_; ++i) {
                // market Spread
                registerWith(bidAskSpreads_.get(i).get(j * 2).get());
                registerWith(bidAskSpreads_.get(i).get(j * 2 + 1).get());
            }
        }

        Period start = new Period(0, TimeUnit.YEARS);
        for (int i = 0; i < nExercise_; ++i) {
            if (i > 0)
                start = swapLengths_.get(i - 1).clone();
            List<Swap> spotRow = new ArrayList<>(nSwapIndexes_);
            List<Swap> fwdRow = new ArrayList<>(nSwapIndexes_);
            for (int j = 0; j < nSwapIndexes_; ++j) {
                // never evaluate the spot swap, only its ibor floating leg
                spotRow.add(new MakeCms(swapLengths_.get(i),
                        swapIndexes_.get(j),
                        iborIndex_).makeSwap());
                fwdRow.add(new MakeCms(TimeUtils.subtract(swapLengths_.get(i), start),
                        swapIndexes_.get(j),
                        iborIndex_)
                        .withForwardStart(start)
                        .withCmsCouponPricer(pricers_.get(j))
                        .withDiscountingTermStructure(discTS_)
                        .makeSwap());
            }
            spotSwaps_.add(spotRow);
            fwdSwaps_.add(fwdRow);
        }
        // probably useless
        performCalculations();
    }

    // called during calibration procedure
    public void reprice(Handle<SwaptionVolatilityStructure> volStructure,
                        double meanReversion) {
        Handle<Quote> meanReversionQuote =
                new Handle<>(new SimpleQuote(meanReversion), true);
        for (int j = 0; j < nSwapIndexes_; ++j) {
            // set new volatility structure and new mean reversion
            pricers_.get(j).setSwaptionVolatility(volStructure);
            if (meanReversion != NULL_REAL) {
                QL_REQUIRE(pricers_.get(j) instanceof MeanRevertingPricer,
                        "mean reverting pricer required at index " + j);
                ((MeanRevertingPricer) pricers_.get(j))
                        .setMeanReversion(meanReversionQuote);
            }
        }
        performCalculations();
    }

    // inspectors ...
    public List<Period> swapTenors() {
        return Collections.unmodifiableList(swapTenors_);
    }

    public List<Period> swapLengths() {
        return Collections.unmodifiableList(swapLengths_);
    }

    public Matrix impliedCmsSpreads() {
        return mdlSpreads_;
    }

    public Matrix spreadErrors() {
        return errSpreads_;
    }

    public Matrix browse() {
        calculate();
        Matrix result = new Matrix(nExercise_ * nSwapIndexes_, 14, 0.0);
        for (int j = 0; j < nSwapIndexes_; ++j) {
            for (int i = 0; i < nExercise_; ++i) {
                result.set(j * nExercise_ + i, 0, swapTenors_.get(j).length());
                result.set(j * nExercise_ + i, 1, swapLengths_.get(i).length());

                // Spreads
                result.set(j * nExercise_ + i, 2, mktBidSpreads_.get(i, j) * 10000);
                result.set(j * nExercise_ + i, 3, mktAskSpreads_.get(i, j) * 10000);
                result.set(j * nExercise_ + i, 4, mktSpreads_.get(i, j) * 10000);
                result.set(j * nExercise_ + i, 5, mdlSpreads_.get(i, j) * 10000);
                result.set(j * nExercise_ + i, 6, errSpreads_.get(i, j) * 10000);
                if (mdlSpreads_.get(i, j) > mktAskSpreads_.get(i, j))
                    result.set(j * nExercise_ + i, 7,
                            (mdlSpreads_.get(i, j) - mktAskSpreads_.get(i, j)) * 10000);
                else if (mdlSpreads_.get(i, j) < mktBidSpreads_.get(i, j))
                    result.set(j * nExercise_ + i, 7,
                            (mktBidSpreads_.get(i, j) - mdlSpreads_.get(i, j)) * 10000);
                else
                    result.set(j * nExercise_ + i, 7, 0.0);

                // spot CMS Leg NPVs
                result.set(j * nExercise_ + i, 8, mktSpotCmsLegNPV_.get(i, j));
                result.set(j * nExercise_ + i, 9, mdlSpotCmsLegNPV_.get(i, j));
                result.set(j * nExercise_ + i, 10, errSpotCmsLegNPV_.get(i, j));

                // forward CMS Leg NPVs
                result.set(j * nExercise_ + i, 11, mktFwdCmsLegNPV_.get(i, j));
                result.set(j * nExercise_ + i, 12, mdlFwdCmsLegNPV_.get(i, j));
                result.set(j * nExercise_ + i, 13, errFwdCmsLegNPV_.get(i, j));
            }
        }
        return result;
    }

    // cms market calibration methods (they haven't Lazy behaviour)
    public double weightedSpreadError(Matrix weights) {
        performCalculations();
        return weightedMean(errSpreads_, weights);
    }

    public double weightedSpotNpvError(Matrix weights) {
        performCalculations();
        return weightedMean(errSpotCmsLegNPV_, weights);
    }

    public double weightedFwdNpvError(Matrix weights) {
        performCalculations();
        return weightedMean(errFwdCmsLegNPV_, weights);
    }

    // array of errors to be used by Levenberg-Marquardt optimization
    public Array weightedSpreadErrors(Matrix weights) {
        performCalculations();
        return weightedMeans(errSpreads_, weights);
    }

    public Array weightedSpotNpvErrors(Matrix weights) {
        performCalculations();
        return weightedMeans(errSpotCmsLegNPV_, weights);
    }

    public Array weightedFwdNpvErrors(Matrix weights) {
        performCalculations();
        return weightedMeans(errFwdCmsLegNPV_, weights);
    }

    @Override
    protected void performCalculations() {
        for (int j = 0; j < nSwapIndexes_; ++j) {
            double mktPrevPart = 0.0, mdlPrevPart = 0.0;
            for (int i = 0; i < nExercise_; ++i) {

                // **** market

                mktBidSpreads_.set(i, j,
                        bidAskSpreads_.get(i).get(j * 2).currentLink().value());
                mktAskSpreads_.set(i, j,
                        bidAskSpreads_.get(i).get(j * 2 + 1).currentLink().value());
                mktSpreads_.set(i, j,
                        (mktBidSpreads_.get(i, j) + mktAskSpreads_.get(i, j)) / 2.0);

                List<CashFlow> spotFloatLeg =
                        spotSwaps_.get(i).get(j).leg(1);
                YieldTermStructure curve = discTS_.currentLink();
                spotFloatLegNPV_.set(i, j,
                        CashFlows.npv(spotFloatLeg,
                                curve,
                                false, curve.referenceDate().copy(), new Date()));
                spotFloatLegBPS_.set(i, j,
                        CashFlows.bps(spotFloatLeg,
                                curve,
                                false, curve.referenceDate().copy(), new Date()));

                // imply the spot CMS leg NPV from the spot ibor floating leg NPV
                mktSpotCmsLegNPV_.set(i, j,
                        -(spotFloatLegNPV_.get(i, j)
                                + spotFloatLegBPS_.get(i, j)
                                * mktSpreads_.get(i, j) / 1e-4));
                // fwd CMS legs can be computed as differences between spot legs
                mktFwdCmsLegNPV_.set(i, j,
                        mktSpotCmsLegNPV_.get(i, j) - mktPrevPart);
                mktPrevPart = mktSpotCmsLegNPV_.get(i, j);

                // **** model

                // calculate the forward swap (the time consuming part)
                mdlFwdCmsLegNPV_.set(i, j, fwdSwaps_.get(i).get(j).legNPV(0));
                errFwdCmsLegNPV_.set(i, j,
                        mdlFwdCmsLegNPV_.get(i, j) - mktFwdCmsLegNPV_.get(i, j));

                // spot CMS legs can be computed as incremental sum of forward legs
                mdlSpotCmsLegNPV_.set(i, j,
                        mdlPrevPart + mdlFwdCmsLegNPV_.get(i, j));
                mdlPrevPart = mdlSpotCmsLegNPV_.get(i, j);
                errSpotCmsLegNPV_.set(i, j,
                        mdlSpotCmsLegNPV_.get(i, j) - mktSpotCmsLegNPV_.get(i, j));

                // equilibriums spread over ibor leg
                double npv = spotFloatLegNPV_.get(i, j)
                        + mdlSpotCmsLegNPV_.get(i, j);
                mdlSpreads_.set(i, j, -npv / spotFloatLegBPS_.get(i, j) * 1e-4);
                errSpreads_.set(i, j, mdlSpreads_.get(i, j) - mktSpreads_.get(i, j));
            }
        }
    }

    private double weightedMean(Matrix var, Matrix w) {
        double mean = 0.0;
        for (int i = 0; i < nExercise_; ++i) {
            for (int j = 0; j < nSwapIndexes_; ++j) {
                mean += w.get(i, j) * var.get(i, j) * var.get(i, j);
            }
        }
        mean = Math.sqrt(mean / (nExercise_ * nSwapIndexes_));
        return mean;
    }

    private Array weightedMeans(Matrix var, Matrix w) {
        Array weightedVars = new Array(nExercise_ * nSwapIndexes_);
        for (int i = 0; i < nExercise_; ++i) {
            for (int j = 0; j < nSwapIndexes_; ++j) {
                weightedVars.set(i * nSwapIndexes_ + j,
                        Math.sqrt(w.get(i, j)) * var.get(i, j));
            }
        }
        return weightedVars;
    }

    private final List<Period> swapLengths_;
    private final List<SwapIndex> swapIndexes_;
    private final IborIndex iborIndex_;
    private final List<List<Handle<Quote>>> bidAskSpreads_;
    private final List<CmsCouponPricer> pricers_;
    private final Handle<YieldTermStructure> discTS_;

    private final int nExercise_;
    private final int nSwapIndexes_;
    private final List<Period> swapTenors_;
    private final Matrix spotFloatLegNPV_, spotFloatLegBPS_;

    // market spreads
    private final Matrix mktBidSpreads_, mktAskSpreads_, mktSpreads_;
    // model (mid) spreads
    private final Matrix mdlSpreads_;
    // differences between market and model mid spreads
    private final Matrix errSpreads_;

    // market mid prices of spot starting Cms Leg
    private final Matrix mktSpotCmsLegNPV_;
    // model mid prices of spot starting Cms Leg
    private final Matrix mdlSpotCmsLegNPV_;
    // Differences between mdlSpotCmsLegNPV_ and mktSpotCmsLegNPV_
    private final Matrix errSpotCmsLegNPV_;

    // market mid prices of forward starting Cms Leg
    private final Matrix mktFwdCmsLegNPV_;
    // model mid prices of forward starting Cms Leg
    private final Matrix mdlFwdCmsLegNPV_;
    // Differences between mdlFwdCmsLegNPV_ and mktFwdCmsLegNPV_
    private final Matrix errFwdCmsLegNPV_;

    private final List<List<Swap>> spotSwaps_;
    private final List<List<Swap>> fwdSwaps_;
}
