package com.finance.model.net;

final class BinanceSymbol {

    private BinanceSymbol() {
    }

    static String getPair(String symbol) {
        switch (symbol) {
            case "btc":
                return "BTCUSDT";
            case "eth":
                return "ETHUSDT";
            default:
                throw new IllegalArgumentException("Unsupported Binance symbol: " + symbol);
        }
    }
}
