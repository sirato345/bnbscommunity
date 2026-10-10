package com.finance.view;

import android.content.Intent;
import android.graphics.Point;
import android.os.Bundle;
import android.view.GestureDetector;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.FragmentActivity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.LinearLayout.LayoutParams;
import android.os.Handler;

import com.finance.R;
import com.finance.common.Const;
import com.finance.common.CustomUncaughtExceptionHandler;
import com.finance.common.LogCleaner;
import com.finance.common.LogWriter;
import com.finance.common.MyApp;
import com.finance.controller.Controller;
import com.finance.model.net.NetOperatorYahoo;
import com.finance.view.component.DateView;
import com.finance.view.component.MacdView;
import com.finance.view.component.StickView;
import com.finance.view.component.stick.ComnInfo;
import com.finance.view.component.stick.StickInfo;
import com.finance.view.component.trendline.TrendLine;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Observable;
import java.util.Observer;
import java.util.Set;

/**
 * 表示画面：ローソク線、MACD
 */
public class ChartActivity extends FragmentActivity implements Observer,
        GestureDetector.OnGestureListener, GestureDetector.OnDoubleTapListener {

    static {
        // 想定外異常の処理ハンドラを設定
        setExceptionHandler();
    }
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
    // タッチイベント処理オブジェクト
    private GestureDetector ges;
    // 全局对象
    private MyApp app;
    // 表示中の銘柄
    private String symbol = Const.Symbol.btc.toString();
    // 表示中のタイムフレーム
    private String timeFrame = Const.TimeFrame.d.toString();
    private final Set<String> loadIntradayAfterHistory = new HashSet<>();
    // 滑动柱体数量
    private int offset;
    // 每次触摸前备份当前滑动柱体数量，该次触摸中每次移动后回设该数量值
    private int offsetBk;
    // 一次触摸中最后一次移动后的偏移量备份，因为每次移动后回设偏移量以便计算下次偏移
    // 所以在抬起时必须将最后一次移动后的偏移量真正设置到偏移量变量中进行保存
    private int offsetLast;
    // 表示中のMACDタイプ
    public String macd = Const.Macd.Single.toString();
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
    private int fingerDistance;
    // 蝋燭の幅
    private int stickWidth;
    // ハンドラ
    private Handler handler = new Handler();
    // 拡大、縮小後の判定
    private boolean isAfterZoom;
    // 日付選択ダイアログ
    private DialogFragment newFragment = new DatePickerFragment();
    // 长按
    private boolean isLongPress;
    // 編集モード
    private boolean isEidtMode;
    // 平均K線１０日表示フラグ
    public boolean isAvg10Disp = true;
    // 平均K線２０日表示フラグ
    public boolean isAvg20Disp = true;
    // 平均K線６０日表示フラグ
    public boolean isAvg60Disp = true;

    @Override
    /* 画面初期生成の場合呼び出される */
    protected void onCreate(Bundle savedInstanceState) {
        // UncaughtExceptionHandlerを実装したクラスをセットする。
        super.onCreate(savedInstanceState);
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
        LogCleaner.startClean();
    }

    @Override
    protected void onStart() {
        super.onStart();
    }

    @Override
    /* onRestoreInstanceStateの直後に呼び出される */
    protected void onResume() {
        super.onResume();
        // 日线以外数据再计算
        controller.calculateWMQY(symbol);
        // DBからデータを取得し、UIに表示
        this.showUI();
        requestMarketData();
    }

    private void requestMarketData() {
        boolean historySyncDue = app.isHistorySyncDue(symbol);
        boolean updateIntraday = Const.TimeFrame.d.toString().equals(timeFrame);
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
            app.setConnecting(symbol);
            controller.loadIntraDayData(symbol);
        }
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
                // 历史数据取得后，先刷新画面
                this.showUI();
                // 历史数据更新完了后，再更新当天数据
                if (shouldLoadIntraday
                        && Const.TimeFrame.d.toString().equals(timeFrame)) {
                    controller.loadIntraDayData(symbol);
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
                this.showUI();
            }
        }
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
        comnInfo = new ComnInfo(this);
        // 画像設定
        setImageView();
        // DBからの最新データを保持するリスト
        List<StickInfo> stickList;
        // K線図、平均K線図を判定
        if (kLine.equals(Const.K_Line.Normal.toString())) {// K線図
            stickList = getSticksOfKLine(this.timeFrame);
        } else {// 平均K線図
            stickList = getSticksOfAvgKLine(this.timeFrame);
        }
        List<TrendLine> trendLines = this.loadTrendLines();
        stickView.setData(comnInfo, stickList, trendLines);
        macdView.setData(comnInfo, stickList, symbol, timeFrame, macd, kLine);
        dateView.setData(comnInfo, stickList, offset);
        stickView.invalidate();
        macdView.invalidate();
        dateView.invalidate();
    }

    private List<TrendLine> loadTrendLines() {
        List<Object[]> datas = controller.loadTrendLines(symbol, timeFrame);
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
            trendLines.add(trendLine);
        }
        return trendLines;
    }

    /* DBからデータを取得し、K線図のオリジナルデータのリストを作成 */
    public List<StickInfo> getSticksOfKLine(String timeFrame) {
        // 最小表示件数
        int minDisplayCount = comnInfo.getMaxStickCount();
        List<Object[]> dataList = controller.getData(symbol, timeFrame, 400, offset, minDisplayCount);
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

    /* DBからデータを取得し、平均K線図のオリジナルデータのリストを作成 */
    public List<StickInfo> getSticksOfAvgKLine(String timeFrame) {
        // 最小表示件数
        int minDisplayCount = comnInfo.getMaxStickCount();
        // ＤＢ数据
        List<Object[]> recordList = controller.getData(symbol, timeFrame, 400, this.offset, minDisplayCount);
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

    /* 想定外異常の処理ハンドラを設定 */
    private static void setExceptionHandler() {
        CustomUncaughtExceptionHandler handler = CustomUncaughtExceptionHandler.getInstance();
        Thread.setDefaultUncaughtExceptionHandler(handler);
    }

    /* 画面初期化 */
    private void initiallize() {
        // タイトル非表示
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        // レイアウト設定
        LinearLayout linearLayout = new LinearLayout(this);
        linearLayout.setOrientation(LinearLayout.VERTICAL);
        addContentView(linearLayout, new ViewGroup.LayoutParams(-1, -1));
        // ローソク線のView設定
        this.stickView = new StickView(this);
        linearLayout.addView(stickView,
                new LayoutParams(LayoutParams.MATCH_PARENT, 0, Const.WEIGHT_STICK));
        // MACD線のView設定
        this.macdView = new MacdView(this);
        linearLayout.addView(macdView,
                new LayoutParams(LayoutParams.MATCH_PARENT, 0, Const.WEIGHT_MACD));
        // 日付のView設定
        this.dateView = new DateView(this);
        linearLayout.addView(dateView,
                new LayoutParams(LayoutParams.MATCH_PARENT, 0, Const.WEIGHT_DATE));
    }

    @Override
    /* 設定画面の戻り値を設定、startActivityForResultによりコールバック */
    /* 設定後、Activity再表示のため、onResumeにより画面再度更新 */
    protected void onActivityResult(int requestCode, int resultCode, Intent intent)
    {
        if (requestCode == Const.REQUEST_CODE && resultCode == Const.REQUEST_CODE)
        {
            Bundle bundle = intent.getExtras();
            Object result = bundle.get(Const.KEY_RESULT);
            if (result instanceof  Const.TimeFrame) {
                this.timeFrame = result.toString();
                // タイムフレーム切替によりスクロールをリセット
                this.setOffset(0);
            } else if (result instanceof  Const.Symbol) {
                this.symbol = result.toString();
                // 銘柄切替によりスクロールをリセット
                this.setOffset(0);
            } else if (result instanceof  Const.Macd) {
                this.macd = result.toString();
            } else {
                this.kLine = result.toString();
            }
        }
        // ＊設定後、Activity再表示のため、onResumeにより画面再度更新＊
    }

    @Override
    /* Activityが切られる前に状態保持 */
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putString(Const.KEY_SYMBOL, this.symbol);
        outState.putString(Const.KEY_MACD, this.macd);
        outState.putString(Const.KEY_K_LINE, this.kLine);
        outState.putString(Const.KEY_TIME_FRAME, this.timeFrame);
        outState.putInt(Const.KEY_STICK_WIDTH, this.stickWidth);
        outState.putBoolean(Const.KEY_EDIT_MODE, this.isEidtMode);
        outState.putBoolean(Const.KEY_AVG_10_DISP, this.isAvg10Disp);
        outState.putBoolean(Const.KEY_AVG_20_DISP, this.isAvg20Disp);
        outState.putBoolean(Const.KEY_AVG_60_DISP, this.isAvg60Disp);
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
        this.isAvg20Disp = savedInstanceState.getBoolean(Const.KEY_AVG_20_DISP);
        this.isAvg60Disp = savedInstanceState.getBoolean(Const.KEY_AVG_60_DISP);
    }

    /* 当面選択された銘柄 */
    public String getSymbol() {
        return symbol;
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
        this.isLongPress = !isLongPress;
        // 长按开始,由touchEvent画线;长按解除,手动消除画线
        if (isLongPress == false) {
            drawTouchedLine(e);
        }
    }

    /* 长按＆滑动：用于显示选中位置的价格和日期 */
    private void drawTouchedLine(MotionEvent e) {
        Point point = this.ajustPoint(e);
        if (isLongPress) {
            StickInfo stickInfo = stickView.getRelatedStick(point.x);
            Point touchPoint = new Point();
            touchPoint.y = point.y;
            if (stickInfo == null) {
                touchPoint.x = point.x;
                stickView.setTouchedDate(null);
                macdView.setTouchedDate(null);
                stickView.setCurrentValue(null);
            } else {
                touchPoint.x = stickInfo.getStickRect().centerX();
                stickView.setTouchedDate(stickInfo.getDate());
                macdView.setTouchedDate(stickInfo.getDate());
                stickView.setCurrentValue(stickInfo.getYValue(touchPoint.y));
            }
            stickView.setTouchPoint(touchPoint);
            macdView.setTouchPoint(touchPoint);
        } else {
            stickView.setTouchPoint(null);
            macdView.setTouchPoint(null);
            stickView.setTouchedDate(null);
            macdView.setTouchedDate(null);
            stickView.setCurrentValue(null);
        }
        // 触摸位置日期价格表示
        this.showUI();
    }

    @Override
    /* 触摸 */
    public boolean onTouchEvent(MotionEvent event) {
        if (!isLongPress) {
            boolean isDispOperate = false;
            // 自定义长按事件处理
            int x = (int) event.getX();
            int y = (int) event.getY();
            // 編集モード
            if(isEidtMode) {
                // 单手指操作
                if (event.getPointerCount() == 1) {
                    Point point = this.ajustPoint(event);
                    stickView.onTouchEvent(event.getAction(), point);
                    this.showUI();
                }
                // 由Activity处理双击,滑动事件
                ges.onTouchEvent(event);
            } else {
                // 单手指操作
                if (event.getPointerCount() == 1) {
                    // 扩大缩小后，延迟一秒再允许其他动作
                    if (isAfterZoom) {
                        // 后面的事件不再执行
                        return false;
                    }
                    switch (event.getAction()) {
                        case MotionEvent.ACTION_DOWN:
                            mLastMotionX = x;
                            mLastMotionY = y;
                            // 长按时距离测定用X轴位置
                            mCurrentMotionX = x;
                            // 长按时距离测定用Y轴位置
                            mCurrentMotionY = y;
                            // ①在一次触摸中备份偏移量
                            offsetBk = offset;
                            // 平均K線表示非表示
                            if(x < 220) {
                                if(y < Const.FONT_SIZE_MIDDLE + Const.STATUS_BAR_HEIGHT) {
                                    isDispOperate = true;
                                } else if(y >= (Const.FONT_SIZE_MIDDLE + Const.STATUS_BAR_HEIGHT) && y < (Const.FONT_SIZE_MIDDLE * 2 + Const.STATUS_BAR_HEIGHT)) {
                                    this.isAvg10Disp = !this.isAvg10Disp;
                                    isDispOperate = true;
                                    this.showUI();
                                } else if(y >= (Const.FONT_SIZE_MIDDLE * 2 + Const.STATUS_BAR_HEIGHT) && y < (Const.FONT_SIZE_MIDDLE * 3 + Const.STATUS_BAR_HEIGHT)) {
                                    this.isAvg20Disp = !this.isAvg20Disp;
                                    isDispOperate = true;
                                    this.showUI();
                                } else if(y >= (Const.FONT_SIZE_MIDDLE * 3 + Const.STATUS_BAR_HEIGHT) && y < (Const.FONT_SIZE_MIDDLE * 4 + Const.STATUS_BAR_HEIGHT)) {
                                    this.isAvg60Disp = !this.isAvg60Disp;
                                    isDispOperate = true;
                                    this.showUI();
                                }
                            }
                            break;
                        case MotionEvent.ACTION_MOVE:
                            // 长按时距离测定用X轴位置
                            mCurrentMotionX = x;
                            // 长按时距离测定用Y轴位置
                            mCurrentMotionY = y;
                            offset = offset - (int) Math.floor((mLastMotionX - x) / Const.ZOOM_IN);
                            if (offset < 0) {
                                offset = 0;
                            }
                            // ②对最后一次偏移进行备份，以便在抬起时设置到偏移量中
                            offsetLast = offset;
                            this.showUI();
                            // ③一次移动完成后，回设偏移量到该次触摸的初始值，以便多次移动均对初始值进行偏移
                            offset = offsetBk;
                            break;
                        case MotionEvent.ACTION_UP:
                            mLastMotionX = 0;
                            mLastMotionY = 0;
                            mCurrentMotionX = 0;
                            mCurrentMotionY = 0;
                            // ④由于每次移动偏移量都回设为初始值，本次移动全部完成后，将该次触摸抬起之间的最后一次偏移
                            //   设置到偏移量变量中，以便下次从此位置开始移动
                            offset = offsetLast;
                            break;
                    }
                }
                // 双手指操作
                if (event.getPointerCount() == 2) {
                    switch (event.getAction() & MotionEvent.ACTION_MASK) {
                        case MotionEvent.ACTION_MOVE:
                            if (fingerDistance - getDistance(event) > 60) {
                                // 缩小
                                if (Const.ZOOM_OUT == stickWidth) {
                                    stickWidth = Const.ZOOM_IN;
                                    this.showUI();
                                }
                            }
                            if (fingerDistance - getDistance(event) < -60) {
                                // 放大
                                if (Const.ZOOM_IN == stickWidth) {
                                    stickWidth = Const.ZOOM_OUT;
                                    this.showUI();
                                }
                            }
                            break;
                        case MotionEvent.ACTION_UP:
                            fingerDistance = 0;
                            break;
                        case MotionEvent.ACTION_POINTER_DOWN:      // 第二指押下触发
                            if (event.getPointerCount() == 2) {
                                fingerDistance = getDistance(event);
                            }
                            break;
                        case MotionEvent.ACTION_POINTER_UP:        // 第二指抬起触发
                            if (event.getPointerCount() == 2) {
                                fingerDistance = 0;
                                isAfterZoom = true;
                                handler.post(runnable);
                            }
                            break;
                    }
                }
                if(!isDispOperate) {
                    // 由Activity处理双击,滑动事件
                    ges.onTouchEvent(event);
                    // 由View处理滑动事件
                    stickView.onTouchEvent(event);
                }
            }
        } else {
            // 由Activity处理双击,滑动事件
            ges.onTouchEvent(event);
            // 处理长按事件
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

    Runnable runnable = new Runnable() {
        @Override
        public void run() {
            handler.postDelayed(this, 1000);
            isAfterZoom = false;
        }
    };

    @Override
    /* 双击：設定画面表示 */
    public boolean onDoubleTap(MotionEvent e) {
        if (isLongPress) {
            return true;
        }
        if(isEidtMode) {
            Point point = this.ajustPoint(e);
            stickView.onDoubleTap(point);
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
        intent.putExtras(bandle);
        // 子画面起動
        startActivityForResult(intent, Const.REQUEST_CODE);
        // 后面的事件不再触发，到此终止
        return true;
    }

    @Override
    /* 快速滑动：用于切换周期和銘柄 */
    public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
        if (isLongPress || isEidtMode) {
            return true;
        }
        if (e1 == null || e2 == null) {
            return true;
        }
        float x_move = e2.getX() - e1.getX();
        float y_move = e2.getY() - e1.getY();
        // タイムフレーム
        int length = Const.timeFrames.length;
        // 上下移動判定
        if (y_move > 180 && (Math.abs(y_move / x_move) > 2)) {// 向上
            // 前のタイムフレームを特定
            for (int i = 0; i < length; i ++) {
                if (Const.timeFrames[i].equals(timeFrame)) {
                    if (i == 0) {
                        // 一番目の場合、最後のタイムフレームに移動
                        this.timeFrame = Const.timeFrames[length - 1];
                    } else {
                        // 前のタイムフレームに移動
                        this.timeFrame = Const.timeFrames[i - 1];
                    }
                    break;
                }
            }
            // 銘柄切替によりスクロールをリセット
            this.setOffset(0);
            this.showUI();
            requestMarketData();
        } else if (y_move < -180 && (Math.abs(y_move / x_move) > 2)) {// 向下
            // 次のタイムフレームを特定
            for (int i = 0; i < length; i ++) {
                if (Const.timeFrames[i].equals(timeFrame)) {
                    if (i + 1 == length) {
                        // 最後の場合、次のサイクルに移動
                        this.timeFrame = Const.timeFrames[0];
                    } else {
                        // 次のタイムフレームに移動
                        this.timeFrame = Const.timeFrames[i + 1];
                    }
                    break;
                }
            }
            // 銘柄切替によりスクロールをリセット
            this.setOffset(0);
            this.showUI();
            requestMarketData();
        }
        return true;
    }

    @Override
    /* 滑动：K线移动 */
    public boolean onScroll(MotionEvent e1, MotionEvent e2, float distanceX, float distanceY) {
        return true;
    }

    @Override
    public boolean onSingleTapConfirmed(MotionEvent e) {
        Point point = this.ajustPoint(e);
        if (isLongPress || isEidtMode) {
            return true;
        }
        // 按下位置判断
        if (point.y < comnInfo.getStickHeight()) {// K线图内
            stickView.onSingleTapConfirmed(e);
        } else if(point.y < (comnInfo.getStickHeight() + comnInfo.getMacdHeight())) {// MACD图内
            macdView.onSingleTapConfirmed(e);
        } else {// 日期图内
            if (!newFragment.isAdded()) {
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
        return false;
    }

    public Controller getController() {
        return controller;
    }

    public ComnInfo getComnInfo() {
        return comnInfo;
    }

    /*获取两指之间的距离*/
    private int getDistance(MotionEvent event) {
        int distance = 0;
        if (event.getPointerCount() == 2) {
            float x = event.getX(1) - event.getX(0);
            float y = event.getY(1) - event.getY(0);
            distance = (int) Math.sqrt(x * x + y * y);
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
        LinearLayout linearLayout = new LinearLayout(this);
        linearLayout.setOrientation(LinearLayout.VERTICAL);
        linearLayout.setX(60);
        linearLayout.setY(comnInfo.getScreenHeight() - comnInfo.getDateHeight() - 115);
        addContentView(linearLayout, new ViewGroup.LayoutParams(-2, -2));
        final ImageView imageView = new ImageView(this);
        if (isEidtMode) {
            imageView.setImageResource(R.drawable.pressed);
            imageView.setTag(R.drawable.pressed);
        } else {
            imageView.setImageResource(R.drawable.normal);
            imageView.setTag(R.drawable.normal);
        }
        imageView.setScaleType(ImageView.ScaleType.CENTER);
        imageView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if((Integer)imageView.getTag() == R.drawable.normal) {
                    imageView.setImageResource(R.drawable.pressed);
                    imageView.setTag(R.drawable.pressed);
                    isEidtMode = true;
                } else {
                    imageView.setImageResource(R.drawable.normal);
                    imageView.setTag(R.drawable.normal);
                    isEidtMode = false;
                }
            }
        });
        linearLayout.addView(imageView);
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
