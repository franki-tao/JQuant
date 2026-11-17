package jquant.experimental.volatility;

import jquant.Handle;
import jquant.Quote;
import jquant.math.optimization.EndCriteria;
import jquant.math.optimization.OptimizationMethod;
import jquant.quotes.SimpleQuote;
import jquant.termstructures.SmileSection;
import jquant.termstructures.volatility.Sarb;
import jquant.time.Date;
import jquant.time.DayCounter;
import jquant.time.daycounters.Actual365Fixed;

import java.util.ArrayList;
import java.util.List;

/**
 * Port of QuantLib's {@code NoArbSabrInterpolatedSmileSection}
 * ({@code ql/experimental/volatility/noarbsabrinterpolatedsmilesection.{hpp,cpp}}).
 *
 * <p>In C++ this class inherits from both {@code SmileSection} and
 * {@code LazyObject}.  Java has single inheritance, so the small amount of
 * lazy-calculation behaviour that is actually used here (a recalculation
 * flag reset by {@link #update()}) is implemented directly.</p>
 */
public class NoArbSabrInterpolatedSmileSection extends SmileSection {
    private boolean calculated_ = false;

    private NoArbSabrInterpolation noArbSabrInterpolation_;

    // market data
    private final Handle<Quote> forward_;
    private final Handle<Quote> atmVolatility_;
    private final List<Handle<Quote>> volHandles_;
    private final List<Double> strikes_;
    private final List<Double> actualStrikes_;
    private final boolean hasFloatingStrikes_;

    private double forwardValue_;
    private final List<Double> vols_ = new ArrayList<>();

    // SABR parameters
    private final double alpha_, beta_, nu_, rho_;
    // SABR interpolation settings
    private final boolean isAlphaFixed_, isBetaFixed_, isNuFixed_, isRhoFixed_;
    private final boolean vegaWeighted_;
    private final EndCriteria endCriteria_;
    private final OptimizationMethod method_;

    /** All market data are quotes. */
    public NoArbSabrInterpolatedSmileSection(
            Date optionDate,
            Handle<Quote> forward,
            List<Double> strikes,
            boolean hasFloatingStrikes,
            Handle<Quote> atmVolatility,
            List<Handle<Quote>> volHandles,
            double alpha,
            double beta,
            double nu,
            double rho,
            boolean isAlphaFixed,
            boolean isBetaFixed,
            boolean isNuFixed,
            boolean isRhoFixed,
            boolean vegaWeighted,
            EndCriteria endCriteria,
            OptimizationMethod method,
            DayCounter dc) {
        super(optionDate, dc == null ? new Actual365Fixed() : dc, new Date(),
                Sarb.VolatilityType.ShiftedLognormal, 0.0);
        forward_ = forward;
        atmVolatility_ = atmVolatility;
        volHandles_ = new ArrayList<>(volHandles);
        strikes_ = new ArrayList<>(strikes);
        actualStrikes_ = new ArrayList<>(strikes);
        hasFloatingStrikes_ = hasFloatingStrikes;
        alpha_ = alpha;
        beta_ = beta;
        nu_ = nu;
        rho_ = rho;
        isAlphaFixed_ = isAlphaFixed;
        isBetaFixed_ = isBetaFixed;
        isNuFixed_ = isNuFixed;
        isRhoFixed_ = isRhoFixed;
        vegaWeighted_ = vegaWeighted;
        endCriteria_ = endCriteria;
        method_ = method;

        registerWith(forward_.currentLink());
        registerWith(atmVolatility_.currentLink());
        for (Handle<Quote> volHandle : volHandles_)
            registerWith(volHandle.currentLink());
    }

