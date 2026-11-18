package com.finance.view.component;

import android.graphics.Canvas;
import android.graphics.Paint;

final class FearGreedLine {
    private FearGreedLine() {
    }

    static int color(float value) {
        if (value >= 80) return 0xFF16C784;
        if (value >= 60) return 0xFF7CFF00;
        if (value >= 40) return 0xFFFFE600;
        if (value >= 20) return 0xFFFF9500;
        return 0xFFEA3943;
    }

    static void draw(Canvas canvas, Paint paint, float x1, float y1, float v1,
                     float x2, float y2, float v2) {
        float previous = 0f;
        int step = v2 >= v1 ? 20 : -20;
        for (int level = step > 0 ? 20 : 80; level >= 20 && level <= 80; level += step) {
            if ((v1 - level) * (v2 - level) < 0) {
                float next = (level - v1) / (v2 - v1);
                drawSegment(canvas, paint, x1, y1, v1, x2, y2, v2, previous, next);
                previous = next;
            }
        }
        drawSegment(canvas, paint, x1, y1, v1, x2, y2, v2, previous, 1f);
    }

    private static void drawSegment(Canvas canvas, Paint paint, float x1, float y1, float v1,
                                    float x2, float y2, float v2, float t1, float t2) {
        paint.setColor(color(v1 + (v2 - v1) * (t1 + t2) / 2));
        canvas.drawLine(x1 + (x2 - x1) * t1, y1 + (y2 - y1) * t1,
                x1 + (x2 - x1) * t2, y1 + (y2 - y1) * t2, paint);
    }
}
