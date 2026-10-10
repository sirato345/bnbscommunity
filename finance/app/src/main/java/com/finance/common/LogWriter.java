package com.finance.common;

import android.content.Context;
import android.util.Log;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

public class LogWriter {
    private static final String TAG = "LogWriter";
    private static final DateTimeFormatter CONTENT_FORMAT =
            DateTimeFormatter.ofPattern("'['yyyy.MM.dd HH:mm:ss']': ", Locale.US);
    private static final DateTimeFormatter LOG_FILE_FORMAT =
            DateTimeFormatter.ofPattern("yyyy.MM.dd", Locale.US);
    private static final DateTimeFormatter ERROR_FILE_FORMAT =
            DateTimeFormatter.ofPattern("yyyy.MM.dd HH:mm:ss", Locale.US);

    private static LogWriter instance;
    private final File logDirectory;

    private LogWriter(Context context) {
        Context applicationContext = context == null ? null : context.getApplicationContext();
        if (applicationContext == null) {
            logDirectory = null;
            return;
        }
        File externalDirectory = applicationContext.getExternalFilesDir("logs");
        logDirectory = externalDirectory != null
                ? externalDirectory : new File(applicationContext.getFilesDir(), "logs");
    }

    public static synchronized LogWriter getInstance(Context context) {
        if (instance == null) {
            instance = new LogWriter(context);
        }
        return instance;
    }

    public static synchronized LogWriter getWriter() {
        if (instance == null) {
            throw new IllegalStateException("LogWriter has not been initialized");
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
                writer.write(CONTENT_FORMAT.format(LocalDateTime.now()));
                writer.write(content);
                writer.write("\n");
                writer.flush();
            }
        } catch (IOException e) {
            Log.e(TAG, "写入日志失败", e);
        }
    }

    private File getLogFile() {
        return getLogFile(Const.LOG_FILE + LOG_FILE_FORMAT.format(LocalDateTime.now()) + ".txt");
    }

    private File getErrFile() {
        return getLogFile(Const.ERR_FILE + ERROR_FILE_FORMAT.format(LocalDateTime.now()) + ".txt");
    }

    private File getLogFile(String fileName) {
        if (logDirectory == null) {
            throw new IllegalStateException("LogWriter requires an application context");
        }
        return new File(logDirectory, fileName);
    }

    File getLogDirectory() {
        if (logDirectory == null) {
            throw new IllegalStateException("LogWriter requires an application context");
        }
        return logDirectory;
    }
}