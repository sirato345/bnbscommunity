package com.finance.model.net;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

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
        JSONObject data = fetchData(3);
        if (data == null) {
            return -1;
        }
        JSONObject now = data.getJSONObject("historicalValues").getJSONObject("now");
        return Math.max(0, Math.min(100, now.getInt("score")));
    }

    /* 取得最近days天的指数，格式与日线记录一致：yyyy-MM-dd,开,高,低,收 */
    public static List<String> fetchRecords(int days) throws Exception {
        List<String> records = new ArrayList<>();
        JSONObject data = fetchData(days);
        if (data == null) {
            return records;
        }
        JSONArray list = data.getJSONArray("dataList");
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
        dateFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
        for (int i = 0; i < list.length(); i++) {
            JSONObject item = list.getJSONObject(i);
            int score = Math.max(0, Math.min(100, item.getInt("score")));
            String date = dateFormat.format(new Date(item.getLong("timestamp") * 1000L));
            records.add(date + "," + score + "," + score + "," + score + "," + score);
        }
        return records;
    }

    private static JSONObject fetchData(int days) throws Exception {
        long start = System.currentTimeMillis() / 1000L - days * 24L * 3600L;
        HttpURLConnection connection = (HttpURLConnection) new URL(URL_TEXT + start + URL_END).openConnection();
        try {
            connection.setRequestProperty("User-Agent", "Mozilla/5.0");
            connection.setConnectTimeout(10000);
            connection.setReadTimeout(10000);
            if (connection.getResponseCode() != HttpURLConnection.HTTP_OK) {
                return null;
            }
            StringBuilder body = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(connection.getInputStream(), "UTF-8"))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    body.append(line);
                }
            }
            return new JSONObject(body.toString()).getJSONObject("data");
        } finally {
            connection.disconnect();
        }
    }
}
