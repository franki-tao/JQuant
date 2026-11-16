package jquant.pricingengines.vanilla;

import jquant.EuropeanOption;
import jquant.Exercise;
import jquant.Instrument;
import jquant.Option;
import jquant.Payoff;
import jquant.PricingEngine;
import jquant.StochasticProcess1D;
import jquant.math.FastFourierTransform;
import jquant.math.interpolations.LinearInterpolation;
import jquant.patterns.Observable;
import jquant.patterns.ObservableSettings;
import jquant.patterns.Observer;
import jquant.pricingengines.StrikedTypePayoff;
import jquant.time.Date;
import org.apache.commons.math3.complex.Complex;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static jquant.math.CommonUtil.QL_FAIL;
import static jquant.math.CommonUtil.QL_REQUIRE;
import static jquant.math.MathUtils.M_PI;

/**
 * Base class for FFT pricing engines for European vanilla options.
 *
 * <p>The FFT engine calculates the values of all options with the same
 * expiry at the same time. For that reason it is very inefficient to price
 * options individually. When using this engine you should collect all the
 * options you wish to price in a list and call the engine's
 * {@link #precalculate(List)} method before calling the NPV method of the
 * option.
 *
 * <p>References: Carr, P. and D. B. Madan (1998), "Option Valuation using the
 * fast Fourier transform," Journal of Computational Finance, 2, 61-73.
 *
 * <p>https://github.com/lballabio/QuantLib/blob/master/ql/experimental/variancegamma/fftengine.cpp
 */
public abstract class FFTEngine implements PricingEngine {
    protected final StochasticProcess1D process_;
    protected final double lambda_; // Log strike spacing

    private final Option.arguments arguments_ = new Option.arguments();
    private final EuropeanOption.results results_ = new EuropeanOption.results();
    private final Set<Observer> observers_ = new HashSet<>();

    // Expiry date serial number -> payoff -> value. The official map is keyed
    // by Date and by payoff identity; Date has no value equality in JQuant,
    // so its serial number is used instead.
    private final Map<Integer, Map<StrikedTypePayoff, Double>> resultMap_ = new HashMap<>();

    public FFTEngine(StochasticProcess1D process, double logStrikeSpacing) {
        process_ = process;
        lambda_ = logStrikeSpacing;
        results_.additionalResults = new HashMap<>();
        process_.registerObserver(new Observer() {
            @Override
            public void registerWith(Observable observable) {
            }

            @Override
            public void unregisterWithAll() {
            }

            @Override
            public void unregisterWith(Observable observable) {
            }

            @Override
            public void update() {
                FFTEngine.this.update();
            }
        });
    }

    /** Process has changed so cached values may no longer be correct. */
    public void update() {
        resultMap_.clear();
        notifyObservers();
    }

    @Override
    public void calculate() {
        QL_REQUIRE(arguments_.exercise.type() == Exercise.Type.European,
                "not an European Option");
        QL_REQUIRE(arguments_.payoff instanceof StrikedTypePayoff,
                "non-striked payoff given");
        StrikedTypePayoff payoff = (StrikedTypePayoff) arguments_.payoff;

        int expiry = arguments_.exercise.lastDate().serialNumber();
        Map<StrikedTypePayoff, Double> byPayoff = resultMap_.get(expiry);
        if (byPayoff != null) {
            Double value = byPayoff.get(payoff);
            if (value != null) {
                results_.value = value;
                return;
            }
        }

        // Option not precalculated - do entire FFT for one option.
        // Not very efficient - call precalculate!
        calculateUncached(payoff, arguments_.exercise);
    }

    protected void calculateUncached(StrikedTypePayoff payoff, Exercise exercise) {
        EuropeanOption option = new EuropeanOption(payoff, exercise);
        List<Instrument> optionList = new ArrayList<>();
        optionList.add(option);

        FFTEngine tempEngine = clone();
        tempEngine.precalculate(optionList);
        option.setPricingEngine(tempEngine);
        results_.value = option.NPV();
    }

