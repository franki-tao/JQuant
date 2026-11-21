package pricingengines;

import jquant.Compounding;
import jquant.Handle;
import jquant.PricingEngine;
import jquant.Quote;
import jquant.RelinkableHandle;
import jquant.SavedSettings;
import jquant.Settings;
import jquant.cashflows.BlackIborCouponPricer;
import jquant.cashflows.FixedRateLeg;
import jquant.cashflows.IborLeg;
import jquant.indexes.Euribor1Y;
import jquant.indexes.IborIndex;
import jquant.indexes.IndexManager;
import jquant.instruments.CapFloor;
import jquant.instruments.Swap;
import jquant.pricingengines.DiscountingMultiLegSwapEngine;
import jquant.pricingengines.capfloor.BlackCapFloorEngine;
import jquant.quotes.SimpleQuote;
import jquant.termstructures.YieldTermStructure;
import jquant.termstructures.volatility.optionlet.ConstantOptionletVolatility;
import jquant.termstructures.yield.FlatForward;
import jquant.time.BusinessDayConvention;
import jquant.time.Calendar;
import jquant.time.Date;
import jquant.time.DateGenerationRule;
import jquant.time.DayCounter;
import jquant.time.Frequency;
import jquant.time.Period;
import jquant.time.Schedule;
import jquant.time.TimeUnit;
import jquant.time.daycounters.Actual365Fixed;
import jquant.time.daycounters.ActualActual;
import jquant.time.daycounters.Thirty360;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static jquant.cashflows.CashflowUtil.setCouponPricer;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Cap/floor coupon pricing from QuantLib
 * {@code test-suite/capflooredcoupon.cpp}.
 *
 * <p>Both official cases are ported: {@code testLargeRates} and
 * {@code testDecomposition} (all nine decomposition branches).
 *
 * <p>Official dates are kept verbatim: {@code today} is
 * {@code calendar.adjust(Date::todaysDate())} and the evaluation date is set
 * to that business day. No historic fixings are injected and no fixed
 * calendar date is pinned.
 */
public class CapFlooredCouponTest {

    private SavedSettings savedSettings_;

    @BeforeEach
    public void setUp() {
        savedSettings_ = new SavedSettings();
    }

    @AfterEach
    public void tearDown() {
        try {
            savedSettings_.restore();
        } finally {
            IndexManager.Instance.clearHistories();
        }
    }

    private static FlatForward flatRate(Date settlement, double rate, DayCounter dc) {
        return new FlatForward(settlement, rate, dc,
                Compounding.Continuous, Frequency.ANNUAL);
    }

    private static class CommonVars {
        Date today;
        Date settlement;
        Date startDate;
        Calendar calendar;
        double nominal;
        List<Double> nominals;
        BusinessDayConvention convention;
        Frequency frequency;
        IborIndex index;
        int settlementDays;
        int fixingDays;
        final RelinkableHandle<YieldTermStructure> termStructure =
                new RelinkableHandle<>();
        int length;
        double volatility;

        CommonVars() {
            length = 20; // years
            volatility = 0.20;
            nominal = 100.0;
            nominals = new ArrayList<>();
            for (int i = 0; i < length; ++i)
                nominals.add(nominal);
            frequency = Frequency.ANNUAL;
            index = new Euribor1Y(termStructure);
            calendar = index.fixingCalendar();
            convention = BusinessDayConvention.MODIFIED_FOLLOWING;
            today = calendar.adjust(Date.todaysDate(), BusinessDayConvention.FOLLOWING);
            Settings.instance.evaluationDate().equal(today);
            settlementDays = 2;
            fixingDays = 2;
            settlement = calendar.advance(today, settlementDays, TimeUnit.DAYS);
            startDate = settlement;
            termStructure.linkTo(flatRate(settlement, 0.05,
                    new ActualActual(ActualActual.Convention.ISDA)), true);
        }

