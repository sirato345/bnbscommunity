package com.finance.model.db;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.DatabaseUtils;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;

import com.finance.common.Const;
import com.finance.common.LogWriter;
import com.finance.common.TimeZoneUtil;
import com.finance.model.file.FileOperator;
import com.finance.view.ChartActivity;
import com.finance.view.component.trendline.TrendLine;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * DB操作クラス
 */
public class DBOperator {

    private static DBHelper dbHelper;
    private static SQLiteDatabase dbWritable;
    private static SQLiteDatabase dbReadable;
    private static SimpleDateFormat sf = new SimpleDateFormat("yyyyMMdd");
    private static SimpleDateFormat sf2 = new SimpleDateFormat("MM/dd/yyyy");
    private Context context;

    public DBOperator(Context context) {
        this.context = context;
        if (dbHelper == null) {
            dbHelper = new DBHelper(context);
        }
        if (dbWritable == null) {
            dbWritable = dbHelper.getWritableDatabase();
        }
        if (dbReadable == null) {
            dbReadable = dbHelper.getReadableDatabase();
        }
    }

    // 添加一个便捷方法用于日志记录
    private void log(String message) {
        if (context != null) {
            LogWriter.getInstance(context).log(message);
        }
    }

    private void logError(String message, Throwable throwable) {
        if (context != null) {
            LogWriter.getInstance(context).error(message, throwable);
        }
    }

    private void logError(String message) {
        if (context != null) {
            LogWriter.getInstance(context).error(message);
        }
    }

    // 日足、週足、月足のデータをファイルからロードする
    public void loadCsvData() {
        // DB行データオブジェクト
        ContentValues dbRecord = new ContentValues();
        // ファイル操作オブジェクト
        FileOperator fileOperator;
        // 初期データファイルの行データ
        String[] fileRow;
        // ロック中、他ユーザはデータの読み取りはでき、書き込みはできない
        dbWritable.beginTransactionNonExclusive();

        for (String tableName : FileOperator.getTableList()) {
            // データがロードしていない場合のみ、ロードする
            if (getRecCount(tableName) == 0) {
                fileOperator = new FileOperator(tableName);
                while ((fileRow = fileOperator.getFileRow()) != null) {
                    if (fileRow.length < 5) {
                        continue;
                    }
                    dbRecord.put("Date", fileRow[0]);
                    dbRecord.put("Open", fileRow[1]);
                    dbRecord.put("High", fileRow[2]);
                    dbRecord.put("Low", fileRow[3]);
                    dbRecord.put("Close", fileRow[4]);
                    dbWritable.insert(tableName, "", dbRecord);
                }
            }
        }
        // コミット
        dbWritable.setTransactionSuccessful();
        // トランザクション終了
        dbWritable.endTransaction();
    }

    // テーブルにレコードがあるかどうかを判定する
    private long getRecCount(String tableName) {
        return DatabaseUtils.queryNumEntries(dbReadable, tableName);
    }

    // データ取得の開始日付を算出
    // 該当タイムフレーム最新日付の前のローソク足の値を取得
    // 更新処理は当面のローソク足のみ更新
    public String getDateFrom(String symbol, String timeFrame) {
        String tableName = symbol + "_" + timeFrame;
        String query = "select date from " + tableName + " order by date desc limit 1,1";
        Cursor cursor = dbReadable.rawQuery(query, null);
        cursor.moveToFirst();
        String secondDate = cursor.getString(0);
        cursor.close();
        return secondDate;
    }

