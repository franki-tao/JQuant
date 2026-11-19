package jquant;

import jquant.pricingengines.GenericEngine;
import jquant.time.Date;
import jquant.time.TimeUtils;

import java.util.HashMap;

import static jquant.math.CommonUtil.QL_FAIL;
import static jquant.math.CommonUtil.QL_REQUIRE;

/**
 * Base class for options on multiple assets.
 *
 * <p>Ported from QuantLib {@code ql/instruments/multiassetoption.{hpp,cpp}}.
 */
public abstract class MultiAssetOption extends Option {
    protected double delta_ = Double.NaN;
    protected double gamma_ = Double.NaN;
    protected double theta_ = Double.NaN;
    protected double vega_ = Double.NaN;
    protected double rho_ = Double.NaN;
    protected double dividendRho_ = Double.NaN;

    public MultiAssetOption(Payoff payoff, Exercise exercise) {
        super(payoff, exercise);
    }

    @Override
    public boolean isExpired() {
        Date today = Settings.instance.evaluationDate().Date();
        return Settings.instance.includeReferenceDateEvents()
                ? TimeUtils.less(exercise_.lastDate(), today)
                : TimeUtils.leq(exercise_.lastDate(), today);
    }

    public double delta() {
        calculate();
        QL_REQUIRE(!Double.isNaN(delta_), "delta not provided");
        return delta_;
    }

    public double gamma() {
        calculate();
        QL_REQUIRE(!Double.isNaN(gamma_), "gamma not provided");
        return gamma_;
    }

    public double theta() {
        calculate();
        QL_REQUIRE(!Double.isNaN(theta_), "theta not provided");
        return theta_;
    }

    public double vega() {
        calculate();
        QL_REQUIRE(!Double.isNaN(vega_), "vega not provided");
        return vega_;
    }

    public double rho() {
        calculate();
        QL_REQUIRE(!Double.isNaN(rho_), "rho not provided");
        return rho_;
    }

    public double dividendRho() {
        calculate();
        QL_REQUIRE(!Double.isNaN(dividendRho_), "dividend rho not provided");
        return dividendRho_;
    }

    @Override
    protected void setupExpired() {
        super.setupExpired();
        NPV_ = delta_ = gamma_ = theta_ =
                vega_ = rho_ = dividendRho_ = 0.0;
    }

    @Override
    public void setupArguments(PricingEngine.arguments args) {
        if (!(args instanceof Option.arguments)) {
            QL_FAIL("wrong argument type");
        }
        Option.arguments optionArgs = (Option.arguments) args;
        optionArgs.payoff = payoff_;
        optionArgs.exercise = exercise_;
    }

    @Override
    public void fetchResults(PricingEngine.results r) {
        super.fetchResults(r);
        if (!(r instanceof MultiAssetOption.results)) {
            QL_FAIL("no greeks returned from pricing engine");
        }
        MultiAssetOption.results multiResults = (MultiAssetOption.results) r;
        delta_ = multiResults.delta;
        gamma_ = multiResults.gamma;
        theta_ = multiResults.theta;
        vega_ = multiResults.vega;
        rho_ = multiResults.rho;
        dividendRho_ = multiResults.dividendRho;
    }

    /** Results from a multi-asset option calculation. */
    public static class results extends Instrument.results {
        public double delta;
        public double gamma;
        public double theta;
        public double vega;
        public double rho;
        public double dividendRho;

        public results() {
            additionalResults = new HashMap<>();
            reset();
        }

        @Override
        public void reset() {
            super.reset();
            delta = gamma = theta = vega = rho = dividendRho = Double.NaN;
        }
    }

    /** Base class for multi-asset option engines. */
    public abstract static class engine
            extends GenericEngine<Option.arguments, MultiAssetOption.results> {
        protected engine() {
            super(new Option.arguments(), new MultiAssetOption.results());
        }
    }
}
