package jquant.cashflows;

import jquant.CashFlow;
import jquant.Compounding;
import jquant.Handle;
import jquant.InterestRate;
import jquant.Quote;
import jquant.Settings;
import jquant.math.Function;
import jquant.math.Solver1D;
import jquant.math.solvers1d.Brent;
import jquant.math.solvers1d.NewtonSafe;
import jquant.quotes.SimpleQuote;
import jquant.termstructures.YieldTermStructure;
import jquant.termstructures.yield.ZeroSpreadedTermStructure;
import jquant.time.Date;
import jquant.time.DayCounter;
import jquant.time.Frequency;
import jquant.time.TimeUtils;

import java.util.List;
import java.util.Optional;

import static jquant.math.CommonUtil.QL_FAIL;
import static jquant.math.CommonUtil.QL_REQUIRE;

//! %cashflow-analysis functions
/*! \todo add tests */
public class CashFlows {
    private CashFlows() {
    }

    private static boolean isNullDate(final Date d) {
        return d == null || d.serialNumber() == 0;
    }

    private static Date settlementDate(final Date settlementDate) {
        if (isNullDate(settlementDate))
            return Settings.instance.evaluationDate().Date();
        return settlementDate;
    }

    private static Date npvDate(final Date npvDate, final Date settlementDate) {
        if (isNullDate(npvDate))
            return settlementDate;
        return npvDate;
    }

    private static boolean include(Boolean includeSettlementDateFlows) {
        return includeSettlementDateFlows != null && includeSettlementDateFlows;
    }

    // Date inspectors

    public static Date startDate(final List<CashFlow> leg) {
        QL_REQUIRE(!leg.isEmpty(), "empty leg");

        Date d = Date.maxDate();
        for (CashFlow i : leg) {
            if (i instanceof Coupon c)
                d = TimeUtils.min(d, c.accrualStartDate());
            else
                d = TimeUtils.min(d, i.date());
        }
        return d;
    }

    public static Date maturityDate(final List<CashFlow> leg) {
        QL_REQUIRE(!leg.isEmpty(), "empty leg");

        Date d = Date.minDate();
        for (CashFlow i : leg) {
            if (i instanceof Coupon c)
                d = TimeUtils.max(d, c.accrualEndDate());
            else
                d = TimeUtils.max(d, i.date());
        }
        return d;
    }

    public static boolean isExpired(final List<CashFlow> leg) {
        return isExpired(leg, null, new Date());
    }

    public static boolean isExpired(final List<CashFlow> leg,
                                    Boolean includeSettlementDateFlows,
                                    Date settlementDate) {
        if (leg.isEmpty())
            return true;

        settlementDate = settlementDate(settlementDate);

        for (int i = leg.size(); i > 0; --i)
            if (!leg.get(i - 1).hasOccurred(settlementDate,
                    Optional.ofNullable(includeSettlementDateFlows)))
                return false;
        return true;
    }

    // CashFlow functions

    //! the last cashflow paying before or at the given date
    public static Optional<CashFlow> previousCashFlow(final List<CashFlow> leg) {
        return previousCashFlow(leg, null, new Date());
    }

    public static Optional<CashFlow> previousCashFlow(final List<CashFlow> leg,
                                                      Boolean includeSettlementDateFlows,
                                                      Date settlementDate) {
        if (leg.isEmpty())
            return Optional.empty();

        settlementDate = settlementDate(settlementDate);

        for (int i = leg.size() - 1; i >= 0; --i) {
            if (leg.get(i).hasOccurred(settlementDate,
                    Optional.ofNullable(includeSettlementDateFlows)))
                return Optional.of(leg.get(i));
        }
        return Optional.empty();
    }

    //! the first cashflow paying after the given date
    public static Optional<CashFlow> nextCashFlow(final List<CashFlow> leg) {
        return nextCashFlow(leg, null, new Date());
    }

    public static Optional<CashFlow> nextCashFlow(final List<CashFlow> leg,
                                                  Boolean includeSettlementDateFlows,
                                                  Date settlementDate) {
        if (leg.isEmpty())
            return Optional.empty();

        settlementDate = settlementDate(settlementDate);

        for (CashFlow cashFlow : leg) {
            if (!cashFlow.hasOccurred(settlementDate,
                    Optional.ofNullable(includeSettlementDateFlows)))
                return Optional.of(cashFlow);
        }
        return Optional.empty();
    }

    public static Date previousCashFlowDate(final List<CashFlow> leg) {
        return previousCashFlowDate(leg, null, new Date());
    }

    public static Date previousCashFlowDate(final List<CashFlow> leg,
                                            Boolean includeSettlementDateFlows,
                                            Date settlementDate) {
        Optional<CashFlow> cf = previousCashFlow(leg, includeSettlementDateFlows, settlementDate);
        return cf.map(CashFlow::date).orElseGet(Date::new);
    }

    public static Date nextCashFlowDate(final List<CashFlow> leg) {
        return nextCashFlowDate(leg, null, new Date());
    }

    public static Date nextCashFlowDate(final List<CashFlow> leg,
                                        Boolean includeSettlementDateFlows,
                                        Date settlementDate) {
        Optional<CashFlow> cf = nextCashFlow(leg, includeSettlementDateFlows, settlementDate);
        return cf.map(CashFlow::date).orElseGet(Date::new);
    }

    public static double previousCashFlowAmount(final List<CashFlow> leg) {
        return previousCashFlowAmount(leg, null, new Date());
    }

    public static double previousCashFlowAmount(final List<CashFlow> leg,
                                                Boolean includeSettlementDateFlows,
                                                Date settlementDate) {
        Optional<CashFlow> cf = previousCashFlow(leg, includeSettlementDateFlows, settlementDate);
        if (cf.isEmpty())
            return 0.0;

        int index = leg.indexOf(cf.get());
        Date paymentDate = cf.get().date();
        double result = 0.0;
        for (int i = index; i >= 0 && TimeUtils.equals(leg.get(i).date(), paymentDate); --i)
            result += leg.get(i).amount();
        return result;
    }

    public static double nextCashFlowAmount(final List<CashFlow> leg) {
        return nextCashFlowAmount(leg, null, new Date());
    }

    public static double nextCashFlowAmount(final List<CashFlow> leg,
                                            Boolean includeSettlementDateFlows,
                                            Date settlementDate) {
        Optional<CashFlow> cf = nextCashFlow(leg, includeSettlementDateFlows, settlementDate);
        if (cf.isEmpty())
            return 0.0;

        int index = leg.indexOf(cf.get());
        Date paymentDate = cf.get().date();
        double result = 0.0;
        for (int i = index; i < leg.size() && TimeUtils.equals(leg.get(i).date(), paymentDate); ++i)
            result += leg.get(i).amount();
        return result;
    }

    // Coupon utility functions

    private static double aggregateRate(final List<CashFlow> leg,
                                        int first,
                                        final int last,
                                        int step) {
        if (first == last)
            return 0.0;

        Date paymentDate = leg.get(first).date();
        boolean firstCouponFound = false;
        double nominal = 0.0;
        double accrualPeriod = 0.0;
        DayCounter dc = new DayCounter();
        double result = 0.0;
        for (; first != last && TimeUtils.equals(leg.get(first).date(), paymentDate); first += step) {
            CashFlow cashFlow = leg.get(first);
            if (cashFlow instanceof Coupon cp) {
                if (firstCouponFound) {
                    QL_REQUIRE(nominal == cp.nominal()
                                    && accrualPeriod == cp.accrualPeriod()
                                    && TimeUtils.equals(dc, cp.dayCounter()),
                            "cannot aggregate two different coupons on " + paymentDate);
                } else {
                    firstCouponFound = true;
                    nominal = cp.nominal();
                    accrualPeriod = cp.accrualPeriod();
                    dc = cp.dayCounter();
                }
                result += cp.rate();
            }
        }
        QL_REQUIRE(firstCouponFound,
                "no coupon paid at cashflow date " + paymentDate);
        return result;
    }

    public static double previousCouponRate(final List<CashFlow> leg) {
        return previousCouponRate(leg, null, new Date());
    }

