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

import com.finance.R;
import com.finance.common.Const;
import com.finance.view.ChartActivity;
import com.finance.view.component.macd.MacdInfo;
import com.finance.view.component.macd.MacdCalculator;
import com.finance.view.component.stick.ComnInfo;
import com.finance.view.component.stick.StickInfo;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by gu zihan on 08/30/2015.
 */
public class MacdView  extends View {

    // アクティビティ
    private ChartActivity activity;
    // 短期指数平滑移動平均線(EMA)
    private final int EMA_FAST = 12;
    // 長期指数平滑移動平均線(EMA)
    private final int EMA_SLOW = 26;
    // MACDの指数平滑化移動平均（MACD＝短期EMA－長期EMA）
    private final int SIGNAL = 9;
    // ローソク線の情報
    private List<StickInfo> stickList;
    // ローソク線の情報
    private List<MacdInfo> macdList;
    // 最大表示ローソクの数
    private int maxStickCount;
    // EMA快线的计算开始位置
    private int start_Ema_Fast;
    // EMA慢线的计算开始位置
    private int start_Ema_Slow;
    // 双线中的MACD线的计算开始位置
    private int start_Macd;
    // 信号线的计算开始位置
    private int start_Signal;
    // 双线柱体的计算开始位置
    private int start_Macd_Osci;
    // 取得数据的柱体数量
    private int sizeOfStick;
    // 计算的柱体的终了位置（由左至右计算）
    private int endOfStick = 0;
    // 单线还是双线
    private String macdType;
    // 表示中の銘柄
    private String symbol;
    // 表示中のタイムフレーム
    private String timeFrame;
    // 表示中のK線種別
    private String kLine;
    // 共通情報
    private ComnInfo comnInfo;
    // タッチ箇所
    private Point touchPoint;
    // タッチ箇所の日付
    private String touchedDate;
    private final Paint intervalButtonPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    public MacdView(Context context) {
        super(context);
        super.setBackgroundColor(Color.BLACK);
        this.activity = (ChartActivity)context;
    }

    /* ローソク足のデータを設定し、MACD情報を自動計算させる */
    public void setData(ComnInfo comnInfo, List<StickInfo> stickList,
                        String symbol, String timeFrame, String macdType, String kLine) {
        this.stickList = stickList;
        this.macdList = new ArrayList<>();
        this.sizeOfStick = stickList.size();
        this.symbol = symbol;
        this.timeFrame = timeFrame;
        this.macdType = macdType;
        this.kLine = kLine;
        this.comnInfo = comnInfo;
        // MACDオブジェクト生成
        for (int i = 0; i < sizeOfStick; i ++) {
            MacdInfo macdInfo = new MacdInfo(comnInfo);
            macdInfo.setDate(stickList.get(i).getDate());
            macdList.add(macdInfo);
        }
        this.start_Ema_Fast = sizeOfStick - EMA_FAST;
        this.start_Ema_Slow = sizeOfStick - EMA_SLOW;
        this.start_Macd = sizeOfStick - EMA_SLOW;
        this.start_Signal = sizeOfStick - EMA_SLOW - SIGNAL + 1;
        this.start_Macd_Osci = sizeOfStick - EMA_SLOW - SIGNAL + 1;
        // 画面表示最大のローソクの数
        this.maxStickCount = comnInfo.getMaxStickCount(stickList.size());
        this.calculate();
    }

    /* MACDの自身情報を計算 */
    private void calculate() {
        this.calcEMA_Fast();
        this.calcEMA_Slow();
        this.calcMacd();
        this.calcSignal();
        this.calcMacd_osci();
        this.calcMaxValue();
        this.calcPosition();
    }

