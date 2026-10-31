package com.finance.view.component;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Point;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.os.SystemClock;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.PopupWindow;

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
import com.finance.view.component.trendline.TrendLine;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.text.SimpleDateFormat;
import java.util.TimeZone;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Created by gu zihan on 08/30/2015.
 */
public class MacdView  extends View {
    private static final String[] CRYPTO_TIMEFRAMES = {
            "5m", "15m", "1h", "4h", "d", "3d", "w", "m", "q", "y"
    };
    private static final String[] STOCK_TIMEFRAMES = {"d", "w", "m", "q", "y"};
    private static final String[] FNG_TIMEFRAMES = {"d", "3d", "w", "m", "y"};
    private static final int VISIBLE_CRYPTO_TIMEFRAMES = 5;
    private static final float CHART_TITLE_BASELINE = 45;
    private static final String DIVERGENCE_PREFS = "divergence_lines";
    private static final float DIVERGENCE_HIT_RADIUS_DP = 24;
    private static final String DIVERGENCE_DELETED = "deleted";

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
    // 正在拖动的背离线端点
    private String draggingDivergenceKey;
    private boolean draggingDivergenceStart;
    private Point draggingDivergencePoint;
    private Bitmap loupeBitmap;
    private Canvas loupeCanvas;
    private static final String KDJ_LINE_SUFFIX = "@kdj";
    private static final int KDJ_NEAR_LINE_PX = 100;
    private static final int KDJ_MIN_LENGTH_PX = 120;
    private List<TrendLine> kdjLines = new ArrayList<>();
    private TrendLine kdjEditLine;
    private Point kdjDownPoint;
    private Point kdjCurrentPoint;
    private boolean kdjTouchActive;
    private long kdjTapCreatedTime;
    private int kdjTapCreatedLineCount = -1;