    // StooqからのデータをDBにインサート
    public void updateDB(String symbol, String timeFrame, List<String> netDataList) {
        if (netDataList.size() == 0) {
            return;
        }
        // ロック中、他ユーザはデータの読み取りはでき、書き込みはできない
        dbWritable.beginTransactionNonExclusive();
        String table = symbol + "_" + timeFrame;

        ///////////////////////取得開始日データの更新////////////////////////
        String[] recordUpdate = convertToRecord(netDataList.get(0));
        // 判断数据格式是否正确
        if (recordUpdate == null) {
            return;
        }
        String sql = "update " + table + " set OPEN='" + recordUpdate[1]
                + "'" + " , HIGH='" + recordUpdate[2] + "'" + " , LOW='"
                + recordUpdate[3] + "'" + " , CLOSE='" + recordUpdate[4]
                + "'" + " where date='" + recordUpdate[0] + "'";
        log("SQL実行:" + sql);
        dbWritable.execSQL(sql);

        ///////////////////////取得開始日以降データの登録/////////////////////
        try {
            for (int i = 1; i < netDataList.size(); i++) {
                String[] recordForInsert = convertToRecord(netDataList.get(i));
                // 判断数据格式是否正确
                if (recordForInsert == null) {
                    return;
                }
                sql = "insert into " + table + " values ('" + recordForInsert[0]
                        + "','" + recordForInsert[1] + "','" + recordForInsert[2]
                        + "','" + recordForInsert[3] + "','" + recordForInsert[4]
                        + "')";
                dbWritable.execSQL(sql);
                log("SQL実行:" + sql);
            }
        } catch(SQLException e) {
            for (int i = 0; i < netDataList.size(); i++) {
                String[] recordForInsert = convertToRecord(netDataList.get(i));
                sql = "delete from " + table + " where date = '" + recordForInsert[0]
                        + "'";
                dbWritable.execSQL(sql);
                sql = "insert into " + table + " values ('" + recordForInsert[0]
                        + "','" + recordForInsert[1] + "','" + recordForInsert[2]
                        + "','" + recordForInsert[3] + "','" + recordForInsert[4]
                        + "')";
                dbWritable.execSQL(sql);
            }
        }
        dbWritable.setTransactionSuccessful();
        dbWritable.endTransaction();
        // 日线数据加载后，重新计算周月季年数据
        this.calculateWMQY(symbol);
    }

    // YahooからのデータをDBにインサート
    public void updateDB(String symbol, String timeFrame, String intraDayData) {
        // ロック中、他ユーザはデータの読み取りはでき、書き込みはできない
        dbWritable.beginTransactionNonExclusive();
        String table = symbol + "_" + timeFrame;
        String[] recordForInsert = convertToRecord(intraDayData);
        // 判断数据格式是否正确
        if (recordForInsert == null) {
            return;
        }
        // 判断当日数据是否存在
        if (hasData(table, recordForInsert[0])) {
            return;
        }
        String sql = "insert into " + table + " values ('" + recordForInsert[0]
                + "','" + recordForInsert[1] + "','" + recordForInsert[2]
                + "','" + recordForInsert[3] + "','" + recordForInsert[4]
                + "')";
        dbWritable.execSQL(sql);
        log("SQL実行:" + sql);
        dbWritable.setTransactionSuccessful();
        dbWritable.endTransaction();
        // 日线数据加载后，重新计算周月季年数据
        this.calculateWMQY(symbol);
    }

    // GoogleからのデータをDBにインサート
    public void updateDB(String symbol, List<String> calendarDataList) {
        // ロック中、他ユーザはデータの読み取りはでき、書き込みはできない
        dbWritable.beginTransactionNonExclusive();
        String localeYear = TimeZoneUtil.getLocaleYear(symbol);
        String calendarPrefix = Const.CALENDAR_PREFIX + localeYear;
        String calendarDate = null;
        String summary = null;
        String data = null;
        for (int i = 0; i < calendarDataList.size(); i++) {
            data = calendarDataList.get(i);
            if (data.indexOf(calendarPrefix) >= 0) {
                calendarDate = data.substring(Const.CALENDAR_PREFIX.length());
            }
            if (calendarDate != null &&
                    data.indexOf(Const.SUMMARY_PREFIX) >= 0) {
                summary = data.substring(Const.SUMMARY_PREFIX.length()).replaceAll("'","''");
                // 法定节假日判断
                if(symbol.equals("shc")) {
                    if(summary.indexOf("元旦") < 0
                            && summary.indexOf("春节") < 0
                            && summary.indexOf("清明节") < 0
                            && summary.indexOf("劳动节") < 0
                            && summary.indexOf("端午节") < 0
                            && summary.indexOf("中秋节") < 0
                            && summary.indexOf("国庆节") < 0) {

                        continue;
                    }
                }
                if(symbol.equals("dow")) {
                    if(summary.indexOf("Veterans") < 0
                            && summary.indexOf("Thanksgiving") < 0
                            && summary.indexOf("Presidents") < 0
                            && summary.indexOf("New Year") < 0
                            && summary.indexOf("Memorial") < 0
                            && summary.indexOf("Martin Luther King") < 0
                            && summary.indexOf("Labor") < 0
                            && summary.indexOf("Independence") < 0
                            && summary.indexOf("Christmas") < 0
                            && summary.indexOf("Columbus") < 0) {
                        continue;
                    }
                }
                String sql = "insert into " + Const.CALENDAR_TABLE + " values (NULL,'" + calendarDate
                        + "','" + summary + "','" + symbol
                        + "')";
                dbWritable.execSQL(sql);
                log("SQL実行:" + sql);
                calendarDate = null;
            }
        }
        dbWritable.setTransactionSuccessful();
        dbWritable.endTransaction();
    }

