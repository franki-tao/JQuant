package jquant.time.calendars.impl;

import jquant.time.Date;

public class SofrImpl extends GovernmentBondImpl {
    @Override
    public String name() {
        return "SOFR fixing calendar";
    }

    @Override
    public boolean isBusinessDay(Date date) {
        // so far (that is, up to 2023 at the time of this change) SOFR never fixed
        // on Good Friday.  We're extrapolating that pattern.  This might change if
        // a fixing on Good Friday occurs in future years.
        final int dY = date.dayOfYear();
        final int y = date.year();

        // Good Friday
        if (dY == (easterMonday(y) - 3))
            return false;

        return super.isBusinessDay(date);
    }
}
