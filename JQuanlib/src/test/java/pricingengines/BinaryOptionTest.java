package pricingengines;

import jquant.AmericanExercise;
import jquant.Barrier;
import jquant.BarrierOption;
import jquant.Compounding;
import jquant.Exercise;
import jquant.Handle;
import jquant.Option;
import jquant.SavedSettings;
import jquant.Settings;
import jquant.indexes.IndexManager;
import jquant.pricingengines.AssetOrNothingPayoff;
import jquant.pricingengines.CashOrNothingPayoff;
import jquant.pricingengines.StrikedTypePayoff;
import jquant.pricingengines.barrier.AnalyticBinaryBarrierEngine;
import jquant.processes.BlackScholesMertonProcess;
import jquant.quotes.SimpleQuote;
import jquant.termstructures.YieldTermStructure;
import jquant.termstructures.volatility.equityfx.BlackConstantVol;
import jquant.termstructures.volatility.equityfx.BlackVolTermStructure;
import jquant.termstructures.yield.FlatForward;
import jquant.time.Date;
import jquant.time.DayCounter;
import jquant.time.Frequency;
import jquant.time.calendars.NullCalendar;
import jquant.time.daycounters.Actual360;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.fail;

/**
 * Binary barrier option pricing from QuantLib
 * {@code test-suite/binaryoption.cpp}.
 *
 * <p>Complete port of the three official cases: cash-or-nothing Haug
 * values, asset-or-nothing Haug values and cash-or-nothing at low
 * volatility.
 */
public class BinaryOptionTest {

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

    /** Official {@code BinaryOptionData}. */
    private static class BinaryOptionData {
        final Barrier.Type barrierType;
        final double barrier;
        final double cash;
        final Option.Type type;
        final double strike;
        final double s;
        final double q;
        final double r;
        final double t;
        final double v;
        final double result;
        final double tol;

        BinaryOptionData(Barrier.Type barrierType, double barrier,
                         double cash, Option.Type type, double strike,
                         double s, double q, double r, double t, double v,
                         double result, double tol) {
            this.barrierType = barrierType;
            this.barrier = barrier;
            this.cash = cash;
            this.type = type;
            this.strike = strike;
            this.s = s;
            this.q = q;
            this.r = r;
            this.t = t;
            this.v = v;
            this.result = result;
            this.tol = tol;
        }
    }