    /* 指定銘柄の休日カレンダーが存在するかどうかを判断 */
    public boolean hasCalendar(String symbol) {
        String localeYear = TimeZoneUtil.getLocaleYear(symbol);
        String query = "select * from " + Const.CALENDAR_TABLE +
                " where date like '" + localeYear + "%' and symbol='" +
                symbol + "'";
        Cursor cursor = dbReadable.rawQuery(query, null);
        int count = 0;
        while (cursor.moveToNext()) {
            count = cursor.getInt(0);
        }
        cursor.close();
        if (count > 0) {
            return true;
        } else {
            // 一次会取得去年，今年，明年数据，去年的数据和DB有重复，会导致一意制约，所以全部删除
            this.deleteHolidays();
            return false;
        }
    }

    /* 指定銘柄の休日カレンダーが存在するかどうかを判断 */
    public boolean isHoliday(String symbol, String localeDate) {
        String query = "select count(*) from " + Const.CALENDAR_TABLE +
                " where date = '" + localeDate + "' and symbol='" +
                symbol + "'";
        Cursor cursor = dbReadable.rawQuery(query, null);
        int count = 0;
        while (cursor.moveToNext()) {
            count = cursor.getInt(0);
        }
        cursor.close();
        if (count > 0) {
            return true;
        } else {
            return false;
        }
    }

    // ネットからのデータをDBレコードに変更
    private String[] convertToRecord(String netData) {
        String[] record = new String[5];
        String[] temp = netData.split(",");
        if (temp.length < 5) {
            logError("数据不整合:" + netData);
            return null;
        }
        if (temp[0].indexOf("-") >= 0) {// stooqフォーマット
            temp[0] = temp[0].replace("-", "");
        } else if (temp[0].indexOf("/") >= 0){// Yahooフォーマット
            // 判断日期是否正确
            try {
                temp[0] = sf.format(sf2.parse(temp[0].replaceAll("\"","")));
            } catch (ParseException e) {
                return null;
            }
            // 判断是否为非数字
            if ("N/A".equals(temp[1])) {
                return null;
            }
        }
        record[0] = temp[0];
        record[1] = temp[1];
        record[2] = temp[2];
        record[3] = temp[3];
        record[4] = temp[4];
        return record;
    }

    // 初期化のためのデータを取得
    public List<Object[]> getData(String symbol, String timeFrame, int dataCount, int offset,
                                  int minDisplayCount) {
        String tableName = symbol + "_" + timeFrame;
        String query = "select * from " + tableName + " order by date desc limit " + dataCount;
        // 取得可能件数
        int count = getCount(tableName);
        // 判断是否可滑动
        if (count > minDisplayCount) {// 可滑动
            // 判断指定的滑动范围是否超过最少表示柱体数量
            if (offset > count - minDisplayCount) {// 超过
                // 计算滑动到最少表示柱体时的偏移量
                offset = count - minDisplayCount;
            }
            query = query + " offset " + (offset - Const.DEFAULT_OFFSET);
            // 实际滑动可能距离设置
            ((ChartActivity)context).setOffset(offset);
        } else if (count == minDisplayCount) {
            // 实际滑动可能距离设置
            ((ChartActivity)context).setOffset(offset);
        } else {
            offset = count - ((ChartActivity)context).getComnInfo().getMinDispCount();
            // 实际取得数据数量少于最少表示数量时，偏移量设为0
            if (offset < 0) {
                offset = 0;
            }
            ((ChartActivity)context).setOffset(offset);
        }

        Cursor cursor = dbReadable.rawQuery(query, null);
        List<Object[]> dataList = new ArrayList<>();
        Object[] data = null;
        while (cursor.moveToNext()) {
            data = new Object[5];
            data[0] = cursor.getString(0);
            data[1] = cursor.getFloat(1);
            data[2] = cursor.getFloat(2);
            data[3] = cursor.getFloat(3);
            data[4] = cursor.getFloat(4);
            dataList.add(data);
        }
        cursor.close();
        return dataList;
    }

