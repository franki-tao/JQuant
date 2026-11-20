package jquant.pricingengines;

import jquant.Option;
import jquant.math.Function;
import jquant.math.distributions.CumulativeNormalDistribution;
import jquant.math.distributions.InverseCumulativeNormal;
import jquant.math.solvers1d.Brent;
import jquant.quotes.DeltaVolQuote;

import static jquant.math.CommonUtil.QL_FAIL;
import static jquant.math.CommonUtil.QL_REQUIRE;
import static jquant.math.MathUtils.QL_EPSILON;

/**
 * Black delta calculator class.
 *
 * <p>Java port of {@code BlackDeltaCalculator} from
 * {@code ql/pricingengines/blackdeltacalculator.{hpp,cpp}}. It supports the
 * FX delta conventions (spot, forward and premium-adjusted) and ATM types.
 *
 * <p>Instead of a volatility it uses the standard deviation, i.e.
 * volatility*sqrt(timeToMaturity).
 */
public class BlackDeltaCalculator {

    private DeltaVolQuote.DeltaType dt_;
    private Option.Type ot_;
    private final double dDiscount_;
    private final double fDiscount_;
    private final double stdDev_;
    private final double spot_;
    private final double forward_;
    private int phi_;
    private final double fExpPos_;
    private final double fExpNeg_;

    public BlackDeltaCalculator(Option.Type ot,
                                DeltaVolQuote.DeltaType dt,
                                double spot,
                                double dDiscount,   // domestic discount
                                double fDiscount,   // foreign discount
                                double stdDev) {
        dt_ = dt;
        ot_ = ot;
        dDiscount_ = dDiscount;
        fDiscount_ = fDiscount;
        stdDev_ = stdDev;
        spot_ = spot;
        forward_ = spot * fDiscount / dDiscount;
        phi_ = ot_ == Option.Type.Call ? 1 : -1;

        QL_REQUIRE(spot_ > 0.0,
                "positive spot value required: " + spot_ + " not allowed");
        QL_REQUIRE(dDiscount_ > 0.0,
                "positive domestic discount factor required: "
                        + dDiscount_ + " not allowed");
        QL_REQUIRE(fDiscount_ > 0.0,
                "positive foreign discount factor required: "
                        + fDiscount_ + " not allowed");
        QL_REQUIRE(stdDev_ >= 0.0,
                "non-negative standard deviation required: "
                        + stdDev_ + " not allowed");

        fExpPos_ = forward_ * Math.exp(0.5 * stdDev_ * stdDev_);
        fExpNeg_ = forward_ * Math.exp(-0.5 * stdDev_ * stdDev_);
    }

    /** Computes the option delta for a given strike. */
    public double deltaFromStrike(double strike) {

        QL_REQUIRE(strike >= 0.0,
                "positive strike value required: " + strike + " not allowed");

        double res = 0.0;

        switch (dt_) {
            case Spot:
                res = phi_ * fDiscount_ * cumD1(strike);
                break;

            case Fwd:
                res = phi_ * cumD1(strike);
                break;

            case PaSpot:
                res = phi_ * fDiscount_ * cumD2(strike) * strike / forward_;
                break;

            case PaFwd:
                res = phi_ * cumD2(strike) * strike / forward_;
                break;

            default:
                QL_FAIL("invalid delta type");
        }
        return res;
    }

    /** Computes the strike corresponding to a given delta. */
    public double strikeFromDelta(double delta) {
        return strikeFromDelta(delta, dt_);
    }

    private double strikeFromDelta(double delta, DeltaVolQuote.DeltaType dt) {
        double res = 0.0;
        double arg = 0.0;
        InverseCumulativeNormal invNorm = new InverseCumulativeNormal();

        QL_REQUIRE(delta * phi_ >= 0.0,
                "Option type and delta are incoherent.");

        switch (dt) {
            case Spot:
                QL_REQUIRE(Math.abs(delta) <= fDiscount_,
                        "Spot delta out of range.");

                arg = -phi_ * invNorm.value(phi_ * delta / fDiscount_) * stdDev_
                        + 0.5 * stdDev_ * stdDev_;
                res = forward_ * Math.exp(arg);
                break;

            case Fwd:
                QL_REQUIRE(Math.abs(delta) <= 1.0,
                        "Forward delta out of range.");

                arg = -phi_ * invNorm.value(phi_ * delta) * stdDev_
                        + 0.5 * stdDev_ * stdDev_;
                res = forward_ * Math.exp(arg);
                break;

            case PaSpot:
            case PaFwd: {
                // This has to be solved numerically. One of the
                // problems is that the premium adjusted call delta is
                // not monotonic in strike, such that two solutions
                // might occur. The one right to the max of the delta is
                // considered to be the correct strike.  Some proper
                // interval bounds for the strike need to be chosen, the
                // numerics can otherwise be very unreliable and
                // unstable.  Brent over Newton is used, since the
                // interval can be specified explicitly and we cannot
                // run into the area on the left of the maximum.  The
                // put delta doesn't have this property and can be
                // solved without any problems, but also numerically.
                Function f = strike -> deltaFromStrike(strike) - delta;

                Brent solver = new Brent();
                solver.setMaxEvaluations(1000);
                double accuracy = 1.0e-10;

                double rightLimit;
                double leftLimit;

                // Strike of not premium adjusted is always to the right
                // of premium adjusted
                if (dt == DeltaVolQuote.DeltaType.PaSpot) {
                    rightLimit = strikeFromDelta(delta, DeltaVolQuote.DeltaType.Spot);
                } else {
                    rightLimit = strikeFromDelta(delta, DeltaVolQuote.DeltaType.Fwd);
                }

                if (phi_ < 0) { // if put
                    res = solver.solve(f, accuracy, rightLimit, 0.0, spot_ * 100.0);
                    break;
                } else {
                    // find out the left limit which is the strike
                    // corresponding to the value where premium adjusted
                    // deltas have their maximum
                    Function g = strike -> cumD2(strike) * stdDev_ - nD2(strike);

                    leftLimit = solver.solve(g, accuracy, rightLimit * 0.5,
                            0.0, rightLimit);

                    double guess = leftLimit + (rightLimit - leftLimit) * 0.5;

                    res = solver.solve(f, accuracy, guess, leftLimit, rightLimit);
                } // end if phi<0 else

                break;
            }

            default:
                QL_FAIL("invalid delta type");
        }

        return res;
    }

