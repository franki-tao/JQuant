package jquant.pricingengines.bond;

import jquant.Compounding;
import jquant.InterestRate;
import jquant.cashflows.CashFlows;
import jquant.cashflows.Duration;
import jquant.instruments.Bond;
import jquant.termstructures.YieldTermStructure;
import jquant.time.Date;
import jquant.time.DayCounter;
import jquant.time.Frequency;
import jquant.time.TimeUtils;

import static jquant.math.CommonUtil.QL_REQUIRE;

/**
 * Bond adapters of the {@link CashFlows} functions.
 *
 * <p>These adapters pass the bond cash flows, exclude cash flows on the
 * settlement date, and use the bond settlement date when no date is given.
 *
 * <p><b>Units:</b> prices are per 100 of the current notional, as per market
 * convention. If the bond has a face value other than 100, both price inputs
 * (for example the price passed to {@link #yield} or {@link #zSpread}) and
 * returned prices are per 100: {@code price = cash price * 100 / face}.
 *
 * <p>This is different from the raw {@link CashFlows} functions, which work in
 * cash amounts. In particular {@link #basisPointValue} and
 * {@link #yieldValueBasisPoint} are <em>not</em> rescaled by the official
 * adapters; they already return cash amounts (per unit notional). Likewise
 * {@link #duration} is a time in years and {@link #convexity} is the
 * convexity of the whole cash-flow leg, not a per-100 quantity; neither is
 * rescaled.
 *
 * <p>https://github.com/lballabio/QuantLib/blob/master/ql/pricingengines/bond/bondfunctions.hpp
 * <p>https://github.com/lballabio/QuantLib/blob/master/ql/pricingengines/bond/bondfunctions.cpp
 */
public final class BondFunctions {

    private BondFunctions() {
    }

    //! \name Date inspectors
    public static boolean isTradable(Bond bond) {
        return isTradable(bond, new Date());
    }

    public static boolean isTradable(Bond bond, Date settlementDate) {
        settlementDate = settlement(bond, settlementDate);
        return bond.notional(settlementDate) != 0.0;
    }

    //! \name Coupon inspectors
    public static double accruedAmount(Bond bond) {
        return accruedAmount(bond, new Date());
    }

    public static double accruedAmount(Bond bond, Date settlementDate) {
        settlementDate = settlement(bond, settlementDate);

        if (!isTradable(bond, settlementDate))
            return 0.0;

        return CashFlows.accruedAmount(bond.cashflows(), false, settlementDate)
                * 100.0 / bond.notional(settlementDate);
    }

    //! \name YieldTermStructure functions
    public static double cleanPrice(Bond bond, YieldTermStructure discountCurve) {
        return cleanPrice(bond, discountCurve, new Date());
    }

    public static double cleanPrice(Bond bond,
                                    YieldTermStructure discountCurve,
                                    Date settlementDate) {
        settlementDate = settlement(bond, settlementDate);
        return dirtyPrice(bond, discountCurve, settlementDate)
                - bond.accruedAmount(settlementDate);
    }

    public static double dirtyPrice(Bond bond, YieldTermStructure discountCurve) {
        return dirtyPrice(bond, discountCurve, new Date());
    }

    public static double dirtyPrice(Bond bond,
                                    YieldTermStructure discountCurve,
                                    Date settlementDate) {
        settlementDate = settlement(bond, settlementDate);

        QL_REQUIRE(isTradable(bond, settlementDate),
                "non tradable at " + settlementDate
                        + " settlement date (maturity being " + bond.maturityDate() + ")");

        return CashFlows.npv(bond.cashflows(), discountCurve,
                false, settlementDate, settlementDate)
                * 100.0 / bond.notional(settlementDate);
    }

    public static double bps(Bond bond, YieldTermStructure discountCurve) {
        return bps(bond, discountCurve, new Date());
    }

