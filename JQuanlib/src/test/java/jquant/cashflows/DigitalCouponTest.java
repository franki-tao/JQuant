package jquant.cashflows;

import jquant.EuropeanExercise;
import jquant.EuropeanOption;
import jquant.Exercise;
import jquant.Handle;
import jquant.Option;
import jquant.Position;
import jquant.RelinkableHandle;
import jquant.SavedSettings;
import jquant.Settings;
import jquant.indexes.Euribor6M;
import jquant.indexes.IborIndex;
import jquant.indexes.IndexManager;
import jquant.math.distributions.CumulativeNormalDistribution;
import jquant.pricingengines.AssetOrNothingPayoff;
import jquant.pricingengines.CashOrNothingPayoff;
import jquant.pricingengines.StrikedTypePayoff;
import jquant.pricingengines.vanilla.AnalyticEuropeanEngine;
import jquant.processes.BlackScholesMertonProcess;
import jquant.quotes.SimpleQuote;
import jquant.termstructures.YieldTermStructure;
import jquant.termstructures.volatility.equityfx.BlackConstantVol;
import jquant.termstructures.volatility.equityfx.BlackVolTermStructure;
import jquant.termstructures.volatility.optionlet.ConstantOptionletVolatility;
import jquant.termstructures.volatility.optionlet.OptionletVolatilityStructure;
import jquant.termstructures.yield.FlatForward;
import jquant.time.BusinessDayConvention;
import jquant.time.Calendar;
import jquant.time.Date;
import jquant.time.TimeUnit;
import jquant.time.calendars.NullCalendar;
import jquant.time.daycounters.Actual360;
import jquant.time.daycounters.Actual365Fixed;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static jquant.pricingengines.BlackFormula.blackFormulaCashItmProbability;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Port of the digital-coupon cases from QuantLib
 * {@code test-suite/digitalcoupon.cpp}.
 *
 * <p>This class currently covers the official asset-or-nothing cases
 * ({@code testAssetOrNothing}, {@code testAssetOrNothingDeepInTheMoney},
 * {@code testAssetOrNothingDeepOutTheMoney}), cash-or-nothing cases
 * ({@code testCashOrNothing}, {@code testCashOrNothingDeepInTheMoney},
 * {@code testCashOrNothingDeepOutTheMoney}), {@code testCallPutParity}
 * and {@code testReplicationType}.
 */
public class DigitalCouponTest {

    private SavedSettings savedSettings;

    @BeforeEach
    void saveSettings() {
        savedSettings = new SavedSettings();
    }

    @AfterEach
    void restoreSettings() {
        // Official TopLevelFixture clears index histories before
        // SavedSettings restores the evaluation date and other settings.
        IndexManager.Instance.clearHistories();
        savedSettings.restore();
    }

    /** Official {@code CommonVars} from test-suite/digitalcoupon.cpp. */
    private static class CommonVars {
        final Date today, settlement;
        final double nominal;
        final Calendar calendar;
        final IborIndex index;
        final int fixingDays;
        final RelinkableHandle<YieldTermStructure> termStructure =
                new RelinkableHandle<>();
        final double optionTolerance;
        final double blackTolerance;

        CommonVars() {
            fixingDays = 2;
            nominal = 1000000.0;
            index = new Euribor6M(termStructure);
            calendar = index.fixingCalendar();
            today = calendar.adjust(Settings.instance.evaluationDate().Date(),
                    BusinessDayConvention.FOLLOWING);
            Settings.instance.evaluationDate().equal(today);
            settlement = calendar.advance(today, fixingDays, TimeUnit.DAYS);
            termStructure.linkTo(
                    new FlatForward(settlement, 0.05, new Actual365Fixed()), true);
            optionTolerance = 1.0e-04;
            blackTolerance = 1e-10;
        }
    }

