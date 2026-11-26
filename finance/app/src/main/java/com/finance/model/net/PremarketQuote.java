package com.finance.model.net;

public final class PremarketQuote extends ExtendedHoursQuote {
    public PremarketQuote(float price, long quoteTime, long sessionStart, long sessionEnd) {
        super(price, quoteTime, sessionStart, sessionEnd);
    }

    public boolean isCurrent(YahooIndexQuote market, long nowSeconds) {
        return market != null && market.isPremarket(nowSeconds)
                && sessionStart == market.premarketOpen && sessionEnd == market.premarketClose
                && quoteTime >= sessionStart && quoteTime < sessionEnd;
    }

}