    // 初期化のためのデータを取得
    public List<Object[]> getData(String symbol, String timeFrame, int dataCount) {
        String tableName = symbol + "_" + timeFrame;
        String query = "select * from " + tableName + " order by date desc limit " + dataCount;

        Cursor cursor = dbReadable.rawQuery(query, null);
        List<Object[]> dataList = new ArrayList<>();
        Object[] data = null;
        while (cursor.moveToNext()) {
            data = new Object[5];
            data[0] = cursor.getString(0);
            data[1] = cursor.getFloat(1);
            data[2] = cursor.getFloat(2);
            data[3] = cursor.getFloat(3);
            data[4] = cursor.getFloat(4);
            dataList.add(data);
        }
        cursor.close();
        return dataList;
    }

    // タイムフレーム計算
    public void calculateWMQY(String symbol) {
        this.calculateWeek(symbol);
        this.calculateMonth(symbol);
        this.calculateQuarter(symbol);
        this.calculateYear(symbol);
    }

    // 计算取得可能数据件数
    private int getCount(String tableName) {
        String query = "select count(*) from " + tableName;
        Cursor cursor = dbReadable.rawQuery(query, null);
        int count = 0;
        while (cursor.moveToNext()) {
            count = cursor.getInt(0);
        }
        cursor.close();
        return count;
    }

    // 周线计算
    private void calculateWeek(String symbol) {
        String tableDaily = symbol + "_d";
        String tableWeek = symbol + "_w";
        String query = null;
        if (symbol.equals("shc")) {
            query = "select * from " + tableDaily + " where DATE >= " +
                    "(select strftime('%Y%m%d',date(substr(max(DATE),1,4) || '-' " +
                    "|| substr(max(DATE),5,2) || '-' || substr(max(DATE),7,2)," +
                    "'-6 day')) from " + tableWeek + ") order by DATE";
        } else {
            query = "select * from " + tableDaily + " where DATE >= " +
                    "(select strftime('%Y%m%d',date(substr(max(DATE),1,4) || '-' " +
                    "|| substr(max(DATE),5,2) || '-' || substr(max(DATE),7,2)," +
                    "'-4 day')) from " + tableWeek + ") order by DATE";
        }
        Cursor cursor = dbReadable.rawQuery(query, null);
        List<Object[]> dataList = new ArrayList<>();
        Map<String, Object[]> weekMap = new TreeMap<>();
        Object[] data = null;
        while (cursor.moveToNext()) {
            String date = getLastDayOfWeek(symbol, cursor.getString(0));
            float open = cursor.getFloat(1);
            float high = cursor.getFloat(2);
            float low = cursor.getFloat(3);
            float close = cursor.getFloat(4);
            if (weekMap.containsKey(date)) {
                // 存在、更新
                data = weekMap.get(date);
                data[2] = Math.max(high, (Float) data[2]);
                data[3] = Math.min(low, (Float) data[3]);
                data[4] = close;
            } else {
                // 存在しない、登録
                data = new Object[] {date, open, high, low, close};
                weekMap.put(date, data);
            }
        }
        cursor.close();
        dataList.addAll(weekMap.values());
        updateDB2(symbol, "w", dataList);
    }

    // 月线计算
    private void calculateMonth(String symbol) {
        String tableDaily = symbol + "_d";
        String tableMonth = symbol + "_m";
        String query = "select * from " + tableDaily + " where DATE >= " +
                "(select substr(max(DATE),1,4) || substr(max(DATE),5,2) from "
                + tableMonth + ") order by DATE";
        Cursor cursor = dbReadable.rawQuery(query, null);
        List<Object[]> dataList = new ArrayList<>();
        Map<String, Object[]> monthMap = new TreeMap<>();
        Object[] data = null;
        while (cursor.moveToNext()) {
            String date = getLastDayOfMonth(cursor.getString(0));
            float open = cursor.getFloat(1);
            float high = cursor.getFloat(2);
            float low = cursor.getFloat(3);
            float close = cursor.getFloat(4);
            if (monthMap.containsKey(date)) {
                // 存在、更新
                data = monthMap.get(date);
                data[2] = Math.max(high, (Float) data[2]);
                data[3] = Math.min(low, (Float) data[3]);
                data[4] = close;
            } else {
                // 存在しない、登録
                data = new Object[] {date, open, high, low, close};
                monthMap.put(date, data);
            }
        }
        cursor.close();
        dataList.addAll(monthMap.values());
        updateDB2(symbol, "m", dataList);
    }

