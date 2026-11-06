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
