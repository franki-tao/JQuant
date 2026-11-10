package jquant.time.calendars.impl;

import jquant.time.Calendar;
import jquant.time.Date;
import jquant.time.Weekday;
import jquant.time.calendars.JointCalendar;
import jquant.time.impl.CalendarImpl;

import java.util.List;

import static jquant.math.CommonUtil.QL_FAIL;

/**
 * <p>https://github.com/lballabio/QuantLib/blob/v1.36/ql/time/calendars/jointcalendar.cpp
 */
public class JointCalendarImpl extends CalendarImpl {
    private final JointCalendar.Rule rule_;
    private final List<Calendar> calendars_;

    public JointCalendarImpl(List<Calendar> calendars, JointCalendar.Rule rule) {
        calendars_ = List.copyOf(calendars);
        rule_ = rule;
    }

    @Override
    public String name() {
        StringBuilder out = new StringBuilder();
        switch (rule_) {
            case JoinHolidays:
                out.append("JoinHolidays(");
                break;
            case JoinBusinessDays:
                out.append("JoinBusinessDays(");
                break;
            default:
                QL_FAIL("unknown joint calendar rule");
        }
        out.append(calendars_.get(0).name());
        for (int i = 1; i < calendars_.size(); i++)
            out.append(", ").append(calendars_.get(i).name());
        out.append(")");
        return out.toString();
    }

    @Override
    public boolean isWeekend(Weekday w) {
        switch (rule_) {
            case JoinHolidays:
                for (Calendar calendar : calendars_) {
                    if (calendar.isWeekend(w))
                        return true;
                }
                return false;
            case JoinBusinessDays:
                for (Calendar calendar : calendars_) {
                    if (!calendar.isWeekend(w))
                        return false;
                }
                return true;
            default:
                QL_FAIL("unknown joint calendar rule");
        }
        return false;
    }

    @Override
    public boolean isBusinessDay(Date date) {
        switch (rule_) {
            case JoinHolidays:
                for (Calendar calendar : calendars_) {
                    if (calendar.isHoliday(date))
                        return false;
                }
                return true;
            case JoinBusinessDays:
                for (Calendar calendar : calendars_) {
                    if (calendar.isBusinessDay(date))
                        return true;
                }
                return false;
            default:
                QL_FAIL("unknown joint calendar rule");
        }
        return false;
    }
}
