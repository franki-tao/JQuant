package jquant.pricingengines.barrier;

import jquant.Barrier;
import jquant.Compounding;
import jquant.Handle;
import jquant.Option;
import jquant.Quote;
import jquant.TwoAssetBarrierOption;
import jquant.math.distributions.BivariateCumulativeNormalDistributionDr78;
import jquant.math.distributions.CumulativeNormalDistribution;
import jquant.pricingengines.PlainVanillaPayoff;
import jquant.processes.GeneralizedBlackScholesProcess;
import jquant.time.Frequency;

import static jquant.math.CommonUtil.QL_FAIL;
import static jquant.math.CommonUtil.QL_REQUIRE;

/**
 * Analytic engine for barrier options on two assets.
 *
 * <p>The formulas by Heynen and Kat are taken from Haug, "Option pricing
 * formulas".
 *
 * <p>Ported from QuantLib
 * {@code ql/pricingengines/barrier/analytictwoassetbarrierengine.{hpp,cpp}}.
 */
public class AnalyticTwoAssetBarrierEngine extends TwoAssetBarrierOption.engine {

    private final GeneralizedBlackScholesProcess process1_;
    private final GeneralizedBlackScholesProcess process2_;
    private final Handle<Quote> rho_;

    public AnalyticTwoAssetBarrierEngine(
            GeneralizedBlackScholesProcess process1,
            GeneralizedBlackScholesProcess process2,
            Handle<Quote> rho) {
        process1_ = process1;
        process2_ = process2;
        rho_ = rho;
        registerWith(process1_);
        registerWith(process2_);
        if (rho_ != null) {
            registerWith(rho_.get());
        }
    }

    @Override
    public void calculate() {
        PlainVanillaPayoff payoff = (arguments_.payoff instanceof PlainVanillaPayoff p)
                ? p : null;
        QL_REQUIRE(payoff != null, "non-plain payoff given");
        QL_REQUIRE(payoff.strike() > 0.0, "strike must be positive");

        double spot2 = process2_.x0();
        // option is triggered by S2
        QL_REQUIRE(spot2 > 0.0, "negative or null underlying given");
        QL_REQUIRE(!triggered(spot2), "barrier touched");

        Barrier.Type barrierType = arguments_.barrierType;

        switch (payoff.optionType()) {
            case Call:
                switch (barrierType) {
                    case DownOut:
                        results_.value = A(1, -1) + B(1, -1);
                        break;
                    case UpOut:
                        results_.value = A(1, 1) + B(1, 1);
                        break;
                    case DownIn:
                        results_.value = call() - (A(1, -1) + B(1, -1));
                        break;
                    case UpIn:
                        results_.value = call() - (A(1, 1) + B(1, 1));
                        break;
                    default:
                        QL_FAIL("unknown type");
                }
                break;
            case Put:
                switch (barrierType) {
                    case DownOut:
                        results_.value = A(-1, -1) + B(-1, -1);
                        break;
                    case UpOut:
                        results_.value = A(-1, 1) + B(-1, 1);
                        break;
                    case DownIn:
                        results_.value = put() - (A(-1, -1) + B(-1, -1));
                        break;
                    case UpIn:
                        results_.value = put() - (A(-1, 1) + B(-1, 1));
                        break;
                    default:
                        QL_FAIL("unknown type");
                }
                break;
            default:
                QL_FAIL("unknown type");
        }
    }

    private double underlying1() {
        return process1_.x0();
    }

    private double underlying2() {
        return process2_.x0();
    }

    private double strike() {
        PlainVanillaPayoff payoff = (arguments_.payoff instanceof PlainVanillaPayoff p)
                ? p : null;
        QL_REQUIRE(payoff != null, "non-plain payoff given");
        return payoff.strike();
    }

    private double residualTime() {
        return process1_.time(arguments_.exercise.lastDate());
    }

    private double volatility1() {
        return process1_.blackVolatility().getValue()
                .blackVol(residualTime(), strike());
    }

    private double volatility2() {
        return process2_.blackVolatility().getValue()
                .blackVol(residualTime(), strike());
    }

    private double barrier() {
        return arguments_.barrier;
    }

    private double rho() {
        return rho_.getValue().value();
    }

    private double riskFreeRate() {
        return process1_.riskFreeRate().getValue().zeroRate(
                residualTime(), Compounding.Continuous,
                Frequency.NO_FREQUENCY, false).rate();
    }

    private double dividendYield1() {
        return process1_.dividendYield().getValue().zeroRate(
                residualTime(), Compounding.Continuous,
                Frequency.NO_FREQUENCY, false).rate();
    }

    private double dividendYield2() {
        return process2_.dividendYield().getValue().zeroRate(
                residualTime(), Compounding.Continuous,
                Frequency.NO_FREQUENCY, false).rate();
    }