    /** No quotes. */
    public NoArbSabrInterpolatedSmileSection(
            Date optionDate,
            double forward,
            List<Double> strikes,
            boolean hasFloatingStrikes,
            double atmVolatility,
            List<Double> volHandles,
            double alpha,
            double beta,
            double nu,
            double rho,
            boolean isAlphaFixed,
            boolean isBetaFixed,
            boolean isNuFixed,
            boolean isRhoFixed,
            boolean vegaWeighted,
            EndCriteria endCriteria,
            OptimizationMethod method,
            DayCounter dc) {
        super(optionDate, dc == null ? new Actual365Fixed() : dc, new Date(),
                Sarb.VolatilityType.ShiftedLognormal, 0.0);
        forward_ = new Handle<>(new SimpleQuote(forward), true);
        atmVolatility_ = new Handle<>(new SimpleQuote(atmVolatility), true);
        volHandles_ = new ArrayList<>(volHandles.size());
        for (Double vol : volHandles)
            volHandles_.add(new Handle<>(new SimpleQuote(vol), true));
        strikes_ = new ArrayList<>(strikes);
        actualStrikes_ = new ArrayList<>(strikes);
        hasFloatingStrikes_ = hasFloatingStrikes;
        alpha_ = alpha;
        beta_ = beta;
        nu_ = nu;
        rho_ = rho;
        isAlphaFixed_ = isAlphaFixed;
        isBetaFixed_ = isBetaFixed;
        isNuFixed_ = isNuFixed;
        isRhoFixed_ = isRhoFixed;
        vegaWeighted_ = vegaWeighted;
        endCriteria_ = endCriteria;
        method_ = method;
    }

    @Override
    public void update() {
        calculated_ = false;
        super.update();
    }

    private void calculate() {
        if (!calculated_) {
            calculated_ = true;
            try {
                performCalculations();
            } catch (RuntimeException e) {
                calculated_ = false;
                throw e;
            }
        }
    }

    private void performCalculations() {
        forwardValue_ = forward_.getValue().value();
        vols_.clear();
        actualStrikes_.clear();
        // we populate the volatilities, skipping the invalid ones
        for (int i = 0; i < volHandles_.size(); ++i) {
            if (volHandles_.get(i).getValue().isValid()) {
                if (hasFloatingStrikes_) {
                    actualStrikes_.add(forwardValue_ + strikes_.get(i));
                    vols_.add(atmVolatility_.getValue().value() +
                            volHandles_.get(i).getValue().value());
                } else {
                    actualStrikes_.add(strikes_.get(i));
                    vols_.add(volHandles_.get(i).getValue().value());
                }
            }
        }
        // we are recreating the sabr interpolation object unconditionally to
        // avoid iterator invalidation
        createInterpolation();
        noArbSabrInterpolation_.update();
    }

    private void createInterpolation() {
        double[] x = new double[actualStrikes_.size()];
        double[] y = new double[vols_.size()];
        for (int i = 0; i < x.length; ++i) {
            x[i] = actualStrikes_.get(i);
            y[i] = vols_.get(i);
        }
        noArbSabrInterpolation_ = new NoArbSabrInterpolation(
                x, y, exerciseTime(), forwardValue_,
                alpha_, beta_, nu_, rho_,
                isAlphaFixed_, isBetaFixed_, isNuFixed_, isRhoFixed_,
                vegaWeighted_, endCriteria_, method_, 0.0020);
    }

    @Override
    public double minStrike() {
        calculate();
        return actualStrikes_.get(0);
    }

    @Override
    public double maxStrike() {
        calculate();
        return actualStrikes_.get(actualStrikes_.size() - 1);
    }

    @Override
    public double atmLevel() {
        calculate();
        return forwardValue_;
    }

    @Override
    public double varianceImpl(double strike) {
        calculate();
        double v = noArbSabrInterpolation_.value(strike, true);
        return v * v * exerciseTime();
    }

    @Override
    protected double volatilityImpl(double strike) {
        calculate();
        return noArbSabrInterpolation_.value(strike, true);
    }

    public double alpha() {
        calculate();
        return noArbSabrInterpolation_.alpha();
    }

    public double beta() {
        calculate();
        return noArbSabrInterpolation_.beta();
    }

    public double nu() {
        calculate();
        return noArbSabrInterpolation_.nu();
    }

    public double rho() {
        calculate();
        return noArbSabrInterpolation_.rho();
    }

    public double rmsError() {
        calculate();
        return noArbSabrInterpolation_.rmsError();
    }

    public double maxError() {
        calculate();
        return noArbSabrInterpolation_.maxError();
    }

    public EndCriteria.Type endCriteria() {
        calculate();
        return noArbSabrInterpolation_.endCriteria();
    }
}
