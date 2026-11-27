package pricingengines;

import jquant.AmericanExercise;
import jquant.Barrier;
import jquant.BarrierOption;
import jquant.Compounding;
import jquant.EuropeanExercise;
import jquant.EuropeanOption;
import jquant.Handle;
import jquant.Option;
import jquant.PricingEngine;
import jquant.Quote;
import jquant.SavedSettings;
import jquant.Settings;
import jquant.cashflows.Dividend;
import jquant.indexes.IndexManager;
import jquant.methods.finitedifferences.meshers.FdmHestonLocalVolatilityVarianceMesher;
import jquant.methods.finitedifferences.meshers.FdmHestonVarianceMesher;
import jquant.methods.finitedifferences.solvers.FdmHestonSolver;
import jquant.methods.finitedifferences.solvers.FdmSchemeDesc;
import jquant.models.equity.HestonModel;
import jquant.pricingengines.PlainVanillaPayoff;
import jquant.pricingengines.StrikedTypePayoff;
import jquant.pricingengines.barrier.AnalyticBarrierEngine;
import jquant.pricingengines.barrier.FdHestonBarrierEngine;
import jquant.pricingengines.vanilla.AnalyticEuropeanEngine;
import jquant.pricingengines.vanilla.AnalyticHestonEngine;
import jquant.pricingengines.vanilla.FdHestonVanillaEngine;
import jquant.processes.BlackScholesMertonProcess;
import jquant.processes.HestonProcess;
import jquant.quotes.SimpleQuote;
import jquant.termstructures.YieldTermStructure;
import jquant.termstructures.volatility.equityfx.BlackConstantVol;
import jquant.termstructures.volatility.equityfx.BlackVolTermStructure;
import jquant.termstructures.volatility.equityfx.LocalConstantVol;
import jquant.termstructures.volatility.equityfx.LocalVolTermStructure;
import jquant.termstructures.yield.FlatForward;
import jquant.time.BusinessDayConvention;
import jquant.time.Date;
import jquant.time.DayCounter;
import jquant.time.Frequency;
import jquant.time.Month;
import jquant.time.Period;
import jquant.time.TimeUnit;
import jquant.time.calendars.NullCalendar;
import jquant.time.daycounters.Actual360;
import jquant.time.daycounters.Actual365Fixed;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static jquant.math.MathUtils.QL_MAX_REAL;
import static jquant.math.MathUtils.QL_MIN_REAL;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Official QuantLib {@code test-suite/fdheston.cpp} finite-difference Heston
 * tests (suite {@code FdHestonTests}).
 *
 * <p>Official {@code BOOST_ERROR} records a failure but continues the test
 * case; the messages are accumulated and the test fails at the end.
 * Official {@code BOOST_FAIL} aborts immediately.
 *
 * <p>{@code testFdmHestonIntradayPricing} is intentionally deferred: it is
 * guarded by {@code QL_HIGH_RESOLUTION_DATE} in the official suite and is
 * disabled by default. JQuant's {@code Date} is a day serial, so there is no
 * day-only substitute that would preserve the official intraday timestamps.
 * The official case is kept verbatim in comments at the end of this class.
 */
public class FdHestonTest {

    private SavedSettings savedSettings;

    private final List<String> boostErrors = new ArrayList<>();

    @BeforeEach
    void saveSettings() {
        savedSettings = new SavedSettings();
    }

    @AfterEach
    void restoreSettings() {
        IndexManager.Instance.clearHistories();
        savedSettings.restore();
    }

    private void checkBoostErrors() {
        if (!boostErrors.isEmpty())
            fail(String.join("\n", boostErrors));
    }

    // Official test-local helper. Not part of the production library.
    private static class ParableLocalVolatility extends LocalVolTermStructure {
        private final Date referenceDate_;
        private final double s0_, alpha_;

        ParableLocalVolatility(Date referenceDate, double s0, double alpha,
                               DayCounter dayCounter) {
            super(referenceDate, new NullCalendar(),
                    BusinessDayConvention.FOLLOWING, dayCounter);
            referenceDate_ = referenceDate;
            s0_ = s0;
            alpha_ = alpha;
        }

        @Override
        public Date maxDate() {
            return Date.maxDate();
        }

        @Override
        public double minStrike() {
            return 0.0;
        }

        @Override
        public double maxStrike() {
            return QL_MAX_REAL;
        }

        @Override
        protected double localVolImpl(double t, double s) {
            return alpha_ * ((s0_ - s) * (s0_ - s) + 25.0);
        }
    }

    private static class NewBarrierOptionData {
        final Barrier.Type barrierType;
        final double barrier;
        final double rebate;
        final Option.Type type;
        final double strike;
        final double s;
        final double q;
        final double r;
        final double t;
        final double v;

        NewBarrierOptionData(Barrier.Type barrierType, double barrier,
                             double rebate, Option.Type type, double strike,
                             double s, double q, double r, double t, double v) {
            this.barrierType = barrierType;
            this.barrier = barrier;
            this.rebate = rebate;
            this.type = type;
            this.strike = strike;
            this.s = s;
            this.q = q;
            this.r = r;
            this.t = t;
            this.v = v;
        }
    }

    private static class HestonTestData {
        final double kappa;
        final double theta;
        final double sigma;
        final double rho;
        final double r;
        final double q;
        final double T;
        final double K;

        HestonTestData(double kappa, double theta, double sigma, double rho,
                       double r, double q, double T, double K) {
            this.kappa = kappa;
            this.theta = theta;
            this.sigma = sigma;
            this.rho = rho;
            this.r = r;
            this.q = q;
            this.T = T;
            this.K = K;
        }
    }

    private static class SchemeDesc {
        final FdmSchemeDesc desc;
        final String name;
        final boolean spurious;

        SchemeDesc(FdmSchemeDesc desc, String name, boolean spurious) {
            this.desc = desc;
            this.name = name;
            this.spurious = spurious;
        }
    }