    @Test
    public void testAssetOrNothing() {
        CommonVars vars = new CommonVars();

        /*  Call Payoff = (aL+b)Heaviside(aL+b-X) =  a Max[L-X'] + (b+aX')Heaviside(L-X')
            Value Call = aF N(d1') + bN(d2')
            Put Payoff =  (aL+b)Heaviside(X-aL-b) = -a Max[X-L'] + (b+aX')Heaviside(X'-L)
            Value Put = aF N(-d1') + bN(-d2')
            where:
            d1' = ln(F/X')/stdDev + 0.5*stdDev;
        */

        double[] vols = {0.05, 0.15, 0.30};
        double[] strikes = {0.01, 0.02, 0.03, 0.04, 0.05, 0.06, 0.07};
        double[] gearings = {1.0, 2.8};
        double[] spreads = {0.0, 0.005};

        double gap = 1e-7; /* low, in order to compare digital option value
                              with black formula result */
        DigitalReplication replication =
                new DigitalReplication(Replication.Central, gap);
        CumulativeNormalDistribution phi = new CumulativeNormalDistribution();
        for (double capletVol : vols) {
            RelinkableHandle<OptionletVolatilityStructure> vol =
                    new RelinkableHandle<>();
            vol.linkTo(new ConstantOptionletVolatility(
                    vars.today, vars.calendar, BusinessDayConvention.FOLLOWING,
                    capletVol, new Actual360()), true);
            for (double strike : strikes) {
                for (int k = 9; k < 10; k++) {
                    Date startDate = vars.calendar.advance(vars.settlement,
                            (k + 1), TimeUnit.YEARS);
                    Date endDate = vars.calendar.advance(vars.settlement,
                            (k + 2), TimeUnit.YEARS);
                    double nullstrike = Double.NaN;
                    Date paymentDate = endDate;
                    for (int h = 0; h < gearings.length; h++) {

                        double gearing = gearings[h];
                        double spread = spreads[h];

                        FloatingRateCoupon underlying = new IborCoupon(
                                paymentDate, vars.nominal,
                                startDate, endDate,
                                vars.fixingDays, vars.index,
                                gearing, spread);
                        // Floating Rate Coupon - Call Digital option
                        DigitalCoupon digitalCappedCoupon = new DigitalCoupon(
                                underlying,
                                strike, Position.Short, false, nullstrike,
                                nullstrike, Position.Short, false, nullstrike,
                                replication);
                        IborCouponPricer pricer = new BlackIborCouponPricer(vol);
                        digitalCappedCoupon.setPricer(pricer);

                        // Check digital option price vs N(d1) price
                        double accrualPeriod = underlying.accrualPeriod();
                        double discount =
                                vars.termStructure.currentLink().discount(endDate);
                        Date exerciseDate = underlying.fixingDate();
                        double forward = underlying.rate();
                        double effFwd = (forward - spread) / gearing;
                        double effStrike = (strike - spread) / gearing;
                        double stdDev = Math.sqrt(vol.currentLink().blackVariance(
                                exerciseDate, effStrike, false));
                        double d1 = Math.log(effFwd / effStrike) / stdDev + 0.5 * stdDev;
                        double d2 = d1 - stdDev;
                        double N_d1 = phi.value(d1);
                        double N_d2 = phi.value(d2);
                        double nd1Price = (gearing * effFwd * N_d1 + spread * N_d2)
                                * vars.nominal * accrualPeriod * discount;
                        double optionPrice = digitalCappedCoupon.callOptionRate()
                                * vars.nominal * accrualPeriod * discount;
                        double error = Math.abs(nd1Price - optionPrice);
                        if (error > vars.optionTolerance)
                            fail("\nDigital Call Option:"
                                    + "\nVolatility = " + capletVol
                                    + "\nStrike = " + strike
                                    + "\nExercise = " + (k + 1) + " years"
                                    + "\nOption price by replication = " + optionPrice
                                    + "\nOption price by Cox-Rubinstein formula = " + nd1Price
                                    + "\nError " + error);

                        // Check digital option price vs N(d1) price using Vanilla Option class
                        if (spread == 0.0) {
                            Exercise exercise = new EuropeanExercise(exerciseDate);
                            double discountAtFixing =
                                    vars.termStructure.currentLink().discount(exerciseDate);
                            SimpleQuote fwd = new SimpleQuote(effFwd * discountAtFixing);
                            SimpleQuote qRate = new SimpleQuote(0.0);
                            YieldTermStructure qTS = new FlatForward(
                                    vars.today, new Handle<>(qRate, true),
                                    new Actual360());
                            // Official declares an unused SimpleQuote vol(0.0);
                            // official flatVol(today, capletVol, Actual360()) uses capletVol.
                            BlackVolTermStructure volTS = new BlackConstantVol(
                                    vars.today, new NullCalendar(),
                                    capletVol, new Actual360());
                            StrikedTypePayoff callPayoff = new AssetOrNothingPayoff(
                                    Option.Type.Call, effStrike);
                            BlackScholesMertonProcess stochProcess =
                                    new BlackScholesMertonProcess(
                                            new Handle<>(fwd, true),
                                            new Handle<>(qTS, true),
                                            vars.termStructure,
                                            new Handle<>(volTS, true));
                            EuropeanOption callOpt = new EuropeanOption(
                                    callPayoff, exercise);
                            callOpt.setPricingEngine(
                                    new AnalyticEuropeanEngine(stochProcess));
                            double callVO = vars.nominal * gearing
                                    * accrualPeriod * callOpt.NPV()
                                    * discount / discountAtFixing
                                    * forward / effFwd;
                            error = Math.abs(nd1Price - callVO);
                            if (error > vars.blackTolerance)
                                fail("\nDigital Call Option:"
                                        + "\nVolatility = " + capletVol
                                        + "\nStrike = " + strike
                                        + "\nExercise = " + (k + 1) + " years"
                                        + "\nOption price by Black asset-ot-nothing payoff = "
                                        + callVO
                                        + "\nOption price by Cox-Rubinstein = " + nd1Price
                                        + "\nError " + error);
                        }

                        // Floating Rate Coupon + Put Digital option
                        DigitalCoupon digitalFlooredCoupon = new DigitalCoupon(
                                underlying,
                                nullstrike, Position.Long, false, nullstrike,
                                strike, Position.Long, false, nullstrike,
                                replication);
                        digitalFlooredCoupon.setPricer(pricer);

                        // Check digital option price vs N(d1) price
                        N_d1 = phi.value(-d1);
                        N_d2 = phi.value(-d2);
                        nd1Price = (gearing * effFwd * N_d1 + spread * N_d2)
                                * vars.nominal * accrualPeriod * discount;
                        optionPrice = digitalFlooredCoupon.putOptionRate()
                                * vars.nominal * accrualPeriod * discount;
                        error = Math.abs(nd1Price - optionPrice);
                        if (error > vars.optionTolerance)
                            fail("\nDigital Put Option:"
                                    + "\nVolatility = " + capletVol
                                    + "\nStrike = " + strike
                                    + "\nExercise = " + (k + 1) + " years"
                                    + "\nOption price by replication = " + optionPrice
                                    + "\nOption price by Cox-Rubinstein = " + nd1Price
                                    + "\nError " + error);

                        // Check digital option price vs N(d1) price using Vanilla Option class
                        if (spread == 0.0) {
                            Exercise exercise = new EuropeanExercise(exerciseDate);
                            double discountAtFixing =
                                    vars.termStructure.currentLink().discount(exerciseDate);
                            SimpleQuote fwd = new SimpleQuote(effFwd * discountAtFixing);
                            SimpleQuote qRate = new SimpleQuote(0.0);
                            YieldTermStructure qTS = new FlatForward(
                                    vars.today, new Handle<>(qRate, true),
                                    new Actual360());
                            // Official declares an unused SimpleQuote vol(0.0);
                            // official flatVol(today, capletVol, Actual360()) uses capletVol.
                            BlackVolTermStructure volTS = new BlackConstantVol(
                                    vars.today, new NullCalendar(),
                                    capletVol, new Actual360());
                            StrikedTypePayoff putPayoff = new AssetOrNothingPayoff(
                                    Option.Type.Put, effStrike);
                            BlackScholesMertonProcess stochProcess =
                                    new BlackScholesMertonProcess(
                                            new Handle<>(fwd, true),
                                            new Handle<>(qTS, true),
                                            vars.termStructure,
                                            new Handle<>(volTS, true));
                            EuropeanOption putOpt = new EuropeanOption(
                                    putPayoff, exercise);
                            putOpt.setPricingEngine(
                                    new AnalyticEuropeanEngine(stochProcess));
                            double putVO = vars.nominal * gearing
                                    * accrualPeriod * putOpt.NPV()
                                    * discount / discountAtFixing
                                    * forward / effFwd;
                            error = Math.abs(nd1Price - putVO);
                            if (error > vars.blackTolerance)
                                fail("\nDigital Put Option:"
                                        + "\nVolatility = " + capletVol
                                        + "\nStrike = " + strike
                                        + "\nExercise = " + (k + 1) + " years"
                                        + "\nOption price by Black asset-ot-nothing payoff = "
                                        + putVO
                                        + "\nOption price by Cox-Rubinstein = " + nd1Price
                                        + "\nError " + error);
                        }
                    }
                }
            }
        }
    }