    public static double bps(Bond bond,
                             YieldTermStructure discountCurve,
                             Date settlementDate) {
        settlementDate = settlement(bond, settlementDate);

        requireTradable(bond, settlementDate);

        return CashFlows.bps(bond.cashflows(), discountCurve,
                false, settlementDate, settlementDate)
                * 100.0 / bond.notional(settlementDate);
    }

    public static double atmRate(Bond bond, YieldTermStructure discountCurve) {
        return atmRate(bond, discountCurve, new Date(), new Bond.Price());
    }

    public static double atmRate(Bond bond,
                                 YieldTermStructure discountCurve,
                                 Date settlementDate) {
        return atmRate(bond, discountCurve, settlementDate, new Bond.Price());
    }

    public static double atmRate(Bond bond,
                                 YieldTermStructure discountCurve,
                                 Date settlementDate,
                                 Bond.Price price) {
        settlementDate = settlement(bond, settlementDate);

        requireTradable(bond, settlementDate);

        double npv = Double.NaN;
        if (price.isValid()) {
            double dirtyPrice = price.amount()
                    + (price.type() == Bond.Price.Type.CLEAN
                    ? bond.accruedAmount(settlementDate) : 0.0);

            double currentNotional = bond.notional(settlementDate);
            npv = dirtyPrice / 100.0 * currentNotional;
        }

        return CashFlows.atmRate(bond.cashflows(), discountCurve,
                false, settlementDate, settlementDate, npv);
    }

    //! \name Yield functions
    public static double cleanPrice(Bond bond, InterestRate yield) {
        return cleanPrice(bond, yield, new Date());
    }

    public static double cleanPrice(Bond bond, InterestRate yield, Date settlementDate) {
        return dirtyPrice(bond, yield, settlementDate) - bond.accruedAmount(settlementDate);
    }

    public static double cleanPrice(Bond bond,
                                    double yield,
                                    DayCounter dayCounter,
                                    Compounding compounding,
                                    Frequency frequency) {
        return cleanPrice(bond, yield, dayCounter, compounding, frequency, new Date());
    }

    public static double cleanPrice(Bond bond,
                                    double yield,
                                    DayCounter dayCounter,
                                    Compounding compounding,
                                    Frequency frequency,
                                    Date settlementDate) {
        return cleanPrice(bond,
                new InterestRate(yield, dayCounter, compounding, frequency),
                settlementDate);
    }

    public static double dirtyPrice(Bond bond, InterestRate yield) {
        return dirtyPrice(bond, yield, new Date());
    }

    public static double dirtyPrice(Bond bond, InterestRate yield, Date settlementDate) {
        settlementDate = settlement(bond, settlementDate);

        requireTradable(bond, settlementDate);

        return CashFlows.npv(bond.cashflows(), yield,
                false, settlementDate, settlementDate)
                * 100.0 / bond.notional(settlementDate);
    }

    public static double dirtyPrice(Bond bond,
                                    double yield,
                                    DayCounter dayCounter,
                                    Compounding compounding,
                                    Frequency frequency) {
        return dirtyPrice(bond, yield, dayCounter, compounding, frequency, new Date());
    }

    public static double dirtyPrice(Bond bond,
                                    double yield,
                                    DayCounter dayCounter,
                                    Compounding compounding,
                                    Frequency frequency,
                                    Date settlementDate) {
        return dirtyPrice(bond,
                new InterestRate(yield, dayCounter, compounding, frequency),
                settlementDate);
    }

    public static double bps(Bond bond, InterestRate yield) {
        return bps(bond, yield, new Date());
    }

    public static double bps(Bond bond, InterestRate yield, Date settlementDate) {
        settlementDate = settlement(bond, settlementDate);

        requireTradable(bond, settlementDate);

        return CashFlows.bps(bond.cashflows(), yield,
                false, settlementDate, settlementDate)
                * 100.0 / bond.notional(settlementDate);
    }

