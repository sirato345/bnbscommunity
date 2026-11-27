package com.finance.view.component;

import android.graphics.Color;

import org.junit.Test;

import com.finance.model.net.YahooIndexQuote;

import java.time.Instant;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ExtendedQuoteTimeTest {
    @Test
    public void formatsOtcUpdateTimeInJapanWithSeconds() {
        assertEquals("09:59:27", MacdView.formatOtcUpdateTime(
                Instant.parse("2026-11-26T00:59:27Z").getEpochSecond()));
        assertEquals("00:05:07", MacdView.formatOtcUpdateTime(
                Instant.parse("2025-12-31T15:05:07Z").getEpochSecond()));
    }

    @Test
    public void colorsPriceOnlyWhenItDiffersFromAValidClose() {
        assertEquals(Color.GREEN, MacdView.extendedPriceColor(101f, 100f));
        assertEquals(Color.RED, MacdView.extendedPriceColor(99f, 100f));
        assertEquals(Color.LTGRAY, MacdView.extendedPriceColor(100f, 100f));
        assertEquals(Color.LTGRAY, MacdView.extendedPriceColor(100f, null));
        assertEquals(Color.LTGRAY, MacdView.extendedPriceColor(Float.NaN, 100f));
    }

    @Test
    public void showsOtcOutsideRegularMarketHoursOnly() {
        YahooIndexQuote market = new YahooIndexQuote("20261127", 100, 105, 98, 102,
                1200, 1000, 1300, 700, 1000, 1300, 1600);
        assertTrue(MacdView.showOtcQuote(market, 699));
        assertTrue(MacdView.showOtcQuote(market, 700));
        assertTrue(MacdView.showOtcQuote(market, 999));
        assertFalse(MacdView.showOtcQuote(market, 1000));
        assertFalse(MacdView.showOtcQuote(market, 1299));
        assertTrue(MacdView.showOtcQuote(market, 1300));
        assertTrue(MacdView.showOtcQuote(market, 1600));
        assertTrue(MacdView.showOtcQuote(null, 1000));
    }
}
