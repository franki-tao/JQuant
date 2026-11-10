package jquant.instruments;

import jquant.cashflows.AverageBMALeg;
import jquant.cashflows.IborLeg;
import jquant.indexes.BMAIndex;
import jquant.indexes.IborIndex;
import jquant.time.DayCounter;
import jquant.time.Schedule;

import static jquant.math.CommonUtil.QL_FAIL;

//! swap paying Libor against average BMA coupons
/*! Payer and Receiver refer to the BMA leg. QuantLib
    {@code ql/instruments/bmaswap.hpp}.
*/
public class BMASwap extends Swap {
    private final Type type_;
    private final double nominal_;
    private final double liborFraction_;
    private final double liborSpread_;

    public BMASwap(Type type,
                   double nominal,
                   Schedule liborSchedule,
                   double liborFraction,
                   double liborSpread,
                   IborIndex liborIndex,
                   DayCounter liborDayCount,
                   Schedule bmaSchedule,
                   BMAIndex bmaIndex,
                   DayCounter bmaDayCount) {
        super(2);
        type_ = type;
        nominal_ = nominal;
        liborFraction_ = liborFraction;
        liborSpread_ = liborSpread;
        legs_.set(0, new IborLeg(liborSchedule, liborIndex)
                .withNotionals(nominal)
                .withPaymentDayCounter(liborDayCount)
                .withPaymentAdjustment(liborSchedule.businessDayConvention())
                .withFixingDays(liborIndex.fixingDays())
                .withGearings(liborFraction)
                .withSpreads(liborSpread)
                .leg());
        legs_.set(1, new AverageBMALeg(bmaSchedule, bmaIndex)
                .withNotionals(nominal)
                .withPaymentDayCounter(bmaDayCount)
                .withPaymentAdjustment(bmaSchedule.businessDayConvention())
                .leg());
        switch (type_) {
            case Payer:
                payer_[0] = 1.0;
                payer_[1] = -1.0;
                break;
            case Receiver:
                payer_[0] = -1.0;
                payer_[1] = 1.0;
                break;
            default:
                QL_FAIL("Unknown BMA-swap type");
        }
        registerLegs();
    }

    public double liborFraction() {
        return liborFraction_;
    }

    public double liborSpread() {
        return liborSpread_;
    }

    public double nominal() {
        return nominal_;
    }

    public Type type() {
        return type_;
    }

    public double liborLegBPS() {
        return legBPS(0);
    }

    public double liborLegNPV() {
        return legNPV(0);
    }

    public double bmaLegBPS() {
        return legBPS(1);
    }

    public double bmaLegNPV() {
        return legNPV(1);
    }

    //! fraction of Libor that matches the BMA leg, keeping the spread
    public double fairLiborFraction() {
        double basisPoint = 1.0e-4;
        double spreadNpv = (liborSpread_ / basisPoint) * liborLegBPS();
        double pureLiborNpv = liborLegNPV() - spreadNpv;
        QL_FAIL_IF_ZERO(pureLiborNpv);
        return -liborFraction_ * (bmaLegNPV() + spreadNpv) / pureLiborNpv;
    }

    //! spread over Libor that sets the NPV to zero, keeping the fraction
    public double fairLiborSpread() {
        double basisPoint = 1.0e-4;
        return liborSpread_ - NPV() / (liborLegBPS() / basisPoint);
    }

    private static void QL_FAIL_IF_ZERO(double pureLiborNpv) {
        if (pureLiborNpv == 0.0)
            QL_FAIL("result not available (null libor NPV)");
    }
}
