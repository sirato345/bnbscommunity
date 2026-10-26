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
    private static final String CREATE_TREND_LINE_TABLE_SQL =
            "CREATE TABLE IF NOT EXISTS %s (Id INTEGER PRIMARY KEY AUTOINCREMENT, Symbol TEXT, Timeframe TEXT, " +
                    "Date1 TEXT, Price1 REAL, Date2 TEXT, Price2 REAL, Type INTEGER DEFAULT 0, Color INTEGER DEFAULT -1, Offset REAL DEFAULT 0)";

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

        if (oldVersion < newVersion) {
            createTables(db);
        }
        if (oldVersion < 7) {
            addTrendLineColumn(db, "Type INTEGER DEFAULT 0", "Type");
            addTrendLineColumn(db, "Color INTEGER DEFAULT -1", "Color");
        }
        if (oldVersion < 8) {
            addTrendLineColumn(db, "Offset REAL DEFAULT 0", "Offset");
        }
        if (oldVersion < 4) {
            for (String tableName : tableList) {
                if (tableName.startsWith(Const.Symbol.trump.toString() + "_")) {
                    db.delete(tableName, null, null);
                }
            }
        }
    }

    private void addTrendLineColumn(SQLiteDatabase db, String definition, String name) {
        try (android.database.Cursor cursor = db.rawQuery(
                "PRAGMA table_info(" + Const.TREND_LINE_TABLE + ")", null)) {
            while (cursor.moveToNext()) {
                if (name.equalsIgnoreCase(cursor.getString(1))) {
                    return;
                }
            }
        }
        db.execSQL("ALTER TABLE " + Const.TREND_LINE_TABLE + " ADD COLUMN " + definition);
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

}