package com.finance.common;

import android.app.Application;
import androidx.annotation.NonNull;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;
import java.util.concurrent.ConcurrentHashMap;

public class MyApp extends Application {

    private final ConcurrentHashMap<String, ConnectStatus> statusMap = new ConcurrentHashMap<>();
    private static final String HISTORY_SYNC_PREFERENCES = "history_sync";
    private static final String DAILY_DATA_REFRESH_SUFFIX = "_daily_data_refresh";

    @Override
    public void onCreate() {
        super.onCreate();
        LogWriter.getInstance(this);
        CustomUncaughtExceptionHandler.getInstance().init(this);
    }

    public synchronized boolean isNeedConnect(@NonNull String symbol) {
        ConnectStatus status = statusMap.get(symbol);

        if (status == null) {
            LogWriter.getInstance(this).log(symbol + ":初回接続のため、接続OK");
            return true;
        }

        if (status.isConnecting()) {
            LogWriter.getInstance(this).log(symbol + ":接続中のため、接続不可");
            return false;
        }
        LogWriter.getInstance(this).log(symbol + ":前回の更新完了、再接続OK");
        return true;
    }

    public void setConnecting(@NonNull String symbol) {
        ConnectStatus status = statusMap.computeIfAbsent(symbol, k -> new ConnectStatus());
        status.setConnecting(true);
    }

    public void setResult(@NonNull String symbol, boolean result) {
        ConnectStatus status = statusMap.get(symbol);
        if (status != null) {
            status.setConnecting(false);
            status.setConnectResult(result);
        }
    }

    public boolean isHistorySyncDue(@NonNull String symbol) {
        return !isHistorySyncSuccessfulToday(symbol);
    }

    public void markHistorySyncAttempted(@NonNull String symbol) {
        getSharedPreferences(HISTORY_SYNC_PREFERENCES, MODE_PRIVATE)
                .edit()
                .putString(symbol, getMarketDate(symbol))
                .remove(getHistorySyncSuccessKey(symbol))
                .apply();
    }

    public boolean isHistorySyncSuccessfulToday(@NonNull String symbol) {
        String marketDate = getMarketDate(symbol);
        String successfulDate = getSharedPreferences(
                HISTORY_SYNC_PREFERENCES, MODE_PRIVATE)
                .getString(getHistorySyncSuccessKey(symbol), null);
        return marketDate.equals(successfulDate);
    }

    public void markHistorySyncSucceeded(@NonNull String symbol) {
        getSharedPreferences(HISTORY_SYNC_PREFERENCES, MODE_PRIVATE)
                .edit()
                .putString(getHistorySyncSuccessKey(symbol), getMarketDate(symbol))
                .apply();
    }

    public boolean isDailyDataRefreshDue(@NonNull String symbol, String latestDbDate) {
        String marketDate = getMarketDate(symbol);
        if (hasDailyDataForToday(symbol, latestDbDate)) {
            markDailyDataRefreshAttempted(symbol);
            return false;
        }
        String attemptedDate = getSharedPreferences(HISTORY_SYNC_PREFERENCES, MODE_PRIVATE)
                .getString(getDailyDataRefreshKey(symbol), null);
        return !marketDate.equals(attemptedDate);
    }

    public boolean hasDailyDataForToday(@NonNull String symbol, String latestDbDate) {
        return getMarketDate(symbol).equals(latestDbDate);
    }

    public void markDailyDataRefreshAttempted(@NonNull String symbol) {
        getSharedPreferences(HISTORY_SYNC_PREFERENCES, MODE_PRIVATE)
                .edit()
                .putString(getDailyDataRefreshKey(symbol), getMarketDate(symbol))
                .apply();
    }

    private String getHistorySyncSuccessKey(@NonNull String symbol) {
        return symbol + "_success";
    }

    private String getDailyDataRefreshKey(@NonNull String symbol) {
        return symbol + DAILY_DATA_REFRESH_SUFFIX;
    }

    private String getMarketDate(String symbol) {
        String timeZoneId;
        switch (symbol) {
            case "btc":
            case "eth":
            case "trump":
                timeZoneId = "UTC";
                break;
            case "nikkei":
                timeZoneId = "Asia/Tokyo";
                break;
            default:
                timeZoneId = "America/New_York";
                break;
        }
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyyMMdd", Locale.US);
        dateFormat.setTimeZone(TimeZone.getTimeZone(timeZoneId));
        return dateFormat.format(new Date());
    }

}