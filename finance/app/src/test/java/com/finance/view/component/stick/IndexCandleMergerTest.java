package com.finance.view.component.stick;

import com.finance.model.net.YahooIndexQuote;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class IndexCandleMergerTest {
    private final YahooIndexQuote quote = new YahooIndexQuote(
            "20261106", 120, 130, 115, 125, 1000, 900, 2000);

    @Test
    public void updatesDailyCandleWithoutDuplicatingIt() {
        List<Object[]> records = new ArrayList<>();
        records.add(new Object[] {"20261106", 1f, 1f, 1f, 1f});
        assertTrue(IndexCandleMerger.apply(records, records, quote, "20261106", date -> date));
        assertArrayEquals(new Object[] {"20261106", 120f, 130f, 115f, 125f}, records.get(0));
    }

    @Test
    public void mergesDailyHistoryIntoCurrentMonthWithoutDuplicatingSameDay() {
        List<Object[]> daily = Arrays.asList(
                new Object[] {"20261106", 999f, 999f, 1f, 999f},
                new Object[] {"20261105", 110f, 135f, 105f, 120f},
                new Object[] {"20261102", 100f, 115f, 95f, 110f},
                new Object[] {"20261030", 80f, 200f, 50f, 100f});
        List<Object[]> monthly = new ArrayList<>();
        monthly.add(new Object[] {"20261130", 0f, 0f, 0f, 0f});
        assertTrue(IndexCandleMerger.apply(monthly, daily, quote, "20261130",
                date -> date.substring(0, 6)));
        assertArrayEquals(new Object[] {"20261130", 100f, 135f, 95f, 125f}, monthly.get(0));
    }

    @Test
    public void doesNotOverwriteNewerHistoricalData() {
        List<Object[]> records = new ArrayList<>();
        records.add(new Object[] {"20261130", 100f, 140f, 90f, 130f});
        List<Object[]> daily = new ArrayList<>();
        daily.add(new Object[] {"20261109", 130f, 140f, 120f, 135f});
        assertFalse(IndexCandleMerger.apply(records, daily, quote, "20261130",
                date -> date.substring(0, 6)));
        assertArrayEquals(new Object[] {"20261130", 100f, 140f, 90f, 130f}, records.get(0));
    }

    @Test
    public void insertsNewPeriodAheadOfPreviousPeriod() {
        List<Object[]> records = new ArrayList<>();
        records.add(new Object[] {"20261030", 80f, 100f, 70f, 90f});
        assertTrue(IndexCandleMerger.apply(records, new ArrayList<>(), quote, "20261130",
                date -> date.substring(0, 6)));
        assertArrayEquals(new Object[] {"20261130", 120f, 130f, 115f, 125f}, records.get(0));
    }
}
