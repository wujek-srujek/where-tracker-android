package com.where.tracker;


import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;

import android.app.NotificationManager;
import android.os.Environment;
import org.threeten.bp.Instant;
import org.threeten.bp.format.DateTimeFormatter;


public class LoggingUncaughtExceptionHandler implements Thread.UncaughtExceptionHandler {

    private final Thread.UncaughtExceptionHandler defaultHandler;

    private final NotificationManager notificationManager;

    public LoggingUncaughtExceptionHandler(Thread.UncaughtExceptionHandler defaultHandler,
                                           NotificationManager notificationManager) {
        this.defaultHandler = defaultHandler;
        this.notificationManager = notificationManager;
    }

    @Override
    public void uncaughtException(Thread thread, Throwable throwable) {
        notificationManager.cancelAll();

        File outputFile = new File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                "crash." + DateTimeFormatter.ISO_INSTANT.format(Instant.now()) + ".txt");
        try (PrintWriter out = new PrintWriter(new OutputStreamWriter(
                new FileOutputStream(outputFile), StandardCharsets.UTF_8))) {
            throwable.printStackTrace(out);
        } catch (Throwable t) {
            // oh, well...
        }

        defaultHandler.uncaughtException(thread, throwable);
    }
}
