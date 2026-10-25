package com.finance.view.component.macd;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class MacdDivergenceCalculatorTest {

    private static final int SIZE = 30;
    private static final int OLD = 20;
    private static final int NEW = 8;

    @Test
    public void detectsTopDivergence() {
        float[] highs = new float[SIZE];
        float[] macd = new float[SIZE];
        macd[OLD] = 8f;
        macd[NEW] = 4f;
        highs[OLD] = 20f;
        highs[NEW] = 25f;

        MacdDivergenceCalculator.Divergence[] divergences = find(highs, highs, macd, macd);

        assertType(MacdDivergenceCalculator.Type.TOP, divergences[NEW]);
        assertEquals(OLD, divergences[NEW].getComparisonIndex());
    }

    @Test
    public void detectsTopDivergenceWhenPeaksAreTenBarsApart() {
        float[] highs = new float[SIZE];
        float[] macd = new float[SIZE];
        macd[18] = 8f;
        macd[NEW] = 4f;
        highs[18] = 20f;
        highs[NEW] = 25f;

        assertType(MacdDivergenceCalculator.Type.TOP, find(highs, highs, macd, macd)[NEW]);
    }

    @Test
    public void ignoresTopDivergenceWhenPeaksAreNineBarsApart() {
        float[] highs = new float[SIZE];
        float[] macd = new float[SIZE];
        macd[17] = 8f;
        macd[NEW] = 4f;
        highs[17] = 20f;
        highs[NEW] = 25f;

        assertType(MacdDivergenceCalculator.Type.NONE, find(highs, highs, macd, macd)[NEW]);
    }

    @Test
    public void ignoresTopDivergenceWhenPeaksAreBelowZero() {
        float[] highs = new float[SIZE];
        float[] macd = new float[SIZE];
        macd[OLD] = -2f;
        macd[NEW] = -4f;
        highs[OLD] = 20f;
        highs[NEW] = 25f;

        assertType(MacdDivergenceCalculator.Type.NONE, find(highs, highs, macd, macd)[NEW]);
    }

    @Test
    public void ignoresTopWhenMacdAndPriceBothRise() {
        float[] highs = new float[SIZE];
        float[] macd = new float[SIZE];
        macd[OLD] = 4f;
        macd[NEW] = 8f;
        highs[OLD] = 15f;
        highs[NEW] = 20f;

        assertType(MacdDivergenceCalculator.Type.NONE, find(highs, highs, macd, macd)[NEW]);
    }

    @Test
    public void ignoresTopDivergenceWhenFirstHistogramPeakIsShorter() {
        float[] highs = new float[SIZE];
        float[] macd = new float[SIZE];
        float[] histogram = new float[SIZE];
        histogram[OLD] = 4f;
        histogram[NEW] = 8f;
        highs[OLD] = 25f;
        highs[NEW] = 20f;

        assertType(MacdDivergenceCalculator.Type.NONE,
                find(highs, highs, macd, histogram)[NEW]);
    }

    @Test
    public void requiresThreeHistogramBarsOnEachSideOfPeaks() {
        float[] highs = new float[SIZE];
        float[] macd = new float[SIZE];
        float[] histogram = new float[SIZE];
        histogram[OLD] = 8f;
        histogram[NEW] = 4f;
        histogram[NEW + 2] = 9f;
        highs[OLD] = 20f;
        highs[NEW] = 25f;

        assertType(MacdDivergenceCalculator.Type.NONE,
                find(highs, highs, macd, histogram)[NEW]);
    }

    @Test
    public void classifiesTopByHistogramWhenMacdIsBelowZero() {
        float[] highs = new float[SIZE];
        float[] macd = new float[SIZE];
        float[] histogram = new float[SIZE];
        histogram[OLD] = 8f;
        histogram[NEW] = 4f;
        macd[NEW] = -1f;
        highs[OLD] = 20f;
        highs[NEW] = 25f;

        assertType(MacdDivergenceCalculator.Type.TOP,
                find(highs, highs, macd, histogram)[NEW]);
    }

    @Test
    public void marksDeathCrossTopDivergence() {
        float[] highs = new float[SIZE];
        float[] macd = new float[SIZE];
        macd[OLD] = 8f;
        macd[NEW] = 4f;
        macd[NEW - 1] = -1f;
        highs[OLD] = 20f;
        highs[NEW] = 25f;

        assertType(MacdDivergenceCalculator.Type.TOP_DEATH_CROSS,
                find(highs, highs, macd, macd)[NEW]);
    }

    @Test
    public void comparesHighsForTopAndLowsForBottom() {
        float[] highs = new float[SIZE];
        float[] lows = new float[SIZE];
        float[] macd = new float[SIZE];
        macd[OLD] = 8f;
        macd[NEW] = 4f;
        highs[OLD] = 20f;
        highs[NEW] = 25f;
        lows[OLD] = 30f;
        lows[NEW] = 10f;

        assertType(MacdDivergenceCalculator.Type.TOP, find(highs, lows, macd, macd)[NEW]);
    }

    @Test
    public void ignoresTopDivergenceWhenHighsDoNotRise() {
        float[] highs = new float[SIZE];
        float[] lows = new float[SIZE];
        float[] macd = new float[SIZE];
        macd[OLD] = 8f;
        macd[NEW] = 4f;
        highs[OLD] = 25f;
        highs[NEW] = 20f;
        lows[OLD] = 10f;
        lows[NEW] = 30f;

        assertType(MacdDivergenceCalculator.Type.NONE, find(highs, lows, macd, macd)[NEW]);
    }

    @Test
    public void detectsBottomDivergence() {
        float[] lows = new float[SIZE];
        float[] macd = new float[SIZE];
        macd[OLD] = -8f;
        macd[NEW] = -4f;
        lows[OLD] = -20f;
        lows[NEW] = -25f;

        MacdDivergenceCalculator.Divergence[] divergences = find(lows, lows, macd, macd);

        assertType(MacdDivergenceCalculator.Type.BOTTOM, divergences[NEW]);
        assertEquals(OLD, divergences[NEW].getComparisonIndex());
    }

    @Test
    public void ignoresBottomDivergenceWhenTroughsAreNineBarsApart() {
        float[] lows = new float[SIZE];
        float[] macd = new float[SIZE];
        macd[17] = -8f;
        macd[NEW] = -4f;
        lows[17] = -20f;
        lows[NEW] = -25f;

        assertType(MacdDivergenceCalculator.Type.NONE, find(lows, lows, macd, macd)[NEW]);
    }

    @Test
    public void detectsBottomDivergenceWhenTroughsAreTenBarsApart() {
        float[] lows = new float[SIZE];
        float[] macd = new float[SIZE];
        macd[18] = -8f;
        macd[NEW] = -4f;
        lows[18] = -20f;
        lows[NEW] = -25f;

        assertType(MacdDivergenceCalculator.Type.BOTTOM, find(lows, lows, macd, macd)[NEW]);
    }

    @Test
    public void ignoresBottomDivergenceWhenTroughsAreAboveZero() {
        float[] lows = new float[SIZE];
        float[] macd = new float[SIZE];
        macd[OLD] = 2f;
        macd[NEW] = 4f;
        lows[OLD] = 20f;
        lows[NEW] = 25f;

        assertType(MacdDivergenceCalculator.Type.NONE, find(lows, lows, macd, macd)[NEW]);
    }

    @Test
    public void ignoresBottomDivergenceWhenFirstHistogramTroughIsShorter() {
        float[] lows = new float[SIZE];
        float[] macd = new float[SIZE];
        float[] histogram = new float[SIZE];
        histogram[OLD] = -4f;
        histogram[NEW] = -8f;
        lows[OLD] = -25f;
        lows[NEW] = -20f;

        assertType(MacdDivergenceCalculator.Type.NONE,
                find(lows, lows, macd, histogram)[NEW]);
    }

    @Test
    public void classifiesBottomByHistogramWhenMacdIsAboveZero() {
        float[] lows = new float[SIZE];
        float[] macd = new float[SIZE];
        float[] histogram = new float[SIZE];
        histogram[OLD] = -8f;
        histogram[NEW] = -4f;
        macd[NEW] = 1f;
        lows[OLD] = -20f;
        lows[NEW] = -25f;

        assertType(MacdDivergenceCalculator.Type.BOTTOM,
                find(lows, lows, macd, histogram)[NEW]);
    }

    @Test
    public void marksGoldenCrossBottomDivergence() {
        float[] lows = new float[SIZE];
        float[] macd = new float[SIZE];
        macd[OLD] = -8f;
        macd[NEW] = -4f;
        macd[NEW - 1] = 2f;
        lows[OLD] = -20f;
        lows[NEW] = -25f;

        assertType(MacdDivergenceCalculator.Type.BOTTOM_GOLDEN_CROSS,
                find(lows, lows, macd, macd)[NEW]);
    }

    @Test
    public void doesNotCompareAgainstPeaksOutsideVisibleRange() {
        float[] highs = new float[SIZE];
        float[] macd = new float[SIZE];
        float[] signal = new float[SIZE];
        macd[OLD] = 8f;
        macd[NEW] = 4f;
        highs[OLD] = 20f;
        highs[NEW] = 25f;

        MacdDivergenceCalculator.Divergence[] divergences =
                MacdDivergenceCalculator.findDivergences(
                        highs, highs, macd, signal, macd, 15, SIZE - 1);

        assertType(MacdDivergenceCalculator.Type.NONE, divergences[NEW]);
    }

    private static MacdDivergenceCalculator.Divergence[] find(
            float[] highs, float[] lows, float[] macd, float[] histogram) {
        return MacdDivergenceCalculator.findDivergences(
                highs, lows, macd, new float[SIZE], histogram, SIZE, SIZE - 1);
    }

    private static void assertType(MacdDivergenceCalculator.Type expected,
                                   MacdDivergenceCalculator.Divergence actual) {
        assertEquals(expected, actual.getType());
    }
}
