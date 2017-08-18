package com.where.tracker.helper;


import java.math.BigDecimal;

import org.threeten.bp.Instant;


public class InstantSerializationHelper {

    public static String toString(Instant src) {
        return src.getEpochSecond() + "." + src.getNano();
    }

    public static Instant fromString(String src) {
        // algorithm:
        // original value may have nanos (i.e. have digits after decimal point)
        // 1. get the seconds count discarding the digits
        // 2. subtract it from the original value to get the fraction part
        // 3. move the comma by 9 places to the right (nano is 10^-9) to get the nanos count
        // 4. create the instance with seconds and nanos
        BigDecimal value = new BigDecimal(src);
        long seconds = value.longValue();
        long nanos = value.subtract(new BigDecimal(seconds)).movePointRight(9).longValue();

        return Instant.ofEpochSecond(seconds, nanos);
    }
}