    private double costOfCarry1() {
        return riskFreeRate() - dividendYield1();
    }

    private double costOfCarry2() {
        return riskFreeRate() - dividendYield2();
    }

    private double d1() {
        return (Math.log(underlying1() / strike())
                + (mu(costOfCarry1(), volatility1())
                + volatility1() * volatility1()) * residualTime())
                / (volatility1() * Math.sqrt(residualTime()));
    }

    private double d2() {
        return d1() - volatility1() * Math.sqrt(residualTime());
    }

    private double d3() {
        return d1() + (2 * rho() * Math.log(barrier() / underlying2()))
                / (volatility2() * Math.sqrt(residualTime()));
    }

    private double d4() {
        return d2() + (2 * rho() * Math.log(barrier() / underlying2()))
                / (volatility2() * Math.sqrt(residualTime()));
    }

    private double e1() {
        return (Math.log(barrier() / underlying2())
                - (mu(costOfCarry2(), volatility2())
                + rho() * volatility1() * volatility2()) * residualTime())
                / (volatility2() * Math.sqrt(residualTime()));
    }

    private double e2() {
        return e1() + rho() * volatility1() * Math.sqrt(residualTime());
    }

    private double e3() {
        return e1() - (2 * Math.log(barrier() / underlying2()))
                / (volatility2() * Math.sqrt(residualTime()));
    }

    private double e4() {
        return e2() - (2 * Math.log(barrier() / underlying2()))
                / (volatility2() * Math.sqrt(residualTime()));
    }

    private double mu(double b, double vol) {
        return b - (vol * vol) / 2;
    }

    private double call() {
        CumulativeNormalDistribution nd = new CumulativeNormalDistribution();
        return underlying1() * Math.exp(-dividendYield1() * residualTime())
                * nd.value(d1())
                - strike() * Math.exp(-riskFreeRate() * residualTime())
                * nd.value(d2());
    }

    private double put() {
        CumulativeNormalDistribution nd = new CumulativeNormalDistribution();
        return strike() * Math.exp(-riskFreeRate() * residualTime())
                * nd.value(-d2())
                - underlying1() * Math.exp(-dividendYield1() * residualTime())
                * nd.value(-d1());
    }

    private double A(double eta, double phi) {
        double S1 = underlying1(), S2 = underlying2();
        double b1 = costOfCarry1(), b2 = costOfCarry2();
        double r = riskFreeRate();
        double T = residualTime();
        double H = barrier(), X = strike();
        double sigma1 = volatility1(), sigma2 = volatility2();
        double rho = rho_ == null ? Double.NaN : rho_.getValue().value();

        double mu1 = b1 - sigma1 * sigma1 / 2.0;
        double mu2 = b2 - sigma2 * sigma2 / 2.0;

        double d1 = (Math.log(S1 / X) + (mu1 + sigma1 * sigma1) * T)
                / (sigma1 * Math.sqrt(T));
        double d2 = d1 - sigma1 * Math.sqrt(T);
        double d3 = d1 + (2 * rho * Math.log(H / S2))
                / (sigma2 * Math.sqrt(T));
        double d4 = d2 + (2 * rho * Math.log(H / S2))
                / (sigma2 * Math.sqrt(T));

        double e1 = (Math.log(H / S2) - (mu2 + rho * sigma1 * sigma2) * T)
                / (sigma2 * Math.sqrt(T));
        double e2 = e1 + rho * sigma1 * Math.sqrt(T);
        double e3 = e1 - (2 * Math.log(H / S2)) / (sigma2 * Math.sqrt(T));
        double e4 = e2 - (2 * Math.log(H / S2)) / (sigma2 * Math.sqrt(T));

        double w =
                eta * S1 * Math.exp((b1 - r) * T)
                        * (M(eta * d1, phi * e1, -eta * phi * rho)
                        - Math.exp((2 * (mu2 + rho * sigma1 * sigma2)
                        * Math.log(H / S2)) / (sigma2 * sigma2))
                        * M(eta * d3, phi * e3, -eta * phi * rho))

                        - eta * X * Math.exp(-r * T)
                        * (M(eta * d2, phi * e2, -eta * phi * rho)
                        - Math.exp((2 * mu2 * Math.log(H / S2)) / (sigma2 * sigma2))
                        * M(eta * d4, phi * e4, -eta * phi * rho));

        return w;
    }

    private double B(double eta, double phi) {
        return 0.0;
    }

    private double M(double m_a, double m_b, double rho) {
        BivariateCumulativeNormalDistributionDr78 f =
                new BivariateCumulativeNormalDistributionDr78(rho);
        return f.value(m_a, m_b);
    }
}
