package jquant.indexes;

import jquant.Currency;
import jquant.Handle;
import jquant.termstructures.YieldTermStructure;
import jquant.time.BusinessDayConvention;
import jquant.time.Calendar;
import jquant.time.Date;
import jquant.time.DayCounter;
import jquant.time.Period;
import jquant.time.TimeUnit;
import jquant.time.calendars.JointCalendar;
import jquant.time.calendars.UnitedKingdom;

import static jquant.math.CommonUtil.QL_REQUIRE;

/**
 * ICE Libor. The fixing calendar is the London stock exchange. Value and
 * maturity dates are adjusted on the joint London and financial-centre calendar.
 *
 * <p>https://github.com/lballabio/QuantLib/blob/v1.36/ql/indexes/ibor/libor.cpp
 */
public class Libor extends IborIndex {
    private final Calendar financialCenterCalendar_;
    private final Calendar jointCalendar_;

    public Libor(String familyName,
                 Period tenor,
                 int settlementDays,
                 Currency currency,
                 Calendar financialCenterCalendar,
                 DayCounter dayCounter,
                 Handle<YieldTermStructure> h) {
        super(familyName, tenor, settlementDays, currency,
                new UnitedKingdom(UnitedKingdom.Market.Exchange),
                liborConvention(tenor), liborEndOfMonth(tenor), dayCounter, h);
        QL_REQUIRE(tenor.units() != TimeUnit.DAYS,
                "for daily tenors (" + tenor + ") dedicated DailyTenor constructor must be used");
        QL_REQUIRE(currency == null || currency.empty() || !"EUR".equals(currency.code()),
                "for EUR Libor dedicated EurLibor constructor must be used");
        financialCenterCalendar_ = financialCenterCalendar;
        jointCalendar_ = new JointCalendar(
                new UnitedKingdom(UnitedKingdom.Market.Exchange),
                financialCenterCalendar,
                JointCalendar.Rule.JoinHolidays);
    }

    @Override
    public Date valueDate(Date fixingDate) {
        QL_REQUIRE(isValidFixingDate(fixingDate),
                "Fixing date " + fixingDate + " is not valid");
        Date d = fixingCalendar().advance(fixingDate, fixingDays(), TimeUnit.DAYS,
                BusinessDayConvention.FOLLOWING, false);
        return jointCalendar_.adjust(d, BusinessDayConvention.FOLLOWING);
    }

    @Override
    public Date maturityDate(Date valueDate) {
        return jointCalendar_.advance(valueDate, tenor(), businessDayConvention(), endOfMonth());
    }

    public Calendar jointCalendar() {
        return jointCalendar_;
    }

    public Calendar financialCenterCalendar() {
        return financialCenterCalendar_;
    }

    private static BusinessDayConvention liborConvention(Period tenor) {
        switch (tenor.units()) {
            case DAYS:
            case WEEKS:
                return BusinessDayConvention.FOLLOWING;
            case MONTHS:
            case YEARS:
                return BusinessDayConvention.MODIFIED_FOLLOWING;
            default:
                QL_REQUIRE(false, "invalid time units");
                return BusinessDayConvention.FOLLOWING;
        }
    }

    private static boolean liborEndOfMonth(Period tenor) {
        switch (tenor.units()) {
            case DAYS:
            case WEEKS:
                return false;
            case MONTHS:
            case YEARS:
                return true;
            default:
                QL_REQUIRE(false, "invalid time units");
                return false;
        }
    }
}
