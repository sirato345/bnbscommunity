package com.finance.model.net;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class BinanceSymbolTest {

    @Test
    public void mapsCryptoSymbolsToBinanceSpotPairs() {
        assertEquals("BTCUSDT", BinanceSymbol.getPair("btc"));
        assertEquals("ETHUSDT", BinanceSymbol.getPair("eth"));
        assertEquals("TRUMPUSDT", BinanceSymbol.getPair("trump"));
    }
}
