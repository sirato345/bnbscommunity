package com.finance.view;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class EditIconTouchAreaTest {
    @Test
    public void usesRequestedBoundaries() {
        float right = EditIconTouchArea.rightEdge(100, 140);
        assertEquals(120, right, 0);
        assertTrue(EditIconTouchArea.contains(0, 75, 0, 75, right, 150));
        assertTrue(EditIconTouchArea.contains(119.9f, 149.9f, 0, 75, right, 150));
        assertFalse(EditIconTouchArea.contains(-0.1f, 100, 0, 75, right, 150));
        assertFalse(EditIconTouchArea.contains(120, 100, 0, 75, right, 150));
        assertFalse(EditIconTouchArea.contains(20, 74.9f, 0, 75, right, 150));
        assertFalse(EditIconTouchArea.contains(20, 150, 0, 75, right, 150));
    }

    @Test
    public void neverExtendsIntoOverlappingTimeframeControls() {
        float right = EditIconTouchArea.rightEdge(100, 80);
        assertEquals(80, right, 0);
        assertTrue(EditIconTouchArea.contains(79, 100, 0, 75, right, 150));
        assertFalse(EditIconTouchArea.contains(80, 100, 0, 75, right, 150));
    }
}
