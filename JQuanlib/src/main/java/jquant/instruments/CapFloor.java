package jquant.instruments;

import jquant.CashFlow;
import jquant.Handle;
import jquant.Instrument;
import jquant.PricingEngine;
import jquant.Quote;
import jquant.Settings;
import jquant.cashflows.CashFlows;
import jquant.cashflows.FloatingRateCoupon;
import jquant.indexes.InterestRateIndex;
import jquant.math.Function;
import jquant.math.solvers1d.NewtonSafe;
import jquant.pricingengines.GenericEngine;
import jquant.pricingengines.capfloor.BachelierCapFloorEngine;
import jquant.pricingengines.capfloor.BlackCapFloorEngine;
import jquant.quotes.SimpleQuote;
import jquant.termstructures.YieldTermStructure;
import jquant.termstructures.volatility.Sarb.VolatilityType;
import jquant.time.Date;
import jquant.time.TimeUtils;
import jquant.time.daycounters.Actual365Fixed;

import java.util.ArrayList;
import java.util.List;

import static jquant.math.CommonUtil.QL_FAIL;
import static jquant.math.CommonUtil.QL_REQUIRE;
import static jquant.termstructures.volatility.Sarb.VolatilityType.Normal;
import static jquant.termstructures.volatility.Sarb.VolatilityType.ShiftedLognormal;

/**
 * Base class for cap-like instruments.
 *
 * <p>Ported from QuantLib
 * {@code ql/instruments/capfloor.{hpp,cpp}}.
 */
public class CapFloor extends Instrument {
    public enum Type { Cap, Floor, Collar }

    public static class arguments implements PricingEngine.arguments {
        public Type type = null;
        public List<Date> startDates;
        public List<Date> fixingDates;
        public List<Date> endDates;
        public List<Double> accrualTimes;
        public List<Double> capRates;
        public List<Double> floorRates;
        public List<Double> forwards;
        public List<Double> gearings;
        public List<Double> spreads;
        public List<Double> nominals;
        public List<InterestRateIndex> indexes;

        @Override
        public void validate() {
            QL_REQUIRE(endDates.size() == startDates.size(),
                    "number of start dates (" + startDates.size()
                            + ") different from that of end dates ("
                            + endDates.size() + ")");
            QL_REQUIRE(accrualTimes.size() == startDates.size(),
                    "number of start dates (" + startDates.size()
                            + ") different from that of accrual times ("
                            + accrualTimes.size() + ")");
            QL_REQUIRE(type == Type.Floor || capRates.size() == startDates.size(),
                    "number of start dates (" + startDates.size()
                            + ") different from that of cap rates ("
                            + capRates.size() + ")");
            QL_REQUIRE(type == Type.Cap || floorRates.size() == startDates.size(),
                    "number of start dates (" + startDates.size()
                            + ") different from that of floor rates ("
                            + floorRates.size() + ")");
            QL_REQUIRE(gearings.size() == startDates.size(),
                    "number of start dates (" + startDates.size()
                            + ") different from that of gearings ("
                            + gearings.size() + ")");
            QL_REQUIRE(spreads.size() == startDates.size(),
                    "number of start dates (" + startDates.size()
                            + ") different from that of spreads ("
                            + spreads.size() + ")");
            QL_REQUIRE(nominals.size() == startDates.size(),
                    "number of start dates (" + startDates.size()
                            + ") different from that of nominals ("
                            + nominals.size() + ")");
            QL_REQUIRE(forwards.size() == startDates.size(),
                    "number of start dates (" + startDates.size()
                            + ") different from that of forwards ("
                            + forwards.size() + ")");
        }
    }

    public static class results extends Instrument.results {
    }

    /** Base class for cap/floor engines. */
    public abstract static class engine
            extends GenericEngine<CapFloor.arguments, CapFloor.results> {
        protected engine() {
            super(new CapFloor.arguments(), new CapFloor.results());
            results_.additionalResults = new java.util.HashMap<>();
        }
    }

    private final Type type_;
    private final List<CashFlow> floatingLeg_;
    private final List<Double> capRates_;
    private final List<Double> floorRates_;

    public CapFloor(Type type,
                    List<CashFlow> floatingLeg,
                    List<Double> capRates,
                    List<Double> floorRates) {
        type_ = type;
        floatingLeg_ = new ArrayList<>(floatingLeg);
        capRates_ = new ArrayList<>(capRates);
        floorRates_ = new ArrayList<>(floorRates);
        if (type_ == Type.Cap || type_ == Type.Collar) {
            QL_REQUIRE(!capRates_.isEmpty(), "no cap rates given");
            while (capRates_.size() < floatingLeg_.size())
                capRates_.add(capRates_.get(capRates_.size() - 1));
        }
        if (type_ == Type.Floor || type_ == Type.Collar) {
            QL_REQUIRE(!floorRates_.isEmpty(), "no floor rates given");
            while (floorRates_.size() < floatingLeg_.size())
                floorRates_.add(floorRates_.get(floorRates_.size() - 1));
        }
        for (CashFlow i : floatingLeg_)
            registerWith(i);
        registerWith(Settings.instance.evaluationDate());
    }

