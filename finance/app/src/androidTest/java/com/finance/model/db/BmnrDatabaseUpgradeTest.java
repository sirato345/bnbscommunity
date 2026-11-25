package com.finance.model.db;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.finance.common.MyApp;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

@RunWith(AndroidJUnit4.class)
public class BmnrDatabaseUpgradeTest {
    @Test
    public void pendingBackfillOverridesRecentHistorySyncSuccess() {
        MyApp app = ApplicationProvider.getApplicationContext();
        String symbol = "bmnr_test_" + UUID.randomUUID();
        try {
            app.markHistorySyncSucceeded(symbol);
            assertFalse(app.isHistorySyncDue(symbol, false));
            assertTrue(app.isHistorySyncDue(symbol, true));
        } finally {
            app.getSharedPreferences("history_sync", android.content.Context.MODE_PRIVATE)
                    .edit().remove(symbol + "_success").commit();
        }
    }

    @Test
    public void backfillRebuildsOlderPeriodsAndPreservesExistingOlderRows() throws Exception {
        Field helperField = DBOperator.class.getDeclaredField("dbHelper");
        Field writableField = DBOperator.class.getDeclaredField("dbWritable");
        Field readableField = DBOperator.class.getDeclaredField("dbReadable");
        helperField.setAccessible(true);
        writableField.setAccessible(true);
        readableField.setAccessible(true);
        Object previousHelper = helperField.get(null);
        Object previousWritable = writableField.get(null);
        Object previousReadable = readableField.get(null);
        try (SQLiteDatabase db = SQLiteDatabase.create(null);
             DBHelper helper = new DBHelper(ApplicationProvider.getApplicationContext())) {
            for (String timeframe : new String[] {"d", "w", "m", "q", "y"}) {
                db.execSQL("CREATE TABLE bmnr_" + timeframe
                        + " (Date TEXT PRIMARY KEY, Open REAL, High REAL, Low REAL, Close REAL)");
            }
            helperField.set(null, helper);
            writableField.set(null, db);
            readableField.set(null, db);
            DBOperator operator = new DBOperator(ApplicationProvider.getApplicationContext());
            operator.updateDB("bmnr", "d", Arrays.asList(
                    "2025-06-05,7.25,7.89,6.8,7.75",
                    "2025-06-06,7.35,7.69,6.31,7.545"));
            db.execSQL("INSERT INTO bmnr_d VALUES ('20210503', 1, 2, 1, 2)");

            operator.updateDB("bmnr", "d", Arrays.asList(
                    "2021-05-03,95.6,95.6,76,76",
                    "2025-06-04,14.82,22,14.82,19",
                    "2025-06-05,7.25,7.89,6.8,7.75",
                    "2025-06-06,7.35,7.69,6.31,7.545"), true);

            try (Cursor cursor = db.rawQuery(
                    "SELECT Close FROM bmnr_d WHERE Date = '20210503'", null)) {
                assertTrue(cursor.moveToFirst());
                assertEquals(2f, cursor.getFloat(0), 0f);
            }
            for (String timeframe : new String[] {"w", "m", "q", "y"}) {
                try (Cursor cursor = db.rawQuery(
                        "SELECT count(*) FROM bmnr_" + timeframe + " WHERE Date < '20220101'", null)) {
                    assertTrue(cursor.moveToFirst());
                    assertEquals(1, cursor.getInt(0));
                }
            }
            try (Cursor cursor = db.rawQuery(
                    "SELECT Open, High FROM bmnr_m WHERE Date = '20250630'", null)) {
                assertTrue(cursor.moveToFirst());
                assertEquals(14.82f, cursor.getFloat(0), 0.001f);
                assertEquals(22f, cursor.getFloat(1), 0f);
            }
            db.execSQL("INSERT INTO bmnr_d VALUES ('20210430', 100, 200, 100, 200)");
            db.execSQL("INSERT INTO bmnr_w VALUES ('20210425', 100, 200, 100, 200)");
            db.execSQL("INSERT INTO bmnr_m VALUES ('20210430', 100, 200, 100, 200)");
            db.execSQL("INSERT INTO bmnr_q VALUES ('20210331', 100, 200, 100, 200)");
            db.execSQL("INSERT OR REPLACE INTO bmnr_q VALUES ('20210630', 100, 200, 1, 2)");
            db.execSQL("INSERT OR REPLACE INTO bmnr_y VALUES ('20211231', 100, 200, 1, 2)");

            operator.trimBmnrHistory();
            operator.updateDB("bmnr", "d", Arrays.asList("2021-04-30,100,200,100,200"));

            for (String timeframe : new String[] {"d", "w", "m", "q"}) {
                try (Cursor cursor = db.rawQuery(
                        "SELECT count(*) FROM bmnr_" + timeframe + " WHERE Date < '20210501'", null)) {
                    assertTrue(cursor.moveToFirst());
                    assertEquals(0, cursor.getInt(0));
                }
            }
            for (String table : new String[] {"bmnr_q", "bmnr_y"}) {
                try (Cursor cursor = db.rawQuery(
                        "SELECT Open, High FROM " + table + " WHERE Date LIKE '2021%'", null)) {
                    assertTrue(cursor.moveToFirst());
                    assertEquals(1f, cursor.getFloat(0), 0f);
                    assertEquals(2f, cursor.getFloat(1), 0f);
                }
            }
            assertEquals("20210503", operator.getMinDBDate("bmnr"));
        } finally {
            helperField.set(null, previousHelper);
            writableField.set(null, previousWritable);
            readableField.set(null, previousReadable);
        }
    }

    @Test
    public void upgradeAddsBmnrTablesAndPreservesExistingData() {
        try (SQLiteDatabase db = SQLiteDatabase.create(null);
             DBHelper helper = new DBHelper(ApplicationProvider.getApplicationContext())) {
            db.execSQL("CREATE TABLE dow_d (Date TEXT PRIMARY KEY, Open REAL, High REAL, Low REAL, Close REAL)");
            db.execSQL("INSERT INTO dow_d VALUES ('20261124', 1, 2, 1, 2)");

            helper.onUpgrade(db, 12, 13);

            for (String timeframe : new String[] {"d", "w", "m", "q", "y"}) {
                try (Cursor cursor = db.rawQuery("SELECT count(*) FROM bmnr_" + timeframe, null)) {
                    assertTrue(cursor.moveToFirst());
                    assertEquals(0, cursor.getInt(0));
                }
            }
            try (Cursor cursor = db.rawQuery("SELECT Close FROM dow_d WHERE Date = '20261124'", null)) {
                assertTrue(cursor.moveToFirst());
                assertEquals(2f, cursor.getFloat(0), 0f);
            }
        }
    }
}
