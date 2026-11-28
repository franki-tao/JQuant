package jquant.pricingengines.swaption;

import jquant.CashFlow;
import jquant.DiscretizedAsset;
import jquant.DiscretizedOption;
import jquant.TimeGrid;
import jquant.cashflows.Coupon;
import jquant.instruments.Swaption;
import jquant.instruments.VanillaSwap;
import jquant.pricingengines.swap.DiscretizedSwap;
import jquant.time.Date;
import jquant.time.DayCounter;
import jquant.time.Schedule;
import jquant.time.TimeUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Discretized Bermudan swaption.
 *
 * <p>Ported from QuantLib
 * {@code ql/pricingengines/swaption/discretizedswaption.{hpp,cpp}}.
 */
public class DiscretizedSwaption extends DiscretizedOption {

    private double lastPayment_;

    public DiscretizedSwaption(Swaption.arguments args,
                               Date referenceDate,
                               DayCounter dayCounter) {
        super(null, args.exercise.type(), new ArrayList<>());

        // Date adjustments can get time vectors out of synch.
        // Here, we try and collapse similar dates which could cause
        // a mispricing.
        final Swaption.arguments snappedArgs = new Swaption.arguments();
        final List<CouponAdjustment> fixedCouponAdjustments = new ArrayList<>();
        final List<CouponAdjustment> floatingCouponAdjustments = new ArrayList<>();

        prepareSwaptionWithSnappedDates(args, referenceDate, dayCounter,
                snappedArgs, fixedCouponAdjustments, floatingCouponAdjustments);

        for (Date exerciseDate : snappedArgs.exercise.dates())
            exerciseTimes_.add(dayCounter.yearFraction(referenceDate, exerciseDate));

        final double lastFixedPayment = dayCounter.yearFraction(
                referenceDate,
                snappedArgs.fixedPayDates.get(snappedArgs.fixedPayDates.size() - 1));
        final List<CashFlow> floatingLeg = snappedArgs.swap.floatingLeg();
        final double lastFloatingPayment = dayCounter.yearFraction(
                referenceDate,
                ((Coupon) floatingLeg.get(floatingLeg.size() - 1)).date());
        lastPayment_ = Math.max(lastFixedPayment, lastFloatingPayment);

        underlying_ = new DiscretizedSwap(snappedArgs, referenceDate, dayCounter,
                fixedCouponAdjustments, floatingCouponAdjustments);
    }

    @Override
    protected void preAdjustValuesImpl() {
    }

    @Override
    public void reset(int size) {
        underlying_.initialize(method(), lastPayment_);
        super.reset(size);
    }

    private static void prepareSwaptionWithSnappedDates(
            Swaption.arguments args,
            Date referenceDate,
            DayCounter dayCounter,
            Swaption.arguments snappedArgs,
            List<CouponAdjustment> fixedCouponAdjustments,
            List<CouponAdjustment> floatingCouponAdjustments) {

        final List<Date> fixedDates =
                new ArrayList<>(args.swap.fixedSchedule().dates());
        final List<Date> floatDates =
                new ArrayList<>(args.swap.floatingSchedule().dates());

        fixedCouponAdjustments.clear();
        for (int i = 0; i < args.swap.fixedLeg().size(); ++i)
            fixedCouponAdjustments.add(CouponAdjustment.pre);
        floatingCouponAdjustments.clear();
        for (int i = 0; i < args.swap.floatingLeg().size(); ++i)
            floatingCouponAdjustments.add(CouponAdjustment.pre);

        for (Date exerciseDate : args.exercise.dates()) {
            for (int j = 0; j < fixedDates.size() - 1; j++) {
                final Date unadjustedDate = fixedDates.get(j);
                if (!TimeUtils.equals(exerciseDate, unadjustedDate)
                        && withinOneWeek(exerciseDate, unadjustedDate)) {
                    fixedDates.set(j, exerciseDate);
                    if (withinPreviousWeek(exerciseDate, unadjustedDate))
                        fixedCouponAdjustments.set(j, CouponAdjustment.post);
                }
            }

            for (int j = 0; j < floatDates.size() - 1; j++) {
                final Date unadjustedDate = floatDates.get(j);
                if (!TimeUtils.equals(exerciseDate, unadjustedDate)
                        && withinOneWeek(exerciseDate, unadjustedDate)) {
                    floatDates.set(j, exerciseDate);
                    if (withinPreviousWeek(exerciseDate, unadjustedDate))
                        floatingCouponAdjustments.set(j, CouponAdjustment.post);
                }
            }
        }

        final Schedule snappedFixedSchedule = new Schedule(fixedDates);
        final Schedule snappedFloatSchedule = new Schedule(floatDates);

        final jquant.instruments.VanillaSwap.Type vanillaType =
                args.swap.swapType() == jquant.instruments.Swap.Type.Payer
                        ? jquant.instruments.VanillaSwap.Type.Payer
                        : jquant.instruments.VanillaSwap.Type.Receiver;

        final VanillaSwap snappedSwap = new VanillaSwap(
                vanillaType, args.swap.nominal(),
                snappedFixedSchedule, args.swap.fixedRate(),
                args.swap.fixedDayCount(), snappedFloatSchedule,
                args.swap.iborIndex(), args.swap.spread(),
                args.swap.floatingDayCount(),
                Optional.of(args.swap.paymentConvention()), Optional.empty());

        final Swaption snappedSwaption = new Swaption(
                snappedSwap, args.exercise, args.settlementType,
                args.settlementMethod);

        snappedSwaption.setupArguments(snappedArgs);
    }

    private static boolean withinPreviousWeek(Date d1, Date d2) {
        return TimeUtils.geq(d2, d1.substract(7)) && TimeUtils.leq(d2, d1);
    }

    private static boolean withinNextWeek(Date d1, Date d2) {
        return TimeUtils.geq(d2, d1) && TimeUtils.leq(d2, d1.add(7));
    }

    private static boolean withinOneWeek(Date d1, Date d2) {
        return withinPreviousWeek(d1, d2) || withinNextWeek(d1, d2);
    }
}
