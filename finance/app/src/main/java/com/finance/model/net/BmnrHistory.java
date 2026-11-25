package com.finance.model.net;

import com.finance.common.Const;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

final class BmnrHistory {
    private static final String YAHOO_START = "2025-06-05";

    static final class Result {
        final List<String> records;
        final List<String> invalidDates;

        Result(List<String> records, List<String> invalidDates) {
            this.records = records;
            this.invalidDates = invalidDates;
        }
    }

    static Result parse(String csv, List<String> yahoo) throws IOException {
        Map<String, Double> yahooCloses = new HashMap<>();
        for (String record : yahoo) {
            String[] fields = record.split(",", -1);
            if (fields.length == 5 && fields[0].compareTo(YAHOO_START) >= 0) {
                try {
                    yahooCloses.put(fields[0], Double.parseDouble(fields[4]));
                } catch (NumberFormatException e) {
                    throw new IOException("Invalid Yahoo BMNR close", e);
                }
            }
        }
        List<String> records = new ArrayList<>();
        List<String> invalidDates = new ArrayList<>();
        int matches = 0;
        String previousDate = "";
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
        dateFormat.setLenient(false);
        try (BufferedReader reader = new BufferedReader(new StringReader(csv))) {
            if (!"date,open,high,low,close,volume".equals(reader.readLine())) {
                throw new IOException("Unexpected BMNR CSV header");
            }
            String line;
            while ((line = reader.readLine()) != null) {
                String[] fields = line.split(",", -1);
                if (fields.length != 6 || !fields[0].matches("\\d{4}-\\d{2}-\\d{2}")
                        || fields[0].compareTo(previousDate) <= 0) {
                    throw new IOException("Invalid or unordered BMNR CSV record: " + line);
                }
                try {
                    dateFormat.parse(fields[0]);
                } catch (ParseException e) {
                    throw new IOException("Invalid BMNR date: " + fields[0], e);
                }
                previousDate = fields[0];
                if (fields[0].replace("-", "").compareTo(Const.BMNR_HISTORY_START) < 0) {
                    continue;
                }
                if (fields[0].compareTo("2025-06-13") > 0) {
                    continue;
                }
                double[] prices = new double[4];
                try {
                    for (int i = 0; i < prices.length; i++) {
                        prices[i] = Double.parseDouble(fields[i + 1]);
                    }
                } catch (NumberFormatException e) {
                    throw new IOException("Invalid BMNR price: " + line, e);
                }
                boolean valid = true;
                for (double price : prices) {
                    valid &= !Double.isNaN(price) && !Double.isInfinite(price) && price > 0;
                }
                valid &= prices[1] >= prices[2] && prices[0] >= prices[2]
                        && prices[0] <= prices[1] && prices[3] >= prices[2]
                        && prices[3] <= prices[1];
                if (!valid) {
                    if ("2023-01-03".equals(fields[0]) && prices[2] == 0) {
                        invalidDates.add(fields[0]);
                        continue;
                    }
                    throw new IOException("Invalid BMNR candle on " + fields[0]);
                }
                if (fields[0].compareTo(YAHOO_START) < 0) {
                    records.add(fields[0] + "," + fields[1] + "," + fields[2]
                            + "," + fields[3] + "," + fields[4]);
                } else if (fields[0].compareTo("2025-06-13") <= 0
                        && yahooCloses.containsKey(fields[0])) {
                    double yahooClose = yahooCloses.get(fields[0]);
                    if (yahooClose <= 0 || !Double.isFinite(yahooClose)
                            || Math.abs(prices[3] - yahooClose) / yahooClose > 0.01) {
                        throw new IOException("BMNR history does not match Yahoo on " + fields[0]);
                    }
                    matches++;
                }
            }
        }
        if (records.isEmpty() || matches < 3) {
            throw new IOException("BMNR history lacks older data or Yahoo overlap");
        }
        return new Result(records, invalidDates);
    }

    private BmnrHistory() {}
}
