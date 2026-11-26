package com.finance.view.component.macd;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class MacdDivergenceCalculatorTest {
    private static final int SIZE = 20;
    private static final int NEW_HIGH = 2;
    private static final int OLD_HIGH = 10;
    private static final int NEW_LOW = 4;
    private static final int OLD_LOW = 12;

    private static float[] dea() {
        float[] values = new float[SIZE];
        values[NEW_HIGH] = 1f;
        values[OLD_HIGH] = 2f;
        values[NEW_LOW] = -1f;
        values[OLD_LOW] = -2f;
        return values;
    }

    @Test
    public void findsTopAndBottomAtConfirmedDeaTurns() {
        float[] highs = new float[SIZE];
        float[] lows = new float[SIZE];
        float[] dif = new float[SIZE];
        highs[NEW_HIGH] = 30f;
        highs[OLD_HIGH] = 20f;
        lows[NEW_LOW] = -30f;
        lows[OLD_LOW] = -20f;
        dif[NEW_HIGH] = 3f;
        dif[OLD_HIGH] = 5f;
        dif[NEW_LOW] = -3f;
        dif[OLD_LOW] = -5f;
        MacdDivergenceCalculator.Divergence[] result =
                MacdDivergenceCalculator.findDivergences(
                        highs, lows, dif, dea(), SIZE - 1, false);
        assertEquals(MacdDivergenceCalculator.Type.TOP, result[NEW_HIGH].getType());
        assertEquals(OLD_HIGH, result[NEW_HIGH].getComparisonIndex());
        assertEquals(NEW_HIGH - 1, result[NEW_HIGH].getConfirmationIndex());
        assertEquals(MacdDivergenceCalculator.Type.BOTTOM, result[NEW_LOW].getType());
        assertEquals(OLD_LOW, result[NEW_LOW].getComparisonIndex());
        assertEquals(NEW_LOW - 1, result[NEW_LOW].getConfirmationIndex());
    }

    @Test
    public void unconfirmedAndFlatTurnsAreNotDetected() {
        float[] highs = new float[SIZE];
        float[] dif = new float[SIZE];
        float[] signal = dea();
        highs[NEW_HIGH] = 30f;
        highs[OLD_HIGH] = 20f;
        dif[NEW_HIGH] = 3f;
        dif[OLD_HIGH] = 5f;
        signal[NEW_HIGH + 1] = signal[NEW_HIGH];
        MacdDivergenceCalculator.Divergence[] result =
                MacdDivergenceCalculator.findDivergences(
                        highs, highs, dif, signal, SIZE - 1, false);
        assertEquals(MacdDivergenceCalculator.Type.NONE, result[0].getType());
        assertEquals(MacdDivergenceCalculator.Type.NONE, result[NEW_HIGH].getType());
    }

    @Test
    public void comparesOnlyTheImmediatelyPreviousSameKindOfTurn() {
        float[] signal = new float[30];
        float[] highs = new float[30];
        float[] dif = new float[30];
        signal[2] = 1f;
        signal[10] = 2f;
        signal[20] = 3f;
        highs[2] = 25f;
        highs[10] = 30f;
        highs[20] = 20f;
        dif[2] = 2f;
        dif[10] = 4f;
        dif[20] = 6f;
        MacdDivergenceCalculator.Divergence[] result =
                MacdDivergenceCalculator.findDivergences(highs, highs, dif, signal, 29, false);
        assertEquals(MacdDivergenceCalculator.Type.TOP, result[10].getType());
        assertEquals(MacdDivergenceCalculator.Type.NONE, result[2].getType());
    }

    @Test
    public void shortIntervalDoesNotCauseFallbackToEarlierTurn() {
        float[] signal = new float[30];
        float[] highs = new float[30];
        float[] dif = new float[30];
        signal[4] = 1f;
        signal[11] = 2f;
        signal[20] = 3f;
        highs[4] = 30f;
        highs[11] = 25f;
        highs[20] = 20f;
        dif[4] = 2f;
        dif[11] = 4f;
        dif[20] = 6f;
        MacdDivergenceCalculator.Divergence[] result =
                MacdDivergenceCalculator.findDivergences(highs, highs, dif, signal, 29, false);
        assertEquals(MacdDivergenceCalculator.Type.TOP, result[11].getType());
        assertEquals(MacdDivergenceCalculator.Type.NONE, result[4].getType());
    }

    @Test
    public void filtersSevenBarsAndAcceptsEightForBothDirections() {
        assertDistance(7, true, MacdDivergenceCalculator.Type.NONE);
        assertDistance(8, true, MacdDivergenceCalculator.Type.TOP);
        assertDistance(7, false, MacdDivergenceCalculator.Type.NONE);
        assertDistance(8, false, MacdDivergenceCalculator.Type.BOTTOM);
    }

    private static void assertDistance(int distance, boolean top,
                                       MacdDivergenceCalculator.Type expected) {
        int current = 2;
        int previous = current + distance;
        float direction = top ? 1f : -1f;
        float[] prices = new float[SIZE];
        float[] dif = new float[SIZE];
        float[] signal = new float[SIZE];
        prices[previous] = direction * 20f;
        prices[current] = direction * 30f;
        dif[previous] = direction * 5f;
        dif[current] = direction * 3f;
        signal[previous] = direction * 2f;
        signal[current] = direction;
        MacdDivergenceCalculator.Divergence[] result =
                MacdDivergenceCalculator.findDivergences(
                        prices, prices, dif, signal, SIZE - 1, false);
        assertEquals(expected, result[current].getType());
        if (expected != MacdDivergenceCalculator.Type.NONE) {
            assertEquals(previous, result[current].getComparisonIndex());
        }
    }

    @Test
    public void inversionSwapsTypeWithoutMovingEndpoints() {
        float[] highs = new float[SIZE];
        float[] dif = new float[SIZE];
        highs[NEW_HIGH] = 30f;
        highs[OLD_HIGH] = 20f;
        dif[NEW_HIGH] = 3f;
        dif[OLD_HIGH] = 5f;
        MacdDivergenceCalculator.Divergence[] result =
                MacdDivergenceCalculator.findDivergences(
                        highs, highs, dif, dea(), SIZE - 1, true);
        assertEquals(MacdDivergenceCalculator.Type.BOTTOM, result[NEW_HIGH].getType());
        assertEquals(OLD_HIGH, result[NEW_HIGH].getComparisonIndex());
        assertEquals(NEW_HIGH - 1, result[NEW_HIGH].getConfirmationIndex());
    }

    @Test
    public void topRequiresBothDifEndpointsStrictlyAboveZero() {
        assertTopType(-1f, -2f, MacdDivergenceCalculator.Type.NONE, false);
        assertTopType(1f, -1f, MacdDivergenceCalculator.Type.NONE, false);
        assertTopType(1f, 0f, MacdDivergenceCalculator.Type.NONE, false);
        assertTopType(0f, -1f, MacdDivergenceCalculator.Type.NONE, false);
        assertTopType(5f, 3f, MacdDivergenceCalculator.Type.TOP, false);
        assertTopType(-1f, -2f, MacdDivergenceCalculator.Type.NONE, true);
    }

    @Test
    public void bottomRequiresBothDifEndpointsStrictlyBelowZero() {
        assertBottomType(1f, 2f, MacdDivergenceCalculator.Type.NONE);
        assertBottomType(-1f, 1f, MacdDivergenceCalculator.Type.NONE);
        assertBottomType(-1f, 0f, MacdDivergenceCalculator.Type.NONE);
        assertBottomType(0f, 1f, MacdDivergenceCalculator.Type.NONE);
        assertBottomType(-5f, -3f, MacdDivergenceCalculator.Type.BOTTOM);
    }

    @Test
    public void editedEndpointsMustRemainOnCorrectSide() {
        assertTrue(MacdDivergenceCalculator.areEndpointsOnCorrectSide(2f, 1f, true));
        assertFalse(MacdDivergenceCalculator.areEndpointsOnCorrectSide(2f, 0f, true));
        assertFalse(MacdDivergenceCalculator.areEndpointsOnCorrectSide(2f, -1f, true));
        assertTrue(MacdDivergenceCalculator.areEndpointsOnCorrectSide(-2f, -1f, false));
        assertFalse(MacdDivergenceCalculator.areEndpointsOnCorrectSide(-2f, 0f, false));
        assertFalse(MacdDivergenceCalculator.areEndpointsOnCorrectSide(-2f, 1f, false));
    }

    @Test
    public void editedEndpointsMustKeepMinimumDistanceInEitherOrder() {
        assertFalse(MacdDivergenceCalculator.areEndpointsFarEnough(10, 3));
        assertTrue(MacdDivergenceCalculator.areEndpointsFarEnough(10, 2));
        assertFalse(MacdDivergenceCalculator.areEndpointsFarEnough(3, 10));
        assertTrue(MacdDivergenceCalculator.areEndpointsFarEnough(2, 10));
    }

    private static void assertTopType(float oldDif, float newDif,
                                      MacdDivergenceCalculator.Type expected, boolean inverted) {
        float[] highs = new float[SIZE];
        float[] dif = new float[SIZE];
        highs[NEW_HIGH] = 30f;
        highs[OLD_HIGH] = 20f;
        dif[NEW_HIGH] = newDif;
        dif[OLD_HIGH] = oldDif;
        MacdDivergenceCalculator.Divergence[] result =
                MacdDivergenceCalculator.findDivergences(
                        highs, highs, dif, dea(), SIZE - 1, inverted);
        assertEquals(expected, result[NEW_HIGH].getType());
    }

    private static void assertBottomType(float oldDif, float newDif,
                                         MacdDivergenceCalculator.Type expected) {
        float[] lows = new float[SIZE];
        float[] dif = new float[SIZE];
        lows[NEW_LOW] = -30f;
        lows[OLD_LOW] = -20f;
        dif[NEW_LOW] = newDif;
        dif[OLD_LOW] = oldDif;
        MacdDivergenceCalculator.Divergence[] result =
                MacdDivergenceCalculator.findDivergences(
                        lows, lows, dif, dea(), SIZE - 1, false);
        assertEquals(expected, result[NEW_LOW].getType());
    }

    @Test
    public void laterBarsDoNotChangeConfirmedHistoricalResult() {
        float[] highs = new float[SIZE];
        float[] dif = new float[SIZE];
        highs[NEW_HIGH] = 30f;
        highs[OLD_HIGH] = 20f;
        dif[NEW_HIGH] = 3f;
        dif[OLD_HIGH] = 5f;
        float[] signal = dea();
        MacdDivergenceCalculator.Divergence[] full =
                MacdDivergenceCalculator.findDivergences(
                        highs, highs, dif, signal, SIZE - 1, false);
        float[] extendedHighs = new float[SIZE + 2];
        float[] extendedDif = new float[SIZE + 2];
        float[] extendedDea = new float[SIZE + 2];
        System.arraycopy(highs, 0, extendedHighs, 2, SIZE);
        System.arraycopy(dif, 0, extendedDif, 2, SIZE);
        System.arraycopy(signal, 0, extendedDea, 2, SIZE);
        extendedDea[0] = 10f;
        extendedDea[1] = -10f;
        MacdDivergenceCalculator.Divergence[] extended =
                MacdDivergenceCalculator.findDivergences(
                        extendedHighs, extendedHighs, extendedDif, extendedDea, SIZE + 1, false);
        assertEquals(full[NEW_HIGH].getType(), extended[NEW_HIGH + 2].getType());
        assertEquals(full[NEW_HIGH].getComparisonIndex() + 2,
                extended[NEW_HIGH + 2].getComparisonIndex());
        assertEquals(full[NEW_HIGH].getConfirmationIndex() + 2,
                extended[NEW_HIGH + 2].getConfirmationIndex());
    }
}
