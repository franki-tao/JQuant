package jquant.cashflows;

import jquant.CashFlow;
import jquant.Handle;
import jquant.Quote;
import jquant.indexes.IborIndex;
import jquant.math.CommonUtil;
import jquant.quotes.SimpleQuote;
import jquant.termstructures.volatility.optionlet.OptionletVolatilityStructure;
import jquant.time.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static jquant.cashflows.CashflowUtil.FloatingLeg;
import static jquant.cashflows.CashflowUtil.setCouponPricer;
import static jquant.cashflows.TimingAdjustment.Black76;

//! helper class building a sequence of capped/floored ibor-rate coupons
public class IborLeg {
    private Schedule schedule_;
    private IborIndex index_;
    private List<Double> notionals_;
    private DayCounter paymentDayCounter_;
    private BusinessDayConvention paymentAdjustment_; // = Following;
    private int paymentLag_ = 0;
    private Calendar paymentCalendar_;
    private List<Integer> fixingDays_;
    private List<Double> gearings_;
    private List<Double> spreads_;
    private List<Double> caps_, floors_;
    private boolean inArrears_ = false, zeroPayments_ = false;
    private Period exCouponPeriod_;
    private Calendar exCouponCalendar_;
    private BusinessDayConvention exCouponAdjustment_; // = Unadjusted;
    private boolean exCouponEndOfMonth_ = false;
    private Optional<Boolean> useIndexedCoupons_;

    public IborLeg(Schedule schedule, IborIndex index) {
        schedule_ = schedule;
        index_ = index;
        notionals_ = new ArrayList<>();
        fixingDays_ = new ArrayList<>();
        gearings_ = new ArrayList<>();
        spreads_ = new ArrayList<>();
        caps_ = new ArrayList<>();
        floors_ = new ArrayList<>();
    }

    public IborLeg withNotionals(double notional) {
        this.notionals_ = CommonUtil.ArrayInit(1, notional);
        return this;
    }

    public IborLeg withNotionals(final List<Double> notionals) {
        this.notionals_ = notionals;
        return this;
    }

    public IborLeg withPaymentDayCounter(final DayCounter dayCounter) {
        this.paymentDayCounter_ = dayCounter;
        return this;
    }

    public IborLeg withPaymentAdjustment(BusinessDayConvention convention) {
        this.paymentAdjustment_ = convention;
        return this;
    }

    public IborLeg withPaymentLag(int lag) {
        paymentLag_ = lag;
        return this;
    }

    public IborLeg withPaymentCalendar(final Calendar cal) {
        paymentCalendar_ = cal;
        return this;
    }

    public IborLeg withFixingDays(int fixingDays) {
        fixingDays_ = CommonUtil.ArrayInit(1, fixingDays);
        return this;
    }

    public IborLeg withFixingDays(final List<Integer> fixingDays) {
        fixingDays_ = fixingDays;
        return this;
    }

    public IborLeg withGearings(double gearing) {
        this.gearings_ = CommonUtil.ArrayInit(1, gearing);
        return this;
    }

    public IborLeg withGearings(final List<Double> gearings) {
        this.gearings_ = gearings;
        return this;
    }

    public IborLeg withSpreads(double spread) {
        this.spreads_ = CommonUtil.ArrayInit(1, spread);
        return this;
    }

    public IborLeg withSpreads(final List<Double> spreads) {
        this.spreads_ = spreads;
        return this;
    }

    public IborLeg withCaps(double cap) {
        this.caps_ = CommonUtil.ArrayInit(1, cap);
        return this;
    }

    public IborLeg withCaps(final List<Double> caps) {
        this.caps_ = caps;
        return this;
    }

    public IborLeg withFloors(double floor) {
        this.floors_ = CommonUtil.ArrayInit(1, floor);
        return this;
    }

    public IborLeg withFloors(final List<Double> floors) {
        this.floors_ = floors;
        return this;
    }

    // flag = true
    public IborLeg inArrears(boolean flag) {
        this.inArrears_ = flag;
        return this;
    }

    // flag = true
    public IborLeg withZeroPayments(boolean flag) {
        this.zeroPayments_ = flag;
        return this;
    }

    // endOfMonth = false
    public IborLeg withExCouponPeriod(final Period period,
                                      final Calendar cal,
                                      BusinessDayConvention convention,
                                      boolean endOfMonth) {
        exCouponPeriod_ = period;
        exCouponCalendar_ = cal;
        exCouponAdjustment_ = convention;
        exCouponEndOfMonth_ = endOfMonth;
        return this;
    }
    // b = true
    public IborLeg withIndexedCoupons(Optional<Boolean> b) {
        useIndexedCoupons_ = b;
        return this;
    }

    // b = true
    public IborLeg withAtParCoupons(boolean b) {
        useIndexedCoupons_ = Optional.of(!b);
        return this;
    }

    public List<CashFlow> leg() {
        List<CashFlow> leg = FloatingLeg( schedule_, notionals_, index_, paymentDayCounter_,
                paymentAdjustment_, fixingDays_, gearings_, spreads_,
                caps_, floors_, inArrears_, zeroPayments_, paymentLag_, paymentCalendar_,
                exCouponPeriod_, exCouponCalendar_, exCouponAdjustment_, exCouponEndOfMonth_);
        if (caps_.isEmpty() && floors_.isEmpty() && !inArrears_) {
            IborCouponPricer pricer = new BlackIborCouponPricer(
                    new Handle<>(new OptionletVolatilityStructure(BusinessDayConvention.FOLLOWING, new DayCounter()), true),
                    Black76,
                    new Handle<>(new SimpleQuote(1.0), true),
                    useIndexedCoupons_);
            setCouponPricer(leg, pricer);
        }

        return leg;
    }
}
