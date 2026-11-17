package pricingengines;

import jquant.AmericanExercise;
import jquant.Compounding;
import jquant.EuropeanExercise;
import jquant.EuropeanOption;
import jquant.Handle;
import jquant.Option;
import jquant.Quote;
import jquant.Settings;
import jquant.models.equity.HestonModel;
import jquant.pricingengines.PlainVanillaPayoff;
import jquant.pricingengines.StrikedTypePayoff;
import jquant.pricingengines.vanilla.FdHestonVanillaEngine;
import jquant.pricingengines.vanilla.MakeFdHestonVanillaEngine;
import jquant.processes.HestonProcess;
import jquant.quotes.SimpleQuote;
import jquant.termstructures.YieldTermStructure;
import jquant.termstructures.yield.FlatForward;
import jquant.time.Date;
import jquant.time.DayCounter;
import jquant.time.Frequency;
import jquant.time.Month;
import jquant.time.daycounters.Actual365Fixed;
import jquant.time.daycounters.ActualActual;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Finite-difference Heston vanilla engine checked against cached values.
 *
 * <p>Ports {@code testFdVanillaVsCached} from {@code test-suite/hestonmodel.cpp}
 * and {@code testFdmHestonAmerican} from {@code test-suite/fdheston.cpp}.
 */
public class FdHestonVanillaEngineTest {
    @Test
    public void testFdVanillaVsCached() {
        Date settlementDate = new Date(27, Month.DECEMBER, 2004);
        Settings.instance.evaluationDate().equal(settlementDate);

        DayCounter dayCounter =
                new ActualActual(ActualActual.Convention.ISDA);
        Date exerciseDate = new Date(28, Month.MARCH, 2005);

        StrikedTypePayoff payoff =
                new PlainVanillaPayoff(Option.Type.Put, 1.05);
        EuropeanExercise exercise = new EuropeanExercise(exerciseDate);

        Handle<YieldTermStructure> riskFreeTS =
                new Handle<>(flatRate(0.7, dayCounter), true);
        Handle<YieldTermStructure> dividendTS =
                new Handle<>(flatRate(0.4, dayCounter), true);

        Handle<Quote> s0 = new Handle<>(new SimpleQuote(1.05), true);

        EuropeanOption option = new EuropeanOption(payoff, exercise);

        HestonProcess process = new HestonProcess(
                riskFreeTS, dividendTS, s0, 0.3, 1.16, 0.2, 0.8, 0.8);

        option.setPricingEngine(
                new MakeFdHestonVanillaEngine(new HestonModel(process))
                        .withTGrid(100)
                        .withXGrid(200)
                        .withVGrid(100)
                        .build());

        double expected = 0.06325;
        double calculated = option.NPV();
        double error = Math.abs(calculated - expected);
        double tolerance = 1.0e-4;

        assertTrue(error <= tolerance,
                "failed to reproduce cached price with FD engine"
                        + "\n    calculated: " + calculated
                        + "\n    expected:   " + expected
                        + "\n    error:      " + error
                        + "\n    tolerance:  " + tolerance);
    }

    /**
     * Port of {@code testFdmHestonAmerican} from {@code test-suite/fdheston.cpp}.
     */
    @Test
    public void testFdmHestonAmerican() {
        Date settlementDate = new Date(28, Month.MARCH, 2004);
        Settings.instance.evaluationDate().equal(settlementDate);

        DayCounter dayCounter = new Actual365Fixed();

        Handle<Quote> s0 = new Handle<>(new SimpleQuote(100.0), true);
        Handle<YieldTermStructure> rTS =
                new Handle<>(flatRate(0.05, dayCounter), true);
        Handle<YieldTermStructure> qTS =
                new Handle<>(flatRate(0.0, dayCounter), true);

        HestonProcess hestonProcess = new HestonProcess(
                rTS, qTS, s0, 0.04, 2.5, 0.04, 0.66, -0.8);

        Date exerciseDate = new Date(28, Month.MARCH, 2005);
        AmericanExercise exercise = new AmericanExercise(exerciseDate);

        StrikedTypePayoff payoff =
                new PlainVanillaPayoff(Option.Type.Put, 100.0);

        EuropeanOption option = new EuropeanOption(payoff, exercise);
        option.setPricingEngine(new FdHestonVanillaEngine(
                new HestonModel(hestonProcess), 200, 100, 50));

        double tol = 0.01;
        double npvExpected = 5.66032;
        double deltaExpected = -0.30065;
        double gammaExpected = 0.02202;

        double npv = option.NPV();
        double delta = option.delta();
        double gamma = option.gamma();

        assertTrue(Math.abs(npv - npvExpected) <= tol,
                "Failed to reproduce expected npv"
                        + "\n    calculated: " + npv
                        + "\n    expected:   " + npvExpected
                        + "\n    tolerance:  " + tol);
        assertTrue(Math.abs(delta - deltaExpected) <= tol,
                "Failed to reproduce expected delta"
                        + "\n    calculated: " + delta
                        + "\n    expected:   " + deltaExpected
                        + "\n    tolerance:  " + tol);
        assertTrue(Math.abs(gamma - gammaExpected) <= tol,
                "Failed to reproduce expected gamma"
                        + "\n    calculated: " + gamma
                        + "\n    expected:   " + gammaExpected
                        + "\n    tolerance:  " + tol);
    }

    /**
     * Official {@code flatRate(Rate, DayCounter)}: the reference date of a
     * {@code FlatForward} built with the null date resolves to the evaluation
     * date.
     */
    private static YieldTermStructure flatRate(double forward, DayCounter dc) {
        Date today = Settings.instance.evaluationDate().Date();
        return new FlatForward(today,
                new Handle<>(new SimpleQuote(forward), true), dc,
                Compounding.Continuous, Frequency.ANNUAL);
    }
}
