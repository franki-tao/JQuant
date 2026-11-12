package jquant.termstructures.yield;

import jquant.math.Array;
import jquant.quotes.SimpleQuote;

import java.util.ArrayList;
import java.util.List;

import static jquant.math.CommonUtil.QL_REQUIRE;

/**
 * Additional global-bootstrap variables backed by {@link SimpleQuote}s. This is
 * a port of QuantLib's {@code SimpleQuoteVariables} in
 * {@code ql/termstructures/globalbootstrapvars.cpp}.
 *
 * <p>A quote with a lower bound is optimized through an exponential/logarithmic
 * transform, so its value stays strictly above the bound. Missing initial guesses
 * default to {@code 0.0}; missing lower bounds mean "no transform", matching the
 * official {@code Null<Real>()} sentinel.
 *
 * <p>https://github.com/lballabio/QuantLib/blob/master/ql/termstructures/globalbootstrapvars.hpp
 */
public class SimpleQuoteVariables implements GlobalBootstrap.AdditionalBootstrapVariables {
    private final List<SimpleQuote> quotes_;
    private final List<Double> initialGuesses_;
    private final List<Double> lowerBounds_;

    public SimpleQuoteVariables(List<SimpleQuote> quotes) {
        this(quotes, new ArrayList<>(), new ArrayList<>());
    }

    public SimpleQuoteVariables(List<SimpleQuote> quotes,
                                List<Double> initialGuesses,
                                List<Double> lowerBounds) {
        quotes_ = quotes == null ? new ArrayList<>() : new ArrayList<>(quotes);
        initialGuesses_ = initialGuesses == null ? new ArrayList<>() : new ArrayList<>(initialGuesses);
        lowerBounds_ = lowerBounds == null ? new ArrayList<>() : new ArrayList<>(lowerBounds);
        QL_REQUIRE(initialGuesses_.size() <= quotes_.size(), "too many initialGuesses");
        QL_REQUIRE(lowerBounds_.size() <= quotes_.size(), "too many lowerBounds");
    }

    @Override
    public Array initialize(boolean validData) {
        Array guesses = new Array(quotes_.size());
        for (int i = 0, size = guesses.size(); i < size; ++i) {
            double guess;
            if (validData) {
                guess = quotes_.get(i).value();
            } else {
                guess = get(initialGuesses_, i, 0.0);
                quotes_.get(i).setValue(guess);
            }
            guesses.set(i, transformInverse(guess, i));
        }
        return guesses;
    }

    @Override
    public void update(Array x) {
        for (int i = 0, size = x.size(); i < size; ++i) {
            quotes_.get(i).setValue(transformDirect(x.get(i), i));
        }
    }

    private double transformDirect(double x, int i) {
        Double lb = get(lowerBounds_, i, null);
        return lb == null ? x : Math.exp(x) + lb;
    }

    private double transformInverse(double x, int i) {
        Double lb = get(lowerBounds_, i, null);
        return lb == null ? x : Math.log(x - lb);
    }

    private static <T> T get(List<T> values, int i, T defaultValue) {
        return i < values.size() ? values.get(i) : defaultValue;
    }
}
