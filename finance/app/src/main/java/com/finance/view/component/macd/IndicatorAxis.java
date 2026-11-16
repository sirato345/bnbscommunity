package com.finance.view.component.macd;

public final class IndicatorAxis {
    private IndicatorAxis() {
    }

    public static float yForValue(float value, float min, float max,
                                  float top, float bottom, boolean inverted) {
        float fraction = (value - min) / (max - min);
        return inverted ? top + fraction * (bottom - top)
                : bottom - fraction * (bottom - top);
    }

    public static float valueForY(float y, float min, float max,
                                  float top, float bottom, boolean inverted) {
        float fraction = inverted ? (y - top) / (bottom - top)
                : (bottom - y) / (bottom - top);
        return min + fraction * (max - min);
    }

    public static int macdY(float value, float absMax, int top, int height, boolean inverted) {
        float orientedValue = inverted ? -value : value;
        float fraction = orientedValue >= 0f
                ? (absMax - orientedValue) / (absMax * 2f)
                : (absMax + Math.abs(orientedValue)) / (absMax * 2f);
        return top + (int) (fraction * height);
    }

    public static float macdValue(float y, float absMax, int top, int height, boolean inverted) {
        float value = absMax - (y - top) / height * (absMax * 2f);
        return inverted ? -value : value;
    }
}