    // 季度线计算
    private void calculateQuarter(String symbol) {
        String tableMonthly = symbol + "_m";
        String tableQuarter = symbol + "_q";
        String query = "select * from " + tableMonthly + " where DATE >= " +
                " (select substr(max(DATE),1,4) || CASE " +
                " WHEN cast(substr(max(DATE),5,2) as integer) BETWEEN 1 AND 3 THEN '01'" +
                " WHEN cast(substr(max(DATE),5,2) as integer) BETWEEN 4 and 6 THEN '04'" +
                " WHEN cast(substr(max(DATE),5,2) as integer) BETWEEN 7 and 9 THEN '07'" +
                " ELSE '10' END from "
                + tableQuarter + ") order by DATE";
        Cursor cursor = dbReadable.rawQuery(query, null);
        List<Object[]> dataList = new ArrayList<>();
        Map<String, Object[]> quarterMap = new TreeMap<>();
        Object[] data = null;
        while (cursor.moveToNext()) {
            String date = getLastDayOfQuarter(symbol, cursor.getString(0));
            float open = cursor.getFloat(1);
            float high = cursor.getFloat(2);
            float low = cursor.getFloat(3);
            float close = cursor.getFloat(4);
            if (quarterMap.containsKey(date)) {
                // 存在、更新
                data = quarterMap.get(date);
                data[2] = Math.max(high, (Float) data[2]);
                data[3] = Math.min(low, (Float) data[3]);
                data[4] = close;
            } else {
                // 存在しない、登録
                data = new Object[] {date, open, high, low, close};
                quarterMap.put(date, data);
            }
        }
        cursor.close();
        dataList.addAll(quarterMap.values());
        updateDB2(symbol, "q", dataList);
    }

    // 年线计算
    private void calculateYear(String symbol) {
        String tableDaily = symbol + "_d";
        String tableYear = symbol + "_y";
        String query = "select * from " + tableDaily + " where DATE >= " +
                "(select substr(max(DATE),1,4) from "
                + tableYear + ") order by DATE";
        Cursor cursor = dbReadable.rawQuery(query, null);
        List<Object[]> dataList = new ArrayList<>();
        Map<String, Object[]> yearMap = new TreeMap<>();
        Object[] data = null;
        while (cursor.moveToNext()) {
            String date = getLastDayOfYear(cursor.getString(0));
            float open = cursor.getFloat(1);
            float high = cursor.getFloat(2);
            float low = cursor.getFloat(3);
            float close = cursor.getFloat(4);
            if (yearMap.containsKey(date)) {
                // 存在、更新
                data = yearMap.get(date);
                data[2] = Math.max(high, (Float) data[2]);
                data[3] = Math.min(low, (Float) data[3]);
                data[4] = close;
            } else {
                // 存在しない、登録
                data = new Object[] {date, open, high, low, close};
                yearMap.put(date, data);
            }
        }
        cursor.close();
        dataList.addAll(yearMap.values());
        updateDB2(symbol, "y", dataList);
    }

    // 指定した日付の週のラストディを算出
    private String getLastDayOfWeek(String symbol, String date) {
        Calendar calc = Calendar.getInstance();
        calc.setFirstDayOfWeek(Calendar.MONDAY);
        calc.set(Calendar.YEAR, Integer.parseInt(date.substring(0, 4)));
        calc.set(Calendar.DATE, 1);// 先初期化日期，以防在31日设置月，导致增加一个月
        calc.set(Calendar.MONTH, Integer.parseInt(date.substring(4, 6)) - 1);
        calc.set(Calendar.DATE, Integer.parseInt(date.substring(6, 8)));
        if (symbol.equals("shc")) {
            calc.add(Calendar.DATE, 8 - calc.get(Calendar.DAY_OF_WEEK));
        } else {
            calc.add(Calendar.DATE, 6 - calc.get(Calendar.DAY_OF_WEEK));
        }
        return sf.format(calc.getTime());
    }

