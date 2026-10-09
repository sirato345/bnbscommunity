package com.finance.view.component;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Point;
import android.graphics.Rect;
import android.view.MotionEvent;
import android.view.View;

import com.finance.common.Const;
import com.finance.view.ChartActivity;
import com.finance.view.component.stick.ComnInfo;
import com.finance.view.component.stick.StickInfo;
import com.finance.view.component.trendline.TrendLine;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class StickView extends View {

    private ChartActivity activity;
    // 共通情報
    private ComnInfo comnInfo;
    // ローソク線の情報
    private List<StickInfo> stickList;
    // 10日移動平均線
    private List<Point> average10;
    // 20日移動平均線
    private List<Point> average20;
    // 60日移動平均線
    private List<Point> average60;
    // 画面表示最大のローソクの数
    private int maxStickCount;
    // 当前10均线价格
    private float current10;
    // 当前20均线价格
    private float current20;
    // 当前60均线价格
    private float current60;
    // 对小数四舍五入
    private DecimalFormat digitFromat = new DecimalFormat("#.###");
    // タッチ箇所
    private Point touchPoint;
    // 編集モードの押下ポイント
    private Point downPoint;
    // 編集モードの当面ポイント
    private Point currentPoint;
    // タッチ箇所の日付
    private String touchedDate;
    // タッチ位置の価格
    private Float currentValue;
    // 编辑中趋势线
    private TrendLine editLine;
    // 趋势线
    private List<TrendLine> trendLines = new ArrayList<>();

    public StickView(Context context) {
        super(context);
        // 背景色
        super.setBackgroundColor(Color.BLACK);
        // 画面対象
        this.activity = (ChartActivity)context;
    }

    /* ローソク足のデータを設定し、自動計算させる */
    public void setData(ComnInfo comnInfo, List<StickInfo> stickList, List<TrendLine> trendLines) {
        this.comnInfo = comnInfo;
        this.stickList = stickList;
        this.trendLines = trendLines;
        this.average10 = new ArrayList<>();
        this.average20 = new ArrayList<>();
        this.average60 = new ArrayList<>();
        this.current10 = 0;
        this.current20 = 0;
        this.current60 = 0;
        this.calculate(this.stickList);
        this.calAverageLine(this.stickList, average10, 10);
        this.calAverageLine(this.stickList, average20, 20);
        this.calAverageLine(this.stickList, average60, 60);
        this.calTDtdSequential();
    }

    /* ローソク足の自身情報を計算 */
    public void calculate(List<StickInfo> stickList) {
        // 最大値
        float maxValue = 0.0f;
        // 最小値
        float minValue = 0.0f;
        // 画面表示最大のローソクの数
        this.maxStickCount = comnInfo.getMaxStickCount(stickList.size());
        // 画面表示最大値と最小値を計算、表示可能なローソクのみを統計
        for (int i = 0; i < maxStickCount; i++) {
            StickInfo stickInfo = stickList.get(i);
            float low = stickInfo.getLow();
            float high = stickInfo.getHigh();
            if (minValue == 0) {
                minValue = low;
            } else {
                minValue = minValue <= low ? minValue : low;
            }
            if (maxValue == 0) {
                maxValue = high;
            } else {
                maxValue = maxValue >= high ? maxValue : high;
            }
        }
        // ローソク足に計算値を設定し、ローソク自身情報も計算
        for (int i = 0; i < maxStickCount; i++) {
            // ローソク足
            StickInfo stickInfo = stickList.get(i);
            // 表示番号設定（左から計算）
            stickInfo.setIndex(maxStickCount - i);
            // 最大上限値
            stickInfo.setMaxValue(maxValue);
            // 最小下限値
            stickInfo.setMinValue(minValue);
            // 最大値ローソク足のフラグを設定
            if (stickInfo.getHigh() == maxValue) {
                stickInfo.setIsMaxValueStick(true);
            }
            // 最小値ローソク足のフラグを設定
            if (stickInfo.getLow() == minValue) {
                stickInfo.setIsMinValueStick(true);
            }
            // ローソク足自身情報を計算
            stickInfo.calculate();
        }
    }

    /* 移動平均線計算 */
    public void calAverageLine(List<StickInfo> stickList, List<Point> averageList, int average) {
        int size = stickList.size() - average;
        StickInfo stickInfo;
        float averageValue;
        Point point = null;
        for (int i = 0; i < size; i++) {
            float averageClose = 0.0f;
            for (int j = i; j < i + average; j++) {
                point = new Point();
                stickInfo = stickList.get(j);
                averageClose = averageClose + stickInfo.getClose();
            }
            stickInfo = stickList.get(i);
            averageValue = averageClose / average;
            if (i == 0) {
                if (average == 10) {
                    current10 = averageValue;
                } else if (average == 20) {
                    current20 = averageValue;
                } else if (average == 60) {
                    current60 = averageValue;
                }else {
                }
            }
            point.y = stickInfo.getYPoint(averageValue);
            point.x = stickInfo.getLineRect().centerX();
            averageList.add(point);
        }
    }

    /* TD序列 */
    private void calTDtdSequential() {
        int size = stickList.size() - 4 > 60 ? 60 : stickList.size() - 4;
        for (int i = 0; i < size; i ++) {
            int up = 0;
            int down = 0;
            for (int j = i; j < i + 9; j ++) {
                if (stickList.size() < j+5) {
                    break;
                }
                if (stickList.get(j).getClose() > stickList.get(j+4).getClose()) {
                    up ++;
                } else {
                    break;
                }
            }
            for (int j = i; j < i + 9; j ++) {
                if (stickList.size() < j+5) {
                    break;
                }
                if (stickList.get(j).getClose() < stickList.get(j+4).getClose()) {
                    down ++;
                } else {
                    break;
                }
            }
            if (up >= 8) {
                stickList.get(i).setTdSequentialUp(up);
            }
            if (down >= 8) {
                stickList.get(i).setTdSequentialDown(down);
            }
        }
    }

    public void onTouchEvent(int action, Point point) {
        switch (action) {
            case MotionEvent.ACTION_DOWN:
                this.editLine = this.getEditLine(point);
                this.downPoint = point;
                break;
            case MotionEvent.ACTION_MOVE:
                if (this.editLine != null) {
                    if (editLine.getEidtPoint() == 1) {
                        this.downPoint = this.getVirtualPoint(editLine.getDate2(), editLine.getPrice2());
                    } else {
                        this.downPoint = this.getVirtualPoint(editLine.getDate1(), editLine.getPrice1());
                    }
                    int index = this.getTrendLineIndex(editLine);
                    if (index != -1) {
                        trendLines.remove(index);
                        activity.deleteTrendLine(editLine.getId());
                    }
                }
                this.currentPoint = point;
                break;
            case MotionEvent.ACTION_UP:
                if(downPoint != null && currentPoint != null) {
                    int length = this.getDistance(downPoint, currentPoint);
                    // 低于一定长度,或者超出日期表示范围,不画线
                    if (length > 120 && this.getDate(downPoint) != null &&
                            this.getDate(currentPoint) != null) {
                        this.saveTrendLine();
                    }
                    downPoint = null;
                    currentPoint = null;
                }
                break;
        }
    }

    /* 双击：删除线 */
    public void onDoubleTap(Point point) {
        if (trendLines.size() == 0) {
            return;
        }
        Map<Double, TrendLine> map = new HashMap<>();
        for (TrendLine trendLine : trendLines) {
            Point startPoint = this.getVirtualPoint(trendLine.getDate1(), trendLine.getPrice1());
            Point endPoint = this.getVirtualPoint(trendLine.getDate2(), trendLine.getPrice2());
            if (startPoint != null && endPoint != null) {
                double length = this.pointToLine(startPoint.x, startPoint.y, endPoint.x, endPoint.y, point.x, point.y);
                map.put(length, trendLine);
            }
        }
        if (map.size() == 0) {
            return;
        }
        List<Double> keys = new ArrayList<>(map.keySet());
        Collections.sort(keys);
        Double distance = keys.get(0);
        if (distance <= Const.EDIT_DISTANCE) {
            TrendLine deleteLine = map.get(distance);
            int index = this.getTrendLineIndex(deleteLine);
            if (index != -1) {
                trendLines.remove(index);
                activity.deleteTrendLine(deleteLine.getId());
            }
        }
    }

    // 删除线ID取得
    private int getTrendLineIndex(TrendLine editLine) {
        for (int i = 0; i < trendLines.size(); i++) {
            if (trendLines.get(i).getId() == editLine.getId()) {
                return i;
            }
        }
        return -1;
    }

    // 计算点之间的距离
    private int getDistance(Point startPoint, Point endPoint) {
        return (int) Math.sqrt(Math.pow(startPoint.x - endPoint.x, 2) +
                                Math.pow(startPoint.y - endPoint.y, 2));
    }

    // 计算触摸位置是否有可编辑的趋势线
    private TrendLine getEditLine(Point point) {
        if (trendLines.size() == 0) {
            return null;
        }
        Map<Integer, TrendLine> map1 = new HashMap<>();
        Map<Integer, TrendLine> map2 = new HashMap<>();
        for (TrendLine trendLine : trendLines) {
            Point startPoint = this.getVirtualPoint(trendLine.getDate1(), trendLine.getPrice1());
            Point endPoint = this.getVirtualPoint(trendLine.getDate2(), trendLine.getPrice2());
            if (startPoint != null && endPoint != null) {
                map1.put(this.getDistance(point, startPoint), trendLine);
                map2.put(this.getDistance(point, endPoint), trendLine);
            }
        }
        if (map1.size() == 0) {
            return null;
        }
        List<Integer> keys1 = new ArrayList<>(map1.keySet());
        List<Integer> keys2 = new ArrayList<>(map2.keySet());
        Collections.sort(keys1);
        Collections.sort(keys2);
        int distance1 = keys1.get(0);
        int distance2 = keys2.get(0);
        if (distance1 <= Const.EDIT_DISTANCE && distance1 <= distance2) {
            TrendLine trendLine = map1.get(distance1);
            trendLine.setEidtPoint(1);
            return trendLine;
        } else if(distance2 <= Const.EDIT_DISTANCE && distance2 <= distance1) {
            TrendLine trendLine = map2.get(distance2);
            trendLine.setEidtPoint(2);
            return trendLine;
        }
        return null;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        this.drawStick(canvas);
        if(activity.isAvg10Disp) {
            this.drawAverageLine(canvas, average10, Color.parseColor("#FFFF00"));
        }
        if(activity.isAvg20Disp) {
            this.drawAverageLine(canvas, average20, Color.parseColor("#FFA500"));
        }
        if(activity.isAvg60Disp) {
            this.drawAverageLine(canvas, average60, Color.parseColor("#00FFFF"));
        }
        this.drawText(canvas);
        this.drawTouchedLine(canvas);
        this.drawTrandLine(canvas);
        this.drawTDtdSequential(canvas);
    }

    /* ローソク足を描く */
    private void drawStick(Canvas canvas) {
        // 描画オブジェクト
        Paint paint = new Paint();
        for (int i = 0; i < maxStickCount; i++) {
            StickInfo stickInfo = stickList.get(i);
            // ローソク足を描く
            Rect stickReck = stickInfo.getStickRect();
            paint.setColor(stickInfo.getColor());
            paint.setStyle(Paint.Style.FILL);
            canvas.drawRect(stickReck, paint);
            // ローソク足の線を描く
            Rect lineRect = stickInfo.getLineRect();
            paint.setStrokeWidth(3);
            paint.setTextSize(Const.FONT_SIZE);
            canvas.drawLine(lineRect.left, lineRect.top, lineRect.right, lineRect.bottom, paint);
        }
    }

    /* 移動平均線を描く */
    private void drawAverageLine(Canvas canvas, List<Point> averageList, int color) {
        Path path = new Path();
        Paint paint = new Paint();
        paint.setColor(color);
        paint.setStrokeWidth(5);
        paint.setAntiAlias(true);
        paint.setStyle(Paint.Style.STROKE);
        // 計算した移動平均線のポイントの数が表示可能最大ローソク数により少ない場合
        int pointCount = maxStickCount > averageList.size() ? averageList.size() : maxStickCount;
        // ＊K線の数が移動平均線の計算最小限を下回る場合、描かないとする＊
        if (pointCount < 3) {
            // 可表示的点小于三个时，不画线
            return;
        }
        for (int i = 0; i < pointCount - 2; i += 2) {
            Point start = averageList.get(i);
            Point control = averageList.get(i + 1);
            Point next = averageList.get(i + 2);
            path.moveTo(start.x, start.y);
            path.quadTo(control.x, control.y, next.x, next.y);
        }
        Point lastPoint = averageList.get(pointCount - 1);
        path.lineTo(lastPoint.x, lastPoint.y);
        canvas.drawPath(path, paint);
    }

    /* 最高と最低、現在価格の水平線と価格を描く */
    private void drawText(Canvas canvas) {
        // 描画オブジェクト
        Paint paint = new Paint();
        paint.setStyle(Paint.Style.FILL);
        paint.setTextSize(Const.FONT_SIZE_MIDDLE);
        paint.setStrokeWidth(2);
        for (int i = 0; i < maxStickCount; i++) {
            StickInfo stickInfo = stickList.get(i);
            Rect stickReck = stickInfo.getStickRect();
            Rect lineRect = stickInfo.getLineRect();
            // 最新価格の水平線を描く
            if (stickList.indexOf(stickInfo) == 0) {
                // 最新価格の線を取得
                int line = stickInfo.getOpen() > stickInfo.getClose() ? stickReck.bottom : stickReck.top;
                // 黄色
                paint.setColor(Color.parseColor("#FFF68F"));
                // 不设置粗体
                paint.setFakeBoldText(false);
                // 最新価格の水平線
                canvas.drawLine(getStartXOfLine(stickInfo), line, stickReck.left, line, paint);
                // 文字粗体
                paint.setFakeBoldText(true);
                // 文字坐标
                int y = 0;
                // 判定文字是否超出屏幕显示
                if (line - Const.FONT_SIZE_HALF < 0) {
                    y = line + Const.FONT_SIZE_MIDDLE - 10;
                } else if (line + Const.FONT_SIZE_HALF > comnInfo.getStickHeight()) {
                    y = line;
                } else {
                    y = line + Const.FONT_SIZE_HALF - 5;
                }
                // 最新価格設定
                canvas.drawText(getLimitDigit(stickInfo.getClose()), getStartXOfText(stickInfo), y, paint);
            }
            // 最新の移動平均価格
            if (i == 0) {
                // 文字粗体
                paint.setFakeBoldText(true);
                // 10平均線の色
                paint.setColor(Color.parseColor("#FFFF00"));
                if (current10 != 0) {
                    // 10平均線設定
                    canvas.drawText("(10) " + getLimitDigit(current10), 0, Const.FONT_SIZE_MIDDLE * 2, paint);
                }
                // 20平均線の色
                paint.setColor(Color.parseColor("#FF9900"));
                if (current20 != 0) {
                    // 20平均線設定
                    canvas.drawText("(20) " + getLimitDigit(current20), 0, Const.FONT_SIZE_MIDDLE * 3, paint);
                }
                // 60平均線の色
                paint.setColor(Color.parseColor("#00FFFF"));
                if (current60 != 0) {
                    // 60平均線設定
                    canvas.drawText("(60) " + getLimitDigit(current60), 0, Const.FONT_SIZE_MIDDLE * 4, paint);
                }
            }
            // 最大値の線
            if (stickInfo.isMaxValueStick()) {
                // 白色
                paint.setColor(Color.WHITE);
                // 不设置粗体
                paint.setFakeBoldText(false);
                // 最大値の線
                canvas.drawLine(0, lineRect.top, comnInfo.getScreenWidth(), lineRect.top, paint);
                // 文字粗体
                paint.setFakeBoldText(true);
                // 最新価格設定
                canvas.drawText(getLimitDigit(stickInfo.getMaxValue()), 0, lineRect.top + Const.FONT_SIZE_MIDDLE, paint);
            }
            // 最小値の線
            if (stickInfo.isMinValueStick()) {
                // 白色
                paint.setColor(Color.WHITE);
                // 不设置粗体
                paint.setFakeBoldText(false);
                // 最小値の線
                canvas.drawLine(0, lineRect.bottom, comnInfo.getScreenWidth(), lineRect.bottom, paint);
                // 文字粗体
                paint.setFakeBoldText(true);
                // 最新価格設定
                canvas.drawText(getLimitDigit(stickInfo.getMinValue()), 0, lineRect.bottom - 8, paint);
            }
        }
    }

    /* タッチ箇所の価格と日付を描く */
    private void drawTouchedLine(Canvas canvas) {
        if (touchPoint == null) {
            return;
        }
        Paint paint = new Paint();
        paint.setStyle(Paint.Style.FILL);
        paint.setStrokeWidth(3);
        paint.setTextSize(Const.FONT_SIZE_MIDDLE);
        paint.setColor(Color.WHITE);
        if (touchPoint.y <= comnInfo.getStickHeight()) {
            if (currentValue != null) {
                // 价格线
                canvas.drawLine(getDigitLength(currentValue),
                        touchPoint.y, comnInfo.getScreenWidth(), touchPoint.y, paint);
                // 文字粗体
                paint.setFakeBoldText(true);
                // 文字坐标
                int y;
                // 判定文字是否超出屏幕显示
                if (touchPoint.y - Const.FONT_SIZE_HALF < 0) {
                    y = touchPoint.y + Const.FONT_SIZE_MIDDLE - 10;
                } else if (touchPoint.y + Const.FONT_SIZE_HALF > comnInfo.getStickHeight()) {
                    y = touchPoint.y;
                } else {
                    y = touchPoint.y + Const.FONT_SIZE_HALF - 5;
                }
                // 最新価格設定
                canvas.drawText(getLimitDigit(currentValue), 0, y, paint);
            } else {
                canvas.drawLine(0, touchPoint.y, comnInfo.getScreenWidth(), touchPoint.y, paint);
            }

        }
        if (touchedDate != null) {
            // 日期线
            canvas.drawLine(touchPoint.x, 0, touchPoint.x, comnInfo.getStickHeight(), paint);
        }
    }

    // 保存趋势线
    private void saveTrendLine() {
        TrendLine trendLine = new TrendLine();
        trendLine.setSymbol(activity.getSymbol());
        trendLine.setTimeFrame(activity.getTimeFrame());
        trendLine.setDate1(this.getDate(downPoint));
        trendLine.setPrice1(this.getPrice(downPoint));
        trendLine.setDate2(this.getDate(currentPoint));
        trendLine.setPrice2(this.getPrice(currentPoint));
        activity.saveTrendLine(trendLine);
    }

    private String getDate(Point point) {
        // 画面表示最大のローソクの数
        int maxStickCount = comnInfo.getMaxStickCount(stickList.size());
        for (int i = 0; i < maxStickCount; i++) {
            StickInfo stick = stickList.get(i);
            Rect stickRect = stick.getStickRect();
            if (point.x >= stickRect.left && point.x <=
                    (stickRect.right + StickInfo.BETWEEN_SPACE)) {
                return stick.getDate();
            }
        }
        return stickList.get(0).getDate();
    }

    private float getPrice(Point point) {
        StickInfo stick = stickList.get(0);
        return stick.getYValue(point.y);
    }

    /* 編集モードの線を描く */
    private void drawTrandLine(Canvas canvas) {
        Paint paint = new Paint();
        paint.setStyle(Paint.Style.FILL);
        paint.setStrokeWidth(3);
        paint.setColor(Color.WHITE);
        // 描画已经保存的线
        for (TrendLine trendLine : trendLines) {
            // 枠を超えたら、描かない。線のずれを防ぐため
            Point fromPoint = this.getVirtualPoint(trendLine.getDate1(),trendLine.getPrice1());
            // 枠を超えたら、描かない。線のずれを防ぐため
            Point toPoint = this.getVirtualPoint(trendLine.getDate2(),trendLine.getPrice2());
            if (fromPoint == null || toPoint == null) {
                continue;
            }
            canvas.drawLine(fromPoint.x, fromPoint.y, toPoint.x, toPoint.y, paint);
        }
        // 描画当前正在画的线
        if (downPoint == null || currentPoint == null) {
            return;
        }
        canvas.drawLine(downPoint.x, downPoint.y, currentPoint.x, currentPoint.y, paint);
    }

    /* TD序列の数字を描く */
    private void drawTDtdSequential(Canvas canvas) {
        // 描画オブジェクト
        Paint paint = new Paint();
        paint.setStyle(Paint.Style.FILL);
        paint.setTextSize(Const.FONT_SIZE_MIDDLE);
        paint.setStrokeWidth(2);
        // 文字粗体
        paint.setFakeBoldText(true);
        for (int i = 0; i < maxStickCount; i++) {
            StickInfo stickInfo = stickList.get(i);
            if (i == 60) {
                break;
            }
            if (stickInfo.getTdSequentialUp() == 0 && stickInfo.getTdSequentialDown() == 0) {
                continue;
            }
            // 文字坐标
            int x = stickInfo.getStickRect().left;
            if (stickInfo.getTdSequentialUp() >= 8) {
                // LIME
                paint.setColor(Color.parseColor("#00FF00"));
                // 最新価格設定
                canvas.drawText(String.valueOf(stickInfo.getTdSequentialUp()), x, comnInfo.getStickHeight() - 8, paint);
            }
            if (stickInfo.getTdSequentialDown() >= 8) {
                // PINK
                paint.setColor(Color.parseColor("#FFB6C1"));
                // 最新価格設定
                canvas.drawText(String.valueOf(stickInfo.getTdSequentialDown()), x, comnInfo.getStickHeight() - 8, paint);
            }
        }
    }

    private int getX(String date) {
        // 画面表示最大のローソクの数
        int maxStickCount = comnInfo.getMaxStickCount(stickList.size());
        for (int i = 0; i < maxStickCount; i++) {
            StickInfo stick = stickList.get(i);
            if (date.equals(stick.getDate())) {
                return stick.getLineRect().centerX();
            }
        }
        return -1;
    }

    /* 超过价格边界线,按照边界线计算 */
    private int getY(float price) {
        StickInfo stick = stickList.get(0);
        if (stick.getMaxValue() < price || stick.getMinValue() > price) {
            return -1;
        } else {
            return stick.getYPoint(price);
        }
    }

    /* 表示时,MACD区域内画线也可以表示 */
    private Point getVirtualPoint(String date, float price) {
        int x = this.getX(date);
        int y = this.getVirtualY(price);
        if (x == -1) {
            return null;
        } else {
            return new Point(x, y);
        }
    }

    /* 超过全屏幕表示范围,则不画线 */
    private int getVirtualY(float price) {
        StickInfo stick = stickList.get(0);
        int y = stick.getYPoint(price);
        if(y < 0) {
            y = 0;
        }
        if (y > comnInfo.getStickHeight()) {
            y = comnInfo.getStickHeight();
        }
        return y;
    }

    /* 单击事件，由Activity转发事件 */
    public void onSingleTapConfirmed(MotionEvent e) {
        if (activity.kLine.equals(Const.K_Line.Normal.toString())) {// K線図
            activity.kLine = Const.K_Line.Average.toString();
        } else {// 平均K線図
            activity.kLine = Const.K_Line.Normal.toString();
        }
        activity.showUI();
    }

    /* 取得数值的像素长度 */
    private int getDigitLength(float value) {
        String digit = getLimitDigit(value);
        if (digit.indexOf("－") == -1) {
            return digit.length() * 23;
        } else {
            return digit.length() * 23;
        }
    }

    /* 根据最新柱体，取得最新价格的表示位置 */
    private int getStartXOfText(StickInfo stickInfo) {
        int startX = (int)getStartXOfLine(stickInfo) - getDigitLength(stickInfo.getClose());
        if (startX < 0) {
            startX = 0;
        }
        return startX;
    }

    /* 根据最新柱体，取得最新价格线的表示位置 */
    private float getStartXOfLine(StickInfo stickInfo) {
        int startX = stickInfo.getStickRect().centerX() - 150;
        int digitLength = getDigitLength(stickInfo.getClose());
        if (startX - digitLength < 0) {
            startX = digitLength;
        }
        return startX;
    }

    /* 取得三位小数数值 */
    public String getLimitDigit(float value) {
        return digitFromat.format(value).replaceAll("-","－");
    }

    public void setTouchPoint(Point touchPoint) {
        this.touchPoint = touchPoint;
    }

    public void setTouchedDate(String date) {
        this.touchedDate = date;
    }

    // 根据触摸位置,将x轴调整为同位置K线的中心
    public StickInfo getRelatedStick(float x) {
        for (int i = 0; i < maxStickCount; i++) {
            StickInfo stickInfo = stickList.get(i);
            Rect stickReck = stickInfo.getStickRect();
            if (x >= stickReck.left && x <= stickReck.right + 2) {
                return stickInfo;
            }
        }
        return null;
    }

    public void setCurrentValue(Float currentValue) {
        this.currentValue = currentValue;
    }

    // 点到直线的最短距离的判断 点（x0,y0） 到由两点组成的线段（x1,y1） ,( x2,y2 )
    private double pointToLine(int x1, int y1, int x2, int y2, int x0,
                               int y0) {
        double space = 0;
        double a, b, c;
        a = lineSpace(x1, y1, x2, y2);// 线段的长度
        b = lineSpace(x1, y1, x0, y0);// (x1,y1)到点的距离
        c = lineSpace(x2, y2, x0, y0);// (x2,y2)到点的距离
        if (c <= 0.000001 || b <= 0.000001) {
            space = 0;
            return space;
        }
        if (a <= 0.000001) {
            space = b;
            return space;
        }
        if (c * c >= a * a + b * b) {
            space = b;
            return space;
        }
        if (b * b >= a * a + c * c) {
            space = c;
            return space;
        }
        double p = (a + b + c) / 2;// 半周长
        double s = Math.sqrt(p * (p - a) * (p - b) * (p - c));// 海伦公式求面积
        space = 2 * s / a;// 返回点到线的距离（利用三角形面积公式求高）
        return space;
    }

    // 计算两点之间的距离
    private double lineSpace(int x1, int y1, int x2, int y2) {
        double lineLength = 0;
        lineLength = Math.sqrt((x1 - x2) * (x1 - x2) + (y1 - y2)
                * (y1 - y2));
        return lineLength;
    }
}
