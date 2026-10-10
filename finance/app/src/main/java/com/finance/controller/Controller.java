package com.finance.controller;

import android.app.Activity;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.finance.model.Model;
import com.finance.view.component.trendline.TrendLine;

import java.util.Collections;
import java.util.List;

/**
 * 控制器层 - 升级版
 * 添加了空安全处理和泛型支持
 */
public class Controller {

    private final Model model;

    public Controller(@NonNull Activity view) {
        this.model = new Model(view);
    }

    /**
     * システム初期化の時に、CSVファイルからDBにデータをロード
     */
    public void loadCsvData() {
        if (model != null) {
            model.loadCsvData();
        }
    }

    /**
     * 過日データをロード
     */
    public boolean loadPastData(@NonNull String symbol) {
        if (model != null) {
            return model.loadPastData(symbol);
        }
        return false;
    }

    /**
     * 画面表示のため、データを検索
     */
    @NonNull
    public List<Object[]> getData(@NonNull String symbol, @NonNull String timeFrame,
                                  int dataCount, int offset, int minDisplayCount) {
        if (model != null) {
            return model.getData(symbol, timeFrame, dataCount, offset, minDisplayCount);
        }
        return Collections.emptyList();
    }

    /**
     * 画面表示のため、データを検索
     */
    @NonNull
    public List<Object[]> getData(@NonNull String symbol, @NonNull String timeFrame, int dataCount) {
        if (model != null) {
            return model.getData(symbol, timeFrame, dataCount);
        }
        return Collections.emptyList();
    }

    /**
     * 当日データをロード
     */
    public void loadIntraDayData(@NonNull String symbol) {
        if (model != null) {
            model.loadIntraDayData(symbol);
        }
    }

    /**
     * 日线以外数据再计算
     */
    public void calculateWMQY(@NonNull String symbol) {
        if (model != null) {
            model.calculateWMQY(symbol);
        }
    }

    /**
     * 取得小于指定日期的数据数量作为偏移量
     */
    @NonNull
    public int[] getOffset(@NonNull String symbol, @NonNull String timeFrame, @NonNull String date) {
        if (model != null) {
            return model.getOffset(symbol, timeFrame, date);
        }
        return new int[]{0, 0};
    }

    /**
     * 加载趋势线
     */
    @NonNull
    public List<Object[]> loadTrendLines(@NonNull String symbol, @NonNull String timeFrame) {
        if (model != null) {
            return model.loadTrendLines(symbol, timeFrame);
        }
        return Collections.emptyList();
    }

    /**
     * 保存趋势线
     */
    public void saveTrendLine(@Nullable TrendLine trendLine) {
        if (model != null && trendLine != null) {
            model.saveTrendLine(trendLine);
        }
    }

    /**
     * 删除趋势线
     */
    public void deleteTrendLine(int id) {
        if (model != null && id > 0) {
            model.deleteTrendLine(id);
        }
    }

}