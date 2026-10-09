package com.finance.view.component.stick;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Rect;

import com.finance.common.Const;

/**
 * Created by gu zihan on 08/30/2015.
 */
public class StickInfo {

    ////////////////// 設定値 //////////////////
    // 共通情報
    ComnInfo comnInfo;
    // 日付
    private String Date;
    // ローソク足の始値
    private float open;
    // ローソク足の高値
    private float high;
    // ローソク足の安値
    private float low;
    // ローソク足の終値
    private float close;
    // ローソク足の表示順序の番号（左から計算）
    private int index;
    // TD序列
    private int tdSequentialUp;
    // TD序列
    private int tdSequentialDown;
    // チャートに表示可能な最大値
    private float maxValue;
    // チャートに表示可能な再小値
    private float minValue;
    // 最大値所属のローソク足かどうかのフラグ
    private boolean isMaxValueStick;
    // 最小値所属のローソク足かどうかのフラグ
    private boolean isMinValueStick;
    ////////////////// 固定値 //////////////////
    // ローソク足間の間隔
    public static int BETWEEN_SPACE = 2;

    ////////////////// 計算値 //////////////////
    // ローソク足の色
    private int color;
    // ローソク足の棒の位置情報
    private Rect stickRect = new Rect();
    // ローソク足の線の位置情報
    private Rect lineRect = new Rect();

    public StickInfo (ComnInfo commInfo) {
        this.comnInfo = commInfo;
    }

    /* ローソク足自身情報を計算 */
    public void calculate() {
        this.setColor();
        this.calcStickRect();
        this.calcLineRect();
    }

    private void calcStickRect() {
        // X軸計算
        stickRect.left = (int)((index - 1) * comnInfo.getStickWidth());
        // Y軸計算
        stickRect.top = getYPoint(open >= close ? open : close);
        // X軸計算
        stickRect.right = (int)(index * comnInfo.getStickWidth() - BETWEEN_SPACE);
        // Y軸計算
        stickRect.bottom = getYPoint(open < close ? open : close);
        // 开盘收盘同等价格时，显示十字星
        if (stickRect.bottom - stickRect.top < 4) {
            stickRect.bottom = stickRect.top + 4;
        }
    }

    private void calcLineRect() {
        // Y軸始点計算
        lineRect.top = getYPoint(high);
        // Y軸終点計算
        lineRect.bottom = getYPoint(low);
        // X軸計算
        lineRect.left = stickRect.left + (comnInfo.getStickWidth() - BETWEEN_SPACE)/ 2;
        // X軸計算
        lineRect.right = lineRect.left;
    }

    /* 縦軸の値から最大値までの差により縦軸の画素数を計算 */
    public int getYPoint(float value) {
        float valueRate = (maxValue - value) / (maxValue - minValue);
        // StatusBarの高を除いて(0,0)になる
        int yPoint = Math.round(valueRate * comnInfo.getStickHeight());
        return yPoint;
    }

    /* 从纵轴的位置,计算纵轴价格 */
    public float getYValue(int y) {
        float yRate = (float)(comnInfo.getStickHeight() - y) / comnInfo.getStickHeight();
        // StatusBarの高を除いて(0,0)になる
        float yValue = yRate * (maxValue - minValue) + minValue;
        return yValue;
    }

    /* ローソク足の色を設定 */
    private void setColor() {
        if (open >= close) {// PINK
            color = Color.parseColor("#FFB6C1");
        } else {// LIME
            color = Color.parseColor("#00FF00");
        }
    }

    public String getDate() {
        return Date;
    }

    public void setDate(String date) {
        Date = date;
    }

    public float getOpen() {
        return open;
    }

    public void setOpen(float open) {
        this.open = open;
    }

    public float getHigh() {
        return high;
    }

    public void setHigh(float high) {
        this.high = high;
    }

    public float getLow() {
        return low;
    }

    public void setLow(float low) {
        this.low = low;
    }

    public float getClose() {
        return close;
    }

    public void setClose(float close) {
        this.close = close;
    }

    public int getIndex() {
        return index;
    }

    public void setIndex(int index) {
        this.index = index;
    }

    public int getColor() {
        return color;
    }

    public void setColor(int color) {
        this.color = color;
    }

    public Rect getStickRect() {
        return stickRect;
    }

    public void setStickRect(Rect stickRect) {
        this.stickRect = stickRect;
    }

    public Rect getLineRect() {
        return lineRect;
    }

    public void setLineRect(Rect lineRect) {
        this.lineRect = lineRect;
    }

    public float getMaxValue() {
        return maxValue;
    }

    public void setMaxValue(float maxValue) {
        this.maxValue = maxValue;
    }

    public float getMinValue() {
        return minValue;
    }

    public void setMinValue(float minValue) {
        this.minValue = minValue;
    }

    public boolean isMinValueStick() {
        return isMinValueStick;
    }

    public void setIsMinValueStick(boolean isMinValueStick) {
        this.isMinValueStick = isMinValueStick;
    }

    public boolean isMaxValueStick() {
        return isMaxValueStick;
    }

    public void setIsMaxValueStick(boolean isMaxValueStick) {
        this.isMaxValueStick = isMaxValueStick;
    }

    public int getTdSequentialUp() {
        return tdSequentialUp;
    }

    public void setTdSequentialUp(int tdSequentialUp) {
        this.tdSequentialUp = tdSequentialUp;
    }

    public int getTdSequentialDown() {
        return tdSequentialDown;
    }

    public void setTdSequentialDown(int tdSequentialDown) {
        this.tdSequentialDown = tdSequentialDown;
    }
}
