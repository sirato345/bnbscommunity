package com.finance.model.net;

import android.app.Activity;
import android.net.ConnectivityManager;

import com.finance.common.Const;
import com.finance.common.TimeZoneUtil;
import com.finance.model.db.DBOperator;
import com.finance.model.file.FileOperator;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Observable;
import java.util.Observer;
import java.util.TimeZone;

/**
 * ネット操作クラス
 */
public class NetOperatorYahoo extends Observable {

    private DBOperator dbOperator;
    private Activity view;

    public NetOperatorYahoo(DBOperator dbOperator, Observer view) {
        // 監視者を設定
        this.addObserver(view);
        this.dbOperator = dbOperator;
        this.view = (Activity)view;
    }

    public void loadPastData(String symbol) {
        String symbolCode = FileOperator.getSymbolMap().getProperty(symbol);
        if (symbolCode == null || symbolCode.trim().isEmpty()) {
            notifyError(symbol, "Unsupported market symbol: " + symbol, true);
            return;
        }
        if (!hasNetwork()) {
            notifyError(symbol, Const.MESSAGE_4, true);
            return;
        }
        String dateFrom = dbOperator.getDateFrom(symbol, Const.TimeFrame.d.toString());
        String dateTo = getDateTo();
        String url = createChartUrl(symbolCode, dateFrom, dateTo);
        new NetConnectorYahoo(this).execute(symbol, url, true);
    }

    // 指定した銘柄の日足データを更新
    public void loadIntraDayData(String symbol) {
        String symbolCode = FileOperator.getSymbolMap().getProperty(symbol);
        if (hasNetwork() && symbolCode != null) {
            // DB内数据最大日期
            String maxDBDate = dbOperator.getMaxDBDate(symbol);
            TimeZone timeZone = TimeZone.getTimeZone(TimeZoneUtil.getTimeZone(symbol));
            boolean isCrypto = "btc".equals(symbol) || "eth".equals(symbol);
            if (isCrypto) {
                timeZone = TimeZone.getTimeZone("UTC");
            }
            Calendar start = Calendar.getInstance(timeZone);
            Calendar end = Calendar.getInstance(timeZone);
            try {
                SimpleDateFormat dateFormat = new SimpleDateFormat("yyyyMMdd");
                dateFormat.setTimeZone(timeZone);
                start.setTime(dateFormat.parse(maxDBDate));
            } catch (ParseException e) {
                notifyError(symbol, "Invalid latest database date: " + maxDBDate);
                return;
            }
            start.add(Calendar.DAY_OF_MONTH, 1);
            end.setTime(start.getTime());
            end.add(Calendar.DAY_OF_MONTH, 1);
            if (!isCrypto) {
                String nextBusinessDate = TimeZoneUtil.getLocaleNextBusinessDate(
                        symbol, dbOperator, maxDBDate);
                try {
                    SimpleDateFormat dateFormat = new SimpleDateFormat("yyyyMMdd");
                    dateFormat.setTimeZone(timeZone);
                    start.setTime(dateFormat.parse(nextBusinessDate));
                    end.setTime(start.getTime());
                    end.add(Calendar.DAY_OF_MONTH, 1);
                } catch (ParseException e) {
                    notifyError(symbol, "Invalid market date: " + nextBusinessDate);
                    return;
                }
            }
            String url = createChartUrl(symbolCode,
                    start.getTimeInMillis() / 1000L, end.getTimeInMillis() / 1000L);
            NetConnectorYahoo netConnector = new NetConnectorYahoo(this);
            netConnector.execute(symbol, url, false);
        } else {
            notifyError(symbol, symbolCode == null ? "Unsupported market symbol: " + symbol : Const.MESSAGE_4);
        }
    }

    private String createChartUrl(String symbolCode, String dateFrom, String dateTo) {
        return createChartUrl(symbolCode, toEpochSeconds(dateFrom), toEpochSeconds(dateTo));
    }

    private String createChartUrl(String symbolCode, long period1, long period2) {
        try {
            return "https://query1.finance.yahoo.com/v8/finance/chart/"
                    + URLEncoder.encode(symbolCode, "UTF-8")
                    + "?period1=" + period1 + "&period2=" + period2
                    + "&interval=1d&events=history";
        } catch (UnsupportedEncodingException e) {
            throw new IllegalStateException(e);
        }
    }

    private String getDateTo() {
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyyMMdd");
        TimeZone utc = TimeZone.getTimeZone("UTC");
        dateFormat.setTimeZone(utc);
        Calendar calendar = Calendar.getInstance(utc);
        calendar.add(Calendar.DAY_OF_MONTH, 1);
        return dateFormat.format(calendar.getTime());
    }

    private long toEpochSeconds(String date) {
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyyMMdd");
        dateFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
        try {
            return dateFormat.parse(date).getTime() / 1000L;
        } catch (ParseException e) {
            throw new IllegalArgumentException("Invalid market data date: " + date, e);
        }
    }

    // ネットデータによりＤＢデータ更新
    public void updateDB(String symbol, String intraDayData) {
        // 画面切り替えはネットデータ返信により早い場合も、取得したデータでDBを更新
        // しかし、画面側は表示中の銘柄とタイムフレーム一致する場合のみ刷新
        dbOperator.updateDB(symbol, Const.TimeFrame.d.toString(), intraDayData);
        // DBデータ変更を画面にお知らせ
        Object[] params = new Object[] {true, symbol, false};
        this.setChanged();
        this.notifyObservers(params);
    }

    public void updateDB(String symbol, List<String> historyData) {
        dbOperator.updateDB(symbol, Const.TimeFrame.d.toString(), historyData);
        Object[] params = new Object[] {true, symbol, true};
        // 状態変更設定
        this.setChanged();
        // 画面通知
        this.notifyObservers(params);
    }

    // ネットワック接続異常
    public void notifyError(String symbol, String errorMsg) {
        notifyError(symbol, errorMsg, false);
    }

    public void notifyError(String symbol, String errorMsg, boolean historical) {
        Object[] params = new Object[] {false, symbol, errorMsg, historical};
        // 状態変更設定
        this.setChanged();
        // 画面通知
        this.notifyObservers(params);
    }

    /* ネットワック有無判定 */
    private boolean hasNetwork() {
        ConnectivityManager manager = (ConnectivityManager) view.
                getSystemService(Activity.CONNECTIVITY_SERVICE);
        boolean wifi = manager.getNetworkInfo(ConnectivityManager.TYPE_WIFI).
                isConnectedOrConnecting();
        boolean internet = manager.getNetworkInfo(ConnectivityManager.TYPE_MOBILE)
                .isConnectedOrConnecting();
        if (wifi || internet) {
            return true;
        } else {
            return false;
        }
    }
}