    @Test
    public void testFdmHestonVarianceMesher() {
        System.out.println("Testing FDM Heston variance mesher...");

        final Date today = new Date(22, Month.FEBRUARY, 2018);
        final DayCounter dc = new Actual365Fixed();
        Settings.instance.evaluationDate().equal(today);

        final HestonProcess process = new HestonProcess(
                flatRate(0.02, dc),
                flatRate(0.02, dc),
                new Handle<>(new SimpleQuote(100.0), true),
                0.09, 1.0, 0.09, 0.2, -0.5);

        final FdmHestonVarianceMesher mesher =
                new FdmHestonVarianceMesher(5, process, 1.0);

        final List<Double> locations = mesher.locations();

        final double[] expected = {
                0.0, 6.652314e-02, 9.000000e-02, 1.095781e-01, 2.563610e-01
        };

        final double tol = 1e-6;
        for (int i = 0; i < locations.size(); ++i) {
            final double diff = Math.abs(expected[i] - locations.get(i));

            if (diff > tol) {
                boostErrors.add("Failed to reproduce Heston variance mesh"
                        + "\n    calculated: " + locations.get(i)
                        + "\n    expected:   " + expected[i]
                        + "\n    difference  " + diff
                        + "\n    tolerance:  " + tol);
            }
        }

        final LocalVolTermStructure lVol =
                new LocalConstantVol(today, 2.5, dc);

        final FdmHestonLocalVolatilityVarianceMesher constSlvMesher =
                new FdmHestonLocalVolatilityVarianceMesher(
                        5, process, lVol, 1.0);

        final double expectedVol = 2.5 * mesher.volaEstimate();
        final double calculatedVol = constSlvMesher.volaEstimate();

        final double diff = Math.abs(calculatedVol - expectedVol);
        if (diff > tol) {
            boostErrors.add("Failed to reproduce Heston local volatility "
                    + "variance estimate"
                    + "\n    calculated: " + calculatedVol
                    + "\n    expected:   " + expectedVol
                    + "\n    difference: " + diff
                    + "\n    tolerance:  " + tol);
        }

        final double alpha = 0.01;
        final LocalVolTermStructure leverageFct =
                new ParableLocalVolatility(today, 100.0, alpha, dc);

        final FdmHestonLocalVolatilityVarianceMesher slvMesher =
                new FdmHestonLocalVolatilityVarianceMesher(
                        5, process, leverageFct, 0.5, 1, 0.01, 1.0);

        final double initialVolEstimate =
                new FdmHestonVarianceMesher(5, process, 0.5, 1, 0.01, 1.0)
                        .volaEstimate();

        // const Real vEst = leverageFct->localVol(0, 100) * initialVolEstimate;
        // Mathematica solution
        //    N[Integrate[
        //      alpha*((100*Exp[vEst*x*Sqrt[0.5]] - 100)^2 + 25)*
        //       PDF[NormalDistribution[0, 1], x], {x ,
        //       InverseCDF[NormalDistribution[0, 1], 0.01],
        //       InverseCDF[NormalDistribution[0, 1], 0.99]}]]

        final double leverageAvg = 0.455881 / (1 - 0.02);

        final double volaEstExpected =
                0.5 * (leverageAvg + leverageFct.localVol(0.0, 100.0, false))
                        * initialVolEstimate;

        final double volaEstCalculated = slvMesher.volaEstimate();

        if (Math.abs(volaEstExpected - volaEstCalculated) > 0.001) {
            // Official quirk: the message prints the values from the
            // constant-local-vol check above, not volaEst*.
            boostErrors.add("Failed to reproduce Heston local volatility "
                    + "variance estimate"
                    + "\n    calculated: " + calculatedVol
                    + "\n    expected:   " + expectedVol
                    + "\n    difference: " + Math.abs(volaEstExpected - volaEstCalculated)
                    + "\n    tolerance:  " + tol);
        }

        checkBoostErrors();
    }

