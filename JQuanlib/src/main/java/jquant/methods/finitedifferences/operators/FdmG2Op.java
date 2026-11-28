package jquant.methods.finitedifferences.operators;

import jquant.math.Array;
import jquant.methods.finitedifferences.meshers.FdmMesher;
import jquant.models.shortrate.TwoFactorModel;
import jquant.models.shortrate.twofactormodels.G2;

/**
 * FDM operator for the G2 two-factor model.
 *
 * <p>Port of {@code ql/methods/finitedifferences/operators/fdmg2op.{hpp,cpp}}.
 */
public class FdmG2Op implements FdmLinearOpComposite {

    private final int direction1_;
    private final int direction2_;
    private final Array x_;
    private final Array y_;
    private final TripleBandLinearOp dxMap_;
    private final TripleBandLinearOp dyMap_;
    private final NinePointLinearOp corrMap_;
    private final TripleBandLinearOp mapX_;
    private final TripleBandLinearOp mapY_;
    private final G2 model_;

    public FdmG2Op(final FdmMesher mesher,
                   final G2 model,
                   int direction1,
                   int direction2) {
        direction1_ = direction1;
        direction2_ = direction2;
        x_ = mesher.locations(direction1);
        y_ = mesher.locations(direction2);

        dxMap_ = new FirstDerivativeOp(direction1, mesher)
                .mult(x_.mutiply(-model.a()))
                .add(new SecondDerivativeOp(direction1, mesher)
                        .mult(new Array(mesher.layout().size(),
                                0.5 * model.sigma() * model.sigma())));

        dyMap_ = new FirstDerivativeOp(direction2, mesher)
                .mult(y_.mutiply(-model.b()))
                .add(new SecondDerivativeOp(direction2, mesher)
                        .mult(new Array(mesher.layout().size(),
                                0.5 * model.eta() * model.eta())));

        corrMap_ = new SecondOrderMixedDerivativeOp(direction1, direction2, mesher)
                .mult(new Array(mesher.layout().size(),
                        model.rho() * model.sigma() * model.eta()));

        mapX_ = new TripleBandLinearOp(direction1, mesher);
        mapY_ = new TripleBandLinearOp(direction2, mesher);

        model_ = model;
    }

    @Override
    public int size() {
        return 2;
    }

    @Override
    public void setTime(double t1, double t2) {
        final TwoFactorModel.ShortRateDynamics dynamics = model_.dynamics();

        final double phi = 0.5 * (dynamics.shortRate(t1, 0.0, 0.0)
                + dynamics.shortRate(t2, 0.0, 0.0));

        final Array hr = x_.add(y_).add(phi).mutiply(-0.5);
        mapX_.axpyb(new Array(0), dxMap_, dxMap_, hr);
        mapY_.axpyb(new Array(0), dyMap_, dyMap_, hr);
    }

    @Override
    public Array apply(Array r) {
        return mapX_.apply(r).add(mapY_.apply(r)).add(apply_mixed(r));
    }

    @Override
    public Array apply_mixed(Array r) {
        return corrMap_.apply(r);
    }

    @Override
    public Array apply_direction(int direction, Array r) {
        if (direction == direction1_) {
            return mapX_.apply(r);
        } else if (direction == direction2_) {
            return mapY_.apply(r);
        } else {
            return new Array(r.size(), 0.0);
        }
    }

    @Override
    public Array solve_splitting(int direction, Array r, double a) {
        if (direction == direction1_) {
            return mapX_.solve_splitting(r, a, 1.0);
        } else if (direction == direction2_) {
            return mapY_.solve_splitting(r, a, 1.0);
        } else {
            return new Array(r.size(), 0.0);
        }
    }

    @Override
    public Array preconditioner(Array r, double dt) {
        return solve_splitting(direction1_, r, dt);
    }
}
