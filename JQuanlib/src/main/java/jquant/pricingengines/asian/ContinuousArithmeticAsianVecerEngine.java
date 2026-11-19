package jquant.pricingengines.asian;

import jquant.Average;
import jquant.Compounding;
import jquant.ContinuousAveragingAsianOption;
import jquant.EuropeanOption;
import jquant.Exercise;
import jquant.Handle;
import jquant.Option;
import jquant.Quote;
import jquant.Settings;
import jquant.math.Array;
import jquant.math.DownRounding;
import jquant.methods.finitedifferences.TridiagonalOperator;
import jquant.pricingengines.StrikedTypePayoff;
import jquant.pricingengines.vanilla.AnalyticEuropeanEngine;
import jquant.processes.GeneralizedBlackScholesProcess;
import jquant.time.Date;
import jquant.time.DayCounter;
import jquant.time.Frequency;
import jquant.time.TimeUtils;

import static jquant.math.CommonUtil.QL_REQUIRE;

/**
 * Vecer engine for continuously averaged arithmetic Asian options.
 *
 * <p>Ported from QuantLib
 * {@code ql/experimental/exoticoptions/continuousarithmeticasianvecerengine.{hpp,cpp}}.
 * See <http://www.stat.columbia.edu/~vecer/asian-vecer.pdf>.
 */
