package com.finance.view.component;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Point;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.view.MotionEvent;
import android.view.View;

import com.finance.R;
import com.finance.common.Const;
import com.finance.view.ChartActivity;
import com.finance.view.component.macd.MacdDivergenceCalculator;
import com.finance.view.component.macd.MacdCalculator;
import com.finance.view.component.macd.KdjCalculator;
import com.finance.view.component.macd.MacdInfo;
import com.finance.view.component.macd.SignalLight;
import com.finance.view.component.stick.ComnInfo;
import com.finance.view.component.stick.ParabolicSarCalculator;
import com.finance.view.component.stick.StickInfo;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * Created by gu zihan on 08/30/2015.
 */
public class MacdView  extends View {
    private static final String[] INTRADAY_INTERVALS = {"4h", "1h", "15m", "5m"};
    private static final float CHART_TITLE_BASELINE = 45;

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
    private MacdDivergenceCalculator.Divergence[] macdDivergences;
    private float[] kdjK;
    private float[] kdjD;
    private float[] kdjJ;
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
    private final DecimalFormat cryptoPriceFormat = new DecimalFormat(
            "0.00", DecimalFormatSymbols.getInstance(Locale.US));
    private final DecimalFormat trumpPriceFormat = new DecimalFormat(
            "0.000", DecimalFormatSymbols.getInstance(Locale.US));
    private final DecimalFormat marketPriceFormat = new DecimalFormat(
            "#.###", DecimalFormatSymbols.getInstance(Locale.US));
    private SignalLight macdKdjSignal = SignalLight.RED;
    private SignalLight sarAverageKdjSignal = SignalLight.RED;

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
        this.kdjK = new float[sizeOfStick];
        this.kdjD = new float[sizeOfStick];
        this.kdjJ = new float[sizeOfStick];
        Arrays.fill(kdjK, Float.NaN);
        Arrays.fill(kdjD, Float.NaN);
        Arrays.fill(kdjJ, Float.NaN);
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
        this.calculateMacdDivergences();
        this.calculateKdj();
        this.calculateSignals();
    }

    private void calculateMacdDivergences() {
        float[] highs = new float[sizeOfStick];
        float[] lows = new float[sizeOfStick];
        float[] macd = new float[sizeOfStick];
        float[] signal = new float[sizeOfStick];
        for (int i = 0; i < sizeOfStick; i++) {
            highs[i] = stickList.get(i).getHigh();
            lows[i] = stickList.get(i).getLow();
            macd[i] = macdList.get(i).getMacd();
            signal[i] = macdList.get(i).getSignal();
        }
        int lastValidIndex = Math.max(-1, Math.min(start_Macd_Osci, sizeOfStick - 1));
        macdDivergences = MacdDivergenceCalculator.findDivergences(
                highs, lows, macd, signal, macd, maxStickCount, lastValidIndex);
    }

    private void calculateKdj() {
        if (sizeOfStick < KdjCalculator.PERIOD) {
            return;
        }
        int firstIndex = sizeOfStick - KdjCalculator.PERIOD;
        float previousK = KdjCalculator.INITIAL_VALUE;
        float previousD = KdjCalculator.INITIAL_VALUE;
        for (int i = firstIndex; i >= 0; i--) {
            float highest = Float.NEGATIVE_INFINITY;
            float lowest = Float.POSITIVE_INFINITY;
            for (int j = i; j < i + KdjCalculator.PERIOD; j++) {
                StickInfo stick = stickList.get(j);
                highest = Math.max(highest, stick.getHigh());
                lowest = Math.min(lowest, stick.getLow());
            }
            float rsv = KdjCalculator.calculateRsv(
                    highest, lowest, stickList.get(i).getClose());
            float k = KdjCalculator.calculateNextK(rsv, previousK);
            float d = KdjCalculator.calculateNextD(k, previousD);
            kdjK[i] = k;
            kdjD[i] = d;
            kdjJ[i] = KdjCalculator.calculateJ(k, d);
            previousK = k;
            previousD = d;
        }
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
        if (Const.Macd.KDJ.toString().equals(macdType)) {
            drawKdj(canvas);
        } else if (Const.Macd.Double.toString().equals(macdType)) {// ダブル線
            this.drawMacdOsci(canvas);
            this.drawMacdLine(canvas, Color.parseColor("#FFFF00"));
            this.drawSignalLine(canvas, Color.parseColor("#00FFFF"));
        } else {// シングル線
            this.drawMacd(canvas, Color.parseColor("#FF9900"));
            this.drawMacdDivergences(canvas);
            this.drawSignalLine(canvas, Color.parseColor("#00FFFF"));
        }
        this.drawText(canvas);
        drawSignals(canvas);
        if (Const.Macd.KDJ.toString().equals(macdType)) {
            drawKdjValues(canvas);
        }
        this.drawTouchedLine(canvas);
        drawIntradayControls(canvas);
        drawMacdModeButton(canvas);
    }

    private void calculateSignals() {
        boolean macdIncreasing = false;
        if (sizeOfStick >= EMA_SLOW + 1) {
            float latestMacd = macdList.get(0).getMacd();
            float previousMacd = macdList.get(1).getMacd();
            macdIncreasing = latestMacd > previousMacd;
        }
        boolean kAboveD = isKAboveD();
        macdKdjSignal = SignalLight.fromConditions(macdIncreasing, kAboveD);

        boolean sarBullish = !stickList.isEmpty()
                && ParabolicSarCalculator.calculate(stickList).get(0) < stickList.get(0).getClose();
        boolean priceAboveTenPeriodAverage = isPriceAboveTenPeriodAverage();
        boolean macdBullish = isMacdAboveSignal();
        sarAverageKdjSignal = SignalLight.fromConditions(
                sarBullish, macdBullish, priceAboveTenPeriodAverage);
    }

    private boolean isMacdAboveSignal() {
        return sizeOfStick >= EMA_SLOW + SIGNAL - 1
                && macdList.get(0).getMacd() > macdList.get(0).getSignal();
    }

    private boolean isKAboveD() {
        return sizeOfStick > 0
                && !Float.isNaN(kdjK[0])
                && !Float.isNaN(kdjD[0])
                && kdjK[0] > kdjD[0];
    }

    private boolean isPriceAboveTenPeriodAverage() {
        if (sizeOfStick < 10) {
            return false;
        }
        float closeSum = 0f;
        for (int i = 0; i < 10; i++) {
            closeSum += stickList.get(i).getClose();
        }
        return stickList.get(0).getClose() > closeSum / 10f;
    }

    private void drawSignals(Canvas canvas) {
        float density = getResources().getDisplayMetrics().density;
        float buttonWidth = 58 * density;
        float buttonHeight = 34 * density;
        float markerSize = getIndicatorMarkerSize();
        float markerGap = 12 * density;
        float secondSignalCenter = getSignalRightCenter(density, markerSize, markerGap);
        float firstSignalCenter = secondSignalCenter - markerSize - markerGap;
        float top = getSignalControlsTop(buttonHeight, markerSize);
        drawSignalMarker(canvas, firstSignalCenter - buttonWidth / 2, top,
                buttonWidth, buttonHeight, markerSize, macdKdjSignal);
        drawSignalMarker(canvas, secondSignalCenter - buttonWidth / 2, top,
                buttonWidth, buttonHeight,
                markerSize, sarAverageKdjSignal);
    }

    private float getSignalControlsTop(float buttonHeight, float markerSize) {
        float titleTop = CHART_TITLE_BASELINE + getTitleBounds().top;
        return titleTop - (buttonHeight - markerSize) / 2;
    }

    private float getIndicatorMarkerSize() {
        Rect priceBounds = getPriceTextBounds(Const.FONT_SIZE_ARROW);
        return priceBounds == null ? getTitleBounds().height() : priceBounds.height() + 20;
    }

    /* 价格文字高度与左侧指标标记一致 */
    private float getPriceTextSize() {
        Rect baseBounds = getPriceTextBounds(Const.FONT_SIZE_ARROW);
        if (baseBounds == null || baseBounds.height() == 0) {
            return Const.FONT_SIZE_ARROW;
        }
        return Const.FONT_SIZE_ARROW * getIndicatorMarkerSize() / baseBounds.height();
    }

    private Rect getPriceTextBounds() {
        return getPriceTextBounds(getPriceTextSize());
    }

    private Rect getTitleBounds() {
        Paint titlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        titlePaint.setTextSize(Const.FONT_SIZE_LARGE);
        Rect titleBounds = new Rect();
        String title = getChartTitle();
        titlePaint.getTextBounds(title, 0, title.length(), titleBounds);
        return titleBounds;
    }

    private Rect getPriceTextBounds(float textSize) {
        String price = getCurrentPriceText();
        if (price == null) {
            return null;
        }
        Paint pricePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        pricePaint.setTextSize(textSize);
        pricePaint.setFakeBoldText(true);
        Rect priceBounds = new Rect();
        pricePaint.getTextBounds(price, 0, price.length(), priceBounds);
        return priceBounds;
    }

    private float getSignalRightCenter(float density, float markerSize, float markerGap) {
        String price = getCurrentPriceText();
        if (price == null) {
            return getWidth() - 8 * density - 29 * density;
        }
        Paint pricePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        pricePaint.setTextSize(getPriceTextSize());
        pricePaint.setFakeBoldText(true);
        float priceWidth = pricePaint.measureText(price);
        float priceLeft = getWidth() - 8 * density - priceWidth;
        return priceLeft - markerGap - markerSize / 2;
    }

    private float getIntradayControlsRight(float density) {
        float buttonWidth = 58 * density;
        float markerSize = 26 * density;
        return getWidth() - 8 * density - buttonWidth / 2 + markerSize / 2
                + 5 * density;
    }

    private void drawSignalMarker(Canvas canvas, float left, float top,
                                  float buttonWidth, float buttonHeight, float shapeSize,
                                  SignalLight signal) {
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        float centerX = left + buttonWidth / 2;
        float centerY = top + buttonHeight / 2;
        switch (signal) {
            case GREEN:
                paint.setColor(Color.rgb(0, 230, 118));
                canvas.drawCircle(centerX, centerY, shapeSize / 2, paint);
                break;
            case YELLOW:
                paint.setColor(Color.rgb(255, 235, 59));
                canvas.drawRect(centerX - shapeSize / 2, centerY - shapeSize / 2,
                        centerX + shapeSize / 2, centerY + shapeSize / 2, paint);
                break;
            case RED:
                paint.setColor(Color.rgb(255, 82, 82));
                Path triangle = new Path();
                triangle.moveTo(centerX, centerY - shapeSize / 2);
                triangle.lineTo(centerX + shapeSize / 2, centerY + shapeSize / 2);
                triangle.lineTo(centerX - shapeSize / 2, centerY + shapeSize / 2);
                triangle.close();
                canvas.drawPath(triangle, paint);
                break;
        }
    }

    private void drawKdj(Canvas canvas) {
        if (sizeOfStick < KdjCalculator.PERIOD || maxStickCount == 0) {
            return;
        }
        float density = getResources().getDisplayMetrics().density;
        float top = Const.MACD_CHART_VERTICAL_INSET;
        float bottom = getHeight() - Const.MACD_CHART_VERTICAL_INSET;
        Paint guidePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        guidePaint.setColor(Color.rgb(70, 70, 70));
        guidePaint.setStrokeWidth(density);
        for (float level : new float[] {20f, 80f}) {
            float y = getKdjY(level, top, bottom);
            canvas.drawLine(0, y, getWidth(), y, guidePaint);
        }
        drawKdjLine(canvas, kdjK, Color.rgb(255, 193, 7), top, bottom);
        drawKdjLine(canvas, kdjD, Color.rgb(3, 218, 198), top, bottom);
        drawKdjLine(canvas, kdjJ, Color.rgb(255, 82, 82), top, bottom);
    }

    private void drawKdjLine(Canvas canvas, float[] values, int color,
                             float top, float bottom) {
        Path path = new Path();
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setColor(color);
        paint.setStrokeWidth(2.5f * getResources().getDisplayMetrics().density);
        paint.setStyle(Paint.Style.STROKE);
        boolean started = false;
        int pointCount = Math.min(maxStickCount, sizeOfStick);
        for (int i = 0; i < pointCount; i++) {
            if (Float.isNaN(values[i])) {
                continue;
            }
            float x = stickList.get(i).getLineRect().centerX();
            float y = getKdjY(values[i], top, bottom);
            if (started) {
                path.lineTo(x, y);
            } else {
                path.moveTo(x, y);
                started = true;
            }
        }
        if (started) {
            canvas.drawPath(path, paint);
        }
    }

    private float getKdjY(float value, float top, float bottom) {
        float clampedValue = Math.max(-20f, Math.min(120f, value));
        return bottom - (clampedValue + 20f) / 140f * (bottom - top);
    }

    private void drawKdjValues(Canvas canvas) {
        if (sizeOfStick == 0 || Float.isNaN(kdjK[0])) {
            return;
        }
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setTextSize(Const.FONT_SIZE_LARGE);
        paint.setTextAlign(Paint.Align.LEFT);
        paint.setFakeBoldText(true);
        float baseline = 45 + paint.getFontSpacing();
        String kValue = String.format(Locale.US, "K: %.1f", kdjK[0]);
        paint.setColor(Color.rgb(255, 193, 7));
        canvas.drawText(kValue, 0, baseline, paint);
        float nextX = paint.measureText(kValue) + 12;
        String dValue = String.format(Locale.US, "D: %.1f", kdjD[0]);
        paint.setColor(Color.rgb(3, 218, 198));
        canvas.drawText(dValue, nextX, baseline, paint);
        nextX += paint.measureText(dValue) + 12;
        String jValue = String.format(Locale.US, "J: %.1f", kdjJ[0]);
        paint.setColor(Color.rgb(255, 82, 82));
        canvas.drawText(jValue, nextX, baseline, paint);
    }

    private void drawIntradayControls(Canvas canvas) {
        if (!activity.isCryptoSymbol()) {
            return;
        }
        String[] intervals = INTRADAY_INTERVALS;
        float density = getResources().getDisplayMetrics().density;
        float buttonWidth = 58 * density;
        float buttonHeight = 34 * density;
        float gap = 6 * density;
        float right = getIntradayControlsRight(density);
        float bottom = getHeight() - 7 * density;
        float textSize = 14 * density;
        intervalButtonPaint.setTextSize(textSize);
        intervalButtonPaint.setTextAlign(Paint.Align.CENTER);
        for (int i = 0; i < intervals.length; i++) {
            float left = right - buttonWidth;
            float top = bottom - buttonHeight;
            boolean selected = intervals[i].equals(activity.getIntradayInterval());
            intervalButtonPaint.setStyle(Paint.Style.FILL);
            intervalButtonPaint.setColor(Color.TRANSPARENT);
            canvas.drawRoundRect(left, top, right, bottom, 10 * density,
                    10 * density, intervalButtonPaint);
            intervalButtonPaint.setStyle(Paint.Style.STROKE);
            intervalButtonPaint.setStrokeWidth((selected ? 2.5f : 1.2f) * density);
            intervalButtonPaint.setColor(selected ? Color.rgb(66, 165, 245)
                    : Color.rgb(96, 125, 139));
            canvas.drawRoundRect(left, top, right, bottom, 10 * density,
                    10 * density, intervalButtonPaint);
            intervalButtonPaint.setStyle(Paint.Style.FILL);
            intervalButtonPaint.setColor(Color.WHITE);
            Paint.FontMetrics fontMetrics = intervalButtonPaint.getFontMetrics();
            float textY = top + (buttonHeight - fontMetrics.ascent - fontMetrics.descent) / 2;
            canvas.drawText(intervals[i].toUpperCase(Locale.US), (left + right) / 2,
                    textY, intervalButtonPaint);
            right = left - gap;
        }
    }

    public boolean handleIntradayControlTap(MotionEvent event) {
        int[] location = new int[2];
        getLocationOnScreen(location);
        float x = event.getRawX() - location[0];
        float y = event.getRawY() - location[1];
        String[] intervals = INTRADAY_INTERVALS;
        float density = getResources().getDisplayMetrics().density;
        float buttonWidth = 58 * density;
        float buttonHeight = 34 * density;
        float signalButtonHeight = 34 * density;
        float gap = 6 * density;
        float markerSize = getIndicatorMarkerSize();
        float markerGap = 12 * density;
        float secondSignalCenter = getSignalRightCenter(density, markerSize, markerGap);
        float firstSignalCenter = secondSignalCenter - markerSize - markerGap;
        float firstSignalLeft = firstSignalCenter - markerSize / 2;
        float signalTop = getSignalControlsTop(signalButtonHeight, markerSize);
        float signalBottom = signalTop + signalButtonHeight;
        if (x >= firstSignalLeft && x <= secondSignalCenter + markerSize / 2
                && y >= signalTop && y <= signalBottom) {
            return true;
        }
        if (!Const.Macd.KDJ.toString().equals(macdType)
                && getMacdModeButtonRect().contains(x, y)) {
            activity.macd = Const.Macd.Double.toString().equals(macdType)
                    ? Const.Macd.Single.toString() : Const.Macd.Double.toString();
            activity.showUI();
            return true;
        }
        if (!activity.isCryptoSymbol()) {
            return false;
        }
        float right = getIntradayControlsRight(density);
        float bottom = getHeight() - 7 * density;
        float top = bottom - buttonHeight;
        for (String interval : intervals) {
            float left = right - buttonWidth;
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

    private void drawMacdDivergences(Canvas canvas) {
        if (macdDivergences == null) {
            return;
        }
        float density = getResources().getDisplayMetrics().density;
        float textSize = 12 * density;
        float gap = 12 * density;
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setTextSize(textSize);
        paint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        paint.setFakeBoldText(true);
        paint.setTextAlign(Paint.Align.CENTER);
        Rect textBounds = new Rect();

        int count = Math.min(maxStickCount, macdDivergences.length);
        for (int i = 0; i < count; i++) {
            MacdDivergenceCalculator.Divergence divergence = macdDivergences[i];
            MacdDivergenceCalculator.Type type = divergence.getType();
            if (type == MacdDivergenceCalculator.Type.NONE) {
                continue;
            }

            String label;
            float baseline;
            boolean isTop = type == MacdDivergenceCalculator.Type.TOP
                    || type == MacdDivergenceCalculator.Type.TOP_DEATH_CROSS;
            Point histogramPoint = getHistogramPivotPoint(i, isTop);
            if (isTop) {
                label = type == MacdDivergenceCalculator.Type.TOP_DEATH_CROSS
                        ? "死叉顶背离" : "顶背离";
            } else {
                label = type == MacdDivergenceCalculator.Type.BOTTOM_GOLDEN_CROSS
                        ? "金叉底背离" : "底背离";
            }
            paint.setColor(isTop ? Color.rgb(255, 82, 82) : Color.rgb(0, 230, 118));
            paint.getTextBounds(label, 0, label.length(), textBounds);
            baseline = isTop
                    ? histogramPoint.y - gap - textBounds.bottom
                    : histogramPoint.y + gap - textBounds.top;

            Point comparisonPoint = getHistogramPivotPoint(
                    divergence.getComparisonIndex(), isTop);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(3 * density);
            canvas.drawLine(comparisonPoint.x, comparisonPoint.y,
                    histogramPoint.x, histogramPoint.y, paint);
            float textCenterY = baseline + (textBounds.top + textBounds.bottom) / 2f;
            canvas.drawLine(histogramPoint.x, histogramPoint.y,
                    histogramPoint.x, textCenterY, paint);

            paint.setStyle(Paint.Style.FILL);
            canvas.drawText(label, histogramPoint.x, baseline, paint);
        }
    }

    private Point getHistogramPivotPoint(int index, boolean isTop) {
        Rect barRect = macdList.get(index).getMacdRect();
        return new Point(barRect.centerX(), isTop ? barRect.top : barRect.bottom);
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
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setColor(Color.WHITE);
        paint.setStrokeWidth(2);
        paint.setTextSize(Const.FONT_SIZE_LARGE);
        canvas.drawText(getChartTitle(), 0, CHART_TITLE_BASELINE, paint);
        drawCurrentPrice(canvas);
    }

    private void drawCurrentPrice(Canvas canvas) {
        String price = getCurrentPriceText();
        if (price == null) {
            return;
        }
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setTextSize(getPriceTextSize());
        paint.setFakeBoldText(true);
        paint.setTextAlign(Paint.Align.RIGHT);
        paint.setColor(stickList.get(0).getColor());
        float density = getResources().getDisplayMetrics().density;
        Rect priceBounds = getPriceTextBounds();
        float priceBaseline = CHART_TITLE_BASELINE + getTitleBounds().top - priceBounds.top;
        canvas.drawText(price, getWidth() - 8 * density, priceBaseline, paint);
    }

    private String getCurrentPriceText() {
        if (stickList == null || stickList.isEmpty()) {
            return null;
        }
        float close = stickList.get(0).getClose();
        if (Const.Symbol.trump.toString().equals(symbol)) {
            return trumpPriceFormat.format(close);
        }
        if (Const.Symbol.btc.toString().equals(symbol)
                || Const.Symbol.eth.toString().equals(symbol)) {
            return cryptoPriceFormat.format(close);
        }
        return marketPriceFormat.format(close);
    }

    private String getChartTitle() {
        String symbolText = "";
        String timeFrameText = "";
        String kLineText = "";
        if (Const.Symbol.btc.toString().equals(symbol)) {
            symbolText = replace(getResources().getString(R.string.symbol_btc));
        } else if (Const.Symbol.eth.toString().equals(symbol)) {
            symbolText = replace(getResources().getString(R.string.symbol_eth));
        } else if (Const.Symbol.trump.toString().equals(symbol)) {
            symbolText = replace(getResources().getString(R.string.symbol_trump));
        } else if (Const.Symbol.dow.toString().equals(symbol)) {
            symbolText = replace(getResources().getString(R.string.symbol_dow));
        } else if (Const.Symbol.sp500.toString().equals(symbol)) {
            symbolText = replace(getResources().getString(R.string.symbol_sp500));
        } else if (Const.Symbol.nasdaq.toString().equals(symbol)) {
            symbolText = replace(getResources().getString(R.string.symbol_nasdaq));
        } else if (Const.Symbol.nikkei.toString().equals(symbol)) {
            symbolText = replace(getResources().getString(R.string.symbol_nikkei));
        } else if (Const.Symbol.usdx.toString().equals(symbol)) {
            symbolText = replace(getResources().getString(R.string.symbol_dollor));
        } else if (Const.Symbol.usbond.toString().equals(symbol)) {
            symbolText = replace(getResources().getString(R.string.symbol_usbond));
        } else if (Const.Symbol.xauusd.toString().equals(symbol)) {
            symbolText = replace(getResources().getString(R.string.symbol_gold));
        } else if (Const.Symbol.xagusd.toString().equals(symbol)) {
            symbolText = replace(getResources().getString(R.string.symbol_silver));
        }
        if (activity.getIntradayInterval() != null) {
            timeFrameText = activity.getIntradayInterval().toUpperCase(Locale.US);
        } else if (Const.TimeFrame.d3.toString().equals(timeFrame)) {
            timeFrameText = replace(getResources().getString(R.string.timeframe_3day));
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
        return symbolText + "  " + timeFrameText + " " + kLineText;
    }

    /* 单击事件，由Activity转发事件 */
    public void onSingleTapConfirmed(MotionEvent e) {
        if (Const.Macd.KDJ.toString().equals(macdType)) {
            activity.macd = Const.Macd.Single.toString();
        } else {
            activity.macd = Const.Macd.KDJ.toString();
        }
        activity.showUI();
    }

    private RectF getMacdModeButtonRect() {
        float density = getResources().getDisplayMetrics().density;
        float left = 4 * density;
        float top = CHART_TITLE_BASELINE + 10 * density;
        return new RectF(left, top, left + 40 * density, top + 30 * density);
    }

    private void drawMacdModeButton(Canvas canvas) {
        if (Const.Macd.KDJ.toString().equals(macdType)) {
            return;
        }
        float density = getResources().getDisplayMetrics().density;
        RectF rect = getMacdModeButtonRect();
        intervalButtonPaint.setTextSize(14 * density);
        intervalButtonPaint.setTextAlign(Paint.Align.CENTER);
        intervalButtonPaint.setStyle(Paint.Style.STROKE);
        intervalButtonPaint.setStrokeWidth(1.2f * density);
        intervalButtonPaint.setColor(Color.rgb(96, 125, 139));
        canvas.drawRoundRect(rect, 10 * density, 10 * density, intervalButtonPaint);
        intervalButtonPaint.setStyle(Paint.Style.FILL);
        intervalButtonPaint.setColor(Color.WHITE);
        Paint.FontMetrics fontMetrics = intervalButtonPaint.getFontMetrics();
        float textY = rect.centerY() - (fontMetrics.ascent + fontMetrics.descent) / 2;
        String label = Const.Macd.Double.toString().equals(macdType) ? "D" : "S";
        canvas.drawText(label, rect.centerX(), textY, intervalButtonPaint);
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
            canvas.drawLine(touchPoint.x, 0, touchPoint.x, getHeight(), paint);
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
