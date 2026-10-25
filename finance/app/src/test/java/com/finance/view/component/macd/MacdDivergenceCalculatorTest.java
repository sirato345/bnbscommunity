package com.finance.view.component.macd;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class MacdDivergenceCalculatorTest {

    @Test
    public void detectsTopDivergenceUsingPeakCandleCloses() {
        float[] closes = new float[18];
        float[] macd = new float[18];
        float[] signal = new float[18];
        fill(signal, 100f);
        setMacdPivots(macd, new int[]{11, 5}, new float[]{8f, 4f},
                new int[]{15, 8, 2}, new float[]{-5f, -8f, -6f});
        closes[11] = 20f;
        closes[5] = 25f;

        MacdDivergenceCalculator.Divergence[] divergences =
                MacdDivergenceCalculator.findDivergences(closes, macd, signal, macd, 18, 17);

        assertType(MacdDivergenceCalculator.Type.TOP, divergences[5]);
        assertEquals(11, divergences[5].getComparisonIndex());
    }

    @Test
    public void ignoresTopDivergenceWhenPeaksAreFiveBarsApart() {
        float[] closes = new float[16];
        float[] macd = new float[16];
        float[] signal = new float[16];
        fill(signal, 100f);
        setMacdPivots(macd, new int[]{10, 5}, new float[]{8f, 4f},
                new int[]{13, 7, 2}, new float[]{-5f, -8f, -6f});
        closes[10] = 20f;
        closes[5] = 25f;

        MacdDivergenceCalculator.Divergence[] divergences =
                MacdDivergenceCalculator.findDivergences(closes, macd, signal, macd, 16, 15);

        assertType(MacdDivergenceCalculator.Type.NONE, divergences[5]);
    }

    @Test
    public void requiresThreeHistogramBarsOnEachSideOfPeaks() {
        float[] closes = new float[18];
        float[] macd = new float[18];
        float[] signal = new float[18];
        float[] histogram = new float[18];
        fill(signal, 100f);
        setMacdPivots(macd, new int[]{11, 5}, new float[]{8f, 4f},
                new int[]{15, 8, 2}, new float[]{-5f, -8f, -6f});
        histogram[11] = 8f;
        histogram[5] = 4f;
        histogram[8] = 9f;
        closes[11] = 20f;
        closes[5] = 25f;

        MacdDivergenceCalculator.Divergence[] divergences =
                MacdDivergenceCalculator.findDivergences(
                        closes, macd, signal, histogram, 18, 17);

        assertType(MacdDivergenceCalculator.Type.NONE, divergences[5]);
    }

    @Test
    public void comparesHighsForTopAndLowsForBottom() {
        float[] highs = new float[18];
        float[] lows = new float[18];
        float[] macd = new float[18];
        float[] signal = new float[18];
        fill(signal, 100f);
        setMacdPivots(macd, new int[]{11, 5}, new float[]{8f, 4f},
                new int[]{15, 8, 2}, new float[]{-5f, -8f, -6f});
        highs[11] = 20f;
        highs[5] = 25f;
        lows[11] = 30f;
        lows[5] = 10f;

        MacdDivergenceCalculator.Divergence[] divergences =
                MacdDivergenceCalculator.findDivergences(
                        highs, lows, macd, signal, macd, 18, 17);

        assertType(MacdDivergenceCalculator.Type.TOP, divergences[5]);
    }

    @Test
    public void ignoresTopDivergenceWhenHighsDoNotRise() {
        float[] highs = new float[18];
        float[] lows = new float[18];
        float[] macd = new float[18];
        float[] signal = new float[18];
        fill(signal, 100f);
        setMacdPivots(macd, new int[]{11, 5}, new float[]{8f, 4f},
                new int[]{15, 8, 2}, new float[]{-5f, -8f, -6f});
        highs[11] = 25f;
        highs[5] = 20f;
        lows[11] = 10f;
        lows[5] = 30f;

        MacdDivergenceCalculator.Divergence[] divergences =
                MacdDivergenceCalculator.findDivergences(
                        highs, lows, macd, signal, macd, 18, 17);

        assertType(MacdDivergenceCalculator.Type.NONE, divergences[5]);
    }

    @Test
    public void ignoresTopDivergenceWhenFirstHistogramPeakIsShorter() {
        float[] closes = new float[18];
        float[] macd = new float[18];
        float[] signal = new float[18];
        float[] histogram = new float[18];
        fill(signal, 100f);
        setMacdPivots(macd, new int[]{11, 5}, new float[]{8f, 4f},
                new int[]{15, 8, 2}, new float[]{-5f, -8f, -6f});
        histogram[11] = 4f;
        histogram[5] = 8f;
        closes[11] = 25f;
        closes[5] = 20f;

        MacdDivergenceCalculator.Divergence[] divergences =
                MacdDivergenceCalculator.findDivergences(
                        closes, macd, signal, histogram, 18, 17);

        assertType(MacdDivergenceCalculator.Type.NONE, divergences[5]);
    }

    @Test
    public void classifiesTopByHistogramWhenMacdIsBelowZero() {
        float[] closes = new float[18];
        float[] macd = new float[18];
        float[] signal = new float[18];
        float[] histogram = new float[18];
        fill(signal, 100f);
        setMacdPivots(histogram, new int[]{11, 5}, new float[]{8f, 4f},
                new int[]{15, 8, 2}, new float[]{-5f, -8f, -6f});
        macd[5] = -1f;
        closes[11] = 20f;
        closes[5] = 25f;

        MacdDivergenceCalculator.Divergence[] divergences =
                MacdDivergenceCalculator.findDivergences(
                        closes, macd, signal, histogram, 18, 17);

        assertType(MacdDivergenceCalculator.Type.TOP, divergences[5]);
    }

    @Test
    public void classifiesBottomByHistogramWhenMacdIsAboveZero() {
        float[] closes = new float[18];
        float[] macd = new float[18];
        float[] signal = new float[18];
        float[] histogram = new float[18];
        fill(signal, 100f);
        setMacdPivots(histogram, new int[]{15, 9, 2}, new float[]{5f, 8f, 6f},
                new int[]{12, 5}, new float[]{-8f, -4f});
        macd[5] = 1f;
        closes[12] = -20f;
        closes[5] = -25f;

        MacdDivergenceCalculator.Divergence[] divergences =
                MacdDivergenceCalculator.findDivergences(
                        closes, macd, signal, histogram, 18, 17);

        assertType(MacdDivergenceCalculator.Type.BOTTOM, divergences[5]);
    }

    @Test
    public void ignoresBottomDivergenceWhenFirstHistogramTroughIsShorter() {
        float[] closes = new float[18];
        float[] macd = new float[18];
        float[] signal = new float[18];
        float[] histogram = new float[18];
        fill(signal, 100f);
        setMacdPivots(macd, new int[]{15, 9, 2}, new float[]{5f, 8f, 6f},
                new int[]{12, 5}, new float[]{-8f, -4f});
        histogram[12] = -4f;
        histogram[5] = -8f;
        closes[12] = -25f;
        closes[5] = -20f;

        MacdDivergenceCalculator.Divergence[] divergences =
                MacdDivergenceCalculator.findDivergences(
                        closes, macd, signal, histogram, 18, 17);

        assertType(MacdDivergenceCalculator.Type.NONE, divergences[5]);
    }

    @Test
    public void ignoresTopDivergenceWhenSecondPeakIsBelowZero() {
        float[] closes = new float[18];
        float[] macd = new float[18];
        float[] signal = new float[18];
        fill(signal, -100f);
        setMacdPivots(macd, new int[]{11, 5}, new float[]{-2f, -4f},
                new int[]{15, 8, 2}, new float[]{-8f, -9f, -7f});
        closes[11] = 20f;
        closes[5] = 25f;

        MacdDivergenceCalculator.Divergence[] divergences =
                MacdDivergenceCalculator.findDivergences(closes, macd, signal, macd, 18, 17);

        assertType(MacdDivergenceCalculator.Type.NONE, divergences[5]);
    }

    @Test
    public void detectsBottomDivergenceWhenTroughsAreMoreThanFiveBarsApart() {
        float[] closes = new float[16];
        float[] macd = new float[16];
        float[] signal = new float[16];
        fill(signal, 100f);
        setMacdPivots(macd, new int[]{15, 9, 2}, new float[]{5f, 8f, 6f},
                new int[]{12, 5}, new float[]{-8f, -4f});
        closes[12] = -20f;
        closes[5] = -25f;

        MacdDivergenceCalculator.Divergence[] divergences =
                MacdDivergenceCalculator.findDivergences(closes, macd, signal, macd, 16, 15);

        assertType(MacdDivergenceCalculator.Type.BOTTOM, divergences[5]);
        assertEquals(12, divergences[5].getComparisonIndex());
    }

    @Test
    public void ignoresBottomDivergenceWhenSecondTroughIsAboveZero() {
        float[] closes = new float[18];
        float[] macd = new float[18];
        float[] signal = new float[18];
        fill(signal, 100f);
        setMacdPivots(macd, new int[]{15, 9, 2}, new float[]{8f, 9f, 7f},
                new int[]{12, 5}, new float[]{2f, 4f});
        closes[12] = 20f;
        closes[5] = 25f;

        MacdDivergenceCalculator.Divergence[] divergences =
                MacdDivergenceCalculator.findDivergences(closes, macd, signal, macd, 18, 17);

        assertType(MacdDivergenceCalculator.Type.NONE, divergences[5]);
    }

    @Test
    public void detectsBottomDivergenceWhenMacdAndPriceBothMakeHigherLows() {
        float[] closes = new float[18];
        float[] macd = new float[18];
        float[] signal = new float[18];
        fill(signal, 100f);
        setMacdPivots(macd, new int[]{15, 9, 2}, new float[]{5f, 8f, 6f},
                new int[]{12, 5}, new float[]{-4f, -8f});
        closes[12] = -25f;
        closes[5] = -20f;

        MacdDivergenceCalculator.Divergence[] divergences =
                MacdDivergenceCalculator.findDivergences(closes, macd, signal, macd, 18, 17);

        assertType(MacdDivergenceCalculator.Type.NONE, divergences[5]);
    }

    @Test
    public void marksGoldenCrossBottomDivergenceAtMacdTrough() {
        float[] closes = new float[16];
        float[] macd = new float[16];
        float[] signal = new float[16];
        setMacdPivots(macd, new int[]{15, 9, 2}, new float[]{5f, 8f, 6f},
                new int[]{12, 5}, new float[]{-8f, -4f});
        closes[12] = -20f;
        closes[5] = -25f;
        macd[4] = -2f;
        signal[5] = -3f;
        signal[4] = -3f;

        MacdDivergenceCalculator.Divergence[] divergences =
                MacdDivergenceCalculator.findDivergences(closes, macd, signal, macd, 16, 15);

        assertType(MacdDivergenceCalculator.Type.BOTTOM_GOLDEN_CROSS, divergences[5]);
        assertEquals(12, divergences[5].getComparisonIndex());
    }

    @Test
    public void doesNotMarkPriceHighWithoutLowerMacdPeak() {
        float[] closes = new float[18];
        float[] macd = new float[18];
        float[] signal = new float[18];
        fill(signal, 100f);
        closes[11] = 20f;
        closes[5] = 25f;
        setMacdPivots(macd, new int[]{11, 5}, new float[]{4f, 8f},
                new int[]{15, 8, 2}, new float[]{-5f, -8f, -6f});

        MacdDivergenceCalculator.Divergence[] divergences =
                MacdDivergenceCalculator.findDivergences(closes, macd, signal, macd, 18, 17);

        assertType(MacdDivergenceCalculator.Type.NONE, divergences[5]);
    }

    @Test
    public void detectsDivergenceWithoutVisibleRangeProminenceThreshold() {
        float[] closes = new float[20];
        float[] macd = new float[20];
        float[] signal = new float[20];
        fill(signal, 100f);
        setMacdPivots(macd, new int[]{17, 11, 4}, new float[]{100f, 2f, 1f},
                new int[]{14, 8, 1}, new float[]{-1f, -0.5f, -1f});
        closes[11] = 20f;
        closes[4] = 25f;

        MacdDivergenceCalculator.Divergence[] divergences =
                MacdDivergenceCalculator.findDivergences(closes, macd, signal, macd, 20, 19);

        assertType(MacdDivergenceCalculator.Type.TOP, divergences[4]);
    }

    @Test
    public void ignoresTopWhenMacdAndCloseBothRise() {
        float[] closes = new float[18];
        float[] macd = new float[18];
        float[] signal = new float[18];
        fill(signal, 100f);
        setMacdPivots(macd, new int[]{11, 5}, new float[]{4f, 8f},
                new int[]{15, 8, 2}, new float[]{-5f, -8f, -6f});
        closes[11] = 15f;
        closes[5] = 20f;

        MacdDivergenceCalculator.Divergence[] divergences =
                MacdDivergenceCalculator.findDivergences(closes, macd, signal, macd, 18, 17);

        assertType(MacdDivergenceCalculator.Type.NONE, divergences[5]);
    }

    @Test
    public void doesNotRequireCloseToBeLocalTrough() {
        float[] closes = new float[18];
        float[] macd = new float[18];
        float[] signal = new float[18];
        fill(signal, 100f);
        setMacdPivots(macd, new int[]{15, 9, 2}, new float[]{5f, 8f, 6f},
                new int[]{12, 5}, new float[]{-8f, -4f});
        closes[12] = -20f;
        closes[5] = -25f;
        closes[3] = -26f;
        closes[1] = -27f;

        MacdDivergenceCalculator.Divergence[] divergences =
                MacdDivergenceCalculator.findDivergences(closes, macd, signal, macd, 18, 17);

        assertType(MacdDivergenceCalculator.Type.BOTTOM, divergences[5]);
    }

    @Test
    public void usesVisiblePeaksAndMarksDeathCrossAfterSecondPeak() {
        float[] closes = new float[20];
        float[] macd = new float[20];
        float[] signal = new float[20];
        closes[15] = 20f;
        closes[8] = 25f;
        closes[3] = 30f;
        setMacdPivots(macd, new int[]{15, 8, 3}, new float[]{8f, 4f, 9f},
                new int[]{12, 5, 1}, new float[]{-5f, -4f, -3f});
        macd[7] = 3f;
        signal[7] = 2f;
        macd[6] = 1f;
        signal[6] = 2f;

        MacdDivergenceCalculator.Divergence[] divergences =
                MacdDivergenceCalculator.findDivergences(closes, macd, signal, macd, 20, 19);

        assertType(MacdDivergenceCalculator.Type.TOP_DEATH_CROSS, divergences[8]);
        assertEquals(15, divergences[8].getComparisonIndex());
        assertType(MacdDivergenceCalculator.Type.NONE, divergences[3]);
    }

    @Test
    public void doesNotCompareAgainstPeaksOutsideVisibleRange() {
        float[] closes = new float[20];
        float[] macd = new float[20];
        float[] signal = new float[20];
        closes[15] = 20f;
        closes[8] = 25f;
        macd[15] = 8f;
        macd[8] = 4f;

        MacdDivergenceCalculator.Divergence[] divergences =
                MacdDivergenceCalculator.findDivergences(closes, macd, signal, macd, 12, 19);

        assertType(MacdDivergenceCalculator.Type.NONE, divergences[8]);
    }

    private static void assertType(MacdDivergenceCalculator.Type expected,
                                   MacdDivergenceCalculator.Divergence actual) {
        assertEquals(expected, actual.getType());
    }

    private static void setMacdPivots(float[] macd, int[] peakIndices, float[] peakValues,
                                      int[] troughIndices, float[] troughValues) {
        for (int i = 0; i < peakIndices.length; i++) {
            macd[peakIndices[i]] = peakValues[i];
        }
        for (int i = 0; i < troughIndices.length; i++) {
            macd[troughIndices[i]] = troughValues[i];
        }
    }

    private static void fill(float[] values, float value) {
        for (int i = 0; i < values.length; i++) {
            values[i] = value;
        }
    }
}
