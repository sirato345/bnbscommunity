package com.finance.model.net;

import android.os.AsyncTask;

import com.finance.common.LogWriter;

import org.json.JSONArray;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

/**
 * Loads an in-memory page of Binance spot candles.
 */
public class NetConnectorBinanceIntraday
        extends AsyncTask<Void, Void, NetConnectorBinanceIntraday.Result> {

    public interface Callback {
        void onComplete(String symbol, String interval, List<String> records, String error);
    }

    public static final int PAGE_SIZE = 200;

    private final String symbol;
    private final String interval;
    private final Long endTime;
    private final Callback callback;

    public NetConnectorBinanceIntraday(String symbol, String interval, Long endTime,
                                       Callback callback) {
        this.symbol = symbol;
        this.interval = interval;
        this.endTime = endTime;
        this.callback = callback;
    }

    @Override
    protected Result doInBackground(Void... params) {
        HttpURLConnection connection = null;
        try {
            String pair = "btc".equals(symbol) ? "BTCUSDT" : "ETHUSDT";
            StringBuilder request = new StringBuilder(
                    "https://api.binance.com/api/v3/klines?symbol=")
                    .append(pair)
                    .append("&interval=").append(interval)
                    .append("&limit=").append(PAGE_SIZE);
            if (endTime != null) {
                request.append("&endTime=").append(endTime);
            }
            LogWriter.getWriter().printLog("Binance intraday URL：" + request);
            connection = (HttpURLConnection)new URL(request.toString()).openConnection();
            connection.setRequestProperty("User-Agent",
                    "Mozilla/5.0 (compatible; FinanceApp/1.0)");
            connection.setRequestProperty("Accept", "application/json");
            connection.setConnectTimeout(10000);
            connection.setReadTimeout(15000);

            int responseCode = connection.getResponseCode();
            if (responseCode != HttpURLConnection.HTTP_OK) {
                throw new java.io.IOException("Binance HTTP " + responseCode + ": "
                        + readError(connection));
            }
            StringBuilder response = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                    connection.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    response.append(line);
                }
            }
            return new Result(parseCandles(response.toString()), null);
        } catch (Exception e) {
            LogWriter.getWriter().printLog(
                    "銘柄：" + symbol + "、Binance intraday request failed: " + e.getMessage());
            return new Result(new ArrayList<>(), e.getMessage() == null
                    ? "Binance intraday request failed" : e.getMessage());
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private List<String> parseCandles(String response) throws Exception {
        JSONArray candles = new JSONArray(response);
        List<String> records = new ArrayList<>();
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US);
        dateFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
        for (int i = 0; i < candles.length(); i++) {
            JSONArray candle = candles.getJSONArray(i);
            if (candle.length() < 5) {
                throw new java.io.IOException("Invalid Binance candle response");
            }
            records.add(candle.getLong(0) + "," + dateFormat.format(new Date(candle.getLong(0)))
                    + "," + candle.getString(1) + "," + candle.getString(2)
                    + "," + candle.getString(3) + "," + candle.getString(4));
        }
        return records;
    }

    private String readError(HttpURLConnection connection) {
        if (connection.getErrorStream() == null) {
            return "No provider error details";
        }
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                connection.getErrorStream(), StandardCharsets.UTF_8))) {
            StringBuilder response = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                response.append(line);
            }
            return response.toString();
        } catch (Exception e) {
            return "Unable to read provider error: " + e.getMessage();
        }
    }

    @Override
    protected void onPostExecute(Result result) {
        callback.onComplete(symbol, interval, result.records, result.error);
    }

    static final class Result {
        private final List<String> records;
        private final String error;

        private Result(List<String> records, String error) {
            this.records = records;
            this.error = error;
        }
    }
}
