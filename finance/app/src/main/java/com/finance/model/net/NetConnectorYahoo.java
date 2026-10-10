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
 * Downloads and parses Yahoo daily chart data.
 */
public class NetConnectorYahoo extends AsyncTask<Object, Void, String> {

    private final NetOperatorYahoo netOperator;
    private String symbol;
    private boolean historical;
    private List<String> records;

    public NetConnectorYahoo(NetOperatorYahoo netOperator) {
        this.netOperator = netOperator;
    }

    @Override
    protected String doInBackground(Object... params) {
        symbol = (String)params[0];
        String url = (String)params[1];
        historical = (Boolean)params[2];
        HttpURLConnection connection = null;
        try {
            URL requestUrl = new URL(url);
            LogWriter.getWriter().printLog("アクセスURL：" + url);
            connection = (HttpURLConnection)requestUrl.openConnection();
            connection.setRequestProperty("User-Agent",
                    "Mozilla/5.0 (compatible; FinanceApp/1.0)");
            connection.setRequestProperty("Accept", "application/json");
            connection.setConnectTimeout(10000);
            connection.setReadTimeout(15000);

            int responseCode = connection.getResponseCode();
            if (responseCode != HttpURLConnection.HTTP_OK) {
                throw new java.io.IOException("HTTP " + responseCode + ": " + readError(connection));
            }

            StringBuilder response = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                    connection.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    response.append(line);
                }
            }
            records = parseChartResponse(response.toString());
            return Const.SUCCESS;
        } catch (Exception e) {
            LogWriter.getWriter().printLog("銘柄：" + symbol + "、接続失敗: " + e.getMessage());
            return e.getMessage() == null ? "Market data request failed" : e.getMessage();
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    List<String> parseChartResponse(String response) throws Exception {
        List<String> parsedRecords = new ArrayList<>();
        JSONObject chart = new JSONObject(response).getJSONObject("chart");
        if (!chart.isNull("error")) {
            throw new java.io.IOException(chart.getJSONObject("error").optString("description"));
        }
        JSONArray results = chart.getJSONArray("result");
        if (results.length() == 0 || results.isNull(0)) {
            return parsedRecords;
        }

        JSONObject result = results.getJSONObject(0);
        JSONArray timestamps = result.getJSONArray("timestamp");
        JSONObject values = result.getJSONObject("indicators")
                .getJSONArray("quote").getJSONObject(0);
        JSONArray opens = values.getJSONArray("open");
        JSONArray highs = values.getJSONArray("high");
        JSONArray lows = values.getJSONArray("low");
        JSONArray closes = values.getJSONArray("close");
        String exchangeTimeZone = result.getJSONObject("meta")
                .optString("exchangeTimezoneName", "UTC");
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
        dateFormat.setTimeZone(TimeZone.getTimeZone(exchangeTimeZone));

        for (int i = 0; i < timestamps.length(); i++) {
            if (i >= opens.length() || i >= highs.length()
                    || i >= lows.length() || i >= closes.length()
                    || opens.isNull(i) || highs.isNull(i)
                    || lows.isNull(i) || closes.isNull(i)) {
                continue;
            }
            String date = dateFormat.format(new Date(timestamps.getLong(i) * 1000L));
            parsedRecords.add(date + "," + opens.getDouble(i) + "," + highs.getDouble(i)
                    + "," + lows.getDouble(i) + "," + closes.getDouble(i));
        }
        return parsedRecords;
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
            if (historical) {
                netOperator.updateHistory(symbol, records);
            } else {
                netOperator.updateLatestData(symbol, records);
            }
        } else {
            netOperator.notifyError(symbol, result, historical);
        }
    }
}
