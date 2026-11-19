package jquant.pricingengines.asian;

import jquant.Average;
import jquant.Compounding;
import jquant.ContinuousAveragingAsianOption;
import jquant.Exercise;
import jquant.Handle;
import jquant.Option;
import jquant.Quote;
import jquant.math.distributions.CumulativeNormalDistribution;
import jquant.pricingengines.StrikedTypePayoff;
import jquant.processes.GeneralizedBlackScholesProcess;
import jquant.time.Date;
import jquant.time.DayCounter;
import jquant.time.Frequency;
import jquant.time.TimeUtils;

import static jquant.math.CommonUtil.QL_REQUIRE;

/**
 * Levy engine for continuously averaged arithmetic Asian options.
 *
 * <p>Formulas from Haug, "Option Pricing Formulas". Port of
 * {@code ContinuousArithmeticAsianLevyEngine} from
 * {@code ql/pricingengines/asian/continuousarithmeticasianlevyengine.{hpp,cpp}}.
 */
public class ContinuousArithmeticAsianLevyEngine
        extends ContinuousAveragingAsianOption.engine {
    private final GeneralizedBlackScholesProcess process_;
    private final Handle<Quote> currentAverage_;
    private final Date startDate_;

    public ContinuousArithmeticAsianLevyEngine(
            GeneralizedBlackScholesProcess process,
            Handle<Quote> currentAverage) {
        this(process, currentAverage, new Date());
    }

    /**
     * @deprecated Use the constructor without a start date and pass the
     * start date to the option instead.
     */
    @Deprecated
    public ContinuousArithmeticAsianLevyEngine(
            GeneralizedBlackScholesProcess process,
            Handle<Quote> currentAverage,
            Date startDate) {
        process_ = process;
        currentAverage_ = currentAverage;
        startDate_ = startDate;
        registerWith(process_);
        registerWith(currentAverage_.get());
    }

    private static double exponentialIntegral(double x) {
        return x == 0.0 ? 1.0 : Math.expm1(x) / x;
    }

    // 2 int_0^1 int_0^t exp(x*(s+t))*expm1(w*s) ds dt.
    private static double levyVariance(double x, double w) {
        int doublings = 0;
        while (Math.abs(x) + w > 0.5) {
            x *= 0.5;
            w *= 0.5;
            ++doublings;
        }

        // For phi(x) = expm1(x)/x the integral is the divided difference
        // 2*w*phi[x,2*x,2*x+w]. Expand its complete homogeneous polynomials
        // directly, without subtracting moments. All nodes have |x| <= 1;
        // 20 terms leave a truncation error below machine precision.
        // The auxiliary covariance C(x,w) = phi(x+w) - phi(x) supplies
        // the cross-interval contribution when doubling the interval.
        double covariance = 0.0;
        double variance = 0.0;
        double power = 1.0;
        double covariancePolynomial = 1.0;
        double momentPolynomial = 1.0;
        double variancePolynomial = 1.0;
        double factorial = 2.0;
        for (int k = 0; k < 20; ++k) {
            covariance += covariancePolynomial / factorial;
            variance += variancePolynomial / (factorial * (k + 3));
            power *= x;
            covariancePolynomial = (x + w) * covariancePolynomial + power;
            momentPolynomial = 2.0 * x * momentPolynomial + power;
            variancePolynomial = (2.0 * x + w) * variancePolynomial
                    + momentPolynomial;
            factorial *= k + 3;
        }
        covariance *= w;
        variance *= 2.0 * w;

        // Restore the interval by splitting its covariance integral into
        // two triangles and a rectangle. This preserves small positive
        // variance without subtracting nearly equal moments, including for
        // negative carry.
        for (int i = 0; i < doublings; ++i) {
            final double mean = exponentialIntegral(x);
            final double e = Math.exp(x);
            final double ew = Math.exp(w);
            final double em1 = Math.expm1(w);
            variance = 0.25 * ((1.0 + e * e * ew) * variance
                    + e * e * em1 * mean * mean
                    + 2.0 * e * mean * covariance);
            covariance = 0.5 * ((1.0 + e * ew) * covariance
                    + e * em1 * mean);
            x *= 2.0;
            w *= 2.0;
        }
        return variance;
    }

    @Override
    public void calculate() {
        QL_REQUIRE(arguments_.averageType == Average.Type.Arithmetic,
                "not an Arithmetic average option");
        QL_REQUIRE(arguments_.exercise.type() == Exercise.Type.European,
                "not an European Option");

        // Prefer start date from option if available, otherwise use
        // constructor parameter. At least one must be specified.
        final Date startDate = !TimeUtils.equals(arguments_.startDate, new Date())
                ? arguments_.startDate : startDate_;
        QL_REQUIRE(!TimeUtils.equals(startDate, new Date()),
                "start date not provided");
        QL_REQUIRE(!TimeUtils.greater(startDate,
                        process_.riskFreeRate().getValue().referenceDate()),
                "start date must be earlier than or equal to reference date");

        final DayCounter rfdc = process_.riskFreeRate().getValue().dayCounter();
        final DayCounter divdc = process_.dividendYield().getValue().dayCounter();
        final double spot = process_.stateVariable().getValue().value();

        // payoff
        QL_REQUIRE(arguments_.payoff instanceof StrikedTypePayoff,
                "non-plain payoff given");
        final StrikedTypePayoff payoff =
                (StrikedTypePayoff) arguments_.payoff;

        // original time to maturity
        final Date maturity = arguments_.exercise.lastDate();
        final double t = rfdc.yearFraction(startDate, maturity);
        // remaining time to maturity
        final double t2 = rfdc.yearFraction(
                process_.riskFreeRate().getValue().referenceDate(), maturity);

        final double strike = payoff.strike();

        final double volatility = process_.blackVolatility().getValue()
                .blackVol(maturity, strike, false);

        final CumulativeNormalDistribution n = new CumulativeNormalDistribution();

        final double riskFreeRate = process_.riskFreeRate().getValue()
                .zeroRate(maturity, rfdc, Compounding.Continuous,
                        Frequency.NO_FREQUENCY, false).rate();
        final double dividendYield = process_.dividendYield().getValue()
                .zeroRate(maturity, divdc, Compounding.Continuous,
                        Frequency.NO_FREQUENCY, false).rate();
        final double b = riskFreeRate - dividendYield;

        final double meanFactor = exponentialIntegral(b * t2);
        final double mean = spot * t2 / t * meanFactor;
        final double discount = Math.exp(-riskFreeRate * t2);
        final double se = mean * discount;

        double x;
        if (t2 < t) {
            QL_REQUIRE(currentAverage_ != null && !currentAverage_.empty()
                            && currentAverage_.getValue().isValid(),
                    "current average required for seasoned option");
            x = strike - ((t - t2) / t) * currentAverage_.getValue().value();
        } else {
            x = strike;
        }

        final double w = volatility * volatility * t2;
        if (w == 0.0 || x <= 0.0) {
            final int typeSign =
                    payoff.optionType() == Option.Type.Call ? 1 : -1;
            results_.value = Math.max(typeSign * (se - x * discount), 0.0);
            return;
        }

        final double v = Math.log1p(levyVariance(b * t2, w)
                / (meanFactor * meanFactor));
        final double stdDev = Math.sqrt(v);
        final double d1 = Math.log(mean / x) / stdDev + 0.5 * stdDev;
        final double d2 = d1 - stdDev;

        if (payoff.optionType() == Option.Type.Call) {
            results_.value = se * n.value(d1) - x * Math.exp(-riskFreeRate * t2)
                    * n.value(d2);
        } else {
            results_.value = se * n.value(d1) - x * Math.exp(-riskFreeRate * t2)
                    * n.value(d2) - se + x * Math.exp(-riskFreeRate * t2);
        }
    }
}
