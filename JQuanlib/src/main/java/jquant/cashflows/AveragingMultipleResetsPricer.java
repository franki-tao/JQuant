package jquant.cashflows;

import java.util.List;

/*! Averaging pricer for multiple-reset coupons.

    Port of QuantLib's {@code AveragingMultipleResetsPricer} in
    {@code ql/cashflows/multipleresetscoupon.hpp} and
    {@code ql/cashflows/multipleresetscoupon.cpp}.
*/
public class AveragingMultipleResetsPricer extends MultipleResetsPricer {

    @Override
    public double swapletRate() {
        // past or future fixing is managed in InterestRateIndex::fixing()

        int nCount = subPeriodFixings_.size();
        List<Double> subPeriodFractions = coupon_.dt();
        double aggregateFactor = 0.0;
        for (int i = 0; i < nCount; i++)
            aggregateFactor += subPeriodFixings_.get(i) * subPeriodFractions.get(i);

        double rate = aggregateFactor / coupon_.accrualPeriod();
        return coupon_.gearing() * rate + coupon_.spread();
    }
}
