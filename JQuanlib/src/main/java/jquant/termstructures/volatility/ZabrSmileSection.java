package jquant.termstructures.volatility;

import jquant.Option;
import jquant.math.interpolations.CubicInterpolation;
import jquant.termstructures.SmileSection;
import jquant.time.Date;
import jquant.time.DayCounter;
import jquant.time.daycounters.Actual365Fixed;

import java.util.ArrayList;
import java.util.List;

import static jquant.math.CommonUtil.QL_REQUIRE;
import static jquant.math.MathUtils.QL_MAX_REAL;
import static jquant.math.interpolations.CubicInterpolation.BoundaryCondition.SecondDerivative;
import static jquant.math.interpolations.CubicInterpolation.DerivativeApprox.Spline;
import static jquant.pricingengines.BlackFormula.bachelierBlackFormula;
import static jquant.pricingengines.BlackFormula.blackFormulaImpliedStdDev;
import static jquant.termstructures.volatility.Sarb.VolatilityType.ShiftedLognormal;

/**
 * ZABR smile section.
 *
 * <p>Ported from QuantLib
 * {@code ql/termstructures/volatility/zabrsmilesection.hpp}. The official
 * class is templated on an evaluation tag; the port takes the corresponding
 * {@link ZabrEvaluation} kernel instead.
 */
public class ZabrSmileSection extends SmileSection {

    private static final double[] DEFAULT_MONEY = {
            0.0, 0.01, 0.05, 0.10, 0.25, 0.40, 0.50, 0.60, 0.70, 0.80, 0.90,
            1.0, 1.25, 1.5, 1.75, 2.0, 5.0, 7.5, 10.0, 15.0, 20.0};

    private ZabrModel model_;
    private final ZabrEvaluation evaluation_;
    private final double forward_;
    private final double[] params_;
    private final int fdRefinement_;
    private final List<Double> strikes_;
    private final List<Double> callPrices_;
    private CubicInterpolation callPriceFct_;
    private double a_, b_;

    /** Time-based section with the official defaults (lognormal kernel). */
    public ZabrSmileSection(double timeToExpiry,
                            double forward,
                            double[] zabrParameters) {
        this(timeToExpiry, forward, zabrParameters,
                ZabrEvaluation.ZabrShortMaturityLognormal,
                new double[0], 5);
    }

    public ZabrSmileSection(double timeToExpiry,
                            double forward,
                            double[] zabrParameters,
                            double[] moneyness,
                            int fdRefinement) {
        this(timeToExpiry, forward, zabrParameters,
                ZabrEvaluation.ZabrShortMaturityLognormal,
                moneyness, fdRefinement);
    }

    public ZabrSmileSection(double timeToExpiry,
                            double forward,
                            double[] zabrParameters,
                            ZabrEvaluation evaluation,
                            double[] moneyness,
                            int fdRefinement) {
        super(timeToExpiry, new DayCounter(), ShiftedLognormal, 0.0);
        forward_ = forward;
        params_ = zabrParameters.clone();
        evaluation_ = evaluation;
        fdRefinement_ = fdRefinement;
        strikes_ = new ArrayList<>();
        callPrices_ = new ArrayList<>();
        init(moneyness);
    }

    /** Date-based section with the official defaults (lognormal kernel). */
    public ZabrSmileSection(Date d,
                            double forward,
                            double[] zabrParameters) {
        this(d, forward, zabrParameters, new Actual365Fixed(),
                new double[0], 5,
                ZabrEvaluation.ZabrShortMaturityLognormal);
    }

    public ZabrSmileSection(Date d,
                            double forward,
                            double[] zabrParameters,
                            DayCounter dc) {
        this(d, forward, zabrParameters, dc, new double[0], 5,
                ZabrEvaluation.ZabrShortMaturityLognormal);
    }

    public ZabrSmileSection(Date d,
                            double forward,
                            double[] zabrParameters,
                            DayCounter dc,
                            double[] moneyness,
                            int fdRefinement,
                            ZabrEvaluation evaluation) {
        super(d.copy(), dc, new Date(), ShiftedLognormal, 0.0);
        forward_ = forward;
        params_ = zabrParameters.clone();
        evaluation_ = evaluation;
        fdRefinement_ = fdRefinement;
        strikes_ = new ArrayList<>();
        callPrices_ = new ArrayList<>();
        init(moneyness);
    }

    @Override
    public double minStrike() {
        return 0.0;
    }

    @Override
    public double maxStrike() {
        return QL_MAX_REAL;
    }

    @Override
    public double atmLevel() {
        return model_.forward();
    }

    public ZabrModel model() {
        return model_;
    }

    public ZabrEvaluation evaluation() {
        return evaluation_;
    }

    @Override
    public double optionPrice(double strike,
                              Option.Type type,
                              double discount) {
        switch (evaluation_) {
            case ZabrShortMaturityLognormal:
                return super.optionPrice(strike, type, discount);
            case ZabrShortMaturityNormal:
                return bachelierBlackFormula(type, strike, forward_,
                        model_.normalVolatility(strike)
                                * Math.sqrt(exerciseTime()), discount);
            case ZabrLocalVolatility:
            case ZabrFullFd: {
                final double call = strike <= strikes_.get(strikes_.size() - 1)
                        ? callPriceFct_.value(strike, false)
                        : Math.exp(-a_ * strike + b_);
                if (type == Option.Type.Call)
                    return call * discount;
                else
                    return (call - (forward_ - strike)) * discount;
            }
            default:
                throw new IllegalStateException("unknown ZABR kernel " + evaluation_);
        }
    }

