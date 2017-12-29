package com.where.tracker.helper;


import java.util.Locale;

import org.threeten.bp.LocalDateTime;
import org.threeten.bp.format.DateTimeFormatter;


public class DateTimeHelper {

    public static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd.MM.yyyy", Locale.US);

    public static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm:ss", Locale.US);

    public static String date(LocalDateTime localDateTime) {
        return DATE_FORMATTER.format(localDateTime);
    }

    public static String time(LocalDateTime localDateTime) {
        return TIME_FORMATTER.format(localDateTime);
    }
}