    @Test
    public void testCashOrNothingHaugValues() {
        BinaryOptionData[] values = {
                /* The data below are from
                  "Option pricing formulas 2nd Ed.", E.G. Haug, McGraw-Hill 2007
                  pag. 180 - cases 13,14,17,18,21,22,25,26
                  Note:
                    q is the dividend rate, while the book gives b, the cost
                    of carry (q=r-b)
                */
                // barrierType, barrier, cash, type, strike, spot, q, r, t, vol, value, tol
                new BinaryOptionData(Barrier.Type.DownIn, 100.00, 15.00, Option.Type.Call, 102.00, 105.00, 0.00, 0.10, 0.5, 0.20, 4.9289, 1e-4),
                new BinaryOptionData(Barrier.Type.DownIn, 100.00, 15.00, Option.Type.Call, 98.00, 105.00, 0.00, 0.10, 0.5, 0.20, 6.2150, 1e-4),
                // following value is wrong in book.
                new BinaryOptionData(Barrier.Type.UpIn, 100.00, 15.00, Option.Type.Call, 102.00, 95.00, 0.00, 0.10, 0.5, 0.20, 5.8926, 1e-4),
                new BinaryOptionData(Barrier.Type.UpIn, 100.00, 15.00, Option.Type.Call, 98.00, 95.00, 0.00, 0.10, 0.5, 0.20, 7.4519, 1e-4),
                // 17,18
                new BinaryOptionData(Barrier.Type.DownIn, 100.00, 15.00, Option.Type.Put, 102.00, 105.00, 0.00, 0.10, 0.5, 0.20, 4.4314, 1e-4),
                new BinaryOptionData(Barrier.Type.DownIn, 100.00, 15.00, Option.Type.Put, 98.00, 105.00, 0.00, 0.10, 0.5, 0.20, 3.1454, 1e-4),
                new BinaryOptionData(Barrier.Type.UpIn, 100.00, 15.00, Option.Type.Put, 102.00, 95.00, 0.00, 0.10, 0.5, 0.20, 5.3297, 1e-4),
                new BinaryOptionData(Barrier.Type.UpIn, 100.00, 15.00, Option.Type.Put, 98.00, 95.00, 0.00, 0.10, 0.5, 0.20, 3.7704, 1e-4),
                // 21,22
                new BinaryOptionData(Barrier.Type.DownOut, 100.00, 15.00, Option.Type.Call, 102.00, 105.00, 0.00, 0.10, 0.5, 0.20, 4.8758, 1e-4),
                new BinaryOptionData(Barrier.Type.DownOut, 100.00, 15.00, Option.Type.Call, 98.00, 105.00, 0.00, 0.10, 0.5, 0.20, 4.9081, 1e-4),
                new BinaryOptionData(Barrier.Type.UpOut, 100.00, 15.00, Option.Type.Call, 102.00, 95.00, 0.00, 0.10, 0.5, 0.20, 0.0000, 1e-4),
                new BinaryOptionData(Barrier.Type.UpOut, 100.00, 15.00, Option.Type.Call, 98.00, 95.00, 0.00, 0.10, 0.5, 0.20, 0.0407, 1e-4),
                // 25,26
                new BinaryOptionData(Barrier.Type.DownOut, 100.00, 15.00, Option.Type.Put, 102.00, 105.00, 0.00, 0.10, 0.5, 0.20, 0.0323, 1e-4),
                new BinaryOptionData(Barrier.Type.DownOut, 100.00, 15.00, Option.Type.Put, 98.00, 105.00, 0.00, 0.10, 0.5, 0.20, 0.0000, 1e-4),
                new BinaryOptionData(Barrier.Type.UpOut, 100.00, 15.00, Option.Type.Put, 102.00, 95.00, 0.00, 0.10, 0.5, 0.20, 3.0461, 1e-4),
                new BinaryOptionData(Barrier.Type.UpOut, 100.00, 15.00, Option.Type.Put, 98.00, 95.00, 0.00, 0.10, 0.5, 0.20, 3.0054, 1e-4),

                // other values calculated with book vba
                new BinaryOptionData(Barrier.Type.UpIn, 100.00, 15.00, Option.Type.Call, 102.00, 95.00, -0.14, 0.10, 0.5, 0.20, 8.6806, 1e-4),
                new BinaryOptionData(Barrier.Type.UpIn, 100.00, 15.00, Option.Type.Call, 102.00, 95.00, 0.03, 0.10, 0.5, 0.20, 5.3112, 1e-4),
                // degenerate conditions (barrier touched)
                new BinaryOptionData(Barrier.Type.DownIn, 100.00, 15.00, Option.Type.Call, 98.00, 95.00, 0.00, 0.10, 0.5, 0.20, 7.4926, 1e-4),
                new BinaryOptionData(Barrier.Type.UpIn, 100.00, 15.00, Option.Type.Call, 98.00, 105.00, 0.00, 0.10, 0.5, 0.20, 11.1231, 1e-4),
                // 17,18
                new BinaryOptionData(Barrier.Type.DownIn, 100.00, 15.00, Option.Type.Put, 102.00, 98.00, 0.00, 0.10, 0.5, 0.20, 7.1344, 1e-4),
                new BinaryOptionData(Barrier.Type.UpIn, 100.00, 15.00, Option.Type.Put, 102.00, 101.00, 0.00, 0.10, 0.5, 0.20, 5.9299, 1e-4),
                // 21,22
                new BinaryOptionData(Barrier.Type.DownOut, 100.00, 15.00, Option.Type.Call, 98.00, 99.00, 0.00, 0.10, 0.5, 0.20, 0.0000, 1e-4),
                new BinaryOptionData(Barrier.Type.UpOut, 100.00, 15.00, Option.Type.Call, 98.00, 101.00, 0.00, 0.10, 0.5, 0.20, 0.0000, 1e-4),
                // 25,26
                new BinaryOptionData(Barrier.Type.DownOut, 100.00, 15.00, Option.Type.Put, 98.00, 99.00, 0.00, 0.10, 0.5, 0.20, 0.0000, 1e-4),
                new BinaryOptionData(Barrier.Type.UpOut, 100.00, 15.00, Option.Type.Put, 98.00, 101.00, 0.00, 0.10, 0.5, 0.20, 0.0000, 1e-4)
        };

        DayCounter dc = new Actual360();
        Date today = Settings.instance.evaluationDate().Date();

        SimpleQuote spot = new SimpleQuote(100.0);
        SimpleQuote qRate = new SimpleQuote(0.04);
        YieldTermStructure qTS = flatRate(today, qRate, dc);
        SimpleQuote rRate = new SimpleQuote(0.01);
        YieldTermStructure rTS = flatRate(today, rRate, dc);
        SimpleQuote vol = new SimpleQuote(0.25);
        BlackVolTermStructure volTS = flatVol(today, vol, dc);

        for (BinaryOptionData value : values) {
            StrikedTypePayoff payoff = new CashOrNothingPayoff(
                    value.type, value.strike, value.cash);

            Date exDate = today.add(timeToDays(value.t));
            Exercise amExercise = new AmericanExercise(today, exDate, true);

            spot.setValue(value.s);
            qRate.setValue(value.q);
            rRate.setValue(value.r);
            vol.setValue(value.v);

            BlackScholesMertonProcess stochProcess =
                    new BlackScholesMertonProcess(
                            new Handle<>(spot, true),
                            new Handle<>(qTS, true),
                            new Handle<>(rTS, true),
                            new Handle<>(volTS, true));

            BarrierOption opt = new BarrierOption(value.barrierType,
                    value.barrier, 0.0, payoff, amExercise);
            opt.setPricingEngine(new AnalyticBinaryBarrierEngine(stochProcess));

            double calculated = opt.NPV();
            double error = Math.abs(calculated - value.result);
            if (error > value.tol) {
                reportFailure(payoff, amExercise, value, today,
                        calculated, error, value.tol);
            }
        }
    }

