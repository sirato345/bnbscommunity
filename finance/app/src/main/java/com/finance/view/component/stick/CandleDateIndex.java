package com.finance.view.component.stick;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

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
        if (anchor < 0) {
            return null;
        }
        if (index >= 0) {
            return anchorX + (anchor - index) * stickWidth;
        }
        Double estimatedIndex = estimateIndex(date, -index - 1);
        return estimatedIndex == null ? null
                : (int) Math.round(anchorX + (anchor - estimatedIndex) * stickWidth);
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
        if (index >= 0 && index < dates.size()) {
            return dates.get(index);
        }
        if (dates.size() < 2) {
            return null;
        }
        int boundary = index < 0 ? 0 : dates.size() - 1;
        int neighbor = index < 0 ? 1 : dates.size() - 2;
        return dateAtBoundaryOffset(boundary, neighbor, boundary - index);
    }

    private SimpleDateFormat formatFor(String date) {
        SimpleDateFormat format = new SimpleDateFormat(
                date.length() == 8 ? "yyyyMMdd" : "yyyy-MM-dd HH:mm", Locale.US);
        format.setTimeZone(TimeZone.getTimeZone("UTC"));
        format.setLenient(false);
        return format;
    }

    private Date parseDate(String date, SimpleDateFormat format) {
        try {
            return format.parse(date);
        } catch (ParseException e) {
            return null;
        }
    }

    private int calendarMonths(long interval) {
        long days = Math.abs(interval) / 86400000L;
        if (days >= 27 && days <= 32) {
            return 1;
        }
        if (days >= 85 && days <= 100) {
            return 3;
        }
        if (days >= 350 && days <= 380) {
            return 12;
        }
        return 0;
    }

    private String dateAtBoundaryOffset(int boundary, int neighbor, int steps) {
        String base = dates.get(boundary);
        SimpleDateFormat format = formatFor(base);
        Date baseDate = parseDate(base, format);
        Date neighborDate = parseDate(dates.get(neighbor), format);
        if (baseDate == null || neighborDate == null) {
            return null;
        }
        long interval = Math.abs(baseDate.getTime() - neighborDate.getTime());
        if (interval == 0) {
            return null;
        }
        int months = calendarMonths(interval);
        if (base.length() == 8 && months != 0) {
            Calendar calendar = Calendar.getInstance(TimeZone.getTimeZone("UTC"), Locale.US);
            calendar.setTime(baseDate);
            int day = calendar.get(Calendar.DAY_OF_MONTH);
            calendar.set(Calendar.DAY_OF_MONTH, 1);
            calendar.add(Calendar.MONTH, steps * months);
            calendar.set(Calendar.DAY_OF_MONTH,
                    Math.min(day, calendar.getActualMaximum(Calendar.DAY_OF_MONTH)));
            return format.format(calendar.getTime());
        }
        return format.format(new Date(baseDate.getTime() + interval * steps));
    }

    private Double estimateIndex(String date, int insertion) {
        if (dates.size() < 2) {
            return null;
        }
        if (insertion > 0 && insertion < dates.size()) {
            SimpleDateFormat format = formatFor(date);
            Date value = parseDate(date, format);
            Date newer = parseDate(dates.get(insertion - 1), format);
            Date older = parseDate(dates.get(insertion), format);
            if (value == null || newer == null || older == null
                    || newer.getTime() == older.getTime()) {
                return null;
            }
            return insertion - 1 + (double) (newer.getTime() - value.getTime())
                    / (newer.getTime() - older.getTime());
        }
        int boundary = insertion == 0 ? 0 : dates.size() - 1;
        int neighbor = insertion == 0 ? 1 : dates.size() - 2;
        String base = dates.get(boundary);
        SimpleDateFormat format = formatFor(base);
        Date value = parseDate(date, format);
        Date baseDate = parseDate(base, format);
        Date neighborDate = parseDate(dates.get(neighbor), format);
        if (value == null || baseDate == null || neighborDate == null) {
            return null;
        }
        long interval = Math.abs(baseDate.getTime() - neighborDate.getTime());
        if (interval == 0) {
            return null;
        }
        int months = base.length() == 8 ? calendarMonths(interval) : 0;
        if (months == 0) {
            return boundary - (double) (value.getTime() - baseDate.getTime()) / interval;
        }
        Calendar baseCalendar = Calendar.getInstance(TimeZone.getTimeZone("UTC"), Locale.US);
        Calendar valueCalendar = Calendar.getInstance(TimeZone.getTimeZone("UTC"), Locale.US);
        baseCalendar.setTime(baseDate);
        valueCalendar.setTime(value);
        int monthDifference = (valueCalendar.get(Calendar.YEAR) - baseCalendar.get(Calendar.YEAR)) * 12
                + valueCalendar.get(Calendar.MONTH) - baseCalendar.get(Calendar.MONTH);
        int steps = Math.floorDiv(monthDifference, months);
        String first = dateAtBoundaryOffset(boundary, neighbor, steps);
        String next = dateAtBoundaryOffset(boundary, neighbor, steps + 1);
        if (first == null || next == null) {
            return null;
        }
        Date firstDate = parseDate(first, format);
        Date nextDate = parseDate(next, format);
        if (firstDate == null || nextDate == null || nextDate.getTime() == firstDate.getTime()) {
            return null;
        }
        double fraction = (double) (value.getTime() - firstDate.getTime())
                / (nextDate.getTime() - firstDate.getTime());
        return boundary - steps - fraction;
    }
}
