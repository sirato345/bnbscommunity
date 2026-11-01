package com.finance.view.component.macd;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class SignalLightTest {

    @Test
    public void allConditionsPassedIsGreen() {
        assertEquals(SignalLight.GREEN, SignalLight.fromConditions(true, true));
    }

    @Test
    public void someConditionsPassedIsYellow() {
        assertEquals(SignalLight.YELLOW, SignalLight.fromConditions(true, false, true));
    }

    @Test
    public void noConditionsPassedIsRed() {
        assertEquals(SignalLight.RED, SignalLight.fromConditions(false, false, false));
    }

    @Test
    public void risingIndicatorDirectionsAreGreen() {
        assertEquals(SignalLight.GREEN,
                SignalLight.fromDirections(true, false, true, false));
    }

    @Test
    public void fallingIndicatorDirectionsAreRed() {
        assertEquals(SignalLight.RED,
                SignalLight.fromDirections(false, true, false, true));
    }

    @Test
    public void mixedOrFlatIndicatorDirectionsAreYellow() {
        assertEquals(SignalLight.YELLOW,
                SignalLight.fromDirections(true, false, false, true));
        assertEquals(SignalLight.YELLOW,
                SignalLight.fromDirections(false, false, false, false));
    }

    @Test
    public void bearishConditionsMapZeroOneAndTwoToGreenYellowAndRed() {
        assertEquals(SignalLight.GREEN,
                SignalLight.fromBearishConditions(false, false));
        assertEquals(SignalLight.YELLOW,
                SignalLight.fromBearishConditions(true, false));
        assertEquals(SignalLight.RED,
                SignalLight.fromBearishConditions(true, true));
    }

}