    @Test
    public void testAssetOrNothingHaugValues() {
        BinaryOptionData[] values = {
                /* The data below are from
                  "Option pricing formulas 2nd Ed.", E.G. Haug, McGraw-Hill 2007
                  pag. 180 - cases 15,16,19,20,23,24,27,28
                  Note:
                    q is the dividend rate, while the book gives b, the cost
                    of carry (q=r-b)
                */
                // barrierType, barrier, cash, type, strike, spot, q, r, t, vol, value, tol
                new BinaryOptionData(Barrier.Type.DownIn, 100.00, 0.00, Option.Type.Call, 102.00, 105.00, 0.00, 0.10, 0.5, 0.20, 37.2782, 1e-4),
                new BinaryOptionData(Barrier.Type.DownIn, 100.00, 0.00, Option.Type.Call, 98.00, 105.00, 0.00, 0.10, 0.5, 0.20, 45.8530, 1e-4),
                new BinaryOptionData(Barrier.Type.UpIn, 100.00, 0.00, Option.Type.Call, 102.00, 95.00, 0.00, 0.10, 0.5, 0.20, 44.5294, 1e-4),
                new BinaryOptionData(Barrier.Type.UpIn, 100.00, 0.00, Option.Type.Call, 98.00, 95.00, 0.00, 0.10, 0.5, 0.20, 54.9262, 1e-4),
                // 19,20
                new BinaryOptionData(Barrier.Type.DownIn, 100.00, 0.00, Option.Type.Put, 102.00, 105.00, 0.00, 0.10, 0.5, 0.20, 27.5644, 1e-4),
                new BinaryOptionData(Barrier.Type.DownIn, 100.00, 0.00, Option.Type.Put, 98.00, 105.00, 0.00, 0.10, 0.5, 0.20, 18.9896, 1e-4),
                // following value is wrong in book.
                new BinaryOptionData(Barrier.Type.UpIn, 100.00, 0.00, Option.Type.Put, 102.00, 95.00, 0.00, 0.10, 0.5, 0.20, 33.1723, 1e-4),
                new BinaryOptionData(Barrier.Type.UpIn, 100.00, 0.00, Option.Type.Put, 98.00, 95.00, 0.00, 0.10, 0.5, 0.20, 22.7755, 1e-4),
                // 23,24
                new BinaryOptionData(Barrier.Type.DownOut, 100.00, 0.00, Option.Type.Call, 102.00, 105.00, 0.00, 0.10, 0.5, 0.20, 39.9391, 1e-4),
                new BinaryOptionData(Barrier.Type.DownOut, 100.00, 0.00, Option.Type.Call, 98.00, 105.00, 0.00, 0.10, 0.5, 0.20, 40.1574, 1e-4),
                new BinaryOptionData(Barrier.Type.UpOut, 100.00, 0.00, Option.Type.Call, 102.00, 95.00, 0.00, 0.10, 0.5, 0.20, 0.0000, 1e-4),
                new BinaryOptionData(Barrier.Type.UpOut, 100.00, 0.00, Option.Type.Call, 98.00, 95.00, 0.00, 0.10, 0.5, 0.20, 0.2676, 1e-4),
                // 27,28
                new BinaryOptionData(Barrier.Type.DownOut, 100.00, 0.00, Option.Type.Put, 102.00, 105.00, 0.00, 0.10, 0.5, 0.20, 0.2183, 1e-4),
                new BinaryOptionData(Barrier.Type.DownOut, 100.00, 0.00, Option.Type.Put, 98.00, 105.00, 0.00, 0.10, 0.5, 0.20, 0.0000, 1e-4),
                new BinaryOptionData(Barrier.Type.UpOut, 100.00, 0.00, Option.Type.Put, 102.00, 95.00, 0.00, 0.10, 0.5, 0.20, 17.2983, 1e-4),
                new BinaryOptionData(Barrier.Type.UpOut, 100.00, 0.00, Option.Type.Put, 98.00, 95.00, 0.00, 0.10, 0.5, 0.20, 17.0306, 1e-4)
        };

        DayCounter dc = new Actual360();
        Date today = Settings.instance.evaluationDate().Date();

        SimpleQuote spot = new SimpleQuote(100.0);
        SimpleQuote qRate = new SimpleQuote(0.04);
        YieldTermStructure qTS = flatRate(today, qRate, dc);
        SimpleQuote rRate = new SimpleQuote(0.01);
        YieldTermStructure rTS = flatRate(today, rRate, dc);
        SimpleQuote vol = new SimpleQuote(0.25);
        BlackVolTermStructure volTS = flatVol(today, vol, dc);

        for (BinaryOptionData value : values) {
            StrikedTypePayoff payoff = new AssetOrNothingPayoff(
                    value.type, value.strike);

            Date exDate = today.add(timeToDays(value.t));
            Exercise amExercise = new AmericanExercise(today, exDate, true);

            spot.setValue(value.s);
            qRate.setValue(value.q);
            rRate.setValue(value.r);
            vol.setValue(value.v);

            BlackScholesMertonProcess stochProcess =
                    new BlackScholesMertonProcess(
                            new Handle<>(spot, true),
                            new Handle<>(qTS, true),
                            new Handle<>(rTS, true),
                            new Handle<>(volTS, true));

            BarrierOption opt = new BarrierOption(value.barrierType,
                    value.barrier, 0.0, payoff, amExercise);
            opt.setPricingEngine(new AnalyticBinaryBarrierEngine(stochProcess));

            double calculated = opt.NPV();
            double error = Math.abs(calculated - value.result);
            if (error > value.tol) {
                reportFailure(payoff, amExercise, value, today,
                        calculated, error, value.tol);
            }
        }
    }