    public static double previousCouponRate(final List<CashFlow> leg,
                                            Boolean includeSettlementDateFlows,
                                            Date settlementDate) {
        Optional<CashFlow> cf = previousCashFlow(leg, includeSettlementDateFlows, settlementDate);
        if (cf.isEmpty())
            return aggregateRate(leg, 0, 0, -1);
        int index = leg.indexOf(cf.get());
        return aggregateRate(leg, index, -1, -1);
    }

    public static double nextCouponRate(final List<CashFlow> leg) {
        return nextCouponRate(leg, null, new Date());
    }

    public static double nextCouponRate(final List<CashFlow> leg,
                                        Boolean includeSettlementDateFlows,
                                        Date settlementDate) {
        Optional<CashFlow> cf = nextCashFlow(leg, includeSettlementDateFlows, settlementDate);
        if (cf.isEmpty())
            return aggregateRate(leg, 0, 0, 1);
        int index = leg.indexOf(cf.get());
        return aggregateRate(leg, index, leg.size(), 1);
    }

    public static double nominal(final List<CashFlow> leg) {
        return nominal(leg, null, new Date());
    }

    public static double nominal(final List<CashFlow> leg,
                                 Boolean includeSettlementDateFlows,
                                 Date settlementDate) {
        Optional<CashFlow> cf = nextCashFlow(leg, includeSettlementDateFlows, settlementDate);
        if (cf.isEmpty())
            return 0.0;

        int index = leg.indexOf(cf.get());
        Date paymentDate = cf.get().date();
        for (int i = index; i < leg.size()
                && TimeUtils.equals(leg.get(i).date(), paymentDate); ++i) {
            if (leg.get(i) instanceof Coupon cp)
                return cp.nominal();
        }
        return 0.0;
    }

    public static Date accrualStartDate(final List<CashFlow> leg) {
        return accrualStartDate(leg, null, new Date());
    }

    public static Date accrualStartDate(final List<CashFlow> leg,
                                        Boolean includeSettlementDateFlows,
                                        Date settlementDate) {
        Optional<CashFlow> cf = nextCashFlow(leg, includeSettlementDateFlows, settlementDate);
        if (cf.isEmpty())
            return new Date();

        int index = leg.indexOf(cf.get());
        Date paymentDate = cf.get().date();
        for (int i = index; i < leg.size()
                && TimeUtils.equals(leg.get(i).date(), paymentDate); ++i) {
            if (leg.get(i) instanceof Coupon cp)
                return cp.accrualStartDate();
        }
        return new Date();
    }

    public static Date accrualEndDate(final List<CashFlow> leg) {
        return accrualEndDate(leg, null, new Date());
    }

    public static Date accrualEndDate(final List<CashFlow> leg,
                                      Boolean includeSettlementDateFlows,
                                      Date settlementDate) {
        Optional<CashFlow> cf = nextCashFlow(leg, includeSettlementDateFlows, settlementDate);
        if (cf.isEmpty())
            return new Date();

        int index = leg.indexOf(cf.get());
        Date paymentDate = cf.get().date();
        for (int i = index; i < leg.size()
                && TimeUtils.equals(leg.get(i).date(), paymentDate); ++i) {
            if (leg.get(i) instanceof Coupon cp)
                return cp.accrualEndDate();
        }
        return new Date();
    }

    public static Date referencePeriodStart(final List<CashFlow> leg) {
        return referencePeriodStart(leg, null, new Date());
    }

    public static Date referencePeriodStart(final List<CashFlow> leg,
                                            Boolean includeSettlementDateFlows,
                                            Date settlementDate) {
        Optional<CashFlow> cf = nextCashFlow(leg, includeSettlementDateFlows, settlementDate);
        if (cf.isEmpty())
            return new Date();

        int index = leg.indexOf(cf.get());
        Date paymentDate = cf.get().date();
        for (int i = index; i < leg.size()
                && TimeUtils.equals(leg.get(i).date(), paymentDate); ++i) {
            if (leg.get(i) instanceof Coupon cp)
                return cp.referencePeriodStart();
        }
        return new Date();
    }

    public static Date referencePeriodEnd(final List<CashFlow> leg) {
        return referencePeriodEnd(leg, null, new Date());
    }

    public static Date referencePeriodEnd(final List<CashFlow> leg,
                                          Boolean includeSettlementDateFlows,
                                          Date settlementDate) {
        Optional<CashFlow> cf = nextCashFlow(leg, includeSettlementDateFlows, settlementDate);
        if (cf.isEmpty())
            return new Date();

        int index = leg.indexOf(cf.get());
        Date paymentDate = cf.get().date();
        for (int i = index; i < leg.size()
                && TimeUtils.equals(leg.get(i).date(), paymentDate); ++i) {
            if (leg.get(i) instanceof Coupon cp)
                return cp.referencePeriodEnd();
        }
        return new Date();
    }

    public static double accrualPeriod(final List<CashFlow> leg) {
        return accrualPeriod(leg, null, new Date());
    }

    public static double accrualPeriod(final List<CashFlow> leg,
                                       Boolean includeSettlementDateFlows,
                                       Date settlementDate) {
        Optional<CashFlow> cf = nextCashFlow(leg, includeSettlementDateFlows, settlementDate);
        if (cf.isEmpty())
            return 0.0;

        int index = leg.indexOf(cf.get());
        Date paymentDate = cf.get().date();
        for (int i = index; i < leg.size()
                && TimeUtils.equals(leg.get(i).date(), paymentDate); ++i) {
            if (leg.get(i) instanceof Coupon cp)
                return cp.accrualPeriod();
        }
        return 0.0;
    }

    public static int accrualDays(final List<CashFlow> leg) {
        return accrualDays(leg, null, new Date());
    }

    public static int accrualDays(final List<CashFlow> leg,
                                  Boolean includeSettlementDateFlows,
                                  Date settlementDate) {
        Optional<CashFlow> cf = nextCashFlow(leg, includeSettlementDateFlows, settlementDate);
        if (cf.isEmpty())
            return 0;

        int index = leg.indexOf(cf.get());
        Date paymentDate = cf.get().date();
        for (int i = index; i < leg.size()
                && TimeUtils.equals(leg.get(i).date(), paymentDate); ++i) {
            if (leg.get(i) instanceof Coupon cp)
                return cp.accrualDays();
        }
        return 0;
    }

    public static double accruedPeriod(final List<CashFlow> leg) {
        return accruedPeriod(leg, null, new Date());
    }

    public static double accruedPeriod(final List<CashFlow> leg,
                                       Boolean includeSettlementDateFlows,
                                       Date settlementDate) {
        settlementDate = settlementDate(settlementDate);

        Optional<CashFlow> cf = nextCashFlow(leg, includeSettlementDateFlows, settlementDate);
        if (cf.isEmpty())
            return 0.0;

        int index = leg.indexOf(cf.get());
        Date paymentDate = cf.get().date();
        for (int i = index; i < leg.size()
                && TimeUtils.equals(leg.get(i).date(), paymentDate); ++i) {
            if (leg.get(i) instanceof Coupon cp)
                return cp.accruedPeriod(settlementDate);
        }
        return 0.0;
    }

    public static int accruedDays(final List<CashFlow> leg) {
        return accruedDays(leg, null, new Date());
    }

    public static int accruedDays(final List<CashFlow> leg,
                                  Boolean includeSettlementDateFlows,
                                  Date settlementDate) {
        settlementDate = settlementDate(settlementDate);

        Optional<CashFlow> cf = nextCashFlow(leg, includeSettlementDateFlows, settlementDate);
        if (cf.isEmpty())
            return 0;

        int index = leg.indexOf(cf.get());
        Date paymentDate = cf.get().date();
        for (int i = index; i < leg.size()
                && TimeUtils.equals(leg.get(i).date(), paymentDate); ++i) {
            if (leg.get(i) instanceof Coupon cp)
                return cp.accruedDays(settlementDate);
        }
        return 0;
    }

    public static double accruedAmount(final List<CashFlow> leg) {
        return accruedAmount(leg, null, new Date());
    }

    public static double accruedAmount(final List<CashFlow> leg,
                                       Boolean includeSettlementDateFlows,
                                       Date settlementDate) {
        settlementDate = settlementDate(settlementDate);

        Optional<CashFlow> cf = nextCashFlow(leg, includeSettlementDateFlows, settlementDate);
        if (cf.isEmpty())
            return 0.0;

        int index = leg.indexOf(cf.get());
        Date paymentDate = cf.get().date();
        double result = 0.0;
        for (int i = index; i < leg.size()
                && TimeUtils.equals(leg.get(i).date(), paymentDate); ++i) {
            if (leg.get(i) instanceof Coupon cp)
                result += cp.accruedAmount(settlementDate);
        }
        return result;
    }

