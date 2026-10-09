package com.finance.model.net;

import android.os.AsyncTask;
import com.finance.common.Const;
import com.finance.common.LogWriter;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;
import java.util.ArrayList;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONObject;


/**
 * ネット接億クラス
 */
public class NetConnectorYahoo extends AsyncTask {

    private NetOperatorYahoo netOperator;
    private String intraDayData;
    private List<String> historyData;
    private boolean historical;
    private String symbol;

    public NetConnectorYahoo(NetOperatorYahoo netOperator) {
        this.netOperator = netOperator;
    }

    @Override
    protected Object doInBackground(Object[] params) {
        this.symbol = (String)params[0];
        String url = (String)params[1];
        this.historical = (Boolean)params[2];
        URL getUrl;
        // URLオブジェクト取得
        try {
            getUrl = new URL(url);
        } catch (MalformedURLException e) {
            return Const.MESSAGE_6;
        }
        LogWriter.getWriter().printLog("アクセスURL：" + url);
        HttpURLConnection conn = null;
        try {
            conn = (HttpURLConnection)getUrl.openConnection();
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (compatible; FinanceApp/1.0)");
            conn.setRequestProperty("Accept", "application/json");
            conn.setConnectTimeout(10000);
            conn.setReadTimeout(15000);
            int responseCode = conn.getResponseCode();
            if (responseCode != HttpURLConnection.HTTP_OK) {
                throw new java.io.IOException("HTTP " + responseCode + ": " + readError(conn));
            }
            BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream(), "UTF-8"));
            StringBuilder response = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                response.append(line);
            }
            reader.close();
            List<String> records = parseChartResponse(response.toString());
            if (records.isEmpty()) {
                throw new java.io.IOException("No daily market data returned");
            }
            if (historical) {
                historyData = records;
            } else {
                intraDayData = records.get(records.size() - 1);
            }
        } catch (Exception e) {
            LogWriter.getWriter().printLog("銘柄：" + symbol + "、接続失敗: " + e.getMessage());
            return e.getMessage() == null ? "Market data request failed" : e.getMessage();
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
        return Const.SUCCESS;
    }

    private List<String> parseChartResponse(String response) throws Exception {
        List<String> records = new ArrayList<>();
        JSONObject chart = new JSONObject(response).getJSONObject("chart");
        if (!chart.isNull("error")) {
            throw new java.io.IOException(chart.getJSONObject("error").optString("description"));
        }
        JSONArray results = chart.getJSONArray("result");
        if (results.length() == 0 || results.isNull(0)) {
            return records;
        }
        JSONObject result = results.getJSONObject(0);
        JSONArray timestamps = result.getJSONArray("timestamp");
        JSONObject values = result.getJSONObject("indicators").getJSONArray("quote").getJSONObject(0);
        JSONArray opens = values.getJSONArray("open");
        JSONArray highs = values.getJSONArray("high");
        JSONArray lows = values.getJSONArray("low");
        JSONArray closes = values.getJSONArray("close");
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
        dateFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
        for (int i = timestamps.length() - 1; i >= 0; i--) {
            if (opens.isNull(i) || highs.isNull(i) || lows.isNull(i) || closes.isNull(i)) {
                continue;
            }
            String date = dateFormat.format(new Date(timestamps.getLong(i) * 1000L));
                records.add(date + "," + opens.getDouble(i) + "," + highs.getDouble(i)
                    + "," + lows.getDouble(i) + "," + closes.getDouble(i));
        }
            return records;
    }

    private String readError(HttpURLConnection conn) {
        try {
            BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getErrorStream(), "UTF-8"));
            StringBuilder response = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                response.append(line);
            }
            reader.close();
            return response.toString();
        } catch (Exception e) {
            return "Unable to read provider error";
        }
    }

    @Override
    protected void onPostExecute(Object o) {
        super.onPostExecute(o);
        if (o.equals(Const.SUCCESS)) {
            if (historical) {
                netOperator.updateDB(symbol, historyData);
            } else if (intraDayData != null) {
                netOperator.updateDB(symbol, intraDayData);
            } else {
                netOperator.notifyError(symbol, "No daily market data returned");
            }
        } else {
            netOperator.notifyError(symbol, (String) o, historical);
        }
    }
}
