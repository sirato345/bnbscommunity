package com.finance.view.component;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.RectF;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;

import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.finance.common.Const;
import com.finance.controller.Controller;
import com.finance.view.ChartActivity;
import com.finance.view.component.stick.ComnInfo;
import com.finance.view.component.stick.StickInfo;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

@RunWith(AndroidJUnit4.class)
public class FearGreedNavigationTest {
    @Test
    public void doubleTapReturnsToEthIntradayAfterActivityRecreation() {
        try (ActivityScenario<ChartActivity> scenario = ActivityScenario.launch(ChartActivity.class)) {
            scenario.onActivity(activity -> {
                activity.selectSymbol(Const.Symbol.eth);
                activity.selectCryptoTimeframe("5m");
                doubleTapGauge(activity);
                assertEquals("fng", activity.getSymbol());
            });
            scenario.recreate();
            scenario.onActivity(activity -> {
                assertEquals("fng", activity.getSymbol());
                doubleTapGauge(activity);
                assertEquals("eth", activity.getSymbol());
                assertEquals("5m", activity.getIntradayInterval());
            });
        }
    }

    @Test
    public void doubleTapWithoutPreviousChartOpensBtc() {
        try (ActivityScenario<ChartActivity> scenario = ActivityScenario.launch(ChartActivity.class)) {
            scenario.onActivity(activity -> {
                activity.selectSymbol(Const.Symbol.fng);
                doubleTapGauge(activity);
                assertEquals("btc", activity.getSymbol());
            });
        }
    }

    @Test
    public void doubleTapReturnsToBtcAndItsTimeframe() throws InterruptedException {
        try (ActivityScenario<ChartActivity> scenario = ActivityScenario.launch(ChartActivity.class)) {
            boolean[] panelEnabled = new boolean[1];
            scenario.onActivity(activity -> {
                activity.selectSymbol(Const.Symbol.btc);
                activity.selectCryptoTimeframe("w");
                MacdView macd = findMacdView(activity.findViewById(android.R.id.content));
                assertNotNull(macd);
                boolean previousPanel = macd.isFearGreedPanel();
                singleTapGauge(activity, macd);
                panelEnabled[0] = macd.isFearGreedPanel();
                assertNotEquals(previousPanel, panelEnabled[0]);
                doubleTapGauge(activity);
                assertEquals("fng", activity.getSymbol());
                activity.selectCryptoTimeframe("d");
            });
            Thread.sleep(ViewConfiguration.getDoubleTapTimeout() + 150L);
            scenario.onActivity(activity -> {
                doubleTapGauge(activity);
                assertEquals("btc", activity.getSymbol());
                assertEquals("w", activity.getTimeFrame());
                MacdView macd = findMacdView(activity.findViewById(android.R.id.content));
                assertNotNull(macd);
                assertEquals(panelEnabled[0], macd.isFearGreedPanel());
            });
        }
    }

    @Test
    public void fearGreedTitleKeepsItsSizeAndGaugeOnNarrowScreens() {
        try (ActivityScenario<ChartActivity> scenario = ActivityScenario.launch(ChartActivity.class)) {
            scenario.onActivity(activity -> {
                activity.selectSymbol(Const.Symbol.fng);
                MacdView macd = findMacdView(activity.findViewById(android.R.id.content));
                assertNotNull(macd);
                float originalSize = macd.getTitleTextSize();
                int left = macd.getLeft();
                int top = macd.getTop();
                int right = macd.getRight();
                int bottom = macd.getBottom();
                try {
                    int narrowWidth = Math.round(240 * macd.getResources().getDisplayMetrics().density);
                    macd.layout(left, top, left + narrowWidth, bottom);
                    assertEquals(originalSize, macd.getTitleTextSize(), 0.001f);
                    RectF gauge = macd.getFearGreedGaugeBounds();
                    assertNotNull(gauge);
                    assertEquals(true, gauge.left >= 0 && gauge.right <= narrowWidth);
                } finally {
                    macd.layout(left, top, right, bottom);
                }
            });
        }
    }