    /* 短期指数平滑移動平均線(EMA)計算 */
    private void calcEMA_Fast() {
        for (int i = start_Ema_Fast; i >= endOfStick; i--) {
            StickInfo stickInfo = stickList.get(i);
            MacdInfo macdInfo = macdList.get(i);
            // EMAが最初かどうかを判断
            if (i == start_Ema_Fast) {// 一番目
                float simpleAvg = getSimpleAvg_EMA(start_Ema_Fast, EMA_FAST);
                // 最初の日のEMAを単純移動平均で計算
                macdInfo.setEmaFast(simpleAvg);
            } else {// 二番目以降
                MacdInfo macdBefore = macdList.get(i + 1);
                float close = stickInfo.getClose();
                // 指数平滑移動平均計算
                float emaBefore = macdBefore.getEmaFast();
                float emaCurrent = getCurrentEMA(close, EMA_FAST, emaBefore);
                macdInfo.setEmaFast(emaCurrent);
            }
        }
    }

    /* 長期指数平滑移動平均線(EMA) */
    private void calcEMA_Slow() {
        for (int i = start_Ema_Slow; i >= endOfStick; i--) {
            StickInfo stickInfo = stickList.get(i);
            MacdInfo macdInfo = macdList.get(i);
            // EMAが最初かどうかを判断
            if (i == start_Ema_Slow) {// 一番目
                float simpleAvg = getSimpleAvg_EMA(start_Ema_Slow, EMA_SLOW);
                // 最初の日のEMAを単純移動平均で計算
                macdInfo.setEmaSlow(simpleAvg);
            } else {// 二番目以降
                MacdInfo macdBefore = macdList.get(i + 1);
                float close = stickInfo.getClose();
                // 指数平滑移動平均計算
                float emaBefore = macdBefore.getEmaSlow();
                float emaCurrent = getCurrentEMA(close, EMA_SLOW, emaBefore);
                macdInfo.setEmaSlow(emaCurrent);
            }
        }
    }

    /* MACD = EMA(FAST) - EMA(SLOW) */
    private void calcMacd() {
        for (int i = start_Macd; i >= endOfStick; i--) {
            MacdInfo macdInfo = macdList.get(i);
            float macd = MacdCalculator.calculateMacd(
                    macdInfo.getEmaFast(), macdInfo.getEmaSlow());
            macdInfo.setMacd(macd);
        }
    }

    /* シグナル = MACDの指数平滑化移動平均 */
    private void calcSignal() {
        for (int i = start_Signal; i >= endOfStick; i--) {
            MacdInfo macdInfo = macdList.get(i);
            // EMAが最初かどうかを判断
            if (i == start_Signal) {// 一番目
                // 最初の日のEMAを単純移動平均で計算
                macdInfo.setSignal(getSimpleAvg_Signal());
            } else {// 二番目以降
                MacdInfo macdBefore = macdList.get(i + 1);
                float macd = macdInfo.getMacd();
                // 指数平滑移動平均計算
                float signalBefore = macdBefore.getSignal();
                float signalCurrent = getCurrentEMA(macd, SIGNAL, signalBefore);
                macdInfo.setSignal(signalCurrent);
            }
        }
    }

    /* MACD柱体(MACD－シグナル) */
    private void calcMacd_osci() {
        for (int i = start_Macd_Osci; i >= endOfStick; i--) {
            MacdInfo macdInfo = macdList.get(i);
            float macd_osci = MacdCalculator.calculateHistogram(
                    macdInfo.getMacd(), macdInfo.getSignal());
            macdInfo.setMacd_osci(macd_osci);
        }
    }

    /* MACDとシグナル絶対最大値を取得 */
    private void calcMaxValue() {
        float absMaxValue = 0f;
        for (int i = start_Signal; i >= endOfStick; i--) {
            MacdInfo macdInfo = macdList.get(i);
            float macd = Math.abs(macdInfo.getMacd());
            float signal = Math.abs(macdInfo.getSignal());
            absMaxValue = Math.max(absMaxValue, Math.max(macd, signal));
        }
        for (int i = start_Signal; i >= endOfStick; i--) {
            MacdInfo macdInfo = macdList.get(i);
            macdInfo.setAbsMaxValue(absMaxValue);
        }
    }

    /* 位置情報を計算 */
    private void calcPosition() {
        for (int i = start_Signal; i >= endOfStick; i--) {
            StickInfo stickInfo = stickList.get(i);
            MacdInfo macdInfo = macdList.get(i);
            macdInfo.calculate(stickInfo);
        }
    }