    @Test
    public void testAssetOrNothingDeepInTheMoney() {
        CommonVars vars = new CommonVars();

        double gearing = 1.0;
        double spread = 0.0;

        double capletVolatility = 0.0001;
        RelinkableHandle<OptionletVolatilityStructure> volatility =
                new RelinkableHandle<>();
        volatility.linkTo(new ConstantOptionletVolatility(
                vars.today, vars.calendar, BusinessDayConvention.FOLLOWING,
                capletVolatility, new Actual360()), true);

        for (int k = 0; k < 10; k++) {   // Loop on start and end dates
            Date startDate = vars.calendar.advance(vars.settlement,
                    (k + 1), TimeUnit.YEARS);
            Date endDate = vars.calendar.advance(vars.settlement,
                    (k + 2), TimeUnit.YEARS);
            double nullstrike = Double.NaN;
            Date paymentDate = endDate;

            FloatingRateCoupon underlying = new IborCoupon(
                    paymentDate, vars.nominal,
                    startDate, endDate,
                    vars.fixingDays, vars.index,
                    gearing, spread);

            // Floating Rate Coupon - Deep-in-the-money Call Digital option
            double strike = 0.001;
            DigitalCoupon digitalCappedCoupon = new DigitalCoupon(
                    underlying,
                    strike, Position.Short, false, nullstrike,
                    nullstrike, Position.Short, false, nullstrike);
            IborCouponPricer pricer = new BlackIborCouponPricer(volatility);
            digitalCappedCoupon.setPricer(pricer);

            // Check price vs its target price
            double accrualPeriod = underlying.accrualPeriod();
            double discount = vars.termStructure.currentLink().discount(endDate);

            double targetOptionPrice = underlying.price(vars.termStructure);
            double targetPrice = 0.0;
            double digitalPrice = digitalCappedCoupon.price(vars.termStructure);
            double error = Math.abs(targetPrice - digitalPrice);
            double tolerance = 1e-08;
            if (error > tolerance)
                fail("\nFloating Coupon - Digital Call Option:"
                        + "\nVolatility = " + capletVolatility
                        + "\nStrike = " + strike
                        + "\nExercise = " + (k + 1) + " years"
                        + "\nCoupon Price = " + digitalPrice
                        + "\nTarget price = " + targetPrice
                        + "\nError = " + error);

            // Check digital option price
            double replicationOptionPrice = digitalCappedCoupon.callOptionRate()
                    * vars.nominal * accrualPeriod * discount;
            error = Math.abs(targetOptionPrice - replicationOptionPrice);
            double optionTolerance = 1e-08;
            if (error > optionTolerance)
                fail("\nDigital Call Option:"
                        + "\nVolatility = " + capletVolatility
                        + "\nStrike = " + strike
                        + "\nExercise = " + (k + 1) + " years"
                        + "\nPrice by replication = " + replicationOptionPrice
                        + "\nTarget price = " + targetOptionPrice
                        + "\nError = " + error);

            // Floating Rate Coupon + Deep-in-the-money Put Digital option
            strike = 0.99;
            DigitalCoupon digitalFlooredCoupon = new DigitalCoupon(
                    underlying,
                    nullstrike, Position.Long, false, nullstrike,
                    strike, Position.Long, false, nullstrike);
            digitalFlooredCoupon.setPricer(pricer);

            // Check price vs its target price
            targetOptionPrice = underlying.price(vars.termStructure);
            targetPrice = underlying.price(vars.termStructure) + targetOptionPrice;
            digitalPrice = digitalFlooredCoupon.price(vars.termStructure);
            error = Math.abs(targetPrice - digitalPrice);
            tolerance = 2.5e-06;
            if (error > tolerance)
                fail("\nFloating Coupon + Digital Put Option:"
                        + "\nVolatility = " + capletVolatility
                        + "\nStrike = " + strike
                        + "\nExercise = " + (k + 1) + " years"
                        + "\nDigital coupon price = " + digitalPrice
                        + "\nTarget price = " + targetPrice
                        + "\nError " + error);

            // Check digital option
            replicationOptionPrice = digitalFlooredCoupon.putOptionRate()
                    * vars.nominal * accrualPeriod * discount;
            error = Math.abs(targetOptionPrice - replicationOptionPrice);
            optionTolerance = 2.5e-06;
            if (error > optionTolerance)
                fail("\nDigital Put Option:"
                        + "\nVolatility = " + capletVolatility
                        + "\nStrike = " + strike
                        + "\nExercise = " + (k + 1) + " years"
                        + "\nPrice by replication = " + replicationOptionPrice
                        + "\nTarget price = " + targetOptionPrice
                        + "\nError " + error);
        }
    }

