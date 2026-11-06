package com.finance.model.net;

import org.junit.Test;

import java.time.ZoneId;
import java.time.ZonedDateTime;

import static org.junit.Assert.assertEquals;

public class UsMarketScheduleTest {
    private static final ZoneId NY = ZoneId.of("America/New_York");

    private long time(int year, int month, int day, int hour, int minute) {
        return ZonedDateTime.of(year, month, day, hour, minute, 0, 0, NY).toEpochSecond();
    }

    @Test
    public void nextOpenHandlesWeekendsAndDaylightSaving() {
        assertEquals(time(2026, 11, 9, 9, 30),
                UsMarketSchedule.nextOpen(time(2026, 11, 6, 16, 0)));
        assertEquals(time(2026, 3, 9, 9, 30),
                UsMarketSchedule.nextOpen(time(2026, 3, 6, 16, 0)));
    }

    @Test
    public void skipsHolidaysAndObservedHolidays() {
        assertEquals(time(2026, 11, 27, 9, 30),
                UsMarketSchedule.nextOpen(time(2026, 11, 25, 16, 0)));
        assertEquals(time(2026, 4, 6, 9, 30),
                UsMarketSchedule.nextOpen(time(2026, 4, 2, 16, 0)));
        assertEquals(time(2026, 7, 6, 9, 30),
                UsMarketSchedule.nextOpen(time(2026, 7, 2, 16, 0)));
        assertEquals(time(2026, 6, 22, 9, 30),
                UsMarketSchedule.nextOpen(time(2026, 6, 18, 16, 0)));
    }

    @Test
    public void saturdayNewYearDoesNotCloseThePrecedingFriday() {
        assertEquals(time(2027, 12, 31, 9, 30),
                UsMarketSchedule.nextOpen(time(2027, 12, 30, 16, 0)));
    }

    @Test
    public void beforeOpenReturnsTodayAndAfterOpenReturnsNextSession() {
        assertEquals(time(2026, 11, 6, 9, 30),
                UsMarketSchedule.nextOpen(time(2026, 11, 6, 8, 0)));
        assertEquals(time(2026, 11, 9, 9, 30),
                UsMarketSchedule.nextOpen(time(2026, 11, 6, 9, 30)));
    }
}