    // 指定した日付の月のラストディを算出
    private String getLastDayOfMonth(String date) {
        Calendar calc = Calendar.getInstance();
        calc.set(Calendar.YEAR, Integer.parseInt(date.substring(0, 4)));
        calc.set(Calendar.DATE, 1);// 先初期化日期，以防在31日设置月，导致增加一个月
        calc.set(Calendar.MONTH, Integer.parseInt(date.substring(4, 6)) - 1);
        calc.set(Calendar.DATE, calc.getActualMaximum(Calendar.DAY_OF_MONTH));
        for(int i = 1; i <=2; i++) {
            int day = calc.get(Calendar.DAY_OF_WEEK);
            if (day == Calendar.SUNDAY || day == Calendar.SATURDAY) {
                calc.set(Calendar.DATE, calc.getActualMaximum(Calendar.DAY_OF_MONTH) - i);
            } else {
                break;
            }
        }
        return sf.format(calc.getTime());
    }

    // 指定した日付の四半期のラストディを算出
    private String getLastDayOfQuarter(String symbol, String date) {
        Calendar calc = Calendar.getInstance();
        int month = Integer.parseInt(date.substring(4, 6));
        if (month >= 1 && month <= 3) {
            month = 3;
        } else if (month >= 4 && month <= 6) {
            month = 6;
        } else if (month >= 7 && month <= 9) {
            month = 9;
        } else {
            month = 12;
        }
        calc.set(Calendar.YEAR, Integer.parseInt(date.substring(0, 4)));
        calc.set(Calendar.DATE, 1);// 先初期化日期，以防在31日设置月，导致增加一个月
        calc.set(Calendar.MONTH, month - 1);
        calc.set(Calendar.DATE, calc.getActualMaximum(Calendar.DAY_OF_MONTH));
        if (symbol.equals("shc") == false) {
            for (int i = 1; i <= 2; i++) {
                int day = calc.get(Calendar.DAY_OF_WEEK);
                if (day == Calendar.SUNDAY || day == Calendar.SATURDAY) {
                    calc.set(Calendar.DATE, calc.getActualMaximum(Calendar.DAY_OF_MONTH) - i);
                } else {
                    break;
                }
            }
        }
        return sf.format(calc.getTime());
    }

    // 指定した日付の年のラストディを算出
    private String getLastDayOfYear(String date) {
        Calendar calc = Calendar.getInstance();
        calc.set(Calendar.YEAR, Integer.parseInt(date.substring(0, 4)));
        calc.set(Calendar.DATE, 1);// 先初期化日期，以防在31日设置月，导致增加一个月
        calc.set(Calendar.MONTH, 11);
        calc.set(Calendar.DATE, calc.getActualMaximum(Calendar.DAY_OF_MONTH));
        for(int i = 1; i <=2; i++) {
            int day = calc.get(Calendar.DAY_OF_WEEK);
            if (day == Calendar.SUNDAY || day == Calendar.SATURDAY) {
                calc.set(Calendar.DATE, calc.getActualMaximum(Calendar.DAY_OF_MONTH) - i);
            } else {
                break;
            }
        }
        return sf.format(calc.getTime());
    }

    // ネットからのデータをDBにインサート
    private void updateDB2(String symbol, String timeFrame, List<Object[]> dataList) {
        if (dataList.size() == 0) {
            return;
        }
        // ロック中、他ユーザはデータの読み取りはでき、書き込みはできない
        dbWritable.beginTransactionNonExclusive();
        String table = symbol + "_" + timeFrame;

        ///////////////////////取得開始日データの更新////////////////////////
        Object[] recordUpdate = dataList.get(0);
        String sql = "update " + table + " set OPEN='" + recordUpdate[1]
                + "'" + " , HIGH='" + recordUpdate[2] + "'" + " , LOW='"
                + recordUpdate[3] + "'" + " , CLOSE='" + recordUpdate[4]
                + "'" + " where date='" + recordUpdate[0] + "'";
        log("SQL実行:" + sql);
        dbWritable.execSQL(sql);

        ///////////////////////取得開始日以降データの登録/////////////////////
        for (int i = 1; i < dataList.size(); i++) {
            Object[] recordForInsert = dataList.get(i);
            sql = "insert into " + table + " values ('" + recordForInsert[0]
                    + "','" + recordForInsert[1] + "','" + recordForInsert[2]
                    + "','" + recordForInsert[3] + "','" + recordForInsert[4]
                    + "')";
            dbWritable.execSQL(sql);
            log("SQL実行:" + sql);
        }
        dbWritable.setTransactionSuccessful();
        dbWritable.endTransaction();
    }

