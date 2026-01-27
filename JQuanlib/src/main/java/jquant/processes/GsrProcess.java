package jquant.processes;

import jquant.math.Array;
import jquant.time.Date;
import jquant.time.DayCounter;
import jquant.time.TimeUtils;

import static jquant.math.CommonUtil.QL_REQUIRE;

//! GSR stochastic process
/*! \ingroup processes */
public class GsrProcess extends ForwardMeasureProcess1D {
    private GsrProcessCore core_;
    private Date referenceDate_;
    private DayCounter dc_;

    public GsrProcess(final Array times,
                      final Array vols,
                      final Array reversions,
                      final double T,
                      final Date referenceDate,
                      DayCounter dc) {
        super(T);
        core_ = new GsrProcessCore(times, vols, reversions, T);
        referenceDate_ = referenceDate;
        dc_ = dc;
        flushCache();
    }

    @Override
    public double x0() {
        return 0.0;
    }

    @Override
    public double drift(double t, double x) {
        return core_.y(t) -
                core_.G(t, getForwardMeasureTime()) * sigma(t) * sigma(t) -
                reversion(t) * x;
    }

    @Override
    public double diffusion(double t, double x) {
        checkT(t);
        return sigma(t);
    }

    @Override
    public double expectation(double w, double xw, double dt) {
        checkT(w + dt);
        return core_.expectation_x0dep_part(w, xw, dt) +
                core_.expectation_rn_part(w, dt) +
                core_.expectation_tf_part(w, dt);
    }

    @Override
    public double stdDeviation(double t0, double x0, double dt) {
        return Math.sqrt(variance(t0, x0, dt));
    }

    @Override
    public double variance(double w, double x, double dt) {
        checkT(w + dt);
        return core_.variance(w, dt);
    }

    @Override
    public double time(final Date d) {
        QL_REQUIRE(
                TimeUtils.neq(referenceDate_, new Date()) && dc_ != null,
                "time can not be computed without reference date and day counter");
        return dc_.yearFraction(referenceDate_, d, new Date(), new Date());
    }

    @Override
    public void setForwardMeasureTime(double t) {
        flushCache();
        super.setForwardMeasureTime(t);
    }

    public double sigma(double t) {
        return core_.sigma(t);
    }

    public double reversion(double t) {
        return core_.reversion(t);
    }

    public double y(double t) {
        checkT(t);
        return core_.y(t);
    }

    public double G(double t, double w, double x) {
        QL_REQUIRE(w >= t, "G(t,w) should be called with w ("
                + w + ") not lesser than t (" + t + ")");
        QL_REQUIRE(t >= 0.0 && w <= getForwardMeasureTime(),
                "G(t,w) should be called with (t,w)=("
                        + t + "," + w + ") in Range [0,"
                        + getForwardMeasureTime() + "].");

        return core_.G(t, w);
    }

    //! reset cache
    public void flushCache() {
        core_.flushCache();
    }

    private void checkT(final double t) {
        QL_REQUIRE(t <= getForwardMeasureTime() && t >= 0.0,
                "t (" + t
                        + ") must not be greater than forward measure time ("
                        + getForwardMeasureTime() + ") and non-negative");
    }
}
