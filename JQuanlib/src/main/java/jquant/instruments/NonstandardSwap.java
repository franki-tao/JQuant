package jquant.instruments;

import jquant.CashFlow;
import jquant.cashflows.FixedRateLeg;
import jquant.cashflows.IborLeg;
import jquant.cashflows.OvernightLeg;
import jquant.cashflows.RateAveraging;
import jquant.indexes.IborIndex;
import jquant.indexes.OvernightIndex;
import jquant.time.BusinessDayConvention;
import jquant.time.Calendar;
import jquant.time.DayCounter;
import jquant.time.Schedule;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static jquant.math.CommonUtil.QL_FAIL;
import static jquant.math.CommonUtil.QL_REQUIRE;
import static jquant.math.MathUtils.QL_EPSILON;
import static jquant.math.MathUtils.close;

/**
 * Vanilla swap but possibly with period-dependent nominal and strike.
 *
 * <p>Port of QuantLib's {@code NonstandardSwap} in
 * {@code ql/instruments/nonstandardswap.{hpp,cpp}}, limited to the conversion
 * constructor and the {@code init()} common to all constructors. The official
 * conversion constructor takes a {@code FixedVsFloatingSwap}; in this port
 * {@code FixedVsFloatingSwap} is an interface implemented by
 * {@link VanillaSwap} and {@link OvernightIndexedSwap}, whose inspectors do not
 * expose payment lag, payment calendar, or per-coupon nominals. The conversion
 * constructor therefore takes the concrete {@link MultipleResetsSwap}, which
 * provides those inspectors, instead of adding them to the shared interface.
 *
 * <p>The official intermediate/final capital-exchange branches of
 * {@code init()} are omitted: the conversion constructor always sets both
 * flags to {@code false} (as in the official source), and this port has no
 * {@code Redemption} cash-flow type.
 *
 * <p>https://github.com/lballabio/QuantLib/blob/master/ql/instruments/nonstandardswap.hpp
 * <p>https://github.com/lballabio/QuantLib/blob/master/ql/instruments/nonstandardswap.cpp
 */
public class NonstandardSwap extends Swap {
    private final Swap.Type type_;
    private final List<Double> fixedNominal_;
    private final List<Double> floatingNominal_;
    private final Schedule fixedSchedule_;
    private final List<Double> fixedRate_;
    private final DayCounter fixedDayCount_;
    private final Schedule floatingSchedule_;
    private final IborIndex iborIndex_;
    private final List<Double> spread_;
    private final List<Double> gearing_;
    private final boolean singleSpreadAndGearing_;
    private final DayCounter floatingDayCount_;
    private final BusinessDayConvention paymentConvention_;
    private final int paymentLag_;
    private final Calendar paymentCalendar_;

    // Overnight-index options. The official conversion constructor only copies
    // these from an OvernightIndexedSwap; MultipleResetsSwap is never one, so
    // they keep the official default values.
    private final boolean telescopicValueDates_ = false;
    private final RateAveraging averagingMethod_ = RateAveraging.Compound;
    private final Integer lookbackDays_ = null;
    private final int lockoutDays_ = 0;
    private final boolean applyObservationShift_ = false;

    /**
     * Official conversion constructor. A standard leg may carry fewer
     * nominals than coupons; the last notional is then repeated, as in the
     * official {@code resize(..., back())} calls.
     */
    public NonstandardSwap(final MultipleResetsSwap fromVanilla) {
        super(2);

        type_ = fromVanilla.type();
        fixedNominal_ = new ArrayList<>(fromVanilla.fixedNominals());
        floatingNominal_ = new ArrayList<>(fromVanilla.floatingNominals());
        fixedSchedule_ = fromVanilla.fixedSchedule();
        fixedRate_ = new ArrayList<>(Collections.<Double>nCopies(
                fromVanilla.fixedLeg().size(), fromVanilla.fixedRate()));
        fixedDayCount_ = fromVanilla.fixedDayCount();
        floatingSchedule_ = fromVanilla.floatingSchedule();
        iborIndex_ = fromVanilla.iborIndex();
        spread_ = new ArrayList<>(Collections.<Double>nCopies(
                fromVanilla.floatingLeg().size(), fromVanilla.spread()));
        gearing_ = new ArrayList<>(Collections.<Double>nCopies(
                fromVanilla.floatingLeg().size(), 1.0));
        singleSpreadAndGearing_ = true;
        floatingDayCount_ = fromVanilla.floatingDayCount();
        paymentConvention_ = fromVanilla.paymentConvention();
        paymentLag_ = fromVanilla.paymentLag();
        paymentCalendar_ = fromVanilla.paymentCalendar();

        // Standard legs repeat the last notional when the input vector is shorter.
        resize(fixedNominal_, fromVanilla.fixedLeg().size());
        resize(floatingNominal_, fromVanilla.floatingLeg().size());

        init();
    }

    private static void resize(final List<Double> values, int size) {
        // Official back() is undefined on an empty vector; the conversion
        // constructor always starts from a non-empty nominal vector.
        while (!values.isEmpty() && values.size() < size)
            values.add(values.get(values.size() - 1));
        if (values.size() > size)
            values.subList(size, values.size()).clear();
    }

