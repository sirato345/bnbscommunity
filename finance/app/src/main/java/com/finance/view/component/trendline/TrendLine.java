package com.finance.view.component.trendline;

/**
 * Created by gu zihan on 01/14/2018.
 */

public class TrendLine {
    private int id;

    private String symbol;

    private String timeFrame;

    private String date1;

    private float price1;

    private String date2;

    private float price2;

    private int eidtPoint;

    public static final int TYPE_SEGMENT = 0;
    public static final int TYPE_RAY = 1;
    public static final int TYPE_HORIZONTAL = 2;
    public static final int TYPE_CHANNEL = 3;
    public static final int TYPE_FIBONACCI = 4;

    private int type = TYPE_SEGMENT;

    /* 通道线：平行线相对基准线的价格偏移 */
    private float offset;

    public float getOffset() {
        return offset;
    }

    public void setOffset(float offset) {
        this.offset = offset;
    }

    private int color = 0xFFFFFFFF;

    public int getType() {
        return type;
    }

    public void setType(int type) {
        this.type = type;
    }

    public int getColor() {
        return color;
    }

    public void setColor(int color) {
        this.color = color;
    }

    public int getEidtPoint() {
        return eidtPoint;
    }

    public void setEidtPoint(int eidtPoint) {
        this.eidtPoint = eidtPoint;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getSymbol() {
        return symbol;
    }

    public void setSymbol(String symbol) {
        this.symbol = symbol;
    }

    public String getTimeFrame() {
        return timeFrame;
    }

    public void setTimeFrame(String timeFrame) {
        this.timeFrame = timeFrame;
    }

    public String getDate1() {
        return date1;
    }

    public void setDate1(String date1) {
        this.date1 = date1;
    }

    public float getPrice1() {
        return price1;
    }

    public void setPrice1(float price1) {
        this.price1 = price1;
    }

    public String getDate2() {
        return date2;
    }

    public void setDate2(String date2) {
        this.date2 = date2;
    }

    public float getPrice2() {
        return price2;
    }

    public void setPrice2(float price2) {
        this.price2 = price2;
    }
}
