package com.finance.model.net;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

/* 加密货币贪婪恐慌指数（CoinMarketCap），同一分钟内只取一次 */
public final class FearGreedIndex {
    public interface Listener {
        void onUpdated(List<String> records);
    }

    private static final String URL_TEXT = "https://api.coinmarketcap.com/data-api/v3/fear-greed/chart?start=";
    private static final String URL_END = "&end=2000000000";
    private static final long MINUTE_MS = 60L * 1000L;
    private static volatile int value = -1;
    private static long lastFetchedAt;
    private static List<String> cachedRecords = Collections.emptyList();
    private static final List<Listener> pendingListeners = new ArrayList<>();
    private static boolean loading;

    private FearGreedIndex() {
    }

    /* 未取得时返回-1 */
    public static int getValue() {
        return value;
    }

    public static void refreshIfDue(Listener listener) {
        long now = System.currentTimeMillis();
        List<String> records = null;
        synchronized (FearGreedIndex.class) {
            if (loading) {
                if (listener != null) {
                    pendingListeners.add(listener);
                }
                return;
            }
            if (lastFetchedAt > 0 && now - lastFetchedAt < MINUTE_MS) {
                records = cachedRecords;
            } else {
                loading = true;
                if (listener != null) {
                    pendingListeners.add(listener);
                }
            }
        }
        if (records != null) {
            if (listener != null) {
                listener.onUpdated(records);
            }
            return;
        }
        new Thread(() -> {
            try {
                Snapshot snapshot = fetch();
                if (snapshot != null) {
                    List<Listener> listeners;
                    synchronized (FearGreedIndex.class) {
                        value = snapshot.value;
                        cachedRecords = snapshot.records;
                        lastFetchedAt = System.currentTimeMillis();
                        loading = false;
                        listeners = new ArrayList<>(pendingListeners);
                        pendingListeners.clear();
                    }
                    for (Listener pendingListener : listeners) {
                        pendingListener.onUpdated(snapshot.records);
                    }
                }
            } catch (Exception ignored) {
                // 失败时保留上一次的值，下次进入画面再取
            } finally {
                synchronized (FearGreedIndex.class) {
                    loading = false;
                    pendingListeners.clear();
                }
            }
        }).start();
    }

    private static Snapshot fetch() throws Exception {
        JSONObject data = fetchData(30);
        if (data == null) {
            return null;
        }
        JSONObject now = data.getJSONObject("historicalValues").getJSONObject("now");
        int currentValue = clampScore(now.getInt("score"));
        List<String> records = new ArrayList<>();
        JSONArray list = data.getJSONArray("dataList");
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
        dateFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
        String today = dateFormat.format(new Date());
        boolean hasToday = false;
        for (int i = 0; i < list.length(); i++) {
            JSONObject item = list.getJSONObject(i);
            int score = clampScore(item.getInt("score"));
            String date = dateFormat.format(new Date(item.getLong("timestamp") * 1000L));
            if (today.equals(date)) {
                score = currentValue;
                hasToday = true;
            }
            records.add(date + "," + score + "," + score + "," + score + "," + score);
        }
        if (!hasToday) {
            records.add(today + "," + currentValue + "," + currentValue + ","
                    + currentValue + "," + currentValue);
        }
        return new Snapshot(currentValue, Collections.unmodifiableList(records));
    }

    private static int clampScore(int score) {
        return Math.max(0, Math.min(100, score));
    }

    private static final class Snapshot {
        private final int value;
        private final List<String> records;

        private Snapshot(int value, List<String> records) {
            this.value = value;
            this.records = records;
        }
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
