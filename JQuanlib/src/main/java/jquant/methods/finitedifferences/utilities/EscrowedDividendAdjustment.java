package jquant.methods.finitedifferences.utilities;

import jquant.Handle;
import jquant.cashflows.Dividend;
import jquant.termstructures.YieldTermStructure;
import jquant.time.Date;

import java.util.List;
import java.util.function.ToDoubleFunction;

/**
 * Dividend adjustment for the escrowed dividend model.
 *
 * <p>Port of
 * {@code ql/methods/finitedifferences/utilities/escroweddividendadjustment.{hpp,cpp}}.
 */
public class EscrowedDividendAdjustment {
    private final List<Dividend> dividendSchedule_;
    private final Handle<YieldTermStructure> rTS_, qTS_;
    private final ToDoubleFunction<Date> toTime_;
    private final double maturity_;

    public EscrowedDividendAdjustment(List<Dividend> dividendSchedule,
                                      Handle<YieldTermStructure> rTS,
                                      Handle<YieldTermStructure> qTS,
                                      ToDoubleFunction<Date> toTime,
                                      double maturity) {
        dividendSchedule_ = dividendSchedule;
        rTS_ = rTS;
        qTS_ = qTS;
        toTime_ = toTime;
        maturity_ = maturity;
    }

    public double dividendAdjustment(double t) {
        double divAdj = 0.0;
        for (Dividend dividend : dividendSchedule_) {
            final double divTime = toTime_.applyAsDouble(dividend.date());

            if (divTime >= t && divTime <= maturity_) {
                divAdj -= dividend.amount()
                        * rTS_.getValue().discount(divTime, false)
                        / rTS_.getValue().discount(t, false)
                        * qTS_.getValue().discount(t, false)
                        / qTS_.getValue().discount(divTime, false);
            }
        }
        return divAdj;
    }

    public Handle<YieldTermStructure> riskFreeRate() {
        return rTS_;
    }

    public Handle<YieldTermStructure> dividendYield() {
        return qTS_;
    }
}