    /* 終値の平均値を計算 */
    private float getSimpleAvg_EMA(int start, int period) {
        float sumValue = 0f;
        for (int i = sizeOfStick - 1; i >= start; i--) {
            sumValue = sumValue + stickList.get(i).getClose();
        }
        return sumValue / period;
    }

    /* MACDの平均値を計算 */
    private float getSimpleAvg_Signal() {
        float sumValue = 0f;
        for (int i = start_Ema_Slow; i >= start_Signal; i--) {
            sumValue = sumValue + macdList.get(i).getMacd();
        }
        return sumValue / SIGNAL;
    }

    /* 指数平滑移動平均計算 */
    private float getCurrentEMA(float close, int peroid, float emaBefore) {
        return MacdCalculator.calculateNextEma(close, peroid, emaBefore);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        // MACDタイプ判定
        if (Const.Macd.Double.toString().equals(macdType)) {// ダブル線
            this.drawMacdOsci(canvas);
            this.drawMacdLine(canvas, Color.parseColor("#FFFF00"));
            this.drawSignalLine(canvas, Color.parseColor("#00FFFF"));
        } else {// シングル線
            this.drawMacd(canvas, Color.parseColor("#FF9900"));
            this.drawSignalLine(canvas, Color.parseColor("#EE82EE"));
        }
        this.drawText(canvas);
        this.drawTouchedLine(canvas);
        drawIntradayControls(canvas);
    }

    private void drawIntradayControls(Canvas canvas) {
        if (!activity.isCryptoSymbol()) {
            return;
        }
        String[] intervals = {"4h", "1h", "5m"};
        float density = getResources().getDisplayMetrics().density;
        float buttonWidth = 58 * density;
        float buttonHeight = 34 * density;
        float gap = 6 * density;
        float right = getWidth() - 8 * density;
        float bottom = getHeight() - 7 * density;
        float textSize = 14 * density;
        intervalButtonPaint.setTextSize(textSize);
        intervalButtonPaint.setTextAlign(Paint.Align.CENTER);
        intervalButtonPaint.setStrokeWidth(1.2f * density);
        for (int i = 0; i < intervals.length; i++) {
            float left = right - buttonWidth;
            float top = bottom - buttonHeight;
            boolean selected = intervals[i].equals(activity.getIntradayInterval());
            intervalButtonPaint.setStyle(Paint.Style.FILL);
            intervalButtonPaint.setColor(selected ? Color.rgb(30, 136, 229)
                    : Color.rgb(38, 50, 65));
            canvas.drawRoundRect(left, top, right, bottom, 10 * density,
                    10 * density, intervalButtonPaint);
            intervalButtonPaint.setStyle(Paint.Style.STROKE);
            intervalButtonPaint.setColor(selected ? Color.rgb(144, 202, 249)
                    : Color.rgb(96, 125, 139));
            canvas.drawRoundRect(left, top, right, bottom, 10 * density,
                    10 * density, intervalButtonPaint);
            intervalButtonPaint.setStyle(Paint.Style.FILL);
            intervalButtonPaint.setColor(Color.WHITE);
            Paint.FontMetrics fontMetrics = intervalButtonPaint.getFontMetrics();
            float textY = top + (buttonHeight - fontMetrics.ascent - fontMetrics.descent) / 2;
            canvas.drawText(intervals[i], (left + right) / 2, textY, intervalButtonPaint);
            right = left - gap;
        }
    }

    public boolean handleIntradayControlTap(MotionEvent event) {
        if (!activity.isCryptoSymbol()) {
            return false;
        }
        int[] location = new int[2];
        getLocationOnScreen(location);
        float x = event.getRawX() - location[0];
        float y = event.getRawY() - location[1];
        String[] intervals = {"4h", "1h", "5m"};
        float density = getResources().getDisplayMetrics().density;
        float buttonWidth = 58 * density;
        float buttonHeight = 34 * density;
        float gap = 6 * density;
        float right = getWidth() - 8 * density;
        float bottom = getHeight() - 7 * density;
        for (String interval : intervals) {
            float left = right - buttonWidth;
            float top = bottom - buttonHeight;
            if (x >= left && x <= right && y >= top && y <= bottom) {
                activity.selectBinanceInterval(interval);
                return true;
            }
            right = left - gap;
        }
        return false;
    }

