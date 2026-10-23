package com.finance.view.component.macd;

public enum SignalLight {
    RED,
    YELLOW,
    GREEN;

    public static SignalLight fromConditions(boolean... conditions) {
        int passed = 0;
        for (boolean condition : conditions) {
            if (condition) {
                passed++;
            }
        }
        if (passed == conditions.length && passed > 0) {
            return GREEN;
        }
        return passed == 0 ? RED : YELLOW;
    }
}