    @Test
    public void testFdmHestonBarrierVsBlackScholes() {
        System.out.println("Testing FDM with barrier option in Heston model...");

        final NewBarrierOptionData[] values = {
                /* The data below are from
                  "Option pricing formulas", E.G. Haug, McGraw-Hill 1998 pag. 72
                */
                //     barrierType, barrier, rebate,         type, strike,     s,    q,    r,    t,    v
                new NewBarrierOptionData(Barrier.Type.DownOut, 95.0, 3.0, Option.Type.Call, 90, 100.0, 0.04, 0.08, 0.50, 0.25),
                new NewBarrierOptionData(Barrier.Type.DownOut, 95.0, 3.0, Option.Type.Call, 100, 100.0, 0.00, 0.08, 1.00, 0.30),
                new NewBarrierOptionData(Barrier.Type.DownOut, 95.0, 3.0, Option.Type.Call, 110, 100.0, 0.04, 0.08, 0.50, 0.25),
                new NewBarrierOptionData(Barrier.Type.DownOut, 100.0, 3.0, Option.Type.Call, 90, 100.0, 0.00, 0.08, 0.25, 0.25),
                new NewBarrierOptionData(Barrier.Type.DownOut, 100.0, 3.0, Option.Type.Call, 100, 100.0, 0.04, 0.08, 0.50, 0.25),
                new NewBarrierOptionData(Barrier.Type.DownOut, 100.0, 3.0, Option.Type.Call, 110, 100.0, 0.04, 0.08, 0.50, 0.25),
                new NewBarrierOptionData(Barrier.Type.UpOut, 105.0, 3.0, Option.Type.Call, 90, 100.0, 0.04, 0.08, 0.50, 0.25),
                new NewBarrierOptionData(Barrier.Type.UpOut, 105.0, 3.0, Option.Type.Call, 100, 100.0, 0.04, 0.08, 0.50, 0.25),
                new NewBarrierOptionData(Barrier.Type.UpOut, 105.0, 3.0, Option.Type.Call, 110, 100.0, 0.04, 0.08, 0.50, 0.25),

                new NewBarrierOptionData(Barrier.Type.DownIn, 95.0, 3.0, Option.Type.Call, 90, 100.0, 0.04, 0.08, 0.50, 0.25),
                new NewBarrierOptionData(Barrier.Type.DownIn, 95.0, 3.0, Option.Type.Call, 100, 100.0, 0.04, 0.08, 0.50, 0.25),
                new NewBarrierOptionData(Barrier.Type.DownIn, 95.0, 3.0, Option.Type.Call, 110, 100.0, 0.04, 0.08, 0.50, 0.25),
                new NewBarrierOptionData(Barrier.Type.DownIn, 100.0, 3.0, Option.Type.Call, 90, 100.0, 0.00, 0.08, 0.25, 0.25),
                new NewBarrierOptionData(Barrier.Type.DownIn, 100.0, 3.0, Option.Type.Call, 100, 100.0, 0.04, 0.08, 0.50, 0.25),
                new NewBarrierOptionData(Barrier.Type.DownIn, 100.0, 3.0, Option.Type.Call, 110, 100.0, 0.04, 0.08, 0.50, 0.25),
                new NewBarrierOptionData(Barrier.Type.UpIn, 105.0, 3.0, Option.Type.Call, 90, 100.0, 0.04, 0.08, 0.50, 0.25),
                new NewBarrierOptionData(Barrier.Type.UpIn, 105.0, 3.0, Option.Type.Call, 100, 100.0, 0.00, 0.08, 0.40, 0.25),
                new NewBarrierOptionData(Barrier.Type.UpIn, 105.0, 3.0, Option.Type.Call, 110, 100.0, 0.04, 0.08, 0.50, 0.15),

                new NewBarrierOptionData(Barrier.Type.DownOut, 95.0, 3.0, Option.Type.Call, 90, 100.0, 0.04, 0.08, 0.50, 0.30),
                new NewBarrierOptionData(Barrier.Type.DownOut, 95.0, 3.0, Option.Type.Call, 100, 100.0, 0.00, 0.08, 0.40, 0.35),
                new NewBarrierOptionData(Barrier.Type.DownOut, 95.0, 3.0, Option.Type.Call, 110, 100.0, 0.04, 0.08, 0.50, 0.30),
                new NewBarrierOptionData(Barrier.Type.DownOut, 100.0, 3.0, Option.Type.Call, 90, 100.0, 0.04, 0.08, 0.50, 0.15),
                new NewBarrierOptionData(Barrier.Type.DownOut, 100.0, 3.0, Option.Type.Call, 100, 100.0, 0.04, 0.08, 0.50, 0.30),
                new NewBarrierOptionData(Barrier.Type.DownOut, 100.0, 3.0, Option.Type.Call, 110, 100.0, 0.00, 0.00, 1.00, 0.20),
                new NewBarrierOptionData(Barrier.Type.UpOut, 105.0, 3.0, Option.Type.Call, 90, 100.0, 0.04, 0.08, 0.50, 0.30),
                new NewBarrierOptionData(Barrier.Type.UpOut, 105.0, 3.0, Option.Type.Call, 100, 100.0, 0.04, 0.08, 0.50, 0.30),
                new NewBarrierOptionData(Barrier.Type.UpOut, 105.0, 3.0, Option.Type.Call, 110, 100.0, 0.04, 0.08, 0.50, 0.30),

                new NewBarrierOptionData(Barrier.Type.DownIn, 95.0, 3.0, Option.Type.Call, 90, 100.0, 0.04, 0.08, 0.50, 0.30),
                new NewBarrierOptionData(Barrier.Type.DownIn, 95.0, 3.0, Option.Type.Call, 100, 100.0, 0.04, 0.08, 0.50, 0.30),
                new NewBarrierOptionData(Barrier.Type.DownIn, 95.0, 3.0, Option.Type.Call, 110, 100.0, 0.00, 0.08, 1.00, 0.30),
                new NewBarrierOptionData(Barrier.Type.DownIn, 100.0, 3.0, Option.Type.Call, 90, 100.0, 0.04, 0.08, 0.50, 0.30),
                new NewBarrierOptionData(Barrier.Type.DownIn, 100.0, 3.0, Option.Type.Call, 100, 100.0, 0.04, 0.08, 0.50, 0.30),
                new NewBarrierOptionData(Barrier.Type.DownIn, 100.0, 3.0, Option.Type.Call, 110, 100.0, 0.04, 0.08, 0.50, 0.30),
                new NewBarrierOptionData(Barrier.Type.UpIn, 105.0, 3.0, Option.Type.Call, 90, 100.0, 0.04, 0.08, 0.50, 0.30),
                new NewBarrierOptionData(Barrier.Type.UpIn, 105.0, 3.0, Option.Type.Call, 100, 100.0, 0.04, 0.08, 0.50, 0.30),
                new NewBarrierOptionData(Barrier.Type.UpIn, 105.0, 3.0, Option.Type.Call, 110, 100.0, 0.04, 0.08, 0.50, 0.30),

                new NewBarrierOptionData(Barrier.Type.DownOut, 95.0, 3.0, Option.Type.Put, 90, 100.0, 0.04, 0.08, 0.50, 0.25),
                new NewBarrierOptionData(Barrier.Type.DownOut, 95.0, 3.0, Option.Type.Put, 100, 100.0, 0.04, 0.08, 0.50, 0.25),
                new NewBarrierOptionData(Barrier.Type.DownOut, 95.0, 3.0, Option.Type.Put, 110, 100.0, 0.04, 0.08, 0.50, 0.25),
                new NewBarrierOptionData(Barrier.Type.DownOut, 100.0, 3.0, Option.Type.Put, 90, 100.0, 0.04, 0.08, 0.50, 0.25),
                new NewBarrierOptionData(Barrier.Type.DownOut, 100.0, 3.0, Option.Type.Put, 100, 100.0, 0.04, 0.08, 0.50, 0.25),
                new NewBarrierOptionData(Barrier.Type.DownOut, 100.0, 3.0, Option.Type.Put, 110, 100.0, 0.04, 0.08, 0.50, 0.25),
                new NewBarrierOptionData(Barrier.Type.UpOut, 105.0, 3.0, Option.Type.Put, 90, 100.0, 0.04, 0.08, 0.50, 0.25),
                new NewBarrierOptionData(Barrier.Type.UpOut, 105.0, 3.0, Option.Type.Put, 100, 100.0, 0.04, 0.08, 0.50, 0.25),
                new NewBarrierOptionData(Barrier.Type.UpOut, 105.0, 3.0, Option.Type.Put, 110, 100.0, 0.04, 0.08, 0.50, 0.25),

                new NewBarrierOptionData(Barrier.Type.DownIn, 95.0, 3.0, Option.Type.Put, 90, 100.0, 0.04, 0.08, 0.50, 0.25),
                new NewBarrierOptionData(Barrier.Type.DownIn, 95.0, 3.0, Option.Type.Put, 100, 100.0, 0.04, 0.08, 0.50, 0.25),
                new NewBarrierOptionData(Barrier.Type.DownIn, 95.0, 3.0, Option.Type.Put, 110, 100.0, 0.04, 0.08, 0.50, 0.25),
                new NewBarrierOptionData(Barrier.Type.DownIn, 100.0, 3.0, Option.Type.Put, 90, 100.0, 0.04, 0.08, 0.50, 0.25),
                new NewBarrierOptionData(Barrier.Type.DownIn, 100.0, 3.0, Option.Type.Put, 100, 100.0, 0.04, 0.08, 0.50, 0.25),
                new NewBarrierOptionData(Barrier.Type.DownIn, 100.0, 3.0, Option.Type.Put, 110, 100.0, 0.04, 0.08, 0.50, 0.25),
                new NewBarrierOptionData(Barrier.Type.UpIn, 105.0, 3.0, Option.Type.Put, 90, 100.0, 0.04, 0.08, 0.50, 0.25),
                new NewBarrierOptionData(Barrier.Type.UpIn, 105.0, 3.0, Option.Type.Put, 100, 100.0, 0.04, 0.08, 0.50, 0.25),
                new NewBarrierOptionData(Barrier.Type.UpIn, 105.0, 3.0, Option.Type.Put, 110, 100.0, 0.00, 0.04, 1.00, 0.15),

                new NewBarrierOptionData(Barrier.Type.DownOut, 95.0, 3.0, Option.Type.Put, 90, 100.0, 0.04, 0.08, 0.50, 0.30),
                new NewBarrierOptionData(Barrier.Type.DownOut, 95.0, 3.0, Option.Type.Put, 100, 100.0, 0.04, 0.08, 0.50, 0.30),
                new NewBarrierOptionData(Barrier.Type.DownOut, 95.0, 3.0, Option.Type.Put, 110, 100.0, 0.04, 0.08, 0.50, 0.30),
                new NewBarrierOptionData(Barrier.Type.DownOut, 100.0, 3.0, Option.Type.Put, 90, 100.0, 0.04, 0.08, 0.50, 0.30),
                new NewBarrierOptionData(Barrier.Type.DownOut, 100.0, 3.0, Option.Type.Put, 100, 100.0, 0.04, 0.08, 0.50, 0.30),
                new NewBarrierOptionData(Barrier.Type.DownOut, 100.0, 3.0, Option.Type.Put, 110, 100.0, 0.04, 0.08, 0.50, 0.30),
                new NewBarrierOptionData(Barrier.Type.UpOut, 105.0, 3.0, Option.Type.Put, 90, 100.0, 0.04, 0.08, 0.50, 0.30),
                new NewBarrierOptionData(Barrier.Type.UpOut, 105.0, 3.0, Option.Type.Put, 100, 100.0, 0.04, 0.08, 0.50, 0.30),
                new NewBarrierOptionData(Barrier.Type.UpOut, 105.0, 3.0, Option.Type.Put, 110, 100.0, 0.04, 0.08, 0.50, 0.30),

                new NewBarrierOptionData(Barrier.Type.DownIn, 95.0, 3.0, Option.Type.Put, 90, 100.0, 0.04, 0.08, 0.50, 0.30),
                new NewBarrierOptionData(Barrier.Type.DownIn, 95.0, 3.0, Option.Type.Put, 100, 100.0, 0.04, 0.08, 0.50, 0.30),
                new NewBarrierOptionData(Barrier.Type.DownIn, 95.0, 3.0, Option.Type.Put, 110, 100.0, 0.04, 0.08, 0.50, 0.30),
                new NewBarrierOptionData(Barrier.Type.DownIn, 100.0, 3.0, Option.Type.Put, 90, 100.0, 0.04, 0.08, 0.50, 0.30),
                new NewBarrierOptionData(Barrier.Type.DownIn, 100.0, 3.0, Option.Type.Put, 100, 100.0, 0.04, 0.08, 0.50, 0.30),
                new NewBarrierOptionData(Barrier.Type.DownIn, 100.0, 3.0, Option.Type.Put, 110, 100.0, 0.04, 0.08, 1.00, 0.15),
                new NewBarrierOptionData(Barrier.Type.UpIn, 105.0, 3.0, Option.Type.Put, 90, 100.0, 0.04, 0.08, 0.50, 0.30),
                new NewBarrierOptionData(Barrier.Type.UpIn, 105.0, 3.0, Option.Type.Put, 100, 100.0, 0.04, 0.08, 0.50, 0.30),
                new NewBarrierOptionData(Barrier.Type.UpIn, 105.0, 3.0, Option.Type.Put, 110, 100.0, 0.04, 0.08, 0.50, 0.30)
        };

        final DayCounter dc = new Actual365Fixed();
        final Date todaysDate = new Date(28, Month.MARCH, 2004);
        Settings.instance.evaluationDate().equal(todaysDate);

        final SimpleQuote spotQuote = new SimpleQuote(0.0);
        final Handle<Quote> spot = new Handle<>(spotQuote, true);
        final SimpleQuote qRate = new SimpleQuote(0.0);
        final Handle<YieldTermStructure> qTS =
                flatRate(new Handle<>(qRate, true), dc);
        final SimpleQuote rRate = new SimpleQuote(0.0);
        final Handle<YieldTermStructure> rTS =
                flatRate(new Handle<>(rRate, true), dc);
        final SimpleQuote vol = new SimpleQuote(0.0);
        final Handle<BlackVolTermStructure> volTS =
                flatVol(new Handle<>(vol, true), dc);

        final BlackScholesMertonProcess bsProcess =
                new BlackScholesMertonProcess(spot, qTS, rTS, volTS);

        final PricingEngine analyticEngine =
                new AnalyticBarrierEngine(bsProcess);

        for (NewBarrierOptionData value : values) {
            Date exDate = todaysDate.add((int) Math.round(value.t * 365));
            EuropeanExercise exercise = new EuropeanExercise(exDate);

            spotQuote.setValue(value.s);
            qRate.setValue(value.q);
            rRate.setValue(value.r);
            vol.setValue(value.v);

            StrikedTypePayoff payoff =
                    new PlainVanillaPayoff(value.type, value.strike);

            BarrierOption barrierOption = new BarrierOption(
                    value.barrierType, value.barrier, value.rebate,
                    payoff, exercise);

            final double v0 = vol.value() * vol.value();
            final HestonProcess hestonProcess = new HestonProcess(
                    rTS, qTS, spot, v0, 1.0, v0, 0.005, 0.0);

            barrierOption.setPricingEngine(new FdHestonBarrierEngine(
                    new HestonModel(hestonProcess), 200, 101, 3));

            final double calculatedHE = barrierOption.NPV();

            barrierOption.setPricingEngine(analyticEngine);
            final double expected = barrierOption.NPV();

            final double tol = 0.0025;
            if (Math.abs(calculatedHE - expected) / expected > tol) {
                boostErrors.add("Failed to reproduce expected Heston npv"
                        + "\n    calculated: " + calculatedHE
                        + "\n    expected:   " + expected
                        + "\n    tolerance:  " + tol);
            }
        }

        checkBoostErrors();
    }

