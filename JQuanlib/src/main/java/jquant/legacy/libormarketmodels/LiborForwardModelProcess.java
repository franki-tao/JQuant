package jquant.legacy.libormarketmodels;

import jquant.CashFlow;
import jquant.StochasticProcess;
import jquant.cashflows.IborCoupon;
import jquant.cashflows.IborLeg;
import jquant.indexes.IborIndex;
import jquant.math.Array;
import jquant.math.CommonUtil;
import jquant.math.Matrix;
import jquant.processes.EulerDiscretization;
import jquant.time.*;
import org.apache.commons.math3.util.FastMath;

import java.util.List;

import static jquant.math.CommonUtil.QL_REQUIRE;
import static jquant.math.CommonUtil.upper_bound;

//! libor-forward-model process
/*! stochastic process of a libor forward model using the
    rolling forward measure incl. predictor-corrector step

    References:

    Glasserman, Paul, 2004, Monte Carlo Methods in Financial Engineering,
    Springer, Section 3.7

    Antoon Pelsser, 2000, Efficient Methods for Valuing Interest Rate
    Derivatives, Springer, 8

    Hull, John, White, Alan, 1999, Forward Rate Volatilities, Swap Rate
    Volatilities and the Implementation of the Libor Market Model
    (<http://www.rotman.utoronto.ca/~amackay/fin/libormktmodel2.pdf>)

    \test the correctness is tested by Monte-Carlo reproduction of
          caplet & ratchet NPVs and comparison with Black pricing.

    \warning this class does not work correctly with Visual C++ 6.

    \ingroup processes
*/
public class LiborForwardModelProcess extends StochasticProcess {
    private int size_;
    private IborIndex index_;
    private LfmCovarianceParameterization lfmParam_;
    private Array initialValues_;
    private List<Double> fixingTimes_;
    private List<Date> fixingDates_;
    private List<Double> accrualStartTimes_;
    private List<Double> accrualEndTimes_;
    private List<Double> accrualPeriod_;
    private Array m1;
    private Array m2;

    public LiborForwardModelProcess(int size, IborIndex index) {
        super(new EulerDiscretization());
        size_ = size;
        index_ = index;
        initialValues_ = new Array(size_);
        fixingTimes_ = CommonUtil.ArrayInit(size_);
        fixingDates_ = CommonUtil.ArrayInit(size_);
        accrualStartTimes_ = CommonUtil.ArrayInit(size);
        accrualEndTimes_ = CommonUtil.ArrayInit(size);
        accrualPeriod_ = CommonUtil.ArrayInit(size_);
        m1 = new Array(size_);
        m2 = new Array(size_);
        final DayCounter dayCounter = index_.dayCounter();
        final List<CashFlow> flows = cashFlows(1.0);

        QL_REQUIRE(size_ == flows.size(), "wrong number of cashflows");

        Date settlement = index_.forwardingTermStructure().currentLink().referenceDate();
        IborCoupon d1 = (IborCoupon) (flows.get(0));
        final Date startDate = d1.fixingDate();

        for (int i = 0; i < size_; ++i) {
            final IborCoupon coupon = (IborCoupon) flows.get(i);

            QL_REQUIRE(TimeUtils.equals(coupon.date(), coupon.accrualEndDate()),
                    "irregular coupon types are not suppported");

            initialValues_.set(i, coupon.rate());
            accrualPeriod_.set(i, coupon.accrualPeriod());

            fixingDates_.set(i, coupon.fixingDate());
            fixingTimes_.set(i,
                    dayCounter.yearFraction(startDate, coupon.fixingDate(), new Date(), new Date()));
            accrualStartTimes_.set(i,
                    dayCounter.yearFraction(settlement, coupon.accrualStartDate(), new Date(), new Date()));
            accrualEndTimes_.set(i,
                    dayCounter.yearFraction(settlement, coupon.accrualEndDate(), new Date(), new Date()));
        }
    }

    @Override
    public Array initialValues() {
        return initialValues_;
    }

