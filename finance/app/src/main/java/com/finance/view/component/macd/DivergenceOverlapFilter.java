package com.finance.view.component.macd;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class DivergenceOverlapFilter {
    public interface Interval {
        int getStartIndex();
        int getEndIndex();
        float getStartValue();
        float getEndValue();
    }

    private DivergenceOverlapFilter() {
    }

    public static <T extends Interval> List<T> select(List<T> intervals) {
        List<T> candidates = new ArrayList<>(intervals);
        Collections.sort(candidates, (a, b) -> {
            int order = Float.compare(Math.abs(earlierValue(b)), Math.abs(earlierValue(a)));
            if (order != 0) {
                return order;
            }
            order = Float.compare(Math.abs(laterValue(b)), Math.abs(laterValue(a)));
            if (order != 0) {
                return order;
            }
            return Integer.compare(laterIndex(a), laterIndex(b));
        });

        List<T> selected = new ArrayList<>();
        for (T candidate : candidates) {
            boolean overlaps = false;
            for (T existing : selected) {
                if (laterIndex(candidate) <= earlierIndex(existing)
                        && laterIndex(existing) <= earlierIndex(candidate)) {
                    overlaps = true;
                    break;
                }
            }
            if (!overlaps) {
                selected.add(candidate);
            }
        }
        return selected;
    }

    private static int earlierIndex(Interval interval) {
        return Math.max(interval.getStartIndex(), interval.getEndIndex());
    }

    private static int laterIndex(Interval interval) {
        return Math.min(interval.getStartIndex(), interval.getEndIndex());
    }

    private static float earlierValue(Interval interval) {
        return interval.getStartIndex() >= interval.getEndIndex()
                ? interval.getStartValue() : interval.getEndValue();
    }

    private static float laterValue(Interval interval) {
        return interval.getStartIndex() >= interval.getEndIndex()
                ? interval.getEndValue() : interval.getStartValue();
    }
}
