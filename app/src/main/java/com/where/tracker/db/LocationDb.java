package com.where.tracker.db;


import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.os.Environment;
import android.provider.BaseColumns;
import com.where.tracker.dto.LocationDto;
import com.where.tracker.helper.InstantSerializationHelper;
import org.threeten.bp.Instant;
import org.threeten.bp.ZoneId;
import org.threeten.bp.ZonedDateTime;
import org.threeten.bp.format.DateTimeFormatter;
import org.threeten.bp.temporal.ChronoUnit;


public class LocationDb implements AutoCloseable {

    public static final class Contract {

        private Contract() {
            // nope
        }

        public static final String DATABASE_NAME = "location.db";


        public static class Location implements BaseColumns {

            public static final String TABLE = "location";

            public static final String TIMESTAMP_UTC = "timestamp_utc";

            public static final String TIME_ZONE = "time_zone";

            public static final String LATITUDE = "latitude";

            public static final String LONGITUDE = "longitude";

            public static final String ACCURACY = "accuracy";

            public static final String SAVE_MODE = "save_mode";

            public static final String UPLOADED = "uploaded";
        }
    }


    private final SQLiteOpenHelper helper;

    private SQLiteDatabase db;

    public LocationDb(SQLiteOpenHelper helper) {
        this.helper = helper;

        init();
    }

    @Override
    synchronized public void close() {
        db.close();
    }

    // each insert is a separate transaction, seems safer as at least some data may be saved
    synchronized public void insert(LocationDto dto, boolean uploaded) {
        ContentValues values = new ContentValues();
        values.put(
                Contract.Location.TIMESTAMP_UTC,
                InstantSerializationHelper.toString(dto.getTimestampUtc()));
        values.put(Contract.Location.TIME_ZONE, dto.getTimeZone().getId());
        values.put(Contract.Location.LATITUDE, dto.getLatitude());
        values.put(Contract.Location.LONGITUDE, dto.getLongitude());
        values.put(Contract.Location.ACCURACY, dto.getAccuracy());
        values.put(Contract.Location.SAVE_MODE, dto.getSaveMode().name());
        values.put(Contract.Location.UPLOADED, uploaded ? 1 : 0);

        db.insertOrThrow(Contract.Location.TABLE, null, values);
    }

    synchronized public ArrayList<LocationDto> getDays(int count) {
        if (count < 1) {
            throw new IllegalArgumentException("1 is minimum");
        }

        ZonedDateTime startOfToday = ZonedDateTime.now().truncatedTo(ChronoUnit.DAYS);
        ZonedDateTime begin = startOfToday.minusDays(count - 1);
        ZonedDateTime end = startOfToday.plusDays(1);
        String beginS = InstantSerializationHelper.toString(begin.toInstant());
        String endS = InstantSerializationHelper.toString(end.toInstant());

        try (Cursor cursor = db.query(
                Contract.Location.TABLE, null,
                Contract.Location.TIMESTAMP_UTC + " between ? and ?",
                new String[] { beginS, endS },
                null, null,
                Contract.Location.TIMESTAMP_UTC + " desc",
                null)) {
            ArrayList<LocationDto> dtos = new ArrayList<>(cursor.getCount());
            while (cursor.moveToNext()) {
                dtos.add(locationDtoFromCursor(cursor));
            }

            // so let's sort here by timestamp_utc, ascending
            Collections.sort(dtos, new Comparator<LocationDto>() {

                @Override
                public int compare(LocationDto left, LocationDto right) {
                    return left.getTimestampUtc().compareTo(right.getTimestampUtc());
                }
            });

            return dtos;
        }
    }

    synchronized public Map<Long, LocationDto> getNotUploaded() {
        try (Cursor cursor = db.query(
                Contract.Location.TABLE, null,
                Contract.Location.UPLOADED + " = ?", new String[] { "0" },
                null, null, null)) {
            Map<Long, LocationDto> dtos = new LinkedHashMap<>(cursor.getCount());
            while (cursor.moveToNext()) {
                LocationDto dto = locationDtoFromCursor(cursor);

                dtos.put(
                        cursor.getLong(
                                cursor.getColumnIndexOrThrow(Contract.Location._ID)),
                        dto);
            }

            return dtos;
        }
    }

    synchronized public int markUploaded(long id) {
        ContentValues values = new ContentValues();
        values.put(Contract.Location.UPLOADED, 1);

        return db.update(
                Contract.Location.TABLE,
                values,
                Contract.Location._ID + " = ?",
                new String[] { String.valueOf(id) });
    }

    synchronized public File exportDb(Context context) throws IOException {
        db.close();

        File outputFile = new File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                "location.db." + DateTimeFormatter.ISO_INSTANT.format(Instant.now()));
        try (InputStream in = new FileInputStream(context.getDatabasePath(Contract.DATABASE_NAME));
             OutputStream out = new FileOutputStream(outputFile)) {
            byte[] buffer = new byte[1024];
            int length;
            while ((length = in.read(buffer)) > 0) {
                out.write(buffer, 0, length);
            }
        } finally {
            init();
        }

        return outputFile;
    }

    private void init() {
        db = helper.getWritableDatabase();
    }

    private LocationDto locationDtoFromCursor(Cursor cursor) {
        LocationDto locationDto = new LocationDto();
        locationDto.setTimestampUtc(
                InstantSerializationHelper.fromString(cursor.getString(
                        cursor.getColumnIndexOrThrow(Contract.Location.TIMESTAMP_UTC))));

        locationDto.setTimeZone(
                ZoneId.of(cursor.getString(
                        cursor.getColumnIndexOrThrow(Contract.Location.TIME_ZONE))));

        locationDto.setLatitude(
                cursor.getDouble(
                        cursor.getColumnIndexOrThrow(Contract.Location.LATITUDE)));

        locationDto.setLongitude(
                cursor.getDouble(
                        cursor.getColumnIndexOrThrow(Contract.Location.LONGITUDE)));

        locationDto.setAccuracy(
                cursor.getDouble(
                        cursor.getColumnIndexOrThrow(Contract.Location.ACCURACY)));

        locationDto.setSaveMode(
                LocationDto.SaveMode.valueOf(cursor.getString(
                        cursor.getColumnIndexOrThrow(Contract.Location.SAVE_MODE))));

        return locationDto;
    }
}
