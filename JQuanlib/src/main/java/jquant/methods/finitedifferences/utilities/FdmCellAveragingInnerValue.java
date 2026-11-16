package jquant.methods.finitedifferences.utilities;

import jquant.Payoff;
import jquant.math.integrals.SimpsonIntegral;
import jquant.methods.finitedifferences.meshers.FdmMesher;
import jquant.methods.finitedifferences.operators.FdmLinearOpIterator;

import java.util.function.DoubleUnaryOperator;

/**
 * Inner value averaged over the finite-difference cell.
 *
 * <p>Port of {@code FdmCellAveragingInnerValue} from
 * {@code ql/methods/finitedifferences/utilities/fdminnervaluecalculator.{hpp,cpp}}.
 */
public class FdmCellAveragingInnerValue implements FdmInnerValueCalculator {
    private final Payoff payoff_;
    private final FdmMesher mesher_;
    private final int direction_;
    private final DoubleUnaryOperator gridMapping_;

    private double[] avgInnerValues_;

    public FdmCellAveragingInnerValue(Payoff payoff,
                                      FdmMesher mesher,
                                      int direction,
                                      DoubleUnaryOperator gridMapping) {
        payoff_ = payoff;
        mesher_ = mesher;
        direction_ = direction;
        gridMapping_ = gridMapping;
    }

    @Override
    public double innerValue(FdmLinearOpIterator iter, double t) {
        final double loc = mesher_.location(iter, direction_);
        return payoff_.value(gridMapping_.applyAsDouble(loc));
    }

    @Override
    public double avgInnerValue(FdmLinearOpIterator iter, double t) {
        if (avgInnerValues_ == null) {
            final int dim = mesher_.layout().dim()[direction_];
            avgInnerValues_ = new double[dim];
            final boolean[] initialized = new boolean[dim];

            for (FdmLinearOpIterator i : mesher_.layout()) {
                final int xn = i.coordinates()[direction_];
                if (!initialized[xn]) {
                    initialized[xn] = true;
                    avgInnerValues_[xn] = avgInnerValueCalc(i, t);
                }
            }
        }

        return avgInnerValues_[iter.coordinates()[direction_]];
    }

    private double avgInnerValueCalc(FdmLinearOpIterator iter, double t) {
        final int dim = mesher_.layout().dim()[direction_];
        final int coord = iter.coordinates()[direction_];

        if (coord == 0 || coord == dim - 1) {
            return innerValue(iter, t);
        }

        final double loc = mesher_.location(iter, direction_);
        final double a = loc - mesher_.dminus(iter, direction_) / 2.0;
        final double b = loc + mesher_.dplus(iter, direction_) / 2.0;

        final DoubleUnaryOperator f = x -> payoff_.value(gridMapping_.applyAsDouble(x));

        double retVal;
        try {
            final double acc = (f.applyAsDouble(a) != 0.0 || f.applyAsDouble(b) != 0.0)
                    ? (f.applyAsDouble(a) + f.applyAsDouble(b)) * 5e-5
                    : 1e-4;
            retVal = new SimpsonIntegral(acc, 8)
                    .value(x -> f.applyAsDouble(x), a, b) / (b - a);
        } catch (RuntimeException ex) {
            // use default value
            retVal = innerValue(iter, t);
        }

        return retVal;
    }
}