    private static final class DivergenceLine {
        String key;
        boolean isTop;
        String label;
        // 端点的索引可能在屏幕之外（超出范围的按等间距推算）
        int startIndex;
        int endIndex;
        String startDate;
        String endDate;
        float startMacd;
        float endMacd;
        Point startPoint;
        Point endPoint;
    }
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
    private PopupWindow symbolPopupWindow;
    private int firstVisibleCryptoTimeframe = 2;
    private String lastSelectedCryptoTimeframe;
    private float cryptoTimeframeScrollDistance;
    private final DecimalFormat cryptoPriceFormat = new DecimalFormat(
            "0.00", DecimalFormatSymbols.getInstance(Locale.US));
    private final DecimalFormat trumpPriceFormat = new DecimalFormat(
            "0.000", DecimalFormatSymbols.getInstance(Locale.US));
    private final DecimalFormat marketPriceFormat = new DecimalFormat(
            "#.###", DecimalFormatSymbols.getInstance(Locale.US));
    private SignalLight macdKdjSignal = SignalLight.RED;
    private SignalLight sarAverageKdjSignal = SignalLight.RED;
    private SignalLight fngTurnDownSignal = SignalLight.GREEN;
    private SignalLight fngDeadCrossSignal = SignalLight.GREEN;

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
        drawContent(canvas);
        drawLoupe(canvas);
    }

    /* 拖动背离线端点时，在手指上方显示放大镜（允许画到本视图上方的K线区域） */
    private void drawLoupe(Canvas canvas) {
        Point loupePoint = draggingDivergencePoint != null && draggingDivergenceKey != null
                ? draggingDivergencePoint : getKdjLoupePoint();
        if (loupePoint == null) {
            return;
        }
        float density = getResources().getDisplayMetrics().density;
        float radius = 60 * density;
        float gap = 40 * density;
        float scale = 2.5f;
        float fx = loupePoint.x;
        float fy = loupePoint.y;
        float cx = Math.max(radius, Math.min(fx, getWidth() - radius));
        float cy = fy - radius - gap;
        int size = Math.max(1, Math.round(radius * 2));
        if (loupeBitmap == null || loupeBitmap.getWidth() != size) {
            loupeBitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
            loupeCanvas = new Canvas(loupeBitmap);
        }
        loupeBitmap.eraseColor(Color.BLACK);
        loupeCanvas.save();
        loupeCanvas.translate(radius, radius);
        loupeCanvas.scale(scale, scale);
        loupeCanvas.translate(-fx, -fy);
        drawContent(loupeCanvas);
        loupeCanvas.restore();
        canvas.save();
        Path circle = new Path();
        circle.addCircle(cx, cy, radius, Path.Direction.CW);
        canvas.clipPath(circle);
        canvas.drawBitmap(loupeBitmap, cx - radius, cy - radius, null);
        canvas.restore();
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(3);
        paint.setColor(Color.DKGRAY);
        canvas.drawCircle(cx, cy, radius, paint);
        paint.setStrokeWidth(1);
        paint.setColor(Color.RED);
        canvas.drawLine(cx - radius, cy, cx + radius, cy, paint);
        canvas.drawLine(cx, cy - radius, cx, cy + radius, paint);
    }

    private void invalidateWithLoupe() {
        invalidate();
        if (getParent() instanceof View) {
            ((View) getParent()).invalidate();
        }
    }

    private void drawContent(Canvas canvas) {
        if (activity.isFearGreedSymbol()) {
            Paint background = new Paint();
            background.setColor(Const.FEAR_GREED_BACKGROUND);
            canvas.drawRect(0, 0, getWidth(), getHeight(), background);
        }
        if (Const.Macd.KDJ.toString().equals(macdType)) {
            drawKdj(canvas);
            drawKdjLines(canvas);
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
    }

    private void calculateSignals() {
        calculateFearGreedSignals();
        macdKdjSignal = fngTurnDownSignal;
        sarAverageKdjSignal = fngDeadCrossSignal;
    }

    /* 恐慌指数画面：两个都成立为危险色，与加密货币的指标方向相反 */
    private void calculateFearGreedSignals() {
        fngTurnDownSignal = SignalLight.GREEN;
        fngDeadCrossSignal = SignalLight.GREEN;
        if (sizeOfStick < 2) {
            return;
        }
        boolean kdjTurnDown = !Float.isNaN(kdjK[0]) && !Float.isNaN(kdjK[1])
                && kdjK[0] < kdjK[1];
        boolean indexDown = sizeOfStick >= EMA_SLOW + 1
                && macdList.get(0).getMacd() < macdList.get(1).getMacd();
        int turnDown = (kdjTurnDown ? 1 : 0) + (indexDown ? 1 : 0);
        fngTurnDownSignal = turnDown == 2 ? SignalLight.RED
                : turnDown == 1 ? SignalLight.YELLOW : SignalLight.GREEN;
        boolean kdjDead = !Float.isNaN(kdjK[0]) && !Float.isNaN(kdjD[0])
                && kdjK[0] < kdjD[0];
        boolean macdDead = sizeOfStick >= EMA_SLOW + SIGNAL - 1
                && macdList.get(0).getMacd() < macdList.get(0).getSignal();
        int dead = (kdjDead ? 1 : 0) + (macdDead ? 1 : 0);
        fngDeadCrossSignal = dead == 2 ? SignalLight.RED
                : dead == 1 ? SignalLight.YELLOW : SignalLight.GREEN;
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
        if (Const.Symbol.fng.toString().equals(symbol)) {
            drawSignalMarker(canvas, firstSignalCenter - buttonWidth / 2, top,
                    buttonWidth, buttonHeight, markerSize, fngTurnDownSignal);
            drawSignalMarker(canvas, secondSignalCenter - buttonWidth / 2, top,
                    buttonWidth, buttonHeight, markerSize, fngDeadCrossSignal);
            drawFearGreedGauge(canvas, firstSignalCenter - markerSize / 2 - markerGap,
                    top + buttonHeight / 2, markerSize, density);
            return;
        }
        drawSignalMarker(canvas, firstSignalCenter - buttonWidth / 2, top,
                buttonWidth, buttonHeight, markerSize, macdKdjSignal);
        drawSignalMarker(canvas, secondSignalCenter - buttonWidth / 2, top,
                buttonWidth, buttonHeight,
                markerSize, sarAverageKdjSignal);
        drawFearGreedGauge(canvas, firstSignalCenter - markerSize / 2 - markerGap,
                top + buttonHeight / 2, markerSize, density);
    }

    /* 贪婪恐慌指数仪表：画在两个指标标记的左侧，仅加密货币显示 */
    private void drawFearGreedGauge(Canvas canvas, float right, float centerY,
                                    float markerSize, float density) {
        boolean fngSymbol = Const.Symbol.fng.toString().equals(symbol);
        int value = fngSymbol && stickList != null && !stickList.isEmpty()
                ? Math.max(0, Math.min(100, Math.round(stickList.get(0).getClose())))
                : com.finance.model.net.FearGreedIndex.getValue();
        if (!(activity.isCryptoSymbol() || fngSymbol) || value < 0) {
            return;
        }
        float titleRight = 8 * density + getTitleBounds().width();
        float width = Math.min(markerSize * 2.2f, right - titleRight - 8 * density);
        if (width < markerSize) {
            return;
        }
        float centerX = right - width / 2;
        // 半圆总高约为宽度的一半，垂直居中于标记
        float bottom = centerY + width / 4;
        drawGaugeShape(canvas, centerX, bottom, width, value);
    }

    private void drawGaugeShape(Canvas canvas, float centerX, float bottom, float width, int value) {
        float stroke = width * 0.12f;
        float radius = (width - stroke) / 2;
        RectF oval = new RectF(centerX - radius, bottom - radius,
                centerX + radius, bottom + radius);
        float arcCenterY = bottom;
        Paint arc = new Paint(Paint.ANTI_ALIAS_FLAG);
        arc.setStyle(Paint.Style.STROKE);
        arc.setStrokeWidth(stroke);
        arc.setShader(new android.graphics.SweepGradient(centerX, arcCenterY,
                new int[] {Color.rgb(46, 229, 157), Color.rgb(255, 77, 106),
                        Color.rgb(255, 152, 67), Color.rgb(253, 216, 53),
                        Color.rgb(46, 200, 140), Color.rgb(46, 229, 157)},
                new float[] {0f, 0.5f, 0.625f, 0.75f, 0.875f, 1f}));
        canvas.drawArc(oval, 180, 180, false, arc);

        // 指针：沿弧内侧，指向对应数值的位置
        double angle = Math.toRadians(180 + value * 1.8);
        float pointerRadius = radius - stroke * 1.2f;
        float tipRadius = pointerRadius + stroke * 0.7f;
        float baseHalf = stroke * 0.45f;
        float cos = (float) Math.cos(angle);
        float sin = (float) Math.sin(angle);
        float bx = centerX + cos * pointerRadius;
        float by = arcCenterY + sin * pointerRadius;
        Path pointer = new Path();
        pointer.moveTo(centerX + cos * tipRadius, arcCenterY + sin * tipRadius);
        pointer.lineTo(bx - sin * baseHalf, by + cos * baseHalf);
        pointer.lineTo(bx + sin * baseHalf, by - cos * baseHalf);
        pointer.close();
        Paint pointerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        pointerPaint.setColor(Color.rgb(140, 150, 170));
        canvas.drawPath(pointer, pointerPaint);

        Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
        text.setColor(activity.isFearGreedSymbol() ? Color.BLACK : Color.rgb(240, 242, 246));
        text.setTextAlign(Paint.Align.CENTER);
        text.setFakeBoldText(true);
        text.setTextSize(width * 0.34f);
        canvas.drawText(String.valueOf(value), centerX, bottom, text);
    }

    private float getSignalControlsTop(float buttonHeight, float markerSize) {
        float titleTop = getTitleBaseline() + getTitleBounds().top;
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

    /* 标题字号与右侧价格一致；没有价格时用默认大字号 */
    private float getTitleTextSize() {
        return (getCurrentPriceText() == null ? Const.FONT_SIZE_ARROW : getPriceTextSize())
                - 1.6f * getResources().getDisplayMetrics().density;
    }

    private float getBtcTitleTextSize() {
        return getTitleTextSize();
    }

    /* 标题变大后基线随之下移，避免顶部被裁掉 */
    private float getTitleBaseline() {
        return Math.max(CHART_TITLE_BASELINE, 10 - getTitleBounds().top);
    }

    private Rect getTitleBounds() {
        Paint titlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        titlePaint.setTextSize(getTitleTextSize());
        titlePaint.setFakeBoldText(true);
        Rect titleBounds = new Rect();
        String title = getSymbolTitle();
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
        guidePaint.setColor(Color.DKGRAY);
        guidePaint.setStrokeWidth(2);
        guidePaint.setStyle(Paint.Style.STROKE);
        guidePaint.setPathEffect(new DashPathEffect(new float[] {10, 8}, 0));
        for (float level : new float[] {20f, 80f}) {
            float y = getKdjY(level, top, bottom);
            Path guide = new Path();
            guide.moveTo(0, y);
            guide.lineTo(getWidth(), y);
            canvas.drawPath(guide, guidePaint);
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
        float baseline = getTitleBaseline() + 20 + paint.getFontSpacing();
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

    private boolean showsTimeframeControls() {
        return true;
    }

    private String[] getTimeframes() {
        if (activity.isFearGreedSymbol()) {
            return FNG_TIMEFRAMES;
        }
        return activity.isCryptoSymbol() ? CRYPTO_TIMEFRAMES : STOCK_TIMEFRAMES;
    }

    private void drawIntradayControls(Canvas canvas) {
        if (!showsTimeframeControls()) {
            return;
        }
        final String[] timeframes = getTimeframes();
        float density = getResources().getDisplayMetrics().density;
        float gap = 6 * density;
        float buttonWidth = getCryptoTimeframeButtonWidth(density, gap);
        float buttonHeight = 34 * density * 0.88f;
        float controlsLeft = getIntradayControlsRight(density)
                - VISIBLE_CRYPTO_TIMEFRAMES * buttonWidth
                - (VISIBLE_CRYPTO_TIMEFRAMES - 1) * gap;
        float bottom = getHeight() - 7 * density;
        float textSize = 16 * density * 0.88f;
        intervalButtonPaint.setTextSize(textSize);
        intervalButtonPaint.setTextAlign(Paint.Align.CENTER);
        ensureSelectedTimeframeVisible();
        int first = activity.isCryptoSymbol() ? firstVisibleCryptoTimeframe : 0;
        int lastVisible = first + VISIBLE_CRYPTO_TIMEFRAMES - 1;
        for (int k = first; k <= lastVisible; k++) {
            int i = k % timeframes.length;
            float left = controlsLeft;
            float right = left + buttonWidth;
            float top = bottom - buttonHeight;
            String timeframe = timeframes[i];
            boolean selected = isCryptoTimeframeSelected(timeframe);
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
            intervalButtonPaint.setColor(activity.isFearGreedSymbol() ? Color.BLACK : Color.WHITE);
            Paint.FontMetrics fontMetrics = intervalButtonPaint.getFontMetrics();
            float textY = top + (buttonHeight - fontMetrics.ascent - fontMetrics.descent) / 2;
            canvas.drawText(getCryptoTimeframeLabel(timeframe), (left + right) / 2,
                    textY, intervalButtonPaint);
            controlsLeft = right + gap;
        }
    }

    public boolean handleIntradayControlTap(MotionEvent event) {
        int[] location = new int[2];
        getLocationOnScreen(location);
        float x = event.getRawX() - location[0];
        float y = event.getRawY() - location[1];
        if (!showsTimeframeControls()) {
            return false;
        }
        final String[] timeframes = getTimeframes();
        float density = getResources().getDisplayMetrics().density;
        float gap = 6 * density;
        float buttonWidth = getCryptoTimeframeButtonWidth(density, gap);
        float buttonHeight = 34 * density * 0.88f;
        float controlsLeft = getIntradayControlsRight(density)
                - VISIBLE_CRYPTO_TIMEFRAMES * buttonWidth
                - (VISIBLE_CRYPTO_TIMEFRAMES - 1) * gap;
        float bottom = getHeight() - 7 * density;
        float top = bottom - buttonHeight;
        int first = activity.isCryptoSymbol() ? firstVisibleCryptoTimeframe : 0;
        int lastVisible = first + VISIBLE_CRYPTO_TIMEFRAMES - 1;
        for (int k = first; k <= lastVisible; k++) {
            int i = k % timeframes.length;
            float left = controlsLeft;
            float right = left + buttonWidth;
            if (x >= left && x <= right && y >= top && y <= bottom) {
                activity.selectCryptoTimeframe(timeframes[i]);
                return true;
            }
            controlsLeft = right + gap;
        }
        return false;
    }

    public boolean handleCryptoTimeframeScroll(MotionEvent startEvent, float distanceX) {
        if (!activity.isCryptoSymbol()) {
            return false;
        }
        int[] location = new int[2];
        getLocationOnScreen(location);
        float x = startEvent.getRawX() - location[0];
        float y = startEvent.getRawY() - location[1];
        float density = getResources().getDisplayMetrics().density;
        float gap = 6 * density;
        float buttonWidth = getCryptoTimeframeButtonWidth(density, gap);
        float bottom = getHeight() - 7 * density;
        float top = bottom - 34 * density * 0.88f;
        float right = getIntradayControlsRight(density);
        float left = right - VISIBLE_CRYPTO_TIMEFRAMES * buttonWidth
                - (VISIBLE_CRYPTO_TIMEFRAMES - 1) * gap;
        if (x < left || x > right || y < top || y > bottom) {
            return false;
        }

        cryptoTimeframeScrollDistance += distanceX;
        float scrollStep = buttonWidth + gap;
        int count = CRYPTO_TIMEFRAMES.length;
        while (cryptoTimeframeScrollDistance >= scrollStep) {
            firstVisibleCryptoTimeframe = (firstVisibleCryptoTimeframe + 1) % count;
            cryptoTimeframeScrollDistance -= scrollStep;
        }
        while (cryptoTimeframeScrollDistance <= -scrollStep) {
            firstVisibleCryptoTimeframe = (firstVisibleCryptoTimeframe + count - 1) % count;
            cryptoTimeframeScrollDistance += scrollStep;
        }
        invalidate();
        return true;
    }

    public boolean isCryptoTimeframeControlTouch(MotionEvent event) {
        if (!showsTimeframeControls()) {
            return false;
        }
        int[] location = new int[2];
        getLocationOnScreen(location);
        float x = event.getRawX() - location[0];
        float y = event.getRawY() - location[1];
        float density = getResources().getDisplayMetrics().density;
        float gap = 6 * density;
        float buttonWidth = getCryptoTimeframeButtonWidth(density, gap);
        float right = getIntradayControlsRight(density);
        float left = right - VISIBLE_CRYPTO_TIMEFRAMES * buttonWidth
                - (VISIBLE_CRYPTO_TIMEFRAMES - 1) * gap;
        float bottom = getHeight() - 7 * density;
        float top = bottom - 34 * density * 0.88f;
        return x >= left && x <= right && y >= top && y <= bottom;
    }

    /* 选中的周期不在显示范围内时（如上下滑动切换），自动滑动周期栏使其可见 */
    private void ensureSelectedTimeframeVisible() {
        if (!activity.isCryptoSymbol()) {
            return;
        }
        String lastSelected = lastSelectedCryptoTimeframe;
        int selectedIndex = -1;
        for (int i = 0; i < CRYPTO_TIMEFRAMES.length; i++) {
            if (isCryptoTimeframeSelected(CRYPTO_TIMEFRAMES[i])) {
                selectedIndex = i;
                break;
            }
        }
        if (selectedIndex < 0) {
            return;
        }
        lastSelectedCryptoTimeframe = CRYPTO_TIMEFRAMES[selectedIndex];
        // 只在选中的周期发生变化时调整，不干扰用户手动滑动周期栏
        if (CRYPTO_TIMEFRAMES[selectedIndex].equals(lastSelected)) {
            return;
        }
        int count = CRYPTO_TIMEFRAMES.length;
        int offset = ((selectedIndex - firstVisibleCryptoTimeframe) % count + count) % count;
        if (offset >= VISIBLE_CRYPTO_TIMEFRAMES) {
            // 取较近的方向滑动：向右滑到选中项成为最右，或向左滑到选中项成为最左
            if (offset - VISIBLE_CRYPTO_TIMEFRAMES + 1 <= count - offset) {
                firstVisibleCryptoTimeframe = (firstVisibleCryptoTimeframe
                        + offset - VISIBLE_CRYPTO_TIMEFRAMES + 1) % count;
            } else {
                firstVisibleCryptoTimeframe = selectedIndex;
            }
        }
    }

    private float getCryptoTimeframeButtonWidth(float density, float gap) {
        float right = getIntradayControlsRight(density);
        float editButtonRight = 20 + 58 * density;
        float minimumLeft = editButtonRight + gap;
        float maxWidth = (right - minimumLeft
                - (VISIBLE_CRYPTO_TIMEFRAMES - 1) * gap) / VISIBLE_CRYPTO_TIMEFRAMES;
        return Math.max(0, Math.min(58 * density * 0.88f, maxWidth));
    }

    private boolean isCryptoTimeframeSelected(String timeframe) {
        if ("5m".equals(timeframe) || "15m".equals(timeframe)
                || "1h".equals(timeframe) || "4h".equals(timeframe)) {
            return timeframe.equals(activity.getIntradayInterval());
        }
        return activity.getIntradayInterval() == null && timeframe.equals(timeFrame);
    }

    private String getCryptoTimeframeLabel(String timeframe) {
        switch (timeframe) {
            case "5m":
            case "15m":
            case "1h":
            case "4h":
                return timeframe;
            case "d":
                return "1D";
            case "3d":
                return "3D";
            case "w":
                return "1W";
            case "m":
                return "1M";
            case "q":
                return "3M";
            case "y":
                return "1Y";
            default:
                throw new IllegalArgumentException("Unsupported timeframe: " + timeframe);
        }
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

        for (DivergenceLine line : buildDivergenceLines()) {
            boolean isTop = line.isTop;
            Point startPoint = line.startPoint;
            Point endPoint = line.endPoint;
            if (line.key.equals(draggingDivergenceKey) && draggingDivergencePoint != null) {
                if (draggingDivergenceStart) {
                    startPoint = draggingDivergencePoint;
                } else {
                    endPoint = draggingDivergencePoint;
                }
            }
            paint.setColor(isTop ? Color.rgb(255, 82, 82) : Color.rgb(0, 230, 118));
            paint.getTextBounds(line.label, 0, line.label.length(), textBounds);
            float baseline = isTop
                    ? endPoint.y - gap - textBounds.bottom
                    : endPoint.y + gap - textBounds.top;

            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(3 * density);
            canvas.drawLine(startPoint.x, startPoint.y, endPoint.x, endPoint.y, paint);

            paint.setStyle(Paint.Style.FILL);
            canvas.drawText(line.label, endPoint.x, baseline, paint);
        }
    }

    /* 自动计算的背离线，叠加用户手动调整过的端点 */
    private List<DivergenceLine> buildDivergenceLines() {
        List<DivergenceLine> lines = new ArrayList<>();
        if (macdDivergences == null) {
            return lines;
        }
        SharedPreferences prefs = getContext()
                .getSharedPreferences(DIVERGENCE_PREFS, Context.MODE_PRIVATE);
        Map<String, Integer> indexByDate = new HashMap<>();
        for (int i = 0; i < macdList.size(); i++) {
            indexByDate.put(macdList.get(i).getDate(), i);
        }
        int count = Math.min(maxStickCount, macdDivergences.length);
        for (int i = 0; i < count; i++) {
            MacdDivergenceCalculator.Divergence divergence = macdDivergences[i];
            MacdDivergenceCalculator.Type type = divergence.getType();
            if (type == MacdDivergenceCalculator.Type.NONE) {
                continue;
            }
            DivergenceLine line = new DivergenceLine();
            line.key = symbol + "|" + timeFrame + "|" + macdList.get(i).getDate();
            line.isTop = type == MacdDivergenceCalculator.Type.TOP
                    || type == MacdDivergenceCalculator.Type.TOP_DEATH_CROSS;
            if (line.isTop) {
                line.label = type == MacdDivergenceCalculator.Type.TOP_DEATH_CROSS
                        ? "死叉顶背离" : "顶背离";
            } else {
                line.label = type == MacdDivergenceCalculator.Type.BOTTOM_GOLDEN_CROSS
                        ? "金叉底背离" : "底背离";
            }
            line.startIndex = divergence.getComparisonIndex();
            line.endIndex = i;
            line.startDate = macdList.get(line.startIndex).getDate();
            line.endDate = macdList.get(i).getDate();
            line.startMacd = macdList.get(line.startIndex).getMacd();
            line.endMacd = macdList.get(i).getMacd();
            int referenceIndex = i;
            String saved = prefs.getString(line.key, null);
            if (DIVERGENCE_DELETED.equals(saved)) {
                continue;
            }
            if (saved != null) {
                String[] fields = saved.split("\\|", -1);
                try {
                    Integer start = indexByDate.get(fields[0]);
                    Integer end = indexByDate.get(fields[1]);
                    boolean startVisible = start != null && start < maxStickCount;
                    boolean endVisible = end != null && end < maxStickCount;
                    int span = Integer.parseInt(fields[4]);
                    if (!startVisible && !endVisible) {
                        // 两端都已滑出屏幕
                        continue;
                    }
                    line.startDate = fields[0];
                    line.endDate = fields[1];
                    line.startMacd = Float.parseFloat(fields[2]);
                    line.endMacd = Float.parseFloat(fields[3]);
                    if (startVisible) {
                        line.startIndex = start;
                        line.endIndex = endVisible ? end : start - span;
                        referenceIndex = start;
                    } else {
                        line.endIndex = end;
                        line.startIndex = end + span;
                        referenceIndex = end;
                    }
                } catch (RuntimeException e) {
                    // 保存的数据不可用时，恢复自动计算的位置
                    line.startIndex = divergence.getComparisonIndex();
                    line.endIndex = i;
                    line.startDate = macdList.get(line.startIndex).getDate();
                    line.endDate = macdList.get(i).getDate();
                    line.startMacd = macdList.get(line.startIndex).getMacd();
                    line.endMacd = macdList.get(i).getMacd();
                    referenceIndex = i;
                }
            }
            resolveDivergencePoints(line, referenceIndex);
            lines.add(line);
        }
        return lines;
    }

    /* 屏幕之外的端点按K线等间距推算位置，纵向位置按保存的MACD值推算 */
    private void resolveDivergencePoints(DivergenceLine line, int referenceIndex) {
        Rect referenceRect = macdList.get(referenceIndex).getMacdRect();
        MacdInfo referenceInfo = macdList.get(referenceIndex);
        int stickWidth = comnInfo.getStickWidth();
        line.startPoint = new Point(
                referenceRect.centerX() - (line.startIndex - referenceIndex) * stickWidth,
                getPivotY(referenceInfo, line.startMacd, line.isTop));
        line.endPoint = new Point(
                referenceRect.centerX() - (line.endIndex - referenceIndex) * stickWidth,
                getPivotY(referenceInfo, line.endMacd, line.isTop));
    }

    private int getPivotY(MacdInfo info, float macd, boolean isTop) {
        boolean useValue = isTop ? macd >= 0f : macd < 0f;
        return info.getYPoint(useValue ? macd : 0f);
    }

    /* 编辑模式：拖动背离线端点重画。返回true表示事件已由背离线处理 */
    public boolean handleDivergenceEdit(int action, Point point) {
        if (!Const.Macd.Single.toString().equals(macdType) || macdList == null
                || comnInfo == null) {
            return false;
        }
        float density = getResources().getDisplayMetrics().density;
        float x = point.x;
        float y = point.y - comnInfo.getStickHeight();
        if (action == MotionEvent.ACTION_DOWN) {
            float radius = DIVERGENCE_HIT_RADIUS_DP * density;
            float best = radius;
            draggingDivergenceKey = null;
            for (DivergenceLine line : buildDivergenceLines()) {
                Point startPoint = line.startPoint;
                Point endPoint = line.endPoint;
                float startDistance = (float) Math.hypot(startPoint.x - x, startPoint.y - y);
                float endDistance = (float) Math.hypot(endPoint.x - x, endPoint.y - y);
                if (startDistance <= best) {
                    best = startDistance;
                    draggingDivergenceKey = line.key;
                    draggingDivergenceStart = true;
                }
                if (endDistance < best) {
                    best = endDistance;
                    draggingDivergenceKey = line.key;
                    draggingDivergenceStart = false;
                }
            }
            if (draggingDivergenceKey == null) {
                return false;
            }
            draggingDivergencePoint = new Point((int) x, (int) y);
            invalidateWithLoupe();
            return true;
        }
        if (draggingDivergenceKey == null) {
            return false;
        }
        draggingDivergencePoint = new Point((int) x, (int) y);
        if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
            if (action == MotionEvent.ACTION_UP) {
                saveDraggedDivergence((int) x);
            }
            draggingDivergenceKey = null;
            draggingDivergencePoint = null;
        }
        invalidateWithLoupe();
        return true;
    }

    /* 编辑模式：双击背离线删除。返回true表示命中并已删除 */
    public boolean deleteDivergenceAt(Point point) {
        if (!Const.Macd.Single.toString().equals(macdType) || macdList == null
                || comnInfo == null) {
            return false;
        }
        float density = getResources().getDisplayMetrics().density;
        float radius = DIVERGENCE_HIT_RADIUS_DP * density;
        float x = point.x;
        float y = point.y - comnInfo.getStickHeight();
        DivergenceLine hit = null;
        float best = radius;
        for (DivergenceLine line : buildDivergenceLines()) {
            Point a = line.startPoint;
            Point b = line.endPoint;
            float distance = distanceToSegment(x, y, a.x, a.y, b.x, b.y);
            if (distance <= best) {
                best = distance;
                hit = line;
            }
        }
        draggingDivergenceKey = null;
        draggingDivergencePoint = null;
        if (hit == null) {
            return false;
        }
        getContext().getSharedPreferences(DIVERGENCE_PREFS, Context.MODE_PRIVATE)
                .edit().putString(hit.key, DIVERGENCE_DELETED).apply();
        invalidate();
        return true;
    }

    public void setKdjLines(List<TrendLine> lines) {
        this.kdjLines = lines == null ? new ArrayList<>() : lines;
    }

    private boolean isKdjEditable() {
        return Const.Macd.KDJ.toString().equals(macdType) && stickList != null
                && !stickList.isEmpty() && comnInfo != null && maxStickCount > 0
                && getHeight() > 0;
    }

    private float kdjTop() {
        return Const.MACD_CHART_VERTICAL_INSET;
    }

    private float kdjBottom() {
        return getHeight() - Const.MACD_CHART_VERTICAL_INSET;
    }

    private float kdjValueAt(float y) {
        float value = (kdjBottom() - y) / (kdjBottom() - kdjTop()) * 140f - 20f;
        return Math.max(-20f, Math.min(120f, value));
    }

    private long parseKdjTime(String date) {
        try {
            SimpleDateFormat format = new SimpleDateFormat(
                    date.length() == 8 ? "yyyyMMdd" : "yyyy-MM-dd HH:mm", Locale.US);
            format.setTimeZone(TimeZone.getTimeZone("UTC"));
            return format.parse(date).getTime();
        } catch (Exception e) {
            return Long.MIN_VALUE;
        }
    }

    /* 日期对应的X坐标：画面内取K线中心；画面外按K线间距或时间比例外推 */
    private int kdjXOf(String date) {
        if (date == null) {
            return Integer.MIN_VALUE;
        }
        int visible = Math.min(maxStickCount, stickList.size());
        for (int i = 0; i < visible; i++) {
            if (date.equals(stickList.get(i).getDate())) {
                return stickList.get(i).getLineRect().centerX();
            }
        }
        if (visible < 2) {
            return Integer.MIN_VALUE;
        }
        int x0 = stickList.get(0).getLineRect().centerX();
        int xN = stickList.get(visible - 1).getLineRect().centerX();
        for (int i = visible; i < stickList.size(); i++) {
            if (date.equals(stickList.get(i).getDate())) {
                return Math.round(x0 - i * (float) (x0 - xN) / (visible - 1));
            }
        }
        long t = parseKdjTime(date);
        long t0 = parseKdjTime(stickList.get(0).getDate());
        long tN = parseKdjTime(stickList.get(visible - 1).getDate());
        if (t == Long.MIN_VALUE || t0 == Long.MIN_VALUE || tN == Long.MIN_VALUE || t0 == tN) {
            return Integer.MIN_VALUE;
        }
        return (int) Math.round(xN + (double) (t - tN) * (x0 - xN) / (t0 - tN));
    }

    /* X坐标对应的日期，与kdjXOf互为逆运算 */
    private String kdjDateAt(int x) {
        int visible = Math.min(maxStickCount, stickList.size());
        StickInfo newest = stickList.get(0);
        if (visible < 2) {
            return newest.getDate();
        }
        int x0 = newest.getLineRect().centerX();
        int xN = stickList.get(visible - 1).getLineRect().centerX();
        if (x0 == xN) {
            return newest.getDate();
        }
        float index = (x0 - x) * (float) (visible - 1) / (x0 - xN);
        int rounded = Math.round(index);
        if (rounded >= 0 && rounded < stickList.size()) {
            return stickList.get(rounded).getDate();
        }
        long t0 = parseKdjTime(newest.getDate());
        long tN = parseKdjTime(stickList.get(visible - 1).getDate());
        if (t0 == Long.MIN_VALUE || tN == Long.MIN_VALUE || t0 == tN) {
            return newest.getDate();
        }
        long t = Math.round(t0 - index * (double) (t0 - tN) / (visible - 1));
        SimpleDateFormat format = new SimpleDateFormat(
                newest.getDate().length() == 8 ? "yyyyMMdd" : "yyyy-MM-dd HH:mm", Locale.US);
        format.setTimeZone(TimeZone.getTimeZone("UTC"));
        return format.format(new java.util.Date(t));
    }

    private Point kdjPoint(String date, float value) {
        int x = kdjXOf(date);
        if (x == Integer.MIN_VALUE) {
            return null;
        }
        return new Point(x, Math.round(getKdjY(value, kdjTop(), kdjBottom())));
    }

    private double kdjLineDistance(TrendLine line, float x, float y) {
        Point p1 = kdjPoint(line.getDate1(), line.getPrice1());
        Point p2 = kdjPoint(line.getDate2(), line.getPrice2());
        if (p1 == null || p2 == null) {
            return Double.MAX_VALUE;
        }
        if (line.getType() == TrendLine.TYPE_HORIZONTAL) {
            return Math.abs(y - p1.y);
        }
        Point end = line.getType() == TrendLine.TYPE_RAY ? rayEnd(p1, p2) : p2;
        return distanceToSegment(x, y, p1.x, p1.y, end.x, end.y);
    }

    /* 射线从起点经过第二点延伸到很远处（超出部分由画布裁剪） */
    private Point rayEnd(Point p1, Point p2) {
        double dx = p2.x - p1.x;
        double dy = p2.y - p1.y;
        double length = Math.hypot(dx, dy);
        if (length == 0) {
            return p2;
        }
        double scale = 20000 / length;
        return new Point((int) (p1.x + dx * scale), (int) (p1.y + dy * scale));
    }

    private TrendLine findKdjEditLine(float x, float y) {
        TrendLine best = null;
        float bestDistance = Const.EDIT_DISTANCE;
        for (TrendLine line : kdjLines) {
            Point p1 = kdjPoint(line.getDate1(), line.getPrice1());
            Point p2 = kdjPoint(line.getDate2(), line.getPrice2());
            if (p1 == null || p2 == null) {
                continue;
            }
            if (line.getType() == TrendLine.TYPE_HORIZONTAL) {
                float distance = Math.abs(y - p1.y);
                if (distance <= bestDistance) {
                    bestDistance = distance;
                    best = line;
                    line.setEidtPoint(1);
                }
                continue;
            }
            float d1 = (float) Math.hypot(p1.x - x, p1.y - y);
            float d2 = (float) Math.hypot(p2.x - x, p2.y - y);
            if (d1 <= bestDistance) {
                bestDistance = d1;
                best = line;
                line.setEidtPoint(1);
            }
            if (d2 < bestDistance) {
                bestDistance = d2;
                best = line;
                line.setEidtPoint(2);
            }
        }
        return best;
    }

    /* 编辑模式：在KDJ区域画水平线、射线、线段，并可拖动端点。返回true表示事件已处理 */
    public boolean handleKdjLineEdit(int action, Point point) {
        if (!isKdjEditable()) {
            return false;
        }
        float x = point.x;
        float y = point.y - comnInfo.getStickHeight();
        if (action == MotionEvent.ACTION_DOWN) {
            kdjTouchActive = y >= 0;
            if (!kdjTouchActive) {
                return false;
            }
            kdjEditLine = findKdjEditLine(x, y);
            kdjDownPoint = new Point((int) x, (int) y);
            kdjCurrentPoint = null;
            invalidateWithLoupe();
            return true;
        }
        if (!kdjTouchActive) {
            return false;
        }
        if (action == MotionEvent.ACTION_MOVE) {
            if (kdjEditLine != null && kdjEditLine.getType() != TrendLine.TYPE_HORIZONTAL) {
                Point fixed = kdjEditLine.getEidtPoint() == 1
                        ? kdjPoint(kdjEditLine.getDate2(), kdjEditLine.getPrice2())
                        : kdjPoint(kdjEditLine.getDate1(), kdjEditLine.getPrice1());
                if (fixed != null) {
                    kdjDownPoint = fixed;
                }
            }
            kdjCurrentPoint = new Point((int) x, (int) y);
        } else {
            if (action == MotionEvent.ACTION_UP) {
                kdjCurrentPoint = new Point((int) x, (int) y);
                finishKdjLine();
            }
            kdjTouchActive = false;
            kdjEditLine = null;
            kdjDownPoint = null;
            kdjCurrentPoint = null;
        }
        invalidateWithLoupe();
        return true;
    }

    private void finishKdjLine() {
        if (kdjDownPoint == null || kdjCurrentPoint == null && !isHorizontalDraft()) {
            return;
        }
        Point current = kdjCurrentPoint != null ? kdjCurrentPoint : kdjDownPoint;
        if (kdjEditLine != null) {
            TrendLine edit = kdjEditLine;
            if (edit.getType() == TrendLine.TYPE_HORIZONTAL) {
                float value = kdjValueAt(current.y);
                activity.deleteTrendLine(edit.getId());
                saveKdjLine(edit.getType(), edit.getColor(),
                        edit.getDate1(), value, edit.getDate2(), value);
            } else if (distance(kdjDownPoint, current) > KDJ_MIN_LENGTH_PX) {
                activity.deleteTrendLine(edit.getId());
                if (edit.getEidtPoint() == 1) {
                    saveKdjLine(edit.getType(), edit.getColor(),
                            kdjDateAt(current.x), kdjValueAt(current.y),
                            edit.getDate2(), edit.getPrice2());
                } else {
                    saveKdjLine(edit.getType(), edit.getColor(),
                            edit.getDate1(), edit.getPrice1(),
                            kdjDateAt(current.x), kdjValueAt(current.y));
                }
            }
            return;
        }
        int type = activity.getTrendLineType();
        if (!isKdjSupportedType(type)) {
            return;
        }
        if (type == TrendLine.TYPE_HORIZONTAL) {
            float value = kdjValueAt(current.y);
            String date = kdjDateAt(current.x);
            saveKdjLine(type, activity.getTrendLineColor(), date, value, date, value);
            if (distance(kdjDownPoint, current) <= KDJ_MIN_LENGTH_PX) {
                kdjTapCreatedTime = SystemClock.uptimeMillis();
                kdjTapCreatedLineCount = kdjLines.size();
            }
        } else if (type != TrendLine.TYPE_HORIZONTAL
                && distance(kdjDownPoint, current) > KDJ_MIN_LENGTH_PX) {
            if (type != TrendLine.TYPE_RAY) {
                type = TrendLine.TYPE_SEGMENT;
            }
            saveKdjLine(type, activity.getTrendLineColor(),
                    kdjDateAt(kdjDownPoint.x), kdjValueAt(kdjDownPoint.y),
                    kdjDateAt(current.x), kdjValueAt(current.y));
        }
    }

    private static boolean isKdjSupportedType(int type) {
        return type == TrendLine.TYPE_SEGMENT || type == TrendLine.TYPE_RAY
                || type == TrendLine.TYPE_HORIZONTAL;
    }

    private boolean isHorizontalDraft() {
        return kdjEditLine != null ? kdjEditLine.getType() == TrendLine.TYPE_HORIZONTAL
                : activity.getTrendLineType() == TrendLine.TYPE_HORIZONTAL;
    }

    private static double distance(Point a, Point b) {
        return Math.hypot(a.x - b.x, a.y - b.y);
    }

    private void saveKdjLine(int type, int color, String date1, float value1,
                             String date2, float value2) {
        TrendLine line = new TrendLine();
        line.setSymbol(activity.getSymbol());
        line.setTimeFrame(activity.getTrendLineTimeFrame() + KDJ_LINE_SUFFIX);
        line.setType(type);
        line.setColor(color);
        line.setDate1(date1);
        line.setPrice1(value1);
        line.setDate2(date2);
        line.setPrice2(value2);
        activity.saveTrendLine(line);
    }

    /* 编辑模式：双击KDJ区域的线删除。返回true表示命中并已删除 */
    public boolean deleteKdjLineAt(Point point) {
        if (!isKdjEditable()) {
            return false;
        }
        float x = point.x;
        float y = point.y - comnInfo.getStickHeight();
        if (y < 0) {
            return false;
        }
        // 取消第二下按下后正在进行的画线/编辑，避免抬起时又保存
        kdjTouchActive = false;
        kdjEditLine = null;
        kdjDownPoint = null;
        kdjCurrentPoint = null;
        // 双击的第一下点击可能刚画出一条水平线，先撤销它
        boolean undone = false;
        if (SystemClock.uptimeMillis() - kdjTapCreatedTime <= 500
                && kdjTapCreatedLineCount >= 0 && kdjLines.size() > kdjTapCreatedLineCount) {
            TrendLine created = kdjLines.remove(kdjLines.size() - 1);
            activity.deleteTrendLine(created.getId());
            undone = true;
        }
        kdjTapCreatedLineCount = -1;
        TrendLine hit = null;
        double best = KDJ_NEAR_LINE_PX;
        for (TrendLine line : kdjLines) {
            double distance = kdjLineDistance(line, x, y);
            if (distance <= best) {
                best = distance;
                hit = line;
            }
        }
        if (hit == null) {
            invalidate();
            return undone;
        }
        kdjLines.remove(hit);
        activity.deleteTrendLine(hit.getId());
        invalidate();
        return true;
    }

    private Point getKdjLoupePoint() {
        return kdjTouchActive && kdjCurrentPoint != null ? kdjCurrentPoint : null;
    }

    private void drawKdjLines(Canvas canvas) {
        if (!isKdjEditable()) {
            return;
        }
        canvas.save();
        canvas.clipRect(0, 0, getWidth(), getHeight());
        boolean dragging = kdjTouchActive && kdjCurrentPoint != null;
        for (TrendLine line : kdjLines) {
            // 刷新会重新加载线对象，所以按id比较
            if (dragging && kdjEditLine != null && line.getId() == kdjEditLine.getId()) {
                continue;
            }
            Point p1 = kdjPoint(line.getDate1(), line.getPrice1());
            Point p2 = kdjPoint(line.getDate2(), line.getPrice2());
            if (p1 != null && p2 != null) {
                drawKdjShape(canvas, line.getType(), line.getColor(), p1, p2, line.getPrice1());
            }
        }
        if (dragging && kdjDownPoint != null
                && (kdjEditLine != null || isKdjSupportedType(activity.getTrendLineType()))) {
            int type = kdjEditLine != null ? kdjEditLine.getType() : activity.getTrendLineType();
            int color = kdjEditLine != null ? kdjEditLine.getColor() : activity.getTrendLineColor();
            if (type == TrendLine.TYPE_HORIZONTAL) {
                Point p = new Point(0, kdjCurrentPoint.y);
                drawKdjShape(canvas, type, color, p, p, kdjValueAt(kdjCurrentPoint.y));
            } else {
                drawKdjShape(canvas, type == TrendLine.TYPE_RAY ? type : TrendLine.TYPE_SEGMENT,
                        color, kdjDownPoint, kdjCurrentPoint, 0f);
            }
        }
        canvas.restore();
    }

    private void drawKdjShape(Canvas canvas, int type, int color, Point p1, Point p2,
                              float value) {
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setColor(color);
        paint.setStrokeWidth(3);
        paint.setStyle(Paint.Style.STROKE);
        if (type == TrendLine.TYPE_HORIZONTAL) {
            canvas.drawLine(0, p1.y, getWidth(), p1.y, paint);
            paint.setStyle(Paint.Style.FILL);
            paint.setTextSize(Const.FONT_SIZE_MIDDLE);
            Paint.FontMetrics fm = paint.getFontMetrics();
            String text = String.format(Locale.US, "%.1f", value);
            Rect textBounds = new Rect();
            paint.getTextBounds(text, 0, text.length(), textBounds);
            // 以数字实际字形为准，线上方与线下方的间距保持一致
            float gap = 4 + (textBounds.top - fm.ascent);
            float baseline = p1.y - gap;
            // 上方会碰到标题文字时，价格改显示在线下方
            if (baseline + fm.ascent < getTitleBaseline() + 6) {
                baseline = p1.y + gap - textBounds.top;
            }
            canvas.drawText(text, 2, baseline, paint);
        } else if (type == TrendLine.TYPE_RAY) {
            Point end = rayEnd(p1, p2);
            canvas.drawLine(p1.x, p1.y, end.x, end.y, paint);
        } else {
            canvas.drawLine(p1.x, p1.y, p2.x, p2.y, paint);
        }
    }

    private static float distanceToSegment(float px, float py,
                                           float ax, float ay, float bx, float by) {
        float dx = bx - ax;
        float dy = by - ay;
        float lengthSquared = dx * dx + dy * dy;
        float t = lengthSquared == 0 ? 0
                : Math.max(0, Math.min(1, ((px - ax) * dx + (py - ay) * dy) / lengthSquared));
        return (float) Math.hypot(px - (ax + t * dx), py - (ay + t * dy));
    }

    private void saveDraggedDivergence(int x) {
        int nearest = -1;
        int nearestDistance = Integer.MAX_VALUE;
        int count = Math.min(maxStickCount, macdList.size());
        for (int i = 0; i < count; i++) {
            int distance = Math.abs(macdList.get(i).getMacdRect().centerX() - x);
            if (distance < nearestDistance) {
                nearestDistance = distance;
                nearest = i;
            }
        }
        if (nearest < 0) {
            return;
        }
        for (DivergenceLine line : buildDivergenceLines()) {
            if (!line.key.equals(draggingDivergenceKey)) {
                continue;
            }
            int start = draggingDivergenceStart ? nearest : line.startIndex;
            int end = draggingDivergenceStart ? line.endIndex : nearest;
            if (start == end) {
                return;
            }
            MacdInfo nearestInfo = macdList.get(nearest);
            String startDate = draggingDivergenceStart ? nearestInfo.getDate() : line.startDate;
            String endDate = draggingDivergenceStart ? line.endDate : nearestInfo.getDate();
            float startMacd = draggingDivergenceStart ? nearestInfo.getMacd() : line.startMacd;
            float endMacd = draggingDivergenceStart ? line.endMacd : nearestInfo.getMacd();
            getContext().getSharedPreferences(DIVERGENCE_PREFS, Context.MODE_PRIVATE)
                    .edit()
                    .putString(line.key, startDate + "|" + endDate + "|" + startMacd + "|"
                            + endMacd + "|" + (start - end))
                    .apply();
            return;
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
    private int getTextColor() {
        return activity.isFearGreedSymbol() ? Color.BLACK : Color.WHITE;
    }

    private void drawText(Canvas canvas) {
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setColor(getTextColor());
        paint.setStrokeWidth(2);
        paint.setTextSize(getTitleTextSize());
        paint.setFakeBoldText(true);
        drawCurrentPrice(canvas);
        if (Const.Symbol.btc.toString().equals(symbol)) {
            paint.setTextSize(getBtcTitleTextSize());
            canvas.drawText(getChartTitle(), 0, getTitleBaseline(), paint);
        } else {
            canvas.drawText(getChartTitle(), 0, getTitleBaseline(), paint);
        }
    }

    private void drawCurrentPrice(Canvas canvas) {
        String price = getCurrentPriceText();
        if (price == null) {
            return;
        }
        if (Const.Symbol.fng.toString().equals(symbol)) {
            return;
        }
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setTextSize(getPriceTextSize());
        paint.setFakeBoldText(true);
        paint.setTextAlign(Paint.Align.RIGHT);
        paint.setColor(stickList.get(0).getColor());
        float density = getResources().getDisplayMetrics().density;
        Rect priceBounds = getPriceTextBounds();
        float priceBaseline = getTitleBaseline() + getTitleBounds().top - priceBounds.top;
        canvas.drawText(price, getWidth() - 8 * density, priceBaseline, paint);
    }

    private String getCurrentPriceText() {
        if (stickList == null || stickList.isEmpty()) {
            return null;
        }
        float close = stickList.get(0).getClose();
        if (Const.Symbol.fng.toString().equals(symbol)) {
            return String.valueOf(Math.round(close));
        }
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
        String symbolText = getSymbolTitle();
        return symbolText;
    }

    private String getSymbolTitle() {
        if (Const.Symbol.btc.toString().equals(symbol)) {
            return replace(getResources().getString(R.string.symbol_btc));
        }
        if (Const.Symbol.eth.toString().equals(symbol)) {
            return replace(getResources().getString(R.string.symbol_eth));
        }
        if (Const.Symbol.trump.toString().equals(symbol)) {
            return replace(getResources().getString(R.string.symbol_trump));
        }
        if (Const.Symbol.dow.toString().equals(symbol)) {
            return replace(getResources().getString(R.string.symbol_dow));
        }
        if (Const.Symbol.sp500.toString().equals(symbol)) {
            return replace(getResources().getString(R.string.symbol_sp500));
        }
        if (Const.Symbol.nasdaq.toString().equals(symbol)) {
            return replace(getResources().getString(R.string.symbol_nasdaq));
        }
        if (Const.Symbol.nikkei.toString().equals(symbol)) {
            return replace(getResources().getString(R.string.symbol_nikkei));
        }
        if (Const.Symbol.usdx.toString().equals(symbol)) {
            return replace(getResources().getString(R.string.symbol_dollor));
        }
        if (Const.Symbol.usbond.toString().equals(symbol)) {
            return replace(getResources().getString(R.string.symbol_usbond));
        }
        if (Const.Symbol.xauusd.toString().equals(symbol)) {
            return replace(getResources().getString(R.string.symbol_gold));
        }
        if (Const.Symbol.xagusd.toString().equals(symbol)) {
            return replace(getResources().getString(R.string.symbol_silver));
        }
        if (Const.Symbol.fng.toString().equals(symbol)) {
            return replace(getResources().getString(R.string.symbol_fng));
        }
        return "";
    }

    public boolean handleSymbolTitleTap(MotionEvent event) {
        if (!isSymbolTitleHit(event)) {
            return false;
        }
        showSymbolPicker();
        return true;
    }

    public boolean isSymbolTitleHit(MotionEvent event) {
        Paint titlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        titlePaint.setTextSize(Const.Symbol.btc.toString().equals(symbol)
                ? getBtcTitleTextSize() : getTitleTextSize());
        titlePaint.setFakeBoldText(true);
        String symbolTitle = getSymbolTitle();
        Rect symbolBounds = new Rect();
        titlePaint.getTextBounds(symbolTitle, 0, symbolTitle.length(), symbolBounds);
        int[] location = new int[2];
        getLocationOnScreen(location);
        float x = event.getRawX() - location[0];
        float y = event.getRawY() - location[1];
        float density = getResources().getDisplayMetrics().density;
        float padding = 6 * density;
        if (x < 0 || x > titlePaint.measureText(symbolTitle) + padding
                || y < getTitleBaseline() + symbolBounds.top - padding
                || y > getTitleBaseline() + symbolBounds.bottom + padding) {
            return false;
        }
        return true;
    }

    private void showSymbolPicker() {
        if (symbolPopupWindow != null && symbolPopupWindow.isShowing()) {
            symbolPopupWindow.dismiss();
            return;
        }
        float density = getResources().getDisplayMetrics().density;
        Const.Symbol selectedSymbol = Const.Symbol.valueOf(symbol);
        boolean cryptoMenu = isCryptoMenuSymbol(selectedSymbol);
        Const.Symbol[] symbols = cryptoMenu
                ? new Const.Symbol[] {Const.Symbol.btc, Const.Symbol.eth,
                        Const.Symbol.trump, Const.Symbol.fng}
                : new Const.Symbol[] {Const.Symbol.dow, Const.Symbol.sp500,
                        Const.Symbol.nasdaq, Const.Symbol.usdx, Const.Symbol.usbond};
        List<Const.Symbol> otherSymbols = new ArrayList<>();
        for (Const.Symbol optionSymbol : symbols) {
            if (optionSymbol != selectedSymbol) {
                otherSymbols.add(optionSymbol);
            }
        }
        Paint optionPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        optionPaint.setTextSize(getBtcTitleTextSize());
        optionPaint.setFakeBoldText(true);
        int width = 0;
        for (Const.Symbol optionSymbol : otherSymbols) {
            width = Math.max(width, (int) Math.ceil(optionPaint.measureText(
                    getSymbolLabel(optionSymbol))));
        }
        width += (int) (8 * density);
        final int itemSpacing = 50;
        final String[] labels = new String[otherSymbols.size()];
        final Rect[] labelBounds = new Rect[otherSymbols.size()];
        final int[] labelTops = new int[otherSymbols.size()];
        int popupHeight = 0;
        for (int i = 0; i < otherSymbols.size(); i++) {
            labels[i] = getSymbolLabel(otherSymbols.get(i));
            labelBounds[i] = new Rect();
            optionPaint.getTextBounds(labels[i], 0, labels[i].length(), labelBounds[i]);
            labelTops[i] = popupHeight;
            popupHeight += labelBounds[i].height() + itemSpacing;
        }
        popupHeight -= itemSpacing;
        final Paint menuPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        menuPaint.setTextSize(getBtcTitleTextSize());
        menuPaint.setFakeBoldText(true);
        menuPaint.setColor(getTextColor());
        View options = new View(getContext()) {
            @Override
            protected void onDraw(Canvas canvas) {
                super.onDraw(canvas);
                for (int i = 0; i < labels.length; i++) {
                    canvas.drawText(labels[i], 0, labelTops[i] - labelBounds[i].top,
                            menuPaint);
                }
            }

            @Override
            public boolean onTouchEvent(MotionEvent event) {
                if (event.getAction() == MotionEvent.ACTION_UP) {
                    // 每个选项的可点区域延伸到相邻选项间隔的中线，整行宽度都有效
                    for (int i = 0; i < labels.length; i++) {
                        float top = i == 0 ? Float.NEGATIVE_INFINITY
                                : labelTops[i] - itemSpacing / 2f;
                        float bottom = i == labels.length - 1 ? Float.POSITIVE_INFINITY
                                : labelTops[i] + labelBounds[i].height() + itemSpacing / 2f;
                        if (event.getY() >= top && event.getY() <= bottom) {
                            symbolPopupWindow.dismiss();
                            activity.selectSymbol(otherSymbols.get(i));
                            return true;
                        }
                    }
                }
                return true;
            }
        };
        symbolPopupWindow = new PopupWindow(
                options, width, popupHeight, true);
        symbolPopupWindow.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        symbolPopupWindow.setOutsideTouchable(true);
        symbolPopupWindow.setElevation(0);
        int[] location = new int[2];
        getLocationOnScreen(location);
        int popupTop = Math.max(0, location[1] + (int) getTitleBaseline()
                + getTitleBounds().bottom + itemSpacing);
        symbolPopupWindow.showAtLocation(this, Gravity.TOP | Gravity.LEFT,
                location[0], popupTop);
    }

    private String getSymbolLabel(Const.Symbol optionSymbol) {
        switch (optionSymbol) {
            case btc:
                return getResources().getString(R.string.symbol_btc);
            case eth:
                return getResources().getString(R.string.symbol_eth);
            case trump:
                return getResources().getString(R.string.symbol_trump);
            case fng:
                return getResources().getString(R.string.symbol_fng);
            case dow:
                return replace(getResources().getString(R.string.symbol_dow));
            case sp500:
                return replace(getResources().getString(R.string.symbol_sp500));
            case nasdaq:
                return replace(getResources().getString(R.string.symbol_nasdaq));
            case nikkei:
                return replace(getResources().getString(R.string.symbol_nikkei));
            case usdx:
                return replace(getResources().getString(R.string.symbol_dollor));
            case usbond:
                return replace(getResources().getString(R.string.symbol_usbond));
            case xauusd:
                return replace(getResources().getString(R.string.symbol_gold));
            case xagusd:
                return replace(getResources().getString(R.string.symbol_silver));
            default:
                throw new IllegalArgumentException("Unsupported symbol: " + optionSymbol);
        }
    }

    private boolean isCryptoMenuSymbol(Const.Symbol target) {
        return target == Const.Symbol.btc || target == Const.Symbol.eth
                || target == Const.Symbol.trump || target == Const.Symbol.fng;
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

    /* タッチ箇所の価格と日付を描く */
    private void drawTouchedLine(Canvas canvas) {
        if (touchPoint == null) {
            return;
        }
        Paint paint = new Paint();
        paint.setStyle(Paint.Style.FILL);
        paint.setStrokeWidth(2);
        paint.setColor(getTextColor());
        paint.setTextSize(Const.FONT_SIZE);
        if (touchedDate != null) {
            canvas.drawLine(touchPoint.x, 0, touchPoint.x, getHeight(), paint);
        }
        drawKdjTouchedValue(canvas, paint);
    }

    /* KDJ模式下，长按位于MACD区域时，画横线并在左侧显示该位置的数值 */
    private void drawKdjTouchedValue(Canvas canvas, Paint paint) {
        if (!Const.Macd.KDJ.toString().equals(macdType) || comnInfo == null) {
            return;
        }
        float y = touchPoint.y - comnInfo.getStickHeight();
        if (y < 0 || y > getHeight()) {
            return;
        }
        float top = Const.MACD_CHART_VERTICAL_INSET;
        float bottom = getHeight() - Const.MACD_CHART_VERTICAL_INSET;
        float value = (bottom - y) / (bottom - top) * 140f - 20f;
        String text = String.format(Locale.US, "%.1f", value);
        paint.setTextSize(Const.FONT_SIZE_MIDDLE);
        paint.setFakeBoldText(true);
        float textWidth = paint.measureText(text);
        canvas.drawLine(textWidth, y, getWidth(), y, paint);
        Paint.FontMetrics fm = paint.getFontMetrics();
        float baseline = y - (fm.ascent + fm.descent) / 2;
        float minBaseline = -fm.ascent;
        float maxBaseline = getHeight() - fm.descent;
        baseline = Math.max(minBaseline, Math.min(maxBaseline, baseline));
        canvas.drawText(text, 0, baseline, paint);
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
