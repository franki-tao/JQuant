package jquant.methods.finitedifferences.operators;

import jquant.methods.finitedifferences.meshers.FdmMesher;

/**
 * Second-derivative linear operator.
 *
 * <p>Port of {@code ql/methods/finitedifferences/operators/secondderivativeop.{hpp,cpp}}.
 */
public class SecondDerivativeOp extends TripleBandLinearOp {
    public SecondDerivativeOp(int direction, FdmMesher mesher) {
        super(direction, mesher);

        for (FdmLinearOpIterator iter : mesher.layout()) {
            final int i = iter.index();
            final double hm = mesher.dminus(iter, direction_);
            final double hp = mesher.dplus(iter, direction_);

            final double zetam1 = hm * (hm + hp);
            final double zeta0 = hm * hp;
            final double zetap1 = hp * (hm + hp);

            final int co = iter.coordinates()[direction_];
            if (co == 0 || co == mesher.layout().dim()[direction_] - 1) {
                lower_[i] = 0.0;
                diag_[i] = 0.0;
                upper_[i] = 0.0;
            } else {
                lower_[i] = 2.0 / zetam1;
                diag_[i] = -2.0 / zeta0;
                upper_[i] = 2.0 / zetap1;
            }
        }
    }
}
