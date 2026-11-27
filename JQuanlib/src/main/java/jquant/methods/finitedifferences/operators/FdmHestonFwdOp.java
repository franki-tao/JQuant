package jquant.methods.finitedifferences.operators;

import jquant.math.Array;
import jquant.methods.finitedifferences.meshers.FdmMesher;
import jquant.processes.HestonProcess;
import jquant.termstructures.YieldTermStructure;
import jquant.termstructures.volatility.equityfx.LocalVolTermStructure;

import static jquant.Compounding.Continuous;
import static jquant.math.CommonUtil.QL_FAIL;
import static jquant.time.Frequency.ANNUAL;

/**
 * Forward Fokker-Planck operator for the Heston process.
 *
 * <p>Port of
 * {@code ql/methods/finitedifferences/operators/fdmhestonfwdop.{hpp,cpp}}.
 */
public class FdmHestonFwdOp implements FdmLinearOpComposite {

    private final FdmSquareRootFwdOp.TransformationType type_;
    private final double kappa_, theta_, sigma_, rho_, v0_, mixedSigma_;

    private final YieldTermStructure rTS_, qTS_;

    private final Array varianceValues_;
    private final FirstDerivativeOp dxMap_;
    private final ModTripleBandLinearOp dxxMap_;
    private final ModTripleBandLinearOp boundary_;
    private Array L_;

    private final TripleBandLinearOp mapX_;
    private final FdmSquareRootFwdOp mapY_;

    private final NinePointLinearOp correlation_;

    private final LocalVolTermStructure leverageFct_;
    private final FdmMesher mesher_;
    private final Array x_;

    public FdmHestonFwdOp(final FdmMesher mesher,
                          final HestonProcess process,
                          FdmSquareRootFwdOp.TransformationType type) {
        this(mesher, process, type, null, 1.0);
    }

    public FdmHestonFwdOp(final FdmMesher mesher,
                          final HestonProcess process,
                          FdmSquareRootFwdOp.TransformationType type,
                          LocalVolTermStructure leverageFct) {
        this(mesher, process, type, leverageFct, 1.0);
    }