    @Test
    public void testFdmHestonBarrier() {
        System.out.println("Testing FDM with barrier option for Heston model vs "
                + "Black-Scholes model...");

        Settings.instance.evaluationDate()
                .equal(new Date(28, Month.MARCH, 2004));

        final Handle<Quote> s0 =
                new Handle<>(new SimpleQuote(100.0), true);

        final Handle<YieldTermStructure> rTS =
                flatRate(0.05, new Actual365Fixed());
        final Handle<YieldTermStructure> qTS =
                flatRate(0.0, new Actual365Fixed());

        final HestonProcess hestonProcess = new HestonProcess(
                rTS, qTS, s0, 0.04, 2.5, 0.04, 0.66, -0.8);

        final Date exerciseDate = new Date(28, Month.MARCH, 2005);

        final EuropeanExercise exercise = new EuropeanExercise(exerciseDate);

        final StrikedTypePayoff payoff =
                new PlainVanillaPayoff(Option.Type.Call, 100);

        final BarrierOption barrierOption = new BarrierOption(
                Barrier.Type.UpOut, 135, 0.0, payoff, exercise);

        barrierOption.setPricingEngine(new FdHestonBarrierEngine(
                new HestonModel(hestonProcess), 50, 400, 100));

        final double tol = 0.01;
        final double npvExpected = 9.1530;
        final double deltaExpected = 0.5218;
        final double gammaExpected = -0.0354;

        if (Math.abs(barrierOption.NPV() - npvExpected) > tol) {
            boostErrors.add("Failed to reproduce expected npv"
                    + "\n    calculated: " + barrierOption.NPV()
                    + "\n    expected:   " + npvExpected
                    + "\n    tolerance:  " + tol);
        }
        if (Math.abs(barrierOption.delta() - deltaExpected) > tol) {
            boostErrors.add("Failed to reproduce expected delta"
                    + "\n    calculated: " + barrierOption.delta()
                    + "\n    expected:   " + deltaExpected
                    + "\n    tolerance:  " + tol);
        }
        if (Math.abs(barrierOption.gamma() - gammaExpected) > tol) {
            boostErrors.add("Failed to reproduce expected gamma"
                    + "\n    calculated: " + barrierOption.gamma()
                    + "\n    expected:   " + gammaExpected
                    + "\n    tolerance:  " + tol);
        }

        checkBoostErrors();
    }

    @Test
    public void testFdmHestonAmerican() {
        System.out.println("Testing FDM with American option in Heston model...");

        Settings.instance.evaluationDate()
                .equal(new Date(28, Month.MARCH, 2004));

        final Handle<Quote> s0 =
                new Handle<>(new SimpleQuote(100.0), true);

        final Handle<YieldTermStructure> rTS =
                flatRate(0.05, new Actual365Fixed());
        final Handle<YieldTermStructure> qTS =
                flatRate(0.0, new Actual365Fixed());

        final HestonProcess hestonProcess = new HestonProcess(
                rTS, qTS, s0, 0.04, 2.5, 0.04, 0.66, -0.8);

        final Date exerciseDate = new Date(28, Month.MARCH, 2005);

        final AmericanExercise exercise = new AmericanExercise(exerciseDate);

        final StrikedTypePayoff payoff =
                new PlainVanillaPayoff(Option.Type.Put, 100);

        final EuropeanOption option = new EuropeanOption(payoff, exercise);
        option.setPricingEngine(new FdHestonVanillaEngine(
                new HestonModel(hestonProcess), 200, 100, 50));

        final double tol = 0.01;
        final double npvExpected = 5.66032;
        final double deltaExpected = -0.30065;
        final double gammaExpected = 0.02202;

        if (Math.abs(option.NPV() - npvExpected) > tol) {
            boostErrors.add("Failed to reproduce expected npv"
                    + "\n    calculated: " + option.NPV()
                    + "\n    expected:   " + npvExpected
                    + "\n    tolerance:  " + tol);
        }
        if (Math.abs(option.delta() - deltaExpected) > tol) {
            boostErrors.add("Failed to reproduce expected delta"
                    + "\n    calculated: " + option.delta()
                    + "\n    expected:   " + deltaExpected
                    + "\n    tolerance:  " + tol);
        }
        if (Math.abs(option.gamma() - gammaExpected) > tol) {
            boostErrors.add("Failed to reproduce expected gamma"
                    + "\n    calculated: " + option.gamma()
                    + "\n    expected:   " + gammaExpected
                    + "\n    tolerance:  " + tol);
        }

        checkBoostErrors();
    }

