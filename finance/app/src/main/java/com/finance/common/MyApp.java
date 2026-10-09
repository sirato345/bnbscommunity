package com.finance.common;

import android.app.Application;
import androidx.annotation.NonNull;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.concurrent.ConcurrentHashMap;

public class MyApp extends Application {

    private final ConcurrentHashMap<String, ConnectStatus> statusMap = new ConcurrentHashMap<>();
    private final SimpleDateFormat format = new SimpleDateFormat("yyyyMMdd HH:mm:ss");

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

        if (!status.getConnectResult()) {
            LogWriter.getInstance(this).log(symbol + ":接続失敗のため、接続OK");
            return true;
        }

        boolean canConnect = isTimeIntervalValid(status.getPreviousAccess());
        if (canConnect) {
            LogWriter.getInstance(this).log(symbol + ":接続間隔外のため、接続OK");
        } else {
            LogWriter.getInstance(this).log(symbol + ":接続間隔内のため、接続不可");
            LogWriter.getInstance(this).log(symbol + ":前回(" +
                    getTime(status.getPreviousAccess()) + ")、今回(" +
                    getTime(System.currentTimeMillis()) + ")");
        }
        return canConnect;
    }

    public void setConnecting(@NonNull String symbol) {
        ConnectStatus status = statusMap.computeIfAbsent(symbol, k -> new ConnectStatus());
        status.setConnecting(true);
        status.setPreviousAccess(System.currentTimeMillis());
    }

    public void setResult(@NonNull String symbol, boolean result) {
        ConnectStatus status = statusMap.get(symbol);
        if (status != null) {
            status.setConnecting(false);
            status.setConnectResult(result);
        }
    }

    private String getTime(long time) {
        return format.format(new Date(time));
    }

    private boolean isTimeIntervalValid(long previousTime) {
        return System.currentTimeMillis() - previousTime >= Const.UPDATE_GAP;
    }
}