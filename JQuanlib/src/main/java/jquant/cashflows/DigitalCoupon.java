package jquant.cashflows;

import jquant.Position;
import jquant.Settings;
import jquant.patterns.AcyclicVisitor;
import jquant.patterns.Visitor;
import jquant.time.Date;
import jquant.time.TimeUtils;

import static jquant.math.CommonUtil.QL_FAIL;
import static jquant.math.CommonUtil.QL_REQUIRE;

//! Digital-payoff coupon
/*! Implementation of a floating-rate coupon with a digital call/put option.
    Payoffs:
    - Coupon with cash-or-nothing Digital Call
      rate + csi * payoffRate * Heaviside(rate-strike)
    - Coupon with cash-or-nothing Digital Put
      rate + csi * payoffRate * Heaviside(strike-rate)
    where csi=+1 or csi=-1.
    - Coupon with asset-or-nothing Digital Call
      rate + csi * rate * Heaviside(rate-strike)
    - Coupon with asset-or-nothing Digital Put
      rate + csi * rate * Heaviside(strike-rate)
    where csi=+1 or csi=-1. If nakedOption is true, the rate in the
    payoffs is set to zero.
    The evaluation of the coupon is made using the call/put spread
    replication method.

    Port of QuantLib's {@code DigitalCoupon} in
    {@code ql/cashflows/digitalcoupon.hpp} and
    {@code ql/cashflows/digitalcoupon.cpp}.
*/
public class DigitalCoupon extends FloatingRateCoupon {
    //! underlying coupon
    protected FloatingRateCoupon underlying_;
    //! strike rate for the call option
    protected double callStrike_ = Double.NaN;
    //! strike rate for the put option
    protected double putStrike_ = Double.NaN;
    //! multiplicative factor of call payoff
    protected double callCsi_ = 0.0;
    //! multiplicative factor of put payoff
    protected double putCsi_ = 0.0;
    //! inclusion flag of the call payoff if the call option ends at-the-money
    protected boolean isCallATMIncluded_;
    //! inclusion flag of the put payoff if the put option ends at-the-money
    protected boolean isPutATMIncluded_;
    //! digital call option type: if true, cash-or-nothing, if false asset-or-nothing
    protected boolean isCallCashOrNothing_ = false;
    //! digital put option type: if true, cash-or-nothing, if false asset-or-nothing
    protected boolean isPutCashOrNothing_ = false;
    //! digital call option payoff rate, if any
    protected double callDigitalPayoff_ = Double.NaN;
    //! digital put option payoff rate, if any
    protected double putDigitalPayoff_ = Double.NaN;
    //! the left and right gaps applied in payoff replication for call
    protected double callLeftEps_, callRightEps_;
    //! the left and right gaps applied in payoff replication for put
    protected double putLeftEps_, putRightEps_;
    //!
    protected boolean hasPutStrike_ = false, hasCallStrike_ = false;
    //! Type of replication
    protected Replication replicationType_;
    //! underlying excluded from the payoff
    protected boolean nakedOption_;

    /*! Constructor with official default arguments. */
    public DigitalCoupon(final FloatingRateCoupon underlying) {
        this(underlying, Double.NaN, Position.Long, false, Double.NaN,
                Double.NaN, Position.Long, false, Double.NaN, null, false);
    }

    /*! Constructor with official default replication and
        {@code nakedOption = false}. */
    public DigitalCoupon(final FloatingRateCoupon underlying,
                         double callStrike,
                         Position callPosition,
                         boolean isCallITMIncluded,
                         double callDigitalPayoff,
                         double putStrike,
                         Position putPosition,
                         boolean isPutITMIncluded,
                         double putDigitalPayoff) {
        this(underlying, callStrike, callPosition, isCallITMIncluded, callDigitalPayoff,
                putStrike, putPosition, isPutITMIncluded, putDigitalPayoff,
                null, false);
    }

