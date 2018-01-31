package com.where.tracker.activity;


import java.io.File;
import java.io.IOException;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.IntentSender;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.PowerManager;
import android.provider.Settings;
import android.support.annotation.NonNull;
import android.support.v4.content.LocalBroadcastManager;
import android.text.SpannableStringBuilder;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.NumberPicker;
import android.widget.Switch;
import android.widget.TextView;
import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.common.api.CommonStatusCodes;
import com.google.android.gms.common.api.ResolvableApiException;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.LocationSettingsRequest;
import com.google.android.gms.location.LocationSettingsResponse;
import com.google.android.gms.location.LocationSettingsStatusCodes;
import com.google.android.gms.location.SettingsClient;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import com.where.tracker.R;
import com.where.tracker.db.LocationDb;
import com.where.tracker.dto.LocalLocationResultDto;
import com.where.tracker.dto.LocationDto;
import com.where.tracker.dto.NewLocationResultDto;
import com.where.tracker.helper.DateTimeHelper;
import com.where.tracker.helper.SpannableHelper;
import com.where.tracker.service.WhereTrackingService;
import org.threeten.bp.Instant;
import org.threeten.bp.LocalDateTime;
import org.threeten.bp.ZoneId;
import org.threeten.bp.format.DateTimeFormatter;


public class TrackerActivity extends Activity {

    private static final int LOCATION_AUTOMATIC_REQUEST_CODE = 1;

    private static final int LOCATION_MANUAL_REQUEST_CODE = 2;

    private static final int WHITELIST_REQUEST_CODE = 3;

    private static final int WRITE_REQUEST_CODE = 4;

    private static final int AUTOMATIC_INTERVAL = 5 * 1000;

    public static final int AUTOMATIC_INTERVAL_FILTER = 180; // record every 3 minutes

    private static final int COLOR_ORANGE = Color.argb(255, 255, 165, 0);

    private Switch dryRunSwitch;

    private TextView logView;

    private LocationRequest locationRequest;

