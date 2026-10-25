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
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

/**
 * Loads the current Binance candle without storing it in the database.
 */
public class NetConnectorBinanceRealtime
        extends AsyncTask<Void, Void, NetConnectorBinanceRealtime.Result> {

    public interface Callback {
        void onComplete(String symbol, String record, String error);
    }

    private final String symbol;
    private final String interval;
    private final Callback callback;

    public NetConnectorBinanceRealtime(String symbol, String interval, Callback callback) {
        this.symbol = symbol;
        this.interval = interval;
        this.callback = callback;
    }

    @Override
    protected Result doInBackground(Void... params) {
        HttpURLConnection connection = null;
        try {
            String request = "https://api.binance.com/api/v3/klines?symbol="
                    + BinanceSymbol.getPair(symbol) + "&interval=" + interval + "&limit=1";
            LogWriter.getWriter().printLog("Binance realtime request: " + request);
            connection = (HttpURLConnection)new URL(request).openConnection();
            connection.setRequestProperty("User-Agent",
                    "Mozilla/5.0 (compatible; FinanceApp/1.0)");
            connection.setRequestProperty("Accept", "application/json");
            connection.setConnectTimeout(5000);
            connection.setReadTimeout(5000);
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
            String klines = response.toString();
            try {
                klines = applyLatestPrice(klines, fetchLatestPrice());
            } catch (Exception e) {
                LogWriter.getWriter().printLog("Binance ticker price unavailable: "
                        + e.getMessage());
            }
            String record = parseCurrentCandle(interval, klines);
            LogWriter.getWriter().printLog("Binance realtime response received: "
                    + symbol + " " + interval + " " + record);
            return new Result(record, null);
        } catch (Exception e) {
            LogWriter.getWriter().printLog(
                    "銘柄：" + symbol + "、Binance realtime request failed: " + e.getMessage());
            return new Result(null, e.getMessage() == null
                    ? "Binance realtime request failed" : e.getMessage());
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    // Uses the real-time last-price endpoint, which is fresher than the kline close.
    private String fetchLatestPrice() throws Exception {
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection)new URL(
                    "https://api.binance.com/api/v3/ticker/price?symbol="
                            + BinanceSymbol.getPair(symbol)).openConnection();
            connection.setRequestProperty("User-Agent",
                    "Mozilla/5.0 (compatible; FinanceApp/1.0)");
            connection.setConnectTimeout(5000);
            connection.setReadTimeout(5000);
            if (connection.getResponseCode() != HttpURLConnection.HTTP_OK) {
                throw new java.io.IOException("Binance ticker HTTP "
                        + connection.getResponseCode());
            }
            StringBuilder response = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                    connection.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    response.append(line);
                }
            }
            return new org.json.JSONObject(response.toString()).getString("price");
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    static String applyLatestPrice(String klines, String price) throws Exception {
        JSONArray candles = new JSONArray(klines);
        if (candles.length() == 0) {
            return klines;
        }
        JSONArray candle = candles.getJSONArray(0);
        double latest = Double.parseDouble(price);
        if (!(latest > 0) || candle.length() < 5) {
            return klines;
        }
        candle.put(2, new java.math.BigDecimal(
                Math.max(Double.parseDouble(candle.getString(2)), latest)).toPlainString());
        candle.put(3, new java.math.BigDecimal(
                Math.min(Double.parseDouble(candle.getString(3)), latest)).toPlainString());
        candle.put(4, price);
        return candles.toString();
    }

    static String parseCurrentCandle(String interval, String response) throws Exception {
        JSONArray candles = new JSONArray(response);
        if (candles.length() == 0) {
            throw new java.io.IOException("Binance returned no current candle");
        }
        JSONArray candle = candles.getJSONArray(0);
        if (candle.length() < 5) {
            throw new java.io.IOException("Invalid Binance candle response");
        }
        if (!"1d".equals(interval)) {
            SimpleDateFormat dateFormat = new SimpleDateFormat(
                    "yyyy-MM-dd HH:mm", Locale.US);
            dateFormat.setTimeZone(TimeZone.getTimeZone("Asia/Tokyo"));
            return candle.getLong(0) + "," + dateFormat.format(new Date(candle.getLong(0)))
                    + "," + candle.getString(1) + "," + candle.getString(2)
                    + "," + candle.getString(3) + "," + candle.getString(4);
        }
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyyMMdd", Locale.US);
        dateFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
        return dateFormat.format(new Date(candle.getLong(0))) + ","
                + candle.getString(1) + "," + candle.getString(2) + ","
                + candle.getString(3) + "," + candle.getString(4);
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
        callback.onComplete(symbol, result.record, result.error);
    }

    static final class Result {
        private final String record;
        private final String error;

        private Result(String record, String error) {
            this.record = record;
            this.error = error;
        }
    }
}
