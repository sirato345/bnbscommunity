package com.finance.model.net;

import android.os.AsyncTask;

import com.finance.common.Const;
import com.finance.common.LogWriter;

import org.json.JSONArray;
import org.json.JSONObject;

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
 * Downloads missing UTC daily candles from Binance.
 */
public class NetConnectorBinance extends AsyncTask<Void, Void, String> {

    private final NetOperatorYahoo netOperator;
    private final String symbol;
    private final List<String> yahooRecords;
    private final long startTime;
    private final long endTime;
    private List<String> records;

    public NetConnectorBinance(NetOperatorYahoo netOperator, String symbol,
                               List<String> yahooRecords, long startTime, long endTime) {
        this.netOperator = netOperator;
        this.symbol = symbol;
        this.yahooRecords = new ArrayList<>(yahooRecords);
        this.startTime = startTime;
        this.endTime = endTime;
    }

    @Override
    protected String doInBackground(Void... params) {
        HttpURLConnection connection = null;
        try {
            String pair = "btc".equals(symbol) ? "BTCUSDT" : "ETHUSDT";
            String request = "https://api.binance.com/api/v3/klines?symbol=" + pair
                    + "&interval=1d&startTime=" + (startTime * 1000L)
                    + "&endTime=" + (endTime * 1000L - 1L) + "&limit=1000";
            LogWriter.getWriter().printLog("BinanceアクセスURL：" + request);
            connection = (HttpURLConnection)new URL(request).openConnection();
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
            records = mergeRecords(yahooRecords, parseCandles(response.toString()));
            return Const.SUCCESS;
        } catch (Exception e) {
            LogWriter.getWriter().printLog(
                    "銘柄：" + symbol + "、Binance接続失敗: " + e.getMessage());
            return e.getMessage() == null
                    ? "Binance market data request failed" : e.getMessage();
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private List<String> parseCandles(String response) throws Exception {
        JSONArray candles = new JSONArray(response);
        List<String> parsedRecords = new ArrayList<>();
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
        dateFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
        for (int i = 0; i < candles.length(); i++) {
            JSONArray candle = candles.getJSONArray(i);
            if (candle.length() < 5) {
                throw new java.io.IOException("Invalid Binance candle response");
            }
            String date = dateFormat.format(new Date(candle.getLong(0)));
            parsedRecords.add(date + "," + candle.getString(1) + "," + candle.getString(2)
                    + "," + candle.getString(3) + "," + candle.getString(4));
        }
        return parsedRecords;
    }

    private List<String> mergeRecords(List<String> yahoo, List<String> binance) {
        List<String> merged = new ArrayList<>(yahoo);
        merged.addAll(binance);
        return merged;
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
    protected void onPostExecute(String result) {
        if (Const.SUCCESS.equals(result)) {
            netOperator.updateHistoryFromFallback(symbol, records);
        } else {
            netOperator.notifyFallbackError(symbol, result);
        }
    }
}
