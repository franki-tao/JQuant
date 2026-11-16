package jquant.methods.finitedifferences.operators;

import jquant.Compounding;
import jquant.math.Array;
import jquant.methods.finitedifferences.meshers.FdmMesher;
import jquant.processes.GeneralizedBlackScholesProcess;
import jquant.termstructures.YieldTermStructure;
import jquant.termstructures.volatility.equityfx.BlackVolTermStructure;
import jquant.termstructures.volatility.equityfx.LocalVolTermStructure;
import jquant.time.Frequency;
import jquant.utilities.FdmQuantoHelper;

import static jquant.math.MathUtils.squared;

/**
 * Black-Scholes linear operator.
 *
 * <p>Port of {@code ql/methods/finitedifferences/operators/fdmblackscholesop.{hpp,cpp}}.
 * Both the constant (non-local) and the local volatility paths are supported.
 */
public class FdmBlackScholesOp implements FdmLinearOpComposite {
    private final FdmMesher mesher_;
    private final YieldTermStructure rTS_, qTS_;
    private final BlackVolTermStructure volTS_;
    private final LocalVolTermStructure localVol_;
    private final Array x_;
    private final FirstDerivativeOp dxMap_;
    private final TripleBandLinearOp dxxMap_;
    private final TripleBandLinearOp mapT_;
    private final double strike_;
    private final double illegalLocalVolOverwrite_;
    private final int direction_;
    private final FdmQuantoHelper quantoHelper_;

    public FdmBlackScholesOp(FdmMesher mesher,
                             GeneralizedBlackScholesProcess bsProcess,
                             double strike,
                             boolean localVol,
                             double illegalLocalVolOverwrite,
                             int direction,
                             FdmQuantoHelper quantoHelper) {
        mesher_ = mesher;
        rTS_ = bsProcess.riskFreeRate().getValue();
        qTS_ = bsProcess.dividendYield().getValue();
        volTS_ = bsProcess.blackVolatility().getValue();
        if (localVol) {
            localVol_ = bsProcess.localVolatility().getValue();
            final Array locations = mesher.locations(direction);
            x_ = new Array(locations.size());
            for (int i = 0; i < locations.size(); i++) {
                x_.set(i, Math.exp(locations.get(i)));
            }
        } else {
            localVol_ = null;
            x_ = new Array(0);
        }
        dxMap_ = new FirstDerivativeOp(direction, mesher);
        dxxMap_ = new SecondDerivativeOp(direction, mesher);
        mapT_ = new TripleBandLinearOp(direction, mesher);
        strike_ = strike;
        illegalLocalVolOverwrite_ = illegalLocalVolOverwrite;
        direction_ = direction;
        quantoHelper_ = quantoHelper;
    }

    @Override
    public void setTime(double t1, double t2) {
        final double r = rTS_.forwardRate(t1, t2, Compounding.Continuous,
                Frequency.NO_FREQUENCY, false).rate();
        final double q = qTS_.forwardRate(t1, t2, Compounding.Continuous,
                Frequency.NO_FREQUENCY, false).rate();

        if (localVol_ != null) {
            final Array v = new Array(mesher_.layout().size());
            for (FdmLinearOpIterator iter : mesher_.layout()) {
                final int i = iter.index();

                if (illegalLocalVolOverwrite_ < 0.0) {
                    v.set(i, squared(localVol_.localVol(0.5 * (t1 + t2), x_.get(i), true)));
                } else {
                    try {
                        v.set(i, squared(localVol_.localVol(0.5 * (t1 + t2), x_.get(i), true)));
                    } catch (RuntimeException e) {
                        v.set(i, squared(illegalLocalVolOverwrite_));
                    }
                }
            }

            final Array halfV = v.mutiply(0.5);
            if (quantoHelper_ != null) {
                final Array drift = new Array(v.size(), r - q)
                        .subtract(halfV)
                        .subtract(quantoHelper_.quantoAdjustment(
                                v.transform(Math::sqrt), t1, t2));
                mapT_.axpyb(drift, dxMap_, dxxMap_.mult(halfV), new Array(1, -r));
            } else {
                mapT_.axpyb(new Array(v.size(), r - q).subtract(halfV), dxMap_,
                        dxxMap_.mult(halfV), new Array(1, -r));
            }
        } else {
            final int size = mesher_.layout().size();
            final double v = volTS_.blackForwardVariance(t1, t2, strike_, false) / (t2 - t1);

            final Array halfV = new Array(size, 0.5 * v);
            if (quantoHelper_ != null) {
                final Array equityVol = new Array(size, Math.sqrt(v));
                final Array drift = new Array(size, r - q - 0.5 * v)
                        .subtract(quantoHelper_.quantoAdjustment(equityVol, t1, t2));
                mapT_.axpyb(drift, dxMap_, dxxMap_.mult(halfV), new Array(1, -r));
            } else {
                mapT_.axpyb(new Array(1, r - q - 0.5 * v), dxMap_,
                        dxxMap_.mult(halfV), new Array(1, -r));
            }
        }
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
        if (direction == direction_) {
            return mapT_.apply(r);
        }
        return new Array(r.size());
    }

    @Override
    public Array apply_mixed(Array r) {
        return new Array(r.size());
    }

    @Override
    public Array solve_splitting(int direction, Array r, double dt) {
        if (direction == direction_) {
            return mapT_.solve_splitting(r, dt, 1.0);
        }
        return new Array(r);
    }

    @Override
    public Array preconditioner(Array r, double dt) {
        return solve_splitting(direction_, r, dt);
    }
}
