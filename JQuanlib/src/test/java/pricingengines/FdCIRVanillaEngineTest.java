package pricingengines;

import jquant.Compounding;
import jquant.EuropeanExercise;
import jquant.EuropeanOption;
import jquant.Handle;
import jquant.Option;
import jquant.Quote;
import jquant.Settings;
import jquant.methods.finitedifferences.solvers.FdmSchemeDesc;
import jquant.pricingengines.PlainVanillaPayoff;
import jquant.pricingengines.StrikedTypePayoff;
import jquant.pricingengines.vanilla.MakeFdCIRVanillaEngine;
import jquant.processes.BlackScholesMertonProcess;
import jquant.processes.CoxIngersollRossProcess;
import jquant.quotes.SimpleQuote;
import jquant.termstructures.YieldTermStructure;
import jquant.termstructures.volatility.equityfx.BlackConstantVol;
import jquant.termstructures.volatility.equityfx.BlackVolTermStructure;
import jquant.termstructures.yield.FlatForward;
import jquant.time.Date;
import jquant.time.DayCounter;
import jquant.time.Frequency;
import jquant.time.calendars.NullCalendar;
import jquant.time.daycounters.Actual365Fixed;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Finite-difference CIR vanilla engine tested for convergence among schemes.
 *
 * <p>Ports {@code testFdmCIRConvergence} from {@code test-suite/fdcir.cpp}.
 */
public class FdCIRVanillaEngineTest {
    @Test
    public void testFdmCIRConvergence() {
        FdmSchemeDesc[] schemes = {
                FdmSchemeDesc.Hundsdorfer(),
                FdmSchemeDesc.ModifiedCraigSneyd(),
                FdmSchemeDesc.ModifiedHundsdorfer(),
                FdmSchemeDesc.CraigSneyd(),
                FdmSchemeDesc.TrBDF2(),
                FdmSchemeDesc.CrankNicolson(),
        };

        // set up dates
        Date today = Date.todaysDate();
        Settings.instance.evaluationDate().equal(today);

        // our options
        Option.Type type = Option.Type.Put;
        double underlying = 36;
        double strike = 40;
        double dividendYield = 0.00;
        double riskFreeRate = 0.06;
        double volatility = 0.20;
        Date maturity = today.add(365);
        DayCounter dayCounter = new Actual365Fixed();

        EuropeanExercise europeanExercise = new EuropeanExercise(maturity);

        Handle<Quote> underlyingH =
                new Handle<>(new SimpleQuote(underlying), true);

        Handle<YieldTermStructure> flatTermStructure =
                new Handle<>(flatRate(today, riskFreeRate, dayCounter), true);
        Handle<YieldTermStructure> flatDividendTS =
                new Handle<>(flatRate(today, dividendYield, dayCounter), true);
        Handle<BlackVolTermStructure> flatVolTS =
                new Handle<>(flatVol(today, volatility, dayCounter), true);
        StrikedTypePayoff payoff =
                new PlainVanillaPayoff(type, strike);
        BlackScholesMertonProcess bsmProcess =
                new BlackScholesMertonProcess(underlyingH, flatDividendTS,
                        flatTermStructure, flatVolTS);

        EuropeanOption europeanOption =
                new EuropeanOption(payoff, europeanExercise);

        double speed = 1.2188;
        double cirSigma = 0.02438;
        double level = 0.0183;
        double initialRate = 0.06;
        double rho = 0.00789;
        double lambda = -0.5726;
        double newSpeed = speed + (cirSigma * lambda); // 1.0792
        double newLevel = (level * speed)
                / (speed + (cirSigma * lambda));       // 0.0240

        CoxIngersollRossProcess cirProcess = new CoxIngersollRossProcess(
                newSpeed, cirSigma, initialRate, newLevel);

        double expected = 4.275;
        double tolerance = 0.0003;

        for (int i = 0; i < schemes.length; ++i) {
            europeanOption.setPricingEngine(
                    new MakeFdCIRVanillaEngine(cirProcess, bsmProcess, rho)
                            .withFdmSchemeDesc(schemes[i])
                            .build());
            double calculated = europeanOption.NPV();
            assertTrue(Math.abs(expected - calculated) <= tolerance,
                    "Failed to reproduce expected npv with scheme " + i
                            + "\n    calculated: " + calculated
                            + "\n    expected:   " + expected
                            + "\n    tolerance:  " + tolerance);
        }
    }

    /**
     * Official {@code flatRate(Date, Rate, DayCounter)}.
     */
    private static YieldTermStructure flatRate(Date today,
                                               double forward,
                                               DayCounter dc) {
        return new FlatForward(today,
                new Handle<>(new SimpleQuote(forward), true), dc,
                Compounding.Continuous, Frequency.ANNUAL);
    }

    /**
     * Official {@code flatVol(Date, Volatility, DayCounter)}.
     */
    private static BlackVolTermStructure flatVol(Date today,
                                                 double vol,
                                                 DayCounter dc) {
        return new BlackConstantVol(today, new NullCalendar(),
                new Handle<>(new SimpleQuote(vol), true), dc);
    }
}