    // YieldTermStructure utility functions

    private static final double basisPoint_ = 1.0e-4;

    private static final class BPSCalculator {
        private final YieldTermStructure discountCurve;
        private double bps = 0.0;
        private double nonSensNPV = 0.0;

        BPSCalculator(YieldTermStructure discountCurve) {
            this.discountCurve = discountCurve;
        }

        void accept(CashFlow c) {
            if (c instanceof Coupon cpn)
                bps += cpn.nominal() * cpn.accrualPeriod()
                        * discountCurve.discount(c.date(), false);
            else
                nonSensNPV += c.amount() * discountCurve.discount(c.date(), false);
        }

        double bps() {
            return bps;
        }

        double nonSensNPV() {
            return nonSensNPV;
        }
    }

    //! NPV of the cash flows.
    public static double npv(final List<CashFlow> leg,
                             final YieldTermStructure discountCurve) {
        return npv(leg, discountCurve, null, new Date(), new Date());
    }

    public static double npv(final List<CashFlow> leg,
                             final YieldTermStructure discountCurve,
                             Boolean includeSettlementDateFlows,
                             Date settlementDate,
                             Date npvDate) {
        if (leg.isEmpty())
            return 0.0;

        settlementDate = settlementDate(settlementDate);
        npvDate = npvDate(npvDate, settlementDate);

        double totalNPV = 0.0;
        for (CashFlow i : leg) {
            if (!i.hasOccurred(settlementDate,
                    Optional.ofNullable(includeSettlementDateFlows))
                    && !i.tradingExCoupon(settlementDate))
                totalNPV += i.amount() * discountCurve.discount(i.date(), false);
        }

        return totalNPV / discountCurve.discount(npvDate, false);
    }

    //! Basis-point sensitivity of the cash flows.
    public static double bps(final List<CashFlow> leg,
                             final YieldTermStructure discountCurve) {
        return bps(leg, discountCurve, null, new Date(), new Date());
    }

    public static double bps(final List<CashFlow> leg,
                             final YieldTermStructure discountCurve,
                             Boolean includeSettlementDateFlows,
                             Date settlementDate,
                             Date npvDate) {
        if (leg.isEmpty())
            return 0.0;

        settlementDate = settlementDate(settlementDate);
        npvDate = npvDate(npvDate, settlementDate);

        BPSCalculator calc = new BPSCalculator(discountCurve);
        for (CashFlow i : leg) {
            if (!i.hasOccurred(settlementDate,
                    Optional.ofNullable(includeSettlementDateFlows))
                    && !i.tradingExCoupon(settlementDate))
                calc.accept(i);
        }
        return basisPoint_ * calc.bps() / discountCurve.discount(npvDate, false);
    }

    //! NPV and BPS of the cash flows.
    public record NpvBps(double npv, double bps) {
    }

    public static NpvBps npvbps(final List<CashFlow> leg,
                                final YieldTermStructure discountCurve) {
        return npvbps(leg, discountCurve, null, new Date(), new Date());
    }

    public static NpvBps npvbps(final List<CashFlow> leg,
                                final YieldTermStructure discountCurve,
                                Boolean includeSettlementDateFlows,
                                Date settlementDate,
                                Date npvDate) {
        double npv = 0.0;
        double bps = 0.0;

        if (leg.isEmpty())
            return new NpvBps(npv, bps);

        settlementDate = settlementDate(settlementDate);
        npvDate = npvDate(npvDate, settlementDate);

        for (CashFlow cf : leg) {
            if (!cf.hasOccurred(settlementDate,
                    Optional.ofNullable(includeSettlementDateFlows))
                    && !cf.tradingExCoupon(settlementDate)) {
                Coupon cp = cf instanceof Coupon ? (Coupon) cf : null;
                double df = discountCurve.discount(cf.date(), false);
                npv += cf.amount() * df;
                if (cp != null)
                    bps += cp.nominal() * cp.accrualPeriod() * df;
            }
        }
        double d = discountCurve.discount(npvDate, false);
        npv /= d;
        bps = basisPoint_ * bps / d;

        return new NpvBps(npv, bps);
    }

    //! At-the-money rate of the cash flows.
    public static double atmRate(final List<CashFlow> leg,
                                 final YieldTermStructure discountCurve) {
        return atmRate(leg, discountCurve, null, new Date(), new Date(), Double.NaN);
    }

    public static double atmRate(final List<CashFlow> leg,
                                 final YieldTermStructure discountCurve,
                                 Boolean includeSettlementDateFlows,
                                 Date settlementDate,
                                 Date npvDate,
                                 double targetNpv) {
        if (leg.isEmpty())
            return 0.0;

        settlementDate = settlementDate(settlementDate);
        npvDate = npvDate(npvDate, settlementDate);

        double npv = 0.0;
        BPSCalculator calc = new BPSCalculator(discountCurve);
        for (CashFlow i : leg) {
            if (!i.hasOccurred(settlementDate,
                    Optional.ofNullable(includeSettlementDateFlows))
                    && !i.tradingExCoupon(settlementDate)) {
                npv += i.amount() * discountCurve.discount(i.date(), false);
                calc.accept(i);
            }
        }

        if (Double.isNaN(targetNpv))
            targetNpv = npv - calc.nonSensNPV();
        else {
            targetNpv *= discountCurve.discount(npvDate, false);
            targetNpv -= calc.nonSensNPV();
        }

        if (targetNpv == 0.0)
            return 0.0;

        double bps = calc.bps();
        QL_REQUIRE(bps != 0.0, "null bps: impossible atm rate");

        return targetNpv / bps;
    }

    // IRR utility functions

    private static int sign(double x) {
        if (x == 0.0)
            return 0;
        else if (x > 0.0)
            return 1;
        else
            return -1;
    }

    private static double getStepwiseDiscountTime(final CashFlow cashFlow,
                                                  final DayCounter dc,
                                                  final Date npvDate,
                                                  final Date lastDate) {
        Date cashFlowDate = cashFlow.date();
        Date refStartDate, refEndDate;
        Coupon coupon = cashFlow instanceof Coupon ? (Coupon) cashFlow : null;
        if (coupon != null) {
            refStartDate = coupon.referencePeriodStart();
            refEndDate = coupon.referencePeriodEnd();
        } else {
            if (TimeUtils.equals(lastDate, npvDate)) {
                // we don't have a previous coupon date,
                // so we fake it
                refStartDate = cashFlowDate.substract(TimeUtils.multiply(1, jquant.time.TimeUnit.YEARS));
            } else {
                refStartDate = lastDate;
            }
            refEndDate = cashFlowDate;
        }

        if (coupon != null && !TimeUtils.equals(lastDate, coupon.accrualStartDate())) {
            double couponPeriod = dc.yearFraction(coupon.accrualStartDate(),
                    cashFlowDate, refStartDate, refEndDate);
            double accruedPeriod = dc.yearFraction(coupon.accrualStartDate(),
                    lastDate, refStartDate, refEndDate);
            return couponPeriod - accruedPeriod;
        } else {
            return dc.yearFraction(lastDate, cashFlowDate,
                    refStartDate, refEndDate);
        }
    }

    private static InterestRate[] stepwiseYields(final InterestRate y) {
        switch (y.compounding()) {
            case SimpleThenCompounded:
            case CompoundedThenSimple:
                return new InterestRate[]{
                        new InterestRate(y.rate(), y.dayCounter(), Compounding.Compounded, y.frequency()),
                        new InterestRate(y.rate(), y.dayCounter(), Compounding.Simple, y.frequency())
                };
            default:
                return new InterestRate[]{y, y};
        }
    }

    private static InterestRate choose(final InterestRate main,
                                       final InterestRate exception,
                                       Compounding compounding,
                                       boolean inFirstPeriod,
                                       final Date cashFlowDate,
                                       final Date finalCashFlowDate) {
        // The exceptions are positional: the first interval is
        // simple for SimpleThenCompounded and the final interval is
        // simple for CompoundedThenSimple.
        switch (compounding) {
            case SimpleThenCompounded:
                return inFirstPeriod ? exception : main;
            case CompoundedThenSimple:
                return TimeUtils.equals(cashFlowDate, finalCashFlowDate) ? exception : main;
            default:
                return main;
        }
    }

