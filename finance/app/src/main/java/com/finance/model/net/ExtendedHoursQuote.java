package com.finance.model.net;

public class ExtendedHoursQuote {
    public final float price;
    public final long quoteTime;
    public final long sessionStart;
    public final long sessionEnd;

    public ExtendedHoursQuote(float price, long quoteTime, long sessionStart, long sessionEnd) {
        this.price = price;
        this.quoteTime = quoteTime;
        this.sessionStart = sessionStart;
        this.sessionEnd = sessionEnd;
    }

    public boolean isAvailable(long nowSeconds) {
        return Float.isFinite(price) && price > 0 && sessionStart > 0
                && sessionEnd > sessionStart && nowSeconds >= sessionStart
                && quoteTime >= sessionStart && quoteTime < sessionEnd
                && quoteTime <= nowSeconds + 60;
    }

    public boolean isStale(long nowSeconds) {
        return quoteTime > nowSeconds + 60 || nowSeconds - quoteTime > 120;
    }
}