    @Test
    public void testFdmHestonIkonenToivanen() {
        System.out.println("Testing FDM Heston for Ikonen and Toivanen tests...");

        /* check prices of american puts as given in:
           From Efficient numerical methods for pricing American options under
           stochastic volatility, Samuli Ikonen, Jari Toivanen,
           http://users.jyu.fi/~tene/papers/reportB12-05.pdf
        */
        Settings.instance.evaluationDate()
                .equal(new Date(28, Month.MARCH, 2004));

        final Handle<YieldTermStructure> rTS =
                flatRate(0.10, new Actual360());
        final Handle<YieldTermStructure> qTS =
                flatRate(0.0, new Actual360());

        final Date exerciseDate = new Date(26, Month.JUNE, 2004);

        final AmericanExercise exercise = new AmericanExercise(exerciseDate);

        final StrikedTypePayoff payoff =
                new PlainVanillaPayoff(Option.Type.Put, 10);

        final EuropeanOption option = new EuropeanOption(payoff, exercise);

        final double[] strikes = {8, 9, 10, 11, 12};
        final double[] expected = {2.00000, 1.10763, 0.520038, 0.213681, 0.082046};
        final double tol = 0.001;

        for (int i = 0; i < strikes.length; ++i) {
            final Handle<Quote> s0 =
                    new Handle<>(new SimpleQuote(strikes[i]), true);
            final HestonProcess hestonProcess = new HestonProcess(
                    rTS, qTS, s0, 0.0625, 5, 0.16, 0.9, 0.1);

            option.setPricingEngine(new FdHestonVanillaEngine(
                    new HestonModel(hestonProcess), 100, 400, 50));

            final double calculated = option.NPV();
            if (Math.abs(calculated - expected[i]) > tol) {
                boostErrors.add("Failed to reproduce expected npv"
                        + "\n    strike:     " + strikes[i]
                        + "\n    calculated: " + calculated
                        + "\n    expected:   " + expected[i]
                        + "\n    tolerance:  " + tol);
            }
        }

        checkBoostErrors();
    }

    @Test
    public void testFdmHestonBlackScholes() {
        System.out.println("Testing FDM Heston with Black Scholes model...");

        Settings.instance.evaluationDate()
                .equal(new Date(28, Month.MARCH, 2004));

        final Date exerciseDate = new Date(26, Month.JUNE, 2004);

        final Handle<YieldTermStructure> rTS =
                flatRate(0.10, new Actual360());
        final Handle<YieldTermStructure> qTS =
                flatRate(0.0, new Actual360());
        final Handle<BlackVolTermStructure> volTS = new Handle<>(
                flatVol(rTS.currentLink().referenceDate(), 0.25,
                        rTS.currentLink().dayCounter()), true);

        final EuropeanExercise exercise = new EuropeanExercise(exerciseDate);

        final StrikedTypePayoff payoff =
                new PlainVanillaPayoff(Option.Type.Put, 10);

        final EuropeanOption option = new EuropeanOption(payoff, exercise);

        final double[] strikes = {8, 9, 10, 11, 12};
        final double tol = 0.0001;

        for (double strike : strikes) {
            final Handle<Quote> s0 =
                    new Handle<>(new SimpleQuote(strike), true);

            // Official GeneralizedBlackScholesProcess(s0, qTS, rTS, volTS);
            // the Java four-argument convenience constructor lives on the
            // BlackScholesMertonProcess subclass (Euler discretization).
            final BlackScholesMertonProcess bsProcess =
                    new BlackScholesMertonProcess(s0, qTS, rTS, volTS);

            option.setPricingEngine(new AnalyticEuropeanEngine(bsProcess));

            final double expected = option.NPV();

            final HestonProcess hestonProcess = new HestonProcess(
                    rTS, qTS, s0, 0.0625, 1, 0.0625, 0.0001, 0.0);

            // Hundsdorfer scheme
            option.setPricingEngine(new FdHestonVanillaEngine(
                    new HestonModel(hestonProcess), 100, 400, 3));

            double calculated = option.NPV();
            if (Math.abs(calculated - expected) > tol) {
                boostErrors.add("Failed to reproduce expected npv"
                        + "\n    strike:     " + strike
                        + "\n    calculated: " + calculated
                        + "\n    expected:   " + expected
                        + "\n    tolerance:  " + tol);
            }

            // Explicit scheme
            option.setPricingEngine(new FdHestonVanillaEngine(
                    new HestonModel(hestonProcess),
                    4000, 400, 3, 0,
                    FdmSchemeDesc.ExplicitEuler(), null, 1.0));

            calculated = option.NPV();
            if (Math.abs(calculated - expected) > tol) {
                boostErrors.add("Failed to reproduce expected npv"
                        + "\n    strike:     " + strike
                        + "\n    calculated: " + calculated
                        + "\n    expected:   " + expected
                        + "\n    tolerance:  " + tol);
            }
        }

        checkBoostErrors();
    }

    @Test
    public void testFdmHestonEuropeanWithDividends() {
        System.out.println("Testing FDM with European option with dividends "
                + "in Heston model...");

        Settings.instance.evaluationDate()
                .equal(new Date(28, Month.MARCH, 2004));

        final Handle<Quote> s0 =
                new Handle<>(new SimpleQuote(100.0), true);

        final Handle<YieldTermStructure> rTS =
                flatRate(0.05, new Actual365Fixed());
        final Handle<YieldTermStructure> qTS =
                flatRate(0.0, new Actual365Fixed());

        final HestonProcess hestonProcess = new HestonProcess(
                rTS, qTS, s0, 0.04, 2.5, 0.04, 0.66, -0.8);

        final Date exerciseDate = new Date(28, Month.MARCH, 2005);

        // Official comment/message says "European", but the official case
        // uses an AmericanExercise.
        final AmericanExercise exercise = new AmericanExercise(exerciseDate);
        final StrikedTypePayoff payoff =
                new PlainVanillaPayoff(Option.Type.Put, 100);

        final double[] dividends = {5};
        final List<Date> dividendDates =
                List.of(new Date(28, Month.SEPTEMBER, 2004));

        final double tol = 0.01;
        final double gammaTol = 0.001;
        final double npvExpected = 7.38216;
        final double deltaExpected = -0.397902;
        final double gammaExpected = 0.027747;

        final EuropeanOption option = new EuropeanOption(payoff, exercise);
        final FdHestonVanillaEngine engine = new FdHestonVanillaEngine(
                new HestonModel(hestonProcess),
                Dividend.dividendVector(dividendDates, dividends),
                50, 100, 50, 0, FdmSchemeDesc.Hundsdorfer(), null, 1.0);
        option.setPricingEngine(engine);

        if (Math.abs(option.NPV() - npvExpected) > tol) {
            boostErrors.add("Failed to reproduce expected npv"
                    + "\n    calculated: " + option.NPV()
                    + "\n    expected:   " + npvExpected
                    + "\n    tolerance:  " + tol);
        }
        if (Math.abs(option.delta() - deltaExpected) > tol) {
            boostErrors.add("Failed to reproduce expected delta"
                    + "\n    calculated: " + option.delta()
                    + "\n    expected:   " + deltaExpected
                    + "\n    tolerance:  " + tol);
        }
        if (Math.abs(option.gamma() - gammaExpected) > gammaTol) {
            // Official quirk: the message prints tol, not gammaTol.
            boostErrors.add("Failed to reproduce expected gamma"
                    + "\n    calculated: " + option.gamma()
                    + "\n    expected:   " + gammaExpected
                    + "\n    tolerance:  " + tol);
        }

        checkBoostErrors();
    }

