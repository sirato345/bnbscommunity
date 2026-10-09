package com.finance.common;

import android.content.Context;
import android.os.Environment;
import android.util.Log;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;

public class LogWriter {
    private static final String TAG = "LogWriter";
    private static final SimpleDateFormat contentFormat = new SimpleDateFormat("[yyyy.MM.dd HH:mm:ss]: ");
    private static final SimpleDateFormat logFileFormat = new SimpleDateFormat("yyyy.MM.dd");
    private static final SimpleDateFormat errFileFormat = new SimpleDateFormat("yyyy.MM.dd HH:mm:ss");

    private static LogWriter instance;
    private Context context;

    private LogWriter(Context context) {
        this.context = context != null ? context.getApplicationContext() : null;
    }

    public static synchronized LogWriter getInstance(Context context) {
        if (instance == null) {
            instance = new LogWriter(context);
        }
        return instance;
    }

    public static synchronized LogWriter getWriter() {
        if (instance == null) {
            instance = new LogWriter(null);
        }
        return instance;
    }

    public void log(String message) {
        Log.i(TAG, message);
        writeToFile(getLogFile(), message, true);
    }

    public void error(String message, Throwable throwable) {
        Log.e(TAG, message, throwable);
        String stackTrace = Log.getStackTraceString(throwable);
        writeToFile(getErrFile(), message + "\n" + stackTrace, true);
    }

    public void error(String message) {
        Log.e(TAG, message);
        writeToFile(getErrFile(), message, true);
    }

    public void printLog(String message) {
        log(message);
    }

    private synchronized void writeToFile(File file, String content, boolean append) {
        if (file == null) return;

        try {
            // 确保目录存在
            File parentDir = file.getParentFile();
            if (parentDir != null && !parentDir.exists()) {
                parentDir.mkdirs();
            }

            try (BufferedWriter writer = new BufferedWriter(new FileWriter(file, append))) {
                writer.write(contentFormat.format(new Date()));
                writer.write(content);
                writer.write("\n");
                writer.flush();
            }
        } catch (IOException e) {
            Log.e(TAG, "写入日志失败", e);
        }
    }

    private File getLogFile() {
        return getLogFile(Const.LOG_FILE + logFileFormat.format(new Date()) + ".txt");
    }

    private File getErrFile() {
        return getLogFile(Const.ERR_FILE + errFileFormat.format(new Date()) + ".txt");
    }

    private File getLogFile(String fileName) {
        File logDir;

        if (context == null) {
            if (Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED)) {
                logDir = Environment.getExternalStorageDirectory();
            } else {
                logDir = new File("/sdcard");
            }
        } else if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            // Android 10+ 使用应用专属目录
            logDir = context.getExternalFilesDir(null);
            if (logDir == null) {
                logDir = context.getFilesDir();
            }
        } else {
            // Android 9 及以下
            if (Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED)) {
                logDir = Environment.getExternalStorageDirectory();
            } else {
                logDir = context.getFilesDir();
            }
        }

        return new File(logDir, fileName);
    }
}