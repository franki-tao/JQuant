package jquant.termstructures.volatility.optionlet;

import jquant.math.interpolations.LinearInterpolation;
import jquant.termstructures.volatility.Sarb;
import jquant.time.Date;

import java.util.List;

/**
 * Adapter class for turning a StrippedOptionletBase object into an
 * OptionletVolatilityStructure.
 *
 * <p>Ported from QuantLib
 * {@code ql/termstructures/volatility/optionlet/strippedoptionletadapter.{hpp,cpp}}.
 *
 * <p>In the official implementation this class inherits from both
 * {@code OptionletVolatilityStructure} and {@code LazyObject}. Java has
 * single inheritance, so the lazy recalculation performed by
 * {@code performCalculations()} is reproduced here explicitly through
 * {@link #calculate()}.
 */
public class StrippedOptionletAdapter extends OptionletVolatilityStructure {

    private final StrippedOptionletBase optionletStripper_;
    private final int nInterpolations_;
    private final LinearInterpolation[] strikeInterpolations_;
    private boolean calculated_ = false;

    public StrippedOptionletAdapter(StrippedOptionletBase s) {
        super(s.settlementDays(), s.calendar(), s.businessDayConvention(),
                s.dayCounter());
        optionletStripper_ = s;
        nInterpolations_ = s.optionletMaturities();
        strikeInterpolations_ = new LinearInterpolation[nInterpolations_];
        registerWith(optionletStripper_);
    }

    @Override
    public Date maxDate() {
        return optionletStripper_.optionletFixingDates()
                .get(optionletStripper_.optionletFixingDates().size() - 1);
    }

    @Override
    public double minStrike() {
        return optionletStripper_.optionletStrikes(0).get(0);
    }

    @Override
    public double maxStrike() {
        List<Double> strikes = optionletStripper_.optionletStrikes(0);
        return strikes.get(strikes.size() - 1);
    }

    @Override
    public void update() {
        super.update();
        calculated_ = false;
    }

    /** Mirrors the official {@code Observer::deepUpdate()}. */
    public void deepUpdate() {
        optionletStripper_.update();
        update();
    }

    /** Mirrors the official {@code LazyObject::calculate()}. */
    public void calculate() {
        if (!calculated_) {
            calculated_ = true;
            try {
                performCalculations();
            } catch (RuntimeException e) {
                calculated_ = false;
                throw e;
            }
        }
    }

    /** Mirrors the official {@code LazyObject::performCalculations()}. */
    protected void performCalculations() {
        for (int i = 0; i < nInterpolations_; ++i) {
            List<Double> optionletStrikes = optionletStripper_.optionletStrikes(i);
            List<Double> optionletVolatilities =
                    optionletStripper_.optionletVolatilities(i);
            strikeInterpolations_[i] = new LinearInterpolation(
                    toArray(optionletStrikes), toArray(optionletVolatilities));
        }
    }

    @Override
    protected double volatilityImpl(double length, double strike) {
        calculate();

        double[] vol = new double[nInterpolations_];
        for (int i = 0; i < nInterpolations_; ++i)
            vol[i] = strikeInterpolations_[i].value(strike, true);

        double[] optionletTimes = optionletStripper_.optionletFixingTimes();
        LinearInterpolation timeInterpolator =
                new LinearInterpolation(optionletTimes, vol);
        return timeInterpolator.value(length, true);
    }

    @Override
    public Sarb.VolatilityType volatilityType() {
        return optionletStripper_.volatilityType();
    }

    @Override
    public double displacement() {
        return optionletStripper_.displacement();
    }

    public StrippedOptionletBase optionletStripper() {
        return optionletStripper_;
    }

    private static double[] toArray(List<Double> values) {
        double[] result = new double[values.size()];
        for (int i = 0; i < values.size(); ++i)
            result[i] = values.get(i);
        return result;
    }
}