    private static final class CashFlowResults {
        double npv = 0.0;
        double firstDerivative = 0.0;
        double secondDerivative = 0.0;
        double weightedTime = 0.0;
    }

    private static CashFlowResults cashFlowResults(final List<CashFlow> leg,
                                                   final InterestRate yield,
                                                   Boolean includeSettlementDateFlows,
                                                   final Date settlementDate,
                                                   final Date npvDate,
                                                   boolean computeFirstDerivative,
                                                   boolean computeSecondDerivative,
                                                   boolean computeWeightedTime) {
        CashFlowResults results = new CashFlowResults();
        double discount = 1.0;
        double firstDerivative = 0.0;
        double secondDerivative = 0.0;
        double t = 0.0;
        Date lastDate = npvDate;
        DayCounter dc = yield.dayCounter();
        boolean inFirstPeriod = true;
        double previousDiscount = 1.0;
        double previousFirstDerivative = 0.0;
        double previousSecondDerivative = 0.0;
        double thisDiscount = 1.0;
        double thisFirstDerivative = 0.0;
        double thisSecondDerivative = 0.0;

        InterestRate[] stepwise = stepwiseYields(yield);
        InterestRate y_main = stepwise[0];
        InterestRate y_exception = stepwise[1];

        for (CashFlow cashFlow : leg) {
            if (cashFlow.hasOccurred(settlementDate,
                    Optional.ofNullable(includeSettlementDateFlows)))
                continue;

            double amount = cashFlow.amount();
            if (cashFlow.tradingExCoupon(settlementDate))
                amount = 0.0;

            double dt = getStepwiseDiscountTime(cashFlow, dc, npvDate, lastDate);
            t += dt;

            previousDiscount = discount;
            if (computeFirstDerivative) {
                previousFirstDerivative = firstDerivative;
                if (computeSecondDerivative)
                    previousSecondDerivative = secondDerivative;
            }

            InterestRate y = choose(y_main, y_exception, yield.compounding(),
                    inFirstPeriod, cashFlow.date(), leg.get(leg.size() - 1).date());

            thisDiscount = y.discountFactor(dt);
            discount = previousDiscount * thisDiscount;

            if (computeFirstDerivative) {
                // Differentiate the product of this interval's discount
                // factor and all preceding factors.
                thisFirstDerivative = y.discountFactorFirstDerivative(dt);
                firstDerivative = previousFirstDerivative * thisDiscount
                        + previousDiscount * thisFirstDerivative;
                results.firstDerivative += amount * firstDerivative;
                if (computeSecondDerivative) {
                    thisSecondDerivative = y.discountFactorSecondDerivative(dt);
                    secondDerivative = previousSecondDerivative * thisDiscount
                            + 2.0 * previousFirstDerivative * thisFirstDerivative
                            + previousDiscount * thisSecondDerivative;
                    results.secondDerivative += amount * secondDerivative;
                }
            }

            results.npv += amount * discount;

            if (computeWeightedTime)
                results.weightedTime += t * amount * discount;

            lastDate = cashFlow.date();
            inFirstPeriod = false;
        }

        return results;
    }

    private static double simpleDuration(final List<CashFlow> leg,
                                         final InterestRate y,
                                         Boolean includeSettlementDateFlows,
                                         Date settlementDate,
                                         Date npvDate) {
        if (leg.isEmpty())
            return 0.0;

        settlementDate = settlementDate(settlementDate);
        npvDate = npvDate(npvDate, settlementDate);

        CashFlowResults results = cashFlowResults(
                leg, y, includeSettlementDateFlows, settlementDate, npvDate, false, false, true);
        if (results.npv == 0.0) // no cashflows
            return 0.0;
        return results.weightedTime / results.npv;
    }

    private static double modifiedDuration(final List<CashFlow> leg,
                                           final InterestRate y,
                                           Boolean includeSettlementDateFlows,
                                           Date settlementDate,
                                           Date npvDate) {
        if (leg.isEmpty())
            return 0.0;

        settlementDate = settlementDate(settlementDate);
        npvDate = npvDate(npvDate, settlementDate);

        CashFlowResults results = cashFlowResults(
                leg, y, includeSettlementDateFlows, settlementDate, npvDate, true, false, false);
        if (results.npv == 0.0) // no cashflows
            return 0.0;
        return -results.firstDerivative / results.npv;
    }

    private static double macaulayDuration(final List<CashFlow> leg,
                                           final InterestRate y,
                                           Boolean includeSettlementDateFlows,
                                           Date settlementDate,
                                           Date npvDate) {
        QL_REQUIRE(y.compounding() == Compounding.Compounded,
                "compounded rate required");

        return (1.0 + y.rate() / y.frequency().getValue())
                * modifiedDuration(leg, y,
                includeSettlementDateFlows,
                settlementDate, npvDate);
    }

    private static final class IrrFinder implements Function {
        private final List<CashFlow> leg_;
        private final double npv_;
        private final DayCounter dayCounter_;
        private final Compounding compounding_;
        private final Frequency frequency_;
        private final Boolean includeSettlementDateFlows_;
        private Date settlementDate_, npvDate_;

        IrrFinder(final List<CashFlow> leg,
                  double npv,
                  DayCounter dayCounter,
                  Compounding comp,
                  Frequency freq,
                  Boolean includeSettlementDateFlows,
                  Date settlementDate,
                  Date npvDate) {
            leg_ = leg;
            npv_ = npv;
            dayCounter_ = dayCounter;
            compounding_ = comp;
            frequency_ = freq;
            includeSettlementDateFlows_ = includeSettlementDateFlows;
            settlementDate_ = settlementDate;
            npvDate_ = npvDate;

            settlementDate_ = CashFlows.settlementDate(settlementDate_);
            npvDate_ = CashFlows.npvDate(npvDate_, settlementDate_);

            checkSign();
        }

        @Override
        public double value(double y) {
            InterestRate yield = new InterestRate(y, dayCounter_, compounding_, frequency_);
            double NPV = CashFlows.npv(leg_, yield,
                    includeSettlementDateFlows_,
                    settlementDate_, npvDate_);
            return NPV - npv_;
        }

        @Override
        public double derivative(double y) {
            InterestRate yield = new InterestRate(y, dayCounter_, compounding_, frequency_);
            double p = CashFlows.npv(leg_, yield, includeSettlementDateFlows_,
                    settlementDate_, npvDate_);
            return -modifiedDuration(leg_, yield,
                    includeSettlementDateFlows_,
                    settlementDate_, npvDate_) * p;
        }

        private void checkSign() {
            // depending on the sign of the market price, check that cash
            // flows of the opposite sign have been specified (otherwise
            // IRR is nonsensical.)

            int lastSign = sign(-npv_);
            int signChanges = 0;
            for (CashFlow i : leg_) {
                if (!i.hasOccurred(settlementDate_,
                        Optional.ofNullable(includeSettlementDateFlows_))
                        && !i.tradingExCoupon(settlementDate_)) {
                    int thisSign = sign(i.amount());
                    if (lastSign * thisSign < 0) // sign change
                        signChanges++;

                    if (thisSign != 0)
                        lastSign = thisSign;
                }
            }
            QL_REQUIRE(signChanges > 0,
                    "the given cash flows cannot result in the given market "
                            + "price due to their sign");
        }
    }

    //! NPV of the cash flows.
    public static double npv(final List<CashFlow> leg,
                             final InterestRate yield) {
        return npv(leg, yield, null, new Date(), new Date());
    }

