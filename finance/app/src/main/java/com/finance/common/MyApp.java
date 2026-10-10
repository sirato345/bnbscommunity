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

    @Override
    public void onCreate() {
        super.onCreate();
        // 设置未捕获异常处理器
        Thread.setDefaultUncaughtExceptionHandler(CustomUncaughtExceptionHandler.getInstance());
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

    private String getHistorySyncSuccessKey(@NonNull String symbol) {
        return symbol + "_success";
    }

    private String getMarketDate(String symbol) {
        String timeZoneId;
        switch (symbol) {
            case "btc":
            case "eth":
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