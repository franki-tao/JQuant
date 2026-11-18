package jquant.pricingengines.asian;

import jquant.Average;
import jquant.Compounding;
import jquant.ContinuousAveragingAsianOption;
import jquant.Exercise;
import jquant.pricingengines.BlackCalculator;
import jquant.pricingengines.PlainVanillaPayoff;
import jquant.processes.GeneralizedBlackScholesProcess;
import jquant.time.Date;
import jquant.time.DayCounter;
import jquant.time.Frequency;
import jquant.time.TimeUtils;

import static jquant.math.CommonUtil.QL_FAIL;
import static jquant.math.CommonUtil.QL_REQUIRE;

/**
 * Pricing engine for European continuous geometric average price Asian
 * options.
 *
 * <p>Formula from "Option Pricing Formulas", E. G. Haug (1997) pag 96-97.
 * Ported from QuantLib
 * {@code ql/pricingengines/asian/analytic_cont_geom_av_price.cpp}.
 */
public class AnalyticContinuousGeometricAveragePriceAsianEngine
        extends ContinuousAveragingAsianOption.engine {
    private final GeneralizedBlackScholesProcess process_;

    public AnalyticContinuousGeometricAveragePriceAsianEngine(
            GeneralizedBlackScholesProcess process) {
        process_ = process;
        registerWith(process_);
    }

    @Override
    public void calculate() {
        QL_REQUIRE(arguments_.averageType == Average.Type.Geometric,
                "not a geometric average option");
        QL_REQUIRE(arguments_.exercise.type() == Exercise.Type.European,
                "not an European Option");

        // Seasoned options require adjustment of the forward price and
        // variance based on the accumulated geometric average; QuantLib does
        // not support them yet.
        if (!TimeUtils.equals(arguments_.startDate, new Date())) {
            QL_FAIL("seasoned continuous geometric Asian options not yet supported - "
                    + "requires adjustment of forward price and variance based on "
                    + "accumulated geometric average. This feature needs mathematical "
                    + "review and implementation.");
        }

        Date exercise = arguments_.exercise.lastDate();

        QL_REQUIRE(arguments_.payoff instanceof PlainVanillaPayoff,
                "non-plain payoff given");
        PlainVanillaPayoff payoff = (PlainVanillaPayoff) arguments_.payoff;

        double volatility = process_.blackVolatility().getValue()
                .blackVol(exercise, payoff.strike(), false);
        double variance = process_.blackVolatility().getValue()
                .blackVariance(exercise, payoff.strike(), false);
        double riskFreeDiscount = process_.riskFreeRate().getValue()
                .discount(exercise, false);

        DayCounter rfdc = process_.riskFreeRate().getValue().dayCounter();
        DayCounter divdc = process_.dividendYield().getValue().dayCounter();
        DayCounter voldc = process_.blackVolatility().getValue().dayCounter();

        double dividendYield = 0.5 * (
                process_.riskFreeRate().getValue()
                        .zeroRate(exercise, rfdc, Compounding.Continuous,
                                Frequency.NO_FREQUENCY, false).rate()
                        + process_.dividendYield().getValue()
                        .zeroRate(exercise, divdc, Compounding.Continuous,
                                Frequency.NO_FREQUENCY, false).rate()
                        + volatility * volatility / 6.0);

        double t_q = divdc.yearFraction(
                process_.dividendYield().getValue().referenceDate(), exercise);
        double dividendDiscount = Math.exp(-dividendYield * t_q);

        double spot = process_.stateVariable().getValue().value();
        QL_REQUIRE(spot > 0.0, "negative or null underlying");
        double forward = spot * dividendDiscount / riskFreeDiscount;

        BlackCalculator black = new BlackCalculator(
                payoff, forward, Math.sqrt(variance / 3.0), riskFreeDiscount);

        results_.value = black.value();
        results_.delta = black.delta(spot);
        results_.gamma = black.gamma(spot);

        results_.dividendRho = black.dividendRho(t_q) / 2.0;

        double t_r = rfdc.yearFraction(
                process_.riskFreeRate().getValue().referenceDate(),
                arguments_.exercise.lastDate());
        results_.rho = black.rho(t_r) + 0.5 * black.dividendRho(t_q);

        double t_v = voldc.yearFraction(
                process_.blackVolatility().getValue().referenceDate(),
                arguments_.exercise.lastDate());
        results_.vega = black.vega(t_v) / Math.sqrt(3.0)
                + black.dividendRho(t_q) * volatility / 6.0;
        try {
            results_.theta = black.theta(spot, t_v);
        } catch (IllegalArgumentException e) {
            results_.theta = Double.NaN;
        }
    }
}