    @Override
    protected double volatilityImpl(double strike) {
        switch (evaluation_) {
            case ZabrShortMaturityLognormal:
                strike = Math.max(1E-6, strike);
                return model_.lognormalVolatility(strike);
            case ZabrShortMaturityNormal:
            case ZabrLocalVolatility:
            case ZabrFullFd: {
                double impliedVol = 0.0;
                try {
                    final Option.Type type = strike >= model_.forward()
                            ? Option.Type.Call : Option.Type.Put;
                    impliedVol = blackFormulaImpliedStdDev(type, strike,
                            model_.forward(),
                            optionPrice(strike, type, 1.0), 1.0,
                            0.0, Double.NaN, 1e-6, 100)
                            / Math.sqrt(exerciseTime());
                } catch (RuntimeException e) {
                    // official swallows the exception and returns the initial value
                }
                return impliedVol;
            }
            default:
                throw new IllegalStateException("unknown ZABR kernel " + evaluation_);
        }
    }

    // ------------------------------------------------------------------
    // Initialisation (official init / init2 / init3 overloads)
    // ------------------------------------------------------------------

    private void init(double[] moneyness) {
        switch (evaluation_) {
            case ZabrShortMaturityLognormal:
                initLognormal();
                break;
            case ZabrShortMaturityNormal:
                initLognormal();
                break;
            case ZabrLocalVolatility:
                initLocalVolatility(moneyness);
                break;
            case ZabrFullFd:
                initLocalVolatility(moneyness);
                break;
            default:
                throw new IllegalStateException("unknown ZABR kernel " + evaluation_);
        }
        init2();
        init3();
    }

    private void initLognormal() {
        model_ = new ZabrModel(exerciseTime(), forward_, params_[0], params_[1],
                params_[2], params_[3], params_[4]);
    }

    private void initLocalVolatility(double[] moneyness) {
        QL_REQUIRE(params_.length >= 5,
                "zabr expects 5 parameters (alpha,beta,nu,rho,gamma) but ("
                        + params_.length + ") given");

        model_ = new ZabrModel(exerciseTime(), forward_, params_[0], params_[1],
                params_[2], params_[3], params_[4]);

        // set up strike grid for local vol or full fd flavour of this section
        // this is shared with SmileSectionUtils - unify later ?
        final double[] tmp = moneyness.length == 0 ? DEFAULT_MONEY : moneyness;

        strikes_.clear(); // should not be necessary, anyway
        double lastF = 0.0;
        boolean firstStrike = true;
        for (double i : tmp) {
            final double f = i * forward_;
            if (f > 0.0) {
                if (!firstStrike) {
                    for (int j = 1; j <= fdRefinement_; ++j) {
                        strikes_.add(lastF + ((double) j) * (f - lastF)
                                / (fdRefinement_ + 1));
                    }
                }
                firstStrike = false;
                lastF = f;
                strikes_.add(f);
            }
        }
    }

    private void init2() {
        switch (evaluation_) {
            case ZabrShortMaturityLognormal:
            case ZabrShortMaturityNormal:
                break;
            case ZabrLocalVolatility:
                callPrices_.clear();
                for (double callPrice : model_.fdPrice(toArray(strikes_)))
                    callPrices_.add(callPrice);
                break;
            case ZabrFullFd:
                callPrices_.clear();
                for (double strike : strikes_)
                    callPrices_.add(model_.fullFdPrice(strike));
                break;
            default:
                throw new IllegalStateException("unknown ZABR kernel " + evaluation_);
        }
    }

    private void init3() {
        switch (evaluation_) {
            case ZabrShortMaturityLognormal:
            case ZabrShortMaturityNormal:
                break;
            case ZabrLocalVolatility:
            case ZabrFullFd: {
                strikes_.add(0, 0.0);
                callPrices_.add(0, forward_);

                callPriceFct_ = new CubicInterpolation(
                        toArray(strikes_), toArray(callPrices_),
                        Spline, true, SecondDerivative, 0.0,
                        SecondDerivative, 0.0);
                callPriceFct_.enableExtrapolation();

                // on the right side we extrapolate exponetially (because spline
                // does not make sense); we precompute the necessary parameters
                final double eps = 1E-5; // gap for first derivative computation

                final double lastStrike = strikes_.get(strikes_.size() - 1);
                final double c0 = callPriceFct_.value(lastStrike, false);
                final double c0p = (callPriceFct_.value(lastStrike - eps, false)
                        - c0) / eps;

                a_ = c0p / c0;
                b_ = Math.log(c0) + a_ * lastStrike;
                break;
            }
            default:
                throw new IllegalStateException("unknown ZABR kernel " + evaluation_);
        }
    }

    private static double[] toArray(List<Double> values) {
        final double[] result = new double[values.size()];
        for (int i = 0; i < result.length; ++i)
            result[i] = values.get(i);
        return result;
    }
}
