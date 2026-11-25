package jquant.cashflows;

import jquant.Handle;
import jquant.Option;
import jquant.Quote;
import jquant.Settings;
import jquant.math.distributions.CumulativeNormalDistribution;
import jquant.termstructures.volatility.Sarb.VolatilityType;
import jquant.termstructures.volatility.swaption.SwaptionVolatilityStructure;
import jquant.time.Date;
import jquant.time.TimeUtils;

/**
 * CMS coupon pricer using Hagan's analytic static replication.
 *
 * <p>Ported from QuantLib {@code ql/cashflows/conundrumpricer.hpp/.cpp}
 * ({@code AnalyticHaganPricer}).
 */
public class AnalyticHaganPricer extends HaganPricer {

    public AnalyticHaganPricer(Handle<SwaptionVolatilityStructure> swaptionVol,
                               GFunctionFactory.YieldCurveModel modelOfYieldCurve,
                               Handle<Quote> meanReversion) {
        super(swaptionVol, modelOfYieldCurve, meanReversion);
    }

    // Hagan, 3.5b, 3.5c
    @Override
    protected double optionletPrice(Option.Type optionType, double strike) {
        double variance = swaptionVolatility().currentLink().blackVariance(
                fixingDate_, swapTenor_, swapRateValue_);
        double firstDerivativeOfGAtForwardValue =
                gFunction_.firstDerivative(swapRateValue_);
        double price = 0.0;

        double CK = vanillaOptionPricer_.price(strike, optionType, annuity_);
        price += (discount_ / annuity_) * CK;

        int sign = optionType == Option.Type.Call ? 1 : -1;
        if (swaptionVolatility().currentLink().volatilityType()
                == VolatilityType.ShiftedLognormal) {
            double sqrtSigma2T = Math.sqrt(variance);
            double lnRoverK = Math.log(swapRateValue_ / strike);
            double d32 = (lnRoverK + 1.5 * variance) / sqrtSigma2T;
            double d12 = (lnRoverK + 0.5 * variance) / sqrtSigma2T;
            double dminus12 = (lnRoverK - 0.5 * variance) / sqrtSigma2T;

            CumulativeNormalDistribution cumulativeOfNormal =
                    new CumulativeNormalDistribution();
            double n32 = cumulativeOfNormal.value(sign * d32);
            double n12 = cumulativeOfNormal.value(sign * d12);
            double nminus12 = cumulativeOfNormal.value(sign * dminus12);

            price += sign * firstDerivativeOfGAtForwardValue * annuity_
                    * swapRateValue_
                    * (swapRateValue_ * Math.exp(variance) * n32
                    - (swapRateValue_ + strike) * n12
                    + strike * nminus12);
        } else {
            double sqrtSigma2T = Math.sqrt(variance);
            double d = (swapRateValue_ - strike) / sqrtSigma2T;

            CumulativeNormalDistribution cumulativeOfNormal =
                    new CumulativeNormalDistribution();
            double n = cumulativeOfNormal.value(sign * d);
            price += sign * firstDerivativeOfGAtForwardValue * annuity_
                    * variance * n;
        }

        price *= coupon_.accrualPeriod();
        return price;
    }

    // Hagan 3.4c
    @Override
    public double swapletPrice() {
        Date today = Settings.instance.evaluationDate().Date();
        if (TimeUtils.leq(fixingDate_, today)) {
            // the fixing is determined
            double Rs = coupon_.swapIndex().fixing(fixingDate_, false);
            return (gearing_ * Rs + spread_)
                    * (coupon_.accrualPeriod() * discount_);
        }
        double variance = swaptionVolatility().currentLink().blackVariance(
                fixingDate_, swapTenor_, swapRateValue_);
        double firstDerivativeOfGAtForwardValue =
                gFunction_.firstDerivative(swapRateValue_);
        double price = 0.0;
        price += discount_ * swapRateValue_;
        if (swaptionVolatility().currentLink().volatilityType()
                == VolatilityType.ShiftedLognormal) {
            price += firstDerivativeOfGAtForwardValue * annuity_
                    * swapRateValue_ * swapRateValue_
                    * (Math.exp(variance) - 1.0);
        } else {
            price += firstDerivativeOfGAtForwardValue * annuity_ * variance;
        }
        return (gearing_ * price + spread_ * discount_)
                * coupon_.accrualPeriod();
    }
}
