package jquant.time.impl;

import jquant.time.*;

import java.util.Map;

import static jquant.time.TimeUtils.sameMonth;
import static jquant.time.TimeUtils.sameYear;

public class Business252Impl extends DayCounterImpl {
    private Calendar calendar_;

    public Business252Impl(Calendar calendar) {
        calendar_ = calendar;
    }

    @Override
    public String name() {
        return "Business/252(" + calendar_.name() + ")";
    }

    @Override
    public int dayCount(final Date d1, final Date d2) {
        if (sameMonth(d1, d2) || TimeUtils.geq(d1, d2)) {
            // we treat the case of d1 > d2 here, since we'd need a
            // second cache to get it right (our cached figures are
            // for first included, last excluded and might have to be
            // changed going the other way.)
            return calendar_.businessDaysBetween(d1, d2, true, false);
        } else if (sameYear(d1, d2)) {
            Map<Integer, Map<Month, Integer>> cache = TimeUtils.monthlyFigures_.get(calendar_.name());
            int total = 0;
            Date d;
            // first, we get to the beginning of next month.
            d = new Date(1, d1.month(), d1.year()).add(TimeUtils.multiply(1, TimeUnit.MONTHS));
            total += calendar_.businessDaysBetween(d1, d, true, false);
            // then, we add any whole months (whose figures might be
            // cached already) in the middle of our period.
            while (!sameMonth(d, d2)) {
                total += businessDays(cache, calendar_,
                        d.month(), d.year());
                d.addEquals(TimeUtils.multiply(1, TimeUnit.MONTHS));
            }
            // finally, we get to the end of the period.
            total += calendar_.businessDaysBetween(d, d2);
            return total;
        } else {
            Map<Integer, Map<Month, Integer>> cache = TimeUtils.monthlyFigures_.get(calendar_.name());
            Map<Integer, Integer> outerCache = TimeUtils.yearlyFigures_.get(calendar_.name());
            int total = 0;
            Date d;
            // first, we get to the beginning of next year.
            // The first bit gets us to the end of this month...
            d = new Date(1, d1.month(), d1.year()).add(TimeUtils.multiply(1, TimeUnit.MONTHS));
            total += calendar_.businessDaysBetween(d1, d);
            // ...then we add any remaining months, possibly cached
            for (int m = (d1.month().getValue()) + 1; m <= 12; ++m) {
                total += businessDays(cache, calendar_,
                        Month.fromValue(m), d.year());
            }
            // then, we add any whole year in the middle of our period.
            d = new Date(1, Month.JANUARY, d1.year() + 1);
            while (!sameYear(d, d2)) {
                total += businessDays(outerCache, cache,
                        calendar_, d.year());
                d.addEquals(TimeUtils.multiply(1, TimeUnit.YEARS));
            }
            // finally, we get to the end of the period.
            // First, we add whole months...
            for (int m = 1; m < (d2.month().getValue()); ++m) {
                total += businessDays(cache, calendar_,
                        Month.fromValue(m), d2.year());
            }
            // ...then the last bit.
            d = new Date(1, d2.month(), d2.year());
            total += calendar_.businessDaysBetween(d, d2);
            return total;
        }
    }

    @Override
    public double yearFraction(Date d1, Date d2, Date refPeriodStart, Date refPeriodEnd) {
        return dayCount(d1, d2)/252.0;
    }

    private int businessDays(Map<Integer, Map<Month, Integer>> cache, final Calendar calendar, Month month, int year) {
        if (cache.get(year).get(month) == 0) {
            // calculate and store.
            Date d1 = new Date(1, month, year);
            Date d2 = d1.add(TimeUtils.multiply(1, TimeUnit.MONTHS));
            cache.get(year).put(month, calendar.businessDaysBetween(d1, d2));
        }
        return cache.get(year).get(month);
    }

    private int businessDays(Map<Integer, Integer> outerCache,
                             Map<Integer, Map<Month, Integer>> cache,
                             final Calendar calendar, int year) {
        if (outerCache.get(year) == 0) {
            // calculate and store.
            int total = 0;
            for (int i = 1; i <= 12; ++i) {
                total += businessDays(cache, calendar,
                        Month.fromValue(i), year);
            }
            outerCache.put(year, total);
        }
        return outerCache.get(year);
    }
}
