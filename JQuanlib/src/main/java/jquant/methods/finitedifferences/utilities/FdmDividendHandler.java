package jquant.methods.finitedifferences.utilities;

import jquant.cashflows.Dividend;
import jquant.math.Array;
import jquant.math.interpolations.LinearInterpolation;
import jquant.methods.finitedifferences.meshers.FdmMesher;
import jquant.methods.finitedifferences.operators.FdmLinearOpLayout;
import jquant.methods.finitedifferences.stepconditions.FdmStepCondition;
import jquant.time.Date;
import jquant.time.DayCounter;

import java.util.ArrayList;
import java.util.List;

/**
 * Dividend handler for the finite-difference method in one equity direction.
 *
 * <p>Port of
 * {@code ql/methods/finitedifferences/utilities/fdmdividendhandler.{hpp,cpp}}.
 */
public class FdmDividendHandler implements FdmStepCondition {
    private final double[] x_; // grid-equity values in physical units

    private final List<Double> dividendTimes_ = new ArrayList<>();
    private final List<Date> dividendDates_ = new ArrayList<>();
    private final List<Double> dividends_ = new ArrayList<>();
    private final FdmMesher mesher_;
    private final int equityDirection_;

    public FdmDividendHandler(List<Dividend> schedule,
                              FdmMesher mesher,
                              Date referenceDate,
                              DayCounter dayCounter,
                              int equityDirection) {
        mesher_ = mesher;
        equityDirection_ = equityDirection;

        final int dimension = mesher.layout().dim()[equityDirection];
        x_ = new double[dimension];

        for (Dividend dividend : schedule) {
            dividends_.add(dividend.amount());
            dividendDates_.add(dividend.date());
            dividendTimes_.add(dayCounter.yearFraction(referenceDate,
                    dividend.date()));
        }

        final Array tmp = mesher.locations(equityDirection);
        final int spacing = mesher.layout().spacing()[equityDirection];
        for (int i = 0; i < x_.length; ++i) {
            x_[i] = Math.exp(tmp.get(i * spacing));
        }
    }

    public List<Double> dividendTimes() {
        return dividendTimes_;
    }

    public List<Date> dividendDates() {
        return dividendDates_;
    }

    public List<Double> dividends() {
        return dividends_;
    }

    @Override
    public void applyTo(Array a, double t) {
        final Array aCopy = new Array(a);

        int index = -1;
        for (int i = 0; i < dividendTimes_.size(); ++i) {
            if (dividendTimes_.get(i) == t) {
                index = i;
                break;
            }
        }

        if (index >= 0) {
            final double dividend = dividends_.get(index);
            final FdmLinearOpLayout layout = mesher_.layout();

            if (layout.dim().length == 1) {
                final LinearInterpolation interp =
                        new LinearInterpolation(x_, aCopy.toArray());
                for (int k = 0; k < x_.length; ++k) {
                    a.set(k, interp.value(Math.max(x_[0], x_[k] - dividend),
                            true));
                }
            } else {
                final double[] tmp = new double[x_.length];
                final int xSpacing = layout.spacing()[equityDirection_];

                for (int i = 0; i < layout.dim().length; ++i) {
                    if (i != equityDirection_) {
                        final int ySpacing = layout.spacing()[i];
                        for (int j = 0; j < layout.dim()[i]; ++j) {
                            for (int k = 0; k < x_.length; ++k) {
                                final int idx = j * ySpacing + k * xSpacing;
                                tmp[k] = aCopy.get(idx);
                            }
                            final LinearInterpolation interp =
                                    new LinearInterpolation(x_, tmp);
                            for (int k = 0; k < x_.length; ++k) {
                                final int idx = j * ySpacing + k * xSpacing;
                                a.set(idx, interp.value(
                                        Math.max(x_[0], x_[k] - dividend), true));
                            }
                        }
                    }
                }
            }
        }
    }
}