        private Schedule makeSchedule(Date startDate, int length) {
            Date endDate = calendar.advance(startDate, length, TimeUnit.YEARS, convention);
            return new Schedule(startDate, endDate,
                    new Period(frequency), calendar, convention, convention,
                    DateGenerationRule.FORWARD, false, new Date(), new Date());
        }

        List<jquant.CashFlow> makeFixedLeg(Date startDate, int length) {
            Schedule schedule = makeSchedule(startDate, length);
            List<Double> coupons = new ArrayList<>();
            for (int i = 0; i < length; ++i)
                coupons.add(0.0);
            return new FixedRateLeg(schedule)
                    .withNotionals(nominals)
                    .withCouponRates(coupons,
                            new Thirty360(Thirty360.Convention.BondBasis))
                    .leg();
        }

        List<jquant.CashFlow> makeFloatingLeg(Date startDate,
                                              int length,
                                              double gearing,
                                              double spread) {
            Schedule schedule = makeSchedule(startDate, length);
            List<Double> gearingVector = new ArrayList<>();
            List<Double> spreadVector = new ArrayList<>();
            for (int i = 0; i < length; ++i) {
                gearingVector.add(gearing);
                spreadVector.add(spread);
            }
            return new IborLeg(schedule, index)
                    .withNotionals(nominals)
                    .withPaymentDayCounter(index.dayCounter())
                    .withPaymentAdjustment(convention)
                    .withFixingDays(fixingDays)
                    .withGearings(gearingVector)
                    .withSpreads(spreadVector)
                    .leg();
        }

        List<jquant.CashFlow> makeCapFlooredLeg(Date startDate,
                                                int length,
                                                List<Double> caps,
                                                List<Double> floors,
                                                double volatility,
                                                double gearing,
                                                double spread) {
            Schedule schedule = makeSchedule(startDate, length);

            Handle<jquant.termstructures.volatility.optionlet.OptionletVolatilityStructure> vol =
                    new Handle<>(new ConstantOptionletVolatility(
                            0, calendar, BusinessDayConvention.FOLLOWING,
                            volatility, new Actual365Fixed()), true);

            BlackIborCouponPricer pricer = new BlackIborCouponPricer(vol);
            List<Double> gearingVector = new ArrayList<>();
            List<Double> spreadVector = new ArrayList<>();
            for (int i = 0; i < length; ++i) {
                gearingVector.add(gearing);
                spreadVector.add(spread);
            }

            List<jquant.CashFlow> iborLeg = new IborLeg(schedule, index)
                    .withNotionals(nominals)
                    .withPaymentDayCounter(index.dayCounter())
                    .withPaymentAdjustment(convention)
                    .withFixingDays(fixingDays)
                    .withGearings(gearingVector)
                    .withSpreads(spreadVector)
                    .withCaps(caps)
                    .withFloors(floors)
                    .leg();
            setCouponPricer(iborLeg, pricer);
            return iborLeg;
        }

        PricingEngine makeEngine(double volatility) {
            Handle<Quote> vol = new Handle<>(new SimpleQuote(volatility), true);
            return new BlackCapFloorEngine(termStructure, vol);
        }

        CapFloor makeCapFloor(CapFloor.Type type,
                              List<jquant.CashFlow> leg,
                              double capStrike,
                              double floorStrike,
                              double volatility) {
            CapFloor result;
            switch (type) {
                case Cap:
                    result = new CapFloor.Cap(leg, List.of(capStrike));
                    break;
                case Floor:
                    result = new CapFloor.Floor(leg, List.of(floorStrike));
                    break;
                case Collar:
                    result = new CapFloor.Collar(leg, List.of(capStrike),
                            List.of(floorStrike));
                    break;
                default:
                    throw new IllegalArgumentException("unknown cap/floor type");
            }
            result.setPricingEngine(makeEngine(volatility));
            return result;
        }
    }

