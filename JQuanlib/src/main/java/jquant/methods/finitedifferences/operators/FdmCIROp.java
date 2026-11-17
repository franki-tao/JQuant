package jquant.methods.finitedifferences.operators;

import jquant.math.Array;
import jquant.methods.finitedifferences.meshers.FdmMesher;
import jquant.processes.CoxIngersollRossProcess;
import jquant.processes.GeneralizedBlackScholesProcess;
import jquant.termstructures.YieldTermStructure;
import jquant.termstructures.volatility.equityfx.BlackVolTermStructure;

import static jquant.Compounding.Continuous;
import static jquant.math.CommonUtil.QL_FAIL;
import static jquant.time.Frequency.ANNUAL;

/**
 * CIR linear operator.
 *
 * <p>Port of {@code ql/methods/finitedifferences/operators/fdmcirop.{hpp,cpp}}.
 */
public class FdmCIROp implements FdmLinearOpComposite {
    private final FdmCIREquityPart dxMap_;
    private final FdmCIRRatesPart dyMap_;
    private final FdmCIRMixedPart dzMap_;

    public FdmCIROp(final FdmMesher mesher,
                    final CoxIngersollRossProcess cirProcess,
                    final GeneralizedBlackScholesProcess bsProcess,
                    double rho,
                    double strike) {
        dxMap_ = new FdmCIREquityPart(mesher, bsProcess, strike);
        dyMap_ = new FdmCIRRatesPart(mesher,
                cirProcess.volatility(), cirProcess.speed(), cirProcess.level());
        dzMap_ = new FdmCIRMixedPart(mesher, cirProcess, bsProcess, rho, strike);
    }

    @Override
    public void setTime(double t1, double t2) {
        dxMap_.setTime(t1, t2);
        dyMap_.setTime(t1, t2);
        dzMap_.setTime(t1, t2);
    }

    @Override
    public int size() {
        return 2;
    }

    @Override
    public Array apply(Array u) {
        final Array dx = dxMap_.getMap().apply(u);
        final Array dy = dyMap_.getMap().apply(u);
        final Array dz = dzMap_.getMap().apply(u);

        return dy.add(dx).add(dz);
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
        return dzMap_.getMap().apply(r);
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
        return solve_splitting(1, solve_splitting(0, r, dt), dt);
    }
}

/**
 * Equity part of the CIR operator.
 *
 * <p>Port of {@code FdmCIREquityPart} from
 * {@code ql/methods/finitedifferences/operators/fdmcirop.{hpp,cpp}}.
 */
class FdmCIREquityPart {
    private final FirstDerivativeOp dxMap_;
    private final TripleBandLinearOp dxxMap_;
    private final TripleBandLinearOp mapT_;

    private final FdmMesher mesher_;
    private final YieldTermStructure qTS_;
    private final double strike_;
    private final BlackVolTermStructure sigma1_;

    FdmCIREquityPart(final FdmMesher mesher,
                     final GeneralizedBlackScholesProcess bsProcess,
                     double strike) {
        dxMap_ = new FirstDerivativeOp(0, mesher);
        dxxMap_ = new SecondDerivativeOp(0, mesher);
        mapT_ = new TripleBandLinearOp(0, mesher);
        mesher_ = mesher;
        qTS_ = bsProcess.dividendYield().currentLink();
        strike_ = strike;
        sigma1_ = bsProcess.blackVolatility().currentLink();
    }

    void setTime(double t1, double t2) {
        final double q = qTS_.forwardRate(t1, t2, Continuous, ANNUAL, false).rate();

        final double v = sigma1_.blackForwardVariance(t1, t2, strike_, false) / (t2 - t1);

        mapT_.axpyb(mesher_.locations(1).add(-q - 0.5 * v), dxMap_,
                dxxMap_.mult(new Array(mesher_.layout().size(), v / 2)),
                mesher_.locations(1).mutiply(-0.5));
    }

    TripleBandLinearOp getMap() {
        return mapT_;
    }
}

/**
 * Rates part of the CIR operator.
 *
 * <p>Port of {@code FdmCIRRatesPart} from
 * {@code ql/methods/finitedifferences/operators/fdmcirop.{hpp,cpp}}.
 */
class FdmCIRRatesPart {
    private final TripleBandLinearOp dyMap_;
    private final TripleBandLinearOp mapT_;

    private final FdmMesher mesher_;

    FdmCIRRatesPart(final FdmMesher mesher,
                    double sigma, double kappa, double theta) {
        dyMap_ = new SecondDerivativeOp(1, mesher)
                .mult(mesher.locations(1).mutiply(sigma * sigma))
                .add(new FirstDerivativeOp(1, mesher)
                        .mult(mesher.locations(1)
                                .transform(v -> kappa * (theta - v))));
        mapT_ = new TripleBandLinearOp(1, mesher);
        mesher_ = mesher;
    }

    void setTime(double t1, double t2) {
        mapT_.axpyb(new Array(0), dyMap_, dyMap_,
                mesher_.locations(1).mutiply(-0.5));
    }

    TripleBandLinearOp getMap() {
        return mapT_;
    }
}

/**
 * Mixed derivative part of the CIR operator.
 *
 * <p>Port of {@code FdmCIRMixedPart} from
 * {@code ql/methods/finitedifferences/operators/fdmcirop.{hpp,cpp}}.
 */
class FdmCIRMixedPart {
    private final NinePointLinearOp dyMap_;
    private NinePointLinearOp mapT_;

    private final FdmMesher mesher_;
    private final BlackVolTermStructure sigma1_;
    private final double strike_;

    FdmCIRMixedPart(final FdmMesher mesher,
                    final CoxIngersollRossProcess cirProcess,
                    final GeneralizedBlackScholesProcess bsProcess,
                    double rho,
                    double strike) {
        dyMap_ = new SecondOrderMixedDerivativeOp(0, 1, mesher)
                .mult(new Array(mesher.layout().size(),
                        2 * rho * cirProcess.volatility()));
        mapT_ = new NinePointLinearOp(0, 1, mesher);
        mesher_ = mesher;
        sigma1_ = bsProcess.blackVolatility().currentLink();
        strike_ = strike;
    }

    void setTime(double t1, double t2) {
        final double v = Math.sqrt(
                sigma1_.blackForwardVariance(t1, t2, strike_, false) / (t2 - t1));
        mapT_ = dyMap_.mult(new Array(mesher_.layout().size(), v));
    }

    NinePointLinearOp getMap() {
        return mapT_;
    }
}
