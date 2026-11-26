package com.finance.view.component.macd;

import java.util.Arrays;

public final class MacdDivergenceCalculator {
    public enum Type {
        NONE, TOP, TOP_DEATH_CROSS, BOTTOM, BOTTOM_GOLDEN_CROSS
    }

    public static final class Divergence {
        private final Type type;
        private final int comparisonIndex;
        private final int confirmationIndex;

        private Divergence(Type type, int comparisonIndex, int confirmationIndex) {
            this.type = type;
            this.comparisonIndex = comparisonIndex;
            this.confirmationIndex = confirmationIndex;
        }

        public Type getType() {
            return type;
        }

        public int getComparisonIndex() {
            return comparisonIndex;
        }

        public int getConfirmationIndex() {
            return confirmationIndex;
        }
    }

    private static final Divergence NONE = new Divergence(Type.NONE, -1, -1);

    private MacdDivergenceCalculator() {
    }

    // Arrays are newest first. A turn at i + 1 becomes known at i.
    public static Divergence[] findDivergences(float[] highs, float[] lows,
                                               float[] dif, float[] dea,
                                               int lastValidIndex, boolean inverted) {
        int size = highs.length;
        if (lows.length != size || dif.length != size || dea.length != size) {
            throw new IllegalArgumentException("Input arrays must have the same length");
        }
        if (lastValidIndex < -1 || lastValidIndex >= size) {
            throw new IllegalArgumentException("Invalid last valid index");
        }
        Divergence[] result = new Divergence[size];
        Arrays.fill(result, NONE);
        int previousHigh = -1;
        int previousLow = -1;
        for (int confirmation = lastValidIndex - 2; confirmation >= 0; confirmation--) {
            int pivot = confirmation + 1;
            if (dea[pivot] > dea[pivot + 1] && dea[pivot] > dea[confirmation]) {
                if (previousHigh >= 0 && highs[pivot] > highs[previousHigh]
                        && dif[pivot] < dif[previousHigh]) {
                    result[pivot] = new Divergence(inverted ? Type.BOTTOM : Type.TOP,
                            previousHigh, confirmation);
                }
                previousHigh = pivot;
            } else if (dea[pivot] < dea[pivot + 1] && dea[pivot] < dea[confirmation]) {
                if (previousLow >= 0 && lows[pivot] < lows[previousLow]
                        && dif[pivot] > dif[previousLow]) {
                    result[pivot] = new Divergence(inverted ? Type.TOP : Type.BOTTOM,
                            previousLow, confirmation);
                }
                previousLow = pivot;
            }
        }
        return result;
    }
}