    /*! Constructor with official default {@code nakedOption = false}. */
    public DigitalCoupon(final FloatingRateCoupon underlying,
                         double callStrike,
                         Position callPosition,
                         boolean isCallITMIncluded,
                         double callDigitalPayoff,
                         double putStrike,
                         Position putPosition,
                         boolean isPutITMIncluded,
                         double putDigitalPayoff,
                         DigitalReplication replication) {
        this(underlying, callStrike, callPosition, isCallITMIncluded, callDigitalPayoff,
                putStrike, putPosition, isPutITMIncluded, putDigitalPayoff,
                replication, false);
    }

    /*! General constructor. */
    public DigitalCoupon(final FloatingRateCoupon underlying,
                         double callStrike,
                         Position callPosition,
                         boolean isCallITMIncluded,
                         double callDigitalPayoff,
                         double putStrike,
                         Position putPosition,
                         boolean isPutITMIncluded,
                         double putDigitalPayoff,
                         DigitalReplication replication,
                         boolean nakedOption) {
        super(underlying.date(),
                underlying.nominal(),
                underlying.accrualStartDate(),
                underlying.accrualEndDate(),
                underlying.fixingDays(),
                underlying.index(),
                underlying.gearing(),
                underlying.spread(),
                underlying.referencePeriodStart(),
                underlying.referencePeriodEnd(),
                underlying.dayCounter(),
                underlying.isInArrears());
        underlying_ = underlying;
        isCallATMIncluded_ = isCallITMIncluded;
        isPutATMIncluded_ = isPutITMIncluded;
        nakedOption_ = nakedOption;

        DigitalReplication rep = replication;
        if (rep == null)
            rep = new DigitalReplication();

        QL_REQUIRE(rep.gap() > 0.0, "Non positive epsilon not allowed");

        callLeftEps_ = callRightEps_ = putLeftEps_ = putRightEps_ = rep.gap() / 2;
        replicationType_ = rep.replicationType();

        if (Double.isNaN(putStrike)) {
            QL_REQUIRE(Double.isNaN(putDigitalPayoff),
                    "Put Cash rate non allowed if put strike is null");
        }
        if (Double.isNaN(callStrike)) {
            QL_REQUIRE(Double.isNaN(callDigitalPayoff),
                    "Call Cash rate non allowed if call strike is null");
        }
        if (!Double.isNaN(callStrike)) {
            hasCallStrike_ = true;
            callStrike_ = callStrike;
            switch (callPosition) {
                case Long:
                    callCsi_ = 1.0;
                    break;
                case Short:
                    callCsi_ = -1.0;
                    break;
                default:
                    QL_FAIL("unsupported position type");
            }
            if (!Double.isNaN(callDigitalPayoff)) {
                callDigitalPayoff_ = callDigitalPayoff;
                isCallCashOrNothing_ = true;
            }
        }
        if (!Double.isNaN(putStrike)) {
            hasPutStrike_ = true;
            putStrike_ = putStrike;
            switch (putPosition) {
                case Long:
                    putCsi_ = 1.0;
                    break;
                case Short:
                    putCsi_ = -1.0;
                    break;
                default:
                    QL_FAIL("unsupported position type");
            }
            if (!Double.isNaN(putDigitalPayoff)) {
                putDigitalPayoff_ = putDigitalPayoff;
                isPutCashOrNothing_ = true;
            }
        }

        switch (replicationType_) {
            case Central:
                // do nothing
                break;
            case Sub:
                if (hasCallStrike_) {
                    switch (callPosition) {
                        case Long:
                            callLeftEps_ = 0.0;
                            callRightEps_ = rep.gap();
                            break;
                        case Short:
                            callLeftEps_ = rep.gap();
                            callRightEps_ = 0.0;
                            break;
                        default:
                            QL_FAIL("unsupported position type");
                    }
                }
                if (hasPutStrike_) {
                    switch (putPosition) {
                        case Long:
                            putLeftEps_ = rep.gap();
                            putRightEps_ = 0.0;
                            break;
                        case Short:
                            putLeftEps_ = 0.0;
                            putRightEps_ = rep.gap();
                            break;
                        default:
                            QL_FAIL("unsupported position type");
                    }
                }
                break;
            case Super:
                if (hasCallStrike_) {
                    switch (callPosition) {
                        case Long:
                            callLeftEps_ = rep.gap();
                            callRightEps_ = 0.0;
                            break;
                        case Short:
                            callLeftEps_ = 0.0;
                            callRightEps_ = rep.gap();
                            break;
                        default:
                            QL_FAIL("unsupported position type");
                    }
                }
                if (hasPutStrike_) {
                    switch (putPosition) {
                        case Long:
                            putLeftEps_ = 0.0;
                            putRightEps_ = rep.gap();
                            break;
                        case Short:
                            putLeftEps_ = rep.gap();
                            putRightEps_ = 0.0;
                            break;
                        default:
                            QL_FAIL("unsupported position type");
                    }
                }
                break;
            default:
                QL_FAIL("unsupported replication type");
        }

        registerWith(underlying);
    }

