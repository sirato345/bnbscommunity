package com.finance.model.net;

import android.os.AsyncTask;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

public class NetConnectorYahooIndex
        extends AsyncTask<Void, Void, NetConnectorYahooIndex.Result> {
    public interface Callback {
        void onComplete(String symbol, YahooIndexQuote quote, String error);
    }

    private final String symbol;
    private final Callback callback;

    public NetConnectorYahooIndex(String symbol, Callback callback) {
        this.symbol = symbol;
        this.callback = callback;
    }

    static String ticker(String symbol) {
        switch (symbol) {
            case "dow": return "%5EDJI";
            case "sp500": return "%5EGSPC";
            case "nasdaq": return "%5EIXIC";
            case "bmnr": return "BMNR";
            default: throw new IllegalArgumentException("Unsupported index: " + symbol);
        }
    }

    @Override
    protected Result doInBackground(Void... params) {
        HttpURLConnection connection = null;
        try {
            URL url = new URL("https://query1.finance.yahoo.com/v8/finance/chart/"
                    + ticker(symbol) + "?range=5d&interval=1d");
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestProperty("User-Agent", "Mozilla/5.0 (compatible; FinanceApp/1.0)");
            connection.setRequestProperty("Accept", "application/json");
            connection.setConnectTimeout(5000);
            connection.setReadTimeout(10000);
            int status = connection.getResponseCode();
            if (status != HttpURLConnection.HTTP_OK) {
                throw new IOException("Yahoo index HTTP " + status);
            }
            StringBuilder body = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                    connection.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    body.append(line);
                }
            }
            return new Result(parse(body.toString(), ticker(symbol).replace("%5E", "^")), null);
        } catch (IOException | JSONException | IllegalArgumentException e) {
            return new Result(null, e.getMessage() == null ? "Yahoo index request failed" : e.getMessage());
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    static YahooIndexQuote parse(String response, String expectedTicker) throws JSONException, IOException {
        JSONObject chart = new JSONObject(response).getJSONObject("chart");
        if (!chart.isNull("error")) {
            throw new IOException("Yahoo chart error: "
                    + chart.getJSONObject("error").optString("description", "unknown"));
        }
        JSONObject result = chart.getJSONArray("result").getJSONObject(0);
        JSONObject meta = result.getJSONObject("meta");
        if (!expectedTicker.equals(meta.getString("symbol"))) {
            throw new IOException("Yahoo index returned an unexpected symbol");
        }
        long quoteTime = meta.getLong("regularMarketTime");
        float price = (float) meta.getDouble("regularMarketPrice");
        JSONObject periods = meta.getJSONObject("currentTradingPeriod");
        JSONObject regular = periods.getJSONObject("regular");
        long openTime = regular.getLong("start");
        long closeTime = regular.getLong("end");
        JSONObject premarket = periods.optJSONObject("pre");
        long premarketOpen = premarket == null ? 0 : premarket.optLong("start", 0);
        long premarketClose = premarket == null ? 0 : premarket.optLong("end", 0);
        JSONObject postmarket = periods.optJSONObject("post");
        long postmarketOpen = postmarket == null ? 0 : postmarket.optLong("start", 0);
        long postmarketClose = postmarket == null ? 0 : postmarket.optLong("end", 0);
        float fullDay = (float) meta.optDouble("fulldayPrice", Double.NaN);
        Float fullDayPrice = "BMNR".equals(expectedTicker)
                && Float.isFinite(fullDay) && fullDay > 0 ? fullDay : null;
        if (quoteTime <= 0 || openTime <= 0 || closeTime <= openTime
                || !Float.isFinite(price) || price <= 0) {
            throw new IOException("Invalid Yahoo index quote or market session");
        }
        SimpleDateFormat format = new SimpleDateFormat("yyyyMMdd", Locale.US);
        format.setTimeZone(TimeZone.getTimeZone("America/New_York"));
        String date = format.format(new Date(quoteTime * 1000L));
        JSONArray timestamps = result.getJSONArray("timestamp");
        JSONObject values = result.getJSONObject("indicators").getJSONArray("quote").getJSONObject(0);
        JSONArray opens = values.getJSONArray("open");
        JSONArray highs = values.getJSONArray("high");
        JSONArray lows = values.getJSONArray("low");
        JSONArray closes = values.optJSONArray("close");
        for (int i = timestamps.length() - 1; i >= 0; i--) {
            if (!date.equals(format.format(new Date(timestamps.getLong(i) * 1000L)))) {
                continue;
            }
            if (i >= opens.length() || i >= highs.length() || i >= lows.length()
                    || opens.isNull(i) || highs.isNull(i) || lows.isNull(i)) {
                throw new IOException("Yahoo index daily candle is incomplete");
            }
            float open = (float) opens.getDouble(i);
            float high = (float) highs.getDouble(i);
            float low = (float) lows.getDouble(i);
            if (!Float.isFinite(open) || !Float.isFinite(high) || !Float.isFinite(low)
                    || open <= 0 || high <= 0 || low <= 0) {
                throw new IOException("Invalid Yahoo index daily candle");
            }
            // Yahoo can return a daily range that excludes the open or current market price.
            high = Math.max(high, Math.max(open, price));
            low = Math.min(low, Math.min(open, price));
            Float previousClose = null;
            if ("BMNR".equals(expectedTicker) && closes != null) {
                for (int j = i - 1; j >= 0; j--) {
                    long previousTime = timestamps.optLong(j, 0);
                    if (previousTime <= 0 || date.compareTo(
                            format.format(new Date(previousTime * 1000L))) <= 0) {
                        continue;
                    }
                    float close = (float) closes.optDouble(j, Double.NaN);
                    if (Float.isFinite(close) && close > 0) {
                        previousClose = close;
                    }
                    break;
                }
            }
            return new YahooIndexQuote(date, open, high, low, price, quoteTime, openTime, closeTime,
                    premarketOpen, premarketClose, postmarketOpen, postmarketClose,
                    fullDayPrice, previousClose);
        }
        throw new IOException("Yahoo index quote has no matching daily candle");
    }

    @Override
    protected void onPostExecute(Result result) {
        callback.onComplete(symbol, result.quote, result.error);
    }

    static final class Result {
        final YahooIndexQuote quote;
        final String error;

        Result(YahooIndexQuote quote, String error) {
            this.quote = quote;
            this.error = error;
        }
    }
}