    @Test
    public void testLargeRates() {
        CommonVars vars = new CommonVars();

        /* A vanilla floating leg and a capped floating leg with strike
           equal to 100 and floor equal to 0 must have (about) the same NPV
           (depending on variance: option expiry and volatility) */
        List<Double> caps = new ArrayList<>();
        List<Double> floors = new ArrayList<>();
        for (int i = 0; i < vars.length; ++i) {
            caps.add(100.0);
            floors.add(0.0);
        }
        double tolerance = 1e-10;

        // fixed leg with zero rate
        List<jquant.CashFlow> fixedLeg =
                vars.makeFixedLeg(vars.startDate, vars.length);
        List<jquant.CashFlow> floatLeg =
                vars.makeFloatingLeg(vars.startDate, vars.length, 1.0, 0.0);
        List<jquant.CashFlow> collaredLeg =
                vars.makeCapFlooredLeg(vars.startDate, vars.length,
                        caps, floors, vars.volatility, 1.0, 0.0);

        PricingEngine engine = new DiscountingMultiLegSwapEngine(vars.termStructure);
        Swap vanillaLeg = new Swap(fixedLeg, floatLeg);
        Swap collarLeg = new Swap(fixedLeg, collaredLeg);
        vanillaLeg.setPricingEngine(engine);
        collarLeg.setPricingEngine(engine);

        if (Math.abs(vanillaLeg.NPV() - collarLeg.NPV()) > tolerance) {
            fail("Length: " + vars.length + " y" + "\n"
                    + "Volatility: " + vars.volatility * 100 + "%\n"
                    + "Notional: " + vars.nominal + "\n"
                    + "Vanilla floating leg NPV: " + vanillaLeg.NPV()
                    + "\n"
                    + "Collared floating leg NPV (strikes 0 and 100): "
                    + collarLeg.NPV()
                    + "\n"
                    + "Diff: " + Math.abs(vanillaLeg.NPV() - collarLeg.NPV()));
        }
    }