    /**
     * Calculates and caches prices for all the given European options,
     * grouping payoffs by expiry date since one FFT prices a whole expiry.
     */
    public void precalculate(List<? extends Instrument> optionList) {
        resultMap_.clear();

        Map<Integer, List<StrikedTypePayoff>> payoffMap = new LinkedHashMap<>();
        Map<Integer, Date> expiryDates = new HashMap<>();

        for (Instrument instrument : optionList) {
            QL_REQUIRE(instrument instanceof EuropeanOption,
                    "instrument must be option");
            EuropeanOption option = (EuropeanOption) instrument;
            QL_REQUIRE(option.exercise().type() == Exercise.Type.European,
                    "not an European Option");

            Payoff payoff = option.payoff();
            QL_REQUIRE(payoff instanceof StrikedTypePayoff,
                    "non-striked payoff given");
            StrikedTypePayoff strikedPayoff = (StrikedTypePayoff) payoff;

            Date expiryDate = option.exercise().lastDate();
            int expiry = expiryDate.serialNumber();
            payoffMap.computeIfAbsent(expiry, k -> new ArrayList<>()).add(strikedPayoff);
            expiryDates.putIfAbsent(expiry, expiryDate);
        }

        double alpha = 1.25;

        for (Map.Entry<Integer, List<StrikedTypePayoff>> payIt : payoffMap.entrySet()) {
            Date expiryDate = expiryDates.get(payIt.getKey());
            List<StrikedTypePayoff> payoffs = payIt.getValue();

            // Calculate n large enough for maximum strike, and round up to a power of 2
            double maxStrike = 0.0;
            for (StrikedTypePayoff payoff : payoffs) {
                if (payoff.strike() > maxStrike) {
                    maxStrike = payoff.strike();
                }
            }
            double nR = 2.0 * (Math.log(maxStrike) + lambda_) / lambda_;
            int log2_n = ((int) (Math.log(nR) / Math.log(2.0))) + 1;
            int n = 1 << log2_n;

            // Strike range (equation 19,20)
            double b = n * lambda_ / 2.0;

            // Grid spacing (equation 23)
            double eta = 2.0 * M_PI / (lambda_ * n);

            // Discount factor
            double df = discountFactor(expiryDate);
            double div = dividendYield(expiryDate);

            // Input to fourier transform
            List<Complex> fti = new ArrayList<>(n);
            for (int i = 0; i < n; i++) {
                fti.add(Complex.ZERO);
            }

            // Precalculate any discount factors etc.
            precalculateExpiry(expiryDate);

            for (int i = 0; i < n; i++) {
                double v_j = eta * i;
                double sw = eta * (3.0 + ((i % 2) == 0 ? -1.0 : 1.0)
                        - ((i == 0) ? 1.0 : 0.0)) / 3.0;

                Complex psi = complexFourierTransform(
                        new Complex(v_j, -(alpha + 1))).multiply(df);
                psi = psi.divide(new Complex(
                        alpha * alpha + alpha - v_j * v_j,
                        (2 * alpha + 1.0) * v_j));

                fti.set(i, new Complex(0.0, b * v_j).exp()
                        .multiply(sw).multiply(psi));
            }

            // Perform fft
            List<Complex> results = new ArrayList<>(n);
            for (int i = 0; i < n; i++) {
                results.add(Complex.ZERO);
            }
            FastFourierTransform fft = new FastFourierTransform(log2_n);
            fft.transformComplex(fti, results);

            // Call prices
            double[] prices = new double[n];
            double[] strikes = new double[n];
            for (int i = 0; i < n; i++) {
                double k_u = -b + lambda_ * i;
                prices[i] = (Math.exp(-alpha * k_u) / M_PI) * results.get(i).getReal();
                strikes[i] = Math.exp(k_u);
            }
            LinearInterpolation interpolation = new LinearInterpolation(strikes, prices);

            for (StrikedTypePayoff payoff : payoffs) {
                double callPrice = interpolation.value(payoff.strike(), false);
                double value;
                switch (payoff.optionType()) {
                    case Call:
                        value = callPrice;
                        break;
                    case Put:
                        value = callPrice - process_.x0() * div + payoff.strike() * df;
                        break;
                    default:
                        QL_FAIL("Invalid option type");
                        return;
                }
                resultMap_.computeIfAbsent(
                        payIt.getKey(), k -> new HashMap<>()).put(payoff, value);
            }
        }
    }

    /** Clone this engine, preserving the process and the strike spacing. */
    public abstract FFTEngine clone();

    protected abstract void precalculateExpiry(Date d);

    protected abstract Complex complexFourierTransform(Complex u);

    protected abstract double discountFactor(Date d);

    protected abstract double dividendYield(Date d);

    @Override
    public arguments getArguments() {
        return arguments_;
    }

    @Override
    public results getResults() {
        return results_;
    }

    @Override
    public void reset() {
        results_.value = Double.NaN;
        results_.errorEstimate = Double.NaN;
        results_.valuationDate = new Date();
        results_.delta = Double.NaN;
        results_.gamma = Double.NaN;
        results_.theta = Double.NaN;
        results_.thetaPerDay = Double.NaN;
        results_.vega = Double.NaN;
        results_.rho = Double.NaN;
        results_.dividendRho = Double.NaN;
        results_.elasticity = Double.NaN;
        if (results_.additionalResults != null) {
            results_.additionalResults.clear();
        }
    }

    @Override
    public void registerObserver(Observer o) {
        observers_.add(o);
    }

    @Override
    public void unregisterObserver(Observer o) {
        observers_.remove(o);
    }

    @Override
    public void notifyObservers() {
        if (!ObservableSettings.getInstance().isUpdatesEnabled()) {
            ObservableSettings.getInstance().registerDeferred(observers_);
        } else {
            for (Observer observer : Set.copyOf(observers_)) {
                observer.update();
            }
        }
    }
}
