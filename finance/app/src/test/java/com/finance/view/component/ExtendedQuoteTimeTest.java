package com.finance.view.component;

import org.junit.Test;

import com.finance.model.net.ExtendedHoursQuote;
import com.finance.model.net.PremarketQuote;
import com.finance.model.net.YahooIndexQuote;

import java.time.Instant;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ExtendedQuoteTimeTest {
    @Test
    public void formatsOnlyJapaneseHoursAndMinutesAcrossDates() {
        assertEquals("09:59", MacdView.formatExtendedQuoteTime(
                Instant.parse("2026-11-26T00:59:27Z").getEpochSecond()));
        assertEquals("00:05", MacdView.formatExtendedQuoteTime(
                Instant.parse("2025-12-31T15:05:00Z").getEpochSecond()));
    }

    @Test
    public void selectsTheMostRecentAvailableExtendedHoursQuote() {
        PremarketQuote pre = new PremarketQuote(10, 800, 700, 1000);
        ExtendedHoursQuote post = new ExtendedHoursQuote(11, 1400, 1300, 1600);
        assertTrue(MacdView.showPostmarketQuote(pre, post, null, 1500));
        PremarketQuote nextPre = new PremarketQuote(12, 1800, 1700, 2000);
        assertFalse(MacdView.showPostmarketQuote(nextPre, post, null, 1900));
        assertTrue(MacdView.showPostmarketQuote(nextPre, post, null, 1650));
        assertFalse(MacdView.showPostmarketQuote(pre, null, null, 1500));
        assertTrue(MacdView.showPostmarketQuote(null, post, null, 1500));
    }

    @Test
    public void usesActiveSessionWhenNoQuoteIsAvailable() {
        YahooIndexQuote market = new YahooIndexQuote("20261127", 100, 105, 98, 102,
                1200, 1000, 1300, 700, 1000, 1300, 1600);
        assertTrue(MacdView.showPostmarketQuote(null, null, market, 1400));
        assertFalse(MacdView.showPostmarketQuote(null, null, market, 800));
        assertFalse(MacdView.showPostmarketQuote(null, null, null, 1400));
    }
}
