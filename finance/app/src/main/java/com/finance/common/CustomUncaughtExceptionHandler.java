package com.finance.common;

import android.util.Log;

import java.io.PrintWriter;
import java.io.StringWriter;

/* 未知異常をキャッチして、ログファイルにエラーを出力する */
public class CustomUncaughtExceptionHandler implements Thread.UncaughtExceptionHandler {

    private static final String TAG = "CustomUncaughtExceptionHandler";
    private static CustomUncaughtExceptionHandler handler;
    private LogWriter logWriter;

    private CustomUncaughtExceptionHandler() {
        // 私有构造函数，防止外部实例化
    }

    public static CustomUncaughtExceptionHandler getInstance() {
        if (handler == null) {
            handler = new CustomUncaughtExceptionHandler();
        }
        return handler;
    }

    /**
     * 初始化方法，需要在Application中调用
     */
    public void init(android.app.Application application) {
        this.logWriter = LogWriter.getInstance(application);
        Thread.setDefaultUncaughtExceptionHandler(this);
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

        // デフォルト例外ハンドラを実行し、強制終了します。
        Thread.getDefaultUncaughtExceptionHandler().uncaughtException(thread, ex);
    }
}