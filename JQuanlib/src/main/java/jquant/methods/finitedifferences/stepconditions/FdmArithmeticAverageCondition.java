package jquant.methods.finitedifferences.stepconditions;

import jquant.math.Array;
import jquant.math.interpolations.MonotonicCubicNaturalSpline;
import jquant.methods.finitedifferences.meshers.FdmMesher;

import java.util.ArrayList;
import java.util.List;

import static jquant.math.CommonUtil.QL_REQUIRE;

/**
 * Step condition to handle an arithmetic average.
 *
 * <p>Port of {@code FdmArithmeticAverageCondition} from
 * {@code ql/methods/finitedifferences/stepconditions/fdmarithmeticaveragecondition.{hpp,cpp}}.
 */
public class FdmArithmeticAverageCondition implements FdmStepCondition {
    private final double[] x_; // grid-equity values in physical units
    private final double[] a_; // average values in physical units

    private final List<Double> averageTimes_;
    private final int pastFixings_;
    private final FdmMesher mesher_;
    private final int equityDirection_;

    public FdmArithmeticAverageCondition(List<Double> averageTimes,
                                         double runningAccumulator,
                                         int pastFixings,
                                         FdmMesher mesher,
                                         int equityDirection) {
        QL_REQUIRE(mesher.layout().dim().length == 2, "2D allowed only");
        QL_REQUIRE(equityDirection == 0 || equityDirection == 1,
                "equityDirection has to be 0 or 1");

        final int[] dim = mesher.layout().dim();
        x_ = new double[dim[equityDirection]];
        a_ = new double[dim[equityDirection == 0 ? 1 : 0]];

        averageTimes_ = new ArrayList<>(averageTimes);
        pastFixings_ = pastFixings;
        mesher_ = mesher;
        equityDirection_ = equityDirection;

        final int[] spacing = mesher.layout().spacing();
        final int xSpacing = spacing[equityDirection];
        final Array tmp = mesher.locations(equityDirection);
        for (int i = 0; i < x_.length; ++i) {
            x_[i] = Math.exp(tmp.get(i * xSpacing));
        }

        final int averageDirection = equityDirection == 0 ? 1 : 0;
        final int aSpacing = spacing[averageDirection];
        final Array averageLocations = mesher.locations(averageDirection);
        for (int i = 0; i < a_.length; ++i) {
            a_[i] = Math.exp(averageLocations.get(i * aSpacing));
        }
    }

    @Override
    public void applyTo(Array a, double t) {
        QL_REQUIRE(mesher_.layout().size() == a.size(),
                "inconsistent array dimensions");

        int iter = -1;
        int nTimes = 0;
        for (int i = 0; i < averageTimes_.size(); ++i) {
            if (averageTimes_.get(i) == t) {
                if (iter < 0) {
                    iter = i;
                }
                ++nTimes;
            }
        }

        if (nTimes > 0) {
            final Array aCopy = new Array(a);
            final int iT = iter + 1 + pastFixings_;
            final int averageDirection = equityDirection_ == 0 ? 1 : 0;
            final int[] spacing = mesher_.layout().spacing();
            final int xSpacing = spacing[equityDirection_];
            final int aSpacing = spacing[averageDirection];
            final double[] tmp = new double[a_.length];

            for (int i = 0; i < x_.length; ++i) {
                for (int j = 0; j < a_.length; ++j) {
                    final int index = i * xSpacing + j * aSpacing;
                    tmp[j] = aCopy.get(index);
                }
                final MonotonicCubicNaturalSpline interp =
                        new MonotonicCubicNaturalSpline(a_, tmp);
                for (int j = 0; j < a_.length; ++j) {
                    final int index = i * xSpacing + j * aSpacing;
                    a.set(index, interp.value(
                            (iT - nTimes) / (double) iT * a_[j]
                                    + nTimes / (double) iT * x_[i], true));
                }
            }
        }
    }
}
