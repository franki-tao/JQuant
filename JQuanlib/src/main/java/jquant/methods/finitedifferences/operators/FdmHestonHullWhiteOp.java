package jquant.methods.finitedifferences.operators;

import jquant.math.Array;
import jquant.methods.finitedifferences.meshers.FdmMesher;
import jquant.models.shortrate.OneFactorModel;
import jquant.models.shortrate.onefactormodels.HullWhite;
import jquant.processes.HestonProcess;
import jquant.processes.HullWhiteProcess;
import jquant.termstructures.YieldTermStructure;

import static jquant.Compounding.Continuous;
import static jquant.math.CommonUtil.QL_FAIL;
import static jquant.math.CommonUtil.QL_REQUIRE;
import static jquant.time.Frequency.ANNUAL;

/**
 * Heston-Hull-White linear operator.
 *
 * <p>Port of
 * {@code ql/methods/finitedifferences/operators/fdmhestonhullwhiteop.{hpp,cpp}}.
 */
public class FdmHestonHullWhiteOp implements FdmLinearOpComposite {

    private final double v0_, kappa_, theta_, sigma_, rho_;
    private final HullWhite hwModel_;

    private final NinePointLinearOp hestonCorrMap_;
    private final NinePointLinearOp equityIrCorrMap_;
    private final TripleBandLinearOp dyMap_;
    private final FdmHestonHullWhiteEquityPart dxMap_;
    private final FdmHullWhiteOp hullWhiteOp_;

    public FdmHestonHullWhiteOp(final FdmMesher mesher,
                                final HestonProcess hestonProcess,
                                final HullWhiteProcess hwProcess,
                                double equityShortRateCorrelation) {
        v0_ = hestonProcess.v0();
        kappa_ = hestonProcess.kappa();
        theta_ = hestonProcess.theta();
        sigma_ = hestonProcess.sigma();
        rho_ = hestonProcess.rho();
        hwModel_ = new HullWhite(hestonProcess.riskFreeRate(),
                hwProcess.a(), hwProcess.sigma());

        hestonCorrMap_ = new SecondOrderMixedDerivativeOp(0, 1, mesher)
                .mult(mesher.locations(1)
                        .mutiply(rho_ * sigma_));
        equityIrCorrMap_ = new SecondOrderMixedDerivativeOp(0, 2, mesher)
                .mult(mesher.locations(1).transform(Math::sqrt)
                        .mutiply(hwProcess.sigma() * equityShortRateCorrelation));
        dyMap_ = new SecondDerivativeOp(1, mesher)
                .mult(mesher.locations(1).mutiply(0.5 * sigma_ * sigma_))
                .add(new FirstDerivativeOp(1, mesher)
                        .mult(mesher.locations(1).mutiply(-1.0)
                                .add(theta_).mutiply(kappa_)));
        dxMap_ = new FdmHestonHullWhiteEquityPart(mesher, hwModel_,
                hestonProcess.dividendYield().currentLink());
        hullWhiteOp_ = new FdmHullWhiteOp(mesher, hwModel_, 2);

        QL_REQUIRE(equityShortRateCorrelation * equityShortRateCorrelation
                        + hestonProcess.rho() * hestonProcess.rho() <= 1.0,
                "correlation matrix has negative eigenvalues");
    }

    @Override
    public void setTime(double t1, double t2) {
        dxMap_.setTime(t1, t2);
        hullWhiteOp_.setTime(t1, t2);
    }

    @Override
    public int size() {
        return 3;
    }

    @Override
    public Array apply(Array u) {
        return dyMap_.apply(u)
                .add(dxMap_.getMap().apply(u))
                .add(hullWhiteOp_.apply(u))
                .add(hestonCorrMap_.apply(u))
                .add(equityIrCorrMap_.apply(u));
    }

    @Override
    public Array apply_direction(int direction, Array r) {
        if (direction == 0) {
            return dxMap_.getMap().apply(r);
        } else if (direction == 1) {
            return dyMap_.apply(r);
        } else if (direction == 2) {
            return hullWhiteOp_.apply(r);
        } else {
            QL_FAIL("direction too large");
            return null;
        }
    }

    @Override
    public Array apply_mixed(Array r) {
        return hestonCorrMap_.apply(r).add(equityIrCorrMap_.apply(r));
    }

    @Override
    public Array solve_splitting(int direction, Array r, double a) {
        if (direction == 0) {
            return dxMap_.getMap().solve_splitting(r, a, 1.0);
        } else if (direction == 1) {
            return dyMap_.solve_splitting(r, a, 1.0);
        } else if (direction == 2) {
            return hullWhiteOp_.solve_splitting(2, r, a);
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
 * Equity part of the Heston-Hull-White operator.
 *
 * <p>Port of {@code FdmHestonHullWhiteEquityPart} from
 * {@code ql/methods/finitedifferences/operators/fdmhestonhullwhiteop.{hpp,cpp}}.
 */
class FdmHestonHullWhiteEquityPart {
    private final Array x_;
    private Array varianceValues_, volatilityValues_;
    private final FirstDerivativeOp dxMap_;
    private final TripleBandLinearOp dxxMap_;
    private final TripleBandLinearOp mapT_;

    private final HullWhite hwModel_;
    private final FdmMesher mesher_;
    private final YieldTermStructure qTS_;

    FdmHestonHullWhiteEquityPart(final FdmMesher mesher,
                                 HullWhite hwModel,
                                 YieldTermStructure qTS) {
        x_ = mesher.locations(2);
        varianceValues_ = mesher.locations(1).mutiply(0.5);
        dxMap_ = new FirstDerivativeOp(0, mesher);
        dxxMap_ = new SecondDerivativeOp(0, mesher)
                .mult(mesher.locations(1).mutiply(0.5));
        mapT_ = new TripleBandLinearOp(0, mesher);
        hwModel_ = hwModel;
        mesher_ = mesher;
        qTS_ = qTS;

        // on the boundary s_min and s_max the second derivative
        // d^2V/dS^2 is zero and due to Ito's Lemma the variance term
        // in the drift should vanish.
        for (FdmLinearOpIterator iter : mesher_.layout()) {
            if (iter.coordinates()[0] == 0
                    || iter.coordinates()[0] == mesher_.layout().dim()[0] - 1) {
                varianceValues_.set(iter.index(), 0.0);
            }
        }
        volatilityValues_ = varianceValues_.mutiply(2.0).transform(Math::sqrt);
    }

    void setTime(double t1, double t2) {
        final OneFactorModel.ShortRateDynamics dynamics = hwModel_.dynamics();

        final double phi = 0.5 * (dynamics.shortRate(t1, 0.0)
                + dynamics.shortRate(t2, 0.0));

        final double q = qTS_.forwardRate(t1, t2, Continuous, ANNUAL, false).rate();

        mapT_.axpyb(x_.add(phi - q).subtract(varianceValues_),
                dxMap_, dxxMap_, new Array(0));
    }

    TripleBandLinearOp getMap() {
        return mapT_;
    }
}