    @Test
    public void testAssetOrNothingDeepOutTheMoney() {
        CommonVars vars = new CommonVars();

        double gearing = 1.0;
        double spread = 0.0;

        double capletVolatility = 0.0001;
        RelinkableHandle<OptionletVolatilityStructure> volatility =
                new RelinkableHandle<>();
        volatility.linkTo(new ConstantOptionletVolatility(
                vars.today, vars.calendar, BusinessDayConvention.FOLLOWING,
                capletVolatility, new Actual360()), true);

        for (int k = 0; k < 10; k++) { // loop on start and end dates
            Date startDate = vars.calendar.advance(vars.settlement,
                    (k + 1), TimeUnit.YEARS);
            Date endDate = vars.calendar.advance(vars.settlement,
                    (k + 2), TimeUnit.YEARS);
            double nullstrike = Double.NaN;
            Date paymentDate = endDate;

            FloatingRateCoupon underlying = new IborCoupon(
                    paymentDate, vars.nominal,
                    startDate, endDate,
                    vars.fixingDays, vars.index,
                    gearing, spread);

            // Floating Rate Coupon - Deep-out-of-the-money Call Digital option
            double strike = 0.99;
            DigitalCoupon digitalCappedCoupon = new DigitalCoupon(
                    underlying,
                    strike, Position.Short, false, nullstrike,
                    nullstrike, Position.Long, false, nullstrike);
            IborCouponPricer pricer = new BlackIborCouponPricer(volatility);
            digitalCappedCoupon.setPricer(pricer);

            // Check price vs its target
            double accrualPeriod = underlying.accrualPeriod();
            double discount = vars.termStructure.currentLink().discount(endDate);

            double targetPrice = underlying.price(vars.termStructure);
            double digitalPrice = digitalCappedCoupon.price(vars.termStructure);
            double error = Math.abs(targetPrice - digitalPrice);
            double tolerance = 1e-10;
            if (error > tolerance)
                fail("\nFloating Coupon - Digital Call Option :"
                        + "\nVolatility = " + capletVolatility
                        + "\nStrike = " + strike
                        + "\nExercise = " + (k + 1) + " years"
                        + "\nCoupon price = " + digitalPrice
                        + "\nTarget price = " + targetPrice
                        + "\nError = " + error);

            // Check digital option price
            double targetOptionPrice = 0.;
            double replicationOptionPrice = digitalCappedCoupon.callOptionRate()
                    * vars.nominal * accrualPeriod * discount;
            error = Math.abs(targetOptionPrice - replicationOptionPrice);
            double optionTolerance = 1e-08;
            if (error > optionTolerance)
                fail("\nDigital Call Option:"
                        + "\nVolatility = " + capletVolatility
                        + "\nStrike = " + strike
                        + "\nExercise = " + (k + 1) + " years"
                        + "\nPrice by replication = " + replicationOptionPrice
                        + "\nTarget price = " + targetOptionPrice
                        + "\nError = " + error);

            // Floating Rate Coupon - Deep-out-of-the-money Put Digital option
            strike = 0.01;
            DigitalCoupon digitalFlooredCoupon = new DigitalCoupon(
                    underlying,
                    nullstrike, Position.Long, false, nullstrike,
                    strike, Position.Long, false, nullstrike);
            digitalFlooredCoupon.setPricer(pricer);

            // Check price vs its target
            targetPrice = underlying.price(vars.termStructure);
            digitalPrice = digitalFlooredCoupon.price(vars.termStructure);
            tolerance = 1e-08;
            error = Math.abs(targetPrice - digitalPrice);
            if (error > tolerance)
                fail("\nFloating Coupon + Digital Put Coupon:"
                        + "\nVolatility = " + capletVolatility
                        + "\nStrike = " + strike
                        + "\nExercise = " + (k + 1) + " years"
                        + "\nCoupon price = " + digitalPrice
                        + "\nTarget price = " + targetPrice
                        + "\nError = " + error);

            // Check digital option
            targetOptionPrice = 0.0;
            replicationOptionPrice = digitalFlooredCoupon.putOptionRate()
                    * vars.nominal * accrualPeriod * discount;
            error = Math.abs(targetOptionPrice - replicationOptionPrice);
            if (error > optionTolerance)
                fail("\nDigital Put Coupon:"
                        + "\nVolatility = " + capletVolatility
                        + "\nStrike = " + strike
                        + "\nExercise = " + (k + 1) + " years"
                        + "\nPrice by replication = " + replicationOptionPrice
                        + "\nTarget price = " + targetOptionPrice
                        + "\nError = " + error);
        }
    }

    @Test
    public void testCashOrNothing() {
        CommonVars vars = new CommonVars();

        /*  Call Payoff = R Heaviside(aL+b-X)
            Value Call = R N(d2')
            Put Payoff =  R Heaviside(X-aL-b)
            Value Put = R N(-d2')
            where:
            d2' = ln(F/X')/stdDev - 0.5*stdDev;
        */

        double[] vols = {0.05, 0.15, 0.30};
        double[] strikes = {0.01, 0.02, 0.03, 0.04, 0.05, 0.06, 0.07};

        double gearing = 3.0;
        double spread = -0.0002;

        double gap = 1e-08; /* very low, in order to compare digital option value
                               with black formula result */
        DigitalReplication replication =
                new DigitalReplication(Replication.Central, gap);

        for (double capletVol : vols) {
            RelinkableHandle<OptionletVolatilityStructure> vol =
                    new RelinkableHandle<>();
            vol.linkTo(new ConstantOptionletVolatility(
                    vars.today, vars.calendar, BusinessDayConvention.FOLLOWING,
                    capletVol, new Actual360()), true);
            for (double strike : strikes) {
                for (int k = 0; k < 10; k++) {
                    Date startDate = vars.calendar.advance(vars.settlement,
                            (k + 1), TimeUnit.YEARS);
                    Date endDate = vars.calendar.advance(vars.settlement,
                            (k + 2), TimeUnit.YEARS);
                    double nullstrike = Double.NaN;
                    double cashRate = 0.01;

                    Date paymentDate = endDate;
                    FloatingRateCoupon underlying = new IborCoupon(
                            paymentDate, vars.nominal,
                            startDate, endDate,
                            vars.fixingDays, vars.index,
                            gearing, spread);
                    // Floating Rate Coupon - Call Digital option
                    DigitalCoupon digitalCappedCoupon = new DigitalCoupon(
                            underlying,
                            strike, Position.Short, false, cashRate,
                            nullstrike, Position.Short, false, nullstrike,
                            replication);
                    IborCouponPricer pricer = new BlackIborCouponPricer(vol);
                    digitalCappedCoupon.setPricer(pricer);

                    // Check digital option price vs N(d2) price
                    Date exerciseDate = underlying.fixingDate();
                    double forward = underlying.rate();
                    double effFwd = (forward - spread) / gearing;
                    double effStrike = (strike - spread) / gearing;
                    double accrualPeriod = underlying.accrualPeriod();
                    double discount =
                            vars.termStructure.currentLink().discount(endDate);
                    double stdDev = Math.sqrt(vol.currentLink().blackVariance(
                            exerciseDate, effStrike, false));
                    double ITM = blackFormulaCashItmProbability(
                            Option.Type.Call, effStrike, effFwd, stdDev, 0.0);
                    double nd2Price = ITM * vars.nominal * accrualPeriod
                            * discount * cashRate;
                    double optionPrice = digitalCappedCoupon.callOptionRate()
                            * vars.nominal * accrualPeriod * discount;
                    double error = Math.abs(nd2Price - optionPrice);
                    if (error > vars.optionTolerance)
                        fail("\nDigital Call Option:"
                                + "\nVolatility = " + capletVol
                                + "\nStrike = " + strike
                                + "\nExercise = " + (k + 1) + " years"
                                + "\nPrice by replication = " + optionPrice
                                + "\nPrice by Reiner-Rubinstein = " + nd2Price
                                + "\nError = " + error);

                    // Check digital option price vs N(d2) price using Vanilla Option class
                    Exercise exercise = new EuropeanExercise(exerciseDate);
                    double discountAtFixing =
                            vars.termStructure.currentLink().discount(exerciseDate);
                    SimpleQuote fwd = new SimpleQuote(effFwd * discountAtFixing);
                    SimpleQuote qRate = new SimpleQuote(0.0);
                    YieldTermStructure qTS = new FlatForward(
                            vars.today, new Handle<>(qRate, true),
                            new Actual360());
                    // Official declares an unused SimpleQuote vol(0.0);
                    // official flatVol(today, capletVol, Actual360()) uses capletVol.
                    BlackVolTermStructure volTS = new BlackConstantVol(
                            vars.today, new NullCalendar(),
                            capletVol, new Actual360());
                    StrikedTypePayoff callPayoff = new CashOrNothingPayoff(
                            Option.Type.Call, effStrike, cashRate);
                    BlackScholesMertonProcess stochProcess =
                            new BlackScholesMertonProcess(
                                    new Handle<>(fwd, true),
                                    new Handle<>(qTS, true),
                                    vars.termStructure,
                                    new Handle<>(volTS, true));
                    AnalyticEuropeanEngine engine =
                            new AnalyticEuropeanEngine(stochProcess);
                    EuropeanOption callOpt = new EuropeanOption(
                            callPayoff, exercise);
                    callOpt.setPricingEngine(engine);
                    double callVO = vars.nominal * accrualPeriod
                            * callOpt.NPV()
                            * discount / discountAtFixing;
                    error = Math.abs(nd2Price - callVO);
                    if (error > vars.blackTolerance)
                        fail("\nDigital Call Option:"
                                + "\nVolatility = " + capletVol
                                + "\nStrike = " + strike
                                + "\nExercise = " + (k + 1) + " years"
                                + "\nOption price by Black asset-ot-nothing payoff = "
                                + callVO
                                + "\nOption price by Reiner-Rubinstein = " + nd2Price
                                + "\nError " + error);

                    // Floating Rate Coupon + Put Digital option
                    DigitalCoupon digitalFlooredCoupon = new DigitalCoupon(
                            underlying,
                            nullstrike, Position.Long, false, nullstrike,
                            strike, Position.Long, false, cashRate,
                            replication);
                    digitalFlooredCoupon.setPricer(pricer);

                    // Check digital option price vs N(d2) price
                    ITM = blackFormulaCashItmProbability(
                            Option.Type.Put,
                            effStrike,
                            effFwd,
                            stdDev, 0.0);
                    nd2Price = ITM * vars.nominal * accrualPeriod
                            * discount * cashRate;
                    optionPrice = digitalFlooredCoupon.putOptionRate()
                            * vars.nominal * accrualPeriod * discount;
                    error = Math.abs(nd2Price - optionPrice);
                    if (error > vars.optionTolerance)
                        fail("\nPut Digital Option:"
                                + "\nVolatility = " + capletVol
                                + "\nStrike = " + strike
                                + "\nExercise = " + (k + 1) + " years"
                                + "\nPrice by replication = " + optionPrice
                                + "\nPrice by Reiner-Rubinstein = " + nd2Price
                                + "\nError = " + error);

                    // Check digital option price vs N(d2) price using Vanilla Option class
                    StrikedTypePayoff putPayoff = new CashOrNothingPayoff(
                            Option.Type.Put, effStrike, cashRate);
                    EuropeanOption putOpt = new EuropeanOption(
                            putPayoff, exercise);
                    putOpt.setPricingEngine(engine);
                    double putVO = vars.nominal * accrualPeriod
                            * putOpt.NPV()
                            * discount / discountAtFixing;
                    error = Math.abs(nd2Price - putVO);
                    if (error > vars.blackTolerance)
                        fail("\nDigital Put Option:"
                                + "\nVolatility = " + capletVol
                                + "\nStrike = " + strike
                                + "\nExercise = " + (k + 1) + " years"
                                + "\nOption price by Black asset-ot-nothing payoff = "
                                + putVO
                                + "\nOption price by Reiner-Rubinstein = " + nd2Price
                                + "\nError " + error);
                }
            }
        }
    }

