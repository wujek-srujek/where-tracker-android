package com.where.tracker;


import android.app.Application;
import com.jakewharton.threetenabp.AndroidThreeTen;


public class WhereTrackerApplication extends Application {

    @Override
    public void onCreate() {
        super.onCreate();

        AndroidThreeTen.init(this);

        Thread.setDefaultUncaughtExceptionHandler(
                new LoggingUncaughtExceptionHandler(Thread.getDefaultUncaughtExceptionHandler()));
    }
}
