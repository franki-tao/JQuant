package jquant.cashflows;

import jquant.Handle;
import jquant.Option;
import jquant.Quote;
import jquant.Settings;
import jquant.math.Function;
import jquant.math.integrals.GaussKronrodAdaptive;
import jquant.math.integrals.GaussKronrodNonAdaptive;
import jquant.termstructures.volatility.Sarb.VolatilityType;
import jquant.termstructures.volatility.swaption.SwaptionVolatilityStructure;
import jquant.time.Date;
import jquant.time.TimeUtils;

import static jquant.math.MathUtils.QL_MAX_REAL;

/**
 * CMS coupon pricer using Hagan's static replication with numerical
 * integration of vanilla swaption prices.
 *
 * <p>Ported from QuantLib {@code ql/cashflows/conundrumpricer.hpp/.cpp}
 * ({@code NumericHaganPricer}).
 */
public class NumericHaganPricer extends HaganPricer {

    private double lowerLimit_ = 0.0;
    private double upperLimit_ = 1.0;
    private double stdDeviationsForLowerLimit_;
    private double stdDeviationsForUpperLimit_;
    private final double requiredStdDeviations_ = 8.0;
    private final double precision_;
    private final double refiningIntegrationTolerance_ = 0.0001;
    private final double hardUpperLimit_;

    public NumericHaganPricer(Handle<SwaptionVolatilityStructure> swaptionVol,
                              GFunctionFactory.YieldCurveModel modelOfYieldCurve,
                              Handle<Quote> meanReversion) {
        this(swaptionVol, modelOfYieldCurve, meanReversion,
                0.0, 1.0, 1.0e-6, QL_MAX_REAL);
    }

    public NumericHaganPricer(Handle<SwaptionVolatilityStructure> swaptionVol,
                              GFunctionFactory.YieldCurveModel modelOfYieldCurve,
                              Handle<Quote> meanReversion,
                              double lowerLimit,
                              double upperLimit,
                              double precision,
                              double hardUpperLimit) {
        super(swaptionVol, modelOfYieldCurve, meanReversion);
        lowerLimit_ = lowerLimit;
        upperLimit_ = upperLimit;
        precision_ = precision;
        hardUpperLimit_ = hardUpperLimit;
    }

    public double upperLimit() {
        return upperLimit_;
    }

    public double lowerLimit() {
        return lowerLimit_;
    }

    public double stdDeviations() {
        return stdDeviationsForUpperLimit_;
    }

    private double integrate(double a, double b,
                             final ConundrumIntegrand integrand) {
        double result;
        if (a > 0) {
            // we estimate the actual boundary by testing integrand values
            double upperBoundary = 2.0 * a;
            while (integrand.value(upperBoundary) > precision_)
                upperBoundary *= 2.0;
            // sometimes b < a because of a wrong estimation of b based on stdev
            if (b > a)
                upperBoundary = Math.min(upperBoundary, b);

            GaussKronrodNonAdaptive gaussKronrodNonAdaptive =
                    new GaussKronrodNonAdaptive(precision_, 1000000, 1.0);
            // if the integration interval is wide enough we use the
            // following change variable x -> a + (b-a)*(t/(a-b))^3
            upperBoundary = Math.max(a,
                    Math.min(upperBoundary, hardUpperLimit_));
            if (upperBoundary > 2.0 * a) {
                int k = 3;
                VariableChange variableChange = new VariableChange(integrand,
                        a, upperBoundary, k);
                result = gaussKronrodNonAdaptive.value(variableChange,
                        0.0, 1.0);
            } else {
                result = gaussKronrodNonAdaptive.value(integrand, a,
                        upperBoundary);
            }

            // if the expected precision has not been reached use the old
            // algorithm
            if (!gaussKronrodNonAdaptive.integrationSuccess()) {
                GaussKronrodAdaptive integrator = new GaussKronrodAdaptive(
                        precision_, 100000);
                b = Math.max(a, Math.min(b, hardUpperLimit_));
                result = integrator.value(integrand, a, b);
            }
        } else { // if a < b we use the old algorithm
            b = Math.max(a, Math.min(b, hardUpperLimit_));
            if (swaptionVolatility().currentLink().volatilityType()
                    == VolatilityType.ShiftedLognormal) {
                GaussKronrodAdaptive integrator = new GaussKronrodAdaptive(
                        precision_, 100000);
                result = integrator.value(integrand, a, b);
            } else { // Normal and floorlet
                GaussKronrodNonAdaptive integrator =
                        new GaussKronrodNonAdaptive(precision_, 100000, 1.0);
                result = integrator.value(integrand, a, b);
            }
        }
        return result;
    }

