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
    public static final int TYPE_BOX = 5;
    public static final int TYPE_WAVE = 6;

    /* 五浪：第3~6个点，格式 date|price;date|price（第1、2个点在date1/price1、date2/price2） */
    private String extraPoints = "";

    public String getExtraPoints() {
        return extraPoints;
    }

    public void setExtraPoints(String extraPoints) {
        this.extraPoints = extraPoints == null ? "" : extraPoints;
    }

    /* 五浪全部端点，每个元素为 {日期, 价格} */
    public java.util.List<Object[]> getWavePoints() {
        java.util.List<Object[]> points = new java.util.ArrayList<>();
        points.add(new Object[] {date1, price1});
        points.add(new Object[] {date2, price2});
        for (String item : extraPoints.split(";")) {
            String[] parts = item.split("\\|");
            if (parts.length == 2) {
                try {
                    points.add(new Object[] {parts[0], Float.parseFloat(parts[1])});
                } catch (NumberFormatException ignored) {
                    // 忽略损坏的数据
                }
            }
        }
        return points;
    }

    public void setWavePoints(java.util.List<Object[]> points) {
        date1 = (String) points.get(0)[0];
        price1 = (Float) points.get(0)[1];
        date2 = (String) points.get(1)[0];
        price2 = (Float) points.get(1)[1];
        StringBuilder builder = new StringBuilder();
        for (int i = 2; i < points.size(); i++) {
            if (i > 2) {
                builder.append(';');
            }
            builder.append(points.get(i)[0]).append('|').append(points.get(i)[1]);
        }
        extraPoints = builder.toString();
    }

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
