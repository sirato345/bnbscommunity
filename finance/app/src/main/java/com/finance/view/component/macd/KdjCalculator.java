package com.finance.view.component.macd;

public final class KdjCalculator {

    public static final int PERIOD = 9;
    public static final float INITIAL_VALUE = 50f;

    private KdjCalculator() {
    }

    public static float calculateRsv(float highest, float lowest, float close) {
        if (highest == lowest) {
            return INITIAL_VALUE;
        }
        return (close - lowest) / (highest - lowest) * 100f;
    }

    public static float calculateNextK(float rsv, float previousK) {
        return (2f * previousK + rsv) / 3f;
    }

    public static float calculateNextD(float currentK, float previousD) {
        return (2f * previousD + currentK) / 3f;
    }

    public static float calculateJ(float k, float d) {
        return 3f * k - 2f * d;
    }
}
