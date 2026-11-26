package com.finance.model.net;

public final class YahooIndexQuote {
    public final String date;
    public final float open;
    public final float high;
    public final float low;
    public final float price;
    public final long quoteTime;
    public final long marketOpen;
    public final long marketClose;
    public final long premarketOpen;
    public final long premarketClose;
    public final long postmarketOpen;
    public final long postmarketClose;

    public YahooIndexQuote(String date, float open, float high, float low, float price,
                           long quoteTime, long marketOpen, long marketClose) {
        this(date, open, high, low, price, quoteTime, marketOpen, marketClose, 0, 0);
    }

    public YahooIndexQuote(String date, float open, float high, float low, float price,
                           long quoteTime, long marketOpen, long marketClose,
                           long premarketOpen, long premarketClose) {
        this(date, open, high, low, price, quoteTime, marketOpen, marketClose,
                premarketOpen, premarketClose, 0, 0);
    }

    public YahooIndexQuote(String date, float open, float high, float low, float price,
                           long quoteTime, long marketOpen, long marketClose,
                           long premarketOpen, long premarketClose,
                           long postmarketOpen, long postmarketClose) {
        this.date = date;
        this.open = open;
        this.high = high;
        this.low = low;
        this.price = price;
        this.quoteTime = quoteTime;
        this.marketOpen = marketOpen;
        this.marketClose = marketClose;
        this.premarketOpen = premarketOpen;
        this.premarketClose = premarketClose;
        this.postmarketOpen = postmarketOpen;
        this.postmarketClose = postmarketClose;
    }

    public boolean isPremarket(long nowSeconds) {
        return premarketOpen > 0 && premarketClose == marketOpen
                && premarketClose > premarketOpen
                && nowSeconds >= premarketOpen && nowSeconds < premarketClose;
    }

    public boolean isPostmarket(long nowSeconds) {
        return postmarketOpen > 0 && postmarketOpen == marketClose
                && postmarketClose > postmarketOpen
                && nowSeconds >= postmarketOpen && nowSeconds < postmarketClose;
    }

    public boolean isMarketOpen(long nowSeconds) {
        return nowSeconds >= marketOpen && nowSeconds < marketClose;
    }

    public String status(long nowSeconds) {
        if (!isMarketOpen(nowSeconds)) {
            return "Closed";
        }
        return quoteTime > nowSeconds + 60 || nowSeconds - quoteTime > 120
                ? "Stale" : "Open";
    }
}
