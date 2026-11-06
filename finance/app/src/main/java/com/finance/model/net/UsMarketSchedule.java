package com.finance.model.net;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.TemporalAdjusters;

public final class UsMarketSchedule {
    private static final ZoneId MARKET_ZONE = ZoneId.of("America/New_York");
    private static final LocalTime OPEN = LocalTime.of(9, 30);

    private UsMarketSchedule() {
    }

    public static long nextOpen(long nowSeconds) {
        ZonedDateTime now = Instant.ofEpochSecond(nowSeconds).atZone(MARKET_ZONE);
        LocalDate date = now.toLocalDate();
        ZonedDateTime candidate = date.atTime(OPEN).atZone(MARKET_ZONE);
        if (!candidate.isAfter(now)) {
            date = date.plusDays(1);
        }
        while (!isTradingDay(date)) {
            date = date.plusDays(1);
        }
        return date.atTime(OPEN).atZone(MARKET_ZONE).toEpochSecond();
    }

    private static boolean isTradingDay(LocalDate date) {
        DayOfWeek weekday = date.getDayOfWeek();
        if (weekday == DayOfWeek.SATURDAY || weekday == DayOfWeek.SUNDAY) {
            return false;
        }
        int year = date.getYear();
        LocalDate newYear = LocalDate.of(year, 1, 1);
        LocalDate observedNewYear = newYear.getDayOfWeek() == DayOfWeek.SUNDAY
                ? newYear.plusDays(1) : newYear;
        return !date.equals(observedNewYear)
                && !date.equals(nthWeekday(year, 1, DayOfWeek.MONDAY, 3))
                && !date.equals(nthWeekday(year, 2, DayOfWeek.MONDAY, 3))
                && !date.equals(easter(year).minusDays(2))
                && !date.equals(LocalDate.of(year, 5, 31)
                        .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)))
                && !(year >= 2022 && date.equals(observed(LocalDate.of(year, 6, 19))))
                && !date.equals(observed(LocalDate.of(year, 7, 4)))
                && !date.equals(nthWeekday(year, 9, DayOfWeek.MONDAY, 1))
                && !date.equals(nthWeekday(year, 11, DayOfWeek.THURSDAY, 4))
                && !date.equals(observed(LocalDate.of(year, 12, 25)));
    }

    private static LocalDate observed(LocalDate holiday) {
        if (holiday.getDayOfWeek() == DayOfWeek.SATURDAY) {
            return holiday.minusDays(1);
        }
        if (holiday.getDayOfWeek() == DayOfWeek.SUNDAY) {
            return holiday.plusDays(1);
        }
        return holiday;
    }

    private static LocalDate nthWeekday(int year, int month, DayOfWeek day, int ordinal) {
        return LocalDate.of(year, month, 1).with(TemporalAdjusters.dayOfWeekInMonth(ordinal, day));
    }

    private static LocalDate easter(int year) {
        int a = year % 19;
        int b = year / 100;
        int c = year % 100;
        int d = b / 4;
        int e = b % 4;
        int f = (b + 8) / 25;
        int g = (b - f + 1) / 3;
        int h = (19 * a + b - d - g + 15) % 30;
        int i = c / 4;
        int k = c % 4;
        int l = (32 + 2 * e + 2 * i - h - k) % 7;
        int m = (a + 11 * h + 22 * l) / 451;
        int month = (h + l - 7 * m + 114) / 31;
        int day = (h + l - 7 * m + 114) % 31 + 1;
        return LocalDate.of(year, month, day);
    }
}
