package jquant.models.marketmodels;

import jquant.math.CommonUtil;
import jquant.math.Matrix;

import java.util.ArrayList;
import java.util.List;

/**
 * Official QuantLib {@code ql/models/marketmodels/pathwisediscounter.hpp/.cpp}
 * port.
 *
 * <p>This class returns the number of units of the discretely compounding money
 * market account that 1 unit of cash at the payment can buy using the LIBOR
 * rates from current step.
 *
 * <p>It also returns the derivative of this number with respect to each of the
 * rates.
 *
 * <p>Discounting is purely based on the simulation LIBOR rates, to get a
 * discounting back to zero you need to multiply by the discount factor of
 * t_0.
 */
public class MarketModelPathwiseDiscounter {

    private final int before_;
    private final int numberRates_;
    private final double beforeWeight_;
    private final double postWeight_;
    private final List<Double> taus_ = new ArrayList<>();

    public MarketModelPathwiseDiscounter(double paymentTime,
                                         final List<Double> rateTimes) {
        Utilities.checkIncreasingTimes(rateTimes);

        numberRates_ = rateTimes.size() - 1;
        int before = CommonUtil.lower_bound(rateTimes, paymentTime);

        // handle the case where the payment is in the last
        // period or after the last period
        if (before > rateTimes.size() - 2)
            before = rateTimes.size() - 2;
        before_ = before;

        beforeWeight_ = 1.0 - (paymentTime - rateTimes.get(before_))
                / (rateTimes.get(before_ + 1) - rateTimes.get(before_));

        postWeight_ = 1.0 - beforeWeight_;

        for (int i = 0; i < numberRates_; ++i)
            taus_.add(rateTimes.get(i + 1) - rateTimes.get(i));
    }

    public void getFactors(final Matrix LIBORRates,
                           final Matrix Discounts,
                           int currentStep,
                           List<Double> factors) {
        double preDF = Discounts.get(currentStep, before_);
        double postDF = Discounts.get(currentStep, before_ + 1);

        for (int i = before_ + 1; i < numberRates_; ++i)
            factors.set(i + 1, 0.0);

        if (postWeight_ == 0.0) {
            factors.set(0, preDF);

            for (int i = 0; i < before_; ++i)
                factors.set(i + 1,
                        -preDF * taus_.get(i)
                                * Discounts.get(currentStep, i + 1)
                                / Discounts.get(currentStep, i));

            factors.set(before_ + 1, 0.0);

            return;
        }

        double df = preDF * Math.pow(postDF / preDF, postWeight_);

        factors.set(0, df);

        for (int i = 0; i <= before_; ++i)
            factors.set(i + 1,
                    -df * taus_.get(i)
                            * Discounts.get(currentStep, i + 1)
                            / Discounts.get(currentStep, i));

        factors.set(before_ + 1, factors.get(before_ + 1) * postWeight_);
    }
}
