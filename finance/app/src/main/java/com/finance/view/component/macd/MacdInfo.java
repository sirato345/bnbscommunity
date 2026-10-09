package com.finance.view.component.macd;

import android.graphics.Color;
import android.graphics.Point;
import android.graphics.Rect;

import com.finance.common.Const;
import com.finance.view.component.stick.ComnInfo;
import com.finance.view.component.stick.StickInfo;

/**
 * Created by gu zihan on 09/20/2015.
 */
public class MacdInfo {

    // 共通情報
    private ComnInfo comnInfo;
    // ローソク足
    private StickInfo stickInfo;
    // 日付
    private String Date;
    // 短期指数平滑移動平均線(EMA)
    private float emaFast;
    // 長期指数平滑移動平均線(EMA)
    private float emaSlow;
    // MACDの指数平滑化移動平均(シグナル)
    private float signal;
    // MACD(短期EMA－長期EMA)
    private float macd;
    // MACD柱体(MACD－シグナル)
    private float macd_osci;
    // チャートに表示可能な最大値
    private float absMaxValue;
    // チャートに表示可能な最大範囲
    private float fullValue;
    // 零轴
    private int zero_axis;
    // MACDビューの高さ
    private int macd_height;

    ////////////////// 計算値 //////////////////
    // ローソク足の色
    private int macdOsciColor;
    private Rect macdOsciRect = new Rect();
    private Rect macdRect = new Rect();
    private Point macdPoint = new Point();
    private Point signalPoint = new Point();

    public MacdInfo (ComnInfo commInfo) {
        this.comnInfo = commInfo;
    }

    public void calculate(StickInfo stickInfo) {
        this.stickInfo = stickInfo;
        this.macd_height = comnInfo.getMacdHeight();
        this.zero_axis = macd_height / 2;
        this.fullValue = absMaxValue * 2;
        this.setColor();
        this.calcMacdOsciRect();
        this.calcMacdRect();
        this.calcMacdPoint();
        this.calcSignalPoint();
    }

    /* ローソク足の色を設定 */
    private void setColor() {
        if (macd_osci < 0f) {// PINK
            macdOsciColor = Color.parseColor("#FFB6C1");
        } else {// LIME
            macdOsciColor = Color.parseColor("#A7F1FF");
        }
    }

    private void calcMacdOsciRect() {
        // X軸計算
        macdOsciRect.left = stickInfo.getStickRect().left;
        // X軸計算
        macdOsciRect.right = stickInfo.getStickRect().right;
        if (macd_osci >= 0f) {
            // Y軸計算
            macdOsciRect.top = getYPoint(macd_osci);
            // Y軸計算
            macdOsciRect.bottom = zero_axis;
        } else {
            // Y軸計算
            macdOsciRect.top = zero_axis;
            // Y軸計算
            macdOsciRect.bottom = getYPoint(macd_osci);
        }
    }

    private void calcMacdRect() {
        // X軸計算
        macdRect.left = stickInfo.getStickRect().left;
        // X軸計算
        macdRect.right = stickInfo.getStickRect().right;
        if (macd >= 0f) {
            // Y軸計算
            macdRect.top = getYPoint(macd);
            // Y軸計算
            macdRect.bottom = zero_axis;
        } else {
            // Y軸計算
            macdRect.top = zero_axis;
            // Y軸計算
            macdRect.bottom = getYPoint(macd);
        }
    }

    private void calcMacdPoint() {
        macdPoint.y = getYPoint(macd);
        macdPoint.x = stickInfo.getLineRect().centerX();
    }

    private void calcSignalPoint() {
        signalPoint.y = getYPoint(signal);
        signalPoint.x = stickInfo.getLineRect().centerX();
    }

    /* 縦軸の値から最大値までの差により縦軸の画素数を計算 */
    public int getYPoint(float value) {
        float valueRate = 0f;
        int yPoint = 0;
        if (value >= 0f) {
            valueRate = (absMaxValue - value) / fullValue;
        } else {
            valueRate = (absMaxValue + Math.abs(value)) / fullValue;
        }
        // StatusBarの高を除いて(0,0)になる
        yPoint = (int) (valueRate * macd_height);
        return yPoint;
    }

    public void setAbsMaxValue(float absMaxValue) {
        this.absMaxValue = absMaxValue;
    }

    public String getDate() {
        return Date;
    }

    public void setDate(String date) {
        Date = date;
    }

    public float getEmaFast() {
        return emaFast;
    }

    public void setEmaFast(float emaFast) {
        this.emaFast = emaFast;
    }

    public float getEmaSlow() {
        return emaSlow;
    }

    public void setEmaSlow(float emaSlow) {
        this.emaSlow = emaSlow;
    }

    public float getSignal() {
        return signal;
    }

    public void setSignal(float signal) {
        this.signal = signal;
    }

    public float getMacd() {
        return macd;
    }

    public void setMacd(float macd) {
        this.macd = macd;
    }

    public void setMacd_osci(float macd_osci) {
        this.macd_osci = macd_osci;
    }

    public Rect getMacdOsciRect() {
        return macdOsciRect;
    }

    public Point getMacdPoint() {
        return macdPoint;
    }

    public Point getSignalPoint() {
        return signalPoint;
    }

    public int getMacdOsciColor() {
        return macdOsciColor;
    }

    public Rect getMacdRect() {
        return macdRect;
    }
}
