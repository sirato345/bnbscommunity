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

    private static String ticker(String symbol) {
        switch (symbol) {
            case "dow": return "%5EDJI";
            case "sp500": return "%5EGSPC";
            case "nasdaq": return "%5EIXIC";
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
        JSONObject regular = meta.getJSONObject("currentTradingPeriod").getJSONObject("regular");
        long openTime = regular.getLong("start");
        long closeTime = regular.getLong("end");
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
                    || open <= 0 || low <= 0 || high < low || open > high || open < low) {
                throw new IOException("Invalid Yahoo index daily candle");
            }
            return new YahooIndexQuote(date, open, Math.max(high, price),
                    Math.min(low, price), price, quoteTime, openTime, closeTime);
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
