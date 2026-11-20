package jquant.pricingengines.barrier;

import jquant.Barrier;
import jquant.Compounding;
import jquant.EuropeanOption;
import jquant.Option;
import jquant.PartialBarrier;
import jquant.PartialTimeBarrierOption;
import jquant.math.distributions.BivariateCumulativeNormalDistributionDr78;
import jquant.pricingengines.PlainVanillaPayoff;
import jquant.pricingengines.vanilla.AnalyticEuropeanEngine;
import jquant.processes.EulerDiscretization;
import jquant.processes.GeneralizedBlackScholesProcess;
import jquant.time.Frequency;

import static jquant.math.CommonUtil.QL_FAIL;
import static jquant.math.CommonUtil.QL_REQUIRE;

/**
 * Analytic engine for partial-time barrier options.
 *
 * <p>Formulas from Haug, "Option pricing formulas". It doesn't currently
 * cover the case of knock-in partial-time end options.
 *
 * <p>Ported from QuantLib
 * {@code ql/pricingengines/barrier/analyticpartialtimebarrieroptionengine.{hpp,cpp}}.
 */
public class AnalyticPartialTimeBarrierOptionEngine
        extends PartialTimeBarrierOption.engine {

    private final GeneralizedBlackScholesProcess process_;

    public AnalyticPartialTimeBarrierOptionEngine(
            GeneralizedBlackScholesProcess process) {
        process_ = process;
        registerWith(process_);
    }

    @Override
    public void calculate() {
        PlainVanillaPayoff payoff = (arguments_.payoff instanceof PlainVanillaPayoff p)
                ? p : null;
        QL_REQUIRE(payoff != null, "non-plain payoff given");
        QL_REQUIRE(payoff.strike() > 0.0, "strike must be positive");

        double spot = process_.x0();
        QL_REQUIRE(spot > 0.0, "negative or null underlying given");

        // Official `auto tmp_arguments_ = arguments_;`
        PartialTimeBarrierOption.arguments tmpArguments =
                new PartialTimeBarrierOption.arguments();
        tmpArguments.payoff = arguments_.payoff;
        tmpArguments.exercise = arguments_.exercise;
        tmpArguments.barrierType = arguments_.barrierType;
        tmpArguments.barrierRange = arguments_.barrierRange;
        tmpArguments.barrier = arguments_.barrier;
        tmpArguments.rebate = arguments_.rebate;
        tmpArguments.coverEventDate = arguments_.coverEventDate;

        if (payoff.optionType() == Option.Type.Put) {
            double spotSq = spot * spot;
            double callStrike = spotSq / payoff.strike();
            PlainVanillaPayoff callPayoff =
                    new PlainVanillaPayoff(Option.Type.Call, callStrike);
            tmpArguments.barrierType =
                    getSymmetricBarrierType(arguments_.barrierType);
            tmpArguments.barrier = spotSq / arguments_.barrier;
            tmpArguments.payoff = callPayoff;

            // As in the official code, the risk-free curve is passed as the
            // dividend curve and vice versa: the put under the swapped
            // process maps to a call under the original one.
            GeneralizedBlackScholesProcess callProcess =
                    new GeneralizedBlackScholesProcess(
                            process_.stateVariable(),
                            process_.riskFreeRate(),
                            process_.dividendYield(),
                            process_.blackVolatility(),
                            new EulerDiscretization(),
                            false);

            results_.value = payoff.strike() / spot
                    * calculate(tmpArguments, callPayoff, callProcess);
        } else {
            results_.value = calculate(tmpArguments, payoff, process_);
        }
    }

    /** Official {@code getSymmetricBarrierType} lambda. */
    private static Barrier.Type getSymmetricBarrierType(Barrier.Type barrierType) {
        if (barrierType == Barrier.Type.UpIn) {
            return Barrier.Type.DownIn;
        }
        if (barrierType == Barrier.Type.DownIn) {
            return Barrier.Type.UpIn;
        }
        if (barrierType == Barrier.Type.UpOut) {
            return Barrier.Type.DownOut;
        }
        return Barrier.Type.UpOut;
    }

    private double calculate(PartialTimeBarrierOption.arguments arguments,
                             PlainVanillaPayoff payoff,
                             GeneralizedBlackScholesProcess process) {
        if (arguments.barrierType == null) {
            QL_FAIL("unknown barrier type");
        }
        if (arguments.barrierRange == null) {
            QL_FAIL("invalid barrier range");
        }

        Barrier.Type barrierType = arguments.barrierType;
        PartialBarrier.Range barrierRange = arguments.barrierRange;
        double r = process.riskFreeRate().getValue().zeroRate(
                residualTime(), Compounding.Continuous,
                Frequency.NO_FREQUENCY, false).rate();
        double q = process.dividendYield().getValue().zeroRate(
                residualTime(), Compounding.Continuous,
                Frequency.NO_FREQUENCY, false).rate();
        double barrier = arguments.barrier;
        double strike = payoff.strike();

        switch (barrierType) {
            case DownOut:
                switch (barrierRange) {
                    case Start:
                        return CA(1, barrier, strike, r, q);
                    case EndB1:
                        return CoB1(barrier, strike, r, q);
                    case EndB2:
                        return CoB2(Barrier.Type.DownOut, barrier, strike, r, q);
                    default:
                        QL_FAIL("invalid barrier range");
                }
                break;

            case DownIn:
                switch (barrierRange) {
                    case Start:
                        return CIA(1, barrier, strike, r, q, payoff, process);
                    case EndB1:
                    case EndB2:
                        QL_FAIL("Down-and-in partial-time end barrier is not implemented");
                    default:
                        QL_FAIL("invalid barrier range");
                }
                break;

            case UpOut:
                switch (barrierRange) {
                    case Start:
                        return CA(-1, barrier, strike, r, q);
                    case EndB1:
                        return CoB1(barrier, strike, r, q);
                    case EndB2:
                        return CoB2(Barrier.Type.UpOut, barrier, strike, r, q);
                    default:
                        QL_FAIL("invalid barrier range");
                }
                break;

            case UpIn:
                switch (barrierRange) {
                    case Start:
                        return CIA(-1, barrier, strike, r, q, payoff, process);
                    case EndB1:
                    case EndB2:
                        QL_FAIL("Up-and-in partial-time end barrier is not implemented");
                    default:
                        QL_FAIL("invalid barrier range");
                }
                break;

            default:
                QL_FAIL("unknown barrier type");
        }

        return 0.0; // unreachable
    }

    private double CoB2(Barrier.Type barrierType, double barrier,
                        double strike, double r, double q) {
        double result;
        double b = r - q;
        double T = residualTime();
        double S = underlying();
        double mu_ = mu(strike, b);
        double g1_ = g1(barrier, strike, b);
        double g2_ = g2(barrier, strike, b);
        double g3_ = g3(barrier, strike, b);
        double g4_ = g4(barrier, strike, b);
        double e1_ = e1(barrier, strike, b);
        double e2_ = e2(barrier, strike, b);
        double e3_ = e3(barrier, strike, b);
        double e4_ = e4(barrier, strike, b);
        double rho_ = rho();
        double HSMu = HS(S, barrier, 2 * mu_);
        double HSMu1 = HS(S, barrier, 2 * (mu_ + 1));
        double X1 = strike * Math.exp(-r * T);

        if (strike < barrier) {
            switch (barrierType) {
                case DownOut:
                    result = S * Math.exp((b - r) * T);
                    result *= (M(g1_, e1_, rho_) - HSMu1 * M(g3_, -e3_, -rho_));
                    result -= X1 * (M(g2_, e2_, rho_) - HSMu * M(g4_, -e4_, -rho_));
                    return result;

                case UpOut:
                    result = S * Math.exp((b - r) * T);
                    result *= (M(-g1_, -e1_, rho_) - HSMu1 * M(-g3_, e3_, -rho_));
                    result -= X1 * (M(-g2_, -e2_, rho_) - HSMu * M(-g4_, e4_, -rho_));
                    result -= S * Math.exp((b - r) * T)
                            * (M(-d1(strike, b), -e1_, rho_) - HSMu1
                            * M(e3_, -f1(barrier, strike, b), -rho_));
                    result += X1 * (M(-d2(strike, b), -e2_, rho_) - HSMu
                            * M(e4_, -f2(barrier, strike, b), -rho_));
                    return result;

                default:
                    QL_FAIL("invalid barrier type");
            }
        } else {
            QL_FAIL("case of strike>barrier is not implemented for OutEnd B2 type");
        }

        return 0.0; // unreachable
    }

    private double CoB1(double barrier, double strike, double r, double q) {
        double b = r - q;
        double T = residualTime();
        double S = underlying();
        double mu_ = mu(strike, b);
        double g1_ = g1(barrier, strike, b);
        double g2_ = g2(barrier, strike, b);
        double g3_ = g3(barrier, strike, b);
        double g4_ = g4(barrier, strike, b);
        double e1_ = e1(barrier, strike, b);
        double e2_ = e2(barrier, strike, b);
        double e3_ = e3(barrier, strike, b);
        double e4_ = e4(barrier, strike, b);
        double rho_ = rho();
        double HSMu = HS(S, barrier, 2 * mu_);
        double HSMu1 = HS(S, barrier, 2 * (mu_ + 1));
        double X1 = strike * Math.exp(-r * T);

        if (strike > barrier) {
            double result = S * Math.exp((b - r) * T);
            result *= (M(d1(strike, b), e1_, rho_) - HSMu1
                    * M(f1(barrier, strike, b), -e3_, -rho_));
            result -= X1 * (M(d2(strike, b), e2_, rho_) - HSMu
                    * M(f2(barrier, strike, b), -e4_, -rho_));
            return result;
        } else {
            double S1 = S * Math.exp((b - r) * T);
            double result = S1;
            result *= (M(-g1_, -e1_, rho_) - HSMu1 * M(-g3_, e3_, -rho_));
            result -= X1 * (M(-g2_, -e2_, rho_) - HSMu * M(-g4_, e4_, -rho_));
            result -= S1 * (M(-d1(strike, b), -e1_, rho_) - HSMu1
                    * M(-f1(barrier, strike, b), e3_, -rho_));
            result += X1 * (M(-d2(strike, b), -e2_, rho_) - HSMu
                    * M(-f2(barrier, strike, b), e4_, -rho_));
            result += S1 * (M(g1_, e1_, rho_) - HSMu1
                    * M(g3_, -e3_, -rho_));
            result -= X1 * (M(g2_, e2_, rho_) - HSMu
                    * M(g4_, -e4_, -rho_));
            return result;
        }
    }

    // eta = -1: Up-and-In Call
    // eta =  1: Down-and-In Call
    private double CIA(int eta, double barrier, double strike, double r, double q,
                       PlainVanillaPayoff payoff,
                       GeneralizedBlackScholesProcess process) {
        EuropeanOption europeanOption =
                new EuropeanOption(payoff, arguments_.exercise);
        europeanOption.setPricingEngine(new AnalyticEuropeanEngine(process));

        return europeanOption.NPV() - CA(eta, barrier, strike, r, q);
    }

    private double CA(int eta, double barrier, double strike, double r, double q) {
        // Partial-Time-Start-OUT Call Option calculation
        double b = r - q;
        double rho_ = rho();
        double T = residualTime();
        double S = underlying();
        double mu_ = mu(strike, b);
        double e1_ = e1(barrier, strike, b);
        double e2_ = e2(barrier, strike, b);
        double e3_ = e3(barrier, strike, b);
        double e4_ = e4(barrier, strike, b);
        double HSMu = HS(S, barrier, 2 * mu_);
        double HSMu1 = HS(S, barrier, 2 * (mu_ + 1));

        double result = S * Math.exp((b - r) * T);
        result *= (M(d1(strike, b), eta * e1_, eta * rho_) - HSMu1
                * M(f1(barrier, strike, b), eta * e3_, eta * rho_));
        result -= (strike * Math.exp(-r * T)
                * (M(d2(strike, b), eta * e2_, eta * rho_) - HSMu
                * M(f2(barrier, strike, b), eta * e4_, eta * rho_)));
        return result;
    }

    private double underlying() {
        return process_.x0();
    }

    private double residualTime() {
        return process_.time(arguments_.exercise.lastDate());
    }

    private double coverEventTime() {
        return process_.time(arguments_.coverEventDate);
    }

    private double volatility(double t, double strike) {
        return process_.blackVolatility().getValue().blackVol(t, strike);
    }

    private double f1(double barrier, double strike, double b) {
        double S = underlying();
        double T = residualTime();
        double sigma = volatility(T, strike);
        return (Math.log(S / strike) + 2 * Math.log(barrier / S)
                + (b + (sigma * sigma) / 2) * T) / (sigma * Math.sqrt(T));
    }

    private double f2(double barrier, double strike, double b) {
        double T = residualTime();
        return f1(barrier, strike, b) - volatility(T, strike) * Math.sqrt(T);
    }

    private double M(double a, double b, double rho) {
        BivariateCumulativeNormalDistributionDr78 cmlNormDist =
                new BivariateCumulativeNormalDistributionDr78(rho);
        return cmlNormDist.value(a, b);
    }

    private double rho() {
        return Math.sqrt(coverEventTime() / residualTime());
    }

    private double mu(double strike, double b) {
        double vol = volatility(coverEventTime(), strike);
        return (b - (vol * vol) / 2) / (vol * vol);
    }

    private double d1(double strike, double b) {
        double T2 = residualTime();
        double vol = volatility(T2, strike);
        return (Math.log(underlying() / strike) + (b + vol * vol / 2) * T2)
                / (Math.sqrt(T2) * vol);
    }

    private double d2(double strike, double b) {
        double T2 = residualTime();
        double vol = volatility(T2, strike);
        return d1(strike, b) - vol * Math.sqrt(T2);
    }

    private double e1(double barrier, double strike, double b) {
        double T1 = coverEventTime();
        double vol = volatility(T1, strike);
        return (Math.log(underlying() / barrier) + (b + vol * vol / 2) * T1)
                / (Math.sqrt(T1) * vol);
    }

    private double e2(double barrier, double strike, double b) {
        double T1 = coverEventTime();
        double vol = volatility(T1, strike);
        return e1(barrier, strike, b) - vol * Math.sqrt(T1);
    }

    private double e3(double barrier, double strike, double b) {
        double T1 = coverEventTime();
        double vol = volatility(T1, strike);
        return e1(barrier, strike, b)
                + (2 * Math.log(barrier / underlying()) / (vol * Math.sqrt(T1)));
    }

    private double e4(double barrier, double strike, double b) {
        double t = coverEventTime();
        return e3(barrier, strike, b) - volatility(t, strike) * Math.sqrt(t);
    }

    private double g1(double barrier, double strike, double b) {
        double T2 = residualTime();
        double vol = volatility(T2, strike);
        return (Math.log(underlying() / barrier) + (b + vol * vol / 2) * T2)
                / (Math.sqrt(T2) * vol);
    }

    private double g2(double barrier, double strike, double b) {
        double T2 = residualTime();
        double vol = volatility(T2, strike);
        return g1(barrier, strike, b) - vol * Math.sqrt(T2);
    }

    private double g3(double barrier, double strike, double b) {
        double T2 = residualTime();
        double vol = volatility(T2, strike);
        return g1(barrier, strike, b)
                + (2 * Math.log(barrier / underlying()) / (vol * Math.sqrt(T2)));
    }

    private double g4(double barrier, double strike, double b) {
        double T2 = residualTime();
        double vol = volatility(T2, strike);
        return g3(barrier, strike, b) - vol * Math.sqrt(T2);
    }

    private double HS(double S, double H, double power) {
        return Math.pow((H / S), power);
    }
}