    public static double bps(Bond bond,
                             double yield,
                             DayCounter dayCounter,
                             Compounding compounding,
                             Frequency frequency) {
        return bps(bond, yield, dayCounter, compounding, frequency, new Date());
    }

    public static double bps(Bond bond,
                             double yield,
                             DayCounter dayCounter,
                             Compounding compounding,
                             Frequency frequency,
                             Date settlementDate) {
        return bps(bond, new InterestRate(yield, dayCounter, compounding, frequency),
                settlementDate);
    }

    public static double yield(Bond bond,
                               Bond.Price price,
                               DayCounter dayCounter,
                               Compounding compounding,
                               Frequency frequency) {
        return BondFunctions.yield(bond, price, dayCounter, compounding, frequency,
                new Date(), 1.0e-10, 100, 0.05);
    }

    public static double yield(Bond bond,
                               Bond.Price price,
                               DayCounter dayCounter,
                               Compounding compounding,
                               Frequency frequency,
                               Date settlementDate) {
        return BondFunctions.yield(bond, price, dayCounter, compounding, frequency,
                settlementDate, 1.0e-10, 100, 0.05);
    }

    public static double yield(Bond bond,
                               Bond.Price price,
                               DayCounter dayCounter,
                               Compounding compounding,
                               Frequency frequency,
                               Date settlementDate,
                               double accuracy,
                               int maxIterations) {
        return BondFunctions.yield(bond, price, dayCounter, compounding, frequency,
                settlementDate, accuracy, maxIterations, 0.05);
    }

    public static double yield(Bond bond,
                               Bond.Price price,
                               DayCounter dayCounter,
                               Compounding compounding,
                               Frequency frequency,
                               Date settlementDate,
                               double accuracy,
                               int maxIterations,
                               double guess) {
        settlementDate = settlement(bond, settlementDate);

        requireTradable(bond, settlementDate);

        double amount = price.amount();

        if (price.type() == Bond.Price.Type.CLEAN)
            amount += bond.accruedAmount(settlementDate);

        amount /= 100.0 / bond.notional(settlementDate);

        return CashFlows.yield(bond.cashflows(), amount, dayCounter,
                compounding, frequency, false, settlementDate,
                settlementDate, accuracy, maxIterations, guess);
    }

    //! \name Duration and convexity
    /**
     * Duration of the whole bond cash-flow leg, in years. This is a thin
     * adapter of {@link CashFlows#duration(java.util.List, InterestRate,
     * Duration.Type, Boolean, Date, Date)}; unlike clean/dirty prices and
     * accrued amounts it is <b>not</b> rescaled by 100/notional. The default
     * type is {@link Duration.Type#Modified}.
     */
    public static double duration(Bond bond, InterestRate yield) {
        return duration(bond, yield, Duration.Type.Modified, new Date());
    }

    public static double duration(Bond bond, InterestRate yield, Duration.Type type) {
        return duration(bond, yield, type, new Date());
    }

    public static double duration(Bond bond,
                                  InterestRate yield,
                                  Duration.Type type,
                                  Date settlementDate) {
        settlementDate = settlement(bond, settlementDate);

        requireTradable(bond, settlementDate);

        return CashFlows.duration(bond.cashflows(), yield, type,
                false, settlementDate, settlementDate);
    }

    public static double duration(Bond bond,
                                  double yield,
                                  DayCounter dayCounter,
                                  Compounding compounding,
                                  Frequency frequency) {
        return duration(bond, yield, dayCounter, compounding, frequency,
                Duration.Type.Modified, new Date());
    }

    public static double duration(Bond bond,
                                  double yield,
                                  DayCounter dayCounter,
                                  Compounding compounding,
                                  Frequency frequency,
                                  Duration.Type type) {
        return duration(bond, yield, dayCounter, compounding, frequency, type, new Date());
    }