    @Test
    public void testFdmHestonConvergence() {
        /* convergence tests based on
           ADI finite difference schemes for option pricing in the
           Heston model with correlation, K.J. in t'Hout and S. Foulon
        */

        System.out.println("Testing FDM Heston convergence...");

        final HestonTestData[] values = {
                new HestonTestData(1.5, 0.04, 0.3, -0.9, 0.025, 0.0, 1.0, 100),
                new HestonTestData(3.0, 0.12, 0.04, 0.6, 0.01, 0.04, 1.0, 100),
                new HestonTestData(0.6067, 0.0707, 0.2928, -0.7571, 0.03, 0.0, 3.0, 100),
                new HestonTestData(2.5, 0.06, 0.5, -0.1, 0.0507, 0.0469, 0.25, 100)
        };

        final FdmSchemeDesc[] schemes = {
                FdmSchemeDesc.Hundsdorfer(),
                FdmSchemeDesc.ModifiedCraigSneyd(),
                FdmSchemeDesc.ModifiedHundsdorfer(),
                FdmSchemeDesc.CraigSneyd(),
                FdmSchemeDesc.TrBDF2(),
                FdmSchemeDesc.CrankNicolson(),
        };

        final int[] tn = {60};
        final double[] v0 = {0.04};

        final Date todaysDate = new Date(28, Month.MARCH, 2004);
        Settings.instance.evaluationDate().equal(todaysDate);

        final Handle<Quote> s0 =
                new Handle<>(new SimpleQuote(75.0), true);

        for (FdmSchemeDesc scheme : schemes) {
            for (HestonTestData value : values) {
                for (int j : tn) {
                    for (double k : v0) {
                        final Handle<YieldTermStructure> rTS =
                                flatRate(value.r, new Actual365Fixed());
                        final Handle<YieldTermStructure> qTS =
                                flatRate(value.q, new Actual365Fixed());

                        final HestonProcess hestonProcess = new HestonProcess(
                                rTS, qTS, s0, k,
                                value.kappa, value.theta,
                                value.sigma, value.rho);

                        final Date exerciseDate = todaysDate.add(
                                new Period((int) (value.T * 365),
                                        TimeUnit.DAYS));
                        final EuropeanExercise exercise =
                                new EuropeanExercise(exerciseDate);

                        final StrikedTypePayoff payoff =
                                new PlainVanillaPayoff(
                                        Option.Type.Call, value.K);

                        final EuropeanOption option =
                                new EuropeanOption(payoff, exercise);
                        option.setPricingEngine(new FdHestonVanillaEngine(
                                new HestonModel(hestonProcess),
                                j, 101, 51, 0, scheme, null, 1.0));

                        final double calculated = option.NPV();

                        final PricingEngine analyticEngine =
                                new AnalyticHestonEngine(
                                        new HestonModel(hestonProcess), 144);

                        option.setPricingEngine(analyticEngine);
                        final double expected = option.NPV();
                        if (Math.abs(expected - calculated) / expected > 0.02
                                && Math.abs(expected - calculated) > 0.002) {
                            // Official quirk: the message prints 0.01.
                            boostErrors.add("Failed to reproduce expected npv"
                                    + "\n    calculated: " + calculated
                                    + "\n    expected:   " + expected
                                    + "\n    tolerance:  " + 0.01);
                        }
                    }
                }
            }
        }

        checkBoostErrors();
    }

    @Test
    public void testMethodOfLinesAndCN() {
        System.out.println("Testing method of lines to solve Heston PDEs...");

        final DayCounter dc = new Actual365Fixed();
        final Date today = new Date(21, Month.FEBRUARY, 2018);

        Settings.instance.evaluationDate().equal(today);

        final Handle<Quote> spot =
                new Handle<>(new SimpleQuote(100.0), true);
        final Handle<YieldTermStructure> qTS = flatRate(today, 0.0, dc);
        final Handle<YieldTermStructure> rTS = flatRate(today, 0.0, dc);

        final double v0 = 0.09;
        final double kappa = 1.0;
        final double theta = v0;
        final double sigma = 0.4;
        final double rho = -0.75;

        final Date maturity = today.add(new Period(3, TimeUnit.MONTHS));

        final HestonModel model = new HestonModel(new HestonProcess(
                rTS, qTS, spot, v0, kappa, theta, sigma, rho));

        final int xGrid = 21;
        final int vGrid = 7;

        final PricingEngine fdmDefault = new FdHestonVanillaEngine(
                model, 10, xGrid, vGrid, 0,
                FdmSchemeDesc.Hundsdorfer(), null, 1.0);

        final PricingEngine fdmMol = new FdHestonVanillaEngine(
                model, 10, xGrid, vGrid, 0,
                FdmSchemeDesc.MethodOfLines(), null, 1.0);

        final PlainVanillaPayoff payoff =
                new PlainVanillaPayoff(Option.Type.Put, spot.currentLink().value());

        final EuropeanOption option = new EuropeanOption(
                payoff, new AmericanExercise(maturity));

        option.setPricingEngine(fdmMol);
        final double calculatedMoL = option.NPV();

        option.setPricingEngine(fdmDefault);
        final double expected = option.NPV();

        final double tol = 0.005;
        final double diffMoL = Math.abs(expected - calculatedMoL);

        if (diffMoL > tol) {
            fail("Failed to reproduce european option values with MOL"
                    + "\n    calculated: " + calculatedMoL
                    + "\n    expected:   " + expected
                    + "\n    difference: " + diffMoL
                    + "\n    tolerance:  " + tol);
        }

        final PricingEngine fdmCN = new FdHestonVanillaEngine(
                model, 10, xGrid, vGrid, 0,
                FdmSchemeDesc.CrankNicolson(), null, 1.0);
        option.setPricingEngine(fdmCN);

        final double calculatedCN = option.NPV();
        final double diffCN = Math.abs(expected - calculatedCN);

        if (diffCN > tol) {
            fail("Failed to reproduce european option values with Crank-Nicolson"
                    + "\n    calculated: " + calculatedCN
                    + "\n    expected:   " + expected
                    + "\n    difference: " + diffCN
                    + "\n    tolerance:  " + tol);
        }

        final BarrierOption barrierOption = new BarrierOption(
                Barrier.Type.DownOut, 85.0, 10.0,
                payoff, new EuropeanExercise(maturity));

        barrierOption.setPricingEngine(
                new FdHestonBarrierEngine(model, 100, 31, 11));

        final double expectedBarrier = barrierOption.NPV();

        barrierOption.setPricingEngine(
                new FdHestonBarrierEngine(model, 100, 31, 11, 0,
                        FdmSchemeDesc.MethodOfLines(), null, 1.0));

        final double calculatedBarrierMoL = barrierOption.NPV();

        final double barrierTol = 0.01;
        final double barrierDiffMoL =
                Math.abs(expectedBarrier - calculatedBarrierMoL);

        if (barrierDiffMoL > barrierTol) {
            fail("Failed to reproduce barrier option values with MOL"
                    + "\n    calculated: " + calculatedBarrierMoL
                    + "\n    expected:   " + expectedBarrier
                    + "\n    difference: " + barrierDiffMoL
                    + "\n    tolerance:  " + barrierTol);
        }

        barrierOption.setPricingEngine(
                new FdHestonBarrierEngine(model, 100, 31, 11, 0,
                        FdmSchemeDesc.CrankNicolson(), null, 1.0));

        final double calculatedBarrierCN = barrierOption.NPV();
        final double barrierDiffCN =
                Math.abs(expectedBarrier - calculatedBarrierCN);

        if (barrierDiffCN > barrierTol) {
            fail("Failed to reproduce barrier option values with Crank-Nicolson"
                    + "\n    calculated: " + calculatedBarrierCN
                    + "\n    expected:   " + expectedBarrier
                    + "\n    difference: " + barrierDiffCN
                    + "\n    tolerance:  " + barrierTol);
        }
    }