public class ContinuousArithmeticAsianVecerEngine
        extends ContinuousAveragingAsianOption.engine {
    private final GeneralizedBlackScholesProcess process_;
    private final Handle<Quote> currentAverage_;
    private final Date startDate_;
    private final double z_min_;
    private final double z_max_;
    private final int timeSteps_;
    private final int assetSteps_;

    public ContinuousArithmeticAsianVecerEngine(
            GeneralizedBlackScholesProcess process,
            Handle<Quote> currentAverage,
            Date startDate) {
        this(process, currentAverage, startDate, 100, 100, -1.0, 1.0);
    }

    public ContinuousArithmeticAsianVecerEngine(
            GeneralizedBlackScholesProcess process,
            Handle<Quote> currentAverage,
            Date startDate,
            int timeSteps,
            int assetSteps,
            double z_min,
            double z_max) {
        process_ = process;
        currentAverage_ = currentAverage;
        startDate_ = startDate;
        z_min_ = z_min;
        z_max_ = z_max;
        timeSteps_ = timeSteps;
        assetSteps_ = assetSteps;
        registerWith(process_);
        registerWith(currentAverage_.get());
    }

    @Override
    public void calculate() {
        double expectedAverage;

        QL_REQUIRE(arguments_.averageType == Average.Type.Arithmetic,
                "not an Arithmetic average option");
        QL_REQUIRE(arguments_.exercise.type() == Exercise.Type.European,
                "not an European Option");

        final DayCounter rfdc =
                process_.riskFreeRate().currentLink().dayCounter();
        final DayCounter divdc =
                process_.dividendYield().currentLink().dayCounter();
        final double S_0 = process_.stateVariable().currentLink().value();

        // payoff
        QL_REQUIRE(arguments_.payoff instanceof StrikedTypePayoff,
                "non-plain payoff given");
        final StrikedTypePayoff payoff =
                (StrikedTypePayoff) arguments_.payoff;

        // original time to maturity
        final Date maturity = arguments_.exercise.lastDate();

        final double X = payoff.strike();
        QL_REQUIRE(z_min_ <= 0 && z_max_ >= 0,
                "strike (0 for vecer fixed strike asian)  not on Grid");

        final double sigma = process_.blackVolatility().currentLink()
                .blackVol(maturity, X);

        final double r = process_.riskFreeRate().currentLink()
                .zeroRate(maturity, rfdc, Compounding.Continuous,
                        Frequency.NO_FREQUENCY).rate();
        final double q = process_.dividendYield().currentLink()
                .zeroRate(maturity, divdc, Compounding.Continuous,
                        Frequency.NO_FREQUENCY).rate();

        final Date today = Settings.instance.evaluationDate().Date();

        QL_REQUIRE(!TimeUtils.less(startDate_, today),
                "Seasoned Asian not yet implemented");

        // Expiry in Years
        final double T = rfdc.yearFraction(today,
                arguments_.exercise.lastDate());
        final double T1 = rfdc.yearFraction(today,
                startDate_);            // Average Begin
        final double T2 = T;            // Average End (In this version only Maturity...)

        if ((T2 - T1) < 0.001) {
            // its a vanilla option. Use vanilla engine
            final EuropeanOption europeanOption =
                    new EuropeanOption(payoff, arguments_.exercise);
            europeanOption.setPricingEngine(
                    new AnalyticEuropeanEngine(process_));
            results_.value = europeanOption.NPV();
        } else {
            final double Theta = 0.5;        // Mixed Scheme: 0.5 = Crank Nicolson
            final double Z_0 = cont_strategy(0, T1, T2, q, r)
                    - Math.exp(-r * T) * X / S_0;

            QL_REQUIRE(Z_0 >= z_min_ && Z_0 <= z_max_,
                    "spot not on grid");

            final double h = (z_max_ - z_min_) / assetSteps_; // Space step size
            final double k = T / timeSteps_;         // Time Step size

            final double sigma2 = sigma * sigma;
            double vecerTerm;

            final Array SVec = new Array(assetSteps_ + 1);
            final Array u_initial = new Array(assetSteps_ + 1);
            Array u;
            Array rhs;

            for (int i = 0; i <= SVec.size() - 1; i++) {
                SVec.set(i, z_min_ + i * h);     // Value of Underlying on the grid
            }

            // Begin gamma construction
            final TridiagonalOperator gammaOp =
                    new TridiagonalOperator(assetSteps_ + 1);
            gammaOp.setFirstRow(0.0, 0.0);
            gammaOp.setMidRows(1 / (h * h), -2 / (h * h), 1 / (h * h));
            gammaOp.setLastRow(0.0, 0.0);

            final Array upperD = new Array(gammaOp.upperDiagonal());
            final Array lowerD = new Array(gammaOp.lowerDiagonal());
            final Array Dia = new Array(gammaOp.diagonal());

            // Construct Vecer operator
            TridiagonalOperator explicit_part =
                    new TridiagonalOperator(gammaOp.size());
            TridiagonalOperator implicit_part =
                    new TridiagonalOperator(gammaOp.size());

            for (int i = 0; i <= SVec.size() - 1; i++) {
                u_initial.set(i, Math.max(SVec.get(i), 0.0)); // Call Payoff
            }

            u = new Array(u_initial);

            // Start Time Loop

            for (int j = 1; j <= timeSteps_; j++) {
                if (Theta != 1.0) { // Explicit Part
                    for (int i = 1; i <= SVec.size() - 2; i++) {
                        vecerTerm = SVec.get(i)
                                - Math.exp(-q * (T - (j - 1) * k))
                                * cont_strategy(T - (j - 1) * k, T1, T2, q, r);
                        gammaOp.setMidRow(i,
                                0.5 * sigma2 * vecerTerm * vecerTerm * lowerD.get(i - 1),
                                0.5 * sigma2 * vecerTerm * vecerTerm * Dia.get(i),
                                0.5 * sigma2 * vecerTerm * vecerTerm * upperD.get(i));
                    }
                    explicit_part = TridiagonalOperator.identity(gammaOp.size())
                            .add(gammaOp.multiply((1 - Theta) * k));
                    explicit_part.setFirstRow(1.0, 0.0); // Apply before applying
                    explicit_part.setLastRow(-1.0, 1.0); // Neumann BC

                    u = explicit_part.applyTo(u);

                    // Apply after applying (Neumann BC)
                    u.set(assetSteps_, u.get(assetSteps_ - 1) + h);
                    u.set(0, 0.0);
                } // End Explicit Part

                if (Theta != 0.0) {  // Implicit Part
                    for (int i = 1; i <= SVec.size() - 2; i++) {
                        vecerTerm = SVec.get(i)
                                - Math.exp(-q * (T - j * k))
                                * cont_strategy(T - j * k, T1, T2, q, r);
                        gammaOp.setMidRow(i,
                                0.5 * sigma2 * vecerTerm * vecerTerm * lowerD.get(i - 1),
                                0.5 * sigma2 * vecerTerm * vecerTerm * Dia.get(i),
                                0.5 * sigma2 * vecerTerm * vecerTerm * upperD.get(i));
                    }

                    implicit_part = TridiagonalOperator.identity(gammaOp.size())
                            .subtract(gammaOp.multiply(Theta * k));

                    // Apply before solving
                    implicit_part.setFirstRow(1.0, 0.0);
                    implicit_part.setLastRow(-1.0, 1.0);
                    rhs = new Array(u);
                    rhs.set(0, 0.0); // Lower BC
                    rhs.set(assetSteps_, h); // Upper BC (Neumann) Delta=1
                    u = implicit_part.solveFor(rhs);
                } // End implicit Part
            } // End Time Loop

            final DownRounding rounding = new DownRounding(0, 5);
            final int lowerI = (int) rounding.value((Z_0 - z_min_) / h);
            // Interpolate solution
            final double pv = u.get(lowerI)
                    + (u.get(lowerI + 1) - u.get(lowerI))
                    * (Z_0 - SVec.get(lowerI)) / h;
            results_.value = S_0 * pv;

            if (payoff.optionType() == Option.Type.Put) {
                // Apply Call Put Parity for Asians
                if (r == q) {
                    expectedAverage = S_0;
                } else {
                    expectedAverage =
                            S_0 * (Math.exp((r - q) * T2)
                                    - Math.exp((r - q) * T1))
                                    / ((r - q) * (T2 - T1));
                }

                final double asianForward =
                        Math.exp(-r * T2) * (expectedAverage - X);
                results_.value = results_.value - asianForward;
            }
        }
    }

    // Replication of Average by holding this amount in Assets
    protected double cont_strategy(double t,
                                   double T1,
                                   double T2,
                                   double v,
                                   double r) {
        final double eps = 0.00001;

        QL_REQUIRE(T1 <= T2, "Average Start must be before Average End");
        if (Math.abs(t - T2) < eps) {
            return 0.0;
        } else {
            if (t < T1) {
                if (Math.abs(r - v) >= eps) {
                    return (Math.exp(v * (t - T2))
                            * (1 - Math.exp((v - r) * (T2 - T1)))
                            / ((r - v) * (T2 - T1)));
                } else {
                    return Math.exp(v * (t - T2));
                } // end else v-r ==0
            } else { // t<T1
                if (Math.abs(r - v) >= eps) {
                    return Math.exp(v * (t - T2))
                            * (1 - Math.exp((v - r) * (T2 - t)))
                            / ((r - v) * (T2 - T1));
                } else {
                    return Math.exp(v * (t - T2)) * (T2 - t) / (T2 - T1);
                }
            }
        }
    }
}
