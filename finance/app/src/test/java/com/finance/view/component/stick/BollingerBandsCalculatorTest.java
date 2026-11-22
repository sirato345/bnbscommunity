package com.finance.view.component.stick;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

public class BollingerBandsCalculatorTest {
    @Test
    public void calculatesPopulationDeviationAndAlignsNewestFirstWindows() {
        float[] closes = new float[21];
        for (int i = 0; i < closes.length; i++) {
            closes[i] = 21 - i;
        }
        BollingerBandsCalculator.Bands[] bands = BollingerBandsCalculator.calculate(closes);
        assertEquals(11.5f, bands[0].middle, 0.0001f);
        assertEquals(11.5 + 2 * Math.sqrt(33.25), bands[0].upper, 0.0001);
        assertEquals(11.5 - 2 * Math.sqrt(33.25), bands[0].lower, 0.0001);
        assertEquals(10.5f, bands[1].middle, 0.0001f);
        assertNull(bands[2]);
    }

    @Test
    public void requiresExactlyTwentyFiniteValues() {
        assertEquals(0, BollingerBandsCalculator.calculate(new float[0]).length);
        assertNull(BollingerBandsCalculator.calculate(new float[19])[0]);
        float[] closes = new float[20];
        java.util.Arrays.fill(closes, 7f);
        BollingerBandsCalculator.Bands bands = BollingerBandsCalculator.calculate(closes)[0];
        assertNotNull(bands);
        assertEquals(7f, bands.middle, 0f);
        assertEquals(7f, bands.upper, 0f);
        assertEquals(7f, bands.lower, 0f);
        closes[10] = Float.NaN;
        assertNull(BollingerBandsCalculator.calculate(closes)[0]);
        closes[10] = Float.POSITIVE_INFINITY;
        assertNull(BollingerBandsCalculator.calculate(closes)[0]);
    }
}
