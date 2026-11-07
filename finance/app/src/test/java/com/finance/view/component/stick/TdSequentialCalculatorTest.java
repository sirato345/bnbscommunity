package com.finance.view.component.stick;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class TdSequentialCalculatorTest {
    @Test
    public void calculatesAllVisibleCandlesUsingOlderHistory() {
        float[] closes = new float[92];
        for (int i = 0; i < closes.length; i++) {
            closes[i] = closes.length - i;
        }
        int[] signals = TdSequentialCalculator.calculate(closes, 80);
        assertEquals(80, signals.length);
        assertEquals(9, signals[0]);
        assertEquals(9, signals[59]);
        assertEquals(9, signals[60]);
        assertEquals(9, signals[79]);
    }

    @Test
    public void handlesDownwardSequenceAndLimitedHistory() {
        float[] closes = new float[13];
        for (int i = 0; i < closes.length; i++) {
            closes[i] = i;
        }
        int[] signals = TdSequentialCalculator.calculate(closes, 20);
        assertEquals(13, signals.length);
        assertEquals(-9, signals[0]);
        assertEquals(-8, signals[1]);
        assertEquals(0, signals[2]);
    }

    @Test
    public void equalOrReversedComparisonBreaksSequence() {
        float[] equal = new float[13];
        assertEquals(0, TdSequentialCalculator.calculate(equal, 1)[0]);
        float[] reversed = {10, 9, 8, 7, 6, 5, 4, 3, 2, 1, 0, 0, 4};
        assertEquals(8, TdSequentialCalculator.calculate(reversed, 1)[0]);
        reversed[11] = 4;
        assertEquals(0, TdSequentialCalculator.calculate(reversed, 1)[0]);
    }
}
