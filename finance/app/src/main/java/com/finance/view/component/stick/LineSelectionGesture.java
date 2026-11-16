package com.finance.view.component.stick;

public final class LineSelectionGesture {
    private LineSelectionGesture() {
    }

    public static boolean isTap(boolean movedBeyondSlop, double finalDistance, float slop) {
        return !movedBeyondSlop && finalDistance <= slop;
    }

    public static boolean canStartDrawing(boolean hasSelectedLine, boolean startsOnSelectedLine) {
        return !hasSelectedLine || startsOnSelectedLine;
    }
}
