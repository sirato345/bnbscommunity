package com.finance.view.component;

import org.junit.Test;

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
}
