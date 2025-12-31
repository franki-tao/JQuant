package jquant.termstructures.volatility.equityfx;

import jquant.Handle;
import jquant.patterns.AcyclicVisitor;
import jquant.patterns.Visitor;
import jquant.time.Calendar;
import jquant.time.Date;
import jquant.time.DayCounter;

import static jquant.math.CommonUtil.QL_FAIL;

//! Local volatility curve derived from a Black curve
public class LocalVolCurve extends LocalVolTermStructure {
    private Handle<BlackVarianceCurve> blackVarianceCurve_;
    public LocalVolCurve(final Handle<BlackVarianceCurve> curve) {
        super(curve.getValue().businessDayConvention(), curve.getValue().dayCounter());
        blackVarianceCurve_ = curve;
        registerWith(blackVarianceCurve_.getValue());
    }
    //! \name TermStructure interface
    //@{
    public final Date referenceDate() {
        return blackVarianceCurve_.getValue().referenceDate();
    }
    public Calendar calendar() {
        return blackVarianceCurve_.getValue().calendar();
    }
    public DayCounter dayCounter() {
        return blackVarianceCurve_.getValue().dayCounter();
    }
    public Date maxDate() {
        return blackVarianceCurve_.getValue().maxDate();
    }
    //@}
    //! \name VolatilityTermStructure interface
    //@{
    public double minStrike() {
        return Double.MIN_VALUE;
    }
    public double maxStrike() {
        return Double.MAX_VALUE;
    }
    //@}
    //! \name Visitability
    //@{
    public void accept(AcyclicVisitor v) {
        if (v instanceof Visitor) {
            try {
                // 尝试强制转型
                @SuppressWarnings("unchecked")
                Visitor<LocalVolCurve> vv = (Visitor<LocalVolCurve>) v;
                vv.visit(this);
            } catch (ClassCastException e) {
                // 如果类型不匹配，说明该访问者不关心这个特定的子类
                QL_FAIL("not a Black-volatility term structure visitor");
            }
        }
    }
    //@}
    /*! The relation
        \f[
            \int_0^T \sigma_L^2(t)dt = \sigma_B^2 T
        \f]
        holds, where \f$ \sigma_L(t) \f$ is the local volatility at
        time \f$ t \f$ and \f$ \sigma_B(T) \f$ is the Black
        volatility for maturity \f$ T \f$. From the above, the formula
        \f[
            \sigma_L(t) = \sqrt{\frac{\mathrm{d}}{\mathrm{d}t}\sigma_B^2(t)t}
        \f]
        can be deduced which is here implemented.
    */
    protected double localVolImpl(double t, double dummy) {

        double dt = (1.0/365.0);
        double var1 = blackVarianceCurve_.getValue().blackVariance(t, dummy, true);
        double var2 = blackVarianceCurve_.getValue().blackVariance(t+dt, dummy, true);
        double derivative = (var2-var1)/dt;
        return Math.sqrt(derivative);
    }
}
