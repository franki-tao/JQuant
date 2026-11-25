package jquant.service.common;

import jquant.Compounding;
import jquant.time.BusinessDayConvention;
import jquant.time.Calendar;
import jquant.time.Date;
import jquant.time.DayCounter;
import jquant.time.Frequency;
import jquant.time.Month;
import jquant.time.calendars.NullCalendar;
import jquant.time.calendars.Target;
import jquant.time.calendars.UnitedKingdom;
import jquant.time.calendars.UnitedStates;
import jquant.time.daycounters.Actual360;
import jquant.time.daycounters.Actual365Fixed;
import jquant.time.daycounters.ActualActual;
import jquant.time.daycounters.Thirty360;

import java.time.LocalDate;

public final class QuantTypeMapper {

    private QuantTypeMapper() {
    }

    public static Date toQuantDate(LocalDate localDate) {
        if (localDate == null) {
            return new Date();
        }
        return new Date(localDate.getDayOfMonth(), Month.fromValue(localDate.getMonthValue()), localDate.getYear());
    }

    public static LocalDate toLocalDate(Date date) {
        if (date == null || date.serialNumber() == 0) {
            return null;
        }
        return LocalDate.of(date.year(), date.month().getValue(), date.dayOfMonth());
    }

    public static DayCounter toDayCounter(String code) {
        if (code == null || code.isBlank()) {
            return new ActualActual(ActualActual.Convention.ISMA);
        }
        return switch (code.toUpperCase()) {
            case "ACT_ACT_ISMA", "ACTUAL_ACTUAL_ISMA" -> new ActualActual(ActualActual.Convention.ISMA);
            case "ACT_ACT_ISDA", "ACTUAL_ACTUAL_ISDA" -> new ActualActual(ActualActual.Convention.ISDA);
            case "ACT_365F", "ACTUAL_365_FIXED" -> new Actual365Fixed();
            case "ACT_360", "ACTUAL_360" -> new Actual360(false);
            case "THIRTY_360_USA", "30_360" -> new Thirty360(Thirty360.Convention.USA, new Date());
            default -> throw new IllegalArgumentException("Unsupported dayCounter: " + code);
        };
    }

    public static Calendar toCalendar(String code) {
        if (code == null || code.isBlank()) {
            return new UnitedStates(UnitedStates.Market.GovernmentBond);
        }
        return switch (code.toUpperCase()) {
            case "US_GOV", "US_GOVERNMENT_BOND" -> new UnitedStates(UnitedStates.Market.GovernmentBond);
            case "US_SETTLEMENT" -> new UnitedStates(UnitedStates.Market.Settlement);
            case "TARGET" -> new Target();
            case "UK", "UNITED_KINGDOM" -> new UnitedKingdom(UnitedKingdom.Market.Settlement);
            case "NULL" -> new NullCalendar();
            default -> throw new IllegalArgumentException("Unsupported calendar: " + code);
        };
    }

    public static Frequency toFrequency(String code) {
        if (code == null || code.isBlank()) {
            return Frequency.SEMIANNUAL;
        }
        return switch (code.toUpperCase()) {
            case "ANNUAL" -> Frequency.ANNUAL;
            case "SEMIANNUAL" -> Frequency.SEMIANNUAL;
            case "QUARTERLY" -> Frequency.QUARTERLY;
            case "MONTHLY" -> Frequency.MONTHLY;
            default -> throw new IllegalArgumentException("Unsupported frequency: " + code);
        };
    }

    public static Compounding toCompounding(String code) {
        if (code == null || code.isBlank()) {
            return Compounding.Compounded;
        }
        return switch (code.toUpperCase()) {
            case "SIMPLE" -> Compounding.Simple;
            case "COMPOUNDED" -> Compounding.Compounded;
            case "CONTINUOUS" -> Compounding.Continuous;
            default -> throw new IllegalArgumentException("Unsupported compounding: " + code);
        };
    }

    public static BusinessDayConvention toBusinessDayConvention(String code) {
        if (code == null || code.isBlank()) {
            return BusinessDayConvention.MODIFIED_FOLLOWING;
        }
        return switch (code.toUpperCase()) {
            case "FOLLOWING" -> BusinessDayConvention.FOLLOWING;
            case "MODIFIED_FOLLOWING" -> BusinessDayConvention.MODIFIED_FOLLOWING;
            case "PRECEDING" -> BusinessDayConvention.PRECEDING;
            case "UNADJUSTED" -> BusinessDayConvention.UNADJUSTED;
            default -> throw new IllegalArgumentException("Unsupported businessDayConvention: " + code);
        };
    }
}