    public CapFloor(Type type, List<CashFlow> floatingLeg,
                    List<Double> strikes) {
        type_ = type;
        floatingLeg_ = new ArrayList<>(floatingLeg);
        capRates_ = new ArrayList<>();
        floorRates_ = new ArrayList<>();
        QL_REQUIRE(!strikes.isEmpty(), "no strikes given");
        if (type_ == Type.Cap) {
            capRates_.addAll(strikes);
            while (capRates_.size() < floatingLeg_.size())
                capRates_.add(capRates_.get(capRates_.size() - 1));
        } else if (type_ == Type.Floor) {
            floorRates_.addAll(strikes);
            while (floorRates_.size() < floatingLeg_.size())
                floorRates_.add(floorRates_.get(floorRates_.size() - 1));
        } else {
            QL_FAIL("only Cap/Floor types allowed in this constructor");
        }
        for (CashFlow i : floatingLeg_)
            registerWith(i);
        registerWith(Settings.instance.evaluationDate());
    }

    public Type type() {
        return type_;
    }

    public List<Double> capRates() {
        return capRates_;
    }

    public List<Double> floorRates() {
        return floorRates_;
    }

    public List<CashFlow> floatingLeg() {
        return floatingLeg_;
    }

    public Date startDate() {
        return CashFlows.startDate(floatingLeg_);
    }

    public Date maturityDate() {
        return CashFlows.maturityDate(floatingLeg_);
    }

    public FloatingRateCoupon lastFloatingRateCoupon() {
        CashFlow lastCF = floatingLeg_.get(floatingLeg_.size() - 1);
        return (lastCF instanceof FloatingRateCoupon c) ? c : null;
    }

    /** Returns the n-th optionlet as a new CapFloor with only one cash flow. */
    public CapFloor optionlet(int n) {
        QL_REQUIRE(n < floatingLeg().size(),
                ordinal(n + 1) + " optionlet does not exist, only "
                        + floatingLeg().size());
        List<CashFlow> cf = new ArrayList<>();
        cf.add(floatingLeg().get(n));

        List<Double> cap = new ArrayList<>();
        List<Double> floor = new ArrayList<>();
        if (type() == Type.Cap || type() == Type.Collar)
            cap.add(capRates().get(n));
        if (type() == Type.Floor || type() == Type.Collar)
            floor.add(floorRates().get(n));

        return new CapFloor(type(), cf, cap, floor);
    }

    @Override
    public boolean isExpired() {
        for (int i = floatingLeg_.size(); i > 0; --i)
            if (!floatingLeg_.get(i - 1).hasOccurred())
                return false;
        return true;
    }

    public void deepUpdate() {
        for (CashFlow i : floatingLeg_)
            i.update();
        update();
    }

    @Override
    public void setupArguments(PricingEngine.arguments args) {
        QL_REQUIRE(args instanceof CapFloor.arguments, "wrong argument type");
        CapFloor.arguments arguments = (CapFloor.arguments) args;

        int n = floatingLeg_.size();

        arguments.startDates = new ArrayList<>(n);
        arguments.fixingDates = new ArrayList<>(n);
        arguments.endDates = new ArrayList<>(n);
        arguments.accrualTimes = new ArrayList<>(n);
        arguments.forwards = new ArrayList<>(n);
        arguments.nominals = new ArrayList<>(n);
        arguments.gearings = new ArrayList<>(n);
        arguments.capRates = new ArrayList<>(n);
        arguments.floorRates = new ArrayList<>(n);
        arguments.spreads = new ArrayList<>(n);
        arguments.indexes = new ArrayList<>(n);

        arguments.type = type_;

        Date today = Settings.instance.evaluationDate().Date();

        for (int i = 0; i < n; ++i) {
            CashFlow cashFlow = floatingLeg_.get(i);
            QL_REQUIRE(cashFlow instanceof FloatingRateCoupon,
                    "non-FloatingRateCoupon given");
            FloatingRateCoupon coupon = (FloatingRateCoupon) cashFlow;
            arguments.startDates.add(coupon.accrualStartDate());
            arguments.fixingDates.add(coupon.fixingDate());
            arguments.endDates.add(coupon.date());

            // this is passed explicitly for precision
            arguments.accrualTimes.add(coupon.accrualPeriod());

            // this is passed explicitly for precision...
            if (TimeUtils.geq(arguments.endDates.get(i), today)) { // ...but only if needed
                arguments.forwards.add(coupon.adjustedFixing());
            } else {
                arguments.forwards.add(Double.NaN);
            }

            arguments.nominals.add(coupon.nominal());
            double spread = coupon.spread();
            double gearing = coupon.gearing();
            arguments.gearings.add(gearing);
            arguments.spreads.add(spread);

            if (type_ == Type.Cap || type_ == Type.Collar)
                arguments.capRates.add((capRates_.get(i) - spread) / gearing);
            else
                arguments.capRates.add(Double.NaN);

            if (type_ == Type.Floor || type_ == Type.Collar)
                arguments.floorRates.add((floorRates_.get(i) - spread) / gearing);
            else
                arguments.floorRates.add(Double.NaN);

            arguments.indexes.add(coupon.index());
        }
    }

