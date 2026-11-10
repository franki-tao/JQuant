package jquant.time.calendars;

import jquant.time.Calendar;
import jquant.time.calendars.impl.JointCalendarImpl;

import java.util.List;

/**
 * Joint calendar. {@code JoinHolidays} is a holiday when any member is a
 * holiday. {@code JoinBusinessDays} is a business day when any member is.
 *
 * <p>https://github.com/lballabio/QuantLib/blob/v1.36/ql/time/calendars/jointcalendar.hpp
 */
public class JointCalendar extends Calendar {
    public enum Rule { JoinHolidays, JoinBusinessDays }

    public JointCalendar(Calendar c1, Calendar c2) {
        this(c1, c2, Rule.JoinHolidays);
    }

    public JointCalendar(Calendar c1, Calendar c2, Rule rule) {
        impl_ = new JointCalendarImpl(List.of(c1, c2), rule);
    }

    public JointCalendar(Calendar c1, Calendar c2, Calendar c3, Rule rule) {
        impl_ = new JointCalendarImpl(List.of(c1, c2, c3), rule);
    }
}
