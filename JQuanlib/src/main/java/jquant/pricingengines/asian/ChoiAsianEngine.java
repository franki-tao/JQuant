package jquant.pricingengines.asian;

import jquant.Average;
import jquant.AverageBasketPayoff;
import jquant.BasketOption;
import jquant.DiscreteAveragingAsianOption;
import jquant.Exercise;
import jquant.Handle;
import jquant.Option;
import jquant.Quote;
import jquant.EuropeanExercise;
import jquant.math.Array;
import jquant.math.Matrix;
import jquant.pricingengines.BlackFormula;
import jquant.pricingengines.PlainVanillaPayoff;
import jquant.pricingengines.basket.ChoiBasketEngine;
import jquant.processes.EulerDiscretization;
import jquant.processes.GeneralizedBlackScholesProcess;
import jquant.quotes.SimpleQuote;
import jquant.termstructures.YieldTermStructure;
import jquant.termstructures.volatility.equityfx.BlackConstantVol;
import jquant.termstructures.volatility.equityfx.BlackVolTermStructure;
import jquant.termstructures.yield.FlatForward;
import jquant.time.Date;
import jquant.time.DayCounter;
import jquant.time.TimeUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import static jquant.math.CommonUtil.QL_REQUIRE;

/**
 * Black-Scholes arithmetic Asian option engine.
 *
 * <p>Replicates an arithmetic Asian option with a basket option. Ported from
 * QuantLib {@code ql/pricingengines/asian/choiasianengine.{hpp,cpp}}.
 */
public class ChoiAsianEngine extends DiscreteAveragingAsianOption.engine {
    private final GeneralizedBlackScholesProcess process_;
    private final double lambda_;
    private final int maxNrIntegrationSteps_;

    public ChoiAsianEngine(GeneralizedBlackScholesProcess process) {
        this(process, 15.0, 2 << 21);
    }

    public ChoiAsianEngine(GeneralizedBlackScholesProcess process,
                           double lambda, int maxNrIntegrationSteps) {
        process_ = process;
        lambda_ = lambda;
        maxNrIntegrationSteps_ = maxNrIntegrationSteps;
        registerWith(process_);
    }

