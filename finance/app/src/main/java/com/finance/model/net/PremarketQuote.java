package com.finance.model.net;

public final class PremarketQuote {
    public final float price;
    public final long quoteTime;
    public final long sessionStart;
    public final long sessionEnd;

    public PremarketQuote(float price, long quoteTime, long sessionStart, long sessionEnd) {
        this.price = price;
        this.quoteTime = quoteTime;
        this.sessionStart = sessionStart;
        this.sessionEnd = sessionEnd;
    }

    public boolean isCurrent(YahooIndexQuote market, long nowSeconds) {
        return market != null && market.isPremarket(nowSeconds)
                && sessionStart == market.premarketOpen && sessionEnd == market.premarketClose
                && quoteTime >= sessionStart && quoteTime < sessionEnd;
    }

    public boolean isStale(long nowSeconds) {
        return quoteTime > nowSeconds + 60 || nowSeconds - quoteTime > 120;
    }
}
