package jquant.cashflows;

import jquant.Handle;
import jquant.indexes.IborIndex;
import jquant.termstructures.volatility.optionlet.OptionletVolatilityStructure;
import jquant.time.BusinessDayConvention;
import jquant.time.Date;
import jquant.time.TimeUnit;
import jquant.time.TimeUtils;

import java.util.Optional;

import static jquant.math.CommonUtil.QL_FAIL;
import static jquant.math.CommonUtil.QL_REQUIRE;

//! base pricer for capped/floored Ibor coupons
public abstract class IborCouponPricer extends FloatingRateCouponPricer {
    protected IborCoupon coupon_;
    protected IborIndex index_;
    protected Date fixingDate_;
    protected double gearing_;
    protected double spread_;
    protected double accrualPeriod_;
    protected Date fixingValueDate_;
    protected Date fixingEndDate_;
    protected Date fixingMaturityDate_;
    protected double spanningTime_;
    protected double spanningTimeIndexMaturity_;
    protected Handle<OptionletVolatilityStructure> capletVol_;
    protected boolean useIndexedCoupon_;

    public IborCouponPricer(Handle<OptionletVolatilityStructure> v,
                            Optional<Boolean> useIndexedCoupon) {
        capletVol_ = v;
        useIndexedCoupon_ = useIndexedCoupon.orElseGet(() -> !IborCoupon.Settings.instance().usingAtParCoupons());
        registerWith(capletVol_.currentLink());
    }

    public boolean useIndexedCoupon() {
        return useIndexedCoupon_;
    }

    public Handle<OptionletVolatilityStructure> capletVolatility() {
        return capletVol_;
    }

    public void setCapletVolatility(
            final Handle<OptionletVolatilityStructure> v) {
        unregisterWith(capletVol_.currentLink());
        capletVol_ = v;
        registerWith(capletVol_.currentLink());
        update();
    }

    @Override
    public void initialize(final FloatingRateCoupon coupon) {
        if (coupon instanceof IborCoupon) {
            coupon_ = (IborCoupon) coupon;
            initializeCachedData(coupon_);
            index_ = coupon_.iborIndex();
            gearing_ = coupon_.gearing();
            spread_ = coupon_.spread();
            accrualPeriod_ = coupon_.accrualPeriod();
            QL_REQUIRE(accrualPeriod_ != 0.0, "null accrual period");

            fixingDate_ = coupon_.fixingDate_;
            fixingValueDate_ = coupon_.fixingValueDate_;
            fixingMaturityDate_ = coupon_.fixingMaturityDate_;
            spanningTime_ = coupon_.spanningTime_;
            spanningTimeIndexMaturity_ = coupon_.spanningTimeIndexMaturity_;
        } else {
            QL_FAIL("IborCouponPricer: expected IborCoupon");
        }
    }

    public void initializeCachedData(final IborCoupon coupon) {

        if (coupon.cachedDataIsInitialized_)
            return;

        coupon.fixingValueDate_ = coupon.iborIndex().fixingCalendar().advance(
                coupon.fixingDate_, coupon.iborIndex().fixingDays(), TimeUnit.DAYS,
                BusinessDayConvention.FOLLOWING, false);
        coupon.fixingMaturityDate_ = coupon.iborIndex().maturityDate(coupon.fixingValueDate_);

        if (useIndexedCoupon_) {
            coupon.fixingEndDate_ = coupon.fixingMaturityDate_;
        } else {
            if (coupon.isInArrears_)
                coupon.fixingEndDate_ = coupon.fixingMaturityDate_;
            else { // par coupon approximation
                Date nextFixingDate = coupon.iborIndex().fixingCalendar().advance(
                        coupon.accrualEndDate(), -(coupon.fixingDays_), TimeUnit.DAYS, BusinessDayConvention.FOLLOWING,
                        false);
                coupon.fixingEndDate_ = coupon.iborIndex().fixingCalendar().advance(
                        nextFixingDate, coupon.iborIndex().fixingDays(), TimeUnit.DAYS, BusinessDayConvention.FOLLOWING,
                        false);
                // make sure the estimation period contains at least one day
                coupon.fixingEndDate_ =
                        TimeUtils.max(coupon.fixingEndDate_, coupon.fixingValueDate_.add(1));
            }
        }

        coupon.spanningTime_ = coupon.iborIndex().dayCounter().yearFraction(
                coupon.fixingValueDate_, coupon.fixingEndDate_, new Date(), new Date());

        QL_REQUIRE(coupon.spanningTime_ > 0.0,
                "\n cannot calculate forward rate between ");

        coupon.spanningTimeIndexMaturity_ = coupon.iborIndex().dayCounter().yearFraction(
                coupon.fixingValueDate_, coupon.fixingMaturityDate_, new Date(), new Date());

        coupon.cachedDataIsInitialized_ = true;
    }
}
