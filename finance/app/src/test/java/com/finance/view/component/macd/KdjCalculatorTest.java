package com.finance.view.component.macd;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class KdjCalculatorTest {

    @Test
    public void calculatesRsvAndSmoothedKdjValues() {
        float rsv = KdjCalculator.calculateRsv(15f, 5f, 10f);
        float k = KdjCalculator.calculateNextK(rsv, 50f);
        float d = KdjCalculator.calculateNextD(k, 50f);
        float j = KdjCalculator.calculateJ(k, d);

        assertEquals(50f, rsv, 0f);
        assertEquals(50f, k, 0f);
        assertEquals(50f, d, 0f);
        assertEquals(50f, j, 0f);
    }

    @Test
    public void usesNeutralRsvWhenPriceRangeIsFlat() {
        assertEquals(50f, KdjCalculator.calculateRsv(10f, 10f, 10f), 0f);
    }

    @Test
    public void calculatesJOutsideTheKAndDRange() {
        float k = KdjCalculator.calculateNextK(80f, 50f);
        float d = KdjCalculator.calculateNextD(k, 50f);

        assertEquals(60f, k, 0f);
        assertEquals(53.333332f, d, 0.00001f);
        assertEquals(73.333336f, KdjCalculator.calculateJ(k, d), 0.00001f);
    }
}
