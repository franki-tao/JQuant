package jquant.indexes;

import jquant.Currency;
import jquant.Handle;
import jquant.termstructures.YieldTermStructure;
import jquant.time.*;

public class OvernightIndex extends IborIndex {
    public OvernightIndex(final String familyName,
                          int settlementDays,
                          final Currency currency,
                          final Calendar fixingCalendar,
                          final DayCounter dayCounter,
                          final Handle<YieldTermStructure> h) {
        super(familyName, TimeUtils.multiply(1, TimeUnit.DAYS), settlementDays,
                currency, fixingCalendar, BusinessDayConvention.FOLLOWING,
                false, dayCounter, h);
    }

    //! returns a copy of itself linked to a different forwarding curve
    @Override
    public IborIndex clone(final Handle<YieldTermStructure> h) {
        return new OvernightIndex(familyName(),
                fixingDays(),
                currency(),
                fixingCalendar(),
                dayCounter(),
                h);
    }
}