    @Test
    public void testCashOrNothingDeepInTheMoney() {
        CommonVars vars = new CommonVars();

        double gearing = 1.0;
        double spread = 0.0;

        double capletVolatility = 0.0001;
        RelinkableHandle<OptionletVolatilityStructure> volatility =
                new RelinkableHandle<>();
        volatility.linkTo(new ConstantOptionletVolatility(
                vars.today, vars.calendar, BusinessDayConvention.FOLLOWING,
                capletVolatility, new Actual360()), true);

        for (int k = 0; k < 10; k++) {   // Loop on start and end dates
            Date startDate = vars.calendar.advance(vars.settlement,
                    (k + 1), TimeUnit.YEARS);
            Date endDate = vars.calendar.advance(vars.settlement,
                    (k + 2), TimeUnit.YEARS);
            double nullstrike = Double.NaN;
            double cashRate = 0.01;
            Date paymentDate = endDate;

            FloatingRateCoupon underlying = new IborCoupon(
                    paymentDate, vars.nominal,
                    startDate, endDate,
                    vars.fixingDays, vars.index,
                    gearing, spread);
            // Floating Rate Coupon - Deep-in-the-money Call Digital option
            double strike = 0.001;
            DigitalCoupon digitalCappedCoupon = new DigitalCoupon(
                    underlying,
                    strike, Position.Short, false, cashRate,
                    nullstrike, Position.Short, false, nullstrike);
            IborCouponPricer pricer = new BlackIborCouponPricer(volatility);
            digitalCappedCoupon.setPricer(pricer);

            // Check price vs its target
            double accrualPeriod = underlying.accrualPeriod();
            double discount = vars.termStructure.currentLink().discount(endDate);

            double targetOptionPrice = cashRate * vars.nominal
                    * accrualPeriod * discount;
            double targetPrice = underlying.price(vars.termStructure)
                    - targetOptionPrice;
            double digitalPrice = digitalCappedCoupon.price(vars.termStructure);

            double error = Math.abs(targetPrice - digitalPrice);
            double tolerance = 1e-07;
            if (error > tolerance)
                fail("\nFloating Coupon - Digital Call Coupon:"
                        + "\nVolatility = " + capletVolatility
                        + "\nStrike = " + strike
                        + "\nExercise = " + (k + 1) + " years"
                        + "\nCoupon price = " + digitalPrice
                        + "\nTarget price = " + targetPrice
                        + "\nError " + error);

            // Check digital option price
            double replicationOptionPrice = digitalCappedCoupon.callOptionRate()
                    * vars.nominal * accrualPeriod * discount;
            error = Math.abs(targetOptionPrice - replicationOptionPrice);
            double optionTolerance = 1e-07;
            if (error > optionTolerance)
                fail("\nDigital Call Option:"
                        + "\nVolatility = " + capletVolatility
                        + "\nStrike = " + strike
                        + "\nExercise = " + (k + 1) + " years"
                        + "\nPrice by replication = " + replicationOptionPrice
                        + "\nTarget price = " + targetOptionPrice
                        + "\nError = " + error);

            // Floating Rate Coupon + Deep-in-the-money Put Digital option
            strike = 0.99;
            DigitalCoupon digitalFlooredCoupon = new DigitalCoupon(
                    underlying,
                    nullstrike, Position.Long, false, nullstrike,
                    strike, Position.Long, false, cashRate);
            digitalFlooredCoupon.setPricer(pricer);

            // Check price vs its target
            targetPrice = underlying.price(vars.termStructure) + targetOptionPrice;
            digitalPrice = digitalFlooredCoupon.price(vars.termStructure);
            error = Math.abs(targetPrice - digitalPrice);
            if (error > tolerance)
                fail("\nFloating Coupon + Digital Put Option:"
                        + "\nVolatility = " + capletVolatility
                        + "\nStrike = " + strike
                        + "\nExercise = " + (k + 1) + " years"
                        + "\nCoupon price = " + digitalPrice
                        + "\nTarget price  = " + targetPrice
                        + "\nError = " + error);

            // Check digital option
            replicationOptionPrice = digitalFlooredCoupon.putOptionRate()
                    * vars.nominal * accrualPeriod * discount;
            error = Math.abs(targetOptionPrice - replicationOptionPrice);
            if (error > optionTolerance)
                fail("\nDigital Put Coupon:"
                        + "\nVolatility = " + capletVolatility
                        + "\nStrike = " + strike
                        + "\nExercise = " + (k + 1) + " years"
                        + "\nPrice by replication = " + replicationOptionPrice
                        + "\nTarget price = " + targetOptionPrice
                        + "\nError = " + error);
        }
    }

