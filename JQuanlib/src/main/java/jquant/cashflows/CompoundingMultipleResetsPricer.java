package jquant.cashflows;

import java.util.List;

/*! Compounding pricer for multiple-reset coupons.

    Port of QuantLib's {@code CompoundingMultipleResetsPricer} in
    {@code ql/cashflows/multipleresetscoupon.hpp} and
    {@code ql/cashflows/multipleresetscoupon.cpp}.
*/
public class CompoundingMultipleResetsPricer extends MultipleResetsPricer {

    @Override
    public double swapletRate() {
        // past or future fixing is managed in InterestRateIndex::fixing()

        double compoundFactor = 1.0;
        List<Double> subPeriodFractions = coupon_.dt();
        int nCount = subPeriodFixings_.size();
        for (int i = 0; i < nCount; i++)
            compoundFactor *= (1.0 + subPeriodFixings_.get(i) * subPeriodFractions.get(i));

        double rate = (compoundFactor - 1.0) / coupon_.accrualPeriod();
        return coupon_.gearing() * rate + coupon_.spread();
    }
}