    public static double duration(Bond bond,
                                  double yield,
                                  DayCounter dayCounter,
                                  Compounding compounding,
                                  Frequency frequency,
                                  Duration.Type type,
                                  Date settlementDate) {
        return duration(bond,
                new InterestRate(yield, dayCounter, compounding, frequency),
                type, settlementDate);
    }

    /**
     * Convexity of the whole bond cash-flow leg. This is the
     * {@link CashFlows#convexity(java.util.List, InterestRate, Boolean, Date,
     * Date)} value of the whole leg, not the convexity of a per-100 price:
     * like duration it is not rescaled by 100/notional. The official
     * Thirty/360 test divides this number by 100 itself before comparing with
     * the Bloomberg value.
     */
    public static double convexity(Bond bond, InterestRate yield) {
        return convexity(bond, yield, new Date());
    }

    public static double convexity(Bond bond,
                                   InterestRate yield,
                                   Date settlementDate) {
        settlementDate = settlement(bond, settlementDate);

        requireTradable(bond, settlementDate);

        return CashFlows.convexity(bond.cashflows(), yield,
                false, settlementDate, settlementDate);
    }

    public static double convexity(Bond bond,
                                   double yield,
                                   DayCounter dayCounter,
                                   Compounding compounding,
                                   Frequency frequency) {
        return convexity(bond, yield, dayCounter, compounding, frequency, new Date());
    }

    public static double convexity(Bond bond,
                                   double yield,
                                   DayCounter dayCounter,
                                   Compounding compounding,
                                   Frequency frequency,
                                   Date settlementDate) {
        return convexity(bond,
                new InterestRate(yield, dayCounter, compounding, frequency),
                settlementDate);
    }

    //! \name Risk functions
    public static double basisPointValue(Bond bond, InterestRate yield) {
        return basisPointValue(bond, yield, new Date());
    }

    public static double basisPointValue(Bond bond,
                                         InterestRate yield,
                                         Date settlementDate) {
        settlementDate = settlement(bond, settlementDate);

        requireTradable(bond, settlementDate);

        // No notional rescaling: CashFlows already returns a cash amount.
        return CashFlows.basisPointValue(bond.cashflows(), yield,
                false, settlementDate, settlementDate);
    }

    public static double basisPointValue(Bond bond,
                                         double yield,
                                         DayCounter dayCounter,
                                         Compounding compounding,
                                         Frequency frequency) {
        return basisPointValue(bond, yield, dayCounter, compounding, frequency, new Date());
    }

    public static double basisPointValue(Bond bond,
                                         double yield,
                                         DayCounter dayCounter,
                                         Compounding compounding,
                                         Frequency frequency,
                                         Date settlementDate) {
        return basisPointValue(bond,
                new InterestRate(yield, dayCounter, compounding, frequency),
                settlementDate);
    }

    public static double yieldValueBasisPoint(Bond bond, InterestRate yield) {
        return yieldValueBasisPoint(bond, yield, new Date());
    }

    public static double yieldValueBasisPoint(Bond bond,
                                              InterestRate yield,
                                              Date settlementDate) {
        settlementDate = settlement(bond, settlementDate);

        requireTradable(bond, settlementDate);

        // No notional rescaling, as in the official adapter.
        return CashFlows.yieldValueBasisPoint(bond.cashflows(), yield,
                false, settlementDate, settlementDate);
    }

    public static double yieldValueBasisPoint(Bond bond,
                                              double yield,
                                              DayCounter dayCounter,
                                              Compounding compounding,
                                              Frequency frequency) {
        return yieldValueBasisPoint(bond, yield, dayCounter, compounding, frequency,
                new Date());
    }

    public static double yieldValueBasisPoint(Bond bond,
                                              double yield,
                                              DayCounter dayCounter,
                                              Compounding compounding,
                                              Frequency frequency,
                                              Date settlementDate) {
        return yieldValueBasisPoint(bond,
                new InterestRate(yield, dayCounter, compounding, frequency),
                settlementDate);
    }

