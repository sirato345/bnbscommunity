package com.finance.model.net;

import org.junit.Test;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

public class BmnrHistoryTest {
    private static final String HEADER = "date,open,high,low,close,volume\n";
    private static final String OVERLAP = "2025-06-05,7.25,7.89,6.8,7.75,100\n"
            + "2025-06-06,7.19,7.69,6.31,7.55,100\n"
            + "2025-06-09,7.58,7.62,6.36,6.89,100\n";
    private static final List<String> YAHOO = Arrays.asList(
            "2025-06-05,7.25,7.89,6.8,7.75",
            "2025-06-06,7.35,7.69,6.31,7.545",
            "2025-06-09,7.53,7.615,6.36,6.89");

    @Test
    public void keepsOnlyRequestedOlderHistoryAndReportsKnownInvalidCandle() throws Exception {
        String csv = HEADER + "2020-12-31,10,10,10,10,1\n"
                + "2021-01-04,95.6,95.6,76,76,7\n"
                + "2021-04-30,10,11,9,10,7\n"
                + "2021-05-01,11,12,10,11,7\n"
                + "2023-01-03,14,14,0,14,213\n"
                + "2025-06-04,14.82,22,14.82,19,15050\n" + OVERLAP;
        BmnrHistory.Result result = BmnrHistory.parse(csv, YAHOO);

        assertEquals(Arrays.asList("2021-05-01,11,12,10,11",
                "2025-06-04,14.82,22,14.82,19"), result.records);
        assertEquals(Arrays.asList("2023-01-03"), result.invalidDates);
    }

    @Test
    public void rejectsMismatchedOrInsufficientOverlap() {
        String csv = HEADER + "2021-05-03,95.6,95.6,76,76,7\n" + OVERLAP;
        assertThrows(IOException.class, () -> BmnrHistory.parse(csv, Arrays.asList(
                "2025-06-05,7,8,6,3", YAHOO.get(1), YAHOO.get(2))));
        assertThrows(IOException.class, () -> BmnrHistory.parse(csv, YAHOO.subList(0, 2)));
    }

    @Test
    public void rejectsOtherInvalidOrUnorderedCandles() {
        String invalid = HEADER + "2021-05-03,10,10,0,10,1\n" + OVERLAP;
        assertThrows(IOException.class, () -> BmnrHistory.parse(invalid, YAHOO));
        String unordered = HEADER + "2021-05-04,10,10,10,10,1\n"
                + "2021-05-03,10,10,10,10,1\n" + OVERLAP;
        assertThrows(IOException.class, () -> BmnrHistory.parse(unordered, YAHOO));
    }
}
