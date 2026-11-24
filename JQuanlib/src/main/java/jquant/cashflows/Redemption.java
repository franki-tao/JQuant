package jquant.cashflows;

import jquant.time.Date;

/**
 * Bond redemption. A thin {@link SimpleCashFlow} subclass, kept as its own
 * type so that callers can identify redemption flows and so that the official
 * {@code leg.push_back(make_shared<Redemption>(...))} construction translates
 * directly.
 *
 * <p>Official {@code Redemption} is exactly a {@code SimpleCashFlow} with no
 * extra state or behaviour. Like the official value-type {@code Date}, the
 * date is stored by copy so the redemption does not alias the caller's
 * mutable date.
 *
 * <p>https://github.com/lballabio/QuantLib/blob/master/ql/cashflows/simplecashflow.hpp
 */
public class Redemption extends SimpleCashFlow {

    public Redemption(double amount, Date date) {
        super(amount, date.copy());
    }
}
