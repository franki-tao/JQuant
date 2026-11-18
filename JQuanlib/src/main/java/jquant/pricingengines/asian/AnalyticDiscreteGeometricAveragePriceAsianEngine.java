package jquant.pricingengines.asian;

import jquant.Average;
import jquant.Compounding;
import jquant.DiscreteAveragingAsianOption;
import jquant.Exercise;
import jquant.Option;
import jquant.math.distributions.CumulativeNormalDistribution;
import jquant.math.distributions.NormalDistribution;
import jquant.pricingengines.BlackCalculator;
import jquant.pricingengines.Greeks;
import jquant.pricingengines.PlainVanillaPayoff;
import jquant.processes.GeneralizedBlackScholesProcess;
import jquant.time.Date;
import jquant.time.DayCounter;
import jquant.time.Frequency;
import jquant.time.TimeUtils;

import java.util.ArrayList;
import java.util.List;

import static jquant.math.CommonUtil.QL_REQUIRE;
import static jquant.math.MathUtils.QL_EPSILON;

/**
 * Pricing engine for European discrete geometric average price Asian options.
 *
 * <p>Formula from "Asian Option", E. Levy (1997) in "Exotic Options: The
 * State of the Art", edited by L. Clewlow, C. Strickland, pag 65-97.
 * Ported from QuantLib
 * {@code ql/pricingengines/asian/analytic_discr_geom_av_price.cpp}.
 */
public class AnalyticDiscreteGeometricAveragePriceAsianEngine
        extends DiscreteAveragingAsianOption.engine {
    private final GeneralizedBlackScholesProcess process_;

    public AnalyticDiscreteGeometricAveragePriceAsianEngine(
            GeneralizedBlackScholesProcess process) {
        process_ = process;
        registerWith(process_);
    }

    @Override
    public void calculate() {
        // This engine does not check averageType==Geometric, since QuantLib
        // allows it as a control variate for the arithmetic version.

        QL_REQUIRE(arguments_.exercise.type() == Exercise.Type.European,
                "not an European Option");

        double runningLog;
        int pastFixings;
        if (arguments_.averageType == Average.Type.Geometric) {
            QL_REQUIRE(arguments_.runningAccumulator > 0.0,
                    "positive running product required: "
                            + arguments_.runningAccumulator + " not allowed");
            runningLog = Math.log(arguments_.runningAccumulator);
            pastFixings = arguments_.pastFixings;
        } else {  // it is being used as control variate
            runningLog = 1.0;
            pastFixings = 0;
        }

        QL_REQUIRE(arguments_.payoff instanceof PlainVanillaPayoff,
                "non-plain payoff given");
        PlainVanillaPayoff payoff = (PlainVanillaPayoff) arguments_.payoff;

        Date referenceDate = process_.riskFreeRate().getValue().referenceDate();
        DayCounter rfdc = process_.riskFreeRate().getValue().dayCounter();
        DayCounter divdc = process_.dividendYield().getValue().dayCounter();
        DayCounter voldc = process_.blackVolatility().getValue().dayCounter();
        List<Double> fixingTimes = new ArrayList<>();
        for (Date fixingDate : arguments_.fixingDates) {
            if (!TimeUtils.less(fixingDate, referenceDate)) {
                double t = voldc.yearFraction(referenceDate, fixingDate);
                fixingTimes.add(t);
            }
        }

        int remainingFixings = fixingTimes.size();
        int numberOfFixings = pastFixings + remainingFixings;
        double N = numberOfFixings;

        double pastWeight = pastFixings / N;
        double futureWeight = 1.0 - pastWeight;

        double timeSum = 0.0;
        for (double fixingTime : fixingTimes) {
            timeSum += fixingTime;
        }

        double vola = process_.blackVolatility().getValue()
                .blackVol(arguments_.exercise.lastDate(), payoff.strike(), false);
        double temp = 0.0;
        for (int i = pastFixings + 1; i < numberOfFixings; i++) {
            temp += fixingTimes.get(i - pastFixings - 1) * (N - i);
        }
        double variance = vola * vola / N / N * (timeSum + 2.0 * temp);
        double dsigG_dsig = Math.sqrt(timeSum + 2.0 * temp) / N;
        double sigG = vola * dsigG_dsig;
        double dmuG_dsig = -(vola * timeSum) / N;

        Date exDate = arguments_.exercise.lastDate();
        double dividendRate = process_.dividendYield().getValue()
                .zeroRate(exDate, divdc, Compounding.Continuous,
                        Frequency.NO_FREQUENCY, false).rate();
        double riskFreeRate = process_.riskFreeRate().getValue()
                .zeroRate(exDate, rfdc, Compounding.Continuous,
                        Frequency.NO_FREQUENCY, false).rate();
        double nu = riskFreeRate - dividendRate - 0.5 * vola * vola;

        double s = process_.stateVariable().getValue().value();
        QL_REQUIRE(s > 0.0, "positive underlying value required");

        double M = (pastFixings == 0 ? 1 : pastFixings);
        double muG = pastWeight * runningLog / M
                + futureWeight * Math.log(s) + nu * timeSum / N;
        double forwardPrice = Math.exp(muG + variance / 2.0);

        double riskFreeDiscount = process_.riskFreeRate().getValue()
                .discount(arguments_.exercise.lastDate(), false);

        BlackCalculator black = new BlackCalculator(
                payoff, forwardPrice, Math.sqrt(variance), riskFreeDiscount);

        results_.value = black.value();
        results_.delta = futureWeight * black.delta(forwardPrice)
                * forwardPrice / s;
        results_.gamma = forwardPrice * futureWeight / (s * s)
                * (black.gamma(forwardPrice) * futureWeight * forwardPrice
                - pastWeight * black.delta(forwardPrice));

        double Nx_1;
        double nx_1;
        CumulativeNormalDistribution CND = new CumulativeNormalDistribution();
        NormalDistribution ND = new NormalDistribution();
        if (sigG > QL_EPSILON) {
            double x_1 = (muG - Math.log(payoff.strike()) + variance) / sigG;
            Nx_1 = CND.value(x_1);
            nx_1 = ND.value(x_1);
        } else {
            Nx_1 = (muG > Math.log(payoff.strike()) ? 1.0 : 0.0);
            nx_1 = 0.0;
        }
        results_.vega = forwardPrice * riskFreeDiscount
                * ((dmuG_dsig + sigG * dsigG_dsig) * Nx_1
                + nx_1 * dsigG_dsig);

        if (payoff.optionType() == Option.Type.Put) {
            results_.vega -= riskFreeDiscount * forwardPrice
                    * (dmuG_dsig + sigG * dsigG_dsig);
        }

        double tRho = rfdc.yearFraction(
                process_.riskFreeRate().getValue().referenceDate(),
                arguments_.exercise.lastDate());
        results_.rho = black.rho(tRho) * timeSum / (N * tRho)
                - (tRho - timeSum / N) * results_.value;

        double tDiv = divdc.yearFraction(
                process_.dividendYield().getValue().referenceDate(),
                arguments_.exercise.lastDate());

        results_.dividendRho = black.dividendRho(tDiv) * timeSum / (N * tDiv);

        results_.strikeSensitivity = black.strikeSensitivity();

        results_.theta = Greeks.blackScholesTheta(process_,
                results_.value,
                results_.delta,
                results_.gamma);
    }
}