    public static double npv(final List<CashFlow> leg,
                             final InterestRate y,
                             Boolean includeSettlementDateFlows,
                             Date settlementDate,
                             Date npvDate) {
        if (leg.isEmpty())
            return 0.0;

        settlementDate = settlementDate(settlementDate);
        npvDate = npvDate(npvDate, settlementDate);

        double npv = 0.0;
        double discount = 1.0;
        Date lastDate = npvDate;
        DayCounter dc = y.dayCounter();
        boolean inFirstPeriod = true;

        InterestRate[] stepwise = stepwiseYields(y);
        InterestRate y_main = stepwise[0];
        InterestRate y_exception = stepwise[1];

        for (CashFlow cashFlow : leg) {
            if (cashFlow.hasOccurred(settlementDate,
                    Optional.ofNullable(includeSettlementDateFlows)))
                continue;

            double amount = cashFlow.amount();
            if (cashFlow.tradingExCoupon(settlementDate))
                amount = 0.0;

            InterestRate y_i = choose(y_main, y_exception, y.compounding(),
                    inFirstPeriod, cashFlow.date(), leg.get(leg.size() - 1).date());

            double dt = getStepwiseDiscountTime(cashFlow, dc, npvDate, lastDate);
            discount *= y_i.discountFactor(dt);
            npv += amount * discount;

            lastDate = cashFlow.date();
            inFirstPeriod = false;
        }

        return npv;
    }

    public static double npv(final List<CashFlow> leg,
                             double yield,
                             final DayCounter dc,
                             Compounding comp,
                             Frequency freq) {
        return npv(leg, new InterestRate(yield, dc, comp, freq));
    }

    public static double npv(final List<CashFlow> leg,
                             double yield,
                             final DayCounter dc,
                             Compounding comp,
                             Frequency freq,
                             Boolean includeSettlementDateFlows,
                             Date settlementDate,
                             Date npvDate) {
        return npv(leg, new InterestRate(yield, dc, comp, freq),
                includeSettlementDateFlows,
                settlementDate, npvDate);
    }

    //! Basis-point sensitivity of the cash flows.
    public static double bps(final List<CashFlow> leg,
                             final InterestRate yield) {
        return bps(leg, yield, null, new Date(), new Date());
    }

    public static double bps(final List<CashFlow> leg,
                             final InterestRate yield,
                             Boolean includeSettlementDateFlows,
                             Date settlementDate,
                             Date npvDate) {
        if (leg.isEmpty())
            return 0.0;

        settlementDate = settlementDate(settlementDate);
        npvDate = npvDate(npvDate, settlementDate);

        double bps = 0.0;
        double discount = 1.0;
        Date lastDate = npvDate;
        DayCounter dc = yield.dayCounter();
        boolean inFirstPeriod = true;

        InterestRate[] stepwise = stepwiseYields(yield);
        InterestRate y_main = stepwise[0];
        InterestRate y_exception = stepwise[1];

        for (CashFlow cashFlow : leg) {
            if (cashFlow.hasOccurred(settlementDate,
                    Optional.ofNullable(includeSettlementDateFlows)))
                continue;
            Coupon coupon = cashFlow instanceof Coupon ? (Coupon) cashFlow : null;
            if (coupon == null)
                continue;

            InterestRate y_i = choose(y_main, y_exception, yield.compounding(),
                    inFirstPeriod, cashFlow.date(), leg.get(leg.size() - 1).date());

            double dt = getStepwiseDiscountTime(cashFlow, dc, npvDate, lastDate);
            discount *= y_i.discountFactor(dt);
            bps += coupon.nominal() * coupon.accrualPeriod() * discount;
            lastDate = cashFlow.date();
            inFirstPeriod = false;
        }

        return basisPoint_ * bps;
    }

    public static double bps(final List<CashFlow> leg,
                             double yield,
                             final DayCounter dc,
                             Compounding comp,
                             Frequency freq) {
        return bps(leg, new InterestRate(yield, dc, comp, freq));
    }

    public static double bps(final List<CashFlow> leg,
                             double yield,
                             final DayCounter dc,
                             Compounding comp,
                             Frequency freq,
                             Boolean includeSettlementDateFlows,
                             Date settlementDate,
                             Date npvDate) {
        return bps(leg, new InterestRate(yield, dc, comp, freq),
                includeSettlementDateFlows,
                settlementDate, npvDate);
    }

    //! Implied internal rate of return.
    public static double yield(final List<CashFlow> leg,
                               double npv,
                               final DayCounter dayCounter,
                               Compounding compounding,
                               Frequency frequency) {
        return CashFlows.yield(leg, npv, dayCounter, compounding, frequency,
                null, new Date(), new Date(), 1.0e-10, 100, 0.05);
    }

    public static double yield(final List<CashFlow> leg,
                               double npv,
                               final DayCounter dayCounter,
                               Compounding compounding,
                               Frequency frequency,
                               Boolean includeSettlementDateFlows,
                               Date settlementDate,
                               Date npvDate,
                               double accuracy,
                               int maxIterations,
                               double guess) {
        NewtonSafe solver = new NewtonSafe();
        solver.setMaxEvaluations(maxIterations);
        return CashFlows.yield(solver, leg, npv, dayCounter, compounding, frequency,
                includeSettlementDateFlows, settlementDate, npvDate,
                accuracy, guess);
    }

    public static double yield(final Solver1D solver,
                               final List<CashFlow> leg,
                               double npv,
                               final DayCounter dayCounter,
                               Compounding compounding,
                               Frequency frequency,
                               Boolean includeSettlementDateFlows,
                               Date settlementDate,
                               Date npvDate,
                               double accuracy,
                               double guess) {
        IrrFinder objFunction = new IrrFinder(leg, npv, dayCounter, compounding,
                frequency, includeSettlementDateFlows,
                settlementDate, npvDate);
        return solver.solve(objFunction, accuracy, guess, guess / 10.0);
    }

    public static double yield(final Solver1D solver,
                               final List<CashFlow> leg,
                               double npv,
                               final DayCounter dayCounter,
                               Compounding compounding,
                               Frequency frequency) {
        return CashFlows.yield(solver, leg, npv, dayCounter, compounding, frequency,
                null, new Date(), new Date(), 1.0e-10, 0.05);
    }

    //! Cash-flow duration.
    public static double duration(final List<CashFlow> leg,
                                  final InterestRate rate,
                                  Duration.Type type) {
        return duration(leg, rate, type, null, new Date(), new Date());
    }

    public static double duration(final List<CashFlow> leg,
                                  final InterestRate rate,
                                  Duration.Type type,
                                  Boolean includeSettlementDateFlows,
                                  Date settlementDate,
                                  Date npvDate) {
        if (leg.isEmpty())
            return 0.0;

        settlementDate = settlementDate(settlementDate);
        npvDate = npvDate(npvDate, settlementDate);

        if (type == null)
            QL_FAIL("unknown duration type");

        switch (type) {
            case Simple:
                return simpleDuration(leg, rate,
                        includeSettlementDateFlows,
                        settlementDate, npvDate);
            case Modified:
                return modifiedDuration(leg, rate,
                        includeSettlementDateFlows,
                        settlementDate, npvDate);
            case Macaulay:
                return macaulayDuration(leg, rate,
                        includeSettlementDateFlows,
                        settlementDate, npvDate);
            default:
                QL_FAIL("unknown duration type");
                return 0.0;
        }
    }

    public static double duration(final List<CashFlow> leg,
                                  double yield,
                                  final DayCounter dc,
                                  Compounding comp,
                                  Frequency freq,
                                  Duration.Type type) {
        return duration(leg, new InterestRate(yield, dc, comp, freq), type);
    }

    public static double duration(final List<CashFlow> leg,
                                  double yield,
                                  final DayCounter dc,
                                  Compounding comp,
                                  Frequency freq,
                                  Duration.Type type,
                                  Boolean includeSettlementDateFlows,
                                  Date settlementDate,
                                  Date npvDate) {
        return duration(leg, new InterestRate(yield, dc, comp, freq),
                type,
                includeSettlementDateFlows,
                settlementDate, npvDate);
    }

    //! Cash-flow convexity
    public static double convexity(final List<CashFlow> leg,
                                   final InterestRate yield) {
        return convexity(leg, yield, null, new Date(), new Date());
    }

    public static double convexity(final List<CashFlow> leg,
                                   final InterestRate y,
                                   Boolean includeSettlementDateFlows,
                                   Date settlementDate,
                                   Date npvDate) {
        if (leg.isEmpty())
            return 0.0;

        settlementDate = settlementDate(settlementDate);
        npvDate = npvDate(npvDate, settlementDate);

        CashFlowResults results = cashFlowResults(leg, y, includeSettlementDateFlows,
                settlementDate, npvDate, true, true, false);
        if (results.npv == 0.0)
            // no cashflows
            return 0.0;

        return results.secondDerivative / results.npv;
    }

