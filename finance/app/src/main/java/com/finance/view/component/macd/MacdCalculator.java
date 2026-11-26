package com.finance.view.component.macd;

public final class MacdCalculator {

    public static final class Series {
        public final float[] dif;
        public final float[] dea;
        public final int lastValidIndex;

        private Series(float[] dif, float[] dea, int lastValidIndex) {
            this.dif = dif;
            this.dea = dea;
            this.lastValidIndex = lastValidIndex;
        }
    }

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

    public static Series calculateSeries(float[] closes, int fast, int slow, int signal) {
        if (fast <= 0 || slow < fast || signal <= 0) {
            throw new IllegalArgumentException("Invalid MACD periods");
        }
        int size = closes.length;
        float[] dif = new float[size];
        float[] dea = new float[size];
        if (size < slow) {
            return new Series(dif, dea, -1);
        }
        float fastEma = 0f;
        float slowEma = 0f;
        for (int i = size - 1; i >= size - fast; i--) {
            fastEma += closes[i];
        }
        fastEma /= fast;
        for (int i = size - 1; i >= size - slow; i--) {
            slowEma += closes[i];
        }
        slowEma /= slow;
        for (int i = size - fast - 1; i >= size - slow; i--) {
            fastEma = calculateNextEma(closes[i], fast, fastEma);
        }
        int firstDif = size - slow;
        dif[firstDif] = calculateMacd(fastEma, slowEma);
        for (int i = firstDif - 1; i >= 0; i--) {
            fastEma = calculateNextEma(closes[i], fast, fastEma);
            slowEma = calculateNextEma(closes[i], slow, slowEma);
            dif[i] = calculateMacd(fastEma, slowEma);
        }
        if (size < slow + signal - 1) {
            return new Series(dif, dea, -1);
        }
        int firstDea = size - slow - signal + 1;
        float sum = 0f;
        for (int i = firstDif; i >= firstDea; i--) {
            sum += dif[i];
        }
        dea[firstDea] = sum / signal;
        for (int i = firstDea - 1; i >= 0; i--) {
            dea[i] = calculateNextEma(dif[i], signal, dea[i + 1]);
        }
        return new Series(dif, dea, firstDea);
    }
}
