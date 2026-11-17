package jquant.methods.finitedifferences.operators;

import jquant.methods.finitedifferences.meshers.FdmMesher;

/**
 * Second-order mixed derivative linear operator.
 *
 * <p>Port of {@code ql/methods/finitedifferences/operators/secondordermixedderivativeop.{hpp,cpp}}.
 */
public class SecondOrderMixedDerivativeOp extends NinePointLinearOp {
    public SecondOrderMixedDerivativeOp(int d0, int d1, FdmMesher mesher) {
        super(d0, d1, mesher);

        for (FdmLinearOpIterator iter : mesher.layout()) {
            final int i = iter.index();
            final double hm_d0 = mesher.dminus(iter, d0_);
            final double hp_d0 = mesher.dplus(iter, d0_);
            final double hm_d1 = mesher.dminus(iter, d1_);
            final double hp_d1 = mesher.dplus(iter, d1_);

            final double zetam1 = hm_d0 * (hm_d0 + hp_d0);
            final double zeta0 = hm_d0 * hp_d0;
            final double zetap1 = hp_d0 * (hm_d0 + hp_d0);
            final double phim1 = hm_d1 * (hm_d1 + hp_d1);
            final double phi0 = hm_d1 * hp_d1;
            final double phip1 = hp_d1 * (hm_d1 + hp_d1);

            final int c0 = iter.coordinates()[d0_];
            final int c1 = iter.coordinates()[d1_];
            if (c0 == 0 && c1 == 0) {
                // lower left corner
                a00_[i] = a01_[i] = a02_[i] = a10_[i] = a20_[i] = 0.0;
                final double value = 1.0 / (hp_d0 * hp_d1);
                a11_[i] = value;
                a22_[i] = value;
                a21_[i] = -value;
                a12_[i] = -value;
            } else if (c0 == mesher.layout().dim()[d0_] - 1 && c1 == 0) {
                // upper left corner
                a22_[i] = a21_[i] = a20_[i] = a10_[i] = a00_[i] = 0.0;
                final double value = 1.0 / (hm_d0 * hp_d1);
                a01_[i] = value;
                a12_[i] = value;
                a11_[i] = -value;
                a02_[i] = -value;
            } else if (c0 == 0 && c1 == mesher.layout().dim()[d1_] - 1) {
                // lower right corner
                a00_[i] = a01_[i] = a02_[i] = a12_[i] = a22_[i] = 0.0;
                final double value = 1.0 / (hp_d0 * hm_d1);
                a10_[i] = value;
                a21_[i] = value;
                a20_[i] = -value;
                a11_[i] = -value;
            } else if (c0 == mesher.layout().dim()[d0_] - 1
                    && c1 == mesher.layout().dim()[d1_] - 1) {
                // upper right corner
                a20_[i] = a21_[i] = a22_[i] = a12_[i] = a02_[i] = 0.0;
                final double value = 1.0 / (hm_d0 * hm_d1);
                a00_[i] = value;
                a11_[i] = value;
                a10_[i] = -value;
                a01_[i] = -value;
            } else if (c0 == 0) {
                // lower side
                a00_[i] = a01_[i] = a02_[i] = 0.0;

                a10_[i] = hp_d1 / (hp_d0 * phim1);
                a20_[i] = -a10_[i];
                a21_[i] = (hp_d1 - hm_d1) / (hp_d0 * phi0);
                a11_[i] = -a21_[i];
                a22_[i] = hm_d1 / (hp_d0 * phip1);
                a12_[i] = -a22_[i];
            } else if (c0 == mesher.layout().dim()[d0_] - 1) {
                // upper side
                a20_[i] = a21_[i] = a22_[i] = 0.0;

                a00_[i] = hp_d1 / (hm_d0 * phim1);
                a10_[i] = -a00_[i];
                a11_[i] = (hp_d1 - hm_d1) / (hm_d0 * phi0);
                a01_[i] = -a11_[i];
                a12_[i] = hm_d1 / (hm_d0 * phip1);
                a02_[i] = -a12_[i];
            } else if (c1 == 0) {
                // left side
                a00_[i] = a10_[i] = a20_[i] = 0.0;

                a01_[i] = hp_d0 / (zetam1 * hp_d1);
                a02_[i] = -a01_[i];
                a12_[i] = (hp_d0 - hm_d0) / (zeta0 * hp_d1);
                a11_[i] = -a12_[i];
                a22_[i] = hm_d0 / (zetap1 * hp_d1);
                a21_[i] = -a22_[i];
            } else if (c1 == mesher.layout().dim()[d1_] - 1) {
                // right side
                a22_[i] = a12_[i] = a02_[i] = 0.0;

                a00_[i] = hp_d0 / (zetam1 * hm_d1);
                a01_[i] = -a00_[i];
                a11_[i] = (hp_d0 - hm_d0) / (zeta0 * hm_d1);
                a10_[i] = -a11_[i];
                a21_[i] = hm_d0 / (zetap1 * hm_d1);
                a20_[i] = -a21_[i];
            } else {
                a00_[i] = hp_d0 * hp_d1 / (zetam1 * phim1);
                a10_[i] = -(hp_d0 - hm_d0) * hp_d1 / (zeta0 * phim1);
                a20_[i] = -hm_d0 * hp_d1 / (zetap1 * phim1);
                a01_[i] = -hp_d0 * (hp_d1 - hm_d1) / (zetam1 * phi0);
                a11_[i] = (hp_d0 - hm_d0) * (hp_d1 - hm_d1) / (zeta0 * phi0);
                a21_[i] = hm_d0 * (hp_d1 - hm_d1) / (zetap1 * phi0);
                a02_[i] = -hp_d0 * hm_d1 / (zetam1 * phip1);
                a12_[i] = hm_d1 * (hp_d0 - hm_d0) / (zeta0 * phip1);
                a22_[i] = hm_d0 * hm_d1 / (zetap1 * phip1);
            }
        }
    }
}