    @Test
    public void testCashOrNothingDeepOutTheMoney() {
        CommonVars vars = new CommonVars();

        double gearing = 1.0;
        double spread = 0.0;

        double capletVolatility = 0.0001;
        RelinkableHandle<OptionletVolatilityStructure> volatility =
                new RelinkableHandle<>();
        volatility.linkTo(new ConstantOptionletVolatility(
                vars.today, vars.calendar, BusinessDayConvention.FOLLOWING,
                capletVolatility, new Actual360()), true);

        for (int k = 0; k < 10; k++) { // loop on start and end dates
            Date startDate = vars.calendar.advance(vars.settlement,
                    (k + 1), TimeUnit.YEARS);
            Date endDate = vars.calendar.advance(vars.settlement,
                    (k + 2), TimeUnit.YEARS);
            double nullstrike = Double.NaN;
            double cashRate = 0.01;
            Date paymentDate = endDate;

            FloatingRateCoupon underlying = new IborCoupon(
                    paymentDate, vars.nominal,
                    startDate, endDate,
                    vars.fixingDays, vars.index,
                    gearing, spread);
            // Deep out-of-the-money Capped Digital Coupon
            double strike = 0.99;
            DigitalCoupon digitalCappedCoupon = new DigitalCoupon(
                    underlying,
                    strike, Position.Short, false, cashRate,
                    nullstrike, Position.Short, false, nullstrike);

            IborCouponPricer pricer = new BlackIborCouponPricer(volatility);
            digitalCappedCoupon.setPricer(pricer);

            // Check price vs its target
            double accrualPeriod = underlying.accrualPeriod();
            double discount = vars.termStructure.currentLink().discount(endDate);

            double targetPrice = underlying.price(vars.termStructure);
            double digitalPrice = digitalCappedCoupon.price(vars.termStructure);
            double error = Math.abs(targetPrice - digitalPrice);
            double tolerance = 1e-10;
            if (error > tolerance)
                fail("\nFloating Coupon + Digital Call Option:"
                        + "\nVolatility = " + capletVolatility
                        + "\nStrike = " + strike
                        + "\nExercise = " + (k + 1) + " years"
                        + "\nCoupon price = " + digitalPrice
                        + "\nTarget price  = " + targetPrice
                        + "\nError = " + error);

            // Check digital option price
            double targetOptionPrice = 0.;
            double replicationOptionPrice = digitalCappedCoupon.callOptionRate()
                    * vars.nominal * accrualPeriod * discount;
            error = Math.abs(targetOptionPrice - replicationOptionPrice);
            double optionTolerance = 1e-10;
            if (error > optionTolerance)
                fail("\nDigital Call Option:"
                        + "\nVolatility = " + capletVolatility
                        + "\nStrike = " + strike
                        + "\nExercise = " + (k + 1) + " years"
                        + "\nPrice by replication = " + replicationOptionPrice
                        + "\nTarget price = " + targetOptionPrice
                        + "\nError = " + error);

            // Deep out-of-the-money Floored Digital Coupon
            strike = 0.01;
            DigitalCoupon digitalFlooredCoupon = new DigitalCoupon(
                    underlying,
                    nullstrike, Position.Long, false, nullstrike,
                    strike, Position.Long, false, cashRate);
            digitalFlooredCoupon.setPricer(pricer);

            // Check price vs its target
            targetPrice = underlying.price(vars.termStructure);
            digitalPrice = digitalFlooredCoupon.price(vars.termStructure);
            tolerance = 1e-09;
            error = Math.abs(targetPrice - digitalPrice);
            if (error > tolerance)
                fail("\nDigital Floored Coupon:"
                        + "\nVolatility = " + capletVolatility
                        + "\nStrike = " + strike
                        + "\nExercise = " + (k + 1) + " years"
                        + "\nCoupon price = " + digitalPrice
                        + "\nTarget price  = " + targetPrice
                        + "\nError = " + error);

            // Check digital option
            targetOptionPrice = 0.0;
            replicationOptionPrice = digitalFlooredCoupon.putOptionRate()
                    * vars.nominal * accrualPeriod * discount;
            error = Math.abs(targetOptionPrice - replicationOptionPrice);
            if (error > optionTolerance)
                fail("\nDigital Put Option:"
                        + "\nVolatility = " + capletVolatility
                        + "\nStrike = " + strike
                        + "\nExercise = " + (k + 1) + " years"
                        + "\nPrice by replication " + replicationOptionPrice
                        + "\nTarget price " + targetOptionPrice
                        + "\nError " + error);
        }
    }

