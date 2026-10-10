package com.finance.view.component.macd;

public final class MacdCalculator {

    private MacdCalculator() {
    }

    public static float calculateNextEma(float value, int period, float previousEma) {
        float smoothingFactor = 2f / (period + 1);
        return previousEma + smoothingFactor * (value - previousEma);
    }

    public static float calculateMacd(float fastEma, float slowEma) {
        return fastEma - slowEma;
    }

    public static float calculateHistogram(float macd, float signal) {
        return macd - signal;
    }
}
