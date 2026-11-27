package com.finance.model.net;

import com.finance.common.Const;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class YahooIndexQuoteTest {
    @Test
    public void bmnrUsesUsMarketQuoteAndSessionPath() {
        assertEquals("BMNR", NetConnectorYahooIndex.ticker("bmnr"));
        assertTrue(Const.isUsIndexSymbol("bmnr"));
        assertTrue(Const.isUsIndexSymbol("dow"));
        assertTrue(Const.isUsIndexSymbol("sp500"));
        assertTrue(Const.isUsIndexSymbol("nasdaq"));
        assertFalse(Const.isUsIndexSymbol("usbond"));
        assertFalse(Const.isUsIndexSymbol("btc"));
    }

    @Test
    public void reportsMarketSessionAndStaleness() {
        YahooIndexQuote quote = new YahooIndexQuote("20261106", 100, 105, 98, 102,
                1050, 1000, 2000);
        assertEquals("Closed", quote.status(999));
        assertEquals("Open", quote.status(1170));
        assertEquals("Stale", quote.status(1171));
        assertEquals("Closed", quote.status(2000));
        assertEquals("Open", quote.status(1000));
        YahooIndexQuote futureQuote = new YahooIndexQuote("20261106", 100, 105, 98, 102,
                1100, 1000, 2000);
        assertEquals("Stale", futureQuote.status(1000));
    }

    @Test
    public void marketSessionIncludesOpenAndExcludesCloseEvenWithStaleQuote() {
        YahooIndexQuote quote = new YahooIndexQuote("20261127", 100, 105, 98, 102,
                1000, 1000, 1300);
        assertFalse(quote.isMarketOpen(999));
        assertTrue(quote.isMarketOpen(1000));
        assertTrue(quote.isMarketOpen(1299));
        assertEquals("Stale", quote.status(1299));
        assertFalse(quote.isMarketOpen(1300));
    }

    @Test
    public void premarketRequiresMatchingActiveSessionAndDetectsStalePrices() {
        YahooIndexQuote market = new YahooIndexQuote("20261127", 100, 105, 98, 102,
                500, 1000, 1300, 700, 1000);
        PremarketQuote quote = new PremarketQuote(101, 800, 700, 1000);
        assertFalse(market.isPremarket(699));
        assertTrue(market.isPremarket(700));
        assertTrue(quote.isCurrent(market, 920));
        assertTrue(quote.isAvailable(1000));
        assertFalse(quote.isAvailable(699));
        assertFalse(new PremarketQuote(101, 1000, 700, 1000).isAvailable(1100));
        assertFalse(new PremarketQuote(101, 900, 700, 1000).isAvailable(800));
        assertFalse(quote.isStale(920));
        assertTrue(quote.isStale(921));
        assertTrue(quote.isStale(739));
        assertFalse(quote.isCurrent(market, 1000));
        YahooIndexQuote nextDay = new YahooIndexQuote("20261130", 100, 105, 98, 102,
                1500, 2000, 2300, 1700, 2000);
        assertFalse(quote.isCurrent(nextDay, 1800));
        YahooIndexQuote invalid = new YahooIndexQuote("20261127", 100, 105, 98, 102,
                500, 1000, 1300, 700, 999);
        assertFalse(invalid.isPremarket(800));
    }

    @Test
    public void postmarketUsesItsOwnSessionAndKeepsTheLastValidQuote() {
        YahooIndexQuote market = new YahooIndexQuote("20261127", 100, 105, 98, 102,
                1200, 1000, 1300, 700, 1000, 1300, 1600);
        ExtendedHoursQuote quote = new ExtendedHoursQuote(103, 1400, 1300, 1600);
        assertFalse(market.isPostmarket(1299));
        assertTrue(market.isPostmarket(1300));
        assertTrue(market.isPostmarket(1599));
        assertFalse(market.isPostmarket(1600));
        assertTrue(quote.isAvailable(1700));
        assertFalse(quote.isAvailable(1299));
        assertFalse(new ExtendedHoursQuote(103, 1600, 1300, 1600).isAvailable(1700));
        assertFalse(quote.isStale(1520));
        assertTrue(quote.isStale(1521));
        YahooIndexQuote invalid = new YahooIndexQuote("20261127", 100, 105, 98, 102,
                1200, 1000, 1300, 700, 1000, 1301, 1600);
        assertFalse(invalid.isPostmarket(1400));
    }

    @Test
    public void latestCloseUsesPreviousSessionOnlyWhileTheMarketIsOpen() {
        YahooIndexQuote market = new YahooIndexQuote("20261127", 100, 105, 98, 102,
                1200, 1000, 1300, 700, 1000, 1300, 1600, 104f, 99f);
        assertEquals(99f, market.latestRegularClose(1200), 0f);
        assertNull(market.latestRegularClose(800));
        assertNull(market.latestRegularClose(1300));
        YahooIndexQuote beforeOpen = new YahooIndexQuote("20261126", 100, 105, 98, 102,
                600, 1000, 1300, 700, 1000, 1300, 1600, 104f, 99f);
        assertEquals(102f, beforeOpen.latestRegularClose(800), 0f);
        YahooIndexQuote closed = new YahooIndexQuote("20261127", 100, 105, 98, 102,
                1300, 1000, 1300, 700, 1000, 1300, 1600, 104f, 99f);
        assertEquals(102f, closed.latestRegularClose(1400), 0f);
        YahooIndexQuote noPreviousClose = new YahooIndexQuote("20261127", 100, 105, 98, 102,
                1200, 1000, 1300);
        assertNull(noPreviousClose.latestRegularClose(1200));
    }
}
