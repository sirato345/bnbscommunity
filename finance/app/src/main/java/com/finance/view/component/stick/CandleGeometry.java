package com.finance.view.component.stick;

public final class CandleGeometry {
    private CandleGeometry() {
    }

    public static int bodyWidth(int candleSpacing) {
        return Math.max(1, candleSpacing - StickInfo.BETWEEN_SPACE);
    }
}
