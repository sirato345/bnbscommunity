package com.finance.view.component.macd;

public final class MacdDivergenceCalculator {

    public enum Type {
        NONE,
        TOP,
        TOP_DEATH_CROSS,
        BOTTOM,
        BOTTOM_GOLDEN_CROSS
    }

    public static final class Divergence {
        private final Type type;
        private final int comparisonIndex;

        private Divergence(Type type, int comparisonIndex) {
            this.type = type;
            this.comparisonIndex = comparisonIndex;
        }

        public Type getType() {
            return type;
        }

        public int getComparisonIndex() {
            return comparisonIndex;
        }
    }

    private static final int PIVOT_RADIUS = 3;
    private static final int MIN_PIVOT_DISTANCE = 10;

    private MacdDivergenceCalculator() {
    }

    static Divergence[] findDivergences(float[] prices,
                                        float[] macd, float[] signal,
                                        float[] histogram,
                                        int visibleCount, int lastValidIndex) {
        return findDivergences(prices, prices, macd, signal, histogram,
                visibleCount, lastValidIndex);
    }

    /* 顶背离比较最高价，底背离比较最低价 */
    public static Divergence[] findDivergences(float[] highs, float[] lows,
                                               float[] macd, float[] signal,
                                               float[] histogram,
                                               int visibleCount, int lastValidIndex) {
        if (highs.length != lows.length || highs.length != macd.length
                || highs.length != signal.length || highs.length != histogram.length) {
            throw new IllegalArgumentException("Input arrays must have the same length");
        }
        Divergence[] divergences = new Divergence[highs.length];
        for (int i = 0; i < divergences.length; i++) {
            divergences[i] = new Divergence(Type.NONE, -1);
        }
        if (visibleCount < 0 || visibleCount > highs.length
                || lastValidIndex < -1 || lastValidIndex >= highs.length) {
            throw new IllegalArgumentException("Visible range is outside the input arrays");
        }

        int oldestVisibleIndex = Math.min(visibleCount - 1, lastValidIndex);
        int previousHighIndex = -1;
        int previousLowIndex = -1;
        for (int i = oldestVisibleIndex - PIVOT_RADIUS; i >= PIVOT_RADIUS; i--) {
            if (histogram[i] > 0f && isPivotHigh(histogram, i)) {
                if (previousHighIndex >= 0
                        && previousHighIndex - i >= MIN_PIVOT_DISTANCE) {
                    if (areInverselyRelated(
                            histogram[previousHighIndex], histogram[i],
                            highs[previousHighIndex], highs[i])
                            && isFirstHistogramLonger(
                                    histogram[previousHighIndex], histogram[i])) {
                        Type type = hasDeathCrossAfter(i, macd, signal)
                                ? Type.TOP_DEATH_CROSS : Type.TOP;
                        divergences[i] = new Divergence(type, previousHighIndex);
                    }
                }
                previousHighIndex = i;
            }
            if (histogram[i] < 0f && isPivotLow(histogram, i)) {
                if (previousLowIndex >= 0
                        && previousLowIndex - i >= MIN_PIVOT_DISTANCE) {
                    if (areInverselyRelated(
                            histogram[previousLowIndex], histogram[i],
                            lows[previousLowIndex], lows[i])
                            && isFirstHistogramLonger(
                                    histogram[previousLowIndex], histogram[i])) {
                        Type type = hasGoldenCrossAfter(i, macd, signal)
                                ? Type.BOTTOM_GOLDEN_CROSS : Type.BOTTOM;
                        divergences[i] = new Divergence(type, previousLowIndex);
                    }
                }
                previousLowIndex = i;
            }
        }
        return divergences;
    }

    private static boolean isFirstHistogramLonger(float first, float second) {
        return Math.abs(first) > Math.abs(second);
    }

    private static boolean areInverselyRelated(float firstMacd, float secondMacd,
                                               float firstPrice, float secondPrice) {
        return (firstMacd < secondMacd && firstPrice > secondPrice)
                || (firstMacd > secondMacd && firstPrice < secondPrice);
    }

    private static boolean hasDeathCrossAfter(int peakIndex, float[] macd, float[] signal) {
        for (int i = peakIndex - 1; i >= 0; i--) {
            if (macd[i] < signal[i] && macd[i + 1] >= signal[i + 1]) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasGoldenCrossAfter(int troughIndex, float[] macd, float[] signal) {
        for (int i = troughIndex - 1; i >= 0; i--) {
            if (macd[i] > signal[i] && macd[i + 1] <= signal[i + 1]) {
                return true;
            }
        }
        return false;
    }

    private static boolean isPivotHigh(float[] values, int index) {
        if (index < PIVOT_RADIUS || index + PIVOT_RADIUS >= values.length) {
            return false;
        }
        for (int offset = 1; offset <= PIVOT_RADIUS; offset++) {
            if (values[index] <= values[index - offset]
                    || values[index] <= values[index + offset]) {
                return false;
            }
        }
        return true;
    }

    private static boolean isPivotLow(float[] values, int index) {
        if (index < PIVOT_RADIUS || index + PIVOT_RADIUS >= values.length) {
            return false;
        }
        for (int offset = 1; offset <= PIVOT_RADIUS; offset++) {
            if (values[index] >= values[index - offset]
                    || values[index] >= values[index + offset]) {
                return false;
            }
        }
        return true;
    }
}
