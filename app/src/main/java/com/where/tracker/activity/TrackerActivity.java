package com.where.tracker.activity;


import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentSender;
import android.content.pm.PackageManager;
import android.location.Location;
import android.net.Uri;
import android.os.Bundle;
import android.os.PowerManager;
import android.provider.Settings;
import android.support.annotation.NonNull;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.NumberPicker;
import android.widget.Switch;
import android.widget.TextView;
import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.common.api.CommonStatusCodes;
import com.google.android.gms.common.api.ResolvableApiException;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.LocationSettingsRequest;
import com.google.android.gms.location.LocationSettingsResponse;
import com.google.android.gms.location.LocationSettingsStatusCodes;
import com.google.android.gms.location.SettingsClient;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.where.tracker.R;
import com.where.tracker.db.LocationDb;
import com.where.tracker.db.LocationDbSqlHelper;
import com.where.tracker.dto.LocationDto;
import com.where.tracker.dto.LocationListDto;
import com.where.tracker.gson.InstantSerializer;
import com.where.tracker.gson.ZoneIdSerializer;
import com.where.tracker.remote.AuthInterceptor;
import com.where.tracker.remote.WhereService;
import okhttp3.OkHttpClient;
import org.threeten.bp.Instant;
import org.threeten.bp.LocalDateTime;
import org.threeten.bp.ZoneId;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;


public class TrackerActivity extends Activity {

    private static final int LOCATION_AUTOMATIC_REQUEST_CODE = 1;

    private static final int LOCATION_MANUAL_REQUEST_CODE = 2;

    private static final int WHITELIST_REQUEST_CODE = 3;

    private static final int WRITE_REQUEST_CODE = 4;

    private static final int AUTOMATIC_INTERVAL = 15 * 60 * 1000; // record every 15 minutes

    private static final int BATCHING_INTERVAL = 60 * 60 * 1000; // batch processing by hour

    private WhereService whereService;

    private FusedLocationProviderClient fusedLocationClient;

    private LocationRequest locationRequest;

    private Switch dryRunSwitch;

    private TextView logView;

    private LocationCallback locationCallback;