    public FdmHestonFwdOp(final FdmMesher mesher,
                          final HestonProcess process,
                          FdmSquareRootFwdOp.TransformationType type,
                          LocalVolTermStructure leverageFct,
                          double mixingFactor) {
        type_ = type;
        kappa_ = process.kappa();
        theta_ = process.theta();
        sigma_ = process.sigma();
        rho_ = process.rho();
        v0_ = process.v0();
        mixedSigma_ = mixingFactor * sigma_;
        rTS_ = process.riskFreeRate().currentLink();
        qTS_ = process.dividendYield().currentLink();
        varianceValues_ = mesher.locations(1).mutiply(0.5);
        dxMap_ = new FirstDerivativeOp(0, mesher);

        final Array vLocations = mesher.locations(1);
        final Array secondDerivativeFactor = new Array(vLocations.size());
        for (int i = 0; i < vLocations.size(); ++i) {
            secondDerivativeFactor.set(i,
                    type == FdmSquareRootFwdOp.TransformationType.Log
                            ? 0.5 * Math.exp(vLocations.get(i))
                            : 0.5 * vLocations.get(i));
        }
        dxxMap_ = new ModTripleBandLinearOp(
                new SecondDerivativeOp(0, mesher).mult(secondDerivativeFactor));

        boundary_ = new ModTripleBandLinearOp(
                new SecondDerivativeOp(0, mesher).mult(
                        new Array(mesher.locations(0).size(), 0.0)));

        mapX_ = new TripleBandLinearOp(0, mesher);
        mapY_ = new FdmSquareRootFwdOp(mesher, kappa_, theta_, mixedSigma_, 1, type);

        final Array correlationFactor;
        if (type == FdmSquareRootFwdOp.TransformationType.Log) {
            correlationFactor = new Array(mesher.layout().size(), rho_ * mixedSigma_);
        } else {
            correlationFactor = mesher.locations(1).mutiply(rho_ * mixedSigma_);
        }
        correlation_ = new SecondOrderMixedDerivativeOp(0, 1, mesher)
                .mult(correlationFactor);

        leverageFct_ = leverageFct;
        mesher_ = mesher;
        x_ = new Array(0);

        // zero flux boundary condition
        final int n = mesher.layout().dim()[1];
        final double lowerBoundaryFactor = mapY_.lowerBoundaryFactor(type);
        final double upperBoundaryFactor = mapY_.upperBoundaryFactor(type);

        final double logFacLow = type == FdmSquareRootFwdOp.TransformationType.Log
                ? Math.exp(mapY_.v(0)) : 1.0;
        final double logFacUpp = type == FdmSquareRootFwdOp.TransformationType.Log
                ? Math.exp(mapY_.v(n + 1)) : 1.0;

        final double alpha = -2 * rho_ / mixedSigma_ * lowerBoundaryFactor * logFacLow;
        final double beta = -2 * rho_ / mixedSigma_ * upperBoundaryFactor * logFacUpp;

        final ModTripleBandLinearOp fDx =
                new ModTripleBandLinearOp(new FirstDerivativeOp(0, mesher));

        for (FdmLinearOpIterator iter : mesher.layout()) {
            if (iter.coordinates()[1] == 0) {
                final int idx = iter.index();
                if (leverageFct_ == null) {
                    dxxMap_.setUpper(idx, dxxMap_.upper(idx) + alpha * fDx.upper(idx));
                    dxxMap_.setDiag(idx, dxxMap_.diag(idx) + alpha * fDx.diag(idx));
                    dxxMap_.setLower(idx, dxxMap_.lower(idx) + alpha * fDx.lower(idx));
                }
                boundary_.setUpper(idx, alpha * fDx.upper(idx));
                boundary_.setDiag(idx, alpha * fDx.diag(idx));
                boundary_.setLower(idx, alpha * fDx.lower(idx));
            } else if (iter.coordinates()[1] == n - 1) {
                final int idx = iter.index();

                if (leverageFct_ == null) {
                    dxxMap_.setUpper(idx, dxxMap_.upper(idx) + beta * fDx.upper(idx));
                    dxxMap_.setDiag(idx, dxxMap_.diag(idx) + beta * fDx.diag(idx));
                    dxxMap_.setLower(idx, dxxMap_.lower(idx) + beta * fDx.lower(idx));
                }
                boundary_.setUpper(idx, beta * fDx.upper(idx));
                boundary_.setDiag(idx, beta * fDx.diag(idx));
                boundary_.setLower(idx, beta * fDx.lower(idx));
            }
        }
    }

    @Override
    public int size() {
        return 2;
    }

