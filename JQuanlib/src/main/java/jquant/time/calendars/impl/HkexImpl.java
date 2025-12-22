package jquant.time.calendars.impl;

import jquant.time.Date;
import jquant.time.Month;
import jquant.time.Weekday;
import jquant.time.impl.WesternImpl;

public class HkexImpl extends WesternImpl {
    @Override
    public String name() {
        return "Hong Kong stock exchange";
    }

    @Override
    public boolean isBusinessDay(Date date) {
        Weekday w = date.weekday();
        int d = date.dayOfMonth(), dd = date.dayOfYear();
        Month m = date.month();
        int y = date.year();
        int em = easterMonday(y);

        if (isWeekend(w)
                // New Year's Day
                || ((d == 1 || ((d == 2) && w == Weekday.MONDAY))
                && m == Month.JANUARY)
                // Good Friday
                || (dd == em - 3)
                // Easter MONDAY
                || (dd == em)
                // Labor Day
                || ((d == 1 || ((d == 2) && w == Weekday.MONDAY)) && m == Month.MAY)
                // SAR Establishment Day
                || ((d == 1 || ((d == 2) && w == Weekday.MONDAY)) && m == Month.JULY)
                // National Day
                || ((d == 1 || ((d == 2) && w == Weekday.MONDAY))
                && m == Month.OCTOBER)
                // Christmas Day
                || (d == 25 && m == Month.DECEMBER)
                // Boxing Day
                || (d == 26 && m == Month.DECEMBER)
        )
            return false;

        if (y == 2004) {
            if (// Lunar New Year
                    ((d == 22 || d == 23 || d == 24) && m == Month.JANUARY)
                            // Ching Ming Festival
                            || (d == 5 && m == Month.APRIL)
                            // Buddha's birthday
                            || (d == 26 && m == Month.MAY)
                            // Tuen Ng festival
                            || (d == 22 && m == Month.JUNE)
                            // Mid-autumn festival
                            || (d == 29 && m == Month.SEPTEMBER)
                            // Chung Yeung
                            || (d == 22 && m == Month.OCTOBER))
                return false;
        }

        if (y == 2005) {
            if (// Lunar New Year
                    ((d == 9 || d == 10 || d == 11) && m == Month.FEBRUARY)
                            // Ching Ming Festival
                            || (d == 5 && m == Month.APRIL)
                            // Buddha's birthday
                            || (d == 16 && m == Month.MAY)
                            // Tuen Ng festival
                            || (d == 11 && m == Month.JUNE)
                            // Mid-autumn festival
                            || (d == 19 && m == Month.SEPTEMBER)
                            // Chung Yeung festival
                            || (d == 11 && m == Month.OCTOBER))
                return false;
        }

        if (y == 2006) {
            if (// Lunar New Year
                    ((d >= 28 && d <= 31) && m == Month.JANUARY)
                            // Ching Ming Festival
                            || (d == 5 && m == Month.APRIL)
                            // Buddha's birthday
                            || (d == 5 && m == Month.MAY)
                            // Tuen Ng festival
                            || (d == 31 && m == Month.MAY)
                            // Mid-autumn festival
                            || (d == 7 && m == Month.OCTOBER)
                            // Chung Yeung festival
                            || (d == 30 && m == Month.OCTOBER))
                return false;
        }

        if (y == 2007) {
            if (// Lunar New Year
                    ((d >= 17 && d <= 20) && m == Month.FEBRUARY)
                            // Ching Ming Festival
                            || (d == 5 && m == Month.APRIL)
                            // Buddha's birthday
                            || (d == 24 && m == Month.MAY)
                            // Tuen Ng festival
                            || (d == 19 && m == Month.JUNE)
                            // Mid-autumn festival
                            || (d == 26 && m == Month.SEPTEMBER)
                            // Chung Yeung festival
                            || (d == 19 && m == Month.OCTOBER))
                return false;
        }

        if (y == 2008) {
            if (// Lunar New Year
                    ((d >= 7 && d <= 9) && m == Month.FEBRUARY)
                            // Ching Ming Festival
                            || (d == 4 && m == Month.APRIL)
                            // Buddha's birthday
                            || (d == 12 && m == Month.MAY)
                            // Tuen Ng festival
                            || (d == 9 && m == Month.JUNE)
                            // Mid-autumn festival
                            || (d == 15 && m == Month.SEPTEMBER)
                            // Chung Yeung festival
                            || (d == 7 && m == Month.OCTOBER))
                return false;
        }

        if (y == 2009) {
            if (// Lunar New Year
                    ((d >= 26 && d <= 28) && m == Month.JANUARY)
                            // Ching Ming Festival
                            || (d == 4 && m == Month.APRIL)
                            // Buddha's birthday
                            || (d == 2 && m == Month.MAY)
                            // Tuen Ng festival
                            || (d == 28 && m == Month.MAY)
                            // Mid-autumn festival
                            || (d == 3 && m == Month.OCTOBER)
                            // Chung Yeung festival
                            || (d == 26 && m == Month.OCTOBER))
                return false;
        }

        if (y == 2010) {
            if (// Lunar New Year
                    ((d == 15 || d == 16) && m == Month.FEBRUARY)
                            // Ching Ming Festival
                            || (d == 6 && m == Month.APRIL)
                            // Buddha's birthday
                            || (d == 21 && m == Month.MAY)
                            // Tuen Ng festival
                            || (d == 16 && m == Month.JUNE)
                            // Mid-autumn festival
                            || (d == 23 && m == Month.SEPTEMBER))
                return false;
        }

        if (y == 2011) {
            if (// Lunar New Year
                    ((d == 3 || d == 4) && m == Month.FEBRUARY)
                            // Ching Ming Festival
                            || (d == 5 && m == Month.APRIL)
                            // Buddha's birthday
                            || (d == 10 && m == Month.MAY)
                            // Tuen Ng festival
                            || (d == 6 && m == Month.JUNE)
                            // Mid-autumn festival
                            || (d == 13 && m == Month.SEPTEMBER)
                            // Chung Yeung festival
                            || (d == 5 && m == Month.OCTOBER)
                            // Second day after Christmas
                            || (d == 27 && m == Month.DECEMBER))
                return false;
        }

        if (y == 2012) {
            if (// Lunar New Year
                    (d >= 23 && d <= 25 && m == Month.JANUARY)
                            // Ching Ming Festival
                            || (d == 4 && m == Month.APRIL)
                            // Buddha's birthday
                            || (d == 10 && m == Month.MAY)
                            // Mid-autumn festival
                            || (d == 1 && m == Month.OCTOBER)
                            // Chung Yeung festival
                            || (d == 23 && m == Month.OCTOBER))
                return false;
        }

        if (y == 2013) {
            if (// Lunar New Year
                    (d >= 11 && d <= 13 && m == Month.FEBRUARY)
                            // Ching Ming Festival
                            || (d == 4 && m == Month.APRIL)
                            // Buddha's birthday
                            || (d == 17 && m == Month.MAY)
                            // Tuen Ng festival
                            || (d == 12 && m == Month.JUNE)
                            // Mid-autumn festival
                            || (d == 20 && m == Month.SEPTEMBER)
                            // Chung Yeung festival
                            || (d == 14 && m == Month.OCTOBER))
                return false;
        }

        if (y == 2014) {
            if (// Lunar New Year
                    ((d == 31 && m == Month.JANUARY) || (d <= 3 && m == Month.FEBRUARY))
                            // Buddha's birthday
                            || (d == 6 && m == Month.MAY)
                            // Tuen Ng festival
                            || (d == 2 && m == Month.JUNE)
                            // Mid-autumn festival
                            || (d == 9 && m == Month.SEPTEMBER)
                            // Chung Yeung festival
                            || (d == 2 && m == Month.OCTOBER))
                return false;
        }

        if (y == 2015) {
            if (// Lunar New Year
                    ((d == 19 && m == Month.FEBRUARY) || (d == 20 && m == Month.FEBRUARY))
                            // The day following Easter Weekday.MONDAY
                            || (d == 7 && m == Month.APRIL)
                            // Buddha's birthday
                            || (d == 25 && m == Month.MAY)
                            // Tuen Ng festival
                            || (d == 20 && m == Month.JUNE)
                            // The 70th anniversary day of the victory of the Chinese 
                            // people's war of resistance against Japanese aggression
                            || (d == 3 && m == Month.SEPTEMBER)
                            // Mid-autumn festival
                            || (d == 28 && m == Month.SEPTEMBER)
                            // Chung Yeung festival
                            || (d == 21 && m == Month.OCTOBER))
                return false;
        }

        if (y == 2016) {
            if (// Lunar New Year
                    ((d >= 8 && d <= 10) && m == Month.FEBRUARY)
                            // Ching Ming Festival
                            || (d == 4 && m == Month.APRIL)
                            // Tuen Ng festival
                            || (d == 9 && m == Month.JUNE)
                            // Mid-autumn festival
                            || (d == 16 && m == Month.SEPTEMBER)
                            // Chung Yeung festival
                            || (d == 10 && m == Month.OCTOBER)
                            // Second day after Christmas
                            || (d == 27 && m == Month.DECEMBER))
                return false;
        }

        if (y == 2017) {
            if (// Lunar New Year
                    ((d == 30 || d == 31) && m == Month.JANUARY)
                            // Ching Ming Festival
                            || (d == 4 && m == Month.APRIL)
                            // Buddha's birthday
                            || (d == 3 && m == Month.MAY)
                            // Tuen Ng festival
                            || (d == 30 && m == Month.MAY)
                            // Mid-autumn festival
                            || (d == 5 && m == Month.OCTOBER))
                return false;
        }

        if (y == 2018) {
            if (// Lunar New Year
                    ((d == 16 && m == Month.FEBRUARY) || (d == 19 && m == Month.FEBRUARY))
                            // Ching Ming Festival
                            || (d == 5 && m == Month.APRIL)
                            // Buddha's birthday
                            || (d == 22 && m == Month.MAY)
                            // Tuen Ng festival
                            || (d == 18 && m == Month.JUNE)
                            // Mid-autumn festival
                            || (d == 25 && m == Month.SEPTEMBER)
                            // Chung Yeung festival
                            || (d == 17 && m == Month.OCTOBER))
                return false;
        }

        if (y == 2019) {
            if (// Lunar New Year
                    ((d >= 5 && d <= 7) && m == Month.FEBRUARY)
                            // Ching Ming Festival
                            || (d == 5 && m == Month.APRIL)
                            // Tuen Ng festival
                            || (d == 7 && m == Month.JUNE)
                            // Chung Yeung festival
                            || (d == 7 && m == Month.OCTOBER))
                return false;
        }

        if (y == 2020) {
            if (// Lunar New Year
                    ((d == 27 || d == 28) && m == Month.JANUARY)
                            // Ching Ming Festival
                            || (d == 4 && m == Month.APRIL)
                            // Buddha's birthday
                            || (d == 30 && m == Month.APRIL)
                            // Tuen Ng festival
                            || (d == 25 && m == Month.JUNE)
                            // Mid-autumn festival
                            || (d == 2 && m == Month.OCTOBER)
                            // Chung Yeung festival
                            || (d == 26 && m == Month.OCTOBER))
                return false;
        }

        // data from https://www.hkex.com.hk/-/media/hkex-market/services/circulars-and-notices/participant-and-members-circulars/sehk/2020/ce_sehk_ct_038_2020.pdf
        if (y == 2021) {
            if (// Lunar New Year
                    ((d == 12 || d == 15) && m == Month.FEBRUARY)
                            // Ching Ming Festival
                            || (d == 5 && m == Month.APRIL)
                            // Buddha's birthday
                            || (d == 19 && m == Month.MAY)
                            // Tuen Ng festival
                            || (d == 14 && m == Month.JUNE)
                            // Mid-autumn festival
                            || (d == 22 && m == Month.SEPTEMBER)
                            // Chung Yeung festival
                            || (d == 14 && m == Month.OCTOBER))
                return false;
        }

        // data from https://www.hkex.com.hk/-/media/HKEX-Market/Services/Circulars-and-Notices/Participant-and-Members-Circulars/SEHK/2021/ce_SEHK_CT_082_2021.pdf
        if (y == 2022) {
            if (// Lunar New Year
                    ((d >= 1 && d <= 3) && m == Month.FEBRUARY)
                            // Ching Ming Festival
                            || (d == 5 && m == Month.APRIL)
                            // Buddha's birthday
                            || (d == 9 && m == Month.MAY)
                            // Tuen Ng festival
                            || (d == 3 && m == Month.JUNE)
                            // Mid-autumn festival
                            || (d == 12 && m == Month.SEPTEMBER)
                            // Chung Yeung festival
                            || (d == 4 && m == Month.OCTOBER))
                return false;
        }

        // data from https://www.hkex.com.hk/-/media/HKEX-Market/Services/Circulars-and-Notices/Participant-and-Members-Circulars/SEHK/2022/ce_SEHK_CT_058_2022.pdf
        if (y == 2023) {
            if (// Lunar New Year
                    ((d >= 23 && d <= 25) && m == Month.JANUARY)
                            // Ching Ming Festival
                            || (d == 5 && m == Month.APRIL)
                            // Buddha's birthday
                            || (d == 26 && m == Month.MAY)
                            // Tuen Ng festival
                            || (d == 22 && m == Month.JUNE)
                            // Chung Yeung festival
                            || (d == 23 && m == Month.OCTOBER))
                return false;
        }

        // data from https://www.hkex.com.hk/-/media/HKEX-Market/Services/Circulars-and-Notices/Participant-and-Members-Circulars/SEHK/2023/ce_SEHK_CT_079_2023.pdf
        if (y == 2024) {
            if (// Lunar New Year
                    ((d == 12 || d == 13) && m == Month.FEBRUARY)
                            // Ching Ming Festival
                            || (d == 4 && m == Month.APRIL)
                            // Buddha's birthday
                            || (d == 15 && m == Month.MAY)
                            // Tuen Ng festival
                            || (d == 10 && m == Month.JUNE)
                            // Mid-autumn festival
                            || (d == 18 && m == Month.SEPTEMBER)
                            // Chung Yeung festival
                            || (d == 11 && m == Month.OCTOBER))
                return false;
        }

        // data from https://www.hkex.com.hk/-/media/HKEX-Market/Services/Circulars-and-Notices/Participant-and-Members-Circulars/SEHK/2024/ce_SEHK_CT_063_2024.pdf
        if (y == 2025) {
            if (// Lunar New Year
                    ((d >= 29 && d <= 31) && m == Month.JANUARY)
                            // Ching Ming Festival
                            || (d == 4 && m == Month.APRIL)
                            // Buddha's birthday
                            || (d == 5 && m == Month.MAY)
                            // Mid-autumn festival
                            || (d == 7 && m == Month.OCTOBER)
                            // Chung Yeung festival
                            || (d == 29 && m == Month.OCTOBER))
                return false;
        }

        return true;
    }
}
