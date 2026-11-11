package com.finance.view;

import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.Point;
import android.os.Bundle;
import android.view.GestureDetector;
import androidx.annotation.NonNull;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.FragmentActivity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.util.DisplayMetrics;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.view.Gravity;
import android.widget.LinearLayout.LayoutParams;
import android.os.Handler;
import android.os.SystemClock;

import com.finance.R;
import com.finance.common.Const;
import com.finance.common.LogCleaner;
import com.finance.common.LogWriter;
import com.finance.common.MyApp;
import com.finance.controller.Controller;
import com.finance.model.net.NetOperatorYahoo;
import com.finance.model.net.NetConnectorBinanceIntraday;
import com.finance.model.net.NetConnectorBinanceRealtime;
import com.finance.model.net.YahooIndexQuote;
import com.finance.view.component.DateView;
import com.finance.view.component.MacdView;
import com.finance.view.component.StickView;
import com.finance.view.component.stick.ComnInfo;
import com.finance.view.component.stick.CandleDateIndex;
import com.finance.view.component.stick.IndexCandleMerger;
import com.finance.view.component.stick.PriceAxisPan;
import com.finance.view.component.stick.StickInfo;
import com.finance.view.component.trendline.TrendLine;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Observable;
import java.util.Observer;
import java.util.Set;
import java.util.TimeZone;

/**
 * 表示画面：ローソク線、MACD
 */
