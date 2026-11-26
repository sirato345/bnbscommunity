package com.finance.view.component.macd;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;

public class DivergenceOverlapFilterTest {

    @Test
    public void keepsStrongerEarlierPivotAndRejectsSharedEndpoints() {
        Line nov15To21 = new Line(80, 43, -2408f, -1905f);
        Line nov19To25 = new Line(58, 18, -1118f, -684f);
        Line touching = new Line(43, 15, 200f, 100f);
        Line separate = new Line(42, 30, 500f, 300f);

        List<Line> selected = DivergenceOverlapFilter.select(
                Arrays.asList(nov19To25, touching, separate, nov15To21));

        assertEquals(Arrays.asList(nov15To21, separate), selected);
    }

    @Test
    public void comparesLaterPivotAndThenLaterEndWhenEarlierStrengthTies() {
        Line weakerEnd = new Line(50, 30, -200f, -60f);
        Line earlierEnd = new Line(50, 25, -200f, -100f);
        Line laterEnd = new Line(50, 20, -200f, -100f);

        assertEquals(Arrays.asList(laterEnd),
                DivergenceOverlapFilter.select(Arrays.asList(weakerEnd, earlierEnd, laterEnd)));
    }

    @Test
    public void usesChronologicalValuesForReversedEditedEndpoints() {
        Line reversed = new Line(15, 40, -10f, -90f);
        Line weaker = new Line(30, 10, -80f, -5f);

        assertEquals(Arrays.asList(reversed),
                DivergenceOverlapFilter.select(Arrays.asList(weaker, reversed)));
    }

    private static final class Line implements DivergenceOverlapFilter.Interval {
        private final int start;
        private final int end;
        private final float startValue;
        private final float endValue;

        private Line(int start, int end, float startValue, float endValue) {
            this.start = start;
            this.end = end;
            this.startValue = startValue;
            this.endValue = endValue;
        }

        @Override
        public int getStartIndex() {
            return start;
        }

        @Override
        public int getEndIndex() {
            return end;
        }

        @Override
        public float getStartValue() {
            return startValue;
        }

        @Override
        public float getEndValue() {
            return endValue;
        }
    }
}