    /** Official {@code NonstandardSwap::init()} (capital exchange omitted). */
    private void init() {
        QL_REQUIRE(fixedNominal_.size() == fixedRate_.size(),
                "Fixed nominal size (" + fixedNominal_.size()
                        + ") does not match fixed rate size ("
                        + fixedRate_.size() + ")");

        QL_REQUIRE(fixedNominal_.size() == fixedSchedule_.size() - 1,
                "Fixed nominal size (" + fixedNominal_.size()
                        + ") does not match schedule size ("
                        + fixedSchedule_.size() + ") - 1");

        QL_REQUIRE(floatingNominal_.size() == floatingSchedule_.size() - 1,
                "Floating nominal size (" + floatingNominal_.size()
                        + ") does not match schedule size ("
                        + floatingSchedule_.size() + ") - 1");

        QL_REQUIRE(floatingNominal_.size() == spread_.size(),
                "Floating nominal size (" + floatingNominal_.size()
                        + ") does not match spread size (" + spread_.size() + ")");

        QL_REQUIRE(floatingNominal_.size() == gearing_.size(),
                "Floating nominal size (" + floatingNominal_.size()
                        + ") does not match gearing size (" + gearing_.size() + ")");

        // If the gearing is zero then the Ibor leg would be set up with fixed
        // coupons, which makes trouble here. Enforce the gearing to be non-zero.
        for (int i = 0; i < gearing_.size(); ++i) {
            if (close(gearing_.get(i), 0.0))
                gearing_.set(i, QL_EPSILON);
        }

        legs_.set(0, new FixedRateLeg(fixedSchedule_)
                .withNotionals(fixedNominal_)
                .withCouponRates(fixedRate_, fixedDayCount_)
                .withPaymentAdjustment(paymentConvention_)
                .withPaymentLag(paymentLag_)
                .withPaymentCalendar(
                        paymentCalendar_ == null || paymentCalendar_.empty()
                                ? fixedSchedule_.calendar() : paymentCalendar_)
                .leg());

        // The Ibor/overnight legs replace an empty payment calendar with the
        // leg schedule's calendar internally; pass that calendar explicitly so
        // the Java leg builders never receive a null calendar.
        Calendar floatingPaymentCalendar =
                paymentCalendar_ == null || paymentCalendar_.empty()
                        ? floatingSchedule_.calendar() : paymentCalendar_;

        if (iborIndex_ instanceof OvernightIndex overnightIndex) {
            // Only compounded averaging is supported; OvernightLeg uses it by default.
            legs_.set(1, new OvernightLeg(floatingSchedule_, overnightIndex)
                    .withNotionals(floatingNominal_)
                    .withPaymentDayCounter(floatingDayCount_)
                    .withPaymentAdjustment(paymentConvention_)
                    .withPaymentLag(paymentLag_)
                    .withPaymentCalendar(floatingPaymentCalendar)
                    .withSpreads(spread_)
                    .withGearings(gearing_)
                    .withTelescopicValueDates(telescopicValueDates_)
                    .withAveragingMethod(averagingMethod_)
                    .withLookbackDays(lookbackDays_)
                    .withLockoutDays(lockoutDays_)
                    .withObservationShift(applyObservationShift_)
                    .build());
        } else {
            legs_.set(1, new IborLeg(floatingSchedule_, iborIndex_)
                    .withNotionals(floatingNominal_)
                    .withPaymentDayCounter(floatingDayCount_)
                    .withPaymentAdjustment(paymentConvention_)
                    .withPaymentLag(paymentLag_)
                    .withPaymentCalendar(floatingPaymentCalendar)
                    .withSpreads(spread_)
                    .withGearings(gearing_)
                    .leg());
        }

        for (CashFlow cashflow : legs_.get(1))
            registerWith(cashflow);

        switch (type_) {
            case Payer:
                payer_[0] = -1.0;
                payer_[1] = +1.0;
                break;
            case Receiver:
                payer_[0] = +1.0;
                payer_[1] = -1.0;
                break;
            default:
                QL_FAIL("Unknown nonstandard-swap type");
        }
    }

    //! \name Inspectors
    public Swap.Type type() {
        return type_;
    }

    public List<Double> fixedNominal() {
        return List.copyOf(fixedNominal_);
    }

    public List<Double> floatingNominal() {
        return List.copyOf(floatingNominal_);
    }

    public Schedule fixedSchedule() {
        return fixedSchedule_;
    }

    public List<Double> fixedRate() {
        return List.copyOf(fixedRate_);
    }

    public DayCounter fixedDayCount() {
        return fixedDayCount_;
    }

    public Schedule floatingSchedule() {
        return floatingSchedule_;
    }

    public IborIndex iborIndex() {
        return iborIndex_;
    }

    public double spread() {
        QL_REQUIRE(singleSpreadAndGearing_,
                "spread is a vector, use spreads inspector instead");
        return spread_.get(0);
    }

    public double gearing() {
        QL_REQUIRE(singleSpreadAndGearing_,
                "gearing is a vector, use gearings inspector instead");
        return gearing_.get(0);
    }

    public List<Double> spreads() {
        return List.copyOf(spread_);
    }

    public List<Double> gearings() {
        return List.copyOf(gearing_);
    }

    public DayCounter floatingDayCount() {
        return floatingDayCount_;
    }

    public BusinessDayConvention paymentConvention() {
        return paymentConvention_;
    }

    public int paymentLag() {
        return paymentLag_;
    }

    public Calendar paymentCalendar() {
        return paymentCalendar_;
    }

    public List<CashFlow> fixedLeg() {
        return List.copyOf(legs_.get(0));
    }

    public List<CashFlow> floatingLeg() {
        return List.copyOf(legs_.get(1));
    }
}
