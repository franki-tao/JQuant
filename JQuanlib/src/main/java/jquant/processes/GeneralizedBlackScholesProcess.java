package jquant.processes;

import jquant.*;
import jquant.termstructures.YieldTermStructure;
import jquant.termstructures.volatility.equityfx.*;
import jquant.time.Date;
import jquant.time.Frequency;

import static jquant.math.CommonUtil.QL_FAIL;

//! Generalized Black-Scholes stochastic process
/*! This class describes the stochastic process \f$ S \f$ governed by
    \f[
        d\ln S(t) = (r(t) - q(t) - \frac{\sigma(t, S)^2}{2}) dt
                 + \sigma dW_t.
    \f]

    \warning while the interface is expressed in terms of \f$ S \f$,
             the internal calculations work on \f$ ln S \f$.

    \ingroup processes
*/
public class GeneralizedBlackScholesProcess extends StochasticProcess1D {
    private Handle<Quote> x0_;
    private Handle<YieldTermStructure> riskFreeRate_, dividendYield_;
    private Handle<BlackVolTermStructure> blackVolatility_;
    private Handle<LocalVolTermStructure> externalLocalVolTS_;
    private boolean forceDiscretization_;
    private boolean hasExternalLocalVol_;
    private RelinkableHandle<LocalVolTermStructure> localVolatility_;
    private boolean updated_, isStrikeIndependent_;

    public GeneralizedBlackScholesProcess(
            Handle<Quote> x0,
            Handle<YieldTermStructure> dividendTS,
            Handle<YieldTermStructure> riskFreeTS,
            Handle<BlackVolTermStructure> blackVolTS,
            final StochasticProcess1DImpl disc,
            boolean forceDiscretization) {
        super(disc);
        x0_ = x0;
        riskFreeRate_ = riskFreeTS;
        dividendYield_ = dividendTS;
        blackVolatility_ = blackVolTS;
        forceDiscretization_ = forceDiscretization;
        hasExternalLocalVol_ = false;
        updated_ = false;
        isStrikeIndependent_ = false;
        registerWith(x0_.getValue());
        registerWith(riskFreeRate_.getValue());
        registerWith(dividendYield_.getValue());
        registerWith(blackVolatility_.getValue());
    }

    public GeneralizedBlackScholesProcess(
            Handle<Quote> x0,
            Handle<YieldTermStructure> dividendTS,
            Handle<YieldTermStructure> riskFreeTS,
            Handle<BlackVolTermStructure> blackVolTS,
            Handle<LocalVolTermStructure> localVolTS) {
        super(new EulerDiscretization());
        x0_ = x0;
        riskFreeRate_ = riskFreeTS;
        dividendYield_ = dividendTS;
        blackVolatility_ = blackVolTS;
        externalLocalVolTS_ = localVolTS;
        forceDiscretization_ = false;
        hasExternalLocalVol_ = true;
        updated_ = false;
        isStrikeIndependent_ = false;
        registerWith(x0_.getValue());
        registerWith(riskFreeRate_.getValue());
        registerWith(dividendYield_.getValue());
        registerWith(blackVolatility_.getValue());
        registerWith(externalLocalVolTS_.getValue());
    }

    //! \name StochasticProcess1D interface
    //@{
    public double x0() {
        return x0_.getValue().value();
    }

    /*! \todo revise extrapolation */
    public double drift(double t, double x) {
        double sigma = diffusion(t, x);
        // we could be more anticipatory if we know the right dt
        // for which the drift will be used
        double t1 = t + 0.0001;
        return riskFreeRate_.getValue().forwardRate(t, t1, Compounding.Continuous, Frequency.NO_FREQUENCY, true).rate()
                - dividendYield_.getValue().forwardRate(t, t1, Compounding.Continuous, Frequency.NO_FREQUENCY, true).rate()
                - 0.5 * sigma * sigma;
    }

    /*! \todo revise extrapolation */
    public double diffusion(double t, double x) {
        return localVolatility().getValue().localVol(t, x, true);
    }

    public double apply(double x0, double dx) {
        return x0 * Math.exp(dx);
    }

