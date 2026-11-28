package jquant.methods.finitedifferences.operators;

import jquant.math.Array;
import jquant.methods.finitedifferences.meshers.FdmMesher;

import static jquant.math.CommonUtil.QL_FAIL;

/**
 * ZABR linear pricing operator.
 *
 * <p>Ported from QuantLib
 * {@code ql/experimental/finitedifferences/fdmzabrop.{hpp,cpp}}.
 */
public class FdmZabrOp implements FdmLinearOpComposite {

    private final Array volatilityValues_;
    private final Array forwardValues_;
    private final NinePointLinearOp dxyMap_;
    private final FdmZabrUnderlyingPart dxMap_;
    private final FdmZabrVolatilityPart dyMap_;

    public FdmZabrOp(FdmMesher mesher,
                     double beta,
                     double nu,
                     double rho,
                     double gamma) {
        volatilityValues_ = mesher.locations(1);
        forwardValues_ = mesher.locations(0);
        dxyMap_ = new SecondOrderMixedDerivativeOp(0, 1, mesher)
                .mult(volatilityValues_
                        .transform(x -> Math.pow(Math.abs(x), gamma + 1.0))
                        .multiply(forwardValues_
                                .transform(x -> Math.pow(x, beta)))
                        .mutiply(nu * rho));
        dxMap_ = new FdmZabrUnderlyingPart(mesher, beta, nu, rho, gamma);
        dyMap_ = new FdmZabrVolatilityPart(mesher, beta, nu, rho, gamma);
    }

    @Override
    public void setTime(double t1, double t2) {
        dxMap_.setTime(t1, t2);
        dyMap_.setTime(t1, t2);
    }

    @Override
    public int size() {
        return 2;
    }

    @Override
    public Array apply(Array u) {
        return dyMap_.getMap().apply(u)
                .add(dxMap_.getMap().apply(u))
                .add(dxyMap_.apply(u));
    }

    @Override
    public Array apply_direction(int direction, Array r) {
        if (direction == 0) {
            return dxMap_.getMap().apply(r);
        } else if (direction == 1) {
            return dyMap_.getMap().apply(r);
        } else {
            QL_FAIL("direction too large");
            return null;
        }
    }

    @Override
    public Array apply_mixed(Array r) {
        return dxyMap_.apply(r);
    }

    @Override
    public Array solve_splitting(int direction, Array r, double a) {
        if (direction == 0) {
            return dxMap_.getMap().solve_splitting(r, a, 1.0);
        } else if (direction == 1) {
            return dyMap_.getMap().solve_splitting(r, a, 1.0);
        } else {
            QL_FAIL("direction too large");
            return null;
        }
    }

    @Override
    public Array preconditioner(Array r, double dt) {
        return solve_splitting(0, r, dt);
    }
}

/**
 * Underlying part of the ZABR operator.
 *
 * <p>Port of {@code FdmZabrUnderlyingPart} from
 * {@code ql/experimental/finitedifferences/fdmzabrop.{hpp,cpp}}.
 */
class FdmZabrUnderlyingPart {
    private final Array volatilityValues_;
    private final Array forwardValues_;
    private final TripleBandLinearOp mapT_;
    private final FdmMesher mesher_;

    FdmZabrUnderlyingPart(FdmMesher mesher,
                          double beta,
                          double nu,
                          double rho,
                          double gamma) {
        volatilityValues_ = mesher.locations(1);
        forwardValues_ = mesher.locations(0);
        mapT_ = new SecondDerivativeOp(0, mesher)
                .mult(volatilityValues_.multiply(volatilityValues_)
                        .multiply(forwardValues_
                                .transform(x -> Math.pow(x, 2.0 * beta)))
                        .mutiply(0.5));
        mesher_ = mesher;
    }

    void setTime(double t1, double t2) {
    }

    TripleBandLinearOp getMap() {
        return mapT_;
    }
}

/**
 * Volatility part of the ZABR operator.
 *
 * <p>Port of {@code FdmZabrVolatilityPart} from
 * {@code ql/experimental/finitedifferences/fdmzabrop.{hpp,cpp}}.
 */
class FdmZabrVolatilityPart {
    private final Array volatilityValues_;
    private final Array forwardValues_;
    private final TripleBandLinearOp mapT_;
    private final FdmMesher mesher_;

    FdmZabrVolatilityPart(FdmMesher mesher,
                          double beta,
                          double nu,
                          double rho,
                          double gamma) {
        volatilityValues_ = mesher.locations(1);
        forwardValues_ = mesher.locations(0);
        mapT_ = new SecondDerivativeOp(1, mesher)
                .mult(volatilityValues_
                        .transform(x -> Math.pow(x, 2.0 * gamma))
                        .mutiply(0.5 * nu * nu));
        mesher_ = mesher;
    }

    void setTime(double t1, double t2) {
    }

    TripleBandLinearOp getMap() {
        return mapT_;
    }
}
