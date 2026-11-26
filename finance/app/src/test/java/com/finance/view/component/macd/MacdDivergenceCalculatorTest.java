package com.finance.view.component.macd;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class MacdDivergenceCalculatorTest {
    private static final float[] DEA = {0, 1, 0, -1, 0, 2, 0, -2, 0};

    @Test
    public void findsTopAndBottomAtConfirmedDeaTurns() {
        float[] highs = {0, 30, 0, 0, 0, 20, 0, 0, 0};
        float[] lows = {0, 0, 0, -30, 0, 0, 0, -20, 0};
        float[] dif = {0, 3, 0, -3, 0, 5, 0, -5, 0};
        MacdDivergenceCalculator.Divergence[] result =
                MacdDivergenceCalculator.findDivergences(highs, lows, dif, DEA, 8, false);
        assertEquals(MacdDivergenceCalculator.Type.TOP, result[1].getType());
        assertEquals(5, result[1].getComparisonIndex());
        assertEquals(0, result[1].getConfirmationIndex());
        assertEquals(MacdDivergenceCalculator.Type.BOTTOM, result[3].getType());
        assertEquals(7, result[3].getComparisonIndex());
        assertEquals(2, result[3].getConfirmationIndex());
    }

    @Test
    public void unconfirmedAndFlatTurnsAreNotDetected() {
        float[] highs = {30, 30, 0, 0, 0, 20, 0, 0, 0};
        float[] dif = {3, 3, 0, 0, 0, 5, 0, 0, 0};
        float[] dea = {1, 1, 0, -1, 0, 2, 0, -2, 0};
        MacdDivergenceCalculator.Divergence[] result =
                MacdDivergenceCalculator.findDivergences(highs, highs, dif, dea, 8, false);
        assertEquals(MacdDivergenceCalculator.Type.NONE, result[0].getType());
        assertEquals(MacdDivergenceCalculator.Type.NONE, result[1].getType());
    }

    @Test
    public void comparesOnlyTheImmediatelyPreviousSameKindOfTurn() {
        float[] dea = {0, 1, 0, 2, 0, 3, 0};
        float[] highs = {0, 25, 0, 30, 0, 20, 0};
        float[] dif = {0, 2, 0, 4, 0, 6, 0};
        MacdDivergenceCalculator.Divergence[] result =
                MacdDivergenceCalculator.findDivergences(highs, highs, dif, dea, 6, false);
        assertEquals(MacdDivergenceCalculator.Type.TOP, result[3].getType());
        assertEquals(MacdDivergenceCalculator.Type.NONE, result[1].getType());
    }

    @Test
    public void inversionSwapsTypeWithoutMovingEndpoints() {
        float[] highs = {0, 30, 0, 0, 0, 20, 0, 0, 0};
        float[] dif = {0, 3, 0, 0, 0, 5, 0, 0, 0};
        MacdDivergenceCalculator.Divergence[] result =
                MacdDivergenceCalculator.findDivergences(highs, highs, dif, DEA, 8, true);
        assertEquals(MacdDivergenceCalculator.Type.BOTTOM, result[1].getType());
        assertEquals(5, result[1].getComparisonIndex());
        assertEquals(0, result[1].getConfirmationIndex());
    }

    @Test
    public void laterBarsDoNotChangeConfirmedHistoricalResult() {
        float[] highs = {0, 30, 0, 0, 0, 20, 0, 0, 0};
        float[] dif = {0, 3, 0, 0, 0, 5, 0, 0, 0};
        MacdDivergenceCalculator.Divergence[] full =
                MacdDivergenceCalculator.findDivergences(highs, highs, dif, DEA, 8, false);
        float[] extendedHighs = {100, 100, 0, 30, 0, 0, 0, 20, 0, 0, 0};
        float[] extendedDif = {100, -100, 0, 3, 0, 0, 0, 5, 0, 0, 0};
        float[] extendedDea = {10, -10, 0, 1, 0, -1, 0, 2, 0, -2, 0};
        MacdDivergenceCalculator.Divergence[] extended =
                MacdDivergenceCalculator.findDivergences(
                        extendedHighs, extendedHighs, extendedDif, extendedDea, 10, false);
        assertEquals(full[1].getType(), extended[3].getType());
        assertEquals(full[1].getComparisonIndex() + 2, extended[3].getComparisonIndex());
        assertEquals(full[1].getConfirmationIndex() + 2, extended[3].getConfirmationIndex());
    }
}
