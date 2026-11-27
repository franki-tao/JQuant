package jquant.methods.finitedifferences.operators;

import jquant.Quote;
import jquant.math.Array;
import jquant.methods.finitedifferences.meshers.FdmMesher;
import jquant.termstructures.YieldTermStructure;
import jquant.termstructures.volatility.equityfx.LocalVolTermStructure;

import static jquant.Compounding.Continuous;
import static jquant.time.Frequency.ANNUAL;

/**
 * Forward Fokker-Planck operator for a local-volatility process.
 *
 * <p>Port of
 * {@code ql/methods/finitedifferences/operators/fdmlocalvolfwdop.{hpp,cpp}}.
 */
public class FdmLocalVolFwdOp implements FdmLinearOpComposite {

    private final FdmMesher mesher_;
    private final YieldTermStructure rTS_, qTS_;
    private final LocalVolTermStructure localVol_;
    private final Array x_;
    private final FirstDerivativeOp dxMap_;
    private final TripleBandLinearOp dxxMap_;
    private final TripleBandLinearOp mapT_;
    private final int direction_;

    public FdmLocalVolFwdOp(final FdmMesher mesher,
                            final Quote spot,
                            YieldTermStructure rTS,
                            YieldTermStructure qTS,
                            final LocalVolTermStructure localVol) {
        this(mesher, spot, rTS, qTS, localVol, 0);
    }

    public FdmLocalVolFwdOp(final FdmMesher mesher,
                            final Quote spot,
                            YieldTermStructure rTS,
                            YieldTermStructure qTS,
                            final LocalVolTermStructure localVol,
                            int direction) {
        mesher_ = mesher;
        rTS_ = rTS;
        qTS_ = qTS;
        localVol_ = localVol;
        x_ = (localVol != null) ? exp(mesher.locations(direction)) : new Array(0);
        dxMap_ = new FirstDerivativeOp(direction, mesher);
        dxxMap_ = new SecondDerivativeOp(direction, mesher);
        mapT_ = new TripleBandLinearOp(direction, mesher);
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

        final Array v = new Array(mesher_.layout().size());
        for (FdmLinearOpIterator iter : mesher_.layout()) {
            final int i = iter.index();
            v.set(i, squared(localVol_.localVol(0.5 * (t1 + t2), x_.get(i), true)));
        }

        final Array drift = new Array(v.size());
        for (int i = 0; i < v.size(); ++i)
            drift.set(i, -r + q + 0.5 * v.get(i));

        mapT_.axpyb(new Array(1, 1.0), dxMap_.multR(drift),
                dxxMap_.multR(v.multiply(new Array(v.size(), 0.5))),
                new Array(1, 0.0));
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
