package com.finance.view.component.stick;

import org.junit.Test;

import java.util.Arrays;

import static org.junit.Assert.assertEquals;

public class CandleDateIndexTest {
    @Test
    public void preservesPositionsAcrossWeekendAndWindowChanges() {
        CandleDateIndex index = new CandleDateIndex(Arrays.asList(
                "20261109", "20261106", "20261105", "20261104", "20261103"));
        assertEquals(Integer.valueOf(320), index.xForDate("20261109", "20261106", 300, 20));
        assertEquals(Integer.valueOf(280), index.xForDate("20261105", "20261106", 300, 20));
        assertEquals(Integer.valueOf(300), index.xForDate("20261109", "20261105", 260, 20));
        assertEquals("20261109", index.dateForX(320, "20261106", 300, 20));
    }

    @Test
    public void monthlyCandlesRemainEquallySpaced() {
        CandleDateIndex index = new CandleDateIndex(Arrays.asList(
                "20260301", "20260201", "20260101", "20251201"));
        assertEquals(Integer.valueOf(240), index.xForDate("20260301", "20260101", 200, 20));
        assertEquals("20260301", index.dateForX(240, "20260101", 200, 20));
        assertEquals(Integer.valueOf(260), index.xForDate("20260401", "20260101", 200, 20));
        assertEquals("20260401", index.dateForX(260, "20260101", 200, 20));
    }

    @Test
    public void intradayDatesStayAlignedWhenNewestCandleIsOffScreen() {
        CandleDateIndex index = new CandleDateIndex(Arrays.asList(
                "2026-11-06 12:00", "2026-11-06 08:00", "2026-11-06 04:00",
                "2026-11-06 00:00", "2026-11-05 20:00"));
        assertEquals(Integer.valueOf(120),
                index.xForDate("2026-11-06 12:00", "2026-11-06 00:00", 60, 20));
        assertEquals(Integer.valueOf(40),
                index.xForDate("2026-11-05 20:00", "2026-11-06 00:00", 60, 20));
        assertEquals("2026-11-06 08:00",
                index.dateForX(100, "2026-11-06 00:00", 60, 20));
    }

    @Test
    public void futureWavePointsMoveTogetherWhenVisibleCandlesChange() {
        CandleDateIndex index = new CandleDateIndex(Arrays.asList(
                "20261109", "20261106", "20261105", "20261104", "20261103"));
        String[] points = {"20261106", "20261109", "20261112", "20261115"};
        int[] first = new int[points.length];
        for (int i = 0; i < points.length; i++) {
            first[i] = index.xForDate(points[i], "20261109", 200, 20);
            int after = index.xForDate(points[i], "20261105", 140, 20);
            assertEquals(first[i] - 20, after);
        }
        assertEquals("20261112", index.dateForX(220, "20261109", 200, 20));
        assertEquals("20261112", index.dateForX(200, "20261105", 140, 20));
    }

    @Test
    public void futureMonthlyPointsKeepCalendarSpacing() {
        CandleDateIndex index = new CandleDateIndex(Arrays.asList(
                "20260301", "20260201", "20260101", "20251201"));
        assertEquals("20260501", index.dateForX(240, "20260301", 200, 20));
        assertEquals(Integer.valueOf(240), index.xForDate("20260501", "20260301", 200, 20));
        assertEquals(Integer.valueOf(220), index.xForDate("20260501", "20260101", 140, 20));
    }
}
