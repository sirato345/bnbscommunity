package com.finance.view.component;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

import static org.junit.Assert.assertEquals;

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
}