    @Override
    public void calculate() {
        QL_REQUIRE(arguments_.averageType == Average.Type.Arithmetic,
                "must be Average::Type Arithmetic ");

        QL_REQUIRE(arguments_.exercise.type() == Exercise.Type.European,
                "not a European Option");

        QL_REQUIRE(arguments_.payoff instanceof PlainVanillaPayoff,
                "non plain vanilla payoff given");
        PlainVanillaPayoff payoff = (PlainVanillaPayoff) arguments_.payoff;

        List<Date> fixingDates = new ArrayList<>(arguments_.fixingDates);
        fixingDates.sort(Comparator.comparingInt(Date::serialNumber));

        int futureFixings = fixingDates.size();
        int pastFixings = arguments_.pastFixings;
        double runningAccumulator = arguments_.runningAccumulator;

        Date exerciseDate = arguments_.exercise.lastDate();
        Handle<YieldTermStructure> rTS = process_.riskFreeRate();

        // the portion of accruedAverage that still moves with spot: ordinary
        // past fixings are fixed numbers, but a fixing pushed to "past"
        // below because it falls exactly today is today's spot itself
        double spotSensitiveAccrued = 0.0;

        if (futureFixings > 0
                && process_.time(fixingDates.get(0)) == 0.0) {
            // push today fixing to past fixings
            fixingDates.remove(0);
            futureFixings--;
            pastFixings++;
            runningAccumulator += process_.x0();
            spotSensitiveAccrued =
                    process_.x0() / (double) (pastFixings + futureFixings);
        }

        if (futureFixings == 0) {
            QL_REQUIRE(pastFixings > 0, "no past fixings given");
            results_.value = payoff.value(runningAccumulator / pastFixings)
                    * rTS.getValue().discount(exerciseDate);
            return;
        }

        QL_REQUIRE(!TimeUtils.greater(fixingDates.get(fixingDates.size() - 1),
                        exerciseDate),
                "last fixing date must be before exercise date");
        QL_REQUIRE(process_.time(fixingDates.get(0)) >= 0.0,
                "first fixing date is in the past");

        boolean duplicate = false;
        for (int i = 1; i < fixingDates.size(); ++i) {
            if (TimeUtils.equals(fixingDates.get(i), fixingDates.get(i - 1))) {
                duplicate = true;
                break;
            }
        }
        QL_REQUIRE(!duplicate, "two fixing dates are the same");

        double accruedAverage = (pastFixings != 0)
                ? runningAccumulator / (double) (pastFixings + futureFixings)
                : 0.0;

        double strike = payoff.strike() - accruedAverage;

        // Effective strike non-positive: the average is bounded below by the
        // accrued average, so exercise is guaranteed.
        if (strike <= 0.0) {
            double expectedAverage = accruedAverage;
            for (Date fd : fixingDates) {
                expectedAverage += process_.x0()
                        * process_.dividendYield().getValue().discount(fd)
                        / rTS.getValue().discount(fd)
                        / (pastFixings + futureFixings);
            }

            if (payoff.optionType() == Option.Type.Call) {
                results_.value = rTS.getValue().discount(exerciseDate)
                        * (expectedAverage - payoff.strike());
                results_.delta = rTS.getValue().discount(exerciseDate)
                        * (expectedAverage - accruedAverage + spotSensitiveAccrued)
                        / process_.x0();
            } else if (payoff.optionType() == Option.Type.Put) {
                results_.value = 0.0;
                results_.delta = 0.0;
            }
            results_.gamma = 0.0;
            return;
        }

        QL_REQUIRE(strike > 0.0, "effective strike should be positive");

        Handle<YieldTermStructure> qTS = process_.dividendYield();
        Handle<BlackVolTermStructure> volTS = process_.blackVolatility();
        Date volRefDate = volTS.getValue().referenceDate();
        DayCounter volDc = volTS.getValue().dayCounter();

        // Split the fixings into zero-variance (deterministic) and stochastic
        // ones. A zero variance would make the pairwise correlation below 0/0;
        // dropping the deterministic fixings from the basket and folding their
        // forwards into the strike is the exact continuous extension.
        double deterministicForward = 0.0;
        List<Date> stochasticDates = new ArrayList<>();
        List<Double> fixingTimes = new ArrayList<>();
        List<Double> variances = new ArrayList<>();
        for (int i = 0; i < futureFixings; ++i) {
            Date fixingDate = fixingDates.get(i);
            double variance = volTS.getValue()
                    .blackVariance(fixingDate, strike);
            if (variance > 0.0) {
                stochasticDates.add(fixingDate);
                fixingTimes.add(volDc.yearFraction(volRefDate, fixingDate));
                variances.add(variance);
            } else {
                deterministicForward += process_.x0()
                        * qTS.getValue().discount(fixingDate)
                        / rTS.getValue().discount(fixingDate)
                        / (pastFixings + futureFixings);
            }
        }

        if (variances.isEmpty()) {
            // all remaining fixings are deterministic: closed form
            double expectedAverage = accruedAverage + deterministicForward;
            double expectedForwards = expectedAverage - accruedAverage
                    + spotSensitiveAccrued;
            double sensitivity = rTS.getValue().discount(exerciseDate)
                    * expectedForwards / process_.x0();
            results_.value = rTS.getValue().discount(exerciseDate)
                    * payoff.value(expectedAverage);
            if (payoff.optionType() == Option.Type.Call) {
                results_.delta = expectedAverage > payoff.strike()
                        ? sensitivity
                        : (expectedAverage == payoff.strike()
                        ? 0.5 * sensitivity : 0.0);
            } else {
                results_.delta = expectedAverage < payoff.strike()
                        ? -sensitivity
                        : (expectedAverage == payoff.strike()
                        ? -0.5 * sensitivity : 0.0);
            }
            results_.gamma = 0.0;
            return;
        }

        double residualStrike = strike - deterministicForward;

        if (residualStrike <= 0.0) {
            // the stochastic part alone guarantees exercise, as above
            double expectedAverage = accruedAverage + deterministicForward;
            for (Date fd : stochasticDates) {
                expectedAverage += process_.x0()
                        * qTS.getValue().discount(fd)
                        / rTS.getValue().discount(fd)
                        / (pastFixings + futureFixings);
            }

            if (payoff.optionType() == Option.Type.Call) {
                results_.value = rTS.getValue().discount(exerciseDate)
                        * (expectedAverage - payoff.strike());
                results_.delta = rTS.getValue().discount(exerciseDate)
                        * (expectedAverage - accruedAverage + spotSensitiveAccrued)
                        / process_.x0();
            } else if (payoff.optionType() == Option.Type.Put) {
                results_.value = 0.0;
                results_.delta = 0.0;
            }
            results_.gamma = 0.0;
            return;
        }

        if (variances.size() == 1) {
            results_.value = BlackFormula.blackFormula(
                    payoff.optionType(),
                    residualStrike,
                    process_.x0() / (pastFixings + futureFixings)
                            * qTS.getValue().discount(stochasticDates.get(stochasticDates.size() - 1))
                            / rTS.getValue().discount(stochasticDates.get(stochasticDates.size() - 1)),
                    Math.sqrt(variances.get(variances.size() - 1)),
                    rTS.getValue().discount(exerciseDate),
                    0.0);
        } else {
            Matrix rho = new Matrix(variances.size(), variances.size(), 0.0);
            for (int i = 0; i < rho.rows(); ++i) {
                for (int j = i; j < rho.cols(); ++j) {
                    double value = variances.get(Math.min(i, j))
                            / (Math.sqrt(variances.get(i))
                            * Math.sqrt(variances.get(j)));
                    rho.set(i, j, value);
                    rho.set(j, i, value);
                }
            }

            Handle<YieldTermStructure> zeroTS = new Handle<>(
                    new FlatForward(rTS.getValue().referenceDate(), 0.0,
                            rTS.getValue().dayCounter()), true);

            List<GeneralizedBlackScholesProcess> processes = new ArrayList<>();
            for (int i = 0; i < variances.size(); ++i) {
                Date fixingDate = stochasticDates.get(i);
                double sig = volTS.getValue()
                        .blackVol(fixingDate, payoff.strike())
                        * Math.sqrt(fixingTimes.get(i)
                        / fixingTimes.get(fixingTimes.size() - 1));

                processes.add(new GeneralizedBlackScholesProcess(
                        new Handle<Quote>(new SimpleQuote(
                                process_.x0()
                                        * qTS.getValue().discount(fixingDate)
                                        / rTS.getValue().discount(fixingDate)),
                                true),
                        zeroTS, zeroTS,
                        new Handle<BlackVolTermStructure>(
                                new BlackConstantVol(
                                        volRefDate, volTS.getValue().calendar(),
                                        new Handle<Quote>(
                                                new SimpleQuote(sig), true),
                                        volDc), true),
                        new EulerDiscretization(),
                        false));
            }

            BasketOption basketOption = new BasketOption(
                    new AverageBasketPayoff(
                            new PlainVanillaPayoff(payoff.optionType(),
                                    residualStrike),
                            new Array(variances.size(),
                                    1.0 / (futureFixings + pastFixings))),
                    new EuropeanExercise(
                            stochasticDates.get(stochasticDates.size() - 1)));
            basketOption.setPricingEngine(new ChoiBasketEngine(
                    processes, rho, lambda_, maxNrIntegrationSteps_,
                    false, false));

            results_.value = basketOption.NPV()
                    * rTS.getValue().discount(exerciseDate);
        }
    }
}
