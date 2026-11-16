package com.finance.view.component.macd;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class IndicatorAxisTest {
    @Test
    public void reversesKdjAndRoundTripsValuesIncludingOutsidePanel() {
        for (boolean inverted : new boolean[] {false, true}) {
            for (float value : new float[] {-30f, -20f, 20f, 80f, 120f, 130f}) {
                float y = IndicatorAxis.yForValue(value, -20f, 120f, 10f, 290f, inverted);
                assertEquals(value, IndicatorAxis.valueForY(y, -20f, 120f,
                        10f, 290f, inverted), 0.0001f);
                assertEquals(300f, y + IndicatorAxis.yForValue(value, -20f, 120f,
                        10f, 290f, !inverted), 0.0001f);
            }

        }
    }

    @Test
    public void reversesMacdAroundZeroAndRoundTripsValues() {
        for (boolean inverted : new boolean[] {false, true}) {
            assertEquals(110, IndicatorAxis.macdY(0, 100, 10, 200, inverted));
            for (float value : new float[] {-150f, -100f, -50f, 0f, 50f, 100f, 150f}) {
                int y = IndicatorAxis.macdY(value, 100, 10, 200, inverted);
                assertEquals(value, IndicatorAxis.macdValue(y, 100, 10, 200, inverted), 0.001f);
                assertEquals(220, y + IndicatorAxis.macdY(value, 100, 10, 200, !inverted));
            }
        }
    }
}