    @Test
    public void testSpuriousOscillations() {
        System.out.println("Testing for spurious oscillations when "
                + "solving the Heston PDEs...");

        final DayCounter dc = new Actual365Fixed();
        final Date today = new Date(7, Month.JUNE, 2018);

        Settings.instance.evaluationDate().equal(today);

        final Handle<Quote> spot =
                new Handle<>(new SimpleQuote(100.0), true);
        final Handle<YieldTermStructure> qTS = flatRate(today, 0.1, dc);
        final Handle<YieldTermStructure> rTS = flatRate(today, 0.0, dc);

        final double v0 = 0.005;
        final double kappa = 1.0;
        final double theta = 0.005;
        final double sigma = 0.4;
        final double rho = -0.75;

        final Date maturity = today.add(new Period(1, TimeUnit.YEARS));

        final HestonProcess process = new HestonProcess(
                rTS, qTS, spot, v0, kappa, theta, sigma, rho);

        final HestonModel model = new HestonModel(process);

        final FdHestonVanillaEngine hestonEngine =
                new FdHestonVanillaEngine(
                        model, 6, 200, 13, 0,
                        FdmSchemeDesc.TrBDF2(), null, 1.0);

        final EuropeanOption option = new EuropeanOption(
                new PlainVanillaPayoff(Option.Type.Call, spot.currentLink().value()),
                new EuropeanExercise(maturity));

        option.setupArguments(hestonEngine.getArguments());

        final SchemeDesc[] descs = {
                new SchemeDesc(FdmSchemeDesc.CraigSneyd(), "Craig-Sneyd", true),
                new SchemeDesc(FdmSchemeDesc.Hundsdorfer(), "Hundsdorfer", true),
                new SchemeDesc(FdmSchemeDesc.ModifiedHundsdorfer(), "Mod. Hundsdorfer", true),
                new SchemeDesc(FdmSchemeDesc.Douglas(), "Douglas", true),
                new SchemeDesc(FdmSchemeDesc.CrankNicolson(), "Crank-Nicolson", true),
                new SchemeDesc(FdmSchemeDesc.ImplicitEuler(), "Implicit", false),
                new SchemeDesc(FdmSchemeDesc.TrBDF2(), "TR-BDF2", false)
        };

        for (SchemeDesc d : descs) {
            final FdmHestonSolver solver = new FdmHestonSolver(
                    process, hestonEngine.getSolverDesc(1.0),
                    d.desc, null, null, 1.0);

            final List<Double> gammas = new ArrayList<>();
            for (double x = 99; x < 101.001; x += 0.1) {
                gammas.add(solver.gammaAt(x, v0));
            }

            double maximum = QL_MIN_REAL;
            for (int i = 1; i < gammas.size(); ++i) {
                final double diff = Math.abs(gammas.get(i) - gammas.get(i - 1));
                if (diff > maximum)
                    maximum = diff;
            }

            final double tol = 0.01;
            final boolean hasSpuriousOscillations = maximum > tol;

            if (hasSpuriousOscillations != d.spurious) {
                boostErrors.add("unable to reproduce spurious oscillation behaviour "
                        + "\n   scheme name          : " + d.name
                        + "\n   oscillations observed: "
                        + (hasSpuriousOscillations ? 1 : 0)
                        + "\n   oscillations expected: "
                        + (d.spurious ? 1 : 0));
            }
        }

        checkBoostErrors();
    }

    @Test
    public void testAmericanCallPutParity() {
        System.out.println("Testing call/put parity for American options "
                + "under the Heston model...");

        // A. Battauz, M. De Donno,m A. Sbuelz:
        // The put-call symmetry for American options in
        // the Heston stochastic volatility model

        final DayCounter dc = new Actual365Fixed();
        final Date today = new Date(15, Month.APRIL, 2022);

        Settings.instance.evaluationDate().equal(today);

        final OptionSpec[] testCaseSpecs = {
                new OptionSpec(100.0, 90.0, 365, 0.02, 0.15,
                        0.25, 1.0, 0.09, 0.5, -0.75),
                new OptionSpec(100.0, 90.0, 365, 0.05, 0.20,
                        0.5, 1.0, 0.05, 0.75, -0.9)
        };

        final int xGrid = 200;
        final int vGrid = 25;
        final int timeStepsPerYear = 50;

        for (OptionSpec testCaseSpec : testCaseSpecs) {
            final Date maturityDate = today.add(
                    new Period(testCaseSpec.maturityInDays, TimeUnit.DAYS));
            final double maturityTime =
                    dc.yearFraction(today, maturityDate);
            final int tGrid = (int) (maturityTime * timeStepsPerYear);

            final AmericanExercise exercise =
                    new AmericanExercise(today, maturityDate);

            final EuropeanOption callOption = new EuropeanOption(
                    new PlainVanillaPayoff(Option.Type.Call,
                            testCaseSpec.strike),
                    exercise);

            callOption.setPricingEngine(new FdHestonVanillaEngine(
                    new HestonModel(buildStochProcess(testCaseSpec, dc)),
                    tGrid, xGrid, vGrid));

            final double callNpv = callOption.NPV();

            final OptionSpec putOptionSpec = new OptionSpec(
                    testCaseSpec.strike,
                    testCaseSpec.spot,
                    testCaseSpec.maturityInDays,
                    testCaseSpec.q,
                    testCaseSpec.r,
                    testCaseSpec.v0,
                    testCaseSpec.kappa - testCaseSpec.sig * testCaseSpec.rho,
                    testCaseSpec.kappa * testCaseSpec.theta
                            / (testCaseSpec.kappa
                            - testCaseSpec.sig * testCaseSpec.rho),
                    testCaseSpec.sig,
                    -testCaseSpec.rho);

            final EuropeanOption putOption = new EuropeanOption(
                    new PlainVanillaPayoff(Option.Type.Put,
                            putOptionSpec.strike),
                    exercise);

            putOption.setPricingEngine(new FdHestonVanillaEngine(
                    new HestonModel(buildStochProcess(putOptionSpec, dc)),
                    tGrid, xGrid, vGrid));

            final double putNpv = putOption.NPV();

            final double diff = Math.abs(putNpv - callNpv);
            final double tol = 0.025;

            if (diff > tol) {
                fail("failed to reproduce American call/put parity"
                        + "\n    Put NPV   : " + putNpv
                        + "\n    Call NPV  : " + callNpv
                        + "\n    difference: " + diff
                        + "\n    tolerance : " + tol);
            }
        }
    }

