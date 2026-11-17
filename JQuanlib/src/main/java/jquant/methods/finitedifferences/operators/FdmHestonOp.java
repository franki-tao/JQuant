package jquant.methods.finitedifferences.operators;

import jquant.math.Array;
import jquant.methods.finitedifferences.meshers.FdmMesher;
import jquant.processes.HestonProcess;
import jquant.termstructures.YieldTermStructure;
import jquant.termstructures.volatility.equityfx.LocalVolTermStructure;
import jquant.utilities.FdmQuantoHelper;

import static jquant.Compounding.Continuous;
import static jquant.math.CommonUtil.QL_FAIL;
import static jquant.time.Frequency.ANNUAL;

/**
 * Heston linear operator.
 *
 * <p>Port of {@code ql/methods/finitedifferences/operators/fdmhestonop.{hpp,cpp}}.
 */
public class FdmHestonOp implements FdmLinearOpComposite {
    private final NinePointLinearOp correlationMap_;
    private final FdmHestonVariancePart dyMap_;
    private final FdmHestonEquityPart dxMap_;

    public FdmHestonOp(final FdmMesher mesher,
                       final HestonProcess hestonProcess,
                       final FdmQuantoHelper quantoHelper,
                       final LocalVolTermStructure leverageFct,
                       double mixingFactor) {
        correlationMap_ = new SecondOrderMixedDerivativeOp(0, 1, mesher)
                .mult(mesher.locations(1)
                        .mutiply(hestonProcess.rho()
                                * hestonProcess.sigma()
                                * mixingFactor));
        dyMap_ = new FdmHestonVariancePart(
                mesher,
                hestonProcess.riskFreeRate().currentLink(),
                hestonProcess.sigma() * mixingFactor,
                hestonProcess.kappa(),
                hestonProcess.theta());
        dxMap_ = new FdmHestonEquityPart(
                mesher,
                hestonProcess.riskFreeRate().currentLink(),
                hestonProcess.dividendYield().currentLink(),
                quantoHelper,
                leverageFct);
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
                .add(dxMap_.getL().multiply(correlationMap_.apply(u)));
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
        return dxMap_.getL().multiply(correlationMap_.apply(r));
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
 * Equity part of the Heston operator.
 *
 * <p>Port of {@code FdmHestonEquityPart} from
 * {@code ql/methods/finitedifferences/operators/fdmhestonop.{hpp,cpp}}.
 */
class FdmHestonEquityPart {
    private final Array varianceValues_;
    private final Array volatilityValues_;
    private Array L_ = new Array(0);

    private final FirstDerivativeOp dxMap_;
    private final TripleBandLinearOp dxxMap_;
    private final TripleBandLinearOp mapT_;

    private final FdmMesher mesher_;
    private final YieldTermStructure rTS_, qTS_;
    private final FdmQuantoHelper quantoHelper_;
    private final LocalVolTermStructure leverageFct_;

    FdmHestonEquityPart(final FdmMesher mesher,
                        YieldTermStructure rTS,
                        YieldTermStructure qTS,
                        FdmQuantoHelper quantoHelper,
                        LocalVolTermStructure leverageFct) {
        varianceValues_ = mesher.locations(1).mutiply(0.5);
        dxMap_ = new FirstDerivativeOp(0, mesher);
        dxxMap_ = new SecondDerivativeOp(0, mesher)
                .mult(mesher.locations(1).mutiply(0.5));
        mapT_ = new TripleBandLinearOp(0, mesher);
        mesher_ = mesher;
        rTS_ = rTS;
        qTS_ = qTS;
        quantoHelper_ = quantoHelper;
        leverageFct_ = leverageFct;

        // on the boundary s_min and s_max the second derivative
        // d^2V/dS^2 is zero and due to Ito's Lemma the variance term
        // in the drift should vanish.
        for (FdmLinearOpIterator iter : mesher_.layout()) {
            if (iter.coordinates()[0] == 0
                    || iter.coordinates()[0] == mesher_.layout().dim()[0] - 1) {
                varianceValues_.set(iter.index(), 0.0);
            }
        }
        volatilityValues_ = varianceValues_.mutiply(2.0)
                .transform(Math::sqrt);
    }

    void setTime(double t1, double t2) {
        final double r = rTS_.forwardRate(t1, t2, Continuous, ANNUAL, false).rate();
        final double q = qTS_.forwardRate(t1, t2, Continuous, ANNUAL, false).rate();

        L_ = getLeverageFctSlice(t1, t2);
        final Array Lsquare = L_.multiply(L_);

        Array drift = varianceValues_.multiply(Lsquare).mutiply(-1.0)
                .add(r - q);
        if (quantoHelper_ != null) {
            drift = drift.subtract(quantoHelper_.quantoAdjustment(
                    volatilityValues_.multiply(L_), t1, t2));
        }

        mapT_.axpyb(drift, dxMap_, dxxMap_.mult(Lsquare),
                new Array(1, -0.5 * r));
    }

    Array getLeverageFctSlice(double t1, double t2) {
        final Array v = new Array(mesher_.layout().size(), 1.0);

        if (leverageFct_ == null) {
            return v;
        }
        final double t = 0.5 * (t1 + t2);
        final double time = Math.min(leverageFct_.maxTime(), t);

        for (FdmLinearOpIterator iter : mesher_.layout()) {
            final int nx = iter.coordinates()[0];

            if (iter.coordinates()[1] == 0) {
                final double x = Math.exp(mesher_.location(iter, 0));
                final double spot = Math.min(leverageFct_.maxStrike(),
                        Math.max(leverageFct_.minStrike(), x));
                v.set(nx, Math.max(0.01,
                        leverageFct_.localVol(time, spot, true)));
            } else {
                v.set(iter.index(), v.get(nx));
            }
        }
        return v;
    }

    TripleBandLinearOp getMap() {
        return mapT_;
    }

    Array getL() {
        return L_;
    }
}

/**
 * Variance part of the Heston operator.
 *
 * <p>Port of {@code FdmHestonVariancePart} from
 * {@code ql/methods/finitedifferences/operators/fdmhestonop.{hpp,cpp}}.
 */
class FdmHestonVariancePart {
    private final TripleBandLinearOp dyMap_;
    private final TripleBandLinearOp mapT_;

    private final YieldTermStructure rTS_;

    FdmHestonVariancePart(final FdmMesher mesher,
                          YieldTermStructure rTS,
                          double mixedSigma,
                          double kappa,
                          double theta) {
        final Array drift = mesher.locations(1)
                .transform(v -> kappa * (theta - v));
        dyMap_ = new SecondDerivativeOp(1, mesher)
                .mult(mesher.locations(1).mutiply(
                        0.5 * mixedSigma * mixedSigma))
                .add(new FirstDerivativeOp(1, mesher).mult(drift));
        mapT_ = new TripleBandLinearOp(1, mesher);
        rTS_ = rTS;
    }

    void setTime(double t1, double t2) {
        final double r = rTS_.forwardRate(t1, t2, Continuous, ANNUAL, false).rate();
        mapT_.axpyb(new Array(0), dyMap_, dyMap_, new Array(1, -0.5 * r));
    }

    TripleBandLinearOp getMap() {
        return mapT_;
    }
}