    /** Calculates the at-the-money strike for the given ATM convention. */
    public double atmStrike(DeltaVolQuote.AtmType atmT) {

        double res = 0.0;

        switch (atmT) {
            case AtmSpot:
                res = spot_;
                break;

            case AtmDeltaNeutral:
                if (dt_ == DeltaVolQuote.DeltaType.Spot
                        || dt_ == DeltaVolQuote.DeltaType.Fwd) {
                    res = fExpPos_;
                } else {
                    res = fExpNeg_;
                }
                break;

            case AtmFwd:
                res = forward_;
                break;

            case AtmGammaMax:
            case AtmVegaMax:
                res = fExpPos_;
                break;

            case AtmPutCall50:
                QL_REQUIRE(dt_ == DeltaVolQuote.DeltaType.Fwd,
                        "|PutDelta|=CallDelta=0.50 only possible for forward delta.");
                res = fExpPos_;
                break;

            default:
                QL_FAIL("invalid atm type");
        }

        return res;
    }

    private double cumD1(double strike) {

        double d1_;
        double cum_d1_pos_ = 1.0; // N(d1)
        double cum_d1_neg_ = 0.0; // N(-d1)

        CumulativeNormalDistribution f = new CumulativeNormalDistribution();

        if (stdDev_ >= QL_EPSILON) {
            if (strike > 0) {
                d1_ = Math.log(forward_ / strike) / stdDev_ + 0.5 * stdDev_;
                return f.value(phi_ * d1_);
            }
        } else {
            if (forward_ < strike) {
                cum_d1_pos_ = 0.0;
                cum_d1_neg_ = 1.0;
            } else if (forward_ == strike) {
                d1_ = 0.5 * stdDev_;
                return f.value(phi_ * d1_);
            }
        }

        if (phi_ > 0) { // if Call
            return cum_d1_pos_;
        } else {
            return cum_d1_neg_;
        }
    }

    private double nD1(double strike) {

        double d1_;
        double n_d1_ = 0.0; // n(d1)

        if (stdDev_ >= QL_EPSILON) {
            if (strike > 0) {
                d1_ = Math.log(forward_ / strike) / stdDev_ + 0.5 * stdDev_;
                CumulativeNormalDistribution f = new CumulativeNormalDistribution();
                n_d1_ = f.derivative(d1_);
            }
        }

        return n_d1_;
    }

    private double cumD2(double strike) {

        double d2_;
        double cum_d2_pos_ = 1.0; // N(d2)
        double cum_d2_neg_ = 0.0; // N(-d2)

        CumulativeNormalDistribution f = new CumulativeNormalDistribution();

        if (stdDev_ >= QL_EPSILON) {
            if (strike > 0) {
                d2_ = Math.log(forward_ / strike) / stdDev_ - 0.5 * stdDev_;
                return f.value(phi_ * d2_);
            }
        } else {
            if (forward_ < strike) {
                cum_d2_pos_ = 0.0;
                cum_d2_neg_ = 1.0;
            } else if (forward_ == strike) {
                d2_ = -0.5 * stdDev_;
                return f.value(phi_ * d2_);
            }
        }

        if (phi_ > 0) { // if Call
            return cum_d2_pos_;
        } else {
            return cum_d2_neg_;
        }
    }

    private double nD2(double strike) {

        double d2_;
        double n_d2_ = 0.0; // n(d2)

        if (stdDev_ >= QL_EPSILON) {
            if (strike > 0) {
                d2_ = Math.log(forward_ / strike) / stdDev_ - 0.5 * stdDev_;
                CumulativeNormalDistribution f = new CumulativeNormalDistribution();
                n_d2_ = f.derivative(d2_);
            }
        }

        return n_d2_;
    }

    public void setDeltaType(DeltaVolQuote.DeltaType dt) {
        dt_ = dt;
    }

    public void setOptionType(Option.Type ot) {
        ot_ = ot;
        phi_ = ot_ == Option.Type.Call ? 1 : -1;
    }
}
