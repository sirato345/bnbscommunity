package com.finance.view.component.stick;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class LineSelectionGestureTest {
    @Test
    public void selectsOnlyWhenFingerIsReleasedWithoutDragging() {
        assertTrue(LineSelectionGesture.isTap(false, 0, 30));
        assertTrue(LineSelectionGesture.isTap(false, 30, 30));
        assertFalse(LineSelectionGesture.isTap(false, 31, 30));
        assertFalse(LineSelectionGesture.isTap(true, 0, 30));
    }

    @Test
    public void onlyAllowsDrawingWhenNoLineIsSelectedOrGestureStartsOnSelectedLine() {
        assertTrue(LineSelectionGesture.canStartDrawing(false, false));
        assertTrue(LineSelectionGesture.canStartDrawing(false, true));
        assertTrue(LineSelectionGesture.canStartDrawing(true, true));
        assertFalse(LineSelectionGesture.canStartDrawing(true, false));
    }
}