    private LocationDb locationDb;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_tracker);

        dryRunSwitch = findViewById(R.id.dryRunSwitch);
        logView = findViewById(R.id.logView);

        locationRequest = new LocationRequest();
        // configure according to Google recommendations for real-time tracking
        locationRequest.setInterval(AUTOMATIC_INTERVAL);
        locationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);

        locationDb = LocationDb.get();

        LocalBroadcastManager broadcastManager = LocalBroadcastManager.getInstance(this);
        broadcastManager.registerReceiver(
                new NewLocationResultReceiver(),
                new IntentFilter(WhereTrackingService.BROADCAST_NEW_LOCATION_RESULT));
        broadcastManager.registerReceiver(
                new LocalLocationsResultReceiver(),
                new IntentFilter(WhereTrackingService.BROADCAST_LOCAL_LOCATION_RESULT));
        broadcastManager.registerReceiver(
                new MessageBroadcastReceiver(),
                new IntentFilter(WhereTrackingService.BROADCAST_NEW_MESSAGE));

        log("DEF", "Activity created");
    }

    @Override
    public void onRequestPermissionsResult(
            int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        switch (requestCode) {
            case LOCATION_AUTOMATIC_REQUEST_CODE:
            case LOCATION_MANUAL_REQUEST_CODE:
                if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    log("PERM", "Location permission granted");
                    assertPhoneConfiguration(requestCode);
                } else {
                    logTrackingUnavailable("PERM", "permission denied");
                }
                break;

            case WRITE_REQUEST_CODE:
                if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    log("PERM", "Write permission granted");
                    exportDb();
                } else {
                    log("PERM", "Write permission denied");
                }
                break;
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        switch (requestCode) {
            case LOCATION_AUTOMATIC_REQUEST_CODE:
                switch (resultCode) {
                    case Activity.RESULT_OK:
                        log("CONF", "Phone configured");
                        assertInPowerWhitelist();
                        break;

                    case Activity.RESULT_CANCELED:
                        logTrackingUnavailable("CONF", "phone not configured");
                        break;
                }
                break;

            case LOCATION_MANUAL_REQUEST_CODE:
                switch (resultCode) {
                    case Activity.RESULT_OK:
                        log("CONF", "Phone configured");
                        manualSave();
                        break;

                    case Activity.RESULT_CANCELED:
                        logTrackingUnavailable("CONF", "phone not configured");
                        break;
                }
                break;

            case WHITELIST_REQUEST_CODE:
                switch (resultCode) {
                    case Activity.RESULT_OK:
                        log("BAT", "App whitelisted");
                        startAutomatic();
                        break;

                    case Activity.RESULT_CANCELED:
                        logTrackingUnavailable("BAT", "battery optimizations prevent background operations");
                        break;
                }
        }
    }

    @Override
    public void onBackPressed() {
        new AlertDialog.Builder(this)
                .setMessage("Are you sure you want to exit?")
                .setNegativeButton(android.R.string.no, null)
                .setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener() {

                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        TrackerActivity.super.onBackPressed();
                    }
                })
                .create()
                .show();
    }

    public void toggleDryRun(View view) {
        new AlertDialog.Builder(this)
                .setMessage("Are you sure you want to change this setting?")
                .setNegativeButton(android.R.string.no, new DialogInterface.OnClickListener() {

                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        // toggle back to original setting, i.e. undo this event
                        dryRunSwitch.toggle();
                    }
                })
                .setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener() {

                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        toggleDryRun();
                    }
                })
                .create()
                .show();
    }

    public void startAutomatic(View view) {
        safeLocationRequest(LOCATION_AUTOMATIC_REQUEST_CODE);
    }

    public void stopAutomatic(View view) {
        stopAutomatic();
    }

    public void manualSave(View view) {
        safeLocationRequest(LOCATION_MANUAL_REQUEST_CODE);
    }

    public void uploadLocal(View view) {
        uploadLocal();
    }

    public void exportDb(View view) {
        exportDbWithPermission();
    }

    public void clearLog(View view) {
        logView.setText(null);
    }

    public void showRoute(View view) {
        showRoute();
    }

    private void safeLocationRequest(Integer requestCode) {
        // first, it checks the permissions
        // then, if granted, checks phone settings
        assertLocationPermission(requestCode);
    }

    private void assertLocationPermission(Integer requestCode) {
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(
                    new String[] { Manifest.permission.ACCESS_FINE_LOCATION },
                    requestCode);
            return;
        }

        log("PERM", "Location permission already granted");
        assertPhoneConfiguration(requestCode);
    }

    private void assertPhoneConfiguration(final Integer requestCode) {
        SettingsClient settingsClient = LocationServices.getSettingsClient(this);
        Task<LocationSettingsResponse> settingsTask = settingsClient.checkLocationSettings(
                new LocationSettingsRequest.Builder().addLocationRequest(locationRequest).build());

        settingsTask.addOnSuccessListener(this, new OnSuccessListener<LocationSettingsResponse>() {

            @Override
            public void onSuccess(LocationSettingsResponse locationSettingsResponse) {
                log("CONF", "Phone already configured");
                if (requestCode == LOCATION_AUTOMATIC_REQUEST_CODE) {
                    assertInPowerWhitelist();
                } else {
                    manualSave();
                }
            }
        }).addOnFailureListener(this, new OnFailureListener() {

            @Override
            public void onFailure(@NonNull Exception e) {
                int statusCode = ((ApiException) e).getStatusCode();
                switch (statusCode) {
                    case CommonStatusCodes.RESOLUTION_REQUIRED:
                        try {
                            ResolvableApiException resolvable = (ResolvableApiException) e;
                            resolvable.startResolutionForResult(TrackerActivity.this, requestCode);
                        } catch (IntentSender.SendIntentException sendEx) {
                            // cannot happen
                        }
                        break;

                    case LocationSettingsStatusCodes.SETTINGS_CHANGE_UNAVAILABLE:
                        logTrackingUnavailable("CONF", "cannot change phone settings");
                        break;
                }
            }
        });
    }

    // necessary to be able to use network in doze
    // in the future, network operations should use job scheduler if not in foreground
    private void assertInPowerWhitelist() {
        PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        String packageName = getPackageName();
        // will get rid of this when correct doze behavior is implemented
        if (pm.isIgnoringBatteryOptimizations(packageName)) {
            log("BAT", "App already whitelisted");
            startAutomatic();
        } else {
            Intent intent = new Intent();
            // this is not a Google Play candidate, ignore the warning
            // will get rid of this when correct doze behavior is implemented
            intent.setAction(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
            intent.setData(Uri.parse("package:" + packageName));
            startActivityForResult(intent, WHITELIST_REQUEST_CODE);
        }
    }

    private void exportDbWithPermission() {
        if (checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(
                    new String[] { Manifest.permission.WRITE_EXTERNAL_STORAGE },
                    WRITE_REQUEST_CODE);
            return;
        }

        log("PERM", "Write permission already granted");
        exportDb();
    }

    private void toggleDryRun() {
        Intent dryRunIntent = serviceIntent(WhereTrackingService.COMMAND_SET_DRY_RUN);
        addDryRunSetting(dryRunIntent);
        startService(dryRunIntent);
    }

    private void startAutomatic() {
        // starts a (foreground) service
        Intent startIntent = serviceIntent(WhereTrackingService.COMMAND_START_AUTOMATIC);
        addDryRunSetting(startIntent);
        startIntent.putExtra(WhereTrackingService.EXTRA_START_AUTOMATIC_LOCATION_REQUEST, locationRequest);
        if (Build.VERSION.SDK_INT == Build.VERSION_CODES.O) {
            startForegroundService(startIntent);
        } else {
            startService(startIntent);
        }
    }

    private void stopAutomatic() {
        Intent startIntent = serviceIntent(WhereTrackingService.COMMAND_STOP_AUTOMATIC);
        startService(startIntent);
    }

    private void manualSave() {
        Intent saveIntent = serviceIntent(WhereTrackingService.COMMAND_MANUAL);
        addDryRunSetting(saveIntent);
        startService(saveIntent);
    }

    private void uploadLocal() {
        Intent uploadIntent = serviceIntent(WhereTrackingService.COMMAND_UPLOAD_LOCAL);
        startService(uploadIntent);
    }

    private Intent serviceIntent(String command) {
        Intent intent = new Intent(this, WhereTrackingService.class);
        intent.setAction(command);

        return intent;
    }

    private void addDryRunSetting(Intent intent) {
        intent.putExtra(WhereTrackingService.EXTRA_PAYLOAD, isDryRun());
    }

    private void exportDb() {
        try {
            File outputFile = new File(
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                    "location.db." + DateTimeFormatter.ISO_INSTANT.format(Instant.now()));
            locationDb.exportDb(this, outputFile);
            log("DEF", "Database exported to " + outputFile);
        } catch (IOException exc) {
            log("DEF", "Exporting database failed: " + exc);
        }
    }

    private void showRoute() {
        LayoutInflater inflater = getLayoutInflater();
        View dialogView = inflater.inflate(R.layout.dialog_route, null);

        final NumberPicker fromDaysAgoPicker = dialogView.findViewById(R.id.fromDaysAgoPicker);
        final NumberPicker numberOfDaysPicker = dialogView.findViewById(R.id.numberOfDaysPicker);
        final Switch continuousSwitch = dialogView.findViewById(R.id.continuousSwitch);

        fromDaysAgoPicker.setMinValue(1);
        fromDaysAgoPicker.setMaxValue(365);
        fromDaysAgoPicker.setValue(1);

        numberOfDaysPicker.setMinValue(1);
        numberOfDaysPicker.setMaxValue(365);
        numberOfDaysPicker.setValue(1);

        new AlertDialog.Builder(this)
                .setTitle("How many days?")
                .setCancelable(false)
                .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {

                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        Intent intent = new Intent(TrackerActivity.this, RouteActivity.class);
                        intent.putExtra(RouteActivity.EXTRA_FROM_DAYS_AGO, fromDaysAgoPicker.getValue());
                        intent.putExtra(RouteActivity.EXTRA_NUMBER_OF_DAYS, numberOfDaysPicker.getValue());
                        intent.putExtra(RouteActivity.EXTRA_CONTINUOUS, continuousSwitch.isChecked());
                        startActivity(intent);
                        log("DEF", "Showing route");
                    }
                })
                .setNegativeButton(android.R.string.cancel, new DialogInterface.OnClickListener() {

                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        log("RT", "Showing route cancelled");
                    }
                })
                .setView(dialogView)
                .create()
                .show();
    }

    private void logTrackingUnavailable(String tag, String detail) {
        String message = "Location tracking is unavailable";

        if (detail != null) {
            message += ": " + detail;
        }

        log(tag, message);
    }

    private void log(CharSequence tag, CharSequence firstLine, CharSequence... rest) {
        SpannableStringBuilder entry = new SpannableStringBuilder();
        entry.append("[").append(tag).append("] ").append(firstLine);
        for (CharSequence seq : rest) {
            entry.append("\n").append("[").append(tag).append(" …").append("] ").append(seq);
        }
        logView.setText(entry.append("\n").append(logView.getText()));
    }

    private boolean isDryRun() {
        return !dryRunSwitch.isChecked();
    }


    private class NewLocationResultReceiver extends BroadcastReceiver {

        private int receiveCounter;

        @Override
        public void onReceive(Context context, Intent intent) {
            ++receiveCounter;

            String tag = "#" + receiveCounter;

            NewLocationResultDto newLocationResultDto = intent.getParcelableExtra(WhereTrackingService.EXTRA_PAYLOAD);
            LocationDto locationDto = newLocationResultDto.getLocationDto();

            LocalDateTime locationDateTime = LocalDateTime.ofInstant(
                    locationDto.getTimestampUtc(), ZoneId.systemDefault());

            String modeStr = String.valueOf(locationDto.getSaveMode());
            CharSequence mode;
            if (locationDto.getSaveMode() == LocationDto.SaveMode.MANUAL) {
                mode = SpannableHelper.boldString(modeStr);
            } else {
                mode = modeStr;
            }

            CharSequence dateTimeLine = SpannableHelper.join("",
                    DateTimeHelper.date(locationDateTime),
                    " ",
                    SpannableHelper.boldString(DateTimeHelper.time(locationDateTime)),
                    ", ",
                    mode);

            CharSequence locationLine = SpannableHelper.join("",
                    String.valueOf(locationDto.getLatitude()),
                    ", ",
                    String.valueOf(locationDto.getLongitude()),
                    " ~ ",
                    SpannableHelper.coloredString(String.valueOf(locationDto.getAccuracy()), Color.BLUE));

            CharSequence uploadLine = SpannableHelper.join(" ",
                    "Upload:",
                    coloredDetail(newLocationResultDto.isUploadSuccess(), newLocationResultDto.getUploadMessage()));

            CharSequence saveLine = SpannableHelper.join(" ",
                    "Save:",
                    coloredDetail(newLocationResultDto.isSaveSuccess(), newLocationResultDto.getSaveMessage()));

            log(tag, dateTimeLine, locationLine, uploadLine, saveLine);
        }

        private CharSequence coloredDetail(boolean success, CharSequence message) {
            CharSequence detail;
            if (isDryRun()) {
                detail = SpannableHelper.coloredString(message, COLOR_ORANGE);
            } else if (!success) {
                detail = SpannableHelper.coloredString(message, Color.RED);
            } else {
                detail = message;
            }

            return detail;
        }
    }


    private class LocalLocationsResultReceiver extends BroadcastReceiver {

        @Override
        public void onReceive(Context context, Intent intent) {
            LocalLocationResultDto localLocationResultDto =
                    intent.getParcelableExtra(WhereTrackingService.EXTRA_PAYLOAD);

            CharSequence uploadLineDetail;
            if (localLocationResultDto.isUploadSuccess()) {
                uploadLineDetail = localLocationResultDto.getUploadMessage();
            } else {
                uploadLineDetail = SpannableHelper.coloredString(localLocationResultDto.getUploadMessage(), Color.RED);
            }
            CharSequence uploadLine = SpannableHelper.join(" ", "Upload:", uploadLineDetail);

            CharSequence markLineDetail;
            if (!localLocationResultDto.isUploadSuccess()) {
                markLineDetail = SpannableHelper.coloredString(localLocationResultDto.getMarkMessage(), COLOR_ORANGE);
            } else if (!localLocationResultDto.isMarkSuccess()) {
                markLineDetail = SpannableHelper.coloredString(localLocationResultDto.getMarkMessage(), Color.RED);
            } else {
                markLineDetail = localLocationResultDto.getMarkMessage();
            }
            CharSequence markLine = SpannableHelper.join(" ", "Mark:", markLineDetail);

            log("LOC." + localLocationResultDto.getOrdinal(), uploadLine, markLine);
        }
    }


    private class MessageBroadcastReceiver extends BroadcastReceiver {

        @Override
        public void onReceive(Context context, Intent intent) {
            CharSequence message = intent.getCharSequenceExtra(WhereTrackingService.EXTRA_PAYLOAD);
            log("SER", message);
        }
    }
}