    @Test
    public void fearGreedSarControlTogglesOnlyItsOwnChart() {
        try (ActivityScenario<ChartActivity> scenario = ActivityScenario.launch(ChartActivity.class)) {
            scenario.onActivity(activity -> {
                activity.selectSymbol(Const.Symbol.fng);
                activity.isFearGreedSarDisp = false;
                activity.isSarDisp = true;
                StickView view = findStickView(activity.findViewById(android.R.id.content));
                assertNotNull(view);
                ComnInfo info = new ComnInfo(activity);
                StickInfo stick = new StickInfo(info);
                stick.setDate("2026-01-01");
                stick.setOpen(50);
                stick.setClose(50);
                stick.setHigh(50);
                stick.setLow(50);
                view.setData(info, Collections.singletonList(stick), Collections.emptyList());
                int[] location = new int[2];
                view.getLocationOnScreen(location);
                float y = location[1] + view.getHeight() - 2;
                int hitX = -1;
                int[] firstHit = {-1, -1, -1, -1, -1, -1, -1};
                long now = SystemClock.uptimeMillis();
                for (int x = 0; x < view.getWidth(); x++) {
                    MotionEvent event = MotionEvent.obtain(now, now, MotionEvent.ACTION_DOWN,
                            location[0] + x, y, 0);
                    int row = view.getIndicatorControlRow(event);
                    event.recycle();
                    if (row >= 0 && row < firstHit.length && firstHit[row] == -1) {
                        firstHit[row] = x;
                    }
                    if (row == StickView.SAR_CONTROL && hitX == -1) {
                        hitX = location[0] + x;
                    }
                }
                assertTrue(hitX >= 0);
                assertEquals(0, firstHit[0]);
                for (int i = 1; i < firstHit.length; i++) {
                    assertTrue("indicator order " + i, firstHit[i] > firstHit[i - 1]);
                }
                sendTouch(activity, now, now, MotionEvent.ACTION_DOWN, hitX, y);
                sendTouch(activity, now, now + 40, MotionEvent.ACTION_UP, hitX, y);
                assertTrue(activity.isFearGreedSarDisp);
                assertTrue(activity.isSarDisp);
            });
        }
    }

    @Test
    public void fearGreedIndicatorsAreIndependentAndSurviveRecreation() {
        try (ActivityScenario<ChartActivity> scenario = ActivityScenario.launch(ChartActivity.class)) {
            scenario.onActivity(activity -> {
                activity.selectSymbol(Const.Symbol.btc);
                activity.isAvg10Disp = true;
                activity.isAvg20Disp = false;
                activity.isAvg50Disp = true;
                activity.isAvg200Disp = false;
                activity.isSarDisp = true;
                activity.isBollDisp = true;
                activity.selectSymbol(Const.Symbol.fng);
                for (int i = 0; i < 4; i++) {
                    assertEquals(false, activity.isAverageDisplayed(i));
                }
                assertEquals(false, activity.isFearGreedSarDisp);
                assertEquals(false, activity.isFearGreedBollDisp);

                StickView view = findStickView(activity.findViewById(android.R.id.content));
                assertNotNull(view);
                ComnInfo info = new ComnInfo(activity);
                List<StickInfo> sticks = new ArrayList<>();
                for (int i = 0; i < 240; i++) {
                    float close = 50 + (float) Math.sin(i * 0.13) * 20;
                    StickInfo stick = new StickInfo(info);
                    stick.setDate(java.time.LocalDate.of(2026, 11, 26).minusDays(i)
                            .format(java.time.format.DateTimeFormatter.BASIC_ISO_DATE));
                    stick.setOpen(close);
                    stick.setHigh(close + 2);
                    stick.setLow(close - 2);
                    stick.setClose(close);
                    sticks.add(stick);
                }
                for (int control : new int[] {0, 1, 2, 3, 5, StickView.SAR_CONTROL}) {
                    view.setData(info, sticks, Collections.emptyList());
                    Bitmap before = drawStickView(view);
                    tapIndicator(activity, view, control);
                    view.setData(info, sticks, Collections.emptyList());
                    Bitmap after = drawStickView(view);
                    assertTrue("indicator " + control + " did not change the chart",
                            chartPixelsDiffer(before, after));
                }
                for (int i = 0; i < 4; i++) {
                    assertTrue(activity.isAverageDisplayed(i));
                }
                assertTrue(activity.isFearGreedBollDisp);
                assertTrue(activity.isFearGreedSarDisp);
                assertTrue(activity.isAvg10Disp);
                assertEquals(false, activity.isAvg20Disp);
                assertTrue(activity.isAvg50Disp);
                assertEquals(false, activity.isAvg200Disp);
                assertTrue(activity.isSarDisp);
                assertTrue(activity.isBollDisp);
            });
            scenario.recreate();
            scenario.onActivity(activity -> {
                assertEquals("fng", activity.getSymbol());
                for (int i = 0; i < 4; i++) {
                    assertTrue(activity.isAverageDisplayed(i));
                }
                assertTrue(activity.isFearGreedSarDisp);
                assertTrue(activity.isFearGreedBollDisp);
                StickView view = findStickView(activity.findViewById(android.R.id.content));
                assertNotNull(view);
                ComnInfo info = new ComnInfo(activity);
                StickInfo stick = new StickInfo(info);
                stick.setDate("20261126");
                stick.setOpen(50);
                stick.setHigh(50);
                stick.setLow(50);
                stick.setClose(50);
                view.setData(info, Collections.singletonList(stick), Collections.emptyList());
                String[] deleted = new String[2];
                try {
                    Field controllerField = ChartActivity.class.getDeclaredField("controller");
                    controllerField.setAccessible(true);
                    Controller original = (Controller) controllerField.get(activity);
                    Controller recordingController = new Controller(activity) {
                        @Override
                        public void deleteCandleTrendLines(String symbol, String timeFrame) {
                            deleted[0] = symbol;
                            deleted[1] = timeFrame;
                        }
                    };
                    controllerField.set(activity, recordingController);
                    try {
                        tapEraser(activity, view);
                    } finally {
                        controllerField.set(activity, original);
                    }
                } catch (ReflectiveOperationException e) {
                    throw new AssertionError(e);
                }
                assertEquals("fng", deleted[0]);
                assertEquals(activity.getTrendLineTimeFrame(), deleted[1]);
                for (int i = 0; i < 4; i++) {
                    assertEquals(false, activity.isAverageDisplayed(i));
                }
                assertEquals(false, activity.isFearGreedSarDisp);
                assertEquals(false, activity.isFearGreedBollDisp);
                activity.selectSymbol(Const.Symbol.btc);
                assertTrue(activity.isAvg10Disp);
                assertEquals(false, activity.isAvg20Disp);
                assertTrue(activity.isAvg50Disp);
                assertEquals(false, activity.isAvg200Disp);
                assertTrue(activity.isSarDisp);
                assertTrue(activity.isBollDisp);
            });
        }
    }

