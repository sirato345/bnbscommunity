package com.finance.common;

import android.os.Environment;
import android.util.Log;
import androidx.annotation.WorkerThread;

import java.io.File;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 日志清理器 - 升级版
 * 使用线程池代替AsyncTask
 */
public class LogCleaner {
    
    private static final String TAG = "LogCleaner";
    private static final SimpleDateFormat format = new SimpleDateFormat("yyyy.MM.dd");
    private static final ExecutorService executor = Executors.newSingleThreadExecutor();

    /**
     * 启动清理任务
     */
    public static void startClean() {
        executor.submit(() -> {
            try {
                cleanLogs();
            } catch (Exception e) {
                Log.e(TAG, "清理日志失败", e);
            }
        });
    }

    @WorkerThread
    private static void cleanLogs() {
        File logDir;
        if (Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED)) {
            // Android 10+ 使用应用专属目录
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                logDir = new File(Environment.getExternalStorageDirectory() + "/Android/data/com.finance/files");
            } else {
                logDir = Environment.getExternalStorageDirectory();
            }
        } else {
            logDir = new File("/data/data/com.finance/files");
        }

        File[] files = logDir.listFiles();
        if (files == null) return;

        int subStart = Const.LOG_FILE.length();
        int subEnd = subStart + 10;
        Calendar calendar = Calendar.getInstance();
        calendar.add(Calendar.DATE, -30);
        Date limitDate = calendar.getTime();

        for (File file : files) {
            if (!file.isFile()) continue;
            
            String fileName = file.getName();
            if (fileName.startsWith(Const.LOG_FILE) || fileName.startsWith(Const.ERR_FILE)) {
                try {
                    if (fileName.length() > subEnd) {
                        Date createDate = format.parse(fileName.substring(subStart, subEnd));
                        if (createDate.before(limitDate)) {
                            boolean deleted = file.delete();
                            Log.d(TAG, "删除日志文件: " + fileName + " - " + (deleted ? "成功" : "失败"));
                        }
                    }
                } catch (ParseException e) {
                    Log.e(TAG, "解析日期失败: " + fileName, e);
                }
            }
        }
    }
}