package jquant.models.marketmodels.models;

import jquant.models.marketmodels.Utilities;
import jquant.termstructures.volatility.AbcdFunction;

import java.util.ArrayList;
import java.util.List;

import static jquant.math.CommonUtil.QL_REQUIRE;

/**
 * Official QuantLib
 * {@code ql/models/marketmodels/models/piecewiseconstantabcdvariance.hpp/.cpp}
 * port.
 */
public class PiecewiseConstantAbcdVariance extends PiecewiseConstantVariance {

    private final List<Double> variances_;
    private final List<Double> volatilities_;
    private final List<Double> rateTimes_;
    private final double a_;
    private final double b_;
    private final double c_;
    private final double d_;

    public PiecewiseConstantAbcdVariance(double a, double b, double c, double d,
                                         int resetIndex,
                                         List<Double> rateTimes) {
        int n = rateTimes.size() - 1;
        variances_ = filledList(n, 0.0);
        volatilities_ = filledList(n, 0.0);
        rateTimes_ = new ArrayList<>(rateTimes);
        a_ = a;
        b_ = b;
        c_ = c;
        d_ = d;

        Utilities.checkIncreasingTimes(rateTimes);
        QL_REQUIRE(rateTimes.size() > 1,
                "Rate times must contain at least two values");
        QL_REQUIRE(resetIndex < rateTimes_.size() - 1,
                "resetIndex (" + resetIndex +
                        ") must be less than rateTimes.size()-1 (" +
                        (rateTimes_.size() - 1) + ")");
        AbcdFunction abcd = new AbcdFunction(a, b, c, d);
        for (int i = 0; i <= resetIndex; ++i) {
            double startTime = (i == 0 ? 0.0 : rateTimes_.get(i - 1));
            variances_.set(i, abcd.variance(startTime,
                    rateTimes_.get(i),
                    rateTimes_.get(resetIndex)));
            double totTime = rateTimes_.get(i) - startTime;
            volatilities_.set(i, Math.sqrt(variances_.get(i) / totTime));
        }
    }

    /** Official copy constructor (implicit in C++ {@code PiecewiseConstantAbcdVariance(v)}). */
    public PiecewiseConstantAbcdVariance(final PiecewiseConstantAbcdVariance other) {
        variances_ = new ArrayList<>(other.variances_);
        volatilities_ = new ArrayList<>(other.volatilities_);
        rateTimes_ = new ArrayList<>(other.rateTimes_);
        a_ = other.a_;
        b_ = other.b_;
        c_ = other.c_;
        d_ = other.d_;
    }

    public double[] getABCD() {
        return new double[]{a_, b_, c_, d_};
    }

    @Override
    public List<Double> rateTimes() {
        return rateTimes_;
    }

    @Override
    public List<Double> variances() {
        return variances_;
    }

    @Override
    public List<Double> volatilities() {
        return volatilities_;
    }

    private static List<Double> filledList(int n, double value) {
        List<Double> result = new ArrayList<>();
        for (int i = 0; i < n; ++i)
            result.add(value);
        return result;
    }
}
