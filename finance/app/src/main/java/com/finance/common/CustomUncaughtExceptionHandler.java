package com.finance.common;

import android.app.Application;
import android.util.Log;

import java.io.PrintWriter;
import java.io.StringWriter;

/* 未知異常をキャッチして、ログファイルにエラーを出力する */
public class CustomUncaughtExceptionHandler implements Thread.UncaughtExceptionHandler {

    private static final String TAG = "CustomUncaughtExceptionHandler";
    private static CustomUncaughtExceptionHandler handler;
    private volatile LogWriter logWriter;
    private volatile Thread.UncaughtExceptionHandler defaultHandler;

    CustomUncaughtExceptionHandler() {
        // Keep construction within the common package.
    }

    public static synchronized CustomUncaughtExceptionHandler getInstance() {
        if (handler == null) {
            handler = new CustomUncaughtExceptionHandler();
        }
        return handler;
    }

    public synchronized void init(Application application) {
        this.logWriter = LogWriter.getInstance(application);
        Thread.UncaughtExceptionHandler currentHandler =
                Thread.getDefaultUncaughtExceptionHandler();
        if (currentHandler != this) {
            defaultHandler = currentHandler;
            Thread.setDefaultUncaughtExceptionHandler(this);
        }
    }

    @Override
    public void uncaughtException(Thread thread, Throwable ex) {
        // スタックトレースを文字列にします。
        StringWriter stringWriter = new StringWriter();
        ex.printStackTrace(new PrintWriter(stringWriter));
        String stackTrace = stringWriter.toString();

        // エラーを出力
        if (logWriter != null) {
            logWriter.error(stackTrace);
        } else {
            // 如果logWriter未初始化，至少输出到Logcat
            Log.e(TAG, stackTrace);
        }

        Thread.UncaughtExceptionHandler delegate = defaultHandler;
        if (delegate != null && delegate != this) {
            delegate.uncaughtException(thread, ex);
        } else {
            android.os.Process.killProcess(android.os.Process.myPid());
            System.exit(10);
        }
    }
}