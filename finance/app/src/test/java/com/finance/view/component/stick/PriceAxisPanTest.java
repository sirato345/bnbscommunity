package com.finance.view.component.stick;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class PriceAxisPanTest {
    @Test
    public void dragMovesPriceCoordinatesByTheSamePixels() {
        float min = 100f;
        float max = 300f;
        int height = 800;
        float offset = PriceAxisPan.afterDrag(0f, 120f, height);
        float shift = PriceAxisPan.priceShift(offset, min, max);
        float before = (max - 180f) / (max - min) * height;
        float after = (max + shift - 180f) / (max - min) * height;
        assertEquals(120f, after - before, 0.001f);
    }

    @Test
    public void supportsUpwardDraggingAndSuccessiveGestures() {
        float offset = PriceAxisPan.afterDrag(0f, 200f, 800);
        offset = PriceAxisPan.afterDrag(offset, -400f, 800);
        assertEquals(-0.25f, offset, 0f);
        assertEquals(-50f, PriceAxisPan.priceShift(offset, 100f, 300f), 0f);
        assertEquals(0f, PriceAxisPan.afterDrag(offset, 200f, 800), 0f);
    }

    @Test
    public void zoomedRangeKeepsTheSameScreenOffset() {
        float offset = PriceAxisPan.afterDrag(0f, -100f, 500);
        assertEquals(-40f, PriceAxisPan.priceShift(offset, 100f, 300f), 0.001f);
        assertEquals(-80f, PriceAxisPan.priceShift(offset, 0f, 400f), 0.001f);
    }
}
