package com.finance.view.component;

import com.finance.common.Const;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class MacdModeTapTest {
    @Test
    public void combinedModeSelectsTappedPanel() {
        String both = Const.Macd.Both.toString();
        assertEquals(Const.Macd.KDJ.toString(), MacdView.modeAfterTap(both, 0, 400));
        assertEquals(Const.Macd.KDJ.toString(), MacdView.modeAfterTap(both, 199, 400));
        assertEquals(Const.Macd.Single.toString(), MacdView.modeAfterTap(both, 200, 400));
        assertEquals(Const.Macd.Single.toString(), MacdView.modeAfterTap(both, 399, 400));
    }

    @Test
    public void singleModesReturnToCombinedMode() {
        for (Const.Macd mode : new Const.Macd[] {
                Const.Macd.KDJ, Const.Macd.Single, Const.Macd.Double
        }) {
            assertEquals(Const.Macd.Both.toString(),
                    MacdView.modeAfterTap(mode.toString(), 100, 400));
        }
    }
}
