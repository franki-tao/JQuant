package jquant.time.daycounters;

import jquant.time.DayCounter;
import jquant.time.impl.Actual365FixedCAImpl;
import jquant.time.impl.Actual365FixedImpl;
import jquant.time.impl.Actual365FixedNLImpl;
import jquant.time.impl.DayCounterImpl;

import static jquant.math.CommonUtil.QL_FAIL;

public class Actual365Fixed extends DayCounter {
    public enum Convention { Standard, Canadian, NoLeap }

    public Actual365Fixed(Convention convention) {
        super(implementation(convention));
    }

    private static DayCounterImpl implementation(Convention c) {
        switch (c) {
            case Standard:
                return new Actual365FixedImpl();
            case Canadian:
                return new Actual365FixedCAImpl();
            case NoLeap:
                return new Actual365FixedNLImpl();
            default:
                QL_FAIL("unknown Actual/365 (Fixed) convention");
        }
        return new Actual365FixedImpl();
    }

}
