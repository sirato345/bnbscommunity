package com.finance.model.net;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class YahooIndexQuoteTest {
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
}
