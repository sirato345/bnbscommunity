package com.finance.model.db;

import android.database.DatabaseUtils;
import android.database.sqlite.SQLiteDatabase;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.finance.common.Const;

import org.junit.Test;
import org.junit.runner.RunWith;

import static org.junit.Assert.assertEquals;

@RunWith(AndroidJUnit4.class)
public class CandleTrendLineDeletionTest {
    @Test
    public void deletesOnlyCurrentSymbolAndCandleTimeframe() {
        try (SQLiteDatabase db = SQLiteDatabase.create(null)) {
            db.execSQL("CREATE TABLE " + Const.TREND_LINE_TABLE
                    + " (Symbol TEXT, Timeframe TEXT)");
            db.execSQL("INSERT INTO " + Const.TREND_LINE_TABLE + " VALUES "
                    + "('BTC', 'd'), ('BTC', 'd'), ('BTC', 'w'), "
                    + "('BTC', 'd@macd'), ('BTC', 'd@kdj'), ('ETH', 'd')");
            assertEquals(2, DBOperator.deleteCandleTrendLines(db, "BTC", "d"));
            assertEquals(4, DatabaseUtils.queryNumEntries(db, Const.TREND_LINE_TABLE));
            assertEquals(1, DatabaseUtils.queryNumEntries(db, Const.TREND_LINE_TABLE,
                    "Symbol = ? AND Timeframe = ?", new String[] {"BTC", "d@macd"}));
            assertEquals(1, DatabaseUtils.queryNumEntries(db, Const.TREND_LINE_TABLE,
                    "Symbol = ? AND Timeframe = ?", new String[] {"BTC", "d@kdj"}));
            assertEquals(1, DatabaseUtils.queryNumEntries(db, Const.TREND_LINE_TABLE,
                    "Symbol = ? AND Timeframe = ?", new String[] {"BTC", "w"}));
            assertEquals(1, DatabaseUtils.queryNumEntries(db, Const.TREND_LINE_TABLE,
                    "Symbol = ? AND Timeframe = ?", new String[] {"ETH", "d"}));
        }
    }
}
