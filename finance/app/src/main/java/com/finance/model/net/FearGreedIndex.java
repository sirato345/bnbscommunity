package com.finance.model.net;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

/* 加密货币贪婪恐慌指数（CoinMarketCap），同一小时内只取一次 */
public final class FearGreedIndex {
    public interface Listener {
        void onUpdated();
    }

    private static final String URL_TEXT = "https://api.coinmarketcap.com/data-api/v3/fear-greed/chart?start=";
    private static final String URL_END = "&end=2000000000";
    private static final long HOUR_MS = 60L * 60L * 1000L;
    private static volatile int value = -1;
    private static volatile long fetchedHour = -1;
    private static volatile boolean loading;

    private FearGreedIndex() {
    }

    /* 未取得时返回-1 */
    public static int getValue() {
        return value;
    }

    public static void refreshIfDue(Listener listener) {
        long hour = System.currentTimeMillis() / HOUR_MS;
        if (loading || hour == fetchedHour) {
            return;
        }
        loading = true;
        new Thread(() -> {
            try {
                int result = fetch();
                if (result >= 0) {
                    value = result;
                    fetchedHour = hour;
                    if (listener != null) {
                        listener.onUpdated();
                    }
                }
            } catch (Exception ignored) {
                // 失败时保留上一次的值，下次进入画面再取
            } finally {
                loading = false;
            }
        }).start();
    }

    private static int fetch() throws Exception {
        long start = System.currentTimeMillis() / 1000L - 3L * 24L * 3600L;
        HttpURLConnection connection = (HttpURLConnection) new URL(URL_TEXT + start + URL_END).openConnection();
        try {
            connection.setRequestProperty("User-Agent", "Mozilla/5.0");
            connection.setConnectTimeout(10000);
            connection.setReadTimeout(10000);
            if (connection.getResponseCode() != HttpURLConnection.HTTP_OK) {
                return -1;
            }
            StringBuilder body = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(connection.getInputStream(), "UTF-8"))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    body.append(line);
                }
            }
            JSONObject now = new JSONObject(body.toString())
                    .getJSONObject("data").getJSONObject("historicalValues").getJSONObject("now");
            return Math.max(0, Math.min(100, now.getInt("score")));
        } finally {
            connection.disconnect();
        }
    }
}
