package com.finance.model.net;

import android.app.Activity;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;

import com.finance.common.Const;
import com.finance.common.LogWriter;
import com.finance.model.db.DBOperator;
import com.finance.model.file.FileOperator;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Observable;
import java.util.Observer;
import java.util.Set;
import java.util.TimeZone;

/**
 * Yahoo market-data requests and DB updates.
 */
public class NetOperatorYahoo extends Observable {

    private static final long SECONDS_PER_DAY = 24L * 60L * 60L;

    private final DBOperator dbOperator;
    private final Activity view;

    public NetOperatorYahoo(DBOperator dbOperator, Observer view) {
        addObserver(view);
        this.dbOperator = dbOperator;
        this.view = (Activity)view;
    }

    public boolean loadPastData(String symbol) {
        String symbolCode = getSymbolCode(symbol, true);
        if (symbolCode == null) {
            return false;
        }
        if (!hasNetwork()) {
            notifyError(symbol, Const.MESSAGE_4, true);
            return false;
        }

        String latestDate = dbOperator.getMaxDBDate(symbol);
        long period1 = latestDate == null ? 0L : toEpochSeconds(latestDate) - SECONDS_PER_DAY;
        new NetConnectorYahoo(this).execute(
                symbol, createChartUrl(symbolCode, period1, getPeriod2()), true);
        return true;
    }

    public void loadIntraDayData(String symbol) {
        String symbolCode = getSymbolCode(symbol, false);
        if (symbolCode == null) {
            return;
        }
        if (!hasNetwork()) {
            notifyError(symbol, Const.MESSAGE_4);
            return;
        }

        String latestDate = dbOperator.getMaxDBDate(symbol);
        long period1 = latestDate == null
                ? getMarketDayStart(symbol)
                : toEpochSeconds(latestDate) - SECONDS_PER_DAY;
        new NetConnectorYahoo(this).execute(
                symbol, createChartUrl(symbolCode, period1, getPeriod2()), false);
    }

    private long getMarketDayStart(String symbol) {
        Calendar marketDay = Calendar.getInstance(getMarketTimeZone(symbol));
        marketDay.set(Calendar.HOUR_OF_DAY, 0);
        marketDay.set(Calendar.MINUTE, 0);
        marketDay.set(Calendar.SECOND, 0);
        marketDay.set(Calendar.MILLISECOND, 0);
        return marketDay.getTimeInMillis() / 1000L;
    }

    private TimeZone getMarketTimeZone(String symbol) {
        switch (symbol) {
            case "btc":
            case "eth":
                return TimeZone.getTimeZone("UTC");
            case "nikkei":
                return TimeZone.getTimeZone("Asia/Tokyo");
            default:
                return TimeZone.getTimeZone("America/New_York");
        }
    }

    private String getSymbolCode(String symbol, boolean historical) {
        String symbolCode = FileOperator.getSymbolMap().getProperty(symbol);
        if (symbolCode == null || symbolCode.trim().isEmpty()) {
            notifyError(symbol, "Unsupported market symbol: " + symbol, historical);
            return null;
        }
        return symbolCode;
    }

    private String createChartUrl(String symbolCode, long period1, long period2) {
        try {
            return "https://query1.finance.yahoo.com/v8/finance/chart/"
                    + URLEncoder.encode(symbolCode, "UTF-8")
                    + "?period1=" + period1 + "&period2=" + period2
                    + "&interval=1d&events=history";
        } catch (UnsupportedEncodingException e) {
            throw new IllegalStateException("UTF-8 is not supported", e);
        }
    }

    private long getPeriod2() {
        return Calendar.getInstance(TimeZone.getTimeZone("UTC")).getTimeInMillis()
                / 1000L + SECONDS_PER_DAY;
    }

    private long toEpochSeconds(String date) {
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyyMMdd", Locale.US);
        dateFormat.setLenient(false);
        dateFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
        try {
            return dateFormat.parse(date).getTime() / 1000L;
        } catch (ParseException e) {
            throw new IllegalArgumentException("Invalid latest database date: " + date, e);
        }
    }

    public void updateHistory(String symbol, List<String> records) {
        if (!hasCompleteCryptoHistory(symbol, records)) {
            long[] missingDateRange = getMissingCryptoHistoryRange(symbol, records);
            if (missingDateRange != null) {
                new NetConnectorBinance(this, symbol, records,
                        missingDateRange[0], missingDateRange[1]).execute();
            } else {
                notifyHistoryGap(symbol);
            }
            return;
        }
        updateDB(symbol, records, true);
    }

    public void updateHistoryFromFallback(String symbol, List<String> records) {
        if (!hasCompleteCryptoHistory(symbol, records)) {
            notifyHistoryGap(symbol);
            return;
        }
        updateDB(symbol, records, true);
    }

    public void notifyFallbackError(String symbol, String error) {
        notifyError(symbol, error, true);
    }

