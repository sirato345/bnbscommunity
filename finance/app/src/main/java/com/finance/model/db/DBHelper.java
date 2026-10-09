package com.finance.model.db;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;

import androidx.annotation.NonNull;

import com.finance.common.Const;
import com.finance.model.file.FileOperator;

import java.util.List;

/**
 * DB操作オブジェクト - 升级版
 */
public class DBHelper extends SQLiteOpenHelper {

    private static final String TAG = "DBHelper";
    private static final String CREATE_TABLE_SQL =
            "CREATE TABLE IF NOT EXISTS %s (Date TEXT PRIMARY KEY, Open REAL, High REAL, Low REAL, Close REAL)";
    private static final String CREATE_CALENDAR_TABLE_SQL =
            "CREATE TABLE IF NOT EXISTS %s (Id INTEGER PRIMARY KEY AUTOINCREMENT, Date TEXT, Summary TEXT, Symbol TEXT)";
    private static final String CREATE_TREND_LINE_TABLE_SQL =
            "CREATE TABLE IF NOT EXISTS %s (Id INTEGER PRIMARY KEY AUTOINCREMENT, Symbol TEXT, Timeframe TEXT, " +
                    "Date1 TEXT, Price1 REAL, Date2 TEXT, Price2 REAL)";

    private final List<String> tableList;

    public DBHelper(Context context) {
        super(context, Const.DB_NAME, null, Const.DB_VERSION);
        this.tableList = FileOperator.getTableList();
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        createTables(db);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        Log.i(TAG, "Upgrading database from version " + oldVersion + " to " + newVersion);

        // 可以在这里添加升级逻辑
        if (oldVersion < newVersion) {
            // 例如：添加新表或修改表结构
            // 这里简单重新创建表
            dropTables(db);
            onCreate(db);
        }
    }

    @Override
    public void onOpen(SQLiteDatabase db) {
        super.onOpen(db);
        // 确保外键约束开启
        if (!db.isReadOnly()) {
            db.execSQL("PRAGMA foreign_keys=ON;");
        }
    }

    private void createTables(@NonNull SQLiteDatabase db) {
        db.beginTransaction();
        try {
            // 创建K线数据表
            for (String tableName : tableList) {
                String sql = String.format(CREATE_TABLE_SQL, tableName);
                db.execSQL(sql);
            }

            // 创建日历表
            String calendarSql = String.format(CREATE_CALENDAR_TABLE_SQL, Const.CALENDAR_TABLE);
            db.execSQL(calendarSql);

            // 创建趋势线表
            String trendLineSql = String.format(CREATE_TREND_LINE_TABLE_SQL, Const.TREND_LINE_TABLE);
            db.execSQL(trendLineSql);

            db.setTransactionSuccessful();
            Log.i(TAG, "Tables created successfully");
        } catch (Exception e) {
            Log.e(TAG, "Error creating tables", e);
        } finally {
            db.endTransaction();
        }
    }

    private void dropTables(@NonNull SQLiteDatabase db) {
        db.beginTransaction();
        try {
            for (String tableName : tableList) {
                db.execSQL("DROP TABLE IF EXISTS " + tableName);
            }
            db.execSQL("DROP TABLE IF EXISTS " + Const.CALENDAR_TABLE);
            db.execSQL("DROP TABLE IF EXISTS " + Const.TREND_LINE_TABLE);
            db.setTransactionSuccessful();
        } catch (Exception e) {
            Log.e(TAG, "Error dropping tables", e);
        } finally {
            db.endTransaction();
        }
    }
}