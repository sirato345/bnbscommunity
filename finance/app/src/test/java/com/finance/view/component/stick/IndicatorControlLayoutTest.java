package com.finance.view.component.stick;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class IndicatorControlLayoutTest {
    @Test
    public void laysOutAndHitsEachControlIncludingGaps() {
        IndicatorControlLayout layout = new IndicatorControlLayout(
                new float[] {20, 20, 20, 30, 30, 40}, 10, 300);
        assertEquals(1f, layout.getScale(), 0f);
        for (int i = 0; i < 6; i++) {
            assertEquals(i, layout.hitTest(layout.getStart(i) + 1));
        }
        assertEquals(0, layout.hitTest(24));
        assertEquals(1, layout.hitTest(25));
        assertEquals(-1, layout.hitTest(-1));
        assertEquals(-1, layout.hitTest(300));
    }

    @Test
    public void scalesRowToFitNarrowScreens() {
        IndicatorControlLayout layout = new IndicatorControlLayout(
                new float[] {20, 20, 20, 30, 30, 40}, 10, 105);
        assertEquals(0.5f, layout.getScale(), 0f);
        assertEquals(5, layout.hitTest(105));
        assertEquals(-1, layout.hitTest(105.01f));
        for (int i = 0; i < 6; i++) {
            assertEquals(i, layout.hitTest(layout.getStart(i) + 1));
        }
    }

    @Test
    public void eraserHasItsOwnHitAreaAfterBoll() {
        IndicatorControlLayout layout = new IndicatorControlLayout(
                new float[] {20, 20, 20, 30, 30, 40, 30}, 10, 125);
        assertEquals(5, layout.hitTest(layout.getStart(5) + 1));
        assertEquals(6, layout.hitTest(layout.getStart(6) + 1));
        assertEquals(6, layout.hitTest(125));
        assertEquals(-1, layout.hitTest(126));
    }
}