    /* MACD双线柱体 */
    private void drawMacdOsci(Canvas canvas) {
        // 描画オブジェクト
        Paint paint = new Paint();
        for (int i = 0; i < maxStickCount; i ++) {
            MacdInfo macdInfo = macdList.get(i);
            // ローソク足を描く
            Rect macdOsciRect = macdInfo.getMacdOsciRect();
            paint.setColor(macdInfo.getMacdOsciColor());
            paint.setStyle(Paint.Style.FILL);
            canvas.drawRect(macdOsciRect, paint);
        }
    }

    /* MACD单线柱体 */
    private void drawMacd(Canvas canvas, int color) {
        // 描画オブジェクト
        Paint paint = new Paint();
        for (int i = 0; i < maxStickCount; i ++) {
            MacdInfo macdInfo = macdList.get(i);
            // ローソク足を描く
            Rect macdRect = macdInfo.getMacdRect();
            paint.setColor(color);
            paint.setStyle(Paint.Style.FILL);
            canvas.drawRect(macdRect, paint);
        }
    }

    /* MACD双线中的MACD线（即单线中的柱体边缘线） */
    private void drawMacdLine(Canvas canvas, int color) {
        Path path = new Path();
        Paint paint = new Paint();
        paint.setColor(color);
        paint.setStrokeWidth(5);
        paint.setAntiAlias(true);
        paint.setStyle(Paint.Style.STROKE);
        // 計算した移動平均線のポイントの数が表示可能最大ローソク数により少ない場合
        int pointCount = Math.min(maxStickCount, start_Macd_Osci);
        // ＊K線の数がMACDの計算最小限を下回る場合、描かないとする＊
        if (pointCount <= 0) {
            return;
        }
        for (int i = 0; i < pointCount - 2; i += 2) {
            Point start = macdList.get(i).getMacdPoint();
            Point control = macdList.get(i + 1).getMacdPoint();
            Point next = macdList.get(i + 2).getMacdPoint();
            path.moveTo(start.x, start.y);
            path.quadTo(control.x, control.y, next.x, next.y);
        }
        Point lastPoint = macdList.get(pointCount - 1).getMacdPoint();
        path.lineTo(lastPoint.x, lastPoint.y);
        canvas.drawPath(path, paint);
    }

    /* MACD单线以及双线中信号线 */
    private void drawSignalLine(Canvas canvas, int color) {
        Path path = new Path();
        Paint paint = new Paint();
        paint.setColor(color);
        paint.setStrokeWidth(5);
        paint.setAntiAlias(true);
        paint.setStyle(Paint.Style.STROKE);
        // 計算した移動平均線のポイントの数が表示可能最大ローソク数により少ない場合
        int pointCount = Math.min(maxStickCount, start_Macd_Osci);
        // ＊K線の数がMACDの計算最小限を下回る場合、描かないとする＊
        if (pointCount <= 0) {
            return;
        }
        for (int i = 0; i < pointCount - 2; i += 2) {
            Point start = macdList.get(i).getSignalPoint();
            Point control = macdList.get(i + 1).getSignalPoint();
            Point next = macdList.get(i + 2).getSignalPoint();
            path.moveTo(start.x, start.y);
            path.quadTo(control.x, control.y, next.x, next.y);
        }
        Point lastPoint = macdList.get(pointCount - 1).getSignalPoint();
        path.lineTo(lastPoint.x, lastPoint.y);
        canvas.drawPath(path, paint);
    }

