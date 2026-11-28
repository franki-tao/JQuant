package jquant.methods.finitedifferences.operators;

import jquant.math.Array;
import jquant.methods.finitedifferences.meshers.FdmMesher;

import static jquant.math.CommonUtil.QL_FAIL;

/**
 * Dupire local-volatility pricing operator.
 *
 * <p>Ported from QuantLib
 * {@code ql/experimental/finitedifferences/fdmdupire1dop.{hpp,cpp}}.
 * Note that time is reversed in order to make backward solvers work.
 */
public class FdmDupire1dOp implements FdmLinearOpComposite {

    private final FdmMesher mesher_;
    private final Array localVolatility_;
    private final TripleBandLinearOp mapT_;

    public FdmDupire1dOp(FdmMesher mesher,
                         Array localVolatility) {
        mesher_ = mesher;
        localVolatility_ = localVolatility;

        final Array multiplier = new Array(localVolatility.size());
        for (int i = 0; i < multiplier.size(); ++i) {
            final double v = localVolatility.get(i);
            multiplier.set(i, 0.5 * v * v);
        }
        mapT_ = new SecondDerivativeOp(0, mesher).mult(multiplier);
    }

    @Override
    public void setTime(double t1, double t2) {
    }

    @Override
    public int size() {
        return 1;
    }

    @Override
    public Array apply(Array u) {
        return mapT_.apply(u);
    }

    @Override
    public Array apply_direction(int direction, Array r) {
        if (direction == 0)
            return mapT_.apply(r);
        QL_FAIL("direction too large");
        return null;
    }

    @Override
    public Array apply_mixed(Array r) {
        return r;
    }

    @Override
    public Array solve_splitting(int direction, Array r, double a) {
        if (direction == 0)
            return mapT_.solve_splitting(r, a, 1.0);
        QL_FAIL("direction too large");
        return null;
    }

    @Override
    public Array preconditioner(Array r, double dt) {
        return solve_splitting(0, r, dt);
    }
}
