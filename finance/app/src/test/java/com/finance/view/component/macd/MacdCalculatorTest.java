package com.finance.view.component.macd;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class MacdCalculatorTest {

    @Test
    public void calculatesEmaMacdSignalAndHistogram() {
        float fastEma = MacdCalculator.calculateNextEma(120f, 12, 110f);
        float slowEma = MacdCalculator.calculateNextEma(120f, 26, 100f);
        float macd = MacdCalculator.calculateMacd(fastEma, slowEma);
        float signal = MacdCalculator.calculateNextEma(macd, 9, 0f);
        float histogram = MacdCalculator.calculateHistogram(macd, signal);

        assertEquals(111.53846f, fastEma, 0.0001f);
        assertEquals(101.48148f, slowEma, 0.0001f);
        assertEquals(10.05698f, macd, 0.0001f);
        assertEquals(2.011396f, signal, 0.0001f);
        assertEquals(8.045584f, histogram, 0.0001f);
    }

    @Test
    public void calculatesNegativeHistogramWhenSignalExceedsMacd() {
        assertEquals(-2f, MacdCalculator.calculateHistogram(1f, 3f), 0f);
    }
}
