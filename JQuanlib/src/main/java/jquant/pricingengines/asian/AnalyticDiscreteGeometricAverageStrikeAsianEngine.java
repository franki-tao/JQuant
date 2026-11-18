package jquant.pricingengines.asian;

import jquant.Average;
import jquant.DiscreteAveragingAsianOption;
import jquant.Exercise;
import jquant.pricingengines.BlackCalculator;
import jquant.pricingengines.PlainVanillaPayoff;
import jquant.processes.GeneralizedBlackScholesProcess;
import jquant.time.Date;
import jquant.time.DayCounter;
import jquant.time.TimeUtils;

import java.util.ArrayList;
import java.util.List;

import static jquant.math.CommonUtil.QL_REQUIRE;

/**
 * Pricing engine for European discrete geometric average-strike Asian
 * options.
 *
 * <p>Formula from "Asian Option", E. Levy (1997) in "Exotic Options: The
 * State of the Art", edited by L. Clewlow, C. Strickland, pag 65-97.
 * Ported from QuantLib
 * {@code ql/pricingengines/asian/analytic_discr_geom_av_strike.cpp}.
 *
 * <p>As in QuantLib, {@link #calculate()} writes only {@code results_.value};
 * no Greeks are provided by this engine.
 */
public class AnalyticDiscreteGeometricAverageStrikeAsianEngine
        extends DiscreteAveragingAsianOption.engine {
    private final GeneralizedBlackScholesProcess process_;

    public AnalyticDiscreteGeometricAverageStrikeAsianEngine(
            GeneralizedBlackScholesProcess process) {
        process_ = process;
        registerWith(process_);
    }

    @Override
    public void calculate() {
        QL_REQUIRE(arguments_.averageType == Average.Type.Geometric,
                "not a geometric average option");

        QL_REQUIRE(arguments_.exercise.type() == Exercise.Type.European,
                "not an European option");

        QL_REQUIRE(arguments_.runningAccumulator > 0.0,
                "positive running product required: "
                        + arguments_.runningAccumulator + "not allowed");
        double runningLog = Math.log(arguments_.runningAccumulator);
        int pastFixings = arguments_.pastFixings;
        QL_REQUIRE(pastFixings == 0, "past fixings currently not managed");

        QL_REQUIRE(arguments_.payoff instanceof PlainVanillaPayoff,
                "non-plain payoff given");
        PlainVanillaPayoff payoff = (PlainVanillaPayoff) arguments_.payoff;

        Date referenceDate = process_.riskFreeRate().getValue().referenceDate();
        Date volReferenceDate = process_.blackVolatility().getValue().referenceDate();
        DayCounter voldc = process_.blackVolatility().getValue().dayCounter();

        List<Double> fixingTimes = new ArrayList<>();
        double driftSum = 0.0;
        for (Date fixingDate : arguments_.fixingDates) {
            if (!TimeUtils.less(fixingDate, referenceDate)) {
                double t = voldc.yearFraction(volReferenceDate, fixingDate);
                fixingTimes.add(t);
                driftSum += Math.log(
                        process_.dividendYield().getValue().discount(fixingDate, false)
                                / process_.riskFreeRate().getValue()
                                .discount(fixingDate, false));
            }
        }
        QL_REQUIRE(!fixingTimes.isEmpty(), "no future fixings");

        int remainingFixings = fixingTimes.size();
        int numberOfFixings = pastFixings + remainingFixings;
        double N = numberOfFixings;

        double pastWeight = pastFixings / N;
        double futureWeight = 1.0 - pastWeight;

        double timeSum = 0.0;
        for (double fixingTime : fixingTimes) {
            timeSum += fixingTime;
        }

        double residualTime = voldc.yearFraction(volReferenceDate,
                arguments_.exercise.lastDate());

        double underlying = process_.stateVariable().getValue().value();
        QL_REQUIRE(underlying > 0.0, "positive underlying value required");

        double volatility = process_.blackVolatility().getValue()
                .blackVol(arguments_.exercise.lastDate(), underlying, false);

        Date exDate = arguments_.exercise.lastDate();
        double dividendDiscount = process_.dividendYield().getValue()
                .discount(exDate, false);
        double riskFreeDiscount = process_.riskFreeRate().getValue()
                .discount(exDate, false);

        double temp = 0.0;
        for (int i = pastFixings + 1; i < numberOfFixings; i++) {
            temp += fixingTimes.get(i - pastFixings - 1) * (N - i);
        }
        double variance = volatility * volatility / N / N
                * (timeSum + 2.0 * temp);
        // Variance of log(S_T/G): between the i-th and (i+1)-th fixings the
        // Brownian increment has weight i/N, after the last one weight 1.
        // Summing these nonnegative terms avoids cancellation in
        // Var[ln S_T] + Var[ln G] - 2 Cov[ln S_T, ln G].
        double sigmaSum_2 = residualTime - fixingTimes.get(remainingFixings - 1);
        for (int i = 1; i < remainingFixings; i++) {
            double weight = (double) i / N;
            sigmaSum_2 += weight * weight
                    * (fixingTimes.get(i) - fixingTimes.get(i - 1));
        }
        sigmaSum_2 *= volatility * volatility;

        double M = (pastFixings == 0 ? 1 : pastFixings);
        double runningLogAverage = runningLog / M;

        double muG = pastWeight * runningLogAverage
                + futureWeight * Math.log(underlying)
                + (driftSum - 0.5 * volatility * volatility * timeSum) / N;

        double forward = underlying * dividendDiscount / riskFreeDiscount;
        double forwardAverage = Math.exp(muG + variance / 2.0);
        // No randomness left in S_T/G (e.g. every fixing at maturity, or
        // zero volatility).
        if (sigmaSum_2 == 0.0) {
            PlainVanillaPayoff limitPayoff =
                    new PlainVanillaPayoff(payoff.optionType(), forwardAverage);
            results_.value = riskFreeDiscount * limitPayoff.value(forward);
        } else {
            BlackCalculator black = new BlackCalculator(
                    payoff.optionType(), forwardAverage, forward,
                    Math.sqrt(sigmaSum_2), riskFreeDiscount);
            results_.value = black.value();
        }
    }
}
