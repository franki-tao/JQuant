package jquant.methods.finitedifferences.operators;

import jquant.math.Array;
import jquant.methods.finitedifferences.meshers.FdmMesher;

import static jquant.math.CommonUtil.QL_REQUIRE;

/**
 * Nine-point linear operator.
 *
 * <p>Port of {@code ql/methods/finitedifferences/operators/ninepointlinearop.{hpp,cpp}}.
 */
public class NinePointLinearOp implements FdmLinearOp {
    protected int d0_, d1_;
    protected int[] i00_, i10_, i20_;
    protected int[] i01_, i21_;
    protected int[] i02_, i12_, i22_;
    protected double[] a00_, a10_, a20_;
    protected double[] a01_, a11_, a21_;
    protected double[] a02_, a12_, a22_;

    protected FdmMesher mesher_;

    public NinePointLinearOp(int d0, int d1, FdmMesher mesher) {
        d0_ = d0;
        d1_ = d1;
        mesher_ = mesher;

        final int size = mesher.layout().size();
        i00_ = new int[size];
        i10_ = new int[size];
        i20_ = new int[size];
        i01_ = new int[size];
        i21_ = new int[size];
        i02_ = new int[size];
        i12_ = new int[size];
        i22_ = new int[size];
        a00_ = new double[size];
        a10_ = new double[size];
        a20_ = new double[size];
        a01_ = new double[size];
        a11_ = new double[size];
        a21_ = new double[size];
        a02_ = new double[size];
        a12_ = new double[size];
        a22_ = new double[size];

        QL_REQUIRE(d0_ != d1_
                        && d0_ < mesher.layout().dim().length
                        && d1_ < mesher.layout().dim().length,
                "inconsistent derivative directions");

        for (FdmLinearOpIterator iter : mesher.layout()) {
            final int i = iter.index();

            i10_[i] = mesher.layout().neighbourhood(iter, d1_, -1);
            i01_[i] = mesher.layout().neighbourhood(iter, d0_, -1);
            i21_[i] = mesher.layout().neighbourhood(iter, d0_, 1);
            i12_[i] = mesher.layout().neighbourhood(iter, d1_, 1);
            i00_[i] = mesher.layout().neighbourhood(iter, d0_, -1, d1_, -1);
            i20_[i] = mesher.layout().neighbourhood(iter, d0_, 1, d1_, -1);
            i02_[i] = mesher.layout().neighbourhood(iter, d0_, -1, d1_, 1);
            i22_[i] = mesher.layout().neighbourhood(iter, d0_, 1, d1_, 1);
        }
    }

    protected NinePointLinearOp() {
    }

    @Override
    public Array apply(Array u) {
        QL_REQUIRE(u.size() == mesher_.layout().size(),
                "inconsistent length of r " + u.size() + " vs "
                        + mesher_.layout().size());

        final Array retVal = new Array(u.size());
        for (int i = 0; i < retVal.size(); ++i) {
            retVal.set(i,
                    a00_[i] * u.get(i00_[i])
                            + a01_[i] * u.get(i01_[i])
                            + a02_[i] * u.get(i02_[i])
                            + a10_[i] * u.get(i10_[i])
                            + a11_[i] * u.get(i)
                            + a12_[i] * u.get(i12_[i])
                            + a20_[i] * u.get(i20_[i])
                            + a21_[i] * u.get(i21_[i])
                            + a22_[i] * u.get(i22_[i]));
        }
        return retVal;
    }

    public NinePointLinearOp mult(Array u) {
        final NinePointLinearOp retVal = new NinePointLinearOp(d0_, d1_, mesher_);

        final int size = mesher_.layout().size();
        for (int i = 0; i < size; ++i) {
            final double s = u.get(i);
            retVal.a11_[i] = a11_[i] * s;
            retVal.a00_[i] = a00_[i] * s;
            retVal.a01_[i] = a01_[i] * s;
            retVal.a02_[i] = a02_[i] * s;
            retVal.a10_[i] = a10_[i] * s;
            retVal.a20_[i] = a20_[i] * s;
            retVal.a21_[i] = a21_[i] * s;
            retVal.a12_[i] = a12_[i] * s;
            retVal.a22_[i] = a22_[i] * s;
        }
        return retVal;
    }
}
