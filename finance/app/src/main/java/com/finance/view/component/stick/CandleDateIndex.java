package com.finance.view.component.stick;

import java.util.Collections;
import java.util.List;

public final class CandleDateIndex {
    private final List<String> dates;

    public CandleDateIndex(List<String> dates) {
        this.dates = dates;
    }

    private int indexOf(String date) {
        return Collections.binarySearch(dates, date, Collections.reverseOrder());
    }

    public Integer xForDate(String date, String anchorDate, int anchorX, int stickWidth) {
        if (date == null || anchorDate == null || stickWidth <= 0) {
            return null;
        }
        int index = indexOf(date);
        int anchor = indexOf(anchorDate);
        if (index < 0 || anchor < 0) {
            return null;
        }
        return anchorX + (anchor - index) * stickWidth;
    }

    public String dateForX(int x, String anchorDate, int anchorX, int stickWidth) {
        if (anchorDate == null || stickWidth <= 0) {
            return null;
        }
        int anchor = indexOf(anchorDate);
        if (anchor < 0) {
            return null;
        }
        int index = anchor - Math.round((x - anchorX) / (float) stickWidth);
        return index >= 0 && index < dates.size() ? dates.get(index) : null;
    }
}
