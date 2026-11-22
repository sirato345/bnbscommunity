package com.finance.view.component.stick;

public final class IndicatorControlLayout {
    private final float[] starts;
    private final float[] ends;
    private final float scale;

    public IndicatorControlLayout(float[] widths, float gap, float availableWidth) {
        starts = new float[widths.length];
        ends = new float[widths.length];
        float total = Math.max(0, widths.length - 1) * gap;
        for (float width : widths) {
            total += width;
        }
        scale = total > 0 ? Math.min(1f, Math.max(0f, availableWidth) / total) : 1f;
        float x = 0;
        for (int i = 0; i < widths.length; i++) {
            starts[i] = x;
            ends[i] = x + widths[i] * scale;
            x = ends[i] + gap * scale;
        }
    }

    public float getScale() {
        return scale;
    }

    public float getStart(int index) {
        return starts[index];
    }

    public int hitTest(float x) {
        for (int i = 0; i < starts.length; i++) {
            float left = i == 0 ? starts[i] : (ends[i - 1] + starts[i]) / 2;
            float right = i == starts.length - 1 ? ends[i] : (ends[i] + starts[i + 1]) / 2;
            if (x >= left && (x < right || (i == starts.length - 1 && x == right))) {
                return i;
            }
        }
        return -1;
    }
}
