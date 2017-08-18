package com.where.tracker.db;


import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;


public class LocationDbSqlHelper extends SQLiteOpenHelper {

    private static final int DATABASE_VERSION = 1;

    private static final String CREATE_LOCATION_TABLE = "create table location (\n"
            + LocationDb.Contract.Location._ID + " integer primary key autoincrement,\n"
            + LocationDb.Contract.Location.TIMESTAMP_UTC + " text not null,\n"
            + LocationDb.Contract.Location.TIME_ZONE + " text not null,\n"
            + LocationDb.Contract.Location.LATITUDE + " real not null,\n"
            + LocationDb.Contract.Location.LONGITUDE + " real not null,\n"
            + LocationDb.Contract.Location.ACCURACY + " real,\n"
            + LocationDb.Contract.Location.SAVE_MODE + " text not null,\n"
            + LocationDb.Contract.Location.UPLOADED + " integer not null,\n"
            + "constraint valid_save_mode check (save_mode in ('AUTOMATIC', 'MANUAL'))\n" +
            ");";

    public LocationDbSqlHelper(Context context) {
        super(context, LocationDb.Contract.DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL(CREATE_LOCATION_TABLE);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        // nothing yet
    }
}