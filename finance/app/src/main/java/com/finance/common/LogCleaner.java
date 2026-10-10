package com.finance.common;

import android.content.Context;
import android.util.Log;
import androidx.annotation.WorkerThread;

import java.io.File;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 日志清理器 - 升级版
 * 使用线程池代替AsyncTask
 */
public class LogCleaner {
    
    private static final String TAG = "LogCleaner";
    private static final DateTimeFormatter FORMAT =
            DateTimeFormatter.ofPattern("uuuu.MM.dd", Locale.US);
    private static final ExecutorService executor = Executors.newSingleThreadExecutor();

    public static void startClean(Context context) {
        File logDirectory = LogWriter.getInstance(context).getLogDirectory();
        executor.submit(() -> {
            try {
                cleanLogs(logDirectory);
            } catch (Exception e) {
                Log.e(TAG, "清理日志失败", e);
            }
        });
    }

    @WorkerThread
    private static void cleanLogs(File logDirectory) {
        File[] files = logDirectory.listFiles();
        if (files == null) return;

        int subStart = Const.LOG_FILE.length();
        int subEnd = subStart + 10;
        LocalDate limitDate = LocalDate.now().minusDays(30);

        for (File file : files) {
            if (!file.isFile()) continue;
            
            String fileName = file.getName();
            if (fileName.startsWith(Const.LOG_FILE) || fileName.startsWith(Const.ERR_FILE)) {
                try {
                    if (fileName.length() > subEnd) {
                        LocalDate createDate = LocalDate.parse(
                                fileName.substring(subStart, subEnd), FORMAT);
                        if (createDate.isBefore(limitDate)) {
                            boolean deleted = file.delete();
                            Log.d(TAG, "删除日志文件: " + fileName + " - " + (deleted ? "成功" : "失败"));
                        }
                    }
                } catch (DateTimeParseException e) {
                    Log.e(TAG, "解析日期失败: " + fileName, e);
                }
            }
        }
    }
}