    @Override
    public Array drift(double t, final Array x) {
        Array f = new Array(size_, 0.0);
        Matrix covariance = new Matrix(lfmParam_.covariance(t, x).matrix.copy());

        final int m = nextIndexReset(t);

        for (int k = m; k < size_; ++k) {
            m1.set(k, accrualPeriod_.get(k) * x.get(k) / (1 + accrualPeriod_.get(k) * x.get(k)));
            double temp = 0.0;
            for (int i = m; i < k + 1; i++) {
                temp += m1.get(i) * covariance.get(i, k);
            }
            f.set(k, temp - 0.5 * covariance.get(k, k));
        }

        return f;
    }

    @Override
    public Matrix diffusion(double t, final Array x) {
        return lfmParam_.diffusion(t, x);
    }

    @Override
    public Matrix covariance(double t0, final Array x0, double dt) {
        return lfmParam_.covariance(t0, x0).multiply(dt);
    }

    @Override
    public Array apply(final Array x0, final Array dx) {
        Array tmp = new Array(size_);

        for (int k = 0; k < size_; ++k) {
            tmp.set(k, x0.get(k) * FastMath.exp(dx.get(k)));
        }

        return tmp;
    }

    @Override
    // implements the predictor-corrector schema
    public Array evolve(double t0, final Array x0, double dt, final Array dw) {
        /* predictor-corrector step to reduce discretization errors.

           Short - but slow - solution would be

           Array rnd_0     = stdDeviation(t0, x0, dt)*dw;
           Array drift_0   = discretization_->drift(*this, t0, x0, dt);

           return apply(x0, ( drift_0 + discretization_
                ->drift(*this,t0,apply(x0, drift_0 + rnd_0),dt) )*0.5 + rnd_0);

           The following implementation does the same but is faster.
        */

        final int m = nextIndexReset(t0);
        final double sdt = Math.sqrt(dt);

        Array f = new Array(x0);
        Matrix diff = lfmParam_.diffusion(t0, x0);
        Matrix covariance = lfmParam_.covariance(t0, x0);

        for (int k = m; k < size_; ++k) {
            final double y = accrualPeriod_.get(k) * x0.get(k);
            m1.set(k, y / (1 + y));
            double temp = 0d;
            for (int i = m; i < k + 1; i++) {
                temp += m1.get(i) * covariance.get(i, k);
            }
            final double d = (
                    temp - 0.5 * covariance.get(k, k)) * dt;
            temp = 0.0;
            for (int i = 0; i < diff.cols(); i++) {
                temp += diff.get(k, i) * dw.get(i);
            }
            final double r = temp * sdt;
            final double x = y * Math.exp(d + r);
            m2.set(k, x / (1 + x));
            temp = 0.0;
            for (int i = m; i < k + 1; i++) {
                temp += m2.get(i) * covariance.get(i, k);
            }
            f.set(k, x0.get(k) * FastMath.exp(0.5 * (d +
                    (temp - 0.5 * covariance.get(k, k)) * dt) + r));
        }

        return f;
    }

    @Override
    public int size() {
        return size_;
    }

    @Override
    public int factors() {
        return lfmParam_.factors();
    }

    public IborIndex index() {
        return index_;
    }

    // amount = 1.0
    public List<CashFlow> cashFlows(double amount) {
        Date refDate = index_.forwardingTermStructure().currentLink().referenceDate();
        Schedule schedule = new Schedule(refDate,
                refDate.add(new Period(index_.tenor().length() * size_,
                        index_.tenor().units())),
                index_.tenor(),
                index_.fixingCalendar(),
                index_.businessDayConvention(),
                index_.businessDayConvention(),
                DateGenerationRule.FORWARD,
                false,
                new Date(),
                new Date());
        return new IborLeg(schedule, index_)
                .withNotionals(amount)
                .withPaymentDayCounter(index_.dayCounter())
                .withPaymentAdjustment(index_.businessDayConvention())
                .withFixingDays(index_.fixingDays())
                .leg();
    }

    public void setCovarParam(final LfmCovarianceParameterization param) {
        lfmParam_ = param;
    }

    public LfmCovarianceParameterization covarParam() {
        return lfmParam_;
    }

    public int nextIndexReset(double t) {
        return upper_bound(fixingTimes_, t);
    }

    public final List<Double> fixingTimes() {
        return fixingTimes_;
    }

    public final List<Date> fixingDates() {
        return fixingDates_;
    }

    public final List<Double> accrualStartTimes() {
        return accrualStartTimes_;
    }

    public final List<Double> accrualEndTimes() {
        return accrualEndTimes_;
    }
}
