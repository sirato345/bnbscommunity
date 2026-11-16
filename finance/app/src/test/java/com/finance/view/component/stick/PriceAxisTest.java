package com.finance.view.component.stick;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class PriceAxisTest {
    @Test
    public void axisReversesAndRoundTripsPrices() {
        assertEquals(0, PriceAxis.yForPrice(200, 100, 200, 400, false));
        assertEquals(400, PriceAxis.yForPrice(100, 100, 200, 400, false));
        assertEquals(0, PriceAxis.yForPrice(100, 100, 200, 400, true));
        assertEquals(400, PriceAxis.yForPrice(200, 100, 200, 400, true));
        for (boolean inverted : new boolean[] {false, true}) {
            assertEquals(125f, PriceAxis.priceForY(
                    PriceAxis.yForPrice(125, 100, 200, 400, inverted),
                    100, 200, 400, inverted), 0.001f);
        }
    }

    @Test
    public void preservesOffscreenPriceCoordinatesAcrossBothOrientations() {
        for (boolean inverted : new boolean[] {false, true}) {
            for (float price : new float[] {75f, 125f, 175f, 225f}) {
                int y = PriceAxis.yForPrice(price, 100, 200, 400, inverted);
                assertEquals(price, PriceAxis.priceForY(y, 100, 200, 400, inverted), 0.001f);
                assertEquals(400, y + PriceAxis.yForPrice(price, 100, 200, 400, !inverted));
            }
        }
    }
}
