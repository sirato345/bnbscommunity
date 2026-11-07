package com.finance.view.component.stick;

public final class TdSequentialCalculator {
    private TdSequentialCalculator() {
    }

    public static int[] calculate(float[] closes, int visibleCount) {
        int[] signals = new int[Math.min(visibleCount, closes.length)];
        for (int i = 0; i < signals.length; i++) {
            int direction = 0;
            int count = 0;
            for (int j = i; j < i + 9 && j + 4 < closes.length; j++) {
                int comparison = closes[j] > closes[j + 4] ? 1
                        : closes[j] < closes[j + 4] ? -1 : 0;
                if (comparison == 0 || (direction != 0 && comparison != direction)) {
                    break;
                }
                direction = comparison;
                count++;
            }
            if (count >= 8) {
                signals[i] = direction * count;
            }
        }
        return signals;
    }
}