    @Test
    public void testDecomposition() {
        CommonVars vars = new CommonVars();

        double tolerance = 1e-12;
        double npvVanilla, npvCappedLeg, npvFlooredLeg, npvCollaredLeg,
                npvCap, npvFloor, npvCollar;
        double error;
        double floorstrike = 0.05;
        double capstrike = 0.10;

        List<Double> caps = new ArrayList<>();
        List<Double> caps0 = new ArrayList<>();
        List<Double> floors = new ArrayList<>();
        List<Double> floors0 = new ArrayList<>();
        for (int i = 0; i < vars.length; ++i) {
            caps.add(capstrike);
            floors.add(floorstrike);
        }
        double gearing_p = 0.5;
        double spread_p = 0.002;
        double gearing_n = -1.5;
        double spread_n = 0.12;

        // fixed leg with zero rate
        List<jquant.CashFlow> fixedLeg =
                vars.makeFixedLeg(vars.startDate, vars.length);
        // floating leg with gearing=1 and spread=0
        List<jquant.CashFlow> floatLeg =
                vars.makeFloatingLeg(vars.startDate, vars.length, 1.0, 0.0);
        // floating leg with positive gearing (gearing_p) and spread<>0
        List<jquant.CashFlow> floatLeg_p =
                vars.makeFloatingLeg(vars.startDate, vars.length, gearing_p, spread_p);
        // floating leg with negative gearing (gearing_n) and spread<>0
        List<jquant.CashFlow> floatLeg_n =
                vars.makeFloatingLeg(vars.startDate, vars.length, gearing_n, spread_n);

        // Swap with null fixed leg and floating leg with gearing=1 and spread=0
        Swap vanillaLeg = new Swap(fixedLeg, floatLeg);
        // Swap with null fixed leg and floating leg with positive gearing and spread<>0
        Swap vanillaLeg_p = new Swap(fixedLeg, floatLeg_p);
        // Swap with null fixed leg and floating leg with negative gearing and spread<>0
        Swap vanillaLeg_n = new Swap(fixedLeg, floatLeg_n);

        PricingEngine engine = new DiscountingMultiLegSwapEngine(vars.termStructure);
        vanillaLeg.setPricingEngine(engine);
        vanillaLeg_p.setPricingEngine(engine);
        vanillaLeg_n.setPricingEngine(engine);

        /* CAPPED coupon - Decomposition of payoff
           Payoff = Nom * Min(rate,strike) * accrualperiod =
                  = Nom * [rate + Min(0,strike-rate)] * accrualperiod =
                  = Nom * rate * accrualperiod - Nom * Max(rate-strike,0) * accrualperiod =
                  = VanillaFloatingLeg - Call
        */

        // Case gearing = 1 and spread = 0
        List<jquant.CashFlow> cappedLeg =
                vars.makeCapFlooredLeg(vars.startDate, vars.length,
                        caps, floors0, vars.volatility, 1.0, 0.0);
        Swap capLeg = new Swap(fixedLeg, cappedLeg);
        capLeg.setPricingEngine(engine);
        CapFloor cap = new CapFloor.Cap(floatLeg, List.of(capstrike));
        cap.setPricingEngine(vars.makeEngine(vars.volatility));
        npvVanilla = vanillaLeg.NPV();
        npvCappedLeg = capLeg.NPV();
        npvCap = cap.NPV();
        error = Math.abs(npvCappedLeg - (npvVanilla - npvCap));
        if (error > tolerance) {
            fail("\nCapped Leg: gearing=1, spread=0%, strike=" + capstrike * 100
                    + "%\n"
                    + "  Capped Floating Leg NPV: " + npvCappedLeg + "\n"
                    + "  Floating Leg NPV - Cap NPV: " + (npvVanilla - npvCap) + "\n"
                    + "  Diff: " + error);
        }

        /* gearing = 1 and spread = 0
           FLOORED coupon - Decomposition of payoff
           Payoff = Nom * Max(rate,strike) * accrualperiod =
                  = Nom * [rate + Max(0,strike-rate)] * accrualperiod =
                  = Nom * rate * accrualperiod + Nom * Max(strike-rate,0) * accrualperiod =
                  = VanillaFloatingLeg + Put
        */

        List<jquant.CashFlow> flooredLeg =
                vars.makeCapFlooredLeg(vars.startDate, vars.length,
                        caps0, floors, vars.volatility, 1.0, 0.0);
        Swap floorLeg = new Swap(fixedLeg, flooredLeg);
        floorLeg.setPricingEngine(engine);
        CapFloor floor = new CapFloor.Floor(floatLeg, List.of(floorstrike));
        floor.setPricingEngine(vars.makeEngine(vars.volatility));
        npvFlooredLeg = floorLeg.NPV();
        npvFloor = floor.NPV();
        error = Math.abs(npvFlooredLeg - (npvVanilla + npvFloor));
        if (error > tolerance) {
            fail("Floored Leg: gearing=1, spread=0%, strike=" + floorstrike * 100
                    + "%\n"
                    + "  Floored Floating Leg NPV: " + npvFlooredLeg + "\n"
                    + "  Floating Leg NPV + Floor NPV: " + (npvVanilla + npvFloor) + "\n"
                    + "  Diff: " + error);
        }

        /* gearing = 1 and spread = 0
           COLLARED coupon - Decomposition of payoff
           Payoff = Nom * Min(strikem,Max(rate,strikeM)) * accrualperiod =
                  = VanillaFloatingLeg - Collar
        */

        List<jquant.CashFlow> collaredLeg =
                vars.makeCapFlooredLeg(vars.startDate, vars.length,
                        caps, floors, vars.volatility, 1.0, 0.0);
        Swap collarLeg = new Swap(fixedLeg, collaredLeg);
        collarLeg.setPricingEngine(engine);
        CapFloor collar = new CapFloor.Collar(floatLeg,
                List.of(capstrike), List.of(floorstrike));
        collar.setPricingEngine(vars.makeEngine(vars.volatility));
        npvCollaredLeg = collarLeg.NPV();
        npvCollar = collar.NPV();
        error = Math.abs(npvCollaredLeg - (npvVanilla - npvCollar));
        if (error > tolerance) {
            fail("\nCollared Leg: gearing=1, spread=0%, strike="
                    + floorstrike * 100 + "% and " + capstrike * 100 + "%\n"
                    + "  Collared Floating Leg NPV: " + npvCollaredLeg + "\n"
                    + "  Floating Leg NPV - Collar NPV: " + (npvVanilla - npvCollar) + "\n"
                    + "  Diff: " + error);
        }

        /* gearing = a and spread = b
           CAPPED coupon - Decomposition of payoff
           Payoff
           = Nom * Min(a*rate+b,strike) * accrualperiod =
           = Nom * [a*rate+b + Min(0,strike-a*rate-b)] * accrualperiod =
           = Nom * a*rate+b * accrualperiod + Nom * Min(strike-b-a*rate,0) * accrualperiod
           --> If a>0 (assuming positive effective strike):
               Payoff = VanillaFloatingLeg - Call(a*rate+b,strike)
           --> If a<0 (assuming positive effective strike):
               Payoff = VanillaFloatingLeg + Nom * Min(strike-b+|a|*rate+,0) * accrualperiod =
                      = VanillaFloatingLeg + Put(|a|*rate+b,strike)
        */

        // Positive gearing
        List<jquant.CashFlow> cappedLeg_p =
                vars.makeCapFlooredLeg(vars.startDate, vars.length, caps, floors0,
                        vars.volatility, gearing_p, spread_p);
        Swap capLeg_p = new Swap(fixedLeg, cappedLeg_p);
        capLeg_p.setPricingEngine(engine);
        CapFloor cap_p = new CapFloor.Cap(floatLeg_p, List.of(capstrike));
        cap_p.setPricingEngine(vars.makeEngine(vars.volatility));
        npvVanilla = vanillaLeg_p.NPV();
        npvCappedLeg = capLeg_p.NPV();
        npvCap = cap_p.NPV();
        error = Math.abs(npvCappedLeg - (npvVanilla - npvCap));
        if (error > tolerance) {
            fail("\nCapped Leg: gearing=" + gearing_p + ", "
                    + "spread= " + spread_p * 100
                    + "%, strike=" + capstrike * 100 + "%, "
                    + "effective strike= " + (capstrike - spread_p) / gearing_p * 100
                    + "%\n"
                    + "  Capped Floating Leg NPV: " + npvCappedLeg + "\n"
                    + "  Vanilla Leg NPV: " + npvVanilla + "\n"
                    + "  Cap NPV: " + npvCap + "\n"
                    + "  Floating Leg NPV - Cap NPV: " + (npvVanilla - npvCap) + "\n"
                    + "  Diff: " + error);
        }

        // Negative gearing
        List<jquant.CashFlow> cappedLeg_n =
                vars.makeCapFlooredLeg(vars.startDate, vars.length, caps, floors0,
                        vars.volatility, gearing_n, spread_n);
        Swap capLeg_n = new Swap(fixedLeg, cappedLeg_n);
        capLeg_n.setPricingEngine(engine);
        CapFloor floor_n = new CapFloor.Floor(floatLeg,
                List.of((capstrike - spread_n) / gearing_n));
        floor_n.setPricingEngine(vars.makeEngine(vars.volatility));
        npvVanilla = vanillaLeg_n.NPV();
        npvCappedLeg = capLeg_n.NPV();
        npvFloor = floor_n.NPV();
        error = Math.abs(npvCappedLeg - (npvVanilla + gearing_n * npvFloor));
        if (error > tolerance) {
            fail("\nCapped Leg: gearing=" + gearing_n + ", "
                    + "spread= " + spread_n * 100
                    + "%, strike=" + capstrike * 100 + "%, "
                    + "effective strike= " + (capstrike - spread_n) / gearing_n * 100
                    + "%\n"
                    + "  Capped Floating Leg NPV: " + npvCappedLeg + "\n"
                    + "  npv Vanilla: " + npvVanilla + "\n"
                    + "  npvFloor: " + npvFloor + "\n"
                    + "  Floating Leg NPV - Cap NPV: "
                    + (npvVanilla + gearing_n * npvFloor) + "\n"
                    + "  Diff: " + error);
        }

        /* gearing = a and spread = b
           FLOORED coupon - Decomposition of payoff
           Payoff
           = Nom * Max(a*rate+b,strike) * accrualperiod =
           = Nom * [a*rate+b + Max(0,strike-a*rate-b)] * accrualperiod =
           = Nom * a*rate+b * accrualperiod + Nom * Max(strike-b-a*rate,0) * accrualperiod
           --> If a>0 (assuming positive effective strike):
               Payoff = VanillaFloatingLeg + Put(a*rate+b,strike)
           --> If a<0 (assuming positive effective strike):
               Payoff = VanillaFloatingLeg + Nom * Max(strike-b+|a|*rate+,0) * accrualperiod =
                      = VanillaFloatingLeg - Call(|a|*rate+b,strike)
        */

        // Positive gearing
        List<jquant.CashFlow> flooredLeg_p1 =
                vars.makeCapFlooredLeg(vars.startDate, vars.length, caps0, floors,
                        vars.volatility, gearing_p, spread_p);
        Swap floorLeg_p1 = new Swap(fixedLeg, flooredLeg_p1);
        floorLeg_p1.setPricingEngine(engine);
        CapFloor floor_p1 = new CapFloor.Floor(floatLeg_p, List.of(floorstrike));
        floor_p1.setPricingEngine(vars.makeEngine(vars.volatility));
        npvVanilla = vanillaLeg_p.NPV();
        npvFlooredLeg = floorLeg_p1.NPV();
        npvFloor = floor_p1.NPV();
        error = Math.abs(npvFlooredLeg - (npvVanilla + npvFloor));
        if (error > tolerance) {
            fail("\nFloored Leg: gearing=" + gearing_p + ", "
                    + "spread= " + spread_p * 100 + "%, strike=" + floorstrike * 100 + "%, "
                    + "effective strike= " + (floorstrike - spread_p) / gearing_p * 100
                    + "%\n"
                    + "  Floored Floating Leg NPV: " + npvFlooredLeg
                    + "\n"
                    + "  Floating Leg NPV + Floor NPV: " + (npvVanilla + npvFloor)
                    + "\n"
                    + "  Diff: " + error);
        }

        // Negative gearing
        List<jquant.CashFlow> flooredLeg_n =
                vars.makeCapFlooredLeg(vars.startDate, vars.length, caps0, floors,
                        vars.volatility, gearing_n, spread_n);
        Swap floorLeg_n = new Swap(fixedLeg, flooredLeg_n);
        floorLeg_n.setPricingEngine(engine);
        CapFloor cap_n = new CapFloor.Cap(floatLeg,
                List.of((floorstrike - spread_n) / gearing_n));
        cap_n.setPricingEngine(vars.makeEngine(vars.volatility));
        npvVanilla = vanillaLeg_n.NPV();
        npvFlooredLeg = floorLeg_n.NPV();
        npvCap = cap_n.NPV();
        error = Math.abs(npvFlooredLeg - (npvVanilla - gearing_n * npvCap));
        if (error > tolerance) {
            fail("\nCapped Leg: gearing=" + gearing_n + ", "
                    + "spread= " + spread_n * 100
                    + "%, strike=" + floorstrike * 100 + "%, "
                    + "effective strike= " + (floorstrike - spread_n) / gearing_n * 100
                    + "%\n"
                    + "  Capped Floating Leg NPV: " + npvFlooredLeg + "\n"
                    + "  Floating Leg NPV - Cap NPV: "
                    + (npvVanilla - gearing_n * npvCap) + "\n"
                    + "  Diff: " + error);
        }

        /* gearing = a and spread = b
           COLLARED coupon - Decomposition of payoff
           Payoff = Nom * Min(caprate,Max(a*rate+b,floorrate)) * accrualperiod
           --> If a>0 (assuming positive effective strike):
               Payoff = VanillaFloatingLeg - Collar(a*rate+b, floorrate, caprate)
           --> If a<0 (assuming positive effective strike):
               Payoff = VanillaFloatingLeg + Collar(|a|*rate+b, caprate, floorrate)
        */

        // Positive gearing
        List<jquant.CashFlow> collaredLeg_p =
                vars.makeCapFlooredLeg(vars.startDate, vars.length, caps, floors,
                        vars.volatility, gearing_p, spread_p);
        Swap collarLeg_p1 = new Swap(fixedLeg, collaredLeg_p);
        collarLeg_p1.setPricingEngine(engine);
        CapFloor collar_p = new CapFloor.Collar(floatLeg_p,
                List.of(capstrike), List.of(floorstrike));
        collar_p.setPricingEngine(vars.makeEngine(vars.volatility));
        npvVanilla = vanillaLeg_p.NPV();
        npvCollaredLeg = collarLeg_p1.NPV();
        npvCollar = collar_p.NPV();
        error = Math.abs(npvCollaredLeg - (npvVanilla - npvCollar));
        if (error > tolerance) {
            fail("\nCollared Leg: gearing=" + gearing_p + ", "
                    + "spread= " + spread_p * 100 + "%, strike="
                    + floorstrike * 100 + "% and " + capstrike * 100
                    + "%, "
                    + "effective strike=" + (floorstrike - spread_p) / gearing_p * 100
                    + "% and " + (capstrike - spread_p) / gearing_p * 100
                    + "%\n"
                    + "  Collared Floating Leg NPV: " + npvCollaredLeg
                    + "\n"
                    + "  Floating Leg NPV - Collar NPV: " + (npvVanilla - npvCollar)
                    + "\n"
                    + "  Diff: " + error);
        }

        // Negative gearing
        List<jquant.CashFlow> collaredLeg_n =
                vars.makeCapFlooredLeg(vars.startDate, vars.length, caps, floors,
                        vars.volatility, gearing_n, spread_n);
        Swap collarLeg_n1 = new Swap(fixedLeg, collaredLeg_n);
        collarLeg_n1.setPricingEngine(engine);
        CapFloor collar_n = new CapFloor.Collar(floatLeg,
                List.of((floorstrike - spread_n) / gearing_n),
                List.of((capstrike - spread_n) / gearing_n));
        collar_n.setPricingEngine(vars.makeEngine(vars.volatility));
        npvVanilla = vanillaLeg_n.NPV();
        npvCollaredLeg = collarLeg_n1.NPV();
        npvCollar = collar_n.NPV();
        error = Math.abs(npvCollaredLeg - (npvVanilla - gearing_n * npvCollar));
        if (error > tolerance) {
            fail("\nCollared Leg: gearing=" + gearing_n + ", "
                    + "spread= " + spread_n * 100 + "%, strike="
                    + floorstrike * 100 + "% and " + capstrike * 100
                    + "%, "
                    + "effective strike=" + (floorstrike - spread_n) / gearing_n * 100
                    + "% and " + (capstrike - spread_n) / gearing_n * 100
                    + "%\n"
                    + "  Collared Floating Leg NPV: " + npvCollaredLeg
                    + "\n"
                    + "  Floating Leg NPV - Collar NPV: "
                    + (npvVanilla - gearing_n * npvCollar)
                    + "\n"
                    + "  Diff: " + error);
        }
    }
}
