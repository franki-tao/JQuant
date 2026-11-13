package jquant.instruments;

import jquant.Instrument;
import jquant.Settings;
import jquant.indexes.IborIndex;
import jquant.termstructures.YieldTermStructure;
import jquant.time.Date;
import jquant.time.DayCounter;

import java.util.Optional;

import static jquant.math.CommonUtil.QL_REQUIRE;

/**
 * FRA settled at the value date. The payoff is
 * {@code notional * sign * (F-K) * tau / (1 + F * tau)} and the NPV multiplies
 * that amount by the discount factor to the value date.
 *
 * <p>https://github.com/lballabio/QuantLib/blob/master/ql/instruments/forwardrateagreement.cpp
 */
public class ForwardRateAgreement extends Instrument {
    public enum Position { Long, Short }

    private final Position position_;
    private final double notional_;
    private final double strike_;
    private final IborIndex index_;
    private final boolean useIndexedCoupon_;
    private final Date valueDate_;
    private final Date maturityDate_;
    private final YieldTermStructure discountCurve_;
    private double forwardRate_ = Double.NaN;
    private double amount_ = Double.NaN;

    /**
     * Official short constructor: the forward rate is forecast by the index,
     * i.e. {@code useIndexedCoupon=true}, and the maturity is the index
     * maturity of the value date.
     */
    public ForwardRateAgreement(IborIndex index,
                                Date valueDate,
                                Position position,
                                double strike,
                                double notional,
                                YieldTermStructure discountCurve) {
        this(index, valueDate, index.maturityDate(valueDate),
                position, strike, notional, discountCurve, true);
    }

    public ForwardRateAgreement(IborIndex index,
                                Date valueDate,
                                Date maturityDate,
                                Position position,
                                double strike,
                                double notional,
                                YieldTermStructure discountCurve,
                                boolean useIndexedCoupon) {
        QL_REQUIRE(notional > 0.0, "notionalAmount must be positive");
        QL_REQUIRE(TimeLess(valueDate, maturityDate), "valueDate must be earlier than maturityDate");
        position_ = position;
        notional_ = notional;
        strike_ = strike;
        index_ = index;
        useIndexedCoupon_ = useIndexedCoupon;
        valueDate_ = valueDate;
        maturityDate_ = index.fixingCalendar().adjust(maturityDate, index.businessDayConvention());
        discountCurve_ = discountCurve;
        QL_REQUIRE(TimeLess(valueDate_, maturityDate_), "valueDate must be earlier than maturityDate");
        if (discountCurve_ != null)
            registerWith(discountCurve_);
        registerWith(index_);
    }

    public Date valueDate() {
        return valueDate_;
    }

    public Date maturityDate() {
        return maturityDate_;
    }

    public double forwardRate() {
        calculate();
        return forwardRate_;
    }

    public double amount() {
        calculate();
        return amount_;
    }

    @Override
    public boolean isExpired() {
        Date today = Settings.instance.evaluationDate().Date();
        return jquant.CashFlow.hasOccurred(valueDate_, today, Optional.empty());
    }

    @Override
    protected void performCalculations() {
        YieldTermStructure forecast = index_.forwardingTermStructure().currentLink();
        DayCounter dayCounter = index_.dayCounter();
        double tau = dayCounter.yearFraction(valueDate_, maturityDate_, new Date(), new Date());
        if (useIndexedCoupon_)
            forwardRate_ = index_.fixing(index_.fixingDate(valueDate_), false);
        else
            forwardRate_ = (forecast.discount(valueDate_, false) / forecast.discount(maturityDate_, false) - 1.0)
                    / tau;
        int sign = position_ == Position.Long ? 1 : -1;
        amount_ = notional_ * sign * (forwardRate_ - strike_) * tau / (1.0 + forwardRate_ * tau);
        YieldTermStructure discount = discountCurve_ != null ? discountCurve_ : forecast;
        NPV_ = amount_ * discount.discount(valueDate_, false);
        valuationDate_ = discount.referenceDate();
        errorEstimate_ = Double.NaN;
    }

    private static boolean TimeLess(Date left, Date right) {
        return jquant.time.TimeUtils.less(left, right);
    }
}