    /* 銘柄、タイムフレーム文字を表示 */
    private void drawText(Canvas canvas) {
        // 描画オブジェクト
        Paint paint = new Paint();
        paint.setColor(Color.WHITE);
        paint.setStrokeWidth(2);
        paint.setTextSize(Const.FONT_SIZE_LARGE);
        String symbolText = "";
        String timeFrameText = "";
        String kLineText = "";

        if(Const.Symbol.btc.toString().equals(symbol)) {
            symbolText = replace(getResources().getString(R.string.symbol_btc));
        } else if(Const.Symbol.eth.toString().equals(symbol)) {
            symbolText = replace(getResources().getString(R.string.symbol_eth));
        } else if (Const.Symbol.dow.toString().equals(symbol)) {
            symbolText = replace(getResources().getString(R.string.symbol_dow));
        } else if(Const.Symbol.sp500.toString().equals(symbol)) {
            symbolText = replace(getResources().getString(R.string.symbol_sp500));
        } else if(Const.Symbol.nasdaq.toString().equals(symbol)) {
            symbolText = replace(getResources().getString(R.string.symbol_nasdaq));
        } else if(Const.Symbol.nikkei.toString().equals(symbol)) {
            symbolText = replace(getResources().getString(R.string.symbol_nikkei));
        } else if(Const.Symbol.usdx.toString().equals(symbol)) {
            symbolText = replace(getResources().getString(R.string.symbol_dollor));
        } else if(Const.Symbol.usbond.toString().equals(symbol)) {
            symbolText = replace(getResources().getString(R.string.symbol_usbond));
        } else if(Const.Symbol.xauusd.toString().equals(symbol)) {
            symbolText = replace(getResources().getString(R.string.symbol_gold));
        } else if(Const.Symbol.xagusd.toString().equals(symbol)) {
            symbolText = replace(getResources().getString(R.string.symbol_silver));
        }

        if (activity.getIntradayInterval() != null) {
            timeFrameText = activity.getIntradayInterval();
        } else if (Const.TimeFrame.d.toString().equals(timeFrame)) {
            timeFrameText = replace(getResources().getString(R.string.timeframe_day));
        } else if (Const.TimeFrame.w.toString().equals(timeFrame)) {
            timeFrameText = replace(getResources().getString(R.string.timeframe_week));
        } else if (Const.TimeFrame.m.toString().equals(timeFrame)) {
            timeFrameText = replace(getResources().getString(R.string.timeframe_month));
        } else if (Const.TimeFrame.q.toString().equals(timeFrame)) {
            timeFrameText = replace(getResources().getString(R.string.timeframe_quarter));
        } else if (Const.TimeFrame.y.toString().equals(timeFrame)) {
            timeFrameText = replace(getResources().getString(R.string.timeframe_year));
        }
        if (Const.K_Line.Normal.toString().equals(kLine)) {
            kLineText = "(KA)";
        } else {
            kLineText = "(HA)";
        }
        // 最新価格設定
        canvas.drawText(symbolText + "  " + timeFrameText + " " + kLineText, 0, 45, paint);
    }

    /* 单击事件，由Activity转发事件 */
    public void onSingleTapConfirmed(MotionEvent e) {
        if (Const.Macd.Double.toString().equals(macdType)) {// ダブル線
            activity.macd = Const.Macd.Single.toString();
        } else {
            activity.macd = Const.Macd.Double.toString();
        }
        activity.showUI();
    }

    /* タッチ箇所の価格と日付を描く */
    private void drawTouchedLine(Canvas canvas) {
        if (touchPoint == null) {
            return;
        }
        Paint paint = new Paint();
        paint.setStyle(Paint.Style.FILL);
        paint.setStrokeWidth(2);
        paint.setColor(Color.WHITE);
        paint.setTextSize(Const.FONT_SIZE);
        if (touchedDate != null) {
            // 日期线
            canvas.drawLine(touchPoint.x, 0, touchPoint.x, comnInfo.getMacdHeight() - Const.FONT_SIZE, paint);
            String date = DateView.formatDateForDisplay(touchedDate,
                    activity.getIntradayInterval() != null);
            float dateX = touchPoint.x - 92;
            if (activity.getIntradayInterval() != null) {
                dateX = touchPoint.x - paint.measureText(date) / 2 - 4;
            }
            canvas.drawText(date, dateX, comnInfo.getMacdHeight(), paint);
        }
    }

    /* 去掉换行符 */
    private String replace(String in) {
        return in.replaceAll("\\n","");
    }

    public void setTouchPoint(Point touchPoint) {
        this.touchPoint = touchPoint;
    }

    public void setTouchedDate(String date) {
        this.touchedDate = date;
    }
}
