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

public class NetConnectorBmnrPremarket
        extends AsyncTask<Void, Void, NetConnectorBmnrPremarket.Result> {
    public interface Callback {
        void onComplete(PremarketQuote quote, String error);
    }

    private final Callback callback;

    public NetConnectorBmnrPremarket(Callback callback) {
        this.callback = callback;
    }

    @Override
    protected Result doInBackground(Void... params) {
        HttpURLConnection connection = null;
        try {
            URL url = new URL("https://query1.finance.yahoo.com/v8/finance/chart/BMNR"
                    + "?range=1d&interval=1m&includePrePost=true");
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestProperty("User-Agent", "Mozilla/5.0 (compatible; FinanceApp/1.0)");
            connection.setRequestProperty("Accept", "application/json");
            connection.setConnectTimeout(5000);
            connection.setReadTimeout(10000);
            int status = connection.getResponseCode();
            if (status != HttpURLConnection.HTTP_OK) {
                throw new IOException("Yahoo premarket HTTP " + status);
            }
            StringBuilder body = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                    connection.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    body.append(line);
                    if (body.length() > 2_000_000) {
                        throw new IOException("Yahoo premarket response is too large");
                    }
                }
            }
            return new Result(parse(body.toString(), System.currentTimeMillis() / 1000), null);
        } catch (IOException | JSONException | IllegalArgumentException e) {
            return new Result(null, e.getMessage() == null
                    ? "Yahoo premarket request failed" : e.getMessage());
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    static PremarketQuote parse(String response, long nowSeconds) throws JSONException, IOException {
        JSONObject chart = new JSONObject(response).getJSONObject("chart");
        if (!chart.isNull("error")) {
            throw new IOException("Yahoo premarket error: "
                    + chart.getJSONObject("error").optString("description", "unknown"));
        }
        JSONObject result = chart.getJSONArray("result").getJSONObject(0);
        JSONObject meta = result.getJSONObject("meta");
        if (!"BMNR".equals(meta.getString("symbol"))) {
            throw new IOException("Unexpected premarket symbol");
        }
        JSONObject periods = meta.getJSONObject("currentTradingPeriod");
        JSONObject pre = periods.getJSONObject("pre");
        long start = pre.getLong("start");
        long end = pre.getLong("end");
        if (start <= 0 || end <= start || end != periods.getJSONObject("regular").getLong("start")
                || nowSeconds < start || nowSeconds >= end) {
            throw new IOException("BMNR premarket session is not active");
        }
        JSONArray times = result.getJSONArray("timestamp");
        JSONArray closes = result.getJSONObject("indicators").getJSONArray("quote")
                .getJSONObject(0).getJSONArray("close");
        long latest = -1;
        float price = 0;
        for (int i = 0; i < times.length(); i++) {
            if (i >= closes.length() || times.isNull(i) || closes.isNull(i)) {
                continue;
            }
            long time = times.getLong(i);
            if (time < start || time >= end || time > nowSeconds + 60 || time <= latest) {
                continue;
            }
            float value = (float) closes.getDouble(i);
            if (!Float.isFinite(value) || value <= 0) {
                throw new IOException("Invalid BMNR premarket price");
            }
            latest = time;
            price = value;
        }
        if (latest < 0) {
            throw new IOException("No BMNR premarket price for the current session");
        }
        return new PremarketQuote(price, latest, start, end);
    }

    @Override
    protected void onPostExecute(Result result) {
        callback.onComplete(result.quote, result.error);
    }

    static final class Result {
        final PremarketQuote quote;
        final String error;

        Result(PremarketQuote quote, String error) {
            this.quote = quote;
            this.error = error;
        }
    }
}
