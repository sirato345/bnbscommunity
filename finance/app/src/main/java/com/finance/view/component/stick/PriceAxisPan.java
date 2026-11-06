package com.finance.view.component.stick;

public final class PriceAxisPan {
    private PriceAxisPan() {
    }

    public static float afterDrag(float startOffset, float distanceY, int height) {
        return startOffset + distanceY / height;
    }

    public static float priceShift(float offset, float min, float max) {
        return offset * (max - min);
    }
}
