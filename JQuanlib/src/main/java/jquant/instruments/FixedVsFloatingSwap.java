package jquant.instruments;

import jquant.CashFlow;
import jquant.PricingEngine;
import jquant.indexes.IborIndex;
import jquant.patterns.Observable;
import jquant.time.BusinessDayConvention;
import jquant.time.DayCounter;
import jquant.time.Schedule;

import java.util.List;

/**
 * Minimal common view of a fixed-vs-floating swap, as required by
 * {@link Swaption} and the Black-style swaption engines.
 *
 * <p>Java already has {@link VanillaSwap} (which extends {@code Instrument}
 * directly) and this port adds {@link OvernightIndexedSwap}. Rather than
 * changing the existing {@code VanillaSwap} hierarchy, this interface exposes
 * the subset of QuantLib {@code FixedVsFloatingSwap} that swaptions need.
 * {@code VanillaSwap::type()} keeps its existing {@code VanillaSwap.Type}
 * return type; the official {@code Swap::Type} view is exposed through
 * {@link #swapType()}.
 */
public interface FixedVsFloatingSwap extends Observable {
    Swap.Type swapType();

    double nominal();

    double fixedRate();

    double spread();

    DayCounter fixedDayCount();

    Schedule fixedSchedule();

    Schedule floatingSchedule();

    IborIndex iborIndex();

    DayCounter floatingDayCount();

    BusinessDayConvention paymentConvention();

    List<CashFlow> fixedLeg();

    List<CashFlow> floatingLeg();

    /** Official {@code FixedVsFloatingSwap::leg(Size)}. */
    default List<CashFlow> leg(int j) {
        return j == 0 ? fixedLeg() : floatingLeg();
    }

    double fairRate();

    double fixedLegBPS();

    double floatingLegBPS();

    void deepUpdate();

    void setupArguments(PricingEngine.arguments args);

    void setPricingEngine(PricingEngine engine);

    void alwaysForwardNotifications();

    /** Valuation date of the swap (inherited from {@code Instrument}). */
    jquant.time.Date valuationDate();
}