    public static double convexity(final List<CashFlow> leg,
                                   double yield,
                                   final DayCounter dc,
                                   Compounding comp,
                                   Frequency freq) {
        return convexity(leg, new InterestRate(yield, dc, comp, freq));
    }

    public static double convexity(final List<CashFlow> leg,
                                   double yield,
                                   final DayCounter dc,
                                   Compounding comp,
                                   Frequency freq,
                                   Boolean includeSettlementDateFlows,
                                   Date settlementDate,
                                   Date npvDate) {
        return convexity(leg, new InterestRate(yield, dc, comp, freq),
                includeSettlementDateFlows,
                settlementDate, npvDate);
    }

    //! Basis-point value
    public static double basisPointValue(final List<CashFlow> leg,
                                         final InterestRate yield) {
        return basisPointValue(leg, yield, null, new Date(), new Date());
    }

    public static double basisPointValue(final List<CashFlow> leg,
                                         final InterestRate y,
                                         Boolean includeSettlementDateFlows,
                                         Date settlementDate,
                                         Date npvDate) {
        if (leg.isEmpty())
            return 0.0;

        settlementDate = settlementDate(settlementDate);
        npvDate = npvDate(npvDate, settlementDate);

        double npv = CashFlows.npv(leg, y,
                includeSettlementDateFlows,
                settlementDate, npvDate);
        double modifiedDuration = CashFlows.duration(leg, y,
                Duration.Type.Modified,
                includeSettlementDateFlows,
                settlementDate, npvDate);
        double convexity = CashFlows.convexity(leg, y,
                includeSettlementDateFlows,
                settlementDate, npvDate);
        double delta = -modifiedDuration * npv;
        double gamma = convexity * npv;

        double shift = 0.0001;
        delta *= shift;
        gamma *= shift * shift;

        return delta + 0.5 * gamma;
    }

    public static double basisPointValue(final List<CashFlow> leg,
                                         double yield,
                                         final DayCounter dc,
                                         Compounding comp,
                                         Frequency freq) {
        return basisPointValue(leg, new InterestRate(yield, dc, comp, freq));
    }

    public static double basisPointValue(final List<CashFlow> leg,
                                         double yield,
                                         final DayCounter dc,
                                         Compounding comp,
                                         Frequency freq,
                                         Boolean includeSettlementDateFlows,
                                         Date settlementDate,
                                         Date npvDate) {
        return basisPointValue(leg, new InterestRate(yield, dc, comp, freq),
                includeSettlementDateFlows,
                settlementDate, npvDate);
    }

    //! Yield value of a basis point
    public static double yieldValueBasisPoint(final List<CashFlow> leg,
                                              final InterestRate yield) {
        return yieldValueBasisPoint(leg, yield, null, new Date(), new Date());
    }

    public static double yieldValueBasisPoint(final List<CashFlow> leg,
                                              final InterestRate y,
                                              Boolean includeSettlementDateFlows,
                                              Date settlementDate,
                                              Date npvDate) {
        if (leg.isEmpty())
            return 0.0;

        settlementDate = settlementDate(settlementDate);
        npvDate = npvDate(npvDate, settlementDate);

        double npv = CashFlows.npv(leg, y,
                includeSettlementDateFlows,
                settlementDate, npvDate);
        double modifiedDuration = CashFlows.duration(leg, y,
                Duration.Type.Modified,
                includeSettlementDateFlows,
                settlementDate, npvDate);

        double shift = 0.01;
        return (1.0 / (-npv * modifiedDuration)) * shift;
    }

    public static double yieldValueBasisPoint(final List<CashFlow> leg,
                                              double yield,
                                              final DayCounter dc,
                                              Compounding comp,
                                              Frequency freq) {
        return yieldValueBasisPoint(leg, new InterestRate(yield, dc, comp, freq));
    }

    public static double yieldValueBasisPoint(final List<CashFlow> leg,
                                              double yield,
                                              final DayCounter dc,
                                              Compounding comp,
                                              Frequency freq,
                                              Boolean includeSettlementDateFlows,
                                              Date settlementDate,
                                              Date npvDate) {
        return yieldValueBasisPoint(leg, new InterestRate(yield, dc, comp, freq),
                includeSettlementDateFlows,
                settlementDate, npvDate);
    }

    // Z-spread utility functions
    public static double npv(final List<CashFlow> leg,
                             final YieldTermStructure discountCurve,
                             double zSpread,
                             Compounding comp,
                             Frequency freq) {
        return npv(leg, discountCurve, zSpread, comp, freq, null, new Date(), new Date());
    }

    public static double npv(final List<CashFlow> leg,
                             final YieldTermStructure discountCurve,
                             double zSpread,
                             Compounding comp,
                             Frequency freq,
                             Boolean includeSettlementDateFlows,
                             Date settlementDate,
                             Date npvDate) {
        if (leg.isEmpty())
            return 0.0;

        settlementDate = settlementDate(settlementDate);
        npvDate = npvDate(npvDate, settlementDate);

        Handle<YieldTermStructure> discountCurveHandle = new Handle<>(discountCurve, true);
        Handle<Quote> zSpreadQuoteHandle = new Handle<>(new SimpleQuote(zSpread), true);

        ZeroSpreadedTermStructure spreadedCurve = new ZeroSpreadedTermStructure(
                discountCurveHandle, zSpreadQuoteHandle, comp, freq);

        return npv(leg, spreadedCurve,
                includeSettlementDateFlows,
                settlementDate, npvDate);
    }

    public static double npv(final List<CashFlow> leg,
                             final YieldTermStructure discountCurve,
                             double zSpread,
                             final DayCounter dayCounter,
                             Compounding comp,
                             Frequency freq,
                             Boolean includeSettlementDateFlows,
                             Date settlementDate,
                             Date npvDate) {
        return npv(leg, discountCurve, zSpread, comp, freq,
                includeSettlementDateFlows, settlementDate, npvDate);
    }

    //! implied Z-spread.
    public static double zSpread(final List<CashFlow> leg,
                                 double npv,
                                 final YieldTermStructure discount,
                                 Compounding compounding,
                                 Frequency frequency) {
        return zSpread(leg, npv, discount, compounding, frequency,
                null, new Date(), new Date(), 1.0e-10, 100, 0.0);
    }

    public static double zSpread(final List<CashFlow> leg,
                                 double npv,
                                 final YieldTermStructure discount,
                                 Compounding compounding,
                                 Frequency frequency,
                                 Boolean includeSettlementDateFlows,
                                 Date settlementDate,
                                 Date npvDate,
                                 double accuracy,
                                 int maxIterations,
                                 double guess) {
        final Date settlement = settlementDate(settlementDate);
        final Date npvD = npvDate(npvDate, settlement);

        SimpleQuote zSpreadQuote = new SimpleQuote();
        ZeroSpreadedTermStructure spreadedCurve = new ZeroSpreadedTermStructure(
                new Handle<>(discount, true),
                new Handle<>(zSpreadQuote, true),
                compounding,
                frequency);
        Function objFunction = new Function() {
            @Override
            public double value(double zSpread) {
                zSpreadQuote.setValue(zSpread);
                double NPV = CashFlows.npv(leg, spreadedCurve,
                        includeSettlementDateFlows,
                        settlement, npvD);
                return npv - NPV;
            }
        };

        Brent solver = new Brent();
        solver.setMaxEvaluations(maxIterations);
        double step = 0.01;
        return solver.solve(objFunction, accuracy, guess, step);
    }

    public static double zSpread(final List<CashFlow> leg,
                                 double npv,
                                 final YieldTermStructure discount,
                                 final DayCounter dayCounter,
                                 Compounding compounding,
                                 Frequency frequency,
                                 Boolean includeSettlementDateFlows,
                                 Date settlementDate,
                                 Date npvDate,
                                 double accuracy,
                                 int maxIterations,
                                 double guess) {
        return zSpread(leg, npv, discount, compounding, frequency,
                includeSettlementDateFlows, settlementDate, npvDate,
                accuracy, maxIterations, guess);
    }

    // ------------------------------------------------------------------
    // Overloads mirroring the default arguments of the official API.
    // ------------------------------------------------------------------

    public static boolean isExpired(final List<CashFlow> leg, Boolean includeSettlementDateFlows) {
        return isExpired(leg, includeSettlementDateFlows, new Date());
    }

