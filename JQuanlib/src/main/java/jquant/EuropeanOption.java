package jquant;

import jquant.time.Date;
import jquant.time.TimeUtils;

import static jquant.math.CommonUtil.QL_REQUIRE;

/**
 * European option priced by a {@link PricingEngine}. Greeks are read from
 * {@link results} when the engine provides them.
 */
public class EuropeanOption extends Option {
    private double delta_ = Double.NaN;
    private double gamma_ = Double.NaN;
    private double theta_ = Double.NaN;
    private double thetaPerDay_ = Double.NaN;
    private double vega_ = Double.NaN;
    private double rho_ = Double.NaN;
    private double dividendRho_ = Double.NaN;
    private double elasticity_ = Double.NaN;

    public EuropeanOption(Payoff payoff, Exercise exercise) {
        super(payoff, exercise);
    }

    @Override
    public boolean isExpired() {
        Date today = Settings.instance.evaluationDate().Date();
        return TimeUtils.less(exercise_.lastDate(), today);
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

    public double thetaPerDay() {
        calculate();
        QL_REQUIRE(!Double.isNaN(thetaPerDay_), "thetaPerDay not provided");
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
        QL_REQUIRE(!Double.isNaN(dividendRho_), "dividendRho not provided");
        return dividendRho_;
    }

    public double elasticity() {
        calculate();
        QL_REQUIRE(!Double.isNaN(elasticity_), "elasticity not provided");
        return elasticity_;
    }

    @Override
    public void fetchResults(PricingEngine.results r) {
        super.fetchResults(r);
        if (r instanceof results rr) {
            delta_ = rr.delta;
            gamma_ = rr.gamma;
            theta_ = rr.theta;
            thetaPerDay_ = rr.thetaPerDay;
            vega_ = rr.vega;
            rho_ = rr.rho;
            dividendRho_ = rr.dividendRho;
            elasticity_ = rr.elasticity;
        }
    }

    public static class results extends Instrument.results {
        public double delta = Double.NaN;
        public double gamma = Double.NaN;
        public double theta = Double.NaN;
        public double thetaPerDay = Double.NaN;
        public double vega = Double.NaN;
        public double rho = Double.NaN;
        public double dividendRho = Double.NaN;
        public double elasticity = Double.NaN;
    }
}