    @Test
    public void testCashOrNothingLowVolatility() {
        BinaryOptionData[] values = {
                // barrierType, barrier, cash, type, strike, spot, q, r, t, vol, value, tol
                new BinaryOptionData(Barrier.Type.DownOut, 90.00, 10.00, Option.Type.Call, 95.00, 100.00, 0.03, -0.02, 1.0, 0.003, 6.797152649161316, 1e-8),
                new BinaryOptionData(Barrier.Type.UpOut, 110.00, 10.00, Option.Type.Put, 105.00, 100.00, 0.00, 0.03, 1.0, 0.001, 9.704455335485082, 1e-8),
                new BinaryOptionData(Barrier.Type.DownOut, 90.00, 10.00, Option.Type.Call, 100.00, 100.00, 0.00, -0.02, 1.0, 0.001, 0.0, 1e-8)
        };

        DayCounter dc = new Actual360();
        Date today = Settings.instance.evaluationDate().Date();

        SimpleQuote spot = new SimpleQuote(100.0);
        SimpleQuote qRate = new SimpleQuote(0.0);
        YieldTermStructure qTS = flatRate(today, qRate, dc);
        SimpleQuote rRate = new SimpleQuote(0.0);
        YieldTermStructure rTS = flatRate(today, rRate, dc);
        SimpleQuote vol = new SimpleQuote(0.0);
        BlackVolTermStructure volTS = flatVol(today, vol, dc);

        BlackScholesMertonProcess stochProcess =
                new BlackScholesMertonProcess(
                        new Handle<>(spot, true),
                        new Handle<>(qTS, true),
                        new Handle<>(rTS, true),
                        new Handle<>(volTS, true));
        AnalyticBinaryBarrierEngine engine =
                new AnalyticBinaryBarrierEngine(stochProcess);

        for (BinaryOptionData value : values) {
            StrikedTypePayoff payoff = new CashOrNothingPayoff(
                    value.type, value.strike, value.cash);

            Date exDate = today.add(timeToDays(value.t));
            Exercise amExercise = new AmericanExercise(today, exDate, true);

            spot.setValue(value.s);
            qRate.setValue(value.q);
            rRate.setValue(value.r);
            vol.setValue(value.v);

            BarrierOption opt = new BarrierOption(value.barrierType,
                    value.barrier, 0.0, payoff, amExercise);
            opt.setPricingEngine(engine);

            double calculated = opt.NPV();
            double error = Math.abs(calculated - value.result);
            if (!(error <= value.tol)) {
                reportFailure(payoff, amExercise, value, today,
                        calculated, error, value.tol);
            }
        }
    }