    /*! \warning in general raises a "not implemented" exception.
                 It should be rewritten to return the expectation E(S)
                 of the process, not exp(E(log S)).
    */
    public double expectation(double t0,
                              double x0,
                              double dt) {
        localVolatility(); // trigger update
        if (isStrikeIndependent_ && !forceDiscretization_) {
            // exact value for curves
            return x0 *
                    Math.exp(dt * (riskFreeRate_.getValue().forwardRate(t0, t0 + dt, Compounding.Continuous,
                            Frequency.NO_FREQUENCY, true).rate() -
                            dividendYield_.getValue().forwardRate(
                                    t0, t0 + dt, Compounding.Continuous, Frequency.NO_FREQUENCY, true).rate()));
        } else {
            QL_FAIL("not implemented");
        }
        return 0;
    }

    public double stdDeviation(double t0, double x0, double dt) {
        localVolatility(); // trigger update
        if (isStrikeIndependent_ && !forceDiscretization_) {
            // exact value for curves
            return Math.sqrt(variance(t0, x0, dt));
        } else {
            return discretization_.diffusion(this, t0, x0, dt);
        }
    }

    public double variance(double t0, double x0, double dt) {
        localVolatility(); // trigger update
        if (isStrikeIndependent_ && !forceDiscretization_) {
            // exact value for curves
            return blackVolatility_.getValue().blackVariance(t0 + dt, 0.01, false) -
                    blackVolatility_.getValue().blackVariance(t0, 0.01, false);
        } else {
            return discretization_.variance(this, t0, x0, dt);
        }
    }

    public double evolve(double t0, double x0,
                         double dt, double dw) {
        localVolatility(); // trigger update
        if (isStrikeIndependent_ && !forceDiscretization_) {
            // exact value for curves
            double var = variance(t0, x0, dt);
            double drift = (riskFreeRate_.getValue().forwardRate(t0, t0 + dt, Compounding.Continuous,
                    Frequency.NO_FREQUENCY, true).rate() -
                    dividendYield_.getValue().forwardRate(t0, t0 + dt, Compounding.Continuous,
                            Frequency.NO_FREQUENCY, true).rate()) * dt - 0.5 * var;
            return apply(x0, Math.sqrt(var) * dw + drift);
        } else
            return apply(x0, discretization_.drift(this, t0, x0, dt) +
                    stdDeviation(t0, x0, dt) * dw);
    }

    //@}
    public double time(final Date d) {
        return riskFreeRate_.getValue().dayCounter().yearFraction(
                riskFreeRate_.getValue().referenceDate(), d, new Date(), new Date());
    }
    //! \name Observer interface
    //@{
    public void update() {
        updated_ = false;
        super.update();
    }
    //@}
    //! \name Inspectors
    //@{
    public final Handle<Quote> stateVariable() {
        return x0_;
    }
    public final Handle<YieldTermStructure> dividendYield() {
        return dividendYield_;
    }
    public final Handle<YieldTermStructure> riskFreeRate() {
        return riskFreeRate_;
    }
    public final Handle<BlackVolTermStructure> blackVolatility() {
        return blackVolatility_;
    }

    public final Handle<LocalVolTermStructure> localVolatility() {
        if (hasExternalLocalVol_)
            return externalLocalVolTS_;

        if (!updated_) {
            isStrikeIndependent_ = true;

            // constant Black vol?
            if (blackVolatility().getValue() instanceof BlackConstantVol constVol) {
                try {
                    // ok, the local vol is constant too.
                    localVolatility_.linkTo(new LocalConstantVol(constVol.referenceDate(),
                            constVol.blackVol(0, x0_.currentLink().value(), false),
                            constVol.dayCounter()), true);
                    updated_ = true;
                    return localVolatility_;
                } catch (ClassCastException ignored) {
                }
            }
            // ok, so it's not constant. Maybe it's strike-independent?
            if (blackVolatility().getValue() instanceof BlackVarianceCurve volCurve) {
                try {
                    // ok, we can use the optimized algorithm
                    localVolatility_.linkTo(new LocalVolCurve(new Handle<>(volCurve, false)), true);
                    updated_ = true;
                    return localVolatility_;
                } catch (ClassCastException ignored) {
                }
            }
            // ok, so it's strike-dependent. Never mind.
            localVolatility_.linkTo(new LocalVolSurface(blackVolatility_, riskFreeRate_,
                    dividendYield_, x0_.currentLink().value()), true);
            updated_ = true;
            isStrikeIndependent_ = false;
            return localVolatility_;

        } else {
            return localVolatility_;
        }
    }
}
