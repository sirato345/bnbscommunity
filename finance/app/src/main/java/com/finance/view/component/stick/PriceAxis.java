package com.finance.view.component.stick;

public final class PriceAxis {
    private PriceAxis() {
    }

    public static int yForPrice(float price, float min, float max, int height, boolean inverted) {
        float fraction = inverted ? (price - min) / (max - min) : (max - price) / (max - min);
        return Math.round(fraction * height);
    }

    public static float priceForY(int y, float min, float max, int height, boolean inverted) {
        float fraction = (float) y / height;
        return inverted ? min + fraction * (max - min) : max - fraction * (max - min);
    }
}
