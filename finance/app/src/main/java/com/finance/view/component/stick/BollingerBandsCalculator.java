package com.finance.view.component.stick;

public final class BollingerBandsCalculator {
    public static final int PERIOD = 20;
    public static final double DEVIATIONS = 2.0;

    private BollingerBandsCalculator() {
    }

    public static final class Bands {
        public final float middle;
        public final float upper;
        public final float lower;

        private Bands(double middle, double deviation) {
            this.middle = (float) middle;
            this.upper = (float) (middle + DEVIATIONS * deviation);
            this.lower = (float) (middle - DEVIATIONS * deviation);
        }
    }

    // Closes are ordered newest first; each window includes the current candle.
    public static Bands[] calculate(float[] closes) {
        Bands[] result = new Bands[closes.length];
        for (int i = 0; i + PERIOD <= closes.length; i++) {
            double sum = 0;
            boolean valid = true;
            for (int j = i; j < i + PERIOD; j++) {
                if (Float.isNaN(closes[j]) || Float.isInfinite(closes[j])) {
                    valid = false;
                    break;
                }
                sum += closes[j];
            }
            if (!valid) {
                continue;
            }
            double middle = sum / PERIOD;
            double squaredDifferences = 0;
            for (int j = i; j < i + PERIOD; j++) {
                double difference = closes[j] - middle;
                squaredDifferences += difference * difference;
            }
            result[i] = new Bands(middle, Math.sqrt(squaredDifferences / PERIOD));
        }
        return result;
    }
}
