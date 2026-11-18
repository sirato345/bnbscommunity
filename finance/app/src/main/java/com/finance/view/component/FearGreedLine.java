package com.finance.view.component;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

final class FearGreedLine {
    private FearGreedLine() {
    }

    static int color(float value) {
        if (value >= 80) return Color.parseColor("#16C784");
        if (value >= 60) return Color.parseColor("#7CFF00");
        if (value >= 40) return Color.parseColor("#FFE600");
        if (value >= 20) return Color.parseColor("#FF9500");
        return Color.parseColor("#EA3943");
    }

    static void draw(Canvas canvas, Paint paint, float x1, float y1, float v1,
                     float x2, float y2, float v2) {
        List<Float> cuts = new ArrayList<>();
        cuts.add(0f);
        for (int level = 20; level <= 80; level += 20) {
            if ((v1 - level) * (v2 - level) < 0) {
                cuts.add((level - v1) / (v2 - v1));
            }
        }
        cuts.add(1f);
        Collections.sort(cuts);
        for (int i = 0; i + 1 < cuts.size(); i++) {
            float t1 = cuts.get(i);
            float t2 = cuts.get(i + 1);
            paint.setColor(color(v1 + (v2 - v1) * (t1 + t2) / 2));
            canvas.drawLine(x1 + (x2 - x1) * t1, y1 + (y2 - y1) * t1,
                    x1 + (x2 - x1) * t2, y1 + (y2 - y1) * t2, paint);
        }
    }
}
