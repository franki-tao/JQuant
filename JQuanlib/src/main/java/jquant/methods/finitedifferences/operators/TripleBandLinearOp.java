package jquant.methods.finitedifferences.operators;

import jquant.math.Array;
import jquant.methods.finitedifferences.meshers.FdmMesher;

import java.util.function.IntUnaryOperator;

import static jquant.math.CommonUtil.QL_REQUIRE;

/**
 * General triple-band linear operator.
 *
 * <p>Port of {@code ql/methods/finitedifferences/operators/triplebandlinearop.{hpp,cpp}}.
 */
public class TripleBandLinearOp implements FdmLinearOp {
    protected int direction_;
    protected int[] i0_, i2_, reverseIndex_;
    protected double[] lower_, diag_, upper_, temp_;
    protected FdmMesher mesher_;

    public TripleBandLinearOp(int direction, FdmMesher mesher) {
        direction_ = direction;
        mesher_ = mesher;
        final int size = mesher.layout().size();
        i0_ = new int[size];
        i2_ = new int[size];
        reverseIndex_ = new int[size];
        lower_ = new double[size];
        diag_ = new double[size];
        upper_ = new double[size];
        temp_ = new double[size];

        final int[] newDim = mesher.layout().dim().clone();
        swapElements(newDim, 0, direction_);
        final int[] newSpacing = new FdmLinearOpLayout(newDim).spacing().clone();
        swapElements(newSpacing, 0, direction_);

        for (FdmLinearOpIterator iter : mesher.layout()) {
            final int i = iter.index();

            i0_[i] = mesher.layout().neighbourhood(iter, direction_, -1);
            i2_[i] = mesher.layout().neighbourhood(iter, direction_, 1);

            final int[] coordinates = iter.coordinates();
            int newIndex = 0;
            for (int k = 0; k < coordinates.length; ++k) {
                newIndex += coordinates[k] * newSpacing[k];
            }
            reverseIndex_[newIndex] = i;
        }
    }

    protected TripleBandLinearOp() {
    }

    private static void swapElements(int[] a, int i, int j) {
        final int tmp = a[i];
        a[i] = a[j];
        a[j] = tmp;
    }

    @Override
    public Array apply(Array r) {
        QL_REQUIRE(r.size() == mesher_.layout().size(), "inconsistent length of r");

        final Array retVal = new Array(r.size());
        for (int i = 0; i < mesher_.layout().size(); ++i) {
            retVal.set(i, r.get(i0_[i]) * lower_[i]
                    + r.get(i) * diag_[i]
                    + r.get(i2_[i]) * upper_[i]);
        }
        return retVal;
    }

    public Array solve_splitting(Array r, double a, double b) {
        QL_REQUIRE(r.size() == mesher_.layout().size(), "inconsistent size of rhs");

        final int size = mesher_.layout().size();
        final IntUnaryOperator index = (direction_ == 0)
                ? i -> i
                : i -> reverseIndex_[i];

        final double[] result = new double[size];

        int previous = index.applyAsInt(0);
        double beta = a * diag_[previous] + b;
        QL_REQUIRE(beta != 0.0, "division by zero");
        beta = 1.0 / beta;
        result[previous] = r.get(previous) * beta;

        for (int j = 1; j < size; ++j) {
            final int current = index.applyAsInt(j);
            temp_[j] = a * upper_[previous] * beta;

            beta = b + a * (diag_[current] - temp_[j] * lower_[current]);
            QL_REQUIRE(beta != 0.0, "division by zero");
            beta = 1.0 / beta;

            result[current] = (r.get(current) - a * lower_[current] * result[previous]) * beta;
            previous = current;
        }

        for (int j = size - 2; j > 0; --j) {
            result[index.applyAsInt(j)] -= temp_[j + 1] * result[index.applyAsInt(j + 1)];
        }
        result[index.applyAsInt(0)] -= temp_[1] * result[index.applyAsInt(1)];

        return new Array(result);
    }

    public TripleBandLinearOp mult(Array u) {
        final TripleBandLinearOp retVal = new TripleBandLinearOp(direction_, mesher_);

        final int size = mesher_.layout().size();
        for (int i = 0; i < size; ++i) {
            final double s = u.get(i);
            retVal.lower_[i] = lower_[i] * s;
            retVal.diag_[i] = diag_[i] * s;
            retVal.upper_[i] = upper_[i] * s;
        }

        return retVal;
    }

    public TripleBandLinearOp add(Array u) {
        final TripleBandLinearOp retVal = new TripleBandLinearOp(direction_, mesher_);

        final int size = mesher_.layout().size();
        for (int i = 0; i < size; ++i) {
            retVal.lower_[i] = lower_[i];
            retVal.upper_[i] = upper_[i];
            retVal.diag_[i] = diag_[i] + u.get(i);
        }

        return retVal;
    }

    public TripleBandLinearOp add(TripleBandLinearOp m) {
        final TripleBandLinearOp retVal = new TripleBandLinearOp(direction_, mesher_);

        final int size = mesher_.layout().size();
        for (int i = 0; i < size; ++i) {
            retVal.lower_[i] = lower_[i] + m.lower_[i];
            retVal.diag_[i] = diag_[i] + m.diag_[i];
            retVal.upper_[i] = upper_[i] + m.upper_[i];
        }

        return retVal;
    }

    public void axpyb(Array a, TripleBandLinearOp x,
                      TripleBandLinearOp y, Array b) {
        final int size = mesher_.layout().size();

        if (a.empty()) {
            if (b.empty()) {
                for (int i = 0; i < size; ++i) {
                    diag_[i] = y.diag_[i];
                    lower_[i] = y.lower_[i];
                    upper_[i] = y.upper_[i];
                }
            } else {
                final int binc = (b.size() > 1) ? 1 : 0;
                for (int i = 0; i < size; ++i) {
                    diag_[i] = y.diag_[i] + b.get(i * binc);
                    lower_[i] = y.lower_[i];
                    upper_[i] = y.upper_[i];
                }
            }
        } else if (b.empty()) {
            final int ainc = (a.size() > 1) ? 1 : 0;
            for (int i = 0; i < size; ++i) {
                final double s = a.get(i * ainc);
                diag_[i] = y.diag_[i] + s * x.diag_[i];
                lower_[i] = y.lower_[i] + s * x.lower_[i];
                upper_[i] = y.upper_[i] + s * x.upper_[i];
            }
        } else {
            final int binc = (b.size() > 1) ? 1 : 0;
            final int ainc = (a.size() > 1) ? 1 : 0;
            for (int i = 0; i < size; ++i) {
                final double s = a.get(i * ainc);
                diag_[i] = y.diag_[i] + s * x.diag_[i] + b.get(i * binc);
                lower_[i] = y.lower_[i] + s * x.lower_[i];
                upper_[i] = y.upper_[i] + s * x.upper_[i];
            }
        }
    }
}