    private LocationDb locationDb;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_tracker);

        OkHttpClient client = new OkHttpClient.Builder()
                .addInterceptor(new AuthInterceptor(getString(R.string.server_user), getString(R.string
                        .server_password)))
                .build();

        Gson gson = new GsonBuilder()
                .registerTypeAdapter(Instant.class, new InstantSerializer())
                .registerTypeAdapter(ZoneId.class, new ZoneIdSerializer())
                .create();

        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl(getString(R.string.server_url))
                .client(client)
                .addConverterFactory(GsonConverterFactory.create(gson))
                .build();
        whereService = retrofit.create(WhereService.class);

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);

        locationRequest = new LocationRequest();
        // actually, updates may be much more frequent if other apps require this
        // this is OK with us, but we want the frequency to be at least our setting
        locationRequest.setInterval(AUTOMATIC_INTERVAL);
        // may batch multiple locations, but may decide not to
        locationRequest.setMaxWaitTime(BATCHING_INTERVAL);
        locationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);

        dryRunSwitch = findViewById(R.id.dryRunSwitch);
        logView = findViewById(R.id.logView);

        locationCallback = new LocationCallback() {

            private int counter;

            @Override
            public void onLocationResult(LocationResult locationResult) {
                String tag = "#" + (++counter);
                List<Location> locations = locationResult.getLocations();
                LocalDateTime localDateTime = LocalDateTime.ofInstant(Instant.now(), ZoneId.systemDefault());
                log(tag, locations.size() + " locations at " + localDateTime);
                if (!locations.isEmpty()) {
                    processLocations(locations, tag, LocationDto.SaveMode.AUTOMATIC);
                }
            }
        };

        locationDb = new LocationDb(new LocationDbSqlHelper(this));

        log("DEF", "Activity created");
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        locationDb.close();
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

    public void changeDryRun(View view) {
        new AlertDialog.Builder(this)
                .setMessage("Are you sure you want to change this setting?")
                .setNegativeButton(android.R.string.no, new DialogInterface.OnClickListener() {

                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        // toggle back
                        dryRunSwitch.toggle();
                    }
                })
                .setPositiveButton(android.R.string.yes, null)
                .create()
                .show();
    }

    public void startAutomatic(View view) {
        safeLocationRequest(LOCATION_AUTOMATIC_REQUEST_CODE);
    }

    public void stopAutomatic(View view) {
        fusedLocationClient.removeLocationUpdates(locationCallback);
        log("DEF", "Automatic tracking stopped");
    }

    public void manualSave(View view) {
        safeLocationRequest(LOCATION_MANUAL_REQUEST_CODE);
    }

    public void uploadLocal(View view) {
        int i = 1;
        Map<Long, LocationDto> dtos = locationDb.getNotUploaded();
        log("LOC", "Count: " + dtos.size());
        // upload one by one, this is slower but will be triggered manually for now
        // and I actually want this granularity
        for (Map.Entry<Long, LocationDto> entry : dtos.entrySet()) {
            processLocalLocationDtos(Collections.singletonList(entry.getValue()), entry.getKey(), "LOC." + i);
            ++i;
        }
    }

    public void exportDb(View view) {
        exportDbWithPermission();
    }

    public void clearLog(View view) {
        logView.setText(null);
    }

    public void showRoute(View view) {
        LayoutInflater inflater = getLayoutInflater();
        View dialogView = inflater.inflate(R.layout.dialog_route, null);

        final NumberPicker fromDaysAgoPicker = dialogView.findViewById(R.id.fromDaysAgoPicker);
        final NumberPicker numberOfDaysPicker = dialogView.findViewById(R.id.numberOfDaysPicker);

        fromDaysAgoPicker.setMinValue(1);
        fromDaysAgoPicker.setMaxValue(365);
        fromDaysAgoPicker.setValue(1);

        numberOfDaysPicker.setMinValue(1);
        numberOfDaysPicker.setMaxValue(365);
        numberOfDaysPicker.setValue(1);

        new AlertDialog.Builder(this)
                .setTitle("How many days?")
                .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {

                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        ArrayList<LocationDto> locationDtos = locationDb.getDays(
                                fromDaysAgoPicker.getValue(), numberOfDaysPicker.getValue());

                        if (locationDtos.isEmpty()) {
                            log("DEF", "No locations available");
                            return;
                        }

                        Intent intent = new Intent(TrackerActivity.this, RouteActivity.class);
                        intent.putParcelableArrayListExtra(RouteActivity.LOCATIONS_EXTRA, locationDtos);
                        startActivity(intent);
                    }
                })
                .setNegativeButton(android.R.string.cancel, new DialogInterface.OnClickListener() {

                    @Override
                    public void onClick(DialogInterface dialog, int which) {

                    }
                })
                .setView(dialogView)
                .create()
                .show();
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

    private void assertInPowerWhitelist() {
        PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        String packageName = getPackageName();
        if (pm.isIgnoringBatteryOptimizations(packageName)) {
            log("BAT", "App already whitelisted");
            startAutomatic();
        } else {
            Intent intent = new Intent();
            // this is not a Google Play candidate, ignore the warning
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

    private void logTrackingUnavailable(String tag, String detail) {
        String message = "Location tracking is unavailable";

        if (detail != null) {
            message += ": " + detail;
        }

        log(tag, message);
    }

    private void startAutomatic() {
        fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, null);
    }

    private void manualSave() {
        fusedLocationClient.getLastLocation()
                .addOnSuccessListener(TrackerActivity.this, new OnSuccessListener<Location>() {

                    @Override
                    public void onSuccess(Location location) {
                        if (location != null) {
                            log("MAN", "Save succeeded");
                            processLocations(
                                    Collections.singletonList(location), "MAN", LocationDto.SaveMode.MANUAL);
                        }
                    }
                })
                .addOnFailureListener(TrackerActivity.this, new OnFailureListener() {

                    @Override
                    public void onFailure(@NonNull Exception e) {
                        log("MAN", "Save failed: " + e.toString());
                    }
                });
    }

    private void exportDb() {
        try {
            File outputFile = locationDb.exportDb(this);
            log("DEF", "Database exported to " + outputFile);
        } catch (IOException exc) {
            log("DEF", "Copying db failed: " + exc);
        }
    }

    private void processLocations(List<Location> locations, String tag, LocationDto.SaveMode saveMode) {
        List<LocationDto> dtos = new ArrayList<>(locations.size());

        int i = 1;
        for (Location location : locations) {
            LocationDto dto = new LocationDto();
            dto.setTimestampUtc(Instant.ofEpochMilli(location.getTime()));
            dto.setTimeZone(ZoneId.systemDefault());
            dto.setLatitude(location.getLatitude());
            dto.setLongitude(location.getLongitude());
            if (location.hasAccuracy()) {
                dto.setAccuracy((double) location.getAccuracy());
            }
            dto.setSaveMode(saveMode);
            logLocationDto(dto, tag + "." + i);
            dtos.add(dto);
            ++i;
        }

        processNewLocationDtos(dtos, tag);
    }

    private void processNewLocationDtos(List<LocationDto> dtos, final String tag) {
        if (!dryRunSwitch.isChecked()) {
            log("DRY." + tag, "Not processing locations");
            return;
        }

        final LocationListDto listDto = new LocationListDto();
        listDto.setLocations(dtos);

        whereService.saveLocations(listDto).enqueue(new Callback<Void>() {

            @Override
            public void onResponse(Call<Void> call, Response<Void> response) {
                log("NET " + tag, "Upload HTTP code: " + response.code());
                saveLocally(response.isSuccessful(), "LOC " + tag);
            }

            @Override
            public void onFailure(Call<Void> call, Throwable t) {
                log("NET " + tag, "Upload failed: " + t.toString());
                saveLocally(false, "LOC " + tag);
            }

            private void saveLocally(boolean uploaded, String tag) {
                int i = 1;
                for (LocationDto dto : listDto.getLocations()) {
                    try {
                        locationDb.insert(dto, uploaded);
                        log(tag + "." + i, "Save succeeded");
                    } catch (Throwable t) {
                        log(tag + "." + i, "Save failed: " + t);
                    }
                    ++i;
                }
            }
        });
    }

    private void processLocalLocationDtos(List<LocationDto> dtos, final long id, final String tag) {
        if (!dryRunSwitch.isChecked()) {
            log("DRY." + tag, "Not processing local locations");
            return;
        }

        final LocationListDto listDto = new LocationListDto();
        listDto.setLocations(dtos);

        whereService.saveLocations(listDto).enqueue(new Callback<Void>() {

            @Override
            public void onResponse(Call<Void> call, Response<Void> response) {
                log("NET " + tag, "Upload HTTP code: " + response.code());
                if (response.isSuccessful()) {
                    markUploaded(id);
                } else {
                    log(tag, "Not marking uploaded");
                }
            }

            @Override
            public void onFailure(Call<Void> call, Throwable t) {
                log("NET " + tag, "Upload failed: " + t.toString());
                log(tag, "Not marking uploaded");
            }

            private void markUploaded(long id) {
                try {
                    int rowCount = locationDb.markUploaded(id);
                    log(tag, "Marked uploaded (row count: " + rowCount + ")");
                } catch (Throwable t) {
                    log(tag, "Marking uploaded failed: " + t);
                }
            }
        });
    }

    private void logLocationDto(LocationDto locationDto, String tag) {
        LocalDateTime localDateTime = LocalDateTime.ofInstant(locationDto.getTimestampUtc(), ZoneId.systemDefault());
        log(tag, localDateTime + ", "
                + locationDto.getLatitude() + ", " + locationDto.getLongitude()
                + ", " + locationDto.getAccuracy());
    }

    private void log(String tag, CharSequence entry) {
        if (tag != null) {
            entry = "[" + tag + "] " + entry;
        }
        logView.setText(entry + "\n" + logView.getText());
    }
}
