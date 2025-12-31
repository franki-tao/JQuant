package jquant.termstructures.volatility.equityfx;

import jquant.Handle;
import jquant.Quote;
import jquant.patterns.AcyclicVisitor;
import jquant.patterns.Visitor;
import jquant.quotes.SimpleQuote;
import jquant.termstructures.YieldTermStructure;
import jquant.time.Date;
import jquant.time.DayCounter;
import org.apache.commons.math3.util.FastMath;

import static jquant.math.CommonUtil.QL_FAIL;
import static jquant.math.CommonUtil.QL_REQUIRE;

//! Local volatility surface derived from a Black vol surface
/*! For details about this implementation refer to
    "Stochastic Volatility and Local Volatility," in
    "Case Studies and Financial Modelling Course Notes," by
    Jim Gatheral, Fall Term, 2003

    see www.math.nyu.edu/fellows_fin_math/gatheral/Lecture1_Fall02.pdf

    \bug this class is untested, probably unreliable.
*/
public class LocalVolSurface extends LocalVolTermStructure {
    private Handle<BlackVolTermStructure> blackTS_;
    private Handle<YieldTermStructure> riskFreeTS_, dividendTS_;
    private Handle<Quote> underlying_;

    public LocalVolSurface(final Handle<BlackVolTermStructure> blackTS,
                           Handle<YieldTermStructure> riskFreeTS,
                           Handle<YieldTermStructure> dividendTS,
                           Handle<Quote> underlying) {
        super(blackTS.getValue().businessDayConvention(), blackTS.getValue().dayCounter());
        blackTS_ = blackTS;
        riskFreeTS_ = riskFreeTS;
        dividendTS_ = dividendTS;
        underlying_ = underlying;
        registerWith(blackTS_.getValue());
        registerWith(riskFreeTS_.getValue());
        registerWith(dividendTS_.getValue());
        registerWith(underlying_.getValue());
    }

    public LocalVolSurface(final Handle<BlackVolTermStructure> blackTS,
                           Handle<YieldTermStructure> riskFreeTS,
                           Handle<YieldTermStructure> dividendTS,
                           double underlying) {
        super(blackTS.getValue().businessDayConvention(), blackTS.getValue().dayCounter());
        blackTS_ = blackTS;
        riskFreeTS_ = riskFreeTS;
        dividendTS_ = dividendTS;
        underlying_ = new Handle<>(new SimpleQuote(underlying), false);
        registerWith(blackTS_.getValue());
        registerWith(riskFreeTS_.getValue());
        registerWith(dividendTS_.getValue());
    }

    //! \name TermStructure interface
    //@{
    public final Date referenceDate() {
        return blackTS_.getValue().referenceDate();
    }

    public DayCounter dayCounter() {
        return blackTS_.getValue().dayCounter();
    }

    public Date maxDate() {
        return blackTS_.getValue().maxDate();
    }

    //@}
    //! \name VolatilityTermStructure interface
    //@{
    public double minStrike() {
        return blackTS_.getValue().minStrike();
    }

    public double maxStrike() {
        return blackTS_.getValue().maxStrike();
    }

    //@}
    //! \name Visitability
    //@{
    public void accept(AcyclicVisitor v) {
        if (v instanceof Visitor) {
            try {
                // 尝试强制转型
                @SuppressWarnings("unchecked")
                Visitor<LocalVolSurface> vv = (Visitor<LocalVolSurface>) v;
                vv.visit(this);
            } catch (ClassCastException e) {
                // 如果类型不匹配，说明该访问者不关心这个特定的子类
                QL_FAIL("not a Black-volatility term structure visitor");
            }
        }
    }

    //@}
    protected double localVolImpl(double t, double underlyingLevel) {

        double dr = riskFreeTS_.getValue().discount(t, true);
        double dq = dividendTS_.currentLink().discount(t, true);
        double forwardValue = underlying_.getValue().value() * dq / dr;

        // strike derivatives
        double strike, y, dy, strikep, strikem;
        double w, wp, wm, dwdy, d2wdy2;
        strike = underlyingLevel;
        y = FastMath.log(strike / forwardValue);
        dy = ((Math.abs(y) > 0.001) ? (y * 0.0001) : 0.000001);
        strikep = strike * FastMath.exp(dy);
        strikem = strike / FastMath.exp(dy);
        w = blackTS_.getValue().blackVariance(t, strike, true);
        wp = blackTS_.getValue().blackVariance(t, strikep, true);
        wm = blackTS_.getValue().blackVariance(t, strikem, true);
        dwdy = (wp - wm) / (2.0 * dy);
        d2wdy2 = (wp - 2.0 * w + wm) / (dy * dy);

        // time derivative
        double dt, wpt, wmt, dwdt;
        if (t == 0.0) {
            dt = 0.0001;
            double drpt = riskFreeTS_.getValue().discount(t + dt, true);
            double dqpt = dividendTS_.getValue().discount(t + dt, true);
            double strikept = strike * dr * dqpt / (drpt * dq);

            wpt = blackTS_.getValue().blackVariance(t + dt, strikept, true);
            QL_REQUIRE(wpt >= w,
                    "decreasing variance at strike " + strike
                            + " between time " + t + " and time " + (t + dt));
            dwdt = (wpt - w) / dt;
        } else {
            dt = Math.min(0.0001, t / 2.0);
            double drpt = riskFreeTS_.getValue().discount(t + dt, true);
            double drmt = riskFreeTS_.getValue().discount(t - dt, true);
            double dqpt = dividendTS_.getValue().discount(t + dt, true);
            double dqmt = dividendTS_.getValue().discount(t - dt, true);

            double strikept = strike * dr * dqpt / (drpt * dq);
            double strikemt = strike * dr * dqmt / (drmt * dq);

            wpt = blackTS_.getValue().blackVariance(t + dt, strikept, true);
            wmt = blackTS_.getValue().blackVariance(t - dt, strikemt, true);

            QL_REQUIRE(wpt >= w,
                    "decreasing variance at strike " + strike
                            + " between time " + t + " and time " + (t + dt));
            QL_REQUIRE(w >= wmt,
                    "decreasing variance at strike " + strike
                            + " between time " + (t - dt) + " and time " + t);

            dwdt = (wpt - wmt) / (2.0 * dt);
        }

        if (dwdy == 0.0 && d2wdy2 == 0.0) { // avoid /w where w might be 0.0
            return FastMath.sqrt(dwdt);
        } else {
            double den1 = 1.0 - y / w * dwdy;
            double den2 = 0.25 * (-0.25 - 1.0 / w + y * y / w / w) * dwdy * dwdy;
            double den3 = 0.5 * d2wdy2;
            double den = den1 + den2 + den3;
            double result = dwdt / den;

            QL_REQUIRE(result >= 0.0,
                    "negative local vol^2 at strike " + strike
                            + " and time " + t
                            + "; the black vol surface is not smooth enough");

            return Math.sqrt(result);
        }
    }
}
