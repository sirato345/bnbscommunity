package com.finance.view.component;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

import static org.junit.Assert.assertEquals;

@RunWith(AndroidJUnit4.class)
public class MacdPriceTextBoundsTest {
    @Test
    public void missingPriceUsesDigitHeightForSizing() {
        int placeholderHeight = MacdView.getPriceTextBounds("--", 60).height();
        int priceHeight = MacdView.getPriceTextBounds("12.87", 60).height();
        assertEquals(priceHeight, placeholderHeight);
    }
}