    //! \name Z-spread functions
    public static double cleanPrice(Bond bond,
                                    YieldTermStructure discountCurve,
                                    double zSpread,
                                    Compounding compounding,
                                    Frequency frequency) {
        return cleanPrice(bond, discountCurve, zSpread, compounding, frequency, new Date());
    }

    public static double cleanPrice(Bond bond,
                                    YieldTermStructure discountCurve,
                                    double zSpread,
                                    Compounding compounding,
                                    Frequency frequency,
                                    Date settlementDate) {
        settlementDate = settlement(bond, settlementDate);
        return dirtyPrice(bond, discountCurve, zSpread, compounding, frequency, settlementDate)
                - bond.accruedAmount(settlementDate);
    }

    public static double dirtyPrice(Bond bond,
                                    YieldTermStructure discountCurve,
                                    double zSpread,
                                    Compounding compounding,
                                    Frequency frequency) {
        return dirtyPrice(bond, discountCurve, zSpread, compounding, frequency, new Date());
    }

    public static double dirtyPrice(Bond bond,
                                    YieldTermStructure discountCurve,
                                    double zSpread,
                                    Compounding compounding,
                                    Frequency frequency,
                                    Date settlementDate) {
        settlementDate = settlement(bond, settlementDate);

        requireTradable(bond, settlementDate);

        return CashFlows.npv(bond.cashflows(), discountCurve, zSpread,
                compounding, frequency, false, settlementDate, settlementDate)
                * 100.0 / bond.notional(settlementDate);
    }

    public static double zSpread(Bond bond,
                                 Bond.Price price,
                                 YieldTermStructure discountCurve,
                                 Compounding compounding,
                                 Frequency frequency) {
        return zSpread(bond, price, discountCurve, compounding, frequency,
                new Date(), 1.0e-10, 100, 0.0);
    }

    public static double zSpread(Bond bond,
                                 Bond.Price price,
                                 YieldTermStructure discountCurve,
                                 Compounding compounding,
                                 Frequency frequency,
                                 Date settlementDate) {
        return zSpread(bond, price, discountCurve, compounding, frequency,
                settlementDate, 1.0e-10, 100, 0.0);
    }

    public static double zSpread(Bond bond,
                                 Bond.Price price,
                                 YieldTermStructure discountCurve,
                                 Compounding compounding,
                                 Frequency frequency,
                                 Date settlementDate,
                                 double accuracy,
                                 int maxIterations) {
        return zSpread(bond, price, discountCurve, compounding, frequency,
                settlementDate, accuracy, maxIterations, 0.0);
    }

    public static double zSpread(Bond bond,
                                 Bond.Price price,
                                 YieldTermStructure discountCurve,
                                 Compounding compounding,
                                 Frequency frequency,
                                 Date settlementDate,
                                 double accuracy,
                                 int maxIterations,
                                 double guess) {
        settlementDate = settlement(bond, settlementDate);

        requireTradable(bond, settlementDate);

        double dirtyPrice = price.amount()
                + (price.type() == Bond.Price.Type.CLEAN
                ? bond.accruedAmount(settlementDate) : 0.0);

        dirtyPrice /= 100.0 / bond.notional(settlementDate);

        return CashFlows.zSpread(bond.cashflows(), dirtyPrice, discountCurve,
                compounding, frequency, false, settlementDate, settlementDate,
                accuracy, maxIterations, guess);
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private static Date settlement(Bond bond, Date settlementDate) {
        if (settlementDate == null || TimeUtils.equals(settlementDate, new Date()))
            return bond.settlementDate();
        return settlementDate.copy();
    }

    private static void requireTradable(Bond bond, Date settlementDate) {
        QL_REQUIRE(isTradable(bond, settlementDate),
                "non tradable at " + settlementDate
                        + " (maturity being " + bond.maturityDate() + ")");
    }
}
