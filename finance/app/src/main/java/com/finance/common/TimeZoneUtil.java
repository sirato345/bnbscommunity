package com.finance.common;

import com.finance.model.db.DBOperator;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

public class TimeZoneUtil {

    private static final String DEFAULT_LOCALE = "Asia/Tokyo";
    private static final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyyMMdd", Locale.US);
    private static final SimpleDateFormat dateTimeFormat = new SimpleDateFormat("yyyyMMddHHmmss", Locale.US);

    public static String getTimeZone(String symbol) {
        switch (symbol) {
            case "dow":
                return "America/New_York";
            case "shc":
                return "Asia/Shanghai";
            case "nikkei":
                return "Asia/Tokyo";
            default:
                return DEFAULT_LOCALE;
        }
    }

    public static String getLocaleLastBusinessDate(String symbol, DBOperator dbOperator) {
        String localeDateTime = getLocaleDateTime(symbol);
        Calendar calendar = parseDateTime(localeDateTime);

        adjustToBusinessDay(calendar, symbol, dbOperator);
        return dateFormat.format(calendar.getTime());
    }

    public static String getLocaleCurrentBusinessDate(String symbol, DBOperator dbOperator) {
        String localeDateTime = getLocaleDateTime(symbol);
        Calendar calendar = parseDateTime(localeDateTime);

        adjustToBusinessDay(calendar, symbol, dbOperator);
        return dateFormat.format(calendar.getTime());
    }

    public static String getLocaleNextBusinessDate(String symbol, DBOperator dbOperator, String maxDBDate) {
        Calendar calendar = parseDate(maxDBDate);
        calendar.add(Calendar.DAY_OF_MONTH, 1);

        adjustToBusinessDay(calendar, symbol, dbOperator);
        return dateFormat.format(calendar.getTime());
    }

    private static void adjustToBusinessDay(Calendar calendar, String symbol, DBOperator dbOperator) {
        // 调整为非周末
        while (isWeekend(calendar)) {
            calendar.add(Calendar.DAY_OF_MONTH, -1);
        }

        // 调整为非节假日
        while (true) {
            String date = dateFormat.format(calendar.getTime());
            if (dbOperator.isHoliday(symbol, date)) {
                calendar.add(Calendar.DAY_OF_MONTH, -1);
            } else {
                break;
            }
        }
    }

    private static boolean isWeekend(Calendar calendar) {
        int dayOfWeek = calendar.get(Calendar.DAY_OF_WEEK);
        return dayOfWeek == Calendar.SATURDAY || dayOfWeek == Calendar.SUNDAY;
    }

    public static String getLocaleDateTime(String symbol) {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMddHHmmss", Locale.US);
        sdf.setTimeZone(TimeZone.getTimeZone(getTimeZone(symbol)));
        return sdf.format(new Date());
    }

    public static String getLocaleDate(String symbol) {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd", Locale.US);
        sdf.setTimeZone(TimeZone.getTimeZone(getTimeZone(symbol)));
        return sdf.format(new Date());
    }

    public static String getLocaleTime(String symbol) {
        SimpleDateFormat sdf = new SimpleDateFormat("HHmmss", Locale.US);
        sdf.setTimeZone(TimeZone.getTimeZone(getTimeZone(symbol)));
        return sdf.format(new Date());
    }

    public static String getLocaleYear(String symbol) {
        String localeDate = getLocaleDate(symbol);
        return localeDate.substring(0, 4);
    }

    private static Date strToDate(String value) throws ParseException {
        return dateFormat.parse(value);
    }

    private static Calendar parseDate(String value) {
        try {
            Calendar cal = Calendar.getInstance();
            cal.setTime(dateFormat.parse(value));
            return cal;
        } catch (ParseException e) {
            throw new RuntimeException("日期解析失败: " + value, e);
        }
    }

    private static Calendar parseDateTime(String value) {
        try {
            Calendar cal = Calendar.getInstance();
            cal.setTime(dateTimeFormat.parse(value));
            return cal;
        } catch (ParseException e) {
            throw new RuntimeException("日期时间解析失败: " + value, e);
        }
    }
}