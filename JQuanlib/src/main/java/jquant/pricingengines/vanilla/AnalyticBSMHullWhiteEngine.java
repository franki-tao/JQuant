package jquant.pricingengines.vanilla;

import jquant.EuropeanOption;
import jquant.Handle;
import jquant.Option;
import jquant.models.shortrate.onefactormodels.HullWhite;
import jquant.patterns.AcyclicVisitor;
import jquant.patterns.Visitor;
import jquant.pricingengines.GenericEngine;
import jquant.pricingengines.StrikedTypePayoff;
import jquant.processes.EulerDiscretization;
import jquant.processes.GeneralizedBlackScholesProcess;
import jquant.termstructures.volatility.equityfx.BlackVolTermStructure;
import jquant.time.BusinessDayConvention;
import jquant.time.Date;

import java.util.HashMap;

import static jquant.math.CommonUtil.QL_FAIL;
import static jquant.math.CommonUtil.QL_REQUIRE;
import static jquant.math.MathUtils.QL_EPSILON;

/**
 * Analytic European option pricer including stochastic interest rates.
 *
 * <p>Port of {@code ql/pricingengines/vanilla/analyticbsmhullwhiteengine.{hpp,cpp}}.
 */
public class AnalyticBSMHullWhiteEngine
        extends GenericEngine<Option.arguments, EuropeanOption.results> {

    private final double rho_;
    private final GeneralizedBlackScholesProcess process_;
    private final HullWhite model_;

    public AnalyticBSMHullWhiteEngine(double equityShortRateCorrelation,
                                      GeneralizedBlackScholesProcess process,
                                      final HullWhite model) {
        super(new Option.arguments(), new EuropeanOption.results());
        rho_ = equityShortRateCorrelation;
        process_ = process;
        model_ = model;
        QL_REQUIRE(process_ != null, "no Black-Scholes process specified");
        QL_REQUIRE(model_ != null, "no Hull-White model specified");
        results_.additionalResults = new HashMap<>();
        registerWith(process_);
        registerWith(model_);
    }

    @Override
    public void calculate() {
        QL_REQUIRE(process_.x0() > 0.0, "negative or null underlying given");

        final StrikedTypePayoff payoff;
        QL_REQUIRE(arguments_.payoff instanceof StrikedTypePayoff,
                "non-striked payoff given");
        payoff = (StrikedTypePayoff) arguments_.payoff;

        final jquant.Exercise exercise = arguments_.exercise;
        final Date exerciseDate = exercise.lastDate();

        final double t = process_.riskFreeRate().currentLink().dayCounter()
                .yearFraction(process_.riskFreeRate().currentLink().referenceDate(),
                        exerciseDate);

        // official: model_->params()[0] / model_->params()[1]
        final double a = model_.a();
        final double sigma = model_.sigma();
        final double eta = process_.blackVolatility().currentLink()
                .blackVol(exerciseDate, payoff.strike());

        final double varianceOffset;
        if (a * t > Math.pow(QL_EPSILON, 0.25)) {
            final double v = sigma * sigma / (a * a)
                    * (t + 2 / a * Math.exp(-a * t)
                    - 1 / (2 * a) * Math.exp(-2 * a * t) - 3 / (2 * a));
            final double mu = 2 * rho_ * sigma * eta / a
                    * (t - 1 / a * (1 - Math.exp(-a * t)));

            varianceOffset = v + mu;
        } else {
            // low-a algebraic limit
            final double v = sigma * sigma * t * t * t
                    * (1 / 3.0 - 0.25 * a * t + 7 / 60.0 * a * a * t * t);
            final double mu = rho_ * sigma * eta * t * t
                    * (1 - a * t / 3.0 + a * a * t * t / 12.0);

            varianceOffset = v + mu;
        }

        final Handle<BlackVolTermStructure> volTS =
                new Handle<>(new ShiftedBlackVolTermStructure(varianceOffset,
                        process_.blackVolatility()), true);

        final GeneralizedBlackScholesProcess adjProcess =
                new GeneralizedBlackScholesProcess(process_.stateVariable(),
                        process_.dividendYield(),
                        process_.riskFreeRate(),
                        volTS,
                        new EulerDiscretization(),
                        false);

        try {
            final AnalyticEuropeanEngine bsmEngine =
                    new AnalyticEuropeanEngine(adjProcess);

            final Option.arguments bsmArgs =
                    (Option.arguments) bsmEngine.getArguments();
            bsmArgs.payoff = payoff;
            bsmArgs.exercise = exercise;
            bsmEngine.calculate();

            copyResults((EuropeanOption.results) bsmEngine.getResults(), results_);
        } finally {
            // The official function-local process is destroyed at the end of
            // calculate() and its Observer registrations are released. Java has
            // no destructor, so detach it explicitly from the shared curves.
            adjProcess.unregisterWithAll();
        }
    }

    private static void copyResults(EuropeanOption.results src,
                                    EuropeanOption.results dst) {
        dst.value = src.value;
        dst.errorEstimate = src.errorEstimate;
        dst.valuationDate = src.valuationDate;
        dst.delta = src.delta;
        dst.gamma = src.gamma;
        dst.theta = src.theta;
        dst.thetaPerDay = src.thetaPerDay;
        dst.vega = src.vega;
        dst.rho = src.rho;
        dst.dividendRho = src.dividendRho;
        dst.elasticity = src.elasticity;
        dst.strikeSensitivity = src.strikeSensitivity;
        if (src.additionalResults != null) {
            dst.additionalResults = new HashMap<>(src.additionalResults);
        } else {
            dst.additionalResults = new HashMap<>();
        }
    }

    private static class ShiftedBlackVolTermStructure
            extends BlackVolTermStructure {
        private final double varianceOffset_;
        private final Handle<BlackVolTermStructure> volTS_;

        ShiftedBlackVolTermStructure(double varianceOffset,
                                     Handle<BlackVolTermStructure> volTS) {
            super(volTS.currentLink().referenceDate(),
                    volTS.currentLink().calendar(),
                    BusinessDayConvention.FOLLOWING,
                    volTS.currentLink().dayCounter());
            varianceOffset_ = varianceOffset;
            volTS_ = volTS;
        }

        @Override
        public double minStrike() {
            return volTS_.currentLink().minStrike();
        }

        @Override
        public double maxStrike() {
            return volTS_.currentLink().maxStrike();
        }

        @Override
        public Date maxDate() {
            return volTS_.currentLink().maxDate();
        }

        @Override
        protected double blackVarianceImpl(double t, double strike) {
            return volTS_.currentLink().blackVariance(t, strike, true)
                    + varianceOffset_;
        }

        @Override
        protected double blackVolImpl(double t, double strike) {
            final double nonZeroMaturity = (t == 0.0 ? 0.00001 : t);
            final double var = blackVarianceImpl(nonZeroMaturity, strike);
            return Math.sqrt(var / nonZeroMaturity);
        }

        @Override
        public void accept(AcyclicVisitor v) {
            if (v instanceof Visitor) {
                @SuppressWarnings("unchecked")
                final Visitor<ShiftedBlackVolTermStructure> vv =
                        (Visitor<ShiftedBlackVolTermStructure>) v;
                vv.visit(this);
            } else {
                QL_FAIL("not a Black-volatility term structure visitor");
            }
        }
    }
}
