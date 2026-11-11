package com.finance.view;

final class EditIconTouchArea {
    private EditIconTouchArea() {
    }

    static boolean contains(float x, float y, float left, float top, float right, float bottom) {
        return x >= left && x < right && y >= top && y < bottom;
    }

    static float rightEdge(float iconRight, float controlsLeft) {
        return Math.min(controlsLeft, (iconRight + controlsLeft) / 2f);
    }
}