    public double atmRate(YieldTermStructure discountCurve) {
        boolean includeSettlementDateFlows = false;
        Date settlementDate = discountCurve.referenceDate();
        return CashFlows.atmRate(floatingLeg_, discountCurve,
                includeSettlementDateFlows, settlementDate);
    }

    /** Implied term volatility. */
    public double impliedVolatility(double targetValue,
                                    Handle<YieldTermStructure> d,
                                    double guess,
                                    double accuracy,
                                    int maxEvaluations,
                                    double minVol,
                                    double maxVol,
                                    VolatilityType type,
                                    double displacement) {
        // calculate();
        QL_REQUIRE(!isExpired(), "instrument expired");

        ImpliedCapVolHelper f = new ImpliedCapVolHelper(
                this, d, targetValue, displacement, type);
        NewtonSafe solver = new NewtonSafe();
        solver.setMaxEvaluations(maxEvaluations);
        return solver.solve(f, accuracy, guess, minVol, maxVol);
    }

    private static String ordinal(int n) {
        int mod100 = n % 100;
        if (mod100 >= 11 && mod100 <= 13)
            return n + "th";
        switch (n % 10) {
            case 1:
                return n + "st";
            case 2:
                return n + "nd";
            case 3:
                return n + "rd";
            default:
                return n + "th";
        }
    }

    private static final class ImpliedCapVolHelper implements Function {
        private final Handle<YieldTermStructure> discountCurve_;
        private final double targetValue_;
        private final SimpleQuote vol_;
        private final PricingEngine engine_;
        private final CapFloor.results results_;

        ImpliedCapVolHelper(CapFloor cap,
                            Handle<YieldTermStructure> discountCurve,
                            double targetValue,
                            double displacement,
                            VolatilityType type) {
            discountCurve_ = discountCurve;
            targetValue_ = targetValue;
            vol_ = new SimpleQuote(-1.0);

            // vol_ is set an implausible value, so that calculation is forced
            // at first ImpliedCapVolHelper.value(x) call
            Handle<Quote> h = new Handle<>(vol_, true);

            switch (type) {
                case ShiftedLognormal:
                    engine_ = new BlackCapFloorEngine(discountCurve_, h,
                            new Actual365Fixed(), displacement);
                    break;
                case Normal:
                    engine_ = new BachelierCapFloorEngine(discountCurve_, h,
                            new Actual365Fixed());
                    break;
                default:
                    QL_FAIL("unknown VolatilityType (" + type + ")");
                    throw new IllegalArgumentException("unknown VolatilityType");
            }

            cap.setupArguments(engine_.getArguments());

            results_ = (CapFloor.results) engine_.getResults();
        }

        @Override
        public double value(double x) {
            if (x != vol_.value()) {
                vol_.setValue(x);
                engine_.calculate();
            }
            return results_.value - targetValue_;
        }

        @Override
        public double derivative(double x) {
            if (x != vol_.value()) {
                vol_.setValue(x);
                engine_.calculate();
            }
            Object vega = results_.additionalResults.get("vega");
            QL_REQUIRE(vega != null, "vega not provided");
            return (Double) vega;
        }
    }

    /** Concrete cap class. */
    public static class Cap extends CapFloor {
        public Cap(List<CashFlow> floatingLeg, List<Double> exerciseRates) {
            super(CapFloor.Type.Cap, floatingLeg, exerciseRates);
        }
    }

    /** Concrete floor class. */
    public static class Floor extends CapFloor {
        public Floor(List<CashFlow> floatingLeg, List<Double> exerciseRates) {
            super(CapFloor.Type.Floor, floatingLeg, exerciseRates);
        }
    }

    /** Concrete collar class. */
    public static class Collar extends CapFloor {
        public Collar(List<CashFlow> floatingLeg,
                      List<Double> capRates,
                      List<Double> floorRates) {
            super(CapFloor.Type.Collar, floatingLeg, capRates, floorRates);
        }
    }
}
