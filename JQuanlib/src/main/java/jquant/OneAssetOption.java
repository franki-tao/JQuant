package jquant;

import jquant.pricingengines.GenericEngine;
import jquant.time.Date;
import jquant.time.TimeUtils;

import java.util.HashMap;

import static jquant.math.CommonUtil.QL_FAIL;
import static jquant.math.CommonUtil.QL_REQUIRE;

/**
 * Base class for options on a single asset.
 *
 * <p>Ported from QuantLib {@code ql/instruments/oneassetoption.{hpp,cpp}}.
 * Results are read from {@link results}, which carries the values of the
 * official {@code Greeks} and {@code MoreGreeks} structures.
 */
public abstract class OneAssetOption extends Option {
    protected double delta_ = Double.NaN;
    protected double deltaForward_ = Double.NaN;
    protected double elasticity_ = Double.NaN;
    protected double gamma_ = Double.NaN;
    protected double theta_ = Double.NaN;
    protected double thetaPerDay_ = Double.NaN;
    protected double vega_ = Double.NaN;
    protected double rho_ = Double.NaN;
    protected double dividendRho_ = Double.NaN;
    protected double strikeSensitivity_ = Double.NaN;
    protected double itmCashProbability_ = Double.NaN;

    public OneAssetOption(Payoff payoff, Exercise exercise) {
        super(payoff, exercise);
    }

    @Override
    public boolean isExpired() {
        // QuantLib Event::hasOccurred with the default reference date.
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

    public double deltaForward() {
        calculate();
        QL_REQUIRE(!Double.isNaN(deltaForward_), "forward delta not provided");
        return deltaForward_;
    }

    public double elasticity() {
        calculate();
        QL_REQUIRE(!Double.isNaN(elasticity_), "elasticity not provided");
        return elasticity_;
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

    public double thetaPerDay() {
        calculate();
        QL_REQUIRE(!Double.isNaN(thetaPerDay_), "theta per-day not provided");
        return thetaPerDay_;
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

    public double strikeSensitivity() {
        calculate();
        QL_REQUIRE(!Double.isNaN(strikeSensitivity_),
                "strike sensitivity not provided");
        return strikeSensitivity_;
    }

    public double itmCashProbability() {
        calculate();
        QL_REQUIRE(!Double.isNaN(itmCashProbability_),
                "in-the-money cash probability not provided");
        return itmCashProbability_;
    }

    @Override
    protected void setupExpired() {
        super.setupExpired();
        delta_ = deltaForward_ = elasticity_ = gamma_ = theta_ =
                thetaPerDay_ = vega_ = rho_ = dividendRho_ =
                        strikeSensitivity_ = itmCashProbability_ = 0.0;
    }

    @Override
    public void fetchResults(PricingEngine.results r) {
        super.fetchResults(r);
        if (!(r instanceof results)) {
            QL_FAIL("no greeks returned from pricing engine");
        }
        results rr = (results) r;
        // No check on null values - just copy, as in QuantLib: derived
        // options decide what to do with missing results.
        delta_ = rr.delta;
        gamma_ = rr.gamma;
        theta_ = rr.theta;
        vega_ = rr.vega;
        rho_ = rr.rho;
        dividendRho_ = rr.dividendRho;
        deltaForward_ = rr.deltaForward;
        elasticity_ = rr.elasticity;
        thetaPerDay_ = rr.thetaPerDay;
        strikeSensitivity_ = rr.strikeSensitivity;
        itmCashProbability_ = rr.itmCashProbability;
    }

    /** Results from a single-asset option calculation. */
    public static class results extends Instrument.results {
        public double delta;
        public double deltaForward;
        public double elasticity;
        public double gamma;
        public double theta;
        public double thetaPerDay;
        public double vega;
        public double rho;
        public double dividendRho;
        public double strikeSensitivity;
        public double itmCashProbability;

        public results() {
            // Instrument.results::reset() clears this map, so it must exist
            // before the first reset.
            additionalResults = new HashMap<>();
            reset();
        }

        @Override
        public void reset() {
            super.reset();
            delta = deltaForward = elasticity = gamma = theta = thetaPerDay =
                    vega = rho = dividendRho = strikeSensitivity =
                            itmCashProbability = Double.NaN;
        }
    }

    /** Base class for single-asset option engines. */
    public abstract static class engine
            extends GenericEngine<OneAssetOption.arguments, OneAssetOption.results> {
        protected engine() {
            super(new OneAssetOption.arguments(), new OneAssetOption.results());
        }
    }
}