public class ChartActivity extends FragmentActivity implements Observer,
        GestureDetector.OnGestureListener, GestureDetector.OnDoubleTapListener {

    private static final long BINANCE_REFRESH_INTERVAL_MS = 2000L;
    // 请求超过该时间仍无响应，视为卡住，允许发起新请求
    private static final long BINANCE_REQUEST_STUCK_MS = 8000L;
    private static final long FEAR_GREED_REFRESH_INTERVAL_MS = 60L * 1000L;
    private static final long INDEX_REFRESH_INTERVAL_MS = 15000L;
    private static final int MIN_STICK_WIDTH = 4;
    private static final int MAX_STICK_WIDTH = 60;
    private static final long ZOOM_REFRESH_INTERVAL_MS = 32L;

    // 画面サイズ共通情報
    ComnInfo comnInfo;
    // コントローラー
    private Controller controller;
    // ローソク線のView
    private StickView stickView;
    // MACD線のView
    private MacdView macdView;
    // 日付のView
    private DateView dateView;
    private LinearLayout chartLayout;
    // タッチイベント処理オブジェクト
    private GestureDetector ges;
    // 全局对象
    private MyApp app;
    // 表示中の銘柄
    private String symbol = Const.Symbol.btc.toString();
    // 表示中のタイムフレーム
    private String timeFrame = Const.TimeFrame.d.toString();
    private String candleDatesKey;
    private List<String> candleDates;
    private CandleDateIndex candleDateIndex;
    private String intradayInterval;
    private final List<String> intradayRecords = new ArrayList<>();
    private boolean intradayLoading;
    private boolean intradayHasMore = true;
    private long intradayGeneration;
    private final Set<String> loadIntradayAfterHistory = new HashSet<>();
    private boolean activityResumed;
    private boolean binanceRealtimeLoading;
    private long binanceRealtimeRequestedAt;
    private int binanceRealtimeRequestId;
    private boolean cryptoTimeframeControlGesture;
    private boolean titleTapCandidate;
    private boolean editTouchBlocked;
    private String realtimeCandle;
    private YahooIndexQuote indexQuote;
    private boolean indexQuoteFailed;
    private boolean indexQuoteLoading;
    private int indexRequestId;
    private final Runnable indexRefreshRunnable = new Runnable() {
        @Override
        public void run() {
            if (!activityResumed) {
                return;
            }
            if (isUsIndexSymbol() && !indexQuoteLoading) {
                requestYahooIndex();
            }
            handler.postDelayed(this, INDEX_REFRESH_INTERVAL_MS);
        }
    };
    private LinearLayout editButtonContainer;
    private ImageView editButton;
    private final Runnable binanceRefreshRunnable = new Runnable() {
        @Override
        public void run() {
            if (!activityResumed) {
                return;
            }
            boolean stuck = binanceRealtimeLoading && SystemClock.elapsedRealtime()
                    - binanceRealtimeRequestedAt > BINANCE_REQUEST_STUCK_MS;
            if (isBinanceRealtimeEligible() && (!binanceRealtimeLoading || stuck)) {
                requestBinanceRealtimeCandle();
            }
            handler.postDelayed(this, BINANCE_REFRESH_INTERVAL_MS);
        }
    };
    private final Runnable fearGreedRefreshRunnable = new Runnable() {
        @Override
        public void run() {
            if (!activityResumed) {
                return;
            }
            if (isCryptoSymbol() || isFearGreedSymbol()) {
                requestFearGreedIndex();
            }
            handler.postDelayed(this, FEAR_GREED_REFRESH_INTERVAL_MS);
        }
    };
    // 滑动柱体数量
    private int offset;
    // 每次触摸前备份当前滑动柱体数量，该次触摸中每次移动后回设该数量值
    private int offsetBk;
    // 一次触摸中最后一次移动后的偏移量备份，因为每次移动后回设偏移量以便计算下次偏移
    // 所以在抬起时必须将最后一次移动后的偏移量真正设置到偏移量变量中进行保存
    private int offsetLast;
    // 表示中のMACDタイプ
    public String macd = Const.Macd.Both.toString();
    // 表示中のローソク線のタイプ
    public String kLine = Const.K_Line.Normal.toString();
    //最后一次按下时的位置
    private int mLastMotionX;
    //最后一次按下时的位置
    private int mLastMotionY;
    //当前按下时的位置
    private int mCurrentMotionX;
    //当前按下时的位置
    private int mCurrentMotionY;
    // 双指按下时，双指间的距离
    private float fingerDistance;
    private float fingerSepY;
    /* 价格范围缩放倍数：1为自动适配，越大K线越小 */
    private float priceZoom = 1f;
    private float pricePan;
    private float pricePanStart;
    private boolean pricePanGesture;
    private static final float MAX_PRICE_ZOOM = 4f;

    public float getPriceZoom() {
        return priceZoom;
    }

    public float getPricePan() {
        return pricePan;
    }
    private float pinchStickWidth;
    // 蝋燭の幅
    private int stickWidth;
    // ハンドラ
    private Handler handler = new Handler();
    private boolean zoomRefreshPending;
    // 日付選択ダイアログ
    private DialogFragment newFragment = new DatePickerFragment();
    // 长按
    private boolean isLongPress;
    // 編集モード
    private boolean isEidtMode;
    // 画线工具：线型、颜色、吸附
    private static final String[] TREND_TYPE_LABELS = {"线段", "射线", "水平", "通道", "斐波", "箱体", "五浪"};
    /* 正在绘制的五浪端点 {日期, 价格}，满6个点后保存 */
    private final List<Object[]> waveDraft = new ArrayList<>();

    public List<Object[]> getWaveDraft() {
        return waveDraft;
    }
    private static final int[] TREND_COLORS = {
            Color.WHITE, Color.CYAN, Color.YELLOW, Color.rgb(255, 165, 0),
            Color.MAGENTA, Color.GREEN};
    private int trendLineType;
    private int trendColorIndex;
    private LinearLayout trendToolContainer;
    private final List<TextView> trendTypeButtons = new ArrayList<>();
    private TextView trendColorButton;
    // 平均K線１０日表示フラグ
    public boolean isAvg10Disp;
    // 平均K線２０日表示フラグ
    public boolean isAvg20Disp = true;
    // 平均K線５０日表示フラグ
    public boolean isAvg50Disp;
    public boolean isAvg200Disp = true;
    public boolean isSarDisp;
    public boolean isFearGreedSarDisp;

    @Override
    /* 画面初期生成の場合呼び出される */
    protected void onCreate(Bundle savedInstanceState) {
        // UncaughtExceptionHandlerを実装したクラスをセットする。
        super.onCreate(savedInstanceState);
        if (savedInstanceState != null) {
            intradayInterval = savedInstanceState.getString("IntradayInterval");
        }
        // View設定
        this.initiallize();
        // 全局对象
        this.app = (MyApp)getApplication();
        // イベント処理オブジェクト設定
        this.ges = new GestureDetector(this, this);
        // コントローラ   ー設定
        this.controller = new Controller(this);
        // CSVファイルからDBにデータをロード
        this.controller.loadCsvData();
        // ログファイルクリア
        LogCleaner.startClean(this);
    }

    @Override
    protected void onStart() {
        super.onStart();
    }

    @Override
    /* onRestoreInstanceStateの直後に呼び出される */
    protected void onResume() {
        super.onResume();
        activityResumed = true;
        handler.removeCallbacks(binanceRefreshRunnable);
        handler.removeCallbacks(fearGreedRefreshRunnable);
        handler.removeCallbacks(indexRefreshRunnable);
        if (isBinanceRealtimeEligible()) {
            requestBinanceRealtimeCandle();
            handler.postDelayed(binanceRefreshRunnable, BINANCE_REFRESH_INTERVAL_MS);
        }
        handler.postDelayed(fearGreedRefreshRunnable, FEAR_GREED_REFRESH_INTERVAL_MS);
        if (isUsIndexSymbol()) {
            requestYahooIndex();
        }
        handler.postDelayed(indexRefreshRunnable, INDEX_REFRESH_INTERVAL_MS);
        if (intradayInterval == null) {
            controller.calculateWMQY(symbol);
        }
        // DBからデータを取得し、UIに表示
        this.showUI();
        requestMarketData();
    }

    @Override
    protected void onPause() {
        activityResumed = false;
        handler.removeCallbacks(binanceRefreshRunnable);
        handler.removeCallbacks(fearGreedRefreshRunnable);
        handler.removeCallbacks(indexRefreshRunnable);
        indexRequestId++;
        indexQuoteLoading = false;
        super.onPause();
    }

    private void requestMarketData() {
        if (isFearGreedSymbol()) {
            requestFearGreedIndex();
            return;
        }
        if (isCryptoSymbol()) {
            requestFearGreedIndex();
        }
        if (intradayInterval != null) {
            if (intradayRecords.isEmpty() && !intradayLoading) {
                loadBinanceIntradayPage(null);
            }
            return;
        }
        boolean historySyncDue = app.isHistorySyncDue(symbol);
        boolean updateIntraday = Const.TimeFrame.d.toString().equals(timeFrame)
                || isCryptoSymbol();
        String latestDbDate = getLatestDailyDataDate(symbol);
        if (historySyncDue) {
            if (updateIntraday) {
                loadIntradayAfterHistory.add(symbol);
            } else {
                loadIntradayAfterHistory.remove(symbol);
            }
            if (!app.isNeedConnect(symbol)) {
                return;
            }
            app.setConnecting(symbol);
            if (controller.loadPastData(symbol)) {
                app.markHistorySyncAttempted(symbol);
            } else {
                loadIntradayAfterHistory.remove(symbol);
                app.setResult(symbol, false);
            }
        } else if (updateIntraday && app.isNeedConnect(symbol)) {
            loadDailyDataIfNeeded(symbol, latestDbDate);
        }
    }

    private void requestFearGreedIndex() {
        com.finance.model.net.FearGreedIndex.refreshIfDue(records -> {
            if (isFearGreedSymbol()) {
                controller.updateFearGreedHistory(symbol, records, () -> {
                    if (isFearGreedSymbol()) {
                        candleDatesKey = null;
                        showUI();
                    }
                });
            } else if (isCryptoSymbol()) {
                runOnUiThread(() -> macdView.invalidate());
            }
        });
    }

    @Override
    /* ネットデータ更新後、コールバックして画面更新 */
    public synchronized void update(Observable observable, Object data) {
        if (observable instanceof NetOperatorYahoo && isPastDataUpdate((Object[]) data)) {
            updateByPastData((Object[]) data);
        } else {
            // 当日データ更新
            updateByYahoo((Object[]) data);
        }
    }

    /* 過日データ更新 */
    private boolean isPastDataUpdate(Object[] data) {
        int typeIndex = Boolean.TRUE.equals(data[0]) ? 2 : 3;
        return data.length > typeIndex && Boolean.TRUE.equals(data[typeIndex]);
    }

    private void updateByPastData(Object[] data) {
        // DBデータ更新後、更新済みの銘柄とタイムフレームを非同期通知
        Object[] resultParam = data;
        // 更新結果
        Boolean updResult = (Boolean)resultParam[0];
        // 更新銘柄
        String updSymbol = (String)resultParam[1];
        // DB更新結果判定
        if (updResult) {// 更新正常終了
            app.markHistorySyncSucceeded(updSymbol);
            boolean shouldLoadIntraday = loadIntradayAfterHistory.remove(updSymbol);
            // ネットデータが来る前に、画面切替をした場合、DB更新のみを行い、画面更新なし
            // ネットからの戻りデータが画面表示銘柄と一致する場合のみ画面更新
            if (symbol.equals(updSymbol)) {
                candleDatesKey = null;
                // 历史数据取得后，先刷新画面
                this.showUI();
                // 历史数据更新完了后，再更新当天数据
                if (shouldLoadIntraday
                        && (Const.TimeFrame.d.toString().equals(timeFrame)
                        || isCryptoSymbol(updSymbol))) {
                    loadDailyDataIfNeeded(updSymbol);
                } else {
                    app.setResult(updSymbol, true);
                }
            } else {
                app.setResult(updSymbol, true);
            }
        } else {// ネットワック異常又は更新断られる
            loadIntradayAfterHistory.remove(updSymbol);
            app.setResult(updSymbol, false);
            if (symbol.equals(updSymbol)) {
                candleDatesKey = null;
                this.showUI();
            }
        }
    }

    private void loadDailyDataIfNeeded(String requestedSymbol) {
        loadDailyDataIfNeeded(requestedSymbol, getLatestDailyDataDate(requestedSymbol));
    }

    private void loadDailyDataIfNeeded(String requestedSymbol, String latestDbDate) {
        if (!app.isDailyDataRefreshDue(requestedSymbol, latestDbDate)) {
            app.setResult(requestedSymbol, true);
            return;
        }
        app.markDailyDataRefreshAttempted(requestedSymbol);
        app.setConnecting(requestedSymbol);
        controller.loadIntraDayData(requestedSymbol);
    }

    private String getLatestDailyDataDate(String requestedSymbol) {
        List<Object[]> latestRecords = controller.getData(requestedSymbol,
                Const.TimeFrame.d.toString(), 1);
        return latestRecords.isEmpty()
                ? null : (String)latestRecords.get(0)[0];
    }

    /* 当日データ更新 */
    private void updateByYahoo(Object[] data) {
        // DBデータ更新後、更新済みの銘柄とタイムフレームを非同期通知
        Object[] resultParam = data;
        // 更新結果
        Boolean updResult = (Boolean)resultParam[0];
        // 更新銘柄
        String updSymbol = (String)resultParam[1];
        app.setResult(updSymbol, updResult);
        // DB更新結果判定
        if (updResult) {// 更新正常終了
            // ネットデータが来る前に、画面切替をした場合、DB更新のみを行い、画面更新なし
            // ネットからの戻りデータが画面表示銘柄と一致する場合のみ画面更新
            if (symbol.equals(updSymbol)) {
                candleDatesKey = null;
                this.showUI();
            }
        } else {// ネットワック異常又は当日データがまだ存在しない
            if (symbol.equals(updSymbol)) {
                this.showUI();
            }
        }
    }

    /* DBから最新データ取得し、画面表示 */
    public void showUI() {
        // 共通情報
        if (chartLayout != null) {
            updateChartLayoutWeights();
        }
        comnInfo = new ComnInfo(this);
        // DBからの最新データを保持するリスト
        List<StickInfo> stickList;
        // K線図、平均K線図を判定
        if (intradayInterval != null) {
            if (kLine.equals(Const.K_Line.Normal.toString())) {
                stickList = getSticksOfKLine(intradayInterval);
            } else {
                List<Object[]> records = getIntradayObjectRecords();
                stickList = calSticksOfAvgKLine(getSimpleAverage(records));
            }
        } else if (kLine.equals(Const.K_Line.Normal.toString())) {// K線図
            stickList = getSticksOfKLine(this.timeFrame);
        } else {// 平均K線図
            stickList = getSticksOfAvgKLine(this.timeFrame);
        }
        updateCandleDateIndex(stickList);
        List<TrendLine> trendLines = this.loadTrendLines();
        List<StickInfo> fearGreedSarHistory = null;
        if (isFearGreedSymbol()) {
            fearGreedSarHistory = getFearGreedSarHistory(timeFrame);
        }
        stickView.setData(comnInfo, stickList, trendLines);
        if (fearGreedSarHistory != null) {
            stickView.setSarHistory(fearGreedSarHistory);
        }
        String displayedTimeFrame = intradayInterval == null ? timeFrame : intradayInterval;
        macdView.setData(comnInfo, stickList, symbol, displayedTimeFrame, macd, kLine);
        if (fearGreedSarHistory != null && !stickList.isEmpty()) {
            macdView.setFearGreedSarValue(stickView.getSarValue(
                    stickList.get(0).getDate()));
        } else {
            macdView.setFearGreedSarValue(null);
        }
        macdView.setKdjLines(loadTrendLines(getTrendLineTimeFrame() + "@kdj"));
        macdView.setMacdLines(loadTrendLines(getTrendLineTimeFrame() + "@macd"));
        setImageView();
        int dateOffset = intradayInterval == null
                ? offset : Math.min(offset, Const.DEFAULT_OFFSET);
        dateView.setData(comnInfo, stickList, dateOffset);
        stickView.invalidate();
        macdView.invalidate();
        dateView.invalidate();
        maybeLoadMoreIntraday();
    }

    private void updateCandleDateIndex(List<StickInfo> sticks) {
        String key = symbol + ":" + (intradayInterval == null ? timeFrame : intradayInterval);
        if (!key.equals(candleDatesKey)) {
            if (intradayInterval == null) {
                candleDates = controller.getCandleDates(symbol, timeFrame);
            } else {
                candleDates = new ArrayList<>(intradayRecords.size());
                for (String record : intradayRecords) {
                    int start = record.indexOf(',') + 1;
                    int end = record.indexOf(',', start);
                    candleDates.add(record.substring(start, end));
                }
            }
            candleDatesKey = key;
        }
        if (!sticks.isEmpty()) {
            String date = sticks.get(0).getDate();
            int position = Collections.binarySearch(candleDates, date, Collections.reverseOrder());
            if (position < 0) {
                candleDates.add(-position - 1, date);
            }
        }
        candleDateIndex = new CandleDateIndex(candleDates);
    }

    public CandleDateIndex getCandleDateIndex() {
        return candleDateIndex;
    }

    private List<StickInfo> getFearGreedSarHistory(String timeFrame) {
        int dataCount = controller.getDataCount(symbol, timeFrame);
        List<Object[]> records = controller.getData(symbol, timeFrame, dataCount);
        List<StickInfo> history = new ArrayList<>(records.size());
        for (Object[] record : records) {
            StickInfo stick = new StickInfo(comnInfo);
            stick.setDate((String) record[0]);
            stick.setOpen((float) record[1]);
            stick.setHigh((float) record[2]);
            stick.setLow((float) record[3]);
            stick.setClose((float) record[4]);
            history.add(stick);
        }
        return history;
    }

    private List<TrendLine> loadTrendLines() {
        return loadTrendLines(getTrendLineTimeFrame());
    }

    private List<TrendLine> loadTrendLines(String lineTimeFrame) {
        List<Object[]> datas = controller.loadTrendLines(symbol, lineTimeFrame);
        List<TrendLine> trendLines = new ArrayList<>();
        for (Object[] data : datas) {
            TrendLine trendLine = new TrendLine();
            trendLine.setId((Integer) data[0]);
            trendLine.setSymbol((String)data[1]);
            trendLine.setTimeFrame((String)data[2]);
            trendLine.setDate1((String)data[3]);
            trendLine.setPrice1((float)data[4]);
            trendLine.setDate2((String)data[5]);
            trendLine.setPrice2((float)data[6]);
            trendLine.setType((Integer) data[7]);
            trendLine.setColor((Integer) data[8]);
            trendLine.setOffset((float) data[9]);
            trendLine.setExtraPoints((String) data[10]);
            trendLines.add(trendLine);
        }
        return trendLines;
    }

    /* DBからデータを取得し、K線図のオリジナルデータのリストを作成 */
    public List<StickInfo> getSticksOfKLine(String timeFrame) {
        if (intradayInterval != null) {
            return getIntradaySticks();
        }
        // 最小表示件数
        int minDisplayCount = comnInfo.getMaxStickCount();
        List<Object[]> dataList = controller.getData(symbol, timeFrame,
                getChartDataCount(minDisplayCount, false), offset, minDisplayCount);
        applyRealtimeCandle(dataList, timeFrame);
        List<StickInfo> stickList = new ArrayList<>();
        for (Object[] stickData : dataList) {
            StickInfo stickInfo = new StickInfo(comnInfo);
            stickInfo.setDate((String)stickData[0]);
            stickInfo.setOpen((float)stickData[1]);
            stickInfo.setHigh((float)stickData[2]);
            stickInfo.setLow((float)stickData[3]);
            stickInfo.setClose((float)stickData[4]);
            stickList.add(stickInfo);
        }
        return stickList;
    }

    private List<StickInfo> getIntradaySticks() {
        List<Object[]> records = getIntradayObjectRecords();
        List<StickInfo> stickList = new ArrayList<>();
        for (Object[] record : records) {
            StickInfo stickInfo = new StickInfo(comnInfo);
            stickInfo.setDate((String)record[0]);
            stickInfo.setOpen((Float)record[1]);
            stickInfo.setHigh((Float)record[2]);
            stickInfo.setLow((Float)record[3]);
            stickInfo.setClose((Float)record[4]);
            stickList.add(stickInfo);
        }
        return stickList;
    }

    private List<Object[]> getIntradayObjectRecords() {
        int minDisplayCount = comnInfo.getMaxStickCount();
        int maxOffset = Math.max(0, intradayRecords.size() - minDisplayCount);
        if (offset > maxOffset) {
            setOffset(maxOffset);
        }
        int start = Math.max(0, offset - Const.DEFAULT_OFFSET);
        int dataCount = getChartDataCount(minDisplayCount, true);
        int end = Math.min(intradayRecords.size(), start + dataCount);
        List<Object[]> records = new ArrayList<>();
        for (int i = start; i < end; i++) {
            String[] fields = intradayRecords.get(i).split(",", -1);
            if (fields.length != 6) {
                continue;
            }
            records.add(new Object[] {
                    fields[1],
                    Float.parseFloat(fields[2]),
                    Float.parseFloat(fields[3]),
                    Float.parseFloat(fields[4]),
                    Float.parseFloat(fields[5])
            });
        }
        return records;
    }

    private int getChartDataCount(int visibleCount, boolean averageKLine) {
        int requiredCount = visibleCount;
        if (averageKLine) {
            requiredCount += Const.SMOOTHED;
        }
        // 多取200根，使200均线能铺满整个画面
        return Math.max(400, requiredCount + 200 + Const.SMOOTHED);
    }

    private void maybeLoadMoreIntraday() {
        if (intradayInterval == null || intradayLoading || !intradayHasMore) {
            return;
        }
        if (intradayRecords.isEmpty()) {
            loadBinanceIntradayPage(null);
            return;
        }
        int visibleCount = comnInfo.getMaxStickCount();
        if (offset + visibleCount + 200 >= intradayRecords.size() - 24) {
            String oldestRecord = intradayRecords.get(intradayRecords.size() - 1);
            long oldestOpenTime = Long.parseLong(oldestRecord.substring(0, oldestRecord.indexOf(',')));
            loadBinanceIntradayPage(oldestOpenTime - 1L);
        }
    }

    private void loadBinanceIntradayPage(Long endTime) {
        if (intradayInterval == null || intradayLoading || !intradayHasMore) {
            return;
        }
        intradayLoading = true;
        final long requestGeneration = intradayGeneration;
        controller.loadBinanceIntraday(symbol, intradayInterval, endTime,
                (requestedSymbol, requestedInterval, records, error) -> {
                    if (requestGeneration != intradayGeneration
                            || !requestedSymbol.equals(symbol)
                            || !requestedInterval.equals(intradayInterval)) {
                        return;
                    }
                    intradayLoading = false;
                    if (error != null) {
                        LogWriter.getInstance(this).error(
                                "Binance intraday request failed: " + error);
                        android.widget.Toast.makeText(this, error,
                                android.widget.Toast.LENGTH_SHORT).show();
                        return;
                    }

                    Set<String> existingTimes = new HashSet<>();
                    for (String record : intradayRecords) {
                        existingTimes.add(record.substring(0, record.indexOf(',')));
                    }
                    int added = 0;
                    for (int i = records.size() - 1; i >= 0; i--) {
                        String record = records.get(i);
                        String openTime = record.substring(0, record.indexOf(','));
                        if (existingTimes.add(openTime)) {
                            intradayRecords.add(record);
                            added++;
                        }
                    }
                    intradayHasMore = records.size()
                            == NetConnectorBinanceIntraday.PAGE_SIZE && added > 0;
                    if (added > 0) {
                        candleDatesKey = null;
                    }
                    showUI();
                });
    }

    public boolean isFearGreedSymbol() {
        return Const.Symbol.fng.toString().equals(symbol);
    }

    public void refreshFearGreedSarDisplay() {
        stickView.invalidate();
        macdView.invalidate();
    }

    public boolean isCryptoSymbol() {
        return isCryptoSymbol(symbol);
    }

    public boolean isUsIndexSymbol() {
        return Const.Symbol.dow.toString().equals(symbol)
                || Const.Symbol.sp500.toString().equals(symbol)
                || Const.Symbol.nasdaq.toString().equals(symbol);
    }

    public YahooIndexQuote getIndexQuote() {
        return indexQuote;
    }

    public boolean isIndexQuoteFailed() {
        return indexQuoteFailed;
    }

    private void requestYahooIndex() {
        indexQuoteLoading = true;
        int requestId = ++indexRequestId;
        String requestedSymbol = symbol;
        controller.loadYahooIndex(requestedSymbol, (responseSymbol, quote, error) -> {
            if (requestId != indexRequestId) {
                return;
            }
            indexQuoteLoading = false;
            if (!activityResumed || !symbol.equals(responseSymbol)) {
                return;
            }
            if (error != null || quote == null) {
                indexQuoteFailed = true;
                LogWriter.getInstance(this).error("Yahoo index quote failed: " + responseSymbol
                        + ": " + (error == null ? "empty response" : error));
                macdView.invalidate();
                return;
            }
            if (indexQuote != null && quote.quoteTime < indexQuote.quoteTime) {
                indexQuoteFailed = true;
                LogWriter.getInstance(this).error("Yahoo index quote moved backwards: " + responseSymbol);
                macdView.invalidate();
                return;
            }
            if (indexQuote == null || !indexQuote.date.equals(quote.date)) {
                candleDatesKey = null;
            }
            boolean changed = indexQuote == null || !indexQuote.date.equals(quote.date)
                    || indexQuote.open != quote.open || indexQuote.high != quote.high
                    || indexQuote.low != quote.low || indexQuote.price != quote.price;
            indexQuote = quote;
            indexQuoteFailed = false;
            if (changed) {
                showUI();
            } else {
                macdView.invalidate();
            }
        });
    }

    private boolean isBinanceRealtimeEligible() {
        return isCryptoSymbol();
    }

    private boolean isCryptoSymbol(String candidateSymbol) {
        return Const.Symbol.btc.toString().equals(candidateSymbol)
                || Const.Symbol.eth.toString().equals(candidateSymbol);
    }

    private void requestBinanceRealtimeCandle() {
        binanceRealtimeLoading = true;
        binanceRealtimeRequestedAt = SystemClock.elapsedRealtime();
        final int requestId = ++binanceRealtimeRequestId;
        String requestedSymbol = symbol;
        String requestedInterval = intradayInterval == null ? "1d" : intradayInterval;
        long requestGeneration = intradayGeneration;
        controller.loadBinanceRealtime(requestedSymbol, requestedInterval,
                (responseSymbol, record, error) -> {
            // 已被更新的请求取代（旧请求卡住后才返回）时不再处理
            if (requestId != binanceRealtimeRequestId) {
                return;
            }
            binanceRealtimeLoading = false;
            if (!activityResumed || !requestedSymbol.equals(symbol)
                    || !responseSymbol.equals(symbol)
                    || !requestedInterval.equals(
                    intradayInterval == null ? "1d" : intradayInterval)
                    || (intradayInterval != null && requestGeneration != intradayGeneration)) {
                return;
            }
            if (error != null) {
                LogWriter.getInstance(this).error(
                        "Binance realtime request failed for " + requestedInterval + ": " + error);
                return;
            }
            if (record == null) {
                LogWriter.getInstance(this).error(
                        "Binance realtime request returned an empty candle");
                return;
            }
            if (intradayInterval != null) {
                if (upsertIntradayRealtimeRecord(record)) {
                    LogWriter.getInstance(this).log(
                            "Binance realtime candle updated: " + requestedSymbol
                                    + " " + requestedInterval);
                    showUI();
                }
            } else if (!record.equals(realtimeCandle)) {
                if (realtimeCandle == null || !record.split(",", -1)[0]
                        .equals(realtimeCandle.split(",", -1)[0])) {
                    candleDatesKey = null;
                }
                realtimeCandle = record;
                LogWriter.getInstance(this).log(
                        "Binance realtime candle updated: " + requestedSymbol
                                + " " + requestedInterval);
                if (offset <= Const.DEFAULT_OFFSET) {
                    showUI();
                }
            }
        });
    }

    private boolean upsertIntradayRealtimeRecord(String record) {
        String[] fields = record.split(",", -1);
        if (fields.length != 6) {
            LogWriter.getInstance(this).error("Invalid Binance intraday realtime candle");
            return false;
        }
        try {
            long openTime = Long.parseLong(fields[0]);
            int insertIndex = 0;
            for (int i = 0; i < intradayRecords.size(); i++) {
                String existing = intradayRecords.get(i);
                int separator = existing.indexOf(',');
                long existingOpenTime = Long.parseLong(existing.substring(0, separator));
                if (existingOpenTime == openTime) {
                    if (existing.equals(record)) {
                        return false;
                    }
                    intradayRecords.set(i, record);
                    return true;
                }
                if (existingOpenTime > openTime) {
                    insertIndex = i + 1;
                } else {
                    break;
                }
            }
            intradayRecords.add(insertIndex, record);
            candleDatesKey = null;
            if (insertIndex == 0 && offset > 0) {
                setOffset(offset + 1);
            }
            return true;
        } catch (RuntimeException e) {
            LogWriter.getInstance(this).error(
                    "Unable to merge Binance intraday realtime candle", e);
            return false;
        }
    }

    private void applyRealtimeCandle(List<Object[]> records, String selectedTimeFrame) {
        if (isUsIndexSymbol()) {
            applyIndexCandle(records, selectedTimeFrame);
            return;
        }
        if (realtimeCandle == null || !isCryptoSymbol() || offset > Const.DEFAULT_OFFSET
                || !isBinanceTimeFrame(selectedTimeFrame)) {
            return;
        }

        String[] fields = realtimeCandle.split(",", -1);
        if (fields.length != 5) {
            LogWriter.getInstance(this).error("Invalid cached Binance realtime candle");
            return;
        }
        try {
            String dailyDate = fields[0];
            float liveOpen = Float.parseFloat(fields[1]);
            float liveHigh = Float.parseFloat(fields[2]);
            float liveLow = Float.parseFloat(fields[3]);
            float liveClose = Float.parseFloat(fields[4]);
            if (dailyDate.length() != 8 || !Float.isFinite(liveOpen)
                    || !Float.isFinite(liveHigh) || !Float.isFinite(liveLow)
                    || !Float.isFinite(liveClose)) {
                throw new IllegalArgumentException("Invalid Binance realtime candle values");
            }

            String liveBucket = getRealtimeBucketKey(dailyDate, selectedTimeFrame);
            float[] periodValues = getRealtimePeriodValues(
                    dailyDate, selectedTimeFrame, liveOpen, liveHigh, liveLow, liveClose);
            if (!records.isEmpty()) {
                Object[] latest = records.get(0);
                String latestDate = (String)latest[0];
                if (liveBucket.equals(getRealtimeBucketKey(latestDate, selectedTimeFrame))) {
                    latest[1] = periodValues[0];
                    latest[2] = periodValues[1];
                    latest[3] = periodValues[2];
                    latest[4] = periodValues[3];
                    return;
                }
            }

            records.add(0, new Object[] {
                    getRealtimePeriodLabel(dailyDate, selectedTimeFrame),
                    periodValues[0], periodValues[1], periodValues[2], periodValues[3]
            });
        } catch (RuntimeException e) {
            LogWriter.getInstance(this).error("Unable to apply Binance realtime candle", e);
        }
    }

    private void applyIndexCandle(List<Object[]> records, String selectedTimeFrame) {
        if (indexQuote == null || offset > Const.DEFAULT_OFFSET
                || !isBinanceTimeFrame(selectedTimeFrame)) {
            return;
        }
        String label = getRealtimePeriodLabel(indexQuote.date, selectedTimeFrame);
        List<Object[]> dailyRecords = controller.getData(symbol, "d", 400);
        IndexCandleMerger.apply(records, dailyRecords, indexQuote, label,
                date -> getRealtimeBucketKey(date, selectedTimeFrame));
    }

    private float[] getRealtimePeriodValues(String liveDate, String selectedTimeFrame,
                                            float liveOpen, float liveHigh, float liveLow,
                                            float liveClose) {
        float open = liveOpen;
        float high = liveHigh;
        float low = liveLow;
        if (!Const.TimeFrame.d.toString().equals(selectedTimeFrame)) {
            List<Object[]> dailyRecords = controller.getData(symbol, "d", 400);
            boolean hasEarlierCandle = false;
            for (int i = dailyRecords.size() - 1; i >= 0; i--) {
                Object[] daily = dailyRecords.get(i);
                String date = (String)daily[0];
                if (date.compareTo(liveDate) >= 0
                        || !getRealtimeBucketKey(date, selectedTimeFrame)
                        .equals(getRealtimeBucketKey(liveDate, selectedTimeFrame))) {
                    continue;
                }
                float dailyOpen = ((Number)daily[1]).floatValue();
                float dailyHigh = ((Number)daily[2]).floatValue();
                float dailyLow = ((Number)daily[3]).floatValue();
                if (!hasEarlierCandle) {
                    open = dailyOpen;
                    high = dailyHigh;
                    low = dailyLow;
                    hasEarlierCandle = true;
                } else {
                    high = Math.max(high, dailyHigh);
                    low = Math.min(low, dailyLow);
                }
            }
            high = Math.max(high, liveHigh);
            low = Math.min(low, liveLow);
        }
        return new float[] {open, high, low, liveClose};
    }

    private boolean isBinanceTimeFrame(String candidate) {
        return Const.TimeFrame.d.toString().equals(candidate)
                || Const.TimeFrame.d3.toString().equals(candidate)
                || Const.TimeFrame.w.toString().equals(candidate)
                || Const.TimeFrame.m.toString().equals(candidate)
                || Const.TimeFrame.q.toString().equals(candidate)
                || Const.TimeFrame.y.toString().equals(candidate);
    }

    private String getRealtimeBucketKey(String date, String selectedTimeFrame) {
        switch (selectedTimeFrame) {
            case "3d":
                Calendar utcCalendar = getCalendar(date, true);
                long day = Math.floorDiv(utcCalendar.getTimeInMillis(),
                        24L * 60 * 60 * 1000);
                return "3d:" + Math.floorDiv(day, 3L);
            case "w":
                return "w:" + getRealtimePeriodLabel(date, selectedTimeFrame);
            case "m":
                return "m:" + date.substring(0, 6);
            case "q":
                return "q:" + date.substring(0, 4)
                        + ((Integer.parseInt(date.substring(4, 6)) - 1) / 3);
            case "y":
                return "y:" + date.substring(0, 4);
            default:
                return "d:" + date;
        }
    }

    private String getRealtimePeriodLabel(String date, String selectedTimeFrame) {
        Calendar calendar = getCalendar(date, "3d".equals(selectedTimeFrame));
        switch (selectedTimeFrame) {
            case "3d":
                long day = Math.floorDiv(calendar.getTimeInMillis(),
                        24L * 60 * 60 * 1000);
                calendar.setTimeInMillis(Math.floorDiv(day, 3L) * 3L
                        * 24 * 60 * 60 * 1000);
                break;
            case "w":
                calendar.add(Calendar.DATE, Calendar.SUNDAY - calendar.get(Calendar.DAY_OF_WEEK));
                break;
            case "m":
                calendar.set(Calendar.DAY_OF_MONTH,
                        calendar.getActualMaximum(Calendar.DAY_OF_MONTH));
                moveWeekendBack(calendar);
                break;
            case "q":
                int month = calendar.get(Calendar.MONTH);
                calendar.set(Calendar.DAY_OF_MONTH, 1);
                calendar.set(Calendar.MONTH, month - (month % 3) + 2);
                calendar.set(Calendar.DAY_OF_MONTH,
                        calendar.getActualMaximum(Calendar.DAY_OF_MONTH));
                moveWeekendBack(calendar);
                break;
            case "y":
                calendar.set(Calendar.MONTH, Calendar.DECEMBER);
                calendar.set(Calendar.DAY_OF_MONTH,
                        calendar.getActualMaximum(Calendar.DAY_OF_MONTH));
                moveWeekendBack(calendar);
                break;
            default:
                break;
        }
        return String.format(Locale.US, "%04d%02d%02d",
                calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH) + 1,
                calendar.get(Calendar.DAY_OF_MONTH));
    }

    private void moveWeekendBack(Calendar calendar) {
        for (int i = 1; i <= 2; i++) {
            int dayOfWeek = calendar.get(Calendar.DAY_OF_WEEK);
            if (dayOfWeek == Calendar.SUNDAY || dayOfWeek == Calendar.SATURDAY) {
                calendar.set(Calendar.DAY_OF_MONTH,
                        calendar.getActualMaximum(Calendar.DAY_OF_MONTH) - i);
            } else {
                break;
            }
        }
    }

    private Calendar getCalendar(String date, boolean utc) {
        if (date.length() != 8) {
            throw new IllegalArgumentException("Invalid candle date: " + date);
        }
        Calendar calendar = utc
                ? Calendar.getInstance(TimeZone.getTimeZone("UTC"), Locale.US)
                : Calendar.getInstance();
        calendar.clear();
        calendar.set(Integer.parseInt(date.substring(0, 4)),
                Integer.parseInt(date.substring(4, 6)) - 1,
                Integer.parseInt(date.substring(6, 8)));
        return calendar;
    }

    public String getIntradayInterval() {
        return intradayInterval;
    }

    public void selectBinanceInterval(String interval) {
        if (!isCryptoSymbol()
                || !("5m".equals(interval) || "15m".equals(interval)
                || "1h".equals(interval) || "4h".equals(interval))) {
            return;
        }
        if (!interval.equals(intradayInterval)) {
            pricePan = 0f;
        }
        intradayInterval = interval;
        intradayGeneration++;
        intradayRecords.clear();
        candleDatesKey = null;
        intradayLoading = false;
        intradayHasMore = true;
        setOffset(0);
        showUI();
    }

    public void selectCryptoTimeframe(@NonNull String selectedTimeframe) {
        if ("5m".equals(selectedTimeframe) || "15m".equals(selectedTimeframe)
                || "1h".equals(selectedTimeframe) || "4h".equals(selectedTimeframe)) {
            selectBinanceInterval(selectedTimeframe);
            return;
        }
        if (isFearGreedSymbol()
                ? "q".equals(selectedTimeframe)
                : (!("d".equals(selectedTimeframe) || "3d".equals(selectedTimeframe)
                || "w".equals(selectedTimeframe) || "m".equals(selectedTimeframe)
                || "q".equals(selectedTimeframe) || "y".equals(selectedTimeframe))
                || ("3d".equals(selectedTimeframe) && !isCryptoSymbol()))) {
            return;
        }
        if (intradayInterval != null || !selectedTimeframe.equals(timeFrame)) {
            pricePan = 0f;
        }
        clearIntradaySelection();
        timeFrame = selectedTimeframe;
        setOffset(0);
        showUI();
        requestMarketData();
    }

    private void clearIntradaySelection() {
        intradayInterval = null;
        resetIntradayRecords();
    }

    private void resetIntradayRecords() {
        candleDatesKey = null;
        intradayGeneration++;
        intradayRecords.clear();
        intradayLoading = false;
        intradayHasMore = true;
    }

    /* DBからデータを取得し、平均K線図のオリジナルデータのリストを作成 */
    public List<StickInfo> getSticksOfAvgKLine(String timeFrame) {
        if (intradayInterval != null) {
            return calSticksOfAvgKLine(getSimpleAverage(getIntradayObjectRecords()));
        }
        // 最小表示件数
        int minDisplayCount = comnInfo.getMaxStickCount();
        // ＤＢ数据
        List<Object[]> recordList = controller.getData(symbol, timeFrame,
                getChartDataCount(minDisplayCount, true), this.offset, minDisplayCount);
        applyRealtimeCandle(recordList, timeFrame);
        // 四本値に対して平均値を求めた平均データリスト
        List<Object[]> averageList = this.getSimpleAverage(recordList);
        // 平均K線データ計算
        return calSticksOfAvgKLine(averageList);
    }

    /* 四本値に対して平均値を求める */
    public List<Object[]> getSimpleAverage(List<Object[]> recordList) {
        // 移动平均后的四本值数据
        List<Object[]> averageList = new ArrayList();
        // 移动平均Ｋ线数
        int size = recordList.size() - Const.SMOOTHED;
        for (int i = 0; i < size; i++) {
            float averageOpen = 0.0f;
            float averageHigh = 0.0f;
            float averageLow = 0.0f;
            float averageClose = 0.0f;
            // 取得合计值
            for (int j = i; j < i + Const.SMOOTHED; j++) {
                Object[] record = recordList.get(j);
                averageOpen = averageOpen + (float)record[1];
                averageHigh = averageHigh + (float)record[2];
                averageLow = averageLow + (float)record[3];
                averageClose = averageClose + (float)record[4];
            }
            // 计算平均值
            Object[] currentRecord = new Object[5];
            currentRecord[0] = recordList.get(i)[0];
            currentRecord[1] = averageOpen / Const.SMOOTHED;
            currentRecord[2] = averageHigh / Const.SMOOTHED;
            currentRecord[3] = averageLow / Const.SMOOTHED;
            currentRecord[4] = averageClose / Const.SMOOTHED;
            averageList.add(currentRecord);
        }
        return averageList;
    }

    /* 平均K線データ計算 */
    public List<StickInfo> calSticksOfAvgKLine(List<Object[]> averageList) {
        List<StickInfo> stickList = new ArrayList<>();
        for (int i = 0; i < averageList.size(); i ++) {
            Object[] stickData = averageList.get(i);
            StickInfo stickInfo = new StickInfo(comnInfo);
            stickInfo.setDate(String.valueOf(stickData[0]));
            if (i == averageList.size() - 1) {// 最後のローソク足
                stickInfo.setOpen((float)stickData[1]);
                stickInfo.setHigh((float)stickData[2]);
                stickInfo.setLow((float)stickData[3]);
                stickInfo.setClose((float)stickData[4]);
            } else {
                Object[] stickBefore = averageList.get(i + 1);
                // 开盘价=(前期开盘价+前期收盘价) / 2
                float open = ((float)stickBefore[1] + (float)stickBefore[4]) / 2;
                // 收盘价=(当期开盘价+当期收盘价+当期最高价+当期最低价) / 4
                float close = ((float)stickData[1] + (float)stickData[2] +
                        (float)stickData[3] + (float)stickData[4]) / 4;
                // 高价=最高值(高点，开市价，收市价)
                float high = Math.max((float)stickData[2], Math.max(open, close));
                // 低价=最低值（低点，开市价，收市价）
                float low = Math.min((float)stickData[3], Math.min(open, close));
                stickInfo.setOpen(open);
                stickInfo.setClose(close);
                stickInfo.setHigh(high);
                stickInfo.setLow(low);
            }
            stickList.add(stickInfo);
        }
        return stickList;
    }

    /* 画面初期化 */
    private void initiallize() {
        // タイトル非表示
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        // レイアウト設定
        chartLayout = new LinearLayout(this);
        chartLayout.setOrientation(LinearLayout.VERTICAL);
        // 允许MACD区域的放大镜画到上方的K线区域
        chartLayout.setClipChildren(false);
        addContentView(chartLayout, new ViewGroup.LayoutParams(-1, -1));
        float[] weights = getChartAreaWeights();
        // ローソク線のView設定
        this.stickView = new StickView(this);
        chartLayout.addView(stickView, new LayoutParams(LayoutParams.MATCH_PARENT, 0,
                weights[0]));
        // MACD線のView設定
        this.macdView = new MacdView(this);
        chartLayout.addView(macdView, new LayoutParams(LayoutParams.MATCH_PARENT, 0,
                weights[1]));
        // 日付のView設定
        this.dateView = new DateView(this);
        chartLayout.addView(dateView, new LayoutParams(LayoutParams.MATCH_PARENT, 0,
                weights[2]));
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        if (chartLayout == null) {
            return;
        }
        updateChartLayoutWeights();
        if (controller != null) {
            handler.removeCallbacks(zoomRefreshRunnable);
            zoomRefreshPending = false;
            showUI();
        }
    }

    private float[] getChartAreaWeights() {
        boolean isPortrait = getResources().getConfiguration().orientation
                == Configuration.ORIENTATION_PORTRAIT;
        float dateWeightAdjustment = isPortrait ? Const.WEIGHT_DATE / 6f : 0f;
        DisplayMetrics displayMetrics = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getMetrics(displayMetrics);
        int screenHeight = displayMetrics.heightPixels - (isPortrait ? 30 : 100);
        float areaWeightShift = (Const.MACD_AREA_HEIGHT_SHIFT + getMacdExtraHeight())
                * (float) Const.WEIGHT_ALL / screenHeight;
        return new float[] {
                Const.WEIGHT_STICK - areaWeightShift,
                Const.WEIGHT_MACD + dateWeightAdjustment + areaWeightShift,
                Const.WEIGHT_DATE - dateWeightAdjustment
        };
    }

    /* KDJ和MACD同时显示时，扩大MACD区域 */
    public int getMacdExtraHeight() {
        return Const.MACD_BOTH_EXTRA_HEIGHT;
    }

    private void updateChartLayoutWeights() {
        float[] weights = getChartAreaWeights();
        for (int i = 0; i < chartLayout.getChildCount(); i++) {
            View child = chartLayout.getChildAt(i);
            LayoutParams params = (LayoutParams) child.getLayoutParams();
            if (Math.abs(params.weight - weights[i]) < 0.0001f) {
                continue;
            }
            params.weight = weights[i];
            child.setLayoutParams(params);
        }
    }

    @Override
    /* 設定画面の戻り値を設定、startActivityForResultによりコールバック */
    /* 設定後、Activity再表示のため、onResumeにより画面再度更新 */
    protected void onActivityResult(int requestCode, int resultCode, Intent intent)
    {
        super.onActivityResult(requestCode, resultCode, intent);
        if (requestCode == Const.REQUEST_CODE && resultCode == Const.REQUEST_CODE)
        {
            Bundle bundle = intent.getExtras();
            Object result = bundle.get(Const.KEY_RESULT);
            if (result instanceof  Const.TimeFrame) {
                String selectedTimeFrame = result.toString();
                String currentTimeFrame = intradayInterval == null ? timeFrame : intradayInterval;
                if (!selectedTimeFrame.equals(currentTimeFrame)) {
                    pricePan = 0f;
                }
                if (Const.TimeFrame.h4.toString().equals(selectedTimeFrame)
                        || Const.TimeFrame.h1.toString().equals(selectedTimeFrame)
                        || Const.TimeFrame.m15.toString().equals(selectedTimeFrame)
                        || Const.TimeFrame.m5.toString().equals(selectedTimeFrame)) {
                    if (isCryptoSymbol()) {
                        intradayInterval = selectedTimeFrame;
                        resetIntradayRecords();
                    }
                } else {
                    clearIntradaySelection();
                    this.timeFrame = selectedTimeFrame;
                }
                // タイムフレーム切替によりスクロールをリセット
                this.setOffset(0);
            } else if (result instanceof  Const.Symbol) {
                applySymbolSelection(result.toString());
            } else if (result instanceof  Const.Macd) {
                this.macd = result.toString();
            } else {
                this.kLine = result.toString();
            }
        }
        // ＊設定後、Activity再表示のため、onResumeにより画面再度更新＊
    }

    public void selectSymbol(@NonNull Const.Symbol selectedSymbol) {
        applySymbolSelection(selectedSymbol.toString());
        showUI();
        if (isUsIndexSymbol() && activityResumed && !indexQuoteLoading) {
            requestYahooIndex();
        }
        if (isBinanceRealtimeEligible()) {
            requestBinanceRealtimeCandle();
            // 从恐慌指数等非加密货币画面切换过来时，确保定时刷新在运行
            handler.removeCallbacks(binanceRefreshRunnable);
            handler.postDelayed(binanceRefreshRunnable, BINANCE_REFRESH_INTERVAL_MS);
        }
        requestMarketData();
    }

    private void applySymbolSelection(String newSymbol) {
        if (!symbol.equals(newSymbol)) {
            realtimeCandle = null;
            indexQuote = null;
            indexQuoteFailed = false;
            indexQuoteLoading = false;
            indexRequestId++;
            pricePan = 0f;
        }
        if (intradayInterval != null && isCryptoSymbol()
                && isCryptoSymbol(newSymbol)) {
            resetIntradayRecords();
        } else {
            intradayInterval = null;
            resetIntradayRecords();
        }
        if (!isCryptoSymbol(newSymbol)
                && Const.TimeFrame.d3.toString().equals(timeFrame)
                && !Const.Symbol.fng.toString().equals(newSymbol)) {
            timeFrame = Const.TimeFrame.d.toString();
        }
        if (Const.Symbol.fng.toString().equals(newSymbol)
                && Const.TimeFrame.q.toString().equals(timeFrame)) {
            timeFrame = Const.TimeFrame.d.toString();
        }
        symbol = newSymbol;
        setOffset(0);
    }

    @Override
    /* Activityが切られる前に状態保持 */
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putString(Const.KEY_SYMBOL, this.symbol);
        outState.putString(Const.KEY_MACD, this.macd);
        outState.putString(Const.KEY_K_LINE, this.kLine);
        outState.putString(Const.KEY_TIME_FRAME, this.timeFrame);
        outState.putString("IntradayInterval", intradayInterval);
        outState.putInt(Const.KEY_STICK_WIDTH, this.stickWidth);
        outState.putBoolean(Const.KEY_EDIT_MODE, this.isEidtMode);
        outState.putBoolean(Const.KEY_AVG_10_DISP, this.isAvg10Disp);
        outState.putBoolean(Const.KEY_AVG_20_DISP, this.isAvg20Disp);
        outState.putBoolean(Const.KEY_AVG_50_DISP, this.isAvg50Disp);
        outState.putBoolean("Avg200Disp", this.isAvg200Disp);
        outState.putBoolean("SarDisp", this.isSarDisp);
        outState.putBoolean("FearGreedSarDisp", this.isFearGreedSarDisp);
        outState.putFloat("PricePan", pricePan);
    }

    @Override
    /* Activityが状態回復 */
    protected void onRestoreInstanceState(Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);
        this.symbol = savedInstanceState.getString(Const.KEY_SYMBOL);
        this.macd = savedInstanceState.getString(Const.KEY_MACD);
        this.kLine = savedInstanceState.getString(Const.KEY_K_LINE);
        this.timeFrame = savedInstanceState.getString(Const.KEY_TIME_FRAME);
        this.stickWidth = savedInstanceState.getInt(Const.KEY_STICK_WIDTH);
        this.isEidtMode = savedInstanceState.getBoolean(Const.KEY_EDIT_MODE);
        this.isAvg10Disp = savedInstanceState.getBoolean(Const.KEY_AVG_10_DISP);
        this.isAvg20Disp = savedInstanceState.getBoolean(Const.KEY_AVG_20_DISP, true);
        this.isAvg50Disp = savedInstanceState.getBoolean(Const.KEY_AVG_50_DISP);
        this.isAvg200Disp = savedInstanceState.getBoolean("Avg200Disp", true);
        this.isSarDisp = savedInstanceState.getBoolean("SarDisp");
        this.isFearGreedSarDisp = savedInstanceState.getBoolean("FearGreedSarDisp");
        this.pricePan = savedInstanceState.getFloat("PricePan", 0f);
    }

    /* 当面選択された銘柄 */
    public String getSymbol() {
        return symbol;
    }

    /* 趋势线按当前实际显示的周期（含5m~4h）分别保存 */
    public String getTrendLineTimeFrame() {
        return intradayInterval == null ? timeFrame : intradayInterval;
    }

    /* 当面選択されタイムフレーム */
    public String getTimeFrame() {
        return timeFrame;
    }

    /* 滑动后，当前实际滑动距离设置（根据DB数据设置） */
    public void setOffset(int offset) {
        this.offset = offset;
        // 滑动到最后柱体时，重设偏移量，也防止最后一次滑动的偏移量被误设
        this.offsetLast = offset;
    }

    /* ドラグの移動距離を返す */
    public int getOffset() {
        return offset;
    }

    @Override
    /* 长按：用于显示选中位置的价格和日期 */
    public void onLongPress(MotionEvent e) {
        if (mCurrentMotionX == 0 ||
                mCurrentMotionY == 0 ||
                Math.abs(mCurrentMotionX - mLastMotionX) > 10 ||
                Math.abs(mCurrentMotionY - mLastMotionY) > 10) {
            return;
        }
        if(isEidtMode) {
            return;
        }
        this.isLongPress = true;
        drawTouchedLine(e);
    }

    /* 长按＆滑动：用于显示选中位置的价格和日期 */
    private void drawTouchedLine(MotionEvent e) {
        Point point = this.ajustPoint(e);
        if (isLongPress) {
            StickInfo stickInfo = stickView.getRelatedStick(point.x);
            Point touchPoint = new Point();
            touchPoint.y = point.y;
            List<StickInfo> list = stickView.getStickList();
            StickInfo newest = list == null || list.size() < 2 ? null : list.get(0);
            float sw = comnInfo.getStickWidth();
            if (stickInfo == null && newest != null && sw > 0
                    && point.x > newest.getStickRect().right) {
                // 超出最新K线：吸附到右侧未来K线位置，日期按周期推算
                int steps = Math.max(1, Math.round(
                        (point.x - newest.getStickRect().centerX()) / sw));
                String future = candleDateIndex == null ? null : candleDateIndex.dateForX(
                        point.x, newest.getDate(), newest.getLineRect().centerX(),
                        comnInfo.getStickWidth());
                if (future == null) {
                    future = com.finance.view.component.DateView.futureDate(
                            newest.getDate(), list.get(1).getDate(), steps);
                }
                touchPoint.x = Math.round(newest.getStickRect().centerX() + steps * sw);
                if (future != null) {
                    stickView.setTouchedDate(future);
                    macdView.setTouchedDate(future);
                    dateView.setTouchedDate(future, touchPoint.x);
                } else {
                    stickView.setTouchedDate(null);
                    macdView.setTouchedDate(null);
                    dateView.setTouchedDate(null, touchPoint.x);
                }
                stickView.setCurrentValue(newest.getYValue(touchPoint.y));
            } else if (stickInfo == null) {
                touchPoint.x = point.x;
                stickView.setTouchedDate(null);
                macdView.setTouchedDate(null);
                dateView.setTouchedDate(null, touchPoint.x);
                stickView.setCurrentValue(null);
            } else {
                touchPoint.x = stickInfo.getStickRect().centerX();
                stickView.setTouchedDate(stickInfo.getDate());
                macdView.setTouchedDate(stickInfo.getDate());
                dateView.setTouchedDate(stickInfo.getDate(), touchPoint.x);
                stickView.setCurrentValue(stickInfo.getYValue(touchPoint.y));
            }
            stickView.setTouchPoint(touchPoint);
            macdView.setTouchPoint(touchPoint);
        } else {
            stickView.setTouchPoint(null);
            macdView.setTouchPoint(null);
            stickView.setTouchedDate(null);
            macdView.setTouchedDate(null);
            dateView.setTouchedDate(null, 0);
            stickView.setCurrentValue(null);
        }
        // 触摸位置日期价格表示
        this.showUI();
    }

    @Override
    /* 触摸 */
    public boolean onTouchEvent(MotionEvent event) {
        if (titleTapCandidate) {
            // 标题点击已在按下时处理，本次触摸的后续事件不再作为滑动
            int action = event.getActionMasked();
            if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
                titleTapCandidate = false;
            }
            return true;
        }
        if (!isLongPress && !isEidtMode && event.getPointerCount() == 1
                && event.getAction() == MotionEvent.ACTION_DOWN
                && macdView.handleSymbolTitleTap(event)) {
            titleTapCandidate = true;
            return true;
        }
        if (!isLongPress) {
            boolean isDispOperate = false;
            // 自定义长按事件处理
            int x = (int) event.getX();
            int y = (int) event.getY();
            // 編集モード
            if(isEidtMode) {
                if (isEditTouchBlocked(event)) {
                    return true;
                }
                // 单手指操作
                if (event.getPointerCount() == 1) {
                    Point point = this.ajustPoint(event);
                    if (macdView.handleKdjLineEdit(event.getAction(), point)) {
                        if (event.getAction() == MotionEvent.ACTION_UP) {
                            this.showUI();
                        }
                    } else if (macdView.handleDivergenceEdit(event.getAction(), point)) {
                        // 背离线已处理
                    } else if (macdView.handleMacdLineEdit(event.getAction(), point)) {
                        if (event.getAction() == MotionEvent.ACTION_UP) {
                            this.showUI();
                        }
                    } else {
                        stickView.onTouchEvent(event.getAction(), point);
                        this.showUI();
                    }
                }
                // 由Activity处理双击,滑动事件
                ges.onTouchEvent(event);
            } else {
                // 单手指操作
                if (event.getPointerCount() == 1) {
                    switch (event.getAction()) {
                        case MotionEvent.ACTION_DOWN:
                            cryptoTimeframeControlGesture =
                                    macdView.isCryptoTimeframeControlTouch(event);
                            mLastMotionX = x;
                            mLastMotionY = y;
                            // 长按时距离测定用X轴位置
                            mCurrentMotionX = x;
                            // 长按时距离测定用Y轴位置
                            mCurrentMotionY = y;
                            // ①在一次触摸中备份偏移量
                            offsetBk = offset;
                            // 平均K線表示非表示
                            int indicatorRow = stickView.getIndicatorControlRow(event);
                            if (indicatorRow >= 0) {
                                if (indicatorRow == 0) {
                                    this.isAvg10Disp = !this.isAvg10Disp;
                                    isDispOperate = true;
                                    this.showUI();
                                } else if (indicatorRow == 1) {
                                    this.isAvg20Disp = !this.isAvg20Disp;
                                    isDispOperate = true;
                                    this.showUI();
                                } else if (indicatorRow == 2) {
                                    this.isAvg50Disp = !this.isAvg50Disp;
                                    isDispOperate = true;
                                    this.showUI();
                                } else if (indicatorRow == 3) {
                                    this.isAvg200Disp = !this.isAvg200Disp;
                                    isDispOperate = true;
                                    this.showUI();
                                } else {
                                    if (isFearGreedSymbol()) {
                                        isDispOperate = true;
                                    } else {
                                        this.isSarDisp = !this.isSarDisp;
                                        isDispOperate = true;
                                        this.showUI();
                                    }
                                }
                            }
                            int chartY = ajustPoint(event).y;
                            pricePanGesture = !isDispOperate && !cryptoTimeframeControlGesture
                                    && chartY >= 0 && chartY < comnInfo.getStickHeight()
                                    && comnInfo.getStickHeight() > 0
                                    && stickView.getStickList() != null
                                    && !stickView.getStickList().isEmpty();
                            pricePanStart = pricePan;
                            break;
                        case MotionEvent.ACTION_MOVE:
                            // 长按时距离测定用X轴位置
                            mCurrentMotionX = x;
                            // 长按时距离测定用Y轴位置
                            mCurrentMotionY = y;
                            if (!cryptoTimeframeControlGesture) {
                                if (pricePanGesture) {
                                    pricePan = PriceAxisPan.afterDrag(pricePanStart,
                                            y - mLastMotionY, comnInfo.getStickHeight());
                                }
                                offset = offset - (int) Math.floor(
                                        (mLastMotionX - x) / (float) Math.max(1, stickWidth));
                                // 允许向右拖出未来空白，至少保留2根K线在画面内
                                int minOffset = Const.DEFAULT_OFFSET
                                        - Math.max(0, comnInfo.getMaxDispCount() - 2);
                                if (offset < minOffset) {
                                    offset = minOffset;
                                }
                                // ②对最后一次偏移进行备份，以便在抬起时设置到偏移量中
                                offsetLast = offset;
                                this.showUI();
                                // ③一次移动完成后，回设偏移量到该次触摸的初始值
                                offset = offsetBk;
                            }
                            break;
                        case MotionEvent.ACTION_UP:
                            pricePanGesture = false;
                            mLastMotionX = 0;
                            mLastMotionY = 0;
                            mCurrentMotionX = 0;
                            mCurrentMotionY = 0;
                            // ④由于每次移动偏移量都回设为初始值，本次移动全部完成后，将该次触摸抬起之间的最后一次偏移
                            //   设置到偏移量变量中，以便下次从此位置开始移动
                            if (!cryptoTimeframeControlGesture) {
                                offset = offsetLast;
                            }
                            break;
                    }
                }
                // 双手指操作
                if (event.getPointerCount() == 2) {
                    switch (event.getAction() & MotionEvent.ACTION_MASK) {
                        case MotionEvent.ACTION_MOVE:
                            float currentFingerDistance = getDistance(event);
                            float currentSepY = Math.abs(event.getY(1) - event.getY(0));
                            float currentSepX = Math.abs(event.getX(1) - event.getX(0));
                            if (currentSepY > currentSepX) {
                                // 双指上下方向捏合：缩放价格范围（捏合缩小K线，张开恢复）
                                if (fingerSepY > 0 && currentSepY > 0) {
                                    float zoom = Math.max(1f, Math.min(MAX_PRICE_ZOOM,
                                            priceZoom * fingerSepY / currentSepY));
                                    if (Math.abs(zoom - priceZoom) > 0.005f) {
                                        priceZoom = zoom;
                                        scheduleZoomRefresh();
                                    }
                                }
                                fingerSepY = currentSepY;
                                fingerDistance = currentFingerDistance;
                                break;
                            }
                            fingerSepY = currentSepY;
                            if (fingerDistance > 0 && currentFingerDistance > 0) {
                                pinchStickWidth = Math.max(MIN_STICK_WIDTH,
                                        Math.min(MAX_STICK_WIDTH, pinchStickWidth
                                                * currentFingerDistance / fingerDistance));
                                int scaledStickWidth = Math.round(pinchStickWidth);
                                if (scaledStickWidth != stickWidth) {
                                    stickWidth = scaledStickWidth;
                                    scheduleZoomRefresh();
                                }
                            }
                            fingerDistance = currentFingerDistance;
                            break;
                        case MotionEvent.ACTION_UP:
                            fingerDistance = 0;
                            break;
                        case MotionEvent.ACTION_POINTER_DOWN:      // 第二指押下触发
                            if (event.getPointerCount() == 2) {
                                offset = offsetLast;
                                offsetBk = offset;
                                fingerDistance = getDistance(event);
                                fingerSepY = Math.abs(event.getY(1) - event.getY(0));
                                pinchStickWidth = stickWidth;
                            }
                            break;
                        case MotionEvent.ACTION_POINTER_UP:        // 第二指抬起触发
                            if (event.getPointerCount() == 2) {
                                fingerDistance = 0;
                                refreshZoomImmediately();
                                // 以剩下的手指为滑动起点，避免抬起一指后图表跳动
                                int remaining = event.getActionIndex() == 0 ? 1 : 0;
                                mLastMotionX = (int) event.getX(remaining);
                                mLastMotionY = (int) event.getY(remaining);
                                mCurrentMotionX = mLastMotionX;
                                mCurrentMotionY = mLastMotionY;
                                offsetBk = offset;
                                offsetLast = offset;
                                pricePanStart = pricePan;
                            }
                            break;
                    }
                }
                // 标题点击：手指按下的瞬间就弹出菜单
                if(!isDispOperate) {
                    // 由Activity处理双击,滑动事件
                    ges.onTouchEvent(event);
                    // 由View处理滑动事件
                    if (!cryptoTimeframeControlGesture) {
                        stickView.onTouchEvent(event);
                    }
                }
                if (event.getAction() == MotionEvent.ACTION_UP
                        || event.getAction() == MotionEvent.ACTION_CANCEL) {
                    cryptoTimeframeControlGesture = false;
                    pricePanGesture = false;
                }
            }
        } else {
            // 由Activity处理双击,滑动事件
            ges.onTouchEvent(event);
            if (event.getActionMasked() == MotionEvent.ACTION_UP
                    || event.getActionMasked() == MotionEvent.ACTION_CANCEL) {
                isLongPress = false;
            }
            this.drawTouchedLine(event);
            // *消除开启和关闭编辑模式时的距离差*
            mLastMotionX = (int)event.getX();
            mLastMotionY = (int)event.getY();
            mCurrentMotionX = (int)event.getX();
            mCurrentMotionY = (int)event.getY();
        }
        // 后面的事件不再执行
        return false;
    }

    private final Runnable zoomRefreshRunnable = new Runnable() {
        @Override
        public void run() {
            zoomRefreshPending = false;
            showUI();
        }
    };

    private void scheduleZoomRefresh() {
        if (!zoomRefreshPending) {
            zoomRefreshPending = true;
            handler.postDelayed(zoomRefreshRunnable, ZOOM_REFRESH_INTERVAL_MS);
        }
    }

    private void refreshZoomImmediately() {
        if (zoomRefreshPending) {
            handler.removeCallbacks(zoomRefreshRunnable);
            zoomRefreshPending = false;
            showUI();
        }
    }

    @Override
    /* 双击：設定画面表示 */
    public boolean onDoubleTap(MotionEvent e) {
        if (isLongPress) {
            return true;
        }
        if(isEidtMode) {
            Point point = this.ajustPoint(e);
            if (point.y >= 0 && point.y < comnInfo.getStickHeight()) {
                stickView.onDoubleTap(point);
                this.showUI();
            } else if (macdView.deleteKdjLineAt(point)) {
                this.showUI();
            } else if (macdView.deleteMacdLineAt(point)) {
                this.showUI();
            } else {
                macdView.deleteDivergenceAt(point);
            }
            return true;
        }
        // 同じパッケージのアクティビティを起動
        Intent intent = new Intent(this, ChildActivity.class);
        // データを作成してIntentに渡す
        Bundle bandle = new Bundle();
        bandle.putString(Const.KEY_SYMBOL, this.symbol);
        bandle.putString(Const.KEY_MACD, this.macd);
        bandle.putString(Const.KEY_K_LINE, this.kLine);
        bandle.putString(Const.KEY_TIME_FRAME, this.timeFrame);
        bandle.putString("IntradayInterval", this.intradayInterval);
        intent.putExtras(bandle);
        // 子画面起動
        startActivityForResult(intent, Const.REQUEST_CODE);
        // 后面的事件不再触发，到此终止
        return true;
    }

    @Override
    /* 快速滑动：不再切换周期（避免缩放时误触），周期请用标题或周期控件切换 */
    public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
        return true;
    }

    private void changeCryptoTimeframe(boolean swipeUp) {
        String[] timeframes = {"5m", "15m", "1h", "4h", "d", "3d", "w", "m", "q", "y"};
        String currentTimeframe = intradayInterval == null ? timeFrame : intradayInterval;
        int currentIndex = 0;
        for (int i = 0; i < timeframes.length; i++) {
            if (timeframes[i].equals(currentTimeframe)) {
                currentIndex = i;
                break;
            }
        }

        int nextIndex = swipeUp
                ? (currentIndex == 0 ? timeframes.length - 1 : currentIndex - 1)
                : (currentIndex + 1) % timeframes.length;
        selectCryptoTimeframe(timeframes[nextIndex]);
    }

    @Override
    /* 滑动：K线移动 */
    public boolean onScroll(MotionEvent e1, MotionEvent e2, float distanceX, float distanceY) {
        if (cryptoTimeframeControlGesture) {
            macdView.handleCryptoTimeframeScroll(e1, distanceX);
        }
        return true;
    }

    @Override
    public boolean onSingleTapConfirmed(MotionEvent e) {
        Point point = this.ajustPoint(e);
        if (isLongPress || isEidtMode) {
            return true;
        }
        if (macdView.isSymbolTitleHit(e)) {
            return true;
        }
        if (macdView.handleFearGreedGaugeTap(e)) {
            return true;
        }
        if (macdView.handleIntradayControlTap(e)) {
            return true;
        }
        if (macdView.handleFearGreedSarTap(e)) {
            return true;
        }
        // 按下位置判断
        if (point.y < comnInfo.getStickHeight()) {// K线图内
            stickView.onSingleTapConfirmed(e);
        } else if(point.y < (comnInfo.getStickHeight() + comnInfo.getMacdHeight())) {// MACD图内
            macdView.onSingleTapConfirmed(e);
        } else {// 日期图内
            if (dateView.isDatePickerTap(point) && !newFragment.isAdded()) {
                newFragment.show(getSupportFragmentManager(), "datePicker");
            }
        }
        // 后面的事件不再触发，到此终止
        return true;
    }

    @Override
    public boolean onDoubleTapEvent(MotionEvent e) {
        return false;
    }

    @Override
    public boolean onDown(MotionEvent e) {
        return true;
    }

    @Override
    public void onShowPress(MotionEvent e) {}

    @Override
    public boolean onSingleTapUp(MotionEvent e) {
        // 标题点击不等待双击判定，立即弹出菜单
        return false;
    }

    public Controller getController() {
        return controller;
    }

    public ComnInfo getComnInfo() {
        return comnInfo;
    }

    /*获取两指之间的距离*/
    private float getDistance(MotionEvent event) {
        float distance = 0;
        if (event.getPointerCount() == 2) {
            float x = event.getX(1) - event.getX(0);
            float y = event.getY(1) - event.getY(0);
            distance = (float) Math.sqrt(x * x + y * y);
        }
        return distance;
    }

    /*调整按下位置、将顶部状态栏７５像素的高度除去*/
    private Point ajustPoint(MotionEvent e) {
        Point point = new Point();
        point.x = (int)(e.getX());
        point.y = (int)(e.getY() - 75);
        return point;
    }

    /*編集ボタン*/
    private void setImageView() {
        if (editButtonContainer == null) {
            editButtonContainer = new LinearLayout(this);
            editButtonContainer.setOrientation(LinearLayout.VERTICAL);
            editButton = new ImageView(this);
            editButton.setScaleType(ImageView.ScaleType.FIT_CENTER);
            editButton.setContentDescription(getString(R.string.edit_chart));
            editButton.setOnClickListener(v -> {
                isEidtMode = !isEidtMode;
                if (!isEidtMode) {
                    stickView.clearLineSelection();
                }
                updateEditButtonImage();
            });
            editButtonContainer.addView(editButton);
            addContentView(editButtonContainer, new ViewGroup.LayoutParams(-2, -2));
        }
        float density = getResources().getDisplayMetrics().density;
        int buttonWidth = Math.round(58 * density);
        int buttonHeight = Math.round(34 * density);
        editButton.setLayoutParams(new LinearLayout.LayoutParams(buttonWidth, buttonHeight));
        editButtonContainer.setX(20);
        editButtonContainer.setY(comnInfo.getScreenHeight() - comnInfo.getDateHeight()
                - buttonHeight - 7 * density);
        setupTrendTools(buttonWidth, buttonHeight, density);
        updateEditButtonImage();
    }

    /*
     * 编辑模式下，工具栏周围和图表下方（日期区域）的触摸不用于画线，防止点工具按钮时误画。
     * 在按下时判定，整个手势（直到抬起）都忽略。
     */
    private boolean isEditTouchBlocked(MotionEvent event) {
        int action = event.getActionMasked();
        if (action == MotionEvent.ACTION_DOWN) {
            editTouchBlocked = isInEditDeadZone(event.getX(), event.getY());
        }
        boolean blocked = editTouchBlocked;
        if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
            editTouchBlocked = false;
        }
        return blocked;
    }

    private boolean isInEditDeadZone(float x, float y) {
        float density = getResources().getDisplayMetrics().density;
        int[] dateLocation = new int[2];
        dateView.getLocationInWindow(dateLocation);
        if (y >= dateLocation[1]) {
            return true;
        }
        if (editButtonContainer != null && trendToolContainer != null) {
            float margin = 12 * density;
            float right = editButtonContainer.getX() + editButton.getLayoutParams().width + margin;
            float top = trendToolContainer.getY() - margin;
            return x <= right && y >= top;
        }
        return false;
    }

    private void updateEditButtonImage() {
        editButtonContainer.setVisibility(View.VISIBLE);
        editButton.setImageResource(R.drawable.ic_chart_edit);
        editButton.setColorFilter(isEidtMode
                ? Color.rgb(66, 165, 245) : Color.rgb(207, 216, 220));
        editButton.setSelected(isEidtMode);
        if (trendToolContainer != null) {
            trendToolContainer.setVisibility(isEidtMode ? View.VISIBLE : View.GONE);
        }
    }

    private void setupTrendTools(int buttonWidth, int buttonHeight, float density) {
        if (trendToolContainer == null) {
            trendToolContainer = new LinearLayout(this);
            trendToolContainer.setOrientation(LinearLayout.VERTICAL);
            for (int i = 0; i < TREND_TYPE_LABELS.length; i++) {
                final int type = i;
                TextView button = createToolButton();
                button.setOnClickListener(v -> {
                    trendLineType = type;
                    waveDraft.clear();
                    updateTrendToolButtons();
                });
                trendTypeButtons.add(button);
                // 后加入的在上方：从上到下为 箱体、斐波、通道、水平、射线、线段
                trendToolContainer.addView(button, 0);
            }
            trendColorButton = createToolButton();
            trendColorButton.setOnClickListener(v -> {
                trendColorIndex = (trendColorIndex + 1) % TREND_COLORS.length;
                updateTrendToolButtons();
            });
            trendToolContainer.addView(trendColorButton);
            addContentView(trendToolContainer, new ViewGroup.LayoutParams(-2, -2));
        }
        int toolGap = 0;
        int toolButtonHeight = Math.round(buttonHeight * 0.85f * 0.9f);
        List<TextView> buttons = new ArrayList<>(trendTypeButtons);
        buttons.add(trendColorButton);
        for (TextView button : buttons) {
            LinearLayout.LayoutParams params =
                    new LinearLayout.LayoutParams(buttonWidth, toolButtonHeight);
            button.setLayoutParams(params);
        }
        // 在编辑按钮正上方向上展开
        int toolHeight = buttons.size() * (toolButtonHeight + toolGap);
        trendToolContainer.setX(editButtonContainer.getX());
        trendToolContainer.setY(editButtonContainer.getY() - toolHeight);
        updateTrendToolButtons();
    }

    private TextView createToolButton() {
        TextView button = new TextView(this);
        button.setGravity(Gravity.CENTER);
        button.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX,
                16 * getResources().getDisplayMetrics().density);
        button.setTextColor(Color.WHITE);
        button.setBackgroundColor(Color.TRANSPARENT);
        return button;
    }

    private void updateTrendToolButtons() {
        for (int i = 0; i < trendTypeButtons.size(); i++) {
            TextView button = trendTypeButtons.get(i);
            button.setText(TREND_TYPE_LABELS[i]);
            button.setTextColor(i == trendLineType
                    ? Color.rgb(66, 165, 245) : Color.WHITE);
        }
        trendColorButton.setText("颜色");
        trendColorButton.setTextColor(TREND_COLORS[trendColorIndex]);
    }

    public int getTrendLineType() {
        return trendLineType;
    }

    public boolean isChartEditMode() {
        return isEidtMode;
    }

    public int getTrendLineColor() {
        return TREND_COLORS[trendColorIndex];
    }

    public boolean isTrendSnapEnabled() {
        return true;
    }

    public int getStickWidth() {
        return stickWidth;
    }

    public void setStickWidth(int stickWidth) {
        this.stickWidth = stickWidth;
    }

    public void setOffsetBk(int offsetBk) {
        this.offsetBk = offsetBk;
    }

    public void setOffsetLast(int offsetLast) {
        this.offsetLast = offsetLast;
    }

    public void saveTrendLine(TrendLine trendLine) {
        controller.saveTrendLine(trendLine);
    }

    public void deleteTrendLine(int id) {
        controller.deleteTrendLine(id);
    }

    public void calAverageLine(List<StickInfo> stickList, List<Point> averageList, int average) {
        stickView.calAverageLine(stickList, averageList, average);
    }

    /* ローソク足の自身情報を計算 */
    public void calculate(List<StickInfo> stickList) {
        stickView.calculate(stickList);
    }
}