    @Test
    public void testCallPutParity() {
        CommonVars vars = new CommonVars();

        double[] vols = {0.05, 0.15, 0.30};
        double[] strikes = {0.01, 0.02, 0.03, 0.04, 0.05, 0.06, 0.07};

        double gearing = 1.0;
        double spread = 0.0;

        for (double capletVolatility : vols) {
            RelinkableHandle<OptionletVolatilityStructure> volatility =
                    new RelinkableHandle<>();
            volatility.linkTo(new ConstantOptionletVolatility(
                    vars.today, vars.calendar, BusinessDayConvention.FOLLOWING,
                    capletVolatility, new Actual360()), true);
            for (double strike : strikes) {
                for (int k = 0; k < 10; k++) {
                    Date startDate = vars.calendar.advance(vars.settlement,
                            (k + 1), TimeUnit.YEARS);
                    Date endDate = vars.calendar.advance(vars.settlement,
                            (k + 2), TimeUnit.YEARS);
                    double nullstrike = Double.NaN;

                    Date paymentDate = endDate;

                    FloatingRateCoupon underlying = new IborCoupon(
                            paymentDate, vars.nominal,
                            startDate, endDate,
                            vars.fixingDays, vars.index,
                            gearing, spread);
                    // Cash-or-Nothing
                    double cashRate = 0.01;
                    // Floating Rate Coupon + Call Digital option
                    DigitalCoupon cash_digitalCallCoupon = new DigitalCoupon(
                            underlying,
                            strike, Position.Long, false, cashRate,
                            nullstrike, Position.Long, false, nullstrike);
                    IborCouponPricer pricer = new BlackIborCouponPricer(volatility);
                    cash_digitalCallCoupon.setPricer(pricer);
                    // Floating Rate Coupon - Put Digital option
                    DigitalCoupon cash_digitalPutCoupon = new DigitalCoupon(
                            underlying,
                            nullstrike, Position.Long, false, nullstrike,
                            strike, Position.Short, false, cashRate);

                    cash_digitalPutCoupon.setPricer(pricer);
                    double digitalPrice =
                            cash_digitalCallCoupon.price(vars.termStructure)
                                    - cash_digitalPutCoupon.price(vars.termStructure);
                    // Target price
                    double accrualPeriod = underlying.accrualPeriod();
                    double discount =
                            vars.termStructure.currentLink().discount(endDate);
                    double targetPrice = vars.nominal * accrualPeriod
                            * discount * cashRate;

                    double error = Math.abs(targetPrice - digitalPrice);
                    double tolerance = 1.e-08;
                    if (error > tolerance)
                        fail("\nCash-or-nothing:"
                                + "\nVolatility = " + capletVolatility
                                + "\nStrike = " + strike
                                + "\nExercise = " + (k + 1) + " years"
                                + "\nPrice = " + digitalPrice
                                + "\nTarget Price  = " + targetPrice
                                + "\nError = " + error);

                    // Asset-or-Nothing
                    // Floating Rate Coupon + Call Digital option
                    DigitalCoupon asset_digitalCallCoupon = new DigitalCoupon(
                            underlying,
                            strike, Position.Long, false, nullstrike,
                            nullstrike, Position.Long, false, nullstrike);
                    asset_digitalCallCoupon.setPricer(pricer);
                    // Floating Rate Coupon - Put Digital option
                    DigitalCoupon asset_digitalPutCoupon = new DigitalCoupon(
                            underlying,
                            nullstrike, Position.Long, false, nullstrike,
                            strike, Position.Short, false, nullstrike);
                    asset_digitalPutCoupon.setPricer(pricer);
                    digitalPrice = asset_digitalCallCoupon.price(vars.termStructure)
                            - asset_digitalPutCoupon.price(vars.termStructure);
                    // Target price
                    targetPrice = vars.nominal * accrualPeriod
                            * discount * underlying.rate();
                    error = Math.abs(targetPrice - digitalPrice);
                    tolerance = 1.e-07;
                    if (error > tolerance)
                        fail("\nAsset-or-nothing:"
                                + "\nVolatility = " + capletVolatility
                                + "\nStrike = " + strike
                                + "\nExercise = " + (k + 1) + " years"
                                + "\nPrice = " + digitalPrice
                                + "\nTarget Price  = " + targetPrice
                                + "\nError = " + error);
                }
            }
        }
    }

