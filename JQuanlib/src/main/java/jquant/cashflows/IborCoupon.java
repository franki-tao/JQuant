package jquant.cashflows;

import jquant.Settings;
import jquant.indexes.IborIndex;
import jquant.patterns.AcyclicVisitor;
import jquant.patterns.Singleton;
import jquant.patterns.Visitor;
import jquant.time.Date;
import jquant.time.DayCounter;
import jquant.time.TimeUtils;

import static jquant.math.CommonUtil.QL_FAIL;
import static jquant.math.CommonUtil.QL_REQUIRE;

//! %Coupon paying a Libor-type index
public abstract class IborCoupon extends FloatingRateCoupon {
    // IborCoupon::Settings forward declaration
    public static final class Settings implements Singleton<Settings> {
        private Settings() {
        }

        private static final Settings instance = new Settings();

        public static Settings instance() {
            return instance;
        }

        private boolean usingAtParCoupons_;

        //! When called, IborCoupons are created as indexed coupons instead of par coupons.
        public void createAtParCoupons() {
            usingAtParCoupons_ = true;
        }

        //! When called, IborCoupons are created as par coupons instead of indexed coupons.
        public void createIndexedCoupons() {
            usingAtParCoupons_ = false;
        }

        /*! If true the IborCoupons are created as par coupons and vice versa.
            The default depends on the compiler flag QL_USE_INDEXED_COUPON and can be overwritten by
            createAtParCoupons() and createIndexedCoupons() */
        public boolean usingAtParCoupons() {
            return usingAtParCoupons_;
        }
    }

    private IborIndex iborIndex_;
    public Date fixingDate_;
    public boolean cachedDataIsInitialized_;
    public Date fixingValueDate_;
    public Date fixingEndDate_;
    public Date fixingMaturityDate_;
    public double spanningTime_;
    public double spanningTimeIndexMaturity_;

    public IborCoupon(final Date paymentDate,
                      double nominal,
                      final Date startDate,
                      final Date endDate,
                      int fixingDays,
                      final IborIndex iborIndex,
                      double gearing,
                      double spread,
                      final Date refPeriodStart,
                      final Date refPeriodEnd,
                      final DayCounter dayCounter,
                      boolean isInArrears,
                      final Date exCouponDate) {
        super(paymentDate, nominal, startDate, endDate,
                fixingDays, iborIndex, gearing, spread,
                refPeriodStart, refPeriodEnd,
                dayCounter, isInArrears, exCouponDate);
        iborIndex_ = iborIndex;
        fixingDate_ = super.fixingDate();
    }

    public final IborIndex iborIndex() {
        return iborIndex_;
    }

    public boolean hasFixed() {
        Date today = jquant.Settings.instance.evaluationDate().Date();

        if (TimeUtils.greater(fixingDate_, today)) {
            return false;
        } else if (TimeUtils.less(fixingDate_, today)) {
            return true;
        } else {
            // fixingDate_ == today
            if (jquant.Settings.instance.enforcesTodaysHistoricFixings()) {
                return true;
            } else {
                return index_.hasHistoricalFixing(fixingDate_);
            }
        }
    }

    @Override
    public Date fixingDate() {
        return fixingDate_;
    }

    // implemented in order to manage the case of par coupon
    @Override
    public double indexFixing() {
        initializeCachedData();

        /* instead of just returning index_->fixing(fixingValueDate_)
           its logic is duplicated here using a specialized iborIndex
           forecastFixing overload which
           1) allows to save date/time recalculations, and
           2) takes into account par coupon needs
        */

        if (hasFixed()) {
            double result = index_.pastFixing(fixingDate_);
            QL_REQUIRE(!Double.isNaN(result),
                    "Missing " + index_.name() + " fixing for " + fixingDate_);
            return result;
        } else {
            return iborIndex_.forecastFixing(fixingValueDate_, fixingEndDate_, spanningTime_);
        }
    }

    @Override
    public void setPricer(final FloatingRateCouponPricer pricer) {
        cachedDataIsInitialized_ = false;
        super.setPricer(pricer);
    }

    @Override
    public void accept(AcyclicVisitor v) {
        if (v instanceof Visitor) {
            try {
                // 尝试强制转型
                @SuppressWarnings("unchecked")
                Visitor<IborCoupon> vv = (Visitor<IborCoupon>) v;
                vv.visit(this);
            } catch (ClassCastException e) {
                // 如果类型不匹配，说明该访问者不关心这个特定的子类
                super.accept(v);
            }
        }
    }

    //! Start of the deposit period underlying the index fixing
    public final Date fixingValueDate() {
        initializeCachedData();
        return fixingValueDate_;
    }

    //! End of the deposit period underlying the index fixing
    public final Date fixingMaturityDate() {
        initializeCachedData();
        return fixingMaturityDate_;
    }

    //! End of the deposit period underlying the coupon fixing
    /*! This might be not the same as fixingMaturityDate if par coupons are used. */
    public final Date fixingEndDate() {
        initializeCachedData();
        return fixingEndDate_;
    }

    //! Period underlying the index fixing, as a year fraction
    public double spanningTimeIndexMaturity() {
        initializeCachedData();
        return spanningTimeIndexMaturity_;
    }

    //! Period underlying the coupon fixing, as a year fraction
    /*! This might be not the same as spanningTimeIndexMaturity if par coupons are used. */
    public double spanningTime() {
        initializeCachedData();
        return spanningTime_;
    }


    private void initializeCachedData() {
        if (pricer_ instanceof IborCouponPricer) {
            IborCouponPricer p = (IborCouponPricer) pricer_;
            p.initializeCachedData(this);
        } else {
            QL_FAIL("IborCoupon: pricer not set or not derived from IborCouponPricer");
        }
    }
}
