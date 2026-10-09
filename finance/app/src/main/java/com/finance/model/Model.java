package com.finance.model;

import android.app.Activity;
import androidx.annotation.NonNull;

import com.finance.common.LogWriter;
import com.finance.common.TimeZoneUtil;
import com.finance.model.db.DBOperator;
import com.finance.model.net.NetOperatorGoogle;
import com.finance.model.net.NetOperatorYahoo;
import com.finance.view.component.trendline.TrendLine;

import java.util.Collections;
import java.util.List;
import java.util.Observer;

/**
 * 模型层 - 升级版
 * 使用回调接口替代Observer模式
 */
public class Model {

    // DB操作オブジェクト
    private DBOperator dbOperator;
    // ネット操作オブジェクト
    private NetOperatorYahoo netOperatorYahoo;
    private NetOperatorGoogle netOperatorGoogle;

    private Activity view;

    public Model(@NonNull Activity view) {
        this.view = view;
        this.dbOperator = new DBOperator(view);
        this.netOperatorYahoo = new NetOperatorYahoo(dbOperator, (Observer)view);
        this.netOperatorGoogle = new NetOperatorGoogle(dbOperator, view);
    }

    // ① CSVファイルからDBにデータをロード
    public void loadCsvData() {
        if (dbOperator != null) {
            dbOperator.loadCsvData();
        }
    }

    // ② ネットからDBにデータをロード
    public void loadPastData(@NonNull String symbol) {
        if (netOperatorYahoo != null) {
            try {
                netOperatorYahoo.loadPastData(symbol);
            } catch (RuntimeException e) {
                LogWriter.getInstance(view).error("銘柄：" + symbol + " 履歴取得の準備に失敗", e);
                netOperatorYahoo.notifyError(symbol, e.getMessage() == null
                        ? "Market data request setup failed" : e.getMessage(), true);
            }
        } else {
            LogWriter.getInstance(view).error("履歴取得オペレーターが初期化されていません: " + symbol);
        }
    }

    // ③ネットから当日データをDBにロード
    public void loadIntraDayData(@NonNull String symbol) {
        if (!hasTodayData(symbol) && netOperatorYahoo != null) {
            netOperatorYahoo.loadIntraDayData(symbol);
            LogWriter.getInstance(view).log("銘柄：" + symbol + "当日データ更新");
        }
    }

    /* 休日情報を最新化 */
    public void loadCalendarData(@NonNull String symbol) {
        if (dbOperator != null && !dbOperator.hasCalendar(symbol) && netOperatorGoogle != null) {
            netOperatorGoogle.loadCalendarData(symbol);
        }
    }

    /* 日线以外数据再计算 */
    public void calculateWMQY(@NonNull String symbol) {
        if (dbOperator != null) {
            dbOperator.calculateWMQY(symbol);
        }
    }

    // ③ DBからデータを取得し、UIに表示
    @NonNull
    public List<Object[]> getData(@NonNull String symbol, @NonNull String timeFrame,
                                  int dataCount, int offset, int minDisplayCount) {
        if (dbOperator != null) {
            return dbOperator.getData(symbol, timeFrame, dataCount, offset, minDisplayCount);
        }
        return Collections.emptyList();
    }

    // DBからデータを取得し、UIに表示
    @NonNull
    public List<Object[]> getData(@NonNull String symbol, @NonNull String timeFrame, int dataCount) {
        if (dbOperator != null) {
            return dbOperator.getData(symbol, timeFrame, dataCount);
        }
        return Collections.emptyList();
    }

    /* 判断是否有前日数据 */
    public boolean hasPastData(@NonNull String symbol) {
        if (dbOperator == null) return false;

        String lastBusinessDate = TimeZoneUtil.getLocaleLastBusinessDate(symbol, dbOperator);
        String maxDBDate = dbOperator.getMaxDBDate(symbol);
        return maxDBDate != null && lastBusinessDate.compareTo(maxDBDate) <= 0;
    }

    /* 当日データ更新可否判定 */
    public boolean hasTodayData(@NonNull String symbol) {
        if (dbOperator == null) return true;

        String localeDate = TimeZoneUtil.getLocaleCurrentBusinessDate(symbol, dbOperator);

        // 指定銘柄以外，不允许更新
        if (!"dow".equals(symbol) && !"shc".equals(symbol) && !"nikkei".equals(symbol)) {
            return true;
        }

        String maxDBDate = dbOperator.getMaxDBDate(symbol);
        if (maxDBDate == null) return false;

        String nextBusinessDate = TimeZoneUtil.getLocaleNextBusinessDate(symbol, dbOperator, maxDBDate);

        if (localeDate.equals(nextBusinessDate)) {
            LogWriter.getInstance(view).log("銘柄：" + symbol +
                    "、市场当地时间等于DB数据下一交易日：" + nextBusinessDate);

            String localeTime = TimeZoneUtil.getLocaleTime(symbol);

            // 判断是否收盘
            if ("dow".equals(symbol)) {
                return localeTime.compareTo("1621") <= 0;
            } else if ("shc".equals(symbol) || "nikkei".equals(symbol)) {
                return localeTime.compareTo("1521") <= 0;
            }
        }

        return localeDate.compareTo(nextBusinessDate) <= 0;
    }

    /* 取得小于指定日期的数据数量作为偏移量 */
    @NonNull
    public int[] getOffset(@NonNull String symbol, @NonNull String timeFrame, @NonNull String date) {
        if (dbOperator != null) {
            return dbOperator.getOffset(symbol, timeFrame, date);
        }
        return new int[]{0, 0};
    }

    @NonNull
    public List<Object[]> loadTrendLines(@NonNull String symbol, @NonNull String timeFrame) {
        if (dbOperator != null) {
            return dbOperator.loadTrendLines(symbol, timeFrame);
        }
        return Collections.emptyList();
    }

    public void saveTrendLine(@NonNull TrendLine trendLine) {
        if (dbOperator != null) {
            dbOperator.saveTrendLine(trendLine);
        }
    }

    public void deleteTrendLine(int id) {
        if (dbOperator != null) {
            dbOperator.deleteTrendLine(id);
        }
    }

    public void deleteHolidays() {
        if (dbOperator != null) {
            dbOperator.deleteHolidays();
        }
    }
}