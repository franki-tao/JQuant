package jquant.pricingengines.vanilla;

import jquant.processes.GeneralizedBlackScholesProcess;
import jquant.termstructures.volatility.equityfx.BlackConstantVol;
import jquant.termstructures.volatility.equityfx.BlackVolTermStructure;
import jquant.time.Date;
import jquant.time.DayCounter;
import org.apache.commons.math3.complex.Complex;

import static jquant.math.CommonUtil.QL_REQUIRE;

/**
 * FFT pricing engine for vanilla options under a Black-Scholes process.
 *
 * <p>https://github.com/lballabio/QuantLib/blob/master/ql/experimental/variancegamma/fftvanillaengine.cpp
 */
public class FFTVanillaEngine extends FFTEngine {
    private double dividendDiscount_;
    private double riskFreeDiscount_;
    private double t_;
    private double var_;

    public FFTVanillaEngine(GeneralizedBlackScholesProcess process) {
        this(process, 0.001);
    }

    public FFTVanillaEngine(GeneralizedBlackScholesProcess process, double logStrikeSpacing) {
        super(process, logStrikeSpacing);
    }

    @Override
    public FFTEngine clone() {
        return new FFTVanillaEngine(
                (GeneralizedBlackScholesProcess) process_, lambda_);
    }

    @Override
    protected void precalculateExpiry(Date d) {
        GeneralizedBlackScholesProcess process =
                (GeneralizedBlackScholesProcess) process_;

        dividendDiscount_ = process.dividendYield().getValue().discount(d, false);
        riskFreeDiscount_ = process.riskFreeRate().getValue().discount(d, false);

        DayCounter rfdc = process.riskFreeRate().getValue().dayCounter();
        t_ = rfdc.yearFraction(process.riskFreeRate().getValue().referenceDate(),
                d, new Date(), new Date());

        BlackVolTermStructure volatility = process.blackVolatility().getValue();
        QL_REQUIRE(volatility instanceof BlackConstantVol,
                "Constant volatility required");
        BlackConstantVol constVol = (BlackConstantVol) volatility;
        // Official code calls blackVol(0.0, 0.0). BlackConstantVol's minimum
        // strike is Double.MIN_VALUE, so a zero strike fails checkStrike;
        // blackVolImpl ignores the strike, so any in-range strike is
        // equivalent.
        double vol = constVol.blackVol(0.0, 1.0, false);
        var_ = vol * vol;
    }

    @Override
    protected Complex complexFourierTransform(Complex u) {
        Complex i1 = new Complex(0, 1);

        double s = process_.x0();

        Complex exponent = i1.multiply(u)
                .multiply(Math.log(s) - (var_ * t_) / 2.0)
                .subtract(u.multiply(u).multiply((var_ * t_) / 2.0));
        Complex phi = exponent.exp();
        phi = phi.multiply(new Complex(dividendDiscount_ / riskFreeDiscount_)
                .pow(i1.multiply(u)));
        return phi;
    }

    @Override
    protected double discountFactor(Date d) {
        GeneralizedBlackScholesProcess process =
                (GeneralizedBlackScholesProcess) process_;
        return process.riskFreeRate().getValue().discount(d, false);
    }

    @Override
    protected double dividendYield(Date d) {
        GeneralizedBlackScholesProcess process =
                (GeneralizedBlackScholesProcess) process_;
        return process.dividendYield().getValue().discount(d, false);
    }
}