    @Override
    protected double optionletPrice(Option.Type optionType, double strike) {
        ConundrumIntegrand integrand = new ConundrumIntegrand(
                vanillaOptionPricer_, gFunction_, fixingDate_, paymentDate_,
                annuity_, swapRateValue_, strike, optionType);

        stdDeviationsForUpperLimit_ = requiredStdDeviations_;
        stdDeviationsForLowerLimit_ = requiredStdDeviations_;
        double integralValue;
        if (optionType == Option.Type.Call) {
            upperLimit_ = resetUpperLimit(stdDeviationsForUpperLimit_);
            integralValue = integrate(strike, upperLimit_, integrand);
        } else {
            lowerLimit_ = resetLowerLimit(stdDeviationsForLowerLimit_);
            double a = Math.min(strike, lowerLimit_);
            integralValue = integrate(a, strike, integrand);
        }

        double dFdK = integrand.firstDerivativeOfF(strike);
        double swaptionPrice = vanillaOptionPricer_.price(strike, optionType,
                annuity_);

        // v. HAGAN, Conundrums..., formule 2.17a, 2.18a
        return coupon_.accrualPeriod() * (discount_ / annuity_)
                * ((1.0 + dFdK) * swaptionPrice
                + sign(optionType) * integralValue);
    }

    @Override
    public double swapletPrice() {
        Date today = Settings.instance.evaluationDate().Date();
        if (TimeUtils.leq(fixingDate_, today)) {
            // the fixing is determined
            double Rs = coupon_.swapIndex().fixing(fixingDate_, false);
            return (gearing_ * Rs + spread_)
                    * (coupon_.accrualPeriod() * discount_);
        }
        double atmCapletPrice = optionletPrice(Option.Type.Call,
                swapRateValue_);
        double atmFloorletPrice = optionletPrice(Option.Type.Put,
                swapRateValue_);
        return gearing_ * (coupon_.accrualPeriod() * discount_ * swapRateValue_
                + atmCapletPrice - atmFloorletPrice)
                + spreadLegValue_;
    }

    private double resetUpperLimit(double stdDeviationsForUpperLimit) {
        double variance = swaptionVolatility().currentLink().blackVariance(
                fixingDate_, swapTenor_, swapRateValue_);

        if (swaptionVolatility().currentLink().volatilityType()
                == VolatilityType.ShiftedLognormal) {
            return swapRateValue_ * Math.exp(stdDeviationsForUpperLimit
                    * Math.sqrt(variance));
        }
        return swapRateValue_ + stdDeviationsForUpperLimit
                * Math.sqrt(variance);
    }

    private double resetLowerLimit(double stdDeviationsForUpperLimit) {
        double variance = swaptionVolatility().currentLink().blackVariance(
                fixingDate_, swapTenor_, swapRateValue_);

        if (swaptionVolatility().currentLink().volatilityType()
                == VolatilityType.ShiftedLognormal) {
            return lowerLimit_;
        }
        return swapRateValue_ - stdDeviationsForUpperLimit
                * Math.sqrt(variance);
    }

    private static int sign(Option.Type optionType) {
        return optionType == Option.Type.Call ? 1 : -1;
    }

    private static class VariableChange implements Function {
        private final Function f_;
        private final double a_;
        private final double width_;
        private final int k_;

        VariableChange(Function f, double a, double b, int k) {
            f_ = f;
            a_ = a;
            width_ = b - a;
            k_ = k;
        }

        @Override
        public double value(double x) {
            double temp = width_;
            for (int i = 1; i < k_; ++i)
                temp *= x;
            double newVar = a_ + x * temp;
            return f_.value(newVar) * k_ * temp;
        }
    }

    /**
     * Official {@code NumericHaganPricer::ConundrumIntegrand}. The official
     * constructor takes the rate curve but does not use it; it is omitted here.
     */
    private static class ConundrumIntegrand implements Function {
        private final VanillaOptionPricer vanillaOptionPricer_;
        private final double forwardValue_;
        private final double annuity_;
        private final Date fixingDate_;
        private final Date paymentDate_;
        private final double strike_;
        private final Option.Type optionType_;
        private final GFunction gFunction_;

        ConundrumIntegrand(VanillaOptionPricer optionPricer,
                           GFunction gFunction,
                           Date fixingDate,
                           Date paymentDate,
                           double annuity,
                           double forwardValue,
                           double strike,
                           Option.Type optionType) {
            vanillaOptionPricer_ = optionPricer;
            gFunction_ = gFunction;
            fixingDate_ = fixingDate.copy();
            paymentDate_ = paymentDate.copy();
            annuity_ = annuity;
            forwardValue_ = forwardValue;
            strike_ = strike;
            optionType_ = optionType;
        }

        private double functionF(double x) {
            double gx = gFunction_.value(x);
            double gr = gFunction_.value(forwardValue_);
            return (x - strike_) * (gx / gr - 1.0);
        }

        double firstDerivativeOfF(double x) {
            double gx = gFunction_.value(x);
            double gr = gFunction_.value(forwardValue_);
            double g1 = gFunction_.firstDerivative(x);
            return (gx / gr - 1.0) + g1 / gr * (x - strike_);
        }

        private double secondDerivativeOfF(double x) {
            double gr = gFunction_.value(forwardValue_);
            double g1 = gFunction_.firstDerivative(x);
            double g2 = gFunction_.secondDerivative(x);
            return 2.0 * g1 / gr + (x - strike_) * g2 / gr;
        }

        @Override
        public double value(double x) {
            double option = vanillaOptionPricer_.price(x, optionType_,
                    annuity_);
            return option * secondDerivativeOfF(x);
        }
    }
}