    //! \name Observer interface
    @Override
    public void deepUpdate() {
        update();
        underlying_.deepUpdate();
    }

    //! \name LazyObject interface
    @Override
    public void performCalculations() {
        QL_REQUIRE(underlying_.pricer() != null, "pricer not set");

        Date fixingDate = underlying_.fixingDate();
        Date today = Settings.instance.evaluationDate().Date();
        boolean enforceTodaysHistoricFixings =
                Settings.instance.enforcesTodaysHistoricFixings();
        double underlyingRate = nakedOption_ ? 0.0 : underlying_.rate();
        if (TimeUtils.less(fixingDate, today)
                || (TimeUtils.equals(fixingDate, today) && enforceTodaysHistoricFixings)) {
            // must have been fixed
            rate_ = underlyingRate + callCsi_ * callPayoff() + putCsi_ * putPayoff();
        } else if (TimeUtils.equals(fixingDate, today)) {
            // might have been fixed
            if (underlying_.index().hasHistoricalFixing(fixingDate)) {
                rate_ = underlyingRate + callCsi_ * callPayoff() + putCsi_ * putPayoff();
            } else {
                rate_ = underlyingRate + callCsi_ * callOptionRate() + putCsi_ * putOptionRate();
            }
        } else {
            rate_ = underlyingRate + callCsi_ * callOptionRate() + putCsi_ * putOptionRate();
        }
    }

    //! \name Coupon interface
    @Override
    public double rate() {
        calculate();
        return rate_;
    }

    @Override
    public double convexityAdjustment() {
        return underlying_.convexityAdjustment();
    }

    //! \name Digital inspectors
    public double callStrike() {
        if (hasCall())
            return callStrike_;
        else
            return Double.NaN;
    }

    public double putStrike() {
        if (hasPut())
            return putStrike_;
        else
            return Double.NaN;
    }

    public double callDigitalPayoff() {
        if (isCallCashOrNothing_)
            return callDigitalPayoff_;
        else
            return Double.NaN;
    }

    public double putDigitalPayoff() {
        if (isPutCashOrNothing_)
            return putDigitalPayoff_;
        else
            return Double.NaN;
    }

    public boolean hasPut() {
        return hasPutStrike_;
    }

    public boolean hasCall() {
        return hasCallStrike_;
    }

    public boolean hasCollar() {
        return hasCallStrike_ && hasPutStrike_;
    }

    public boolean isLongPut() {
        return putCsi_ == 1.0;
    }

    public boolean isLongCall() {
        return callCsi_ == 1.0;
    }

    public FloatingRateCoupon underlying() {
        return underlying_;
    }

