package jquant.time.impl;

import jquant.time.Date;
import jquant.time.TimeUtils;

import static jquant.math.CommonUtil.QL_REQUIRE;
import static jquant.time.TimeUtils.daysBetween;

public class Actual365FixedCAImpl extends DayCounterImpl {
    @Override
    public String name() {
        return "Actual/365 (Fixed) Canadian Bond";
    }

    @Override
    public double yearFraction(Date d1, Date d2, Date refPeriodStart, Date refPeriodEnd) {
        if (TimeUtils.equals(d1, d2))
            return 0.0;

        // We need the period to calculate the frequency
        QL_REQUIRE(TimeUtils.neq(refPeriodStart, new Date()), "invalid refPeriodStart");
        QL_REQUIRE(TimeUtils.neq(refPeriodEnd, new Date()), "invalid refPeriodEnd");

        double dcs = daysBetween(d1, d2);
        double dcc = daysBetween(refPeriodStart, refPeriodEnd);
        int months = (int) (Math.round(12 * dcc / 365));
        QL_REQUIRE(months != 0,
                "invalid reference period for Act/365 Canadian; "+
                "must be longer than a month");
        int frequency = (int) (12 / months);
        QL_REQUIRE(frequency != 0,
                "invalid reference period for Act/365 Canadian; "+
                "must not be longer than a year");

        if (dcs < (int) (365 / frequency))
            return dcs / 365.0;

        return 1. / frequency - (dcc - dcs) / 365.0;
    }
}
