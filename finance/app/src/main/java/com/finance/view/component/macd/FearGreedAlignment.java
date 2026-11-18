package com.finance.view.component.macd;

import java.time.LocalDate;
import java.time.DateTimeException;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class FearGreedAlignment {
    private static final DateTimeFormatter DATE = DateTimeFormatter.BASIC_ISO_DATE;

    private FearGreedAlignment() {
    }

    public static boolean supports(String timeframe) {
        return "d".equals(timeframe) || "3d".equals(timeframe) || "w".equals(timeframe)
                || "m".equals(timeframe) || "q".equals(timeframe) || "y".equals(timeframe);
    }

    public static Map<String, Float> latestByPeriod(List<String> records, String timeframe) {
        Map<String, Float> values = new HashMap<>();
        Map<String, LocalDate> latestDates = new HashMap<>();
        for (String record : records) {
            String[] fields = record.split(",");
            if (fields.length < 5) {
                throw new IllegalArgumentException("Invalid fear and greed record: " + record);
            }
            LocalDate date;
            try {
                date = LocalDate.parse(fields[0].replace("-", ""), DATE);
            } catch (DateTimeException e) {
                throw new IllegalArgumentException("Invalid fear and greed date: " + fields[0], e);
            }
            float score = Float.parseFloat(fields[4]);
            if (!Float.isFinite(score) || score < 0 || score > 100) {
                throw new IllegalArgumentException("Invalid fear and greed score: " + fields[4]);
            }
            String key = periodKey(date, timeframe);
            LocalDate previous = latestDates.get(key);
            if (previous == null || date.isAfter(previous)) {
                latestDates.put(key, date);
                values.put(key, score);
            }
        }
        return values;
    }

    public static String periodKey(String date, String timeframe) {
        return periodKey(LocalDate.parse(date.replace("-", ""), DATE), timeframe);
    }

    private static String periodKey(LocalDate date, String timeframe) {
        switch (timeframe) {
            case "d":
                return DATE.format(date);
            case "3d":
                return Long.toString(Math.floorDiv(date.toEpochDay(), 3));
            case "w":
                return DATE.format(date.minusDays(date.getDayOfWeek().getValue() % 7));
            case "m":
                return date.getYear() + "-" + date.getMonthValue();
            case "q":
                return date.getYear() + "-" + ((date.getMonthValue() - 1) / 3);
            case "y":
                return Integer.toString(date.getYear());
            default:
                throw new IllegalArgumentException("Unsupported timeframe: " + timeframe);
        }
    }
}
