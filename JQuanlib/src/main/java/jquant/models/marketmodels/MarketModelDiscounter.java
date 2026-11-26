package jquant.models.marketmodels;

import java.util.List;

/**
 * Official QuantLib {@code ql/models/marketmodels/discounter.hpp/.cpp} port.
 */
public class MarketModelDiscounter {

    private int before_;
    private double beforeWeight_;

    public MarketModelDiscounter(double paymentTime,
                                 final List<Double> rateTimes) {
        Utilities.checkIncreasingTimes(rateTimes);
        int before = 0;
        while (before < rateTimes.size() && rateTimes.get(before) < paymentTime)
            ++before;
        before_ = before;

        // handle the case where the payment is in the last
        // period or after the last period
        if (before_ > rateTimes.size() - 2)
            before_ = rateTimes.size() - 2;

        beforeWeight_ = 1.0 - (paymentTime - rateTimes.get(before_))
                / (rateTimes.get(before_ + 1) - rateTimes.get(before_));
    }

    public double numeraireBonds(final CurveState curveState,
                                 int numeraire) {
        double preDF = curveState.discountRatio(before_, numeraire);
        if (beforeWeight_ == 1.0)
            return preDF;

        double postDF = curveState.discountRatio(before_ + 1, numeraire);
        if (beforeWeight_ == 0.0)
            return postDF;

        return Math.pow(preDF, beforeWeight_) * Math.pow(postDF, 1.0 - beforeWeight_);
    }
}