    private static class OptionSpec {
        final double spot;
        final double strike;
        final int maturityInDays;
        final double r, q;
        final double v0, kappa, theta, sig, rho;

        OptionSpec(double spot, double strike, int maturityInDays,
                   double r, double q, double v0, double kappa,
                   double theta, double sig, double rho) {
            this.spot = spot;
            this.strike = strike;
            this.maturityInDays = maturityInDays;
            this.r = r;
            this.q = q;
            this.v0 = v0;
            this.kappa = kappa;
            this.theta = theta;
            this.sig = sig;
            this.rho = rho;
        }
    }

    private static HestonProcess buildStochProcess(OptionSpec testCase,
                                                   DayCounter dc) {
        return new HestonProcess(
                flatRate(testCase.r, dc),
                flatRate(testCase.q, dc),
                new Handle<>(new SimpleQuote(testCase.spot), true),
                testCase.v0, testCase.kappa,
                testCase.theta, testCase.sig, testCase.rho);
    }

    /*
     * DEFERRED official case (test-suite/fdheston.cpp, guarded by
     * #ifdef QL_HIGH_RESOLUTION_DATE, default OFF):
     *
     * BOOST_AUTO_TEST_CASE(testFdmHestonIntradayPricing) {
     *
     *     BOOST_TEST_MESSAGE("Testing FDM Heston intraday pricing...");
     *
     *     const Option::Type type(Option::Put);
     *     const Real underlying = 36;
     *     const Real strike = underlying;
     *     const Spread dividendYield = 0.00;
     *     const Rate riskFreeRate = 0.06;
     *     const Real v0    = 0.2;
     *     const Real kappa = 1.0;
     *     const Real theta = v0;
     *     const Real sigma = 0.0065;
     *     const Real rho   = -0.75;
     *     const DayCounter dayCounter = Actual365Fixed();
     *
     *     const Date maturity(17, May, 2014, 17, 30, 0);
     *
     *     const ext::shared_ptr<Exercise> europeanExercise(
     *         new EuropeanExercise(maturity));
     *     const ext::shared_ptr<StrikedTypePayoff> payoff(
     *         new PlainVanillaPayoff(type, strike));
     *     VanillaOption option(payoff, europeanExercise);
     *
     *     const Handle<Quote> s0(
     *          ext::shared_ptr<Quote>(new SimpleQuote(underlying)));
     *     RelinkableHandle<BlackVolTermStructure> flatVolTS;
     *     RelinkableHandle<YieldTermStructure> flatTermStructure, flatDividendTS;
     *     const ext::shared_ptr<HestonProcess> process(
     *         new HestonProcess(flatTermStructure, flatDividendTS, s0,
     *               v0, kappa, theta, sigma, rho));
     *     const ext::shared_ptr<HestonModel> model(new HestonModel(process));
     *     const ext::shared_ptr<PricingEngine> fdm(
     *         new FdHestonVanillaEngine(model, 20, 100, 26, 0));
     *     option.setPricingEngine(fdm);
     *
     *     const Real gammaExpected[] = {
     *         1.46757, 1.54696, 1.6408, 1.75409, 1.89464,
     *         2.07548, 2.32046, 2.67944, 3.28164, 4.64096  };
     *
     *     for (Size i = 0; i < 10; ++i) {
     *         const Date now(17, May, 2014, 15, i*15, 0);
     *         Settings::instance().evaluationDate() = now;
     *
     *         flatTermStructure.linkTo(ext::shared_ptr<YieldTermStructure>(
     *             new FlatForward(now, riskFreeRate, dayCounter)));
     *         flatDividendTS.linkTo(ext::shared_ptr<YieldTermStructure>(
     *             new FlatForward(now, dividendYield, dayCounter)));
     *
     *         const Real gammaCalculated = option.gamma();
     *         if (std::fabs(gammaCalculated - gammaExpected[i]) > 1e-4) {
     *             BOOST_ERROR("unable to reproduce intraday gamma values at time "
     *                         << "\n   timestamp : " << io::iso_datetime(now)
     *                         << "\n   expiry    : " << io::iso_datetime(maturity)
     *                         << "\n   expected  : " << gammaExpected[i]
     *                         << "\n   calculated: "<<  gammaCalculated);
     *         }
     *     }
     * }
     *
     * JQuant's Date is a day serial, so the intraday timestamps
     * (17 May 2014 15:00 through 17:15, in 15-minute steps) and the
     * 17:30 expiry cannot be represented. No day-only substitution is
     * made and the official tolerance (1e-4) is not loosened.
     */

    /**
     * Official {@code flatRate(Rate, DayCounter)}: the reference date of a
     * {@code FlatForward} built with the null date resolves to the evaluation
     * date.
     */
    private static Handle<YieldTermStructure> flatRate(double forward,
                                                       DayCounter dc) {
        Date today = Settings.instance.evaluationDate().Date();
        return new Handle<>(new FlatForward(today,
                new Handle<>(new SimpleQuote(forward), true), dc,
                Compounding.Continuous, Frequency.ANNUAL), true);
    }

    /**
     * Official {@code flatRate(const Date&, Rate, const DayCounter&)}.
     */
    private static Handle<YieldTermStructure> flatRate(Date today,
                                                       double forward,
                                                       DayCounter dc) {
        return new Handle<>(new FlatForward(today,
                new Handle<>(new SimpleQuote(forward), true), dc,
                Compounding.Continuous, Frequency.ANNUAL), true);
    }

    /**
     * Official {@code flatRate(const Handle<Quote>&, const DayCounter&)}.
     */
    private static Handle<YieldTermStructure> flatRate(Handle<Quote> forward,
                                                       DayCounter dc) {
        return new Handle<>(new FlatForward(0, new NullCalendar(),
                forward, dc), true);
    }

    /**
     * Official {@code flatVol(const Date&, Volatility, const DayCounter&)}.
     */
    private static BlackVolTermStructure flatVol(Date today, double volatility,
                                                 DayCounter dc) {
        return new BlackConstantVol(today, new NullCalendar(),
                new Handle<>(new SimpleQuote(volatility), true), dc);
    }

    /**
     * Official {@code flatVol(const Handle<Quote>&, const DayCounter&)}.
     */
    private static Handle<BlackVolTermStructure> flatVol(Handle<Quote> vol,
                                                         DayCounter dc) {
        return new Handle<>(new BlackConstantVol(0, new NullCalendar(),
                vol, dc), true);
    }
}
