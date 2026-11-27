package jquant.methods.finitedifferences.operators;

import jquant.Handle;
import jquant.math.Array;
import jquant.methods.finitedifferences.meshers.FdmMesher;
import jquant.processes.GeneralizedBlackScholesProcess;
import jquant.termstructures.YieldTermStructure;
import jquant.termstructures.volatility.equityfx.BlackVolTermStructure;
import jquant.termstructures.volatility.equityfx.LocalVolTermStructure;

import static jquant.Compounding.Continuous;
import static jquant.time.Frequency.ANNUAL;

/**
 * Forward Fokker-Planck operator for the Black-Scholes process.
 *
 * <p>Port of
 * {@code ql/methods/finitedifferences/operators/fdmblackscholesfwdop.{hpp,cpp}}.
 */
public class FdmBlackScholesFwdOp implements FdmLinearOpComposite {

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

    public FdmBlackScholesFwdOp(final FdmMesher mesher,
                                final GeneralizedBlackScholesProcess bsProcess,
                                double strike) {
        this(mesher, bsProcess, strike, false, Double.NaN, 0);
    }

    public FdmBlackScholesFwdOp(final FdmMesher mesher,
                                final GeneralizedBlackScholesProcess bsProcess,
                                double strike,
                                boolean localVol) {
        this(mesher, bsProcess, strike, localVol, Double.NaN, 0);
    }

    public FdmBlackScholesFwdOp(final FdmMesher mesher,
                                final GeneralizedBlackScholesProcess bsProcess,
                                double strike,
                                boolean localVol,
                                double illegalLocalVolOverwrite,
                                int direction) {
        mesher_ = mesher;
        final Handle<YieldTermStructure> rTS = bsProcess.riskFreeRate();
        final Handle<YieldTermStructure> qTS = bsProcess.dividendYield();
        rTS_ = rTS.currentLink();
        qTS_ = qTS.currentLink();
        volTS_ = bsProcess.blackVolatility().currentLink();
        localVol_ = localVol ? bsProcess.localVolatility().currentLink() : null;
        x_ = (localVol) ? exp(mesher.locations(direction)) : new Array(0);
        dxMap_ = new FirstDerivativeOp(direction, mesher);
        dxxMap_ = new SecondDerivativeOp(direction, mesher);
        mapT_ = new TripleBandLinearOp(direction, mesher);
        strike_ = strike;
        illegalLocalVolOverwrite_ = illegalLocalVolOverwrite;
        direction_ = direction;
    }

    private static Array exp(Array x) {
        final Array retVal = new Array(x.size());
        for (int i = 0; i < x.size(); ++i)
            retVal.set(i, Math.exp(x.get(i)));
        return retVal;
    }

    @Override
    public void setTime(double t1, double t2) {
        final double r = rTS_.forwardRate(t1, t2, Continuous, ANNUAL, false).rate();
        final double q = qTS_.forwardRate(t1, t2, Continuous, ANNUAL, false).rate();

        if (localVol_ != null) {
            final Array v = new Array(mesher_.layout().size());
            for (FdmLinearOpIterator iter : mesher_.layout()) {
                final int i = iter.index();

                if (illegalLocalVolOverwrite_ < 0.0) {
                    v.set(i, squared(localVol_.localVol(0.5 * (t1 + t2), x_.get(i), true)));
                } else {
                    try {
                        v.set(i, squared(localVol_.localVol(0.5 * (t1 + t2), x_.get(i), true)));
                    } catch (RuntimeException ex) {
                        v.set(i, squared(illegalLocalVolOverwrite_));
                    }
                }
            }
            final Array drift = new Array(v.size());
            for (int i = 0; i < v.size(); ++i)
                drift.set(i, -r + q + 0.5 * v.get(i));

            mapT_.axpyb(new Array(1, 1.0), dxMap_.multR(drift),
                    dxxMap_.multR(v.multiply(new Array(v.size(), 0.5))),
                    new Array(1, 0.0));
        } else {
            final double v = volTS_.blackForwardVariance(t1, t2, strike_, false)
                    / (t2 - t1);
            mapT_.axpyb(new Array(1, -r + q + 0.5 * v), dxMap_,
                    dxxMap_.mult(new Array(mesher_.layout().size(), 0.5 * v)),
                    new Array(1, 0.0));
        }
    }

    private static double squared(double x) {
        return x * x;
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
        if (direction == direction_)
            return mapT_.apply(r);
        else
            return new Array(r.size(), 0.0);
    }

    @Override
    public Array apply_mixed(Array r) {
        return new Array(r.size(), 0.0);
    }

    @Override
    public Array solve_splitting(int direction, Array r, double dt) {
        if (direction == direction_)
            return mapT_.solve_splitting(r, dt, 1.0);
        else
            return r;
    }

    @Override
    public Array preconditioner(Array r, double dt) {
        return solve_splitting(direction_, r, dt);
    }
}
