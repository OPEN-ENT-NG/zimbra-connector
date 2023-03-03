package fr.openent.zimbra.utils;

import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;

public final class DateUtils {
    public static final String DATE_FORMAT = "yyyy-MM-dd";
    public static final String DATE_FORMAT_UTC = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'";
    public static final String DATE_FORMAT_SQL = "yyyy-MM-dd'T'HH:mm:ss.SSS";
    public static final String FRENCH_DATE_FORMAT ="dd-MM-yyyy";
    public static final String YEAR = "yyyy";
    public static final String DAY_MONTH_YEAR_HOUR_TIME = "dd/MM/yyyy HH:mm:ss";
    public static final String HOUR_MINUTE_SECOND = "HH:mm:ss";
    public static final String HOUR_MINUTE = "HH:mm";
    public static final String ICAL_DATE_FORMAT = "yyyyMMdd'T'HHmmss'Z'";
    public static final String ICAL_ALLDAY_FORMAT = "yyyyMMdd";

    private DateUtils()  {}

    public static Date parseDate(String dateToParse, String format) {
        try {
            SimpleDateFormat dateFormat = new SimpleDateFormat(format);
            return dateFormat.parse(dateToParse);
        } catch (ParseException e) {
            return null;
        }
    }

}
