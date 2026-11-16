package com.finance.view.component.stick;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class CandleGeometryTest {
    @Test
    public void minimumSpacingRetainsVisibleCandleBody() {
        assertEquals(1, CandleGeometry.bodyWidth(1));
        assertEquals(1, CandleGeometry.bodyWidth(2));
        assertEquals(1, CandleGeometry.bodyWidth(3));
        assertEquals(2, CandleGeometry.bodyWidth(4));
        assertEquals(18, CandleGeometry.bodyWidth(20));
    }
}
