package com.finance.view.component;

import org.junit.Test;

import com.finance.view.component.stick.StickInfo;

import static org.junit.Assert.assertEquals;

public class FearGreedLineColorTest {
    @Test
    public void preservesColorsAtEachThreshold() {
        assertEquals(0xFFEA3943, FearGreedLine.color(19.9f));
        assertEquals(0xFFFF9500, FearGreedLine.color(20));
        assertEquals(0xFFFFE600, FearGreedLine.color(40));
        assertEquals(0xFF7CFF00, FearGreedLine.color(60));
        assertEquals(0xFF16C784, FearGreedLine.color(80));
    }

    @Test
    public void priceColorFollowsThePreviousDisplayedPeriod() {
        assertEquals(StickInfo.RISING_COLOR,
                MacdView.fearGreedPriceColor(51, "20261130", 50f, "20261031", "m"));
        assertEquals(StickInfo.FALLING_COLOR,
                MacdView.fearGreedPriceColor(49, "20261130", 50f, "20261031", "m"));
        assertEquals(StickInfo.FALLING_COLOR,
                MacdView.fearGreedPriceColor(50.4f, "20261130", 50.1f, "20261031", "m"));
        assertEquals(StickInfo.FALLING_COLOR,
                MacdView.fearGreedPriceColor(50, "20261130", null, null, "m"));
        assertEquals(StickInfo.FALLING_COLOR,
                MacdView.fearGreedPriceColor(50, "20261130", Float.NaN, "20261031", "m"));
        assertEquals(StickInfo.FALLING_COLOR,
                MacdView.fearGreedPriceColor(51, "20261130", 50f, "20260930", "m"));
    }
}
