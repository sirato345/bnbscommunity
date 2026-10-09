package com.finance.view.component.stick;

import android.content.res.Configuration;
import android.graphics.Rect;
import android.util.DisplayMetrics;
import android.view.Window;

import com.finance.view.ChartActivity;

import com.finance.common.Const;

public class ComnInfo {

    // 画面の幅（絶対画素）
    private int screenWidth;
    // 画面の高（絶対画素）
    private int screenHeight;
    // ローソク線エリアの高（絶対画素）
    private int stickHeight;
    // ローソク線エリアの幅（絶対画素）
    private int stickWidth;
    // MACDエリアの高（絶対画素）
    private int macdHeight;
    // 日付エリアの高（絶対画素）
    private int dateHeight;
    // アクティビティ
    private ChartActivity activity;

    public ComnInfo(ChartActivity activity) {
        this.activity = activity;
        // デフォルト表示モード設定
        if (activity.getStickWidth() == 0) {
            activity.setStickWidth(Const.ZOOM_OUT);
        }
        DisplayMetrics dMetrics = new DisplayMetrics();
        activity.getWindowManager().getDefaultDisplay().getMetrics(dMetrics);
        screenWidth = dMetrics.widthPixels;
        // 调节K线区和MACD区交界
        // oppo手机
        if (activity.getResources().getConfiguration().orientation == Configuration.ORIENTATION_PORTRAIT) {
            // 竖屏
            screenHeight = dMetrics.heightPixels - 30;
        } else {
            // 横屏
            screenHeight = dMetrics.heightPixels - 100;
        }
        // LG手机
//        if (activity.getResources().getConfiguration().orientation == Configuration.ORIENTATION_PORTRAIT) {
//            // 竖屏
//            screenHeight = dMetrics.heightPixels + 125;
//        } else {
//            // 横屏
//            screenHeight = dMetrics.heightPixels - 80;
//        }

        // PAD
//        if (activity.getResources().getConfiguration().orientation == Configuration.ORIENTATION_PORTRAIT) {
//            // 竖屏
//            screenHeight = dMetrics.heightPixels - 55;
//        } else {
//            // 横屏
//            screenHeight = dMetrics.heightPixels - 55;
//        }
        stickHeight = screenHeight * Const.WEIGHT_STICK / Const.WEIGHT_ALL;
        macdHeight =screenHeight * Const.WEIGHT_MACD / Const.WEIGHT_ALL;
        dateHeight = screenHeight * Const.WEIGHT_DATE / Const.WEIGHT_ALL;
        stickWidth = activity.getStickWidth();
    }

    // 画面表示の最大ローソクの数を計算
    public int getMaxStickCount(int stickListSize) {
        int maxStickCount = getMaxStickCount();
        // ローソク足の数が画面表示可能の最大数より少ない場合、実際のローソク足の数で設定
        if(maxStickCount > stickListSize) {
            maxStickCount = stickListSize;
        }
        return maxStickCount;
    }

    // 画面表示の最大ローソクの数を計算
    public int getMaxStickCount() {
        int space_right = Const.DEFAULT_OFFSET - activity.getOffset();
        if (space_right < 0) {
            space_right = 0;
        }
        return getMaxDispCount() - space_right;
    }

    // 画面表示可能最大k线数量
    public int getMaxDispCount() {
        return (int)Math.ceil(this.getScreenWidth() / activity.getStickWidth());
    }

    public int getMinDispCount() {
        int minDispCount = (int)Math.ceil(this.getScreenWidth() / activity.getStickWidth()) - Const.DEFAULT_OFFSET;
        return minDispCount;
    }

    public int getScreenWidth() {
        return screenWidth;
    }

    public int getScreenHeight() {
        return screenHeight;
    }

    public int getStickHeight() {
        return stickHeight;
    }

    public int getStickWidth() {
        return stickWidth;
    }

    public int getMacdHeight() {
        return macdHeight;
    }

    public int getDateHeight() {
        return dateHeight;
    }
}
