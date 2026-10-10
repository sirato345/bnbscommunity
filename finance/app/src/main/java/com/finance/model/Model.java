package com.finance.model;

import android.app.Activity;
import androidx.annotation.NonNull;

import com.finance.common.LogWriter;
import com.finance.model.db.DBOperator;
import com.finance.model.net.NetOperatorYahoo;
import com.finance.model.net.NetConnectorBinanceIntraday;
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

    private Activity view;

    public Model(@NonNull Activity view) {
        this.view = view;
        this.dbOperator = new DBOperator(view);
        this.netOperatorYahoo = new NetOperatorYahoo(dbOperator, (Observer)view);
    }

    // ① CSVファイルからDBにデータをロード
    public void loadCsvData() {
        if (dbOperator != null) {
            dbOperator.loadCsvData();
        }
    }

    // ② ネットからDBにデータをロード
    public boolean loadPastData(@NonNull String symbol) {
        if (netOperatorYahoo != null) {
            try {
                return netOperatorYahoo.loadPastData(symbol);
            } catch (RuntimeException e) {
                LogWriter.getInstance(view).error("銘柄：" + symbol + " 履歴取得の準備に失敗", e);
                netOperatorYahoo.notifyError(symbol, e.getMessage() == null
                        ? "Market data request setup failed" : e.getMessage(), true);
            }
        } else {
            LogWriter.getInstance(view).error("履歴取得オペレーターが初期化されていません: " + symbol);
        }
        return false;
    }

    // ③ネットから当日データをDBにロード
    public void loadIntraDayData(@NonNull String symbol) {
        if (netOperatorYahoo != null) {
            try {
                netOperatorYahoo.loadIntraDayData(symbol);
                LogWriter.getInstance(view).log("銘柄：" + symbol + "当日データ更新");
            } catch (RuntimeException e) {
                LogWriter.getInstance(view).error("銘柄：" + symbol + " 当日データ取得の準備に失敗", e);
                netOperatorYahoo.notifyError(symbol, e.getMessage() == null
                        ? "Market data request setup failed" : e.getMessage());
            }
        }
    }

    public void loadBinanceIntraday(@NonNull String symbol, @NonNull String interval,
                                    Long endTime,
                                    @NonNull NetConnectorBinanceIntraday.Callback callback) {
        try {
            new NetConnectorBinanceIntraday(symbol, interval, endTime, callback).execute();
        } catch (RuntimeException e) {
            LogWriter.getInstance(view).error(
                    "銘柄：" + symbol + " Binance intraday request setup failed", e);
            callback.onComplete(symbol, interval, Collections.emptyList(),
                    e.getMessage() == null
                            ? "Binance intraday request setup failed" : e.getMessage());
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

}