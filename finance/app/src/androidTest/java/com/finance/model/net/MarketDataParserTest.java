package com.finance.model.net;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

@RunWith(AndroidJUnit4.class)
public class MarketDataParserTest {

    @Test
    public void parsesYahooCandlesUsingExchangeTimezoneAndSkipsMissingPrices()
            throws Exception {
        NetConnectorYahoo connector = new NetConnectorYahoo(null);
        String response = "{\"chart\":{\"result\":[{\"timestamp\":[1609459200,1609545600],"
                + "\"indicators\":{\"quote\":[{\"open\":[29000.0,null],"
                + "\"high\":[29500.0,null],\"low\":[28800.0,null],"
                + "\"close\":[29300.0,null]}]},"
                + "\"meta\":{\"exchangeTimezoneName\":\"America/New_York\"}}],"
                + "\"error\":null}}";

        List<String> records = connector.parseChartResponse(response);

        assertEquals(Collections.singletonList(
                "2020-12-31,29000.0,29500.0,28800.0,29300.0"), records);
    }

    @Test
    public void parsesBinanceCandlesAsUtcIntradayRecords() throws Exception {
        NetConnectorBinanceIntraday connector = new NetConnectorBinanceIntraday(
                "btc", "1h", null, (symbol, interval, records, error) -> { });

        List<String> records = connector.parseCandles(
                "[[1609459200000,\"29000.00\",\"29500.00\",\"28800.00\","
                        + "\"29300.00\",\"1.0\",1609462799999]]");

        assertEquals(Collections.singletonList(
                "1609459200000,2021-01-01 00:00,29000.00,29500.00,28800.00,29300.00"),
                records);
    }

    @Test
    public void rejectsMalformedBinanceCandles() {
        NetConnectorBinanceIntraday connector = new NetConnectorBinanceIntraday(
                "btc", "1h", null, (symbol, interval, records, error) -> { });

        assertThrows(Exception.class, () -> connector.parseCandles("[[1609459200000]]"));
    }

    @Test
    public void parsesBinanceDailyRealtimeCandleWithUtcDate() throws Exception {
        String record = NetConnectorBinanceRealtime.parseCurrentCandle("1d",
                "[[1609459200000,\"29000.00\",\"29500.00\",\"28800.00\","
                        + "\"29300.00\",\"1.0\",1609545599999]]");

        assertEquals("20210101,29000.00,29500.00,28800.00,29300.00", record);
    }

    @Test
    public void parsesYahooIndexQuoteAndMatchesNewYorkTradingDate() throws Exception {
        String response = "{\"chart\":{\"error\":null,\"result\":[{"
                + "\"meta\":{\"symbol\":\"^DJI\",\"regularMarketTime\":1793914551,\"regularMarketPrice\":102,"
                + "\"currentTradingPeriod\":{\"regular\":{\"start\":1793975400,"
                + "\"end\":1793998800}}},\"timestamp\":[1793802600,1793889000],"
                + "\"indicators\":{\"quote\":[{\"open\":[90,100],\"high\":[95,101],"
                + "\"low\":[85,98]}]}}]}}";
        YahooIndexQuote quote = NetConnectorYahooIndex.parse(response, "^DJI");
        assertEquals("20261105", quote.date);
        assertEquals(100f, quote.open, 0f);
        assertEquals(102f, quote.high, 0f);
        assertEquals(98f, quote.low, 0f);
        assertEquals(102f, quote.price, 0f);
    }

    @Test
    public void parsesBmnrQuoteWithUsMarketSession() throws Exception {
        String response = "{\"chart\":{\"error\":null,\"result\":[{"
                + "\"meta\":{\"symbol\":\"BMNR\",\"regularMarketTime\":1793914551,\"regularMarketPrice\":102,"
                + "\"currentTradingPeriod\":{\"regular\":{\"start\":1793889000,"
                + "\"end\":1793912400}}},\"timestamp\":[1793889000],"
                + "\"indicators\":{\"quote\":[{\"open\":[100],\"high\":[103],"
                + "\"low\":[98]}]}}]}}";
        YahooIndexQuote quote = NetConnectorYahooIndex.parse(response, "BMNR");
        assertEquals("20261105", quote.date);
        assertEquals(100f, quote.open, 0f);
        assertEquals(102f, quote.price, 0f);
        assertEquals(1793889000L, quote.marketOpen);
        assertEquals(1793912400L, quote.marketClose);
    }

    @Test
    public void parsesLatestValidBmnrPremarketMinute() throws Exception {
        String response = "{\"chart\":{\"error\":null,\"result\":[{"
                + "\"meta\":{\"symbol\":\"BMNR\",\"tradingPeriods\":{"
                + "\"pre\":[[{\"start\":700,\"end\":1000}]],"
                + "\"regular\":[[{\"start\":1000,\"end\":1300}]]}},"
                + "\"timestamp\":[500,850,810,870,1000,980],"
                + "\"indicators\":{\"quote\":[{\"close\":[90,102,101,null,103,104]}]}}]}}";
        PremarketQuote quote = NetConnectorBmnrPremarket.parse(response, 880);
        assertEquals(850, quote.quoteTime);
        assertEquals(102f, quote.price, 0f);
        assertEquals(980, NetConnectorBmnrPremarket.parse(response, 1100).quoteTime);
        assertThrows(Exception.class, () -> NetConnectorBmnrPremarket.parse(
                response.replace("\"BMNR\"", "\"OTHER\""), 880));
        assertThrows(Exception.class, () -> NetConnectorBmnrPremarket.parse(
                response.replace("\"close\":[90,102,101,null,103,104]",
                        "\"close\":[null,null,null,null,null,null]"), 880));
    }

    @Test
    public void findsMostRecentPremarketAcrossSessionsWithoutUsingAfterHours() throws Exception {
        String response = "{\"chart\":{\"error\":null,\"result\":[{"
                + "\"meta\":{\"symbol\":\"BMNR\",\"tradingPeriods\":{"
                + "\"pre\":[[{\"start\":700,\"end\":1000}],[{\"start\":1700,\"end\":2000}]],"
                + "\"regular\":[[{\"start\":1000,\"end\":1300}],"
                + "[{\"start\":2000,\"end\":2300}]]}},"
                + "\"timestamp\":[850,1200,1750,1900,2100,2400],"
                + "\"indicators\":{\"quote\":[{\"close\":[101,200,102,103,300,400]}]}}]}}";
        PremarketQuote previous = NetConnectorBmnrPremarket.parse(response, 1600);
        assertEquals(850, previous.quoteTime);
        assertEquals(101f, previous.price, 0f);
        PremarketQuote latest = NetConnectorBmnrPremarket.parse(response, 2500);
        assertEquals(1900, latest.quoteTime);
        assertEquals(103f, latest.price, 0f);
        assertThrows(Exception.class, () -> NetConnectorBmnrPremarket.parse(
                response.replace("\"start\":2000,\"end\":2300",
                        "\"start\":2001,\"end\":2300"), 2500));
    }

    @Test
    public void rejectsYahooQuoteWithoutMatchingDailyCandle() {
        String response = "{\"chart\":{\"error\":null,\"result\":[{"
                + "\"meta\":{\"symbol\":\"^DJI\",\"regularMarketTime\":1793914551,\"regularMarketPrice\":102,"
                + "\"currentTradingPeriod\":{\"regular\":{\"start\":1793975400,"
                + "\"end\":1793998800}}},\"timestamp\":[1793802600],"
                + "\"indicators\":{\"quote\":[{\"open\":[90],\"high\":[95],"
                + "\"low\":[85]}]}}]}}";
        assertThrows(Exception.class, () -> NetConnectorYahooIndex.parse(response, "^DJI"));
        assertThrows(Exception.class, () -> NetConnectorYahooIndex.parse(response, "^GSPC"));
    }

    @Test
    public void rejectsEmptyBinanceRealtimeResponse() {
        assertThrows(Exception.class,
                () -> NetConnectorBinanceRealtime.parseCurrentCandle("1d", "[]"));
    }

    @Test
    public void parsesBinanceIntradayRealtimeCandle() throws Exception {
        String record = NetConnectorBinanceRealtime.parseCurrentCandle("5m",
                "[[1609459200000,\"29000.00\",\"29500.00\",\"28800.00\","
                        + "\"29300.00\",\"1.0\",1609459499999]]");

        assertEquals("1609459200000,2021-01-01 09:00,29000.00,29500.00,"
                + "28800.00,29300.00", record);
    }
}
