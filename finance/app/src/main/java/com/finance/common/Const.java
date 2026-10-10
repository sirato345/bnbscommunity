package com.finance.common;

/**
 * Created by gu zihan on 03/29/2015.
 */
public class Const {

    public static final String DB_NAME = "finance.db";

    public static final String SYMBOL_PROPERTY = "symbol.properties";

    public static final String TREND_LINE_TABLE = "trendline";

    public static final String LOG_FILE = "finance_log_";

    public static final String ERR_FILE = "finance_err_";

    public static final int DB_VERSION = 1;

    public static String[] timeFrames = new String[] { "d", "w", "m", "q", "y" };

    // 平均Ｋ线的平滑周期
    public static final int SMOOTHED = 4;

    // Stick Viewの高さの比率
    public static final int WEIGHT_STICK = 15;

    // MACD Viewの高さの比率
    public static final int WEIGHT_MACD = 8;

    // Date Viewの高さの比率
    public static final int WEIGHT_DATE = 1;

    // 画面全体の高さの比率
    public static final int WEIGHT_ALL = 24;

    // 文字サイズ
    public static final int FONT_SIZE = 40;

    // 文字サイズ
    public static final int FONT_SIZE_MIDDLE = 42;

    // 文字サイズ
    public static final int FONT_SIZE_HALF = FONT_SIZE_MIDDLE / 2;

    // 文字サイズ_ビッグ
    public static final int FONT_SIZE_LARGE = 48;

    // 文字サイズ_ビッグ
    public static final int FONT_SIZE_ARROW = 60;

    // 編集要否の距離
    public static final int EDIT_DISTANCE = 50;

    // タイムフレーム
    public enum TimeFrame {
        d, w, m, q, y;
    }
    // MACDタイプ
    public enum Macd {
        Single, Double;
    }
    // ローソク線タイプ
    public enum K_Line {
        Normal, Average;
    }
    // ローソク線タイプ
    public enum Symbol {
        btc, eth, dow, sp500, nasdaq, nikkei, usdx, usbond, xauusd, xagusd
    }
    // 表示モード（缩小）
    public static final int ZOOM_IN = 10;
    // 表示モード（放大）
    public static final int ZOOM_OUT = 20;

    public static final String KEY_STICK_WIDTH = "StickWidth";

    public static final String KEY_TIME_FRAME = "TimeFrame";

    public static final String KEY_MACD = "Macd";

    public static final String KEY_K_LINE = "K_Line";

    public static final String KEY_SYMBOL = "Symbol";

    public static final String KEY_RESULT = "result";

    public static final String KEY_EDIT_MODE = "eidt_mode";

    public static final String KEY_AVG_10_DISP = "isAvg10Disp";

    public static final String KEY_AVG_20_DISP = "isAvg20Disp";

    public static final String KEY_AVG_60_DISP = "isAvg60Disp";

    public static final String MESSAGE_1 = "銘柄定義ファイルの取得に失敗しました。";

    public static final String MESSAGE_2 = "DB初期データ定義ファイル取得失敗。";

    public static final String MESSAGE_4 = "没有网络连接";

    public static final String MESSAGE_6 = "アクセスURLが不正";

    public static final String SUCCESS = "成功";
    // 設定画面Activityの戻り値取得用
    public final static int REQUEST_CODE = 1;
    // 画面の右側からのスペース数（DP）
    public static final int DEFAULT_OFFSET = 4;

    public static final int STATUS_BAR_HEIGHT = 75;
}