    private void notifyHistoryGap(String symbol) {
        String error = "Yahoo and Binance history are missing one or more recent daily records";
        LogWriter.getWriter().printLog("銘柄：" + symbol + "、" + error);
        notifyError(symbol, error, true);
    }

    private long[] getMissingCryptoHistoryRange(String symbol, List<String> records) {
        if (!"btc".equals(symbol) && !"eth".equals(symbol)) {
            return null;
        }

        String latestDate = dbOperator.getMaxDBDate(symbol);
        if (latestDate == null) {
            return null;
        }

        Set<String> recordDates = new HashSet<>();
        for (String record : records) {
            int separator = record.indexOf(',');
            if (separator > 0) {
                recordDates.add(record.substring(0, separator));
            }
        }

        Calendar expectedDate = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
        expectedDate.setTimeInMillis(toEpochSeconds(latestDate) * 1000L);
        expectedDate.add(Calendar.DAY_OF_MONTH, 1);
        Calendar lastCompletedDay = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
        lastCompletedDay.set(Calendar.HOUR_OF_DAY, 0);
        lastCompletedDay.set(Calendar.MINUTE, 0);
        lastCompletedDay.set(Calendar.SECOND, 0);
        lastCompletedDay.set(Calendar.MILLISECOND, 0);
        lastCompletedDay.add(Calendar.DAY_OF_MONTH, -1);

        long firstMissingDay = -1L;
        while (!expectedDate.after(lastCompletedDay)) {
            String expectedDateString = formatUtcDate(expectedDate.getTimeInMillis());
            if (!recordDates.contains(expectedDateString)) {
                firstMissingDay = expectedDate.getTimeInMillis() / 1000L;
                break;
            }
            expectedDate.add(Calendar.DAY_OF_MONTH, 1);
        }
        if (firstMissingDay < 0L) {
            return null;
        }
        Calendar endDate = (Calendar) lastCompletedDay.clone();
        endDate.add(Calendar.DAY_OF_MONTH, 1);
        return new long[] {firstMissingDay, endDate.getTimeInMillis() / 1000L};
    }

    private boolean hasCompleteCryptoHistory(String symbol, List<String> records) {
        if (!"btc".equals(symbol) && !"eth".equals(symbol)) {
            return true;
        }

        String latestDate = dbOperator.getMaxDBDate(symbol);
        if (latestDate == null) {
            return true;
        }

        Set<String> recordDates = new HashSet<>();
        for (String record : records) {
            int separator = record.indexOf(',');
            if (separator > 0) {
                recordDates.add(record.substring(0, separator));
            }
        }

        Calendar expectedDate = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
        expectedDate.setTimeInMillis(toEpochSeconds(latestDate) * 1000L);
        expectedDate.add(Calendar.DAY_OF_MONTH, 1);
        Calendar lastCompletedDay = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
        lastCompletedDay.set(Calendar.HOUR_OF_DAY, 0);
        lastCompletedDay.set(Calendar.MINUTE, 0);
        lastCompletedDay.set(Calendar.SECOND, 0);
        lastCompletedDay.set(Calendar.MILLISECOND, 0);
        lastCompletedDay.add(Calendar.DAY_OF_MONTH, -1);

        while (!expectedDate.after(lastCompletedDay)) {
            String expectedDateString = formatUtcDate(expectedDate.getTimeInMillis());
            if (!recordDates.contains(expectedDateString)) {
                return false;
            }
            expectedDate.add(Calendar.DAY_OF_MONTH, 1);
        }
        return true;
    }

    private String formatUtcDate(long timeMillis) {
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
        dateFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
        return dateFormat.format(new Date(timeMillis));
    }

    public void updateLatestData(String symbol, List<String> records) {
        updateDB(symbol, records, false);
    }

    private void updateDB(String symbol, List<String> records, boolean historical) {
        try {
            dbOperator.updateDB(symbol, Const.TimeFrame.d.toString(), records);
            setChanged();
            notifyObservers(new Object[] {true, symbol, historical});
        } catch (RuntimeException e) {
            notifyError(symbol,
                    e.getMessage() == null ? "Failed to save Yahoo market data" : e.getMessage(),
                    historical);
        }
    }

    public void notifyError(String symbol, String errorMsg) {
        notifyError(symbol, errorMsg, false);
    }

    public void notifyError(String symbol, String errorMsg, boolean historical) {
        setChanged();
        notifyObservers(new Object[] {false, symbol, errorMsg, historical});
    }

    private boolean hasNetwork() {
        ConnectivityManager manager = (ConnectivityManager)
                view.getSystemService(Activity.CONNECTIVITY_SERVICE);
        if (manager == null) {
            return false;
        }
        NetworkInfo networkInfo = manager.getActiveNetworkInfo();
        return networkInfo != null && networkInfo.isConnectedOrConnecting();
    }
}
