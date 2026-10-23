package com.finance.view.component.stick;

import java.util.ArrayList;
import java.util.List;

public final class ParabolicSarCalculator {

    private ParabolicSarCalculator() {
    }

    public static List<Float> calculate(List<StickInfo> sticks) {
        int size = sticks.size();
        List<Float> sarValues = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            sarValues.add(Float.NaN);
        }
        if (size < 2) {
            return sarValues;
        }

        final float accelerationStep = 0.02f;
        final float maximumAcceleration = 0.2f;
        int oldest = size - 1;
        StickInfo first = sticks.get(oldest);
        StickInfo second = sticks.get(oldest - 1);
        boolean upTrend = second.getClose() >= first.getClose();
        float sar = upTrend
                ? Math.min(first.getLow(), second.getLow())
                : Math.max(first.getHigh(), second.getHigh());
        float extremePoint = upTrend
                ? Math.max(first.getHigh(), second.getHigh())
                : Math.min(first.getLow(), second.getLow());
        float acceleration = accelerationStep;
        sarValues.set(oldest, sar);
        sarValues.set(oldest - 1, sar);

        for (int i = oldest - 2; i >= 0; i--) {
            StickInfo stick = sticks.get(i);
            float nextSar = sar + acceleration * (extremePoint - sar);
            StickInfo previous = sticks.get(i + 1);
            if (upTrend) {
                nextSar = Math.min(nextSar, previous.getLow());
                if (i + 2 < size) {
                    nextSar = Math.min(nextSar, sticks.get(i + 2).getLow());
                }
                if (stick.getLow() < nextSar) {
                    upTrend = false;
                    nextSar = extremePoint;
                    extremePoint = stick.getLow();
                    acceleration = accelerationStep;
                } else if (stick.getHigh() > extremePoint) {
                    extremePoint = stick.getHigh();
                    acceleration = Math.min(acceleration + accelerationStep,
                            maximumAcceleration);
                }
            } else {
                nextSar = Math.max(nextSar, previous.getHigh());
                if (i + 2 < size) {
                    nextSar = Math.max(nextSar, sticks.get(i + 2).getHigh());
                }
                if (stick.getHigh() > nextSar) {
                    upTrend = true;
                    nextSar = extremePoint;
                    extremePoint = stick.getHigh();
                    acceleration = accelerationStep;
                } else if (stick.getLow() < extremePoint) {
                    extremePoint = stick.getLow();
                    acceleration = Math.min(acceleration + accelerationStep,
                            maximumAcceleration);
                }
            }
            sar = nextSar;
            sarValues.set(i, sar);
        }
        return sarValues;
    }
}
