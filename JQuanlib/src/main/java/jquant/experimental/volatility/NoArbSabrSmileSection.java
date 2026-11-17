package jquant.experimental.volatility;

import jquant.Option;
import jquant.math.interpolations.impl.XABRModel;
import jquant.pricingengines.BlackFormula;
import jquant.termstructures.SmileSection;
import jquant.termstructures.volatility.Sarb;
import jquant.time.Date;
import jquant.time.DayCounter;
import jquant.time.daycounters.Actual365Fixed;

import java.util.ArrayList;
import java.util.List;

import static jquant.math.CommonUtil.QL_REQUIRE;
import static jquant.math.MathUtils.QL_MAX_REAL;

/**
 * Port of QuantLib's experimental no-arbitrage SABR smile section
 * ({@code ql/experimental/volatility/noarbsabrsmilesection.{hpp,cpp}}).
 *
 * <p>It also serves as the model instance plugged into
 * {@code XABRInterpolationImpl}, mirroring the official
 * {@code typedef NoArbSabrSmileSection NoArbSabrWrapper}.</p>
 */
public class NoArbSabrSmileSection extends SmileSection implements XABRModel {

    private NoArbSabrModel model_;
    private final double forward_;
    private final List<Double> params_;
    private final double shift_;

    public NoArbSabrSmileSection(double timeToExpiry,
                                 double forward,
                                 List<Double> sabrParameters,
                                 double shift,
                                 Sarb.VolatilityType volatilityType) {
        super(timeToExpiry, new Actual365Fixed(), volatilityType, shift);
        forward_ = forward;
        params_ = new ArrayList<>(sabrParameters);
        shift_ = shift;
        init();
    }

    public NoArbSabrSmileSection(double timeToExpiry,
                                 double forward,
                                 List<Double> sabrParameters) {
        this(timeToExpiry, forward, sabrParameters, 0.0,
                Sarb.VolatilityType.ShiftedLognormal);
    }

    public NoArbSabrSmileSection(Date d,
                                 double forward,
                                 List<Double> sabrParameters) {
        this(d, forward, sabrParameters, new Actual365Fixed(), 0.0,
                Sarb.VolatilityType.ShiftedLognormal);
    }

    public NoArbSabrSmileSection(Date d,
                                 double forward,
                                 List<Double> sabrParameters,
                                 DayCounter dc) {
        this(d, forward, sabrParameters, dc, 0.0,
                Sarb.VolatilityType.ShiftedLognormal);
    }

    public NoArbSabrSmileSection(Date d,
                                 double forward,
                                 List<Double> sabrParameters,
                                 DayCounter dc,
                                 double shift,
                                 Sarb.VolatilityType volatilityType) {
        super(d, dc, new Date(), volatilityType, shift);
        forward_ = forward;
        params_ = new ArrayList<>(sabrParameters);
        shift_ = shift;
        init();
    }

    private void init() {
        QL_REQUIRE(params_.size() >= 4,
                "sabr expects 4 parameters (alpha,beta,nu,rho) but (" +
                        params_.size() + ") given");
        QL_REQUIRE(forward_ > 0.0,
                "forward (" + forward_ + ") must be positive");
        QL_REQUIRE(shift_ == 0.0,
                "shift (" + shift_ +
                        ") must be zero, other shifts are not implemented yet");
        model_ = new NoArbSabrModel(exerciseTime(), forward_, params_.get(0),
                params_.get(1), params_.get(2), params_.get(3));
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
        return forward_;
    }

    @Override
    public double optionPrice(double strike, Option.Type type, double discount) {
        double call = model_.optionPrice(strike);
        return discount *
                (type == Option.Type.Call ? call : call - (forward_ - strike));
    }

    @Override
    public double digitalOptionPrice(double strike, Option.Type type,
                                     double discount, double gap) {
        double call = model_.digitalOptionPrice(strike);
        return discount * (type == Option.Type.Call ? call : 1.0 - call);
    }

    @Override
    public double density(double strike, double discount, double gap) {
        return discount * model_.density(strike);
    }

    @Override
    protected double volatilityImpl(double strike) {
        double impliedVol = 0.0;
        try {
            Option.Type type = strike >= forward_ ? Option.Type.Call : Option.Type.Put;
            impliedVol = BlackFormula.blackFormulaImpliedStdDev(type, strike,
                    forward_, optionPrice(strike, type, 1.0), 1.0,
                    0.0, Double.NaN, 1e-6, 100) / Math.sqrt(exerciseTime());
        } catch (RuntimeException e) {
            // fall through to Hagan 2002 below
        }
        if (impliedVol == 0.0)
            // fall back on Hagan 2002 expansion
            impliedVol = Sarb.unsafeSabrVolatility(strike, forward_,
                    exerciseTime(), params_.get(0), params_.get(1),
                    params_.get(2), params_.get(3), volatilityType());
        return impliedVol;
    }

    /**
     * XABR wrapper interface: the official no-arbitrage interpolation uses
     * the smile section itself as the model instance.
     */
    @Override
    public double volatility(double strike, Sarb.VolatilityType volatilityType) {
        return volatility(strike, volatilityType, shift_);
    }

    public NoArbSabrModel model() {
        return model_;
    }
}
