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
}