    public static Optional<CashFlow> previousCashFlow(final List<CashFlow> leg,
                                                      Boolean includeSettlementDateFlows) {
        return previousCashFlow(leg, includeSettlementDateFlows, new Date());
    }

    public static Optional<CashFlow> nextCashFlow(final List<CashFlow> leg,
                                                  Boolean includeSettlementDateFlows) {
        return nextCashFlow(leg, includeSettlementDateFlows, new Date());
    }

    public static Date previousCashFlowDate(final List<CashFlow> leg,
                                            Boolean includeSettlementDateFlows) {
        return previousCashFlowDate(leg, includeSettlementDateFlows, new Date());
    }

    public static Date nextCashFlowDate(final List<CashFlow> leg,
                                        Boolean includeSettlementDateFlows) {
        return nextCashFlowDate(leg, includeSettlementDateFlows, new Date());
    }

    public static double previousCashFlowAmount(final List<CashFlow> leg,
                                                Boolean includeSettlementDateFlows) {
        return previousCashFlowAmount(leg, includeSettlementDateFlows, new Date());
    }

    public static double nextCashFlowAmount(final List<CashFlow> leg,
                                            Boolean includeSettlementDateFlows) {
        return nextCashFlowAmount(leg, includeSettlementDateFlows, new Date());
    }

    public static double previousCouponRate(final List<CashFlow> leg,
                                            Boolean includeSettlementDateFlows) {
        return previousCouponRate(leg, includeSettlementDateFlows, new Date());
    }

    public static double nextCouponRate(final List<CashFlow> leg,
                                        Boolean includeSettlementDateFlows) {
        return nextCouponRate(leg, includeSettlementDateFlows, new Date());
    }

    public static double nominal(final List<CashFlow> leg,
                                 Boolean includeSettlementDateFlows) {
        return nominal(leg, includeSettlementDateFlows, new Date());
    }

    public static Date accrualStartDate(final List<CashFlow> leg,
                                        Boolean includeSettlementDateFlows) {
        return accrualStartDate(leg, includeSettlementDateFlows, new Date());
    }

    public static Date accrualEndDate(final List<CashFlow> leg,
                                      Boolean includeSettlementDateFlows) {
        return accrualEndDate(leg, includeSettlementDateFlows, new Date());
    }

    public static Date referencePeriodStart(final List<CashFlow> leg,
                                            Boolean includeSettlementDateFlows) {
        return referencePeriodStart(leg, includeSettlementDateFlows, new Date());
    }

    public static Date referencePeriodEnd(final List<CashFlow> leg,
                                          Boolean includeSettlementDateFlows) {
        return referencePeriodEnd(leg, includeSettlementDateFlows, new Date());
    }

    public static double accrualPeriod(final List<CashFlow> leg,
                                       Boolean includeSettlementDateFlows) {
        return accrualPeriod(leg, includeSettlementDateFlows, new Date());
    }

    public static int accrualDays(final List<CashFlow> leg,
                                  Boolean includeSettlementDateFlows) {
        return accrualDays(leg, includeSettlementDateFlows, new Date());
    }

    public static double accruedPeriod(final List<CashFlow> leg,
                                       Boolean includeSettlementDateFlows) {
        return accruedPeriod(leg, includeSettlementDateFlows, new Date());
    }

    public static int accruedDays(final List<CashFlow> leg,
                                  Boolean includeSettlementDateFlows) {
        return accruedDays(leg, includeSettlementDateFlows, new Date());
    }

    public static double accruedAmount(final List<CashFlow> leg,
                                       Boolean includeSettlementDateFlows) {
        return accruedAmount(leg, includeSettlementDateFlows, new Date());
    }

    public static double npv(final List<CashFlow> leg,
                             final YieldTermStructure discountCurve,
                             Boolean includeSettlementDateFlows) {
        return npv(leg, discountCurve, includeSettlementDateFlows, new Date(), new Date());
    }

    public static double npv(final List<CashFlow> leg,
                             final YieldTermStructure discountCurve,
                             Boolean includeSettlementDateFlows,
                             Date settlementDate) {
        return npv(leg, discountCurve, includeSettlementDateFlows, settlementDate, new Date());
    }

    public static double bps(final List<CashFlow> leg,
                             final YieldTermStructure discountCurve,
                             Boolean includeSettlementDateFlows) {
        return bps(leg, discountCurve, includeSettlementDateFlows, new Date(), new Date());
    }

    public static double bps(final List<CashFlow> leg,
                             final YieldTermStructure discountCurve,
                             Boolean includeSettlementDateFlows,
                             Date settlementDate) {
        return bps(leg, discountCurve, includeSettlementDateFlows, settlementDate, new Date());
    }

    public static NpvBps npvbps(final List<CashFlow> leg,
                                final YieldTermStructure discountCurve,
                                Boolean includeSettlementDateFlows) {
        return npvbps(leg, discountCurve, includeSettlementDateFlows, new Date(), new Date());
    }

    public static NpvBps npvbps(final List<CashFlow> leg,
                                final YieldTermStructure discountCurve,
                                Boolean includeSettlementDateFlows,
                                Date settlementDate) {
        return npvbps(leg, discountCurve, includeSettlementDateFlows, settlementDate, new Date());
    }

    public static double atmRate(final List<CashFlow> leg,
                                 final YieldTermStructure discountCurve,
                                 Boolean includeSettlementDateFlows) {
        return atmRate(leg, discountCurve, includeSettlementDateFlows, new Date(), new Date(),
                Double.NaN);
    }

    public static double atmRate(final List<CashFlow> leg,
                                 final YieldTermStructure discountCurve,
                                 Boolean includeSettlementDateFlows,
                                 Date settlementDate) {
        return atmRate(leg, discountCurve, includeSettlementDateFlows, settlementDate, new Date(),
                Double.NaN);
    }

    public static double atmRate(final List<CashFlow> leg,
                                 final YieldTermStructure discountCurve,
                                 Boolean includeSettlementDateFlows,
                                 Date settlementDate,
                                 Date npvDate) {
        return atmRate(leg, discountCurve, includeSettlementDateFlows, settlementDate, npvDate,
                Double.NaN);
    }

    public static double npv(final List<CashFlow> leg,
                             final InterestRate yield,
                             Boolean includeSettlementDateFlows) {
        return npv(leg, yield, includeSettlementDateFlows, new Date(), new Date());
    }

    public static double npv(final List<CashFlow> leg,
                             final InterestRate yield,
                             Boolean includeSettlementDateFlows,
                             Date settlementDate) {
        return npv(leg, yield, includeSettlementDateFlows, settlementDate, new Date());
    }

    public static double npv(final List<CashFlow> leg,
                             double yield,
                             final DayCounter dc,
                             Compounding comp,
                             Frequency freq,
                             Boolean includeSettlementDateFlows) {
        return npv(leg, new InterestRate(yield, dc, comp, freq), includeSettlementDateFlows);
    }

    public static double npv(final List<CashFlow> leg,
                             double yield,
                             final DayCounter dc,
                             Compounding comp,
                             Frequency freq,
                             Boolean includeSettlementDateFlows,
                             Date settlementDate) {
        return npv(leg, new InterestRate(yield, dc, comp, freq), includeSettlementDateFlows,
                settlementDate);
    }

    public static double bps(final List<CashFlow> leg,
                             final InterestRate yield,
                             Boolean includeSettlementDateFlows) {
        return bps(leg, yield, includeSettlementDateFlows, new Date(), new Date());
    }

    public static double bps(final List<CashFlow> leg,
                             final InterestRate yield,
                             Boolean includeSettlementDateFlows,
                             Date settlementDate) {
        return bps(leg, yield, includeSettlementDateFlows, settlementDate, new Date());
    }

    public static double bps(final List<CashFlow> leg,
                             double yield,
                             final DayCounter dc,
                             Compounding comp,
                             Frequency freq,
                             Boolean includeSettlementDateFlows) {
        return bps(leg, new InterestRate(yield, dc, comp, freq), includeSettlementDateFlows);
    }

    public static double bps(final List<CashFlow> leg,
                             double yield,
                             final DayCounter dc,
                             Compounding comp,
                             Frequency freq,
                             Boolean includeSettlementDateFlows,
                             Date settlementDate) {
        return bps(leg, new InterestRate(yield, dc, comp, freq), includeSettlementDateFlows,
                settlementDate);
    }

