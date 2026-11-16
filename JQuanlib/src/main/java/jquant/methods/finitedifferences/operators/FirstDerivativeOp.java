package jquant.methods.finitedifferences.operators;

import jquant.methods.finitedifferences.meshers.FdmMesher;

/**
 * First-derivative linear operator.
 *
 * <p>Port of {@code ql/methods/finitedifferences/operators/firstderivativeop.{hpp,cpp}}.
 */
public class FirstDerivativeOp extends TripleBandLinearOp {
    public FirstDerivativeOp(int direction, FdmMesher mesher) {
        super(direction, mesher);

        for (FdmLinearOpIterator iter : mesher.layout()) {
            final int i = iter.index();
            final double hm = mesher.dminus(iter, direction_);
            final double hp = mesher.dplus(iter, direction_);

            final double zetam1 = hm * (hm + hp);
            final double zeta0 = hm * hp;
            final double zetap1 = hp * (hm + hp);

            if (iter.coordinates()[direction_] == 0) {
                // upwinding scheme
                lower_[i] = 0.0;
                diag_[i] = -(upper_[i] = 1.0 / hp);
            } else if (iter.coordinates()[direction_]
                    == mesher.layout().dim()[direction_] - 1) {
                // downwinding scheme
                lower_[i] = -(diag_[i] = 1.0 / hm);
                upper_[i] = 0.0;
            } else {
                lower_[i] = -hp / zetam1;
                diag_[i] = (hp - hm) / zeta0;
                upper_[i] = hm / zetap1;
            }
        }
    }
}
