package com.finance.view.component.stick;

import com.finance.model.net.YahooIndexQuote;

import java.util.List;
import java.util.function.Function;

public final class IndexCandleMerger {
    private IndexCandleMerger() {
    }

    public static boolean apply(List<Object[]> records, List<Object[]> dailyRecords,
                                YahooIndexQuote quote, String label,
                                Function<String, String> bucketForDate) {
        if (!dailyRecords.isEmpty() && ((String) dailyRecords.get(0)[0]).compareTo(quote.date) > 0) {
            return false;
        }
        if (!records.isEmpty() && ((String) records.get(0)[0]).compareTo(label) > 0) {
            return false;
        }
        String bucket = bucketForDate.apply(quote.date);
        float open = quote.open;
        float high = quote.high;
        float low = quote.low;
        boolean foundEarlier = false;
        for (int i = dailyRecords.size() - 1; i >= 0; i--) {
            Object[] day = dailyRecords.get(i);
            String date = (String) day[0];
            if (date.compareTo(quote.date) >= 0 || !bucket.equals(bucketForDate.apply(date))) {
                continue;
            }
            if (!foundEarlier) {
                open = ((Number) day[1]).floatValue();
                foundEarlier = true;
            }
            high = Math.max(high, ((Number) day[2]).floatValue());
            low = Math.min(low, ((Number) day[3]).floatValue());
        }
        if (!records.isEmpty() && bucket.equals(bucketForDate.apply((String) records.get(0)[0]))) {
            Object[] latest = records.get(0);
            latest[1] = open;
            latest[2] = high;
            latest[3] = low;
            latest[4] = quote.price;
        } else {
            records.add(0, new Object[] {label, open, high, low, quote.price});
        }
        return true;
    }
}
