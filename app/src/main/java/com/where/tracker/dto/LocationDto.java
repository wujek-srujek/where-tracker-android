package com.where.tracker.dto;


import lombok.Getter;
import lombok.Setter;
import org.threeten.bp.Instant;
import org.threeten.bp.ZoneId;


@Getter
@Setter
public class LocationDto {

    public enum SaveMode {

        AUTOMATIC,

        MANUAL
    }


    // format: second.nanosecond
    private Instant timestampUtc;

    private ZoneId timeZone;

    private double latitude;

    private double longitude;

    private Double accuracy;

    private SaveMode saveMode;
}
