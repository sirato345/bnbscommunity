package com.finance.view.component;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class GuideLabelPlacementTest {
    @Test
    public void labelsKeepSameGapFromEveryGuideLine() {
        float top = 10;
        float bottom = 310;
        float textTop = -14;
        float textBottom = 2;
        float gap = 4;
        for (int level = 0; level <= 100; level += 20) {
            float y = bottom - (bottom - top) * level / 100f;
            float baseline = MacdView.guideLabelBaseline(
                    y, top, bottom, textTop, textBottom, gap);
            if (level == 100) {
                assertEquals(gap, baseline + textTop - y, 0f);
            } else {
                assertEquals(gap, y - (baseline + textBottom), 0f);
            }
        }
    }

    @Test
    public void invertedAxisUsesSameGapAtBothEdges() {
        float gap = 4;
        float atTop = MacdView.guideLabelBaseline(10, 10, 310, -14, 2, gap);
        float atBottom = MacdView.guideLabelBaseline(310, 10, 310, -14, 2, gap);
        assertEquals(gap, atTop - 14 - 10, 0f);
        assertEquals(gap, 310 - (atBottom + 2), 0f);
    }

    @Test
    public void kdjPlotKeepsItsLinesAboveTheValueLabels() {
        assertEquals(244f, MacdView.kdjPlotBottom(300f, 290f, -38f, 8f), 0f);
        assertEquals(200f, MacdView.kdjPlotBottom(200f, 290f, -38f, 8f), 0f);
        assertEquals(154f, MacdView.kdjPlotBottom(300f, 200f, -38f, 8f), 0f);
    }
}