    public static double yield(final List<CashFlow> leg,
                               double npv,
                               final DayCounter dayCounter,
                               Compounding compounding,
                               Frequency frequency,
                               Boolean includeSettlementDateFlows) {
        return CashFlows.yield(leg, npv, dayCounter, compounding, frequency,
                includeSettlementDateFlows, new Date(), new Date(), 1.0e-10, 100, 0.05);
    }

    public static double yield(final List<CashFlow> leg,
                               double npv,
                               final DayCounter dayCounter,
                               Compounding compounding,
                               Frequency frequency,
                               Boolean includeSettlementDateFlows,
                               Date settlementDate) {
        return CashFlows.yield(leg, npv, dayCounter, compounding, frequency,
                includeSettlementDateFlows, settlementDate, new Date(), 1.0e-10, 100, 0.05);
    }

    public static double yield(final List<CashFlow> leg,
                               double npv,
                               final DayCounter dayCounter,
                               Compounding compounding,
                               Frequency frequency,
                               Boolean includeSettlementDateFlows,
                               Date settlementDate,
                               Date npvDate) {
        return CashFlows.yield(leg, npv, dayCounter, compounding, frequency,
                includeSettlementDateFlows, settlementDate, npvDate, 1.0e-10, 100, 0.05);
    }

    public static double duration(final List<CashFlow> leg,
                                  final InterestRate rate,
                                  Duration.Type type,
                                  Boolean includeSettlementDateFlows) {
        return duration(leg, rate, type, includeSettlementDateFlows, new Date(), new Date());
    }

    public static double duration(final List<CashFlow> leg,
                                  final InterestRate rate,
                                  Duration.Type type,
                                  Boolean includeSettlementDateFlows,
                                  Date settlementDate) {
        return duration(leg, rate, type, includeSettlementDateFlows, settlementDate, new Date());
    }

    public static double duration(final List<CashFlow> leg,
                                  double yield,
                                  final DayCounter dc,
                                  Compounding comp,
                                  Frequency freq,
                                  Duration.Type type,
                                  Boolean includeSettlementDateFlows) {
        return duration(leg, new InterestRate(yield, dc, comp, freq), type,
                includeSettlementDateFlows);
    }

    public static double duration(final List<CashFlow> leg,
                                  double yield,
                                  final DayCounter dc,
                                  Compounding comp,
                                  Frequency freq,
                                  Duration.Type type,
                                  Boolean includeSettlementDateFlows,
                                  Date settlementDate) {
        return duration(leg, new InterestRate(yield, dc, comp, freq), type,
                includeSettlementDateFlows, settlementDate);
    }

    public static double convexity(final List<CashFlow> leg,
                                   final InterestRate yield,
                                   Boolean includeSettlementDateFlows) {
        return convexity(leg, yield, includeSettlementDateFlows, new Date(), new Date());
    }

    public static double convexity(final List<CashFlow> leg,
                                   final InterestRate yield,
                                   Boolean includeSettlementDateFlows,
                                   Date settlementDate) {
        return convexity(leg, yield, includeSettlementDateFlows, settlementDate, new Date());
    }

    public static double convexity(final List<CashFlow> leg,
                                   double yield,
                                   final DayCounter dc,
                                   Compounding comp,
                                   Frequency freq,
                                   Boolean includeSettlementDateFlows) {
        return convexity(leg, new InterestRate(yield, dc, comp, freq), includeSettlementDateFlows);
    }

    public static double convexity(final List<CashFlow> leg,
                                   double yield,
                                   final DayCounter dc,
                                   Compounding comp,
                                   Frequency freq,
                                   Boolean includeSettlementDateFlows,
                                   Date settlementDate) {
        return convexity(leg, new InterestRate(yield, dc, comp, freq), includeSettlementDateFlows,
                settlementDate);
    }

    public static double basisPointValue(final List<CashFlow> leg,
                                         final InterestRate yield,
                                         Boolean includeSettlementDateFlows) {
        return basisPointValue(leg, yield, includeSettlementDateFlows, new Date(), new Date());
    }

    public static double basisPointValue(final List<CashFlow> leg,
                                         final InterestRate yield,
                                         Boolean includeSettlementDateFlows,
                                         Date settlementDate) {
        return basisPointValue(leg, yield, includeSettlementDateFlows, settlementDate, new Date());
    }

    public static double basisPointValue(final List<CashFlow> leg,
                                         double yield,
                                         final DayCounter dc,
                                         Compounding comp,
                                         Frequency freq,
                                         Boolean includeSettlementDateFlows) {
        return basisPointValue(leg, new InterestRate(yield, dc, comp, freq),
                includeSettlementDateFlows);
    }

    public static double basisPointValue(final List<CashFlow> leg,
                                         double yield,
                                         final DayCounter dc,
                                         Compounding comp,
                                         Frequency freq,
                                         Boolean includeSettlementDateFlows,
                                         Date settlementDate) {
        return basisPointValue(leg, new InterestRate(yield, dc, comp, freq),
                includeSettlementDateFlows, settlementDate);
    }

    public static double yieldValueBasisPoint(final List<CashFlow> leg,
                                              final InterestRate yield,
                                              Boolean includeSettlementDateFlows) {
        return yieldValueBasisPoint(leg, yield, includeSettlementDateFlows, new Date(), new Date());
    }

    public static double yieldValueBasisPoint(final List<CashFlow> leg,
                                              final InterestRate yield,
                                              Boolean includeSettlementDateFlows,
                                              Date settlementDate) {
        return yieldValueBasisPoint(leg, yield, includeSettlementDateFlows, settlementDate,
                new Date());
    }

    public static double yieldValueBasisPoint(final List<CashFlow> leg,
                                              double yield,
                                              final DayCounter dc,
                                              Compounding comp,
                                              Frequency freq,
                                              Boolean includeSettlementDateFlows) {
        return yieldValueBasisPoint(leg, new InterestRate(yield, dc, comp, freq),
                includeSettlementDateFlows);
    }

    public static double yieldValueBasisPoint(final List<CashFlow> leg,
                                              double yield,
                                              final DayCounter dc,
                                              Compounding comp,
                                              Frequency freq,
                                              Boolean includeSettlementDateFlows,
                                              Date settlementDate) {
        return yieldValueBasisPoint(leg, new InterestRate(yield, dc, comp, freq),
                includeSettlementDateFlows, settlementDate);
    }

    public static double npv(final List<CashFlow> leg,
                             final YieldTermStructure discountCurve,
                             double zSpread,
                             Compounding comp,
                             Frequency freq,
                             Boolean includeSettlementDateFlows) {
        return npv(leg, discountCurve, zSpread, comp, freq,
                includeSettlementDateFlows, new Date(), new Date());
    }

    public static double npv(final List<CashFlow> leg,
                             final YieldTermStructure discountCurve,
                             double zSpread,
                             Compounding comp,
                             Frequency freq,
                             Boolean includeSettlementDateFlows,
                             Date settlementDate) {
        return npv(leg, discountCurve, zSpread, comp, freq,
                includeSettlementDateFlows, settlementDate, new Date());
    }

    public static double zSpread(final List<CashFlow> leg,
                                 double npv,
                                 final YieldTermStructure discount,
                                 Compounding compounding,
                                 Frequency frequency,
                                 Boolean includeSettlementDateFlows) {
        return zSpread(leg, npv, discount, compounding, frequency,
                includeSettlementDateFlows, new Date(), new Date(), 1.0e-10, 100, 0.0);
    }

    public static double zSpread(final List<CashFlow> leg,
                                 double npv,
                                 final YieldTermStructure discount,
                                 Compounding compounding,
                                 Frequency frequency,
                                 Boolean includeSettlementDateFlows,
                                 Date settlementDate) {
        return zSpread(leg, npv, discount, compounding, frequency,
                includeSettlementDateFlows, settlementDate, new Date(), 1.0e-10, 100, 0.0);
    }

    public static double zSpread(final List<CashFlow> leg,
                                 double npv,
                                 final YieldTermStructure discount,
                                 Compounding compounding,
                                 Frequency frequency,
                                 Boolean includeSettlementDateFlows,
                                 Date settlementDate,
                                 Date npvDate) {
        return zSpread(leg, npv, discount, compounding, frequency,
                includeSettlementDateFlows, settlementDate, npvDate, 1.0e-10, 100, 0.0);
    }
}
