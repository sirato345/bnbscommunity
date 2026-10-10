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
}
