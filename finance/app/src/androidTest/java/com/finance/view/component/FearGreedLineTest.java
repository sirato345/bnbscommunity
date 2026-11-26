package com.finance.view.component;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;

import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.finance.common.Const;
import com.finance.view.ChartActivity;
import com.finance.view.component.stick.ComnInfo;
import com.finance.view.component.stick.StickInfo;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertTrue;

@RunWith(AndroidJUnit4.class)
public class FearGreedLineTest {
    @Test
    public void colorsThresholdSegmentsInBothDirections() {
        for (boolean reversed : new boolean[] {false, true}) {
            Bitmap bitmap = Bitmap.createBitmap(101, 3, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(bitmap);
            Paint paint = new Paint();
            paint.setStrokeWidth(1);
            FearGreedLine.draw(canvas, paint, 0, 1, reversed ? 100 : 0,
                    100, 1, reversed ? 0 : 100);
            for (int x : new int[] {10, 30, 50, 70, 90}) {
                assertEquals(FearGreedLine.color(reversed ? 100 - x : x), bitmap.getPixel(x, 1));
            }
        }
    }

    @Test
    public void equalAndBoundaryValuesRemainCorrect() {
        Bitmap bitmap = Bitmap.createBitmap(101, 3, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        Paint paint = new Paint();
        FearGreedLine.draw(canvas, paint, 0, 1, 20, 100, 1, 20);
        assertEquals(FearGreedLine.color(20), bitmap.getPixel(50, 1));
    }

    @Test
    public void extremeBandsRemainVisibleOnBlackWithEitherAxisDirection() {
        try (ActivityScenario<ChartActivity> scenario = ActivityScenario.launch(ChartActivity.class)) {
            scenario.onActivity(activity -> {
                activity.selectSymbol(Const.Symbol.fng);
                activity.isFearGreedSarDisp = false;
                ComnInfo info = new ComnInfo(activity);
                StickInfo stick = new StickInfo(info);
                stick.setLow(45);
                stick.setHigh(55);
                stick.setOpen(50);
                stick.setClose(50);
                StickView view = new StickView(activity);
                view.setData(info, Collections.singletonList(stick), Collections.emptyList());
                view.layout(0, 0, info.getScreenWidth(), info.getStickHeight());

                for (boolean inverted : new boolean[] {false, true}) {
                    stick.setPriceAxisInverted(inverted);
                    Bitmap bitmap = Bitmap.createBitmap(info.getScreenWidth(), info.getStickHeight(),
                            Bitmap.Config.ARGB_8888);
                    view.draw(new Canvas(bitmap));
                    int green = bitmap.getPixel(8, stick.getYPoint(90));
                    int red = bitmap.getPixel(8, stick.getYPoint(10));
                    int middle = bitmap.getPixel(8, stick.getYPoint(50));
                    assertTrue(Color.green(green) >= 25 && Color.green(green) <= 45
                            && Color.green(green) > Color.red(green));
                    assertTrue(Color.red(red) >= 40 && Color.red(red) <= 60
                            && Color.red(red) > Color.green(red));
                    assertEquals(Color.BLACK, middle);
                }
            });
        }
    }

    @Test
    public void indicatorChartsUseTheSameGeometryAsBtc() {
        try (ActivityScenario<ChartActivity> scenario = ActivityScenario.launch(ChartActivity.class)) {
            scenario.onActivity(activity -> {
                activity.macd = Const.Macd.Both.toString();
                activity.selectSymbol(Const.Symbol.btc);
                ComnInfo info = new ComnInfo(activity);
                List<StickInfo> sticks = new ArrayList<>();
                for (int i = 0; i < 80; i++) {
                    float close = 50 + (float) Math.sin(i * 0.3) * 15 + i * 0.1f;
                    StickInfo stick = new StickInfo(info);
                    stick.setDate(String.format(java.util.Locale.US, "2026-%02d-%02d",
                            1 + i / 28, 1 + i % 28));
                    stick.setOpen(close - 1);
                    stick.setHigh(close + 5);
                    stick.setLow(close - 5);
                    stick.setClose(close);
                    stick.setMinValue(0);
                    stick.setMaxValue(100);
                    stick.setIndex(80 - i);
                    stick.calculate();
                    sticks.add(stick);
                }
                Bitmap btc = drawIndicatorChart(activity, info, sticks, "btc");
                activity.selectSymbol(Const.Symbol.fng);
                Bitmap fearGreed = drawIndicatorChart(activity, info, sticks, "fng");
                int[] btcPixels = new int[1600 * 880];
                int[] fearGreedPixels = new int[btcPixels.length];
                btc.getPixels(btcPixels, 0, 1600, 0, 120, 1600, 880);
                fearGreed.getPixels(fearGreedPixels, 0, 1600, 0, 120, 1600, 880);
                assertArrayEquals(btcPixels, fearGreedPixels);
                int nonBlack = 0;
                for (int pixel : btcPixels) {
                    if (pixel != Color.BLACK) {
                        nonBlack++;
                    }
                }
                assertTrue(nonBlack > 0);
            });
        }
    }

    @Test
    public void extremeKdjCurvesDoNotEnterTheValuesRow() {
        try (ActivityScenario<ChartActivity> scenario = ActivityScenario.launch(ChartActivity.class)) {
            scenario.onActivity(activity -> {
                for (Const.Symbol symbol : new Const.Symbol[] {Const.Symbol.btc, Const.Symbol.fng}) {
                    for (Const.Macd mode : new Const.Macd[] {Const.Macd.Both, Const.Macd.KDJ}) {
                        activity.macd = mode.toString();
                        activity.selectSymbol(symbol);
                        ComnInfo info = new ComnInfo(activity);
                        List<StickInfo> sticks = new ArrayList<>();
                        for (int i = 0; i < 80; i++) {
                            float close = i < 3 ? 0 : 100;
                            StickInfo stick = new StickInfo(info);
                            stick.setDate(String.format(java.util.Locale.US, "2026-%02d-%02d",
                                    1 + i / 28, 1 + i % 28));
                            stick.setOpen(close);
                            stick.setHigh(close);
                            stick.setLow(close);
                            stick.setClose(close);
                            stick.setMinValue(0);
                            stick.setMaxValue(100);
                            stick.setIndex(30 - i);
                            stick.calculate();
                            sticks.add(stick);
                        }
                        MacdView view = new MacdView(activity);
                        view.setData(info, sticks, symbol.toString(), "d", mode.toString(),
                                activity.kLine);
                        view.layout(0, 0, 1000, 1000);
                        Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
                        text.setTextSize(Const.FONT_SIZE_LARGE);
                        text.setFakeBoldText(true);
                        float baseline = view.kdjValuesBaseline(text);
                        Bitmap bitmap = Bitmap.createBitmap(1000, 1000, Bitmap.Config.ARGB_8888);
                        bitmap.eraseColor(Color.BLACK);
                        view.drawKdj(new Canvas(bitmap));
                        int top = Math.max(0, (int) Math.floor(baseline + text.ascent()));
                        int bottom = Math.min(1000, (int) Math.ceil(baseline + text.descent()));
                        for (int y = top; y < bottom; y++) {
                            for (int x = 0; x < 1000; x++) {
                                assertEquals(Color.BLACK, bitmap.getPixel(x, y));
                            }
                        }
                    }
                }
            });
        }
    }

    private static Bitmap drawIndicatorChart(ChartActivity activity, ComnInfo info,
                                             List<StickInfo> sticks, String symbol) {
        MacdView view = new MacdView(activity);
        view.setData(info, sticks, symbol, "d", Const.Macd.Both.toString(), activity.kLine);
        view.layout(0, 0, 1600, 1200);
        Bitmap bitmap = Bitmap.createBitmap(1600, 1200, Bitmap.Config.ARGB_8888);
        view.draw(new Canvas(bitmap));
        return bitmap;
    }
}