    /* 取得数据库内指定銘柄数据的最大日期 */
    public String getMaxDBDate(String symbol) {
        String tableDaily = symbol + "_d";
        String query = "select max(date) from " + tableDaily;
        Cursor cursor = dbReadable.rawQuery(query, null);
        String maxDate = null;
        while (cursor.moveToNext()) {
            maxDate = cursor.getString(0);
        }
        cursor.close();
        return maxDate;
    }

    /* 取得小于指定日期的数据数量作为偏移量 */
    public int[] getOffset(String symbol, String timeFrame, String date) {
        int[] offset = new int[2];
        String tableName = symbol + "_" + timeFrame;
        String query = "select count(*) from " + tableName +
                " where date > '" + date + "'";
        Cursor cursor = dbReadable.rawQuery(query, null);
        while (cursor.moveToNext()) {
            offset[0] = cursor.getInt(0);
        }
        cursor.close();
        offset[1] = getCount(tableName);
        return offset;
    }

    /* 判断指定日期的数据是否存在 */
    private boolean hasData(String tableName, String date) {
        String query = "select count(*) from " + tableName +
                " where date = '" + date + "'";
        Cursor cursor = dbReadable.rawQuery(query, null);
        int count = 0;
        while (cursor.moveToNext()) {
            count = cursor.getInt(0);
        }
        cursor.close();
        if (count > 0) {
            return true;
        } else {
            return false;
        }
    }

    public List<Object[]> loadTrendLines(String symbol, String timeFrame) {
        String query = "select * from " + Const.TREND_LINE_TABLE + " where Symbol = '" +
                symbol + "' and Timeframe = '" + timeFrame + "' order by id";
        Cursor cursor = dbReadable.rawQuery(query, null);
        List<Object[]> dataList = new ArrayList<>();
        Object[] data = null;
        while (cursor.moveToNext()) {
            data = new Object[7];
            data[0] = cursor.getInt(0);
            data[1] = cursor.getString(1);
            data[2] = cursor.getString(2);
            data[3] = cursor.getString(3);
            data[4] = cursor.getFloat(4);
            data[5] = cursor.getString(5);
            data[6] = cursor.getFloat(6);
            dataList.add(data);
        }
        cursor.close();
        return dataList;
    }

    public void saveTrendLine(TrendLine trendLine) {
        // ロック中、他ユーザはデータの読み取りはでき、書き込みはできない
        dbWritable.beginTransactionNonExclusive();
        String sql = "insert into " + Const.TREND_LINE_TABLE + " values (NULL,'" +
                trendLine.getSymbol()
                + "','" + trendLine.getTimeFrame() + "','" + trendLine.getDate1()
                + "','" + trendLine.getPrice1() + "','" + trendLine.getDate2()
                + "','" + trendLine.getPrice2() + "')";
        dbWritable.execSQL(sql);
        log("SQL実行:" + sql);
        dbWritable.setTransactionSuccessful();
        dbWritable.endTransaction();
    }

    public void deleteTrendLine(int id) {
        // ロック中、他ユーザはデータの読み取りはでき、書き込みはできない
        dbWritable.beginTransactionNonExclusive();
        String sql = "delete from " + Const.TREND_LINE_TABLE + " where id ='" +
                id + "'";
        dbWritable.execSQL(sql);
        log("SQL実行:" + sql);
        dbWritable.setTransactionSuccessful();
        dbWritable.endTransaction();
    }

    public void deleteHolidays() {
        dbWritable.beginTransactionNonExclusive();
        String sql = "delete from " + Const.CALENDAR_TABLE;
        dbWritable.execSQL(sql);
        log("SQL実行:" + sql);
        dbWritable.setTransactionSuccessful();
        dbWritable.endTransaction();
    }
}