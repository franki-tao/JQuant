package jquant.pricingengines.barrier;

import jquant.Barrier;
import jquant.BarrierOption;
import jquant.Compounding;
import jquant.Exercise;
import jquant.Option;
import jquant.pricingengines.PlainVanillaPayoff;
import jquant.processes.GeneralizedBlackScholesProcess;
import jquant.math.distributions.CumulativeNormalDistribution;
import jquant.time.Date;
import jquant.time.Frequency;

import static jquant.math.CommonUtil.QL_FAIL;
import static jquant.math.CommonUtil.QL_REQUIRE;
import static jquant.math.MathUtils.QL_EPSILON;
import static jquant.math.MathUtils.QL_MAX_REAL;
import static jquant.math.MathUtils.QL_MIN_POSITIVE_REAL;

/**
 * Pricing engine for barrier options using analytical formulae.
 *
 * <p>Ported from QuantLib
 * {@code ql/pricingengines/barrier/analyticbarrierengine.{hpp,cpp}}.
 * The formulas are taken from "Option pricing formulas", E.G. Haug,
 * McGraw-Hill, p.69 and following.
 */
public class AnalyticBarrierEngine extends BarrierOption.engine {
    private final GeneralizedBlackScholesProcess process_;
    private final CumulativeNormalDistribution f_ =
            new CumulativeNormalDistribution();

    public AnalyticBarrierEngine(GeneralizedBlackScholesProcess process) {
        process_ = process;
        registerWith(process_);
    }

    /**
     * {@code power * N(y)}, where {@code power = exp(logPower)}. At low
     * volatility and large carry, {@code power} can overflow while
     * {@code N(y)} underflows even though their product is finite and not
     * negligible; in that case the product is formed in log space.
     */
    private double powerTimesCumulativeNormal(double power, double logPower,
                                              double y) {
        double n = f_.value(y);
        if (n >= QL_MIN_POSITIVE_REAL && Double.isFinite(power)) {
            return power * n;
        }

        double logN;
        if (n >= QL_MIN_POSITIVE_REAL) {
            logN = Math.log(n);
        } else {
            // Log of the asymptotic expansion that CumulativeNormalDistribution
            // uses for very negative y, (26.2.12) in Abramowitz and Stegun,
            // with the log of the Gaussian density taken in closed form.
            double sum = 1.0, ysqr = y * y, i = 1.0, g = 1.0, x, z,
                    a = QL_MAX_REAL, lasta;
            do {
                lasta = a;
                x = (4.0 * i - 3.0) / ysqr;
                z = x * ((4.0 * i - 1.0) / ysqr);
                a = g * (x - z);
                sum -= a;
                g *= z;
                ++i;
                a = Math.abs(a);
            } while (lasta > a && a >= Math.abs(sum * QL_EPSILON));
            logN = -0.5 * ysqr - 0.5 * Math.log(2.0 * Math.PI)
                    - Math.log(-y) + Math.log(sum);
        }
        return Math.exp(logPower + logN);
    }

    @Override
    public void calculate() {
        PlainVanillaPayoff payoff =
                (arguments_.payoff instanceof PlainVanillaPayoff p) ? p : null;
        QL_REQUIRE(payoff != null, "non-plain payoff given");
        QL_REQUIRE(payoff.strike() > 0.0, "strike must be positive");

        QL_REQUIRE(arguments_.exercise.type() == Exercise.Type.European,
                "only european style option are supported");

        double strike = payoff.strike();
        double spot = process_.x0();
        QL_REQUIRE(spot > 0.0, "negative or null underlying given");
        QL_REQUIRE(!triggered(spot), "barrier touched");

        Barrier.Type barrierType = arguments_.barrierType;

        double variance = process_.blackVolatility().getValue().blackVariance(
                arguments_.exercise.lastDate(), strike);
        if (variance < QL_EPSILON) {
            // The underlying follows its forward, and hits the barrier (if at
            // all) when the forward reaches it; a spot on the barrier hits it
            // now.
            double forward = spot * dividendDiscount() / riskFreeDiscount();
            boolean down = barrierType == Barrier.Type.DownIn
                    || barrierType == Barrier.Type.DownOut;
            boolean hit = (spot == barrier())
                    || (down ? forward <= barrier() : forward >= barrier());
            double vanilla = riskFreeDiscount() * payoff.value(forward);
            if (barrierType == Barrier.Type.DownIn
                    || barrierType == Barrier.Type.UpIn) {
                results_.value = hit ? vanilla : rebate() * riskFreeDiscount();
            } else if (hit) {
                // A knock-out rebate is paid when the barrier is hit.
                double logHS = Math.log(barrier() / spot);
                double hitTime = (logHS == 0.0)
                        ? 0.0
                        : logHS / (riskFreeRate() - dividendYield());
                results_.value = rebate() * Math.exp(-riskFreeRate() * hitTime);
            } else {
                results_.value = vanilla;
            }
            return;
        }

        switch (payoff.optionType()) {
            case Call:
                switch (barrierType) {
                    case DownIn:
                        if (strike >= barrier()) {
                            results_.value = C(1, 1) + E(1);
                        } else {
                            results_.value = A(1) - B(1) + D(1, 1) + E(1);
                        }
                        break;
                    case UpIn:
                        if (strike >= barrier()) {
                            results_.value = A(1) + E(-1);
                        } else {
                            results_.value = B(1) - C(-1, 1) + D(-1, 1) + E(-1);
                        }
                        break;
                    case DownOut:
                        if (strike >= barrier()) {
                            results_.value = A(1) - C(1, 1) + F(1);
                        } else {
                            results_.value = B(1) - D(1, 1) + F(1);
                        }
                        break;
                    case UpOut:
                        if (strike >= barrier()) {
                            results_.value = F(-1);
                        } else {
                            results_.value = A(1) - B(1) + C(-1, 1)
                                    - D(-1, 1) + F(-1);
                        }
                        break;
                }
                break;
            case Put:
                switch (barrierType) {
                    case DownIn:
                        if (strike >= barrier()) {
                            results_.value = B(-1) - C(1, -1) + D(1, -1) + E(1);
                        } else {
                            results_.value = A(-1) + E(1);
                        }
                        break;
                    case UpIn:
                        if (strike >= barrier()) {
                            results_.value = A(-1) - B(-1) + D(-1, -1) + E(-1);
                        } else {
                            results_.value = C(-1, -1) + E(-1);
                        }
                        break;
                    case DownOut:
                        if (strike >= barrier()) {
                            results_.value = A(-1) - B(-1) + C(1, -1)
                                    - D(1, -1) + F(1);
                        } else {
                            results_.value = F(1);
                        }
                        break;
                    case UpOut:
                        if (strike >= barrier()) {
                            results_.value = B(-1) - D(-1, -1) + F(-1);
                        } else {
                            results_.value = A(-1) - C(-1, -1) + F(-1);
                        }
                        break;
                }
                break;
            default:
                QL_FAIL("unknown type");
        }
    }

    private double underlying() {
        return process_.x0();
    }

    private double strike() {
        PlainVanillaPayoff payoff =
                (arguments_.payoff instanceof PlainVanillaPayoff p) ? p : null;
        QL_REQUIRE(payoff != null, "non-plain payoff given");
        return payoff.strike();
    }

    private double volatility() {
        return process_.blackVolatility().getValue().blackVol(
                arguments_.exercise.lastDate(), strike());
    }

    private double stdDeviation() {
        return Math.sqrt(process_.blackVolatility().getValue().blackVariance(
                arguments_.exercise.lastDate(), strike()));
    }

    private double barrier() {
        return arguments_.barrier;
    }

    private double rebate() {
        return arguments_.rebate;
    }

    private double riskFreeRate() {
        return process_.riskFreeRate().getValue().zeroRate(
                arguments_.exercise.lastDate(),
                process_.riskFreeRate().getValue().dayCounter(),
                Compounding.Continuous, Frequency.NO_FREQUENCY, false).rate();
    }

    private double riskFreeDiscount() {
        return process_.riskFreeRate().getValue().discount(
                arguments_.exercise.lastDate());
    }

    private double dividendYield() {
        return process_.dividendYield().getValue().zeroRate(
                arguments_.exercise.lastDate(),
                process_.dividendYield().getValue().dayCounter(),
                Compounding.Continuous, Frequency.NO_FREQUENCY, false).rate();
    }

    private double dividendDiscount() {
        return process_.dividendYield().getValue().discount(
                arguments_.exercise.lastDate());
    }

    private double mu() {
        double vol = volatility();
        return (riskFreeRate() - dividendYield()) / (vol * vol) - 0.5;
    }

    private double muSigma() {
        return (1 + mu()) * stdDeviation();
    }

    private double A(double phi) {
        double x1 = Math.log(underlying() / strike()) / stdDeviation()
                + muSigma();
        double N1 = f_.value(phi * x1);
        double N2 = f_.value(phi * (x1 - stdDeviation()));

        return phi * (underlying() * dividendDiscount() * N1
                - strike() * riskFreeDiscount() * N2);
    }

    private double B(double phi) {
        double x2 = Math.log(underlying() / barrier()) / stdDeviation()
                + muSigma();
        double N1 = f_.value(phi * x2);
        double N2 = f_.value(phi * (x2 - stdDeviation()));
        return phi * (underlying() * dividendDiscount() * N1
                - strike() * riskFreeDiscount() * N2);
    }

    private double C(double eta, double phi) {
        double HS = barrier() / underlying();
        double logHS = Math.log(HS);
        double powHS0 = Math.pow(HS, 2 * mu());
        double powHS1 = powHS0 * HS * HS;
        double y1 = Math.log(barrier() * HS / strike()) / stdDeviation()
                + muSigma();
        double term1 = powerTimesCumulativeNormal(powHS1,
                (2 * mu() + 2) * logHS, eta * y1);
        double term2 = powerTimesCumulativeNormal(powHS0,
                2 * mu() * logHS, eta * (y1 - stdDeviation()));
        return phi * (underlying() * dividendDiscount() * term1
                - strike() * riskFreeDiscount() * term2);
    }

    private double D(double eta, double phi) {
        double HS = barrier() / underlying();
        double logHS = Math.log(HS);
        double powHS0 = Math.pow(HS, 2 * mu());
        double powHS1 = powHS0 * HS * HS;
        double y2 = Math.log(barrier() / underlying()) / stdDeviation()
                + muSigma();
        double term1 = powerTimesCumulativeNormal(powHS1,
                (2 * mu() + 2) * logHS, eta * y2);
        double term2 = powerTimesCumulativeNormal(powHS0,
                2 * mu() * logHS, eta * (y2 - stdDeviation()));
        return phi * (underlying() * dividendDiscount() * term1
                - strike() * riskFreeDiscount() * term2);
    }

    private double E(double eta) {
        if (rebate() > 0) {
            double HS = barrier() / underlying();
            double powHS0 = Math.pow(HS, 2 * mu());
            double x2 = Math.log(underlying() / barrier()) / stdDeviation()
                    + muSigma();
            double y2 = Math.log(barrier() / underlying()) / stdDeviation()
                    + muSigma();
            double N1 = f_.value(eta * (x2 - stdDeviation()));
            double term2 = powerTimesCumulativeNormal(powHS0,
                    2 * mu() * Math.log(HS),
                    eta * (y2 - stdDeviation()));
            return rebate() * riskFreeDiscount() * (N1 - term2);
        } else {
            return 0.0;
        }
    }

    private double F(double eta) {
        if (rebate() > 0) {
            double m = mu();
            double vol = volatility();
            double lambda = Math.sqrt(m * m
                    + 2.0 * riskFreeRate() / (vol * vol));
            double HS = barrier() / underlying();
            double powHSplus = Math.pow(HS, m + lambda);
            double powHSminus = Math.pow(HS, m - lambda);

            double sigmaSqrtT = stdDeviation();
            double z = Math.log(barrier() / underlying()) / sigmaSqrtT
                    + lambda * sigmaSqrtT;

            double logHS = Math.log(HS);
            double term1 = powerTimesCumulativeNormal(powHSplus,
                    (m + lambda) * logHS, eta * z);
            double term2 = powerTimesCumulativeNormal(powHSminus,
                    (m - lambda) * logHS,
                    eta * (z - 2.0 * lambda * sigmaSqrtT));
            return rebate() * (term1 + term2);
        } else {
            return 0.0;
        }
    }
}
