package jquant.pricingengines.asian;

import jquant.Average;
import jquant.DiscreteAveragingAsianOption;
import jquant.Exercise;
import jquant.Option;
import jquant.pricingengines.BlackCalculator;
import jquant.pricingengines.PlainVanillaPayoff;
import jquant.processes.GeneralizedBlackScholesProcess;
import jquant.time.Date;

import java.util.ArrayList;
import java.util.List;

import static jquant.math.CommonUtil.QL_REQUIRE;

/**
 * Turnbull-Wakeman two-moment matching Asian option engine.
 *
 * <p>Analytical pricing of discrete arithmetic average-rate Asian options.
 * Port of {@code TurnbullWakemanAsianEngine} from
 * {@code ql/pricingengines/asian/turnbullwakemanasianengine.{hpp,cpp}}.
 */
public class TurnbullWakemanAsianEngine
        extends DiscreteAveragingAsianOption.engine {
    private final GeneralizedBlackScholesProcess process_;

    public TurnbullWakemanAsianEngine(
            GeneralizedBlackScholesProcess process) {
        process_ = process;
        registerWith(process_);
    }

    @Override
    public void calculate() {
        // Enforce a few required things
        QL_REQUIRE(arguments_.exercise.type() == Exercise.Type.European,
                "not a European Option");
        QL_REQUIRE(arguments_.averageType == Average.Type.Arithmetic,
                "must be Arithmetic Average::Type");

        // Calculate the accrued portion
        final int pastFixings = arguments_.pastFixings;
        final int futureFixings = arguments_.fixingDates.size();
        double accruedAverage = 0.0;
        if (pastFixings != 0) {
            accruedAverage = arguments_.runningAccumulator
                    / (pastFixings + futureFixings);
        }
        results_.additionalResults.put("accrued", accruedAverage);

        final double discount = process_.riskFreeRate().getValue()
                .discount(arguments_.exercise.lastDate(), false);
        results_.additionalResults.put("discount", discount);

        QL_REQUIRE(arguments_.payoff instanceof PlainVanillaPayoff,
                "non-plain payoff given");
        final PlainVanillaPayoff payoff =
                (PlainVanillaPayoff) arguments_.payoff;

        // We will read the volatility off the surface at the effective strike
        final double effectiveStrike = payoff.strike() - accruedAverage;
        results_.additionalResults.put("strike", payoff.strike());
        results_.additionalResults.put("effective_strike", effectiveStrike);

        if (futureFixings == 0) {
            QL_REQUIRE(pastFixings > 0, "no fixings given");
            results_.value = discount * payoff.value(accruedAverage);
            results_.delta = 0.0;
            results_.gamma = 0.0;
            return;
        }

        // If the effective strike is negative, exercise resp. permanent OTM
        // is guaranteed and the valuation is made easy
        final int m = futureFixings + pastFixings;
        if (effectiveStrike <= 0.0) {
            // For a reference, see "Option Pricing Formulas", Haug, 2nd ed,
            // p. 193
            if (payoff.optionType() == Option.Type.Call) {
                final double spot = process_.stateVariable().getValue().value();
                double sAHat = accruedAverage;
                for (Date fd : arguments_.fixingDates) {
                    sAHat += (spot * process_.dividendYield().getValue()
                            .discount(fd, false)
                            / process_.riskFreeRate().getValue()
                            .discount(fd, false)) / m;
                }
                results_.value = discount * (sAHat - payoff.strike());
                results_.delta = discount * (sAHat - accruedAverage) / spot;
            } else if (payoff.optionType() == Option.Type.Put) {
                results_.value = 0.0;
                results_.delta = 0.0;
            }
            results_.gamma = 0.0;
            return;
        }

        // We should only get this far when the effectiveStrike > 0 but will
        // check anyway
        QL_REQUIRE(effectiveStrike > 0.0,
                "expected effectiveStrike to be positive");

        // Expected value of the non-accrued portion of the average prices
        // In general, m will equal n below if there is no accrued. If
        // accrued, m > n.
        double ea = 0.0;
        final List<Double> forwards = new ArrayList<>();
        final List<Double> times = new ArrayList<>();
        final List<Double> spotVars = new ArrayList<>();
        final List<Double> spotVols = new ArrayList<>();
        final double spot = process_.stateVariable().getValue().value();

        for (Date fd : arguments_.fixingDates) {
            final double dividendDiscount = process_.dividendYield().getValue()
                    .discount(fd, false);
            final double riskFreeDiscountForFwdEstimation =
                    process_.riskFreeRate().getValue().discount(fd, false);

            forwards.add(spot * dividendDiscount
                    / riskFreeDiscountForFwdEstimation);
            times.add(process_.blackVolatility().getValue()
                    .timeFromReference(fd));

            spotVars.add(process_.blackVolatility().getValue()
                    .blackVariance(times.get(times.size() - 1),
                            effectiveStrike, false));
            spotVols.add(process_.blackVolatility().getValue()
                    .blackVol(times.get(times.size() - 1),
                            effectiveStrike, false));

            ea += forwards.get(forwards.size() - 1);
        }
        ea /= m;

        // Expected value of A^2.
        double ea2 = 0.0;
        final int n = forwards.size();

        for (int i = 0; i < n; ++i) {
            ea2 += forwards.get(i) * forwards.get(i)
                    * Math.exp(spotVars.get(i));
            for (int j = 0; j < i; ++j) {
                ea2 += 2.0 * forwards.get(i) * forwards.get(j)
                        * Math.exp(spotVars.get(j));
            }
        }

        ea2 /= (double) m * m;

        // Calculate value
        final double tn = times.get(times.size() - 1);
        final double stdDev = tn > 0.0
                ? Math.sqrt(Math.log(ea2 / (ea * ea))) : 0.0;
        final double sigma = tn > 0.0
                ? stdDev / Math.sqrt(tn) : 0.0;

        // Populate results
        final BlackCalculator black = new BlackCalculator(
                payoff.optionType(), effectiveStrike, ea, stdDev, discount);

        results_.value = black.value();
        results_.delta = stdDev > 0.0
                ? black.delta(spot)
                : discount * black.alpha() * ea / spot;
        results_.gamma = black.gamma(spot);

        results_.additionalResults.put("forward", ea);
        results_.additionalResults.put("exp_A_2", ea2);
        results_.additionalResults.put("tte", tn);
        results_.additionalResults.put("sigma", sigma);
        results_.additionalResults.put("times", times);
        results_.additionalResults.put("spotVols", spotVols);
        results_.additionalResults.put("forwards", forwards);
    }
}
