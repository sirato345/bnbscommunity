package com.finance.view.component.macd;

import org.junit.Test;

import java.util.Arrays;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class FearGreedAlignmentTest {
    @Test
    public void dailyValuesAlignByDateAndLeaveMissingCandlesEmpty() {
        Map<String, Float> values = FearGreedAlignment.latestByPeriod(Arrays.asList(
                "2026-11-18,42,42,42,42", "2026-11-16,25,25,25,25",
                "2026-11-18,12,12,12,12"), "d");
        assertEquals(42f, values.get(FearGreedAlignment.periodKey("20261118", "d")), 0f);
        assertEquals(25f, values.get(FearGreedAlignment.periodKey("20261116", "d")), 0f);
        assertNull(values.get(FearGreedAlignment.periodKey("20261117", "d")));
    }

    @Test
    public void aggregatesUsingLatestAvailableDayInEachCandlePeriod() {
        for (String timeframe : new String[] {"3d", "w", "m", "q", "y"}) {
            Map<String, Float> values = FearGreedAlignment.latestByPeriod(Arrays.asList(
                    "2026-11-17,80,80,80,80", "2026-11-16,20,20,20,20"), timeframe);
            String candleDate = "3d".equals(timeframe) ? "20261115"
                    : "w".equals(timeframe) ? "20261115"
                    : "m".equals(timeframe) ? "20261130" : "20261231";
            assertEquals(timeframe, 80f,
                    values.get(FearGreedAlignment.periodKey(candleDate, timeframe)), 0f);
        }
    }

    @Test
    public void respectsSundayWeeklyAndEpochThreeDayBoundaries() {
        assertEquals(FearGreedAlignment.periodKey("20261115", "w"),
                FearGreedAlignment.periodKey("20261121", "w"));
        assertFalse(FearGreedAlignment.periodKey("20261114", "w")
                .equals(FearGreedAlignment.periodKey("20261115", "w")));
        assertEquals(FearGreedAlignment.periodKey("19700101", "3d"),
                FearGreedAlignment.periodKey("19700103", "3d"));
        assertFalse(FearGreedAlignment.periodKey("19700103", "3d")
                .equals(FearGreedAlignment.periodKey("19700104", "3d")));
        assertTrue(FearGreedAlignment.supports("d"));
        assertTrue(FearGreedAlignment.supports("y"));
        assertFalse(FearGreedAlignment.supports("4h"));
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsMalformedRecords() {
        FearGreedAlignment.latestByPeriod(Arrays.asList("not-a-date,1,1,1,1"), "d");
    }
}
