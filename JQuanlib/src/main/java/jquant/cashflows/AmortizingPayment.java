package jquant.cashflows;

import jquant.time.Date;

/**
 * Amortizing payment. A thin {@link SimpleCashFlow} subclass, kept as its own
 * type so that callers can identify intermediate redemption flows and so that
 * the official {@code leg.push_back(make_shared<AmortizingPayment>(...))}
 * construction translates directly.
 *
 * <p>Official {@code AmortizingPayment} is exactly a {@code SimpleCashFlow}
 * with no extra state or behaviour. Like {@link Redemption}, the Java class
 * does not override the visitor hook, so no visitor is invented here.
 *
 * <p>https://github.com/lballabio/QuantLib/blob/master/ql/cashflows/simplecashflow.hpp
 */
public class AmortizingPayment extends SimpleCashFlow {

    public AmortizingPayment(double amount, Date date) {
        super(amount, date);
    }
}