    private static void reportFailure(StrikedTypePayoff payoff,
                                      Exercise exercise,
                                      BinaryOptionData value, Date today,
                                      double calculated, double error,
                                      double tolerance) {
        fail(payoff.optionType() + " option with "
                + value.barrierType + " barrier type:"
                + "\n    barrier:          " + value.barrier
                + "\n    spot value:       " + value.s
                + "\n    strike:           " + payoff.strike()
                + "\n    dividend yield:   " + value.q
                + "\n    risk-free rate:   " + value.r
                + "\n    reference date:   " + today
                + "\n    maturity:         " + exercise.lastDate()
                + "\n    volatility:       " + value.v
                + "\n    expected value:   " + value.result
                + "\n    calculated value: " + calculated
                + "\n    error:            " + error
                + "\n    tolerance:        " + tolerance);
    }

    private static int timeToDays(double t) {
        return (int) Math.round(t * 360.0);
    }

    private static YieldTermStructure flatRate(Date today, double rate,
                                               DayCounter dc) {
        return flatRate(today, new SimpleQuote(rate), dc);
    }

    private static YieldTermStructure flatRate(Date today, SimpleQuote rate,
                                               DayCounter dc) {
        return new FlatForward(today, new Handle<>(rate, true), dc,
                Compounding.Continuous, Frequency.ANNUAL);
    }

    private static BlackVolTermStructure flatVol(Date today, double vol,
                                                 DayCounter dc) {
        return flatVol(today, new SimpleQuote(vol), dc);
    }

    private static BlackVolTermStructure flatVol(Date today, SimpleQuote vol,
                                                 DayCounter dc) {
        return new BlackConstantVol(today, new NullCalendar(),
                new Handle<>(vol, true), dc);
    }
}
