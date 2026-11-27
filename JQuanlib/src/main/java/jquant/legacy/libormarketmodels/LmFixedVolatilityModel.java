package jquant.legacy.libormarketmodels;

import jquant.math.Array;
import jquant.math.CommonUtil;

import java.util.List;

/**
 * Model of fixed (piecewise-constant in time) volatilities for Libor market
 * models.
 *
 * <p>Official QuantLib
 * {@code ql/legacy/libormarketmodels/lmfixedvolmodel.hpp/.cpp} port.
 * {@code integratedVariance} is intentionally not supported by the official
 * base class; the covariance proxy catches the failure and falls back to
 * numerical integration.
 */
public class LmFixedVolatilityModel extends LmVolatilityModel {

    private final Array volatilities_;
    private final List<Double> startTimes_;

    public LmFixedVolatilityModel(final Array volatilities,
                                  final List<Double> startTimes) {
        super(startTimes.size(), 0);
        volatilities_ = volatilities;
        startTimes_ = startTimes;
        CommonUtil.QL_REQUIRE(startTimes_.size() > 1, "too few dates");
        CommonUtil.QL_REQUIRE(volatilities_.size() == startTimes_.size(),
                "volatility array and fixing time array have to have "
                        + "the same size");
        for (int i = 1; i < startTimes_.size(); ++i) {
            CommonUtil.QL_REQUIRE(startTimes_.get(i) > startTimes_.get(i - 1),
                    "invalid time (" + startTimes_.get(i) + ", vs "
                            + startTimes_.get(i - 1) + ")");
        }
    }

    private int timeIndex(double t) {
        CommonUtil.QL_REQUIRE(
                t >= startTimes_.get(0) && t <= startTimes_.get(startTimes_.size() - 1),
                "invalid time given for volatility model");

        // std::upper_bound(startTimes.begin(), startTimes.end()-1, t)
        int idx = 0;
        while (idx < startTimes_.size() - 1 && startTimes_.get(idx) <= t)
            idx++;
        return idx - 1;
    }

    @Override
    public Array volatility(double t, final Array x) {
        final int ti = timeIndex(t);

        Array tmp = new Array(size_, 0.0);
        for (int i = ti; i < size_; ++i)
            tmp.set(i, volatilities_.get(i - ti));

        return tmp;
    }

    @Override
    public double volatility(int i, double t, final Array x) {
        final int ti = timeIndex(t);
        return volatilities_.get(i - ti);
    }

    @Override
    public double integratedVariance(int i, int j, double u, final Array x) {
        CommonUtil.QL_FAIL("integratedVariance() method is not supported");
        return 0.0;
    }

    @Override
    protected void generateArguments() {
    }
}
