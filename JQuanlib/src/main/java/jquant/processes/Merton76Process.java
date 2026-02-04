package jquant.processes;

import jquant.*;
import jquant.termstructures.YieldTermStructure;
import jquant.termstructures.volatility.equityfx.BlackVolTermStructure;
import jquant.time.Date;

import static jquant.math.CommonUtil.QL_FAIL;

//! Merton-76 jump-diffusion process
/*! \ingroup processes */
public class Merton76Process extends StochasticProcess1D {
    private GeneralizedBlackScholesProcess blackProcess_;
    private Handle<Quote> jumpIntensity_, logMeanJump_, logJumpVolatility_;

    public Merton76Process(final Handle<Quote> stateVariable,
                           final Handle<YieldTermStructure> dividendTS,
                           final Handle<YieldTermStructure> riskFreeTS,
                           final Handle<BlackVolTermStructure> blackVolTS,
                           Handle<Quote> jumpInt,
                           Handle<Quote> logJMean,
                           Handle<Quote> logJVol,
                           final StochasticProcess1DImpl disc) {
        super(disc);
        blackProcess_ = new BlackScholesMertonProcess(stateVariable, dividendTS, riskFreeTS, blackVolTS, disc, false);
        jumpIntensity_ = jumpInt;
        logMeanJump_ = logJMean;
        logJumpVolatility_ = logJVol;
        registerWith(blackProcess_);
        registerWith(jumpIntensity_.currentLink());
        registerWith(logMeanJump_.currentLink());
        registerWith(logJumpVolatility_.currentLink());
    }

    @Override
    public double x0() {
        return blackProcess_.x0();
    }

    @Override
    public double drift(double t, double x) {
        QL_FAIL("Merton76Process does not implement drift");
        return 0d;
    }

    @Override
    public double diffusion(double t, double x) {
        QL_FAIL("Merton76Process does not implement diffusion");
        return 0d;
    }

    @Override
    public double apply(double x, double y) {
        QL_FAIL("Merton76Process does not implement apply");
        return 0d;
    }

    @Override
    public double time(final Date date) {
        return blackProcess_.time(date);
    }

    public final Handle<Quote> stateVariable() {
        return blackProcess_.stateVariable();
    }

    public final Handle<YieldTermStructure> dividendYield() {
        return blackProcess_.dividendYield();
    }

    public final Handle<YieldTermStructure> riskFreeRate() {
        return blackProcess_.riskFreeRate();
    }

    public final Handle<BlackVolTermStructure> blackVolatility() {
        return blackProcess_.blackVolatility();
    }

    public final Handle<Quote> jumpIntensity() {
        return jumpIntensity_;
    }

    public final Handle<Quote> logMeanJump() {
        return logMeanJump_;
    }

    public final Handle<Quote> logJumpVolatility() {
        return logJumpVolatility_;
    }
}
