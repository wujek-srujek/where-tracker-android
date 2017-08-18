package com.where.tracker.db;


import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
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
import org.threeten.bp.format.DateTimeFormatter;


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
                LocationDb.Contract.Location.TIMESTAMP_UTC,
                InstantSerializationHelper.toString(dto.getTimestampUtc()));
        values.put(LocationDb.Contract.Location.TIME_ZONE, dto.getTimeZone().getId());
        values.put(LocationDb.Contract.Location.LATITUDE, dto.getLatitude());
        values.put(LocationDb.Contract.Location.LONGITUDE, dto.getLongitude());
        values.put(LocationDb.Contract.Location.ACCURACY, dto.getAccuracy());
        values.put(LocationDb.Contract.Location.SAVE_MODE, dto.getSaveMode().name());
        values.put(LocationDb.Contract.Location.UPLOADED, uploaded ? 1 : 0);

        db.insertOrThrow(LocationDb.Contract.Location.TABLE, null, values);
    }

    synchronized public Map<Long, LocationDto> getNotUploaded() {
        try (Cursor cursor = db.query(
                LocationDb.Contract.Location.TABLE, null,
                LocationDb.Contract.Location.UPLOADED + " = ?", new String[] { "0" },
                null, null, null)) {
            Map<Long, LocationDto> dtos = new LinkedHashMap<>(cursor.getCount());
            while (cursor.moveToNext()) {
                LocationDto dto = new LocationDto();
                dto.setTimestampUtc(
                        InstantSerializationHelper.fromString(cursor.getString(
                                cursor.getColumnIndexOrThrow(LocationDb.Contract.Location.TIMESTAMP_UTC))));

                dto.setTimeZone(
                        ZoneId.of(cursor.getString(
                                cursor.getColumnIndexOrThrow(LocationDb.Contract.Location.TIME_ZONE))));

                dto.setLatitude(
                        cursor.getDouble(
                                cursor.getColumnIndexOrThrow(LocationDb.Contract.Location.LATITUDE)));

                dto.setLongitude(
                        cursor.getDouble(
                                cursor.getColumnIndexOrThrow(LocationDb.Contract.Location.LONGITUDE)));

                dto.setAccuracy(
                        cursor.getDouble(
                                cursor.getColumnIndexOrThrow(LocationDb.Contract.Location.ACCURACY)));

                dto.setSaveMode(
                        LocationDto.SaveMode.valueOf(cursor.getString(
                                cursor.getColumnIndexOrThrow(LocationDb.Contract.Location.SAVE_MODE))));

                dtos.put(
                        cursor.getLong(
                                cursor.getColumnIndexOrThrow(LocationDb.Contract.Location._ID)),
                        dto);
            }
            return dtos;
        }
    }

    synchronized public int markUploaded(long id) {
        ContentValues values = new ContentValues();
        values.put(LocationDb.Contract.Location.UPLOADED, 1);

        return db.update(
                LocationDb.Contract.Location.TABLE,
                values,
                LocationDb.Contract.Location._ID + " = ?",
                new String[] { String.valueOf(id) });
    }

    synchronized public File exportDb(Context context) throws IOException {
        db.close();

        File outputFile = new File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                "location.db." + DateTimeFormatter.ISO_INSTANT.format(Instant.now()));
        try (InputStream in = new FileInputStream(context.getDatabasePath(LocationDb.Contract.DATABASE_NAME));
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
}