    @Override
    public void setTime(double t1, double t2) {
        final double r = rTS_.forwardRate(t1, t2, Continuous, ANNUAL, false).rate();
        final double q = qTS_.forwardRate(t1, t2, Continuous, ANNUAL, false).rate();
        if (leverageFct_ != null) {
            L_ = getLeverageFctSlice(t1, t2);
            final Array Lsquare = L_.multiply(L_);
            if (type_ == FdmSquareRootFwdOp.TransformationType.Plain) {
                final Array Ldrift = L_.mutiply(rho_ * mixedSigma_);
                mapX_.axpyb(new Array(1, -r + q), dxMap_,
                        dxxMap_.multR(Lsquare).add(boundary_.multR(L_))
                                .add(dxMap_.multR(Ldrift))
                                .add(dxMap_.mult(varianceValues_).multR(Lsquare)),
                        new Array(0));
            } else if (type_ == FdmSquareRootFwdOp.TransformationType.Power) {
                final Array Ldrift = L_.mutiply(
                        rho_ * 2.0 * kappa_ * theta_ / mixedSigma_);
                mapX_.axpyb(new Array(1, -r + q), dxMap_,
                        dxxMap_.multR(Lsquare).add(boundary_.multR(L_))
                                .add(dxMap_.multR(Ldrift))
                                .add(dxMap_.mult(varianceValues_).multR(Lsquare)),
                        new Array(0));
            } else if (type_ == FdmSquareRootFwdOp.TransformationType.Log) {
                final Array exp2 = new Array(varianceValues_.size());
                for (int i = 0; i < exp2.size(); ++i)
                    exp2.set(i, 0.5 * Math.exp(2.0 * varianceValues_.get(i)));
                mapX_.axpyb(new Array(1, -r + q), dxMap_,
                        dxxMap_.multR(Lsquare).add(boundary_.multR(L_))
                                .add(dxMap_.mult(exp2).multR(Lsquare)),
                        new Array(0));
            }
        } else {
            final Array drift = new Array(varianceValues_.size());
            if (type_ == FdmSquareRootFwdOp.TransformationType.Plain) {
                for (int i = 0; i < drift.size(); ++i)
                    drift.set(i, -r + q + rho_ * mixedSigma_ + varianceValues_.get(i));
            } else if (type_ == FdmSquareRootFwdOp.TransformationType.Power) {
                for (int i = 0; i < drift.size(); ++i)
                    drift.set(i, -r + q
                            + rho_ * 2.0 * kappa_ * theta_ / mixedSigma_
                            + varianceValues_.get(i));
            } else if (type_ == FdmSquareRootFwdOp.TransformationType.Log) {
                for (int i = 0; i < drift.size(); ++i)
                    drift.set(i, -r + q
                            + 0.5 * Math.exp(2.0 * varianceValues_.get(i)));
            }
            mapX_.axpyb(drift, dxMap_, dxxMap_, new Array(0));
        }
    }

    @Override
    public Array apply(Array u) {
        if (leverageFct_ != null) {
            return mapX_.apply(u).add(mapY_.apply(u))
                    .add(correlation_.apply(L_.multiply(u)));
        } else {
            return mapX_.apply(u).add(mapY_.apply(u))
                    .add(correlation_.apply(u));
        }
    }

    @Override
    public Array apply_mixed(Array u) {
        if (leverageFct_ != null) {
            return correlation_.apply(L_.multiply(u));
        } else {
            return correlation_.apply(u);
        }
    }

    @Override
    public Array apply_direction(int direction, Array u) {
        if (direction == 0)
            return mapX_.apply(u);
        else if (direction == 1)
            return mapY_.apply(u);
        else {
            QL_FAIL("direction too large");
            return null;
        }
    }

    @Override
    public Array solve_splitting(int direction, Array u, double s) {
        if (direction == 0) {
            return mapX_.solve_splitting(u, s, 1.0);
        } else if (direction == 1) {
            return mapY_.solve_splitting(1, u, s);
        } else {
            QL_FAIL("direction too large");
            return null;
        }
    }

    @Override
    public Array preconditioner(Array u, double dt) {
        return solve_splitting(1, u, dt);
    }

    private Array getLeverageFctSlice(double t1, double t2) {
        final Array v = new Array(mesher_.layout().size(), 1.0);

        if (leverageFct_ == null)
            return v;

        final double t = 0.5 * (t1 + t2);
        final double time = Math.min(leverageFct_.maxTime(), t);

        for (FdmLinearOpIterator iter : mesher_.layout()) {
            final int nx = iter.coordinates()[0];

            if (iter.coordinates()[1] == 0) {
                final double x = Math.exp(mesher_.location(iter, 0));
                final double spot = Math.min(leverageFct_.maxStrike(),
                        Math.max(leverageFct_.minStrike(), x));
                v.set(nx, Math.max(0.01, leverageFct_.localVol(time, spot, true)));
            } else {
                v.set(iter.index(), v.get(nx));
            }
        }
        return v;
    }
}