    @Test
    public void testReplicationType() {
        CommonVars vars = new CommonVars();

        double[] vols = {0.05, 0.15, 0.30};
        double[] strikes = {0.01, 0.02, 0.03, 0.04, 0.05, 0.06, 0.07};

        double gearing = 1.0;
        double spread = 0.0;

        double gap = 1e-04;
        DigitalReplication subReplication =
                new DigitalReplication(Replication.Sub, gap);
        DigitalReplication centralReplication =
                new DigitalReplication(Replication.Central, gap);
        DigitalReplication superReplication =
                new DigitalReplication(Replication.Super, gap);

        for (double capletVolatility : vols) {
            RelinkableHandle<OptionletVolatilityStructure> volatility =
                    new RelinkableHandle<>();
            volatility.linkTo(new ConstantOptionletVolatility(
                    vars.today, vars.calendar, BusinessDayConvention.FOLLOWING,
                    capletVolatility, new Actual360()), true);
            for (double strike : strikes) {
                for (int k = 0; k < 10; k++) {
                    Date startDate = vars.calendar.advance(vars.settlement,
                            (k + 1), TimeUnit.YEARS);
                    Date endDate = vars.calendar.advance(vars.settlement,
                            (k + 2), TimeUnit.YEARS);
                    double nullstrike = Double.NaN;

                    Date paymentDate = endDate;

                    FloatingRateCoupon underlying = new IborCoupon(
                            paymentDate, vars.nominal,
                            startDate, endDate,
                            vars.fixingDays, vars.index,
                            gearing, spread);
                    // Cash-or-Nothing
                    double cashRate = 0.005;
                    // Floating Rate Coupon + Call Digital option
                    DigitalCoupon sub_cash_longDigitalCallCoupon = new DigitalCoupon(
                            underlying,
                            strike, Position.Long, false, cashRate,
                            nullstrike, Position.Long, false, nullstrike,
                            subReplication);
                    DigitalCoupon central_cash_longDigitalCallCoupon = new DigitalCoupon(
                            underlying,
                            strike, Position.Long, false, cashRate,
                            nullstrike, Position.Long, false, nullstrike,
                            centralReplication);
                    DigitalCoupon over_cash_longDigitalCallCoupon = new DigitalCoupon(
                            underlying,
                            strike, Position.Long, false, cashRate,
                            nullstrike, Position.Long, false, nullstrike,
                            superReplication);
                    IborCouponPricer pricer = new BlackIborCouponPricer(volatility);
                    sub_cash_longDigitalCallCoupon.setPricer(pricer);
                    central_cash_longDigitalCallCoupon.setPricer(pricer);
                    over_cash_longDigitalCallCoupon.setPricer(pricer);
                    double sub_digitalPrice =
                            sub_cash_longDigitalCallCoupon.price(vars.termStructure);
                    double central_digitalPrice =
                            central_cash_longDigitalCallCoupon.price(vars.termStructure);
                    double over_digitalPrice =
                            over_cash_longDigitalCallCoupon.price(vars.termStructure);
                    double tolerance = 1.e-09;
                    if (((sub_digitalPrice > central_digitalPrice)
                            && Math.abs(central_digitalPrice - sub_digitalPrice) > tolerance)
                            || ((central_digitalPrice > over_digitalPrice)
                            && Math.abs(central_digitalPrice - over_digitalPrice) > tolerance)) {
                        fail("\nCash-or-nothing: Floating Rate Coupon + Call Digital option"
                                + "\nVolatility = " + capletVolatility
                                + "\nStrike = " + strike
                                + "\nExercise = " + (k + 1) + " years"
                                + "\nSub-Replication Price = " + sub_digitalPrice
                                + "\nCentral-Replication Price = " + central_digitalPrice
                                + "\nOver-Replication Price = " + over_digitalPrice);
                    }

                    // Floating Rate Coupon - Call Digital option
                    DigitalCoupon sub_cash_shortDigitalCallCoupon = new DigitalCoupon(
                            underlying,
                            strike, Position.Short, false, cashRate,
                            nullstrike, Position.Long, false, nullstrike,
                            subReplication);
                    DigitalCoupon central_cash_shortDigitalCallCoupon = new DigitalCoupon(
                            underlying,
                            strike, Position.Short, false, cashRate,
                            nullstrike, Position.Long, false, nullstrike,
                            centralReplication);
                    DigitalCoupon over_cash_shortDigitalCallCoupon = new DigitalCoupon(
                            underlying,
                            strike, Position.Short, false, cashRate,
                            nullstrike, Position.Long, false, nullstrike,
                            superReplication);
                    sub_cash_shortDigitalCallCoupon.setPricer(pricer);
                    central_cash_shortDigitalCallCoupon.setPricer(pricer);
                    over_cash_shortDigitalCallCoupon.setPricer(pricer);
                    sub_digitalPrice =
                            sub_cash_shortDigitalCallCoupon.price(vars.termStructure);
                    central_digitalPrice =
                            central_cash_shortDigitalCallCoupon.price(vars.termStructure);
                    over_digitalPrice =
                            over_cash_shortDigitalCallCoupon.price(vars.termStructure);
                    if (((sub_digitalPrice > central_digitalPrice)
                            && Math.abs(central_digitalPrice - sub_digitalPrice) > tolerance)
                            || ((central_digitalPrice > over_digitalPrice)
                            && Math.abs(central_digitalPrice - over_digitalPrice) > tolerance))
                        fail("\nCash-or-nothing: Floating Rate Coupon - Call Digital option"
                                + "\nVolatility = " + capletVolatility
                                + "\nStrike = " + strike
                                + "\nExercise = " + (k + 1) + " years"
                                + "\nSub-Replication Price = " + sub_digitalPrice
                                + "\nCentral-Replication Price = " + central_digitalPrice
                                + "\nOver-Replication Price = " + over_digitalPrice);
                    // Floating Rate Coupon + Put Digital option
                    DigitalCoupon sub_cash_longDigitalPutCoupon = new DigitalCoupon(
                            underlying,
                            nullstrike, Position.Long, false, nullstrike,
                            strike, Position.Long, false, cashRate,
                            subReplication);
                    DigitalCoupon central_cash_longDigitalPutCoupon = new DigitalCoupon(
                            underlying,
                            nullstrike, Position.Long, false, nullstrike,
                            strike, Position.Long, false, cashRate,
                            centralReplication);
                    DigitalCoupon over_cash_longDigitalPutCoupon = new DigitalCoupon(
                            underlying,
                            nullstrike, Position.Long, false, nullstrike,
                            strike, Position.Long, false, cashRate,
                            superReplication);
                    sub_cash_longDigitalPutCoupon.setPricer(pricer);
                    central_cash_longDigitalPutCoupon.setPricer(pricer);
                    over_cash_longDigitalPutCoupon.setPricer(pricer);
                    sub_digitalPrice =
                            sub_cash_longDigitalPutCoupon.price(vars.termStructure);
                    central_digitalPrice =
                            central_cash_longDigitalPutCoupon.price(vars.termStructure);
                    over_digitalPrice =
                            over_cash_longDigitalPutCoupon.price(vars.termStructure);
                    if (((sub_digitalPrice > central_digitalPrice)
                            && Math.abs(central_digitalPrice - sub_digitalPrice) > tolerance)
                            || ((central_digitalPrice > over_digitalPrice)
                            && Math.abs(central_digitalPrice - over_digitalPrice) > tolerance))
                        fail("\nCash-or-nothing: Floating Rate Coupon + Put Digital option"
                                + "\nVolatility = " + capletVolatility
                                + "\nStrike = " + strike
                                + "\nExercise = " + (k + 1) + " years"
                                + "\nSub-Replication Price = " + sub_digitalPrice
                                + "\nCentral-Replication Price = " + central_digitalPrice
                                + "\nOver-Replication Price = " + over_digitalPrice);

                    // Floating Rate Coupon - Put Digital option
                    DigitalCoupon sub_cash_shortDigitalPutCoupon = new DigitalCoupon(
                            underlying,
                            nullstrike, Position.Long, false, nullstrike,
                            strike, Position.Short, false, cashRate,
                            subReplication);
                    DigitalCoupon central_cash_shortDigitalPutCoupon = new DigitalCoupon(
                            underlying,
                            nullstrike, Position.Long, false, nullstrike,
                            strike, Position.Short, false, cashRate,
                            centralReplication);
                    DigitalCoupon over_cash_shortDigitalPutCoupon = new DigitalCoupon(
                            underlying,
                            nullstrike, Position.Long, false, nullstrike,
                            strike, Position.Short, false, cashRate,
                            superReplication);
                    sub_cash_shortDigitalPutCoupon.setPricer(pricer);
                    central_cash_shortDigitalPutCoupon.setPricer(pricer);
                    over_cash_shortDigitalPutCoupon.setPricer(pricer);
                    sub_digitalPrice =
                            sub_cash_shortDigitalPutCoupon.price(vars.termStructure);
                    central_digitalPrice =
                            central_cash_shortDigitalPutCoupon.price(vars.termStructure);
                    over_digitalPrice =
                            over_cash_shortDigitalPutCoupon.price(vars.termStructure);
                    if (((sub_digitalPrice > central_digitalPrice)
                            && Math.abs(central_digitalPrice - sub_digitalPrice) > tolerance)
                            || ((central_digitalPrice > over_digitalPrice)
                            && Math.abs(central_digitalPrice - over_digitalPrice) > tolerance))
                        fail("\nCash-or-nothing: Floating Rate Coupon + Call Digital option"
                                + "\nVolatility = " + capletVolatility
                                + "\nStrike = " + strike
                                + "\nExercise = " + (k + 1) + " years"
                                + "\nSub-Replication Price = " + sub_digitalPrice
                                + "\nCentral-Replication Price = " + central_digitalPrice
                                + "\nOver-Replication Price = " + over_digitalPrice);
                }
            }
        }
    }
}