    /*! Returns the call option rate
        (multiplied by: nominal*accrualperiod*discount is the NPV of the option)
    */
    public double callOptionRate() {
        double callOptionRate = 0.0;
        if (hasCallStrike_) {
            // Step function
            callOptionRate = isCallCashOrNothing_ ? callDigitalPayoff_ : callStrike_;
            CappedFlooredCoupon next = new CappedFlooredCoupon(underlying_,
                    callStrike_ + callRightEps_);
            CappedFlooredCoupon previous = new CappedFlooredCoupon(underlying_,
                    callStrike_ - callLeftEps_);
            callOptionRate *= (next.rate() - previous.rate())
                    / (callLeftEps_ + callRightEps_);
            if (!isCallCashOrNothing_) {
                // Call
                CappedFlooredCoupon atStrike = new CappedFlooredCoupon(underlying_,
                        callStrike_);
                double call = underlying_.rate() - atStrike.rate();
                // Sum up
                callOptionRate += call;
            }
        }
        return callOptionRate;
    }

    /*! Returns the put option rate
        (multiplied by: nominal*accrualperiod*discount is the NPV of the option)
    */
    public double putOptionRate() {
        double putOptionRate = 0.0;
        if (hasPutStrike_) {
            // Step function
            putOptionRate = isPutCashOrNothing_ ? putDigitalPayoff_ : putStrike_;
            CappedFlooredCoupon next = new CappedFlooredCoupon(underlying_,
                    Double.NaN, putStrike_ + putRightEps_);
            CappedFlooredCoupon previous = new CappedFlooredCoupon(underlying_,
                    Double.NaN, putStrike_ - putLeftEps_);
            putOptionRate *= (next.rate() - previous.rate())
                    / (putLeftEps_ + putRightEps_);
            if (!isPutCashOrNothing_) {
                // Put
                CappedFlooredCoupon atStrike = new CappedFlooredCoupon(underlying_,
                        Double.NaN, putStrike_);
                double put = -underlying_.rate() + atStrike.rate();
                // Sum up
                putOptionRate -= put;
            }
        }
        return putOptionRate;
    }

    //! \name Visitability
    @Override
    public void accept(AcyclicVisitor v) {
        if (v instanceof Visitor) {
            try {
                @SuppressWarnings("unchecked")
                Visitor<DigitalCoupon> vv = (Visitor<DigitalCoupon>) v;
                vv.visit(this);
            } catch (ClassCastException e) {
                super.accept(v);
            }
        }
    }

    @Override
    public void setPricer(final FloatingRateCouponPricer pricer) {
        super.setPricer(pricer);
        underlying_.setPricer(pricer);
    }

    private double callPayoff() {
        // to use only if index has fixed
        double payoff = 0.0;
        if (hasCallStrike_) {
            double underlyingRate = underlying_.rate();
            if ((underlyingRate - callStrike_) > 1.e-16) {
                payoff = isCallCashOrNothing_ ? callDigitalPayoff_ : underlyingRate;
            } else {
                if (isCallATMIncluded_) {
                    if (Math.abs(callStrike_ - underlyingRate) <= 1.e-16)
                        payoff = isCallCashOrNothing_ ? callDigitalPayoff_ : underlyingRate;
                }
            }
        }
        return payoff;
    }

    private double putPayoff() {
        // to use only if index has fixed
        double payoff = 0.0;
        if (hasPutStrike_) {
            double underlyingRate = underlying_.rate();
            if ((putStrike_ - underlyingRate) > 1.e-16) {
                payoff = isPutCashOrNothing_ ? putDigitalPayoff_ : underlyingRate;
            } else {
                // putStrike_ <= underlyingRate
                if (isPutATMIncluded_) {
                    if (Math.abs(putStrike_ - underlyingRate) <= 1.e-16)
                        payoff = isPutCashOrNothing_ ? putDigitalPayoff_ : underlyingRate;
                }
            }
        }
        return payoff;
    }
}
