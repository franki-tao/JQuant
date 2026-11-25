package jquant.termstructures.volatility;

import jquant.termstructures.SmileSection;
import jquant.termstructures.volatility.Sarb.VolatilityType;
import jquant.time.Date;
import jquant.time.DayCounter;
import jquant.time.daycounters.Actual365Fixed;

import static jquant.math.CommonUtil.QL_REQUIRE;
import static jquant.math.MathUtils.QL_MAX_REAL;
import static jquant.termstructures.volatility.Sarb.VolatilityType.ShiftedLognormal;

/**
 * SABR smile section.
 *
 * <p>Ported from QuantLib
 * {@code ql/termstructures/volatility/sabrsmilesection.{hpp,cpp}}. The
 * volatility is the (unsafe) shifted SABR volatility of {@link Sarb}; strikes
 * are clamped to {@code max(0.00001 - shift, strike)} exactly as official.
 *
 * <p>Java {@link Date} instances are mutable, so dates passed to the
 * date-based constructor are copied.
 *
 * <p>https://github.com/lballabio/QuantLib/blob/master/ql/termstructures/volatility/sabrsmilesection.hpp
 */
public class SabrSmileSection extends SmileSection {

    private final double alpha_;
    private final double beta_;
    private final double nu_;
    private final double rho_;
    private final double forward_;
    private final double shift_;

    /** Time-based section with the official defaults (no shift, lognormal). */
    public SabrSmileSection(double timeToExpiry,
                            double forward,
                            double[] sabrParameters) {
        this(timeToExpiry, forward, sabrParameters, 0.0, ShiftedLognormal);
    }

    /** Time-based section. Official passes an empty {@link DayCounter}. */
    public SabrSmileSection(double timeToExpiry,
                            double forward,
                            double[] sabrParameters,
                            double shift,
                            VolatilityType volatilityType) {
        super(timeToExpiry, new DayCounter(), volatilityType, shift);
        forward_ = forward;
        shift_ = shift;
        double[] p = initialise(sabrParameters);
        alpha_ = p[0];
        beta_ = p[1];
        nu_ = p[2];
        rho_ = p[3];
    }

    /** Date-based section with the official defaults. */
    public SabrSmileSection(Date d,
                            double forward,
                            double[] sabrParameters) {
        this(d, forward, sabrParameters, new Date(),
                new Actual365Fixed(), 0.0, ShiftedLognormal);
    }

    /** Date-based section. */
    public SabrSmileSection(Date d,
                            double forward,
                            double[] sabrParameters,
                            Date referenceDate,
                            DayCounter dc,
                            double shift,
                            VolatilityType volatilityType) {
        super(d.copy(), dc,
                referenceDate == null ? new Date() : referenceDate.copy(),
                volatilityType, shift);
        forward_ = forward;
        shift_ = shift;
        double[] p = initialise(sabrParameters);
        alpha_ = p[0];
        beta_ = p[1];
        nu_ = p[2];
        rho_ = p[3];
    }

    private double[] initialise(double[] sabrParams) {
        QL_REQUIRE(forward_ + shift_ > 0.0,
                "at the money forward rate + shift must be positive: "
                        + forward_ + " with shift " + shift_ + " not allowed");
        Sarb.validateSabrParameters(sabrParams[0], sabrParams[1],
                sabrParams[2], sabrParams[3]);
        return new double[]{sabrParams[0], sabrParams[1],
                sabrParams[2], sabrParams[3]};
    }

    @Override
    public double minStrike() {
        return -shift_;
    }

    @Override
    public double maxStrike() {
        return QL_MAX_REAL;
    }

    @Override
    public double atmLevel() {
        return forward_;
    }

    public double alpha() {
        return alpha_;
    }

    public double beta() {
        return beta_;
    }

    public double nu() {
        return nu_;
    }

    public double rho() {
        return rho_;
    }

    @Override
    protected double varianceImpl(double strike) {
        strike = Math.max(0.00001 - shift(), strike);
        double vol = Sarb.unsafeShiftedSabrVolatility(strike, forward_,
                exerciseTime(), alpha_, beta_, nu_, rho_, shift_,
                volatilityType());
        return vol * vol * exerciseTime();
    }

    @Override
    protected double volatilityImpl(double strike) {
        strike = Math.max(0.00001 - shift(), strike);
        return Sarb.unsafeShiftedSabrVolatility(strike, forward_,
                exerciseTime(), alpha_, beta_, nu_, rho_, shift_,
                volatilityType());
    }
}
