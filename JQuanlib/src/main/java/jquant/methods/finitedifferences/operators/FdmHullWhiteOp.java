package jquant.methods.finitedifferences.operators;

import jquant.math.Array;
import jquant.methods.finitedifferences.meshers.FdmMesher;
import jquant.models.shortrate.OneFactorModel;
import jquant.models.shortrate.onefactormodels.HullWhite;

/**
 * FDM operator for the Hull-White interest rate model.
 *
 * <p>Port of {@code ql/methods/finitedifferences/operators/fdmhullwhiteop.{hpp,cpp}}.
 */
public class FdmHullWhiteOp implements FdmLinearOpComposite {

    private final int direction_;
    private final Array x_;
    private final TripleBandLinearOp dzMap_;
    private final TripleBandLinearOp mapT_;
    private final HullWhite model_;

    public FdmHullWhiteOp(final FdmMesher mesher,
                          final HullWhite model,
                          int direction) {
        direction_ = direction;
        x_ = mesher.locations(direction);
        dzMap_ = new FirstDerivativeOp(direction, mesher)
                .mult(x_.mutiply(-model.a()))
                .add(new SecondDerivativeOp(direction, mesher)
                        .mult(new Array(mesher.layout().size(),
                                0.5 * model.sigma() * model.sigma())));
        mapT_ = new TripleBandLinearOp(direction, mesher);
        model_ = model;
    }

    @Override
    public int size() {
        return 1;
    }

    @Override
    public void setTime(double t1, double t2) {
        final OneFactorModel.ShortRateDynamics dynamics = model_.dynamics();

        final double phi = 0.5 * (dynamics.shortRate(t1, 0.0)
                + dynamics.shortRate(t2, 0.0));

        mapT_.axpyb(new Array(0), dzMap_, dzMap_,
                x_.add(phi).mutiply(-1.0));
    }

    @Override
    public Array apply(Array r) {
        return mapT_.apply(r);
    }

    @Override
    public Array apply_mixed(Array r) {
        return new Array(r.size(), 0.0);
    }

    @Override
    public Array apply_direction(int direction, Array r) {
        if (direction == direction_) {
            return mapT_.apply(r);
        } else {
            return new Array(r.size(), 0.0);
        }
    }

    @Override
    public Array solve_splitting(int direction, Array r, double a) {
        if (direction == direction_) {
            return mapT_.solve_splitting(r, a, 1.0);
        } else {
            return new Array(r.size(), 0.0);
        }
    }

    @Override
    public Array preconditioner(Array r, double dt) {
        return solve_splitting(direction_, r, dt);
    }
}