    private static Bitmap drawStickView(StickView view) {
        Bitmap bitmap = Bitmap.createBitmap(view.getWidth(), view.getHeight(),
                Bitmap.Config.ARGB_8888);
        view.draw(new Canvas(bitmap));
        return bitmap;
    }

    private static boolean chartPixelsDiffer(Bitmap first, Bitmap second) {
        int bottom = Math.max(0, first.getHeight() - 100);
        for (int y = 0; y < bottom; y++) {
            for (int x = 0; x < first.getWidth(); x++) {
                if (first.getPixel(x, y) != second.getPixel(x, y)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static void tapIndicator(ChartActivity activity, StickView view, int control) {
        int[] location = new int[2];
        view.getLocationOnScreen(location);
        float y = location[1] + view.getHeight() - 2;
        long now = SystemClock.uptimeMillis();
        int hitX = -1;
        for (int x = 0; x < view.getWidth(); x++) {
            MotionEvent event = MotionEvent.obtain(now, now, MotionEvent.ACTION_DOWN,
                    location[0] + x, y, 0);
            int row = view.getIndicatorControlRow(event);
            event.recycle();
            if (row == control) {
                hitX = location[0] + x;
                break;
            }
        }
        assertTrue("missing control " + control, hitX >= 0);
        sendTouch(activity, now, now, MotionEvent.ACTION_DOWN, hitX, y);
        sendTouch(activity, now, now + 40, MotionEvent.ACTION_UP, hitX, y);
    }

    private static void tapEraser(ChartActivity activity, StickView view) {
        int[] location = new int[2];
        view.getLocationOnScreen(location);
        float y = location[1] + view.getHeight() - 2;
        long now = SystemClock.uptimeMillis();
        int hitX = -1;
        for (int x = 0; x < view.getWidth(); x++) {
            MotionEvent event = MotionEvent.obtain(now, now, MotionEvent.ACTION_DOWN,
                    location[0] + x, y, 0);
            int row = view.getIndicatorControlRow(event);
            event.recycle();
            if (row == StickView.ERASER_CONTROL) {
                hitX = location[0] + x;
                break;
            }
        }
        assertTrue("missing eraser", hitX >= 0);
        MotionEvent down = MotionEvent.obtain(now, now, MotionEvent.ACTION_DOWN, hitX, y, 0);
        MotionEvent up = MotionEvent.obtain(now, now + 40, MotionEvent.ACTION_UP, hitX, y, 0);
        try {
            activity.dispatchTouchEvent(down);
            activity.dispatchTouchEvent(up);
        } finally {
            down.recycle();
            up.recycle();
        }
    }

    @Test
    public void fearGreedValueIsDrawnAtThePricePosition() {
        try (ActivityScenario<ChartActivity> scenario = ActivityScenario.launch(ChartActivity.class)) {
            scenario.onActivity(activity -> {
                activity.selectSymbol(Const.Symbol.fng);
                ComnInfo info = new ComnInfo(activity);
                StickInfo stick = new StickInfo(info);
                stick.setDate("2026-01-01");
                stick.setClose(50);
                StickInfo previous = new StickInfo(info);
                previous.setDate("2025-12-31");
                previous.setClose(40);
                MacdView view = new MacdView(activity);
                view.setData(info, Arrays.asList(stick, previous), "fng", "d",
                        activity.macd, activity.kLine);
                view.layout(0, 0, info.getScreenWidth(), info.getMacdHeight());
                assertEquals("50", view.getCurrentPriceText());
                Bitmap bitmap = Bitmap.createBitmap(view.getWidth(), view.getHeight(),
                        Bitmap.Config.ARGB_8888);
                view.draw(new Canvas(bitmap));
                boolean priceColorFound = false;
                for (int y = 0; y < Math.min(view.getHeight(), 100); y++) {
                    for (int x = view.getWidth() * 3 / 4; x < view.getWidth(); x++) {
                        if (bitmap.getPixel(x, y) == StickInfo.RISING_COLOR) {
                            priceColorFound = true;
                        }
                    }
                }
                assertTrue(priceColorFound);
            });
        }
    }

    private static void singleTapGauge(ChartActivity activity, MacdView macd) {
        RectF bounds = macd.getFearGreedGaugeBounds();
        assertNotNull(bounds);
        int[] location = new int[2];
        macd.getLocationOnScreen(location);
        long now = SystemClock.uptimeMillis();
        MotionEvent event = MotionEvent.obtain(now, now, MotionEvent.ACTION_UP,
                location[0] + bounds.centerX(), location[1] + bounds.centerY(), 0);
        try {
            activity.onSingleTapConfirmed(event);
        } finally {
            event.recycle();
        }
    }

    private static void doubleTapGauge(ChartActivity activity) {
        MacdView macd = findMacdView(activity.findViewById(android.R.id.content));
        assertNotNull(macd);
        RectF bounds = macd.getFearGreedGaugeBounds();
        assertNotNull(bounds);
        int[] location = new int[2];
        macd.getLocationOnScreen(location);
        float x = location[0] + bounds.centerX();
        float y = location[1] + bounds.centerY();
        long start = SystemClock.uptimeMillis();
        sendTouch(activity, start, start, MotionEvent.ACTION_DOWN, x, y);
        sendTouch(activity, start, start + 40, MotionEvent.ACTION_UP, x, y);
        sendTouch(activity, start + 100, start + 100, MotionEvent.ACTION_DOWN, x, y);
        sendTouch(activity, start + 100, start + 140, MotionEvent.ACTION_UP, x, y);
    }

    private static void sendTouch(ChartActivity activity, long down, long time, int action,
                                  float x, float y) {
        MotionEvent event = MotionEvent.obtain(down, time, action, x, y, 0);
        try {
            activity.onTouchEvent(event);
        } finally {
            event.recycle();
        }
    }

    private static MacdView findMacdView(View view) {
        if (view instanceof MacdView) {
            return (MacdView) view;
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                MacdView result = findMacdView(group.getChildAt(i));
                if (result != null) {
                    return result;
                }
            }
        }
        return null;
    }

    private static StickView findStickView(View view) {
        if (view instanceof StickView) {
            return (StickView) view;
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                StickView result = findStickView(group.getChildAt(i));
                if (result != null) {
                    return result;
                }
            }
        }
        return null;
    }
}
