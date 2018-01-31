package com.where.tracker.service;


import java.util.Collections;
import java.util.List;
import java.util.Map;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.location.Location;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.support.annotation.NonNull;
import android.support.v4.content.LocalBroadcastManager;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.where.tracker.R;
import com.where.tracker.activity.TrackerActivity;
import com.where.tracker.db.LocationDb;
import com.where.tracker.dto.LocalLocationResultDto;
import com.where.tracker.dto.LocationDto;
import com.where.tracker.dto.LocationListDto;
import com.where.tracker.dto.NewLocationResultDto;
import com.where.tracker.gson.InstantSerializer;
import com.where.tracker.gson.ZoneIdSerializer;
import com.where.tracker.helper.DateTimeHelper;
import com.where.tracker.helper.SpannableHelper;
import com.where.tracker.remote.AuthInterceptor;
import com.where.tracker.remote.WhereService;
import okhttp3.OkHttpClient;
import org.threeten.bp.Duration;
import org.threeten.bp.Instant;
import org.threeten.bp.LocalDateTime;
import org.threeten.bp.ZoneId;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;


public class WhereTrackingService extends Service {

    private static final String PKG = WhereTrackingService.class.getPackage().getName() + ".";

    public static final String COMMAND_START_AUTOMATIC = PKG + "start";

    public static final String COMMAND_STOP_AUTOMATIC = PKG + "stop";

    public static final String COMMAND_MANUAL = PKG + "manual";

    public static final String COMMAND_UPLOAD_LOCAL = PKG + "upload_local";

    public static final String COMMAND_SET_DRY_RUN = PKG + "toggle_dry_run";

    public static final String EXTRA_START_AUTOMATIC_LOCATION_REQUEST = PKG + "location_request";

    public static final String BROADCAST_NEW_LOCATION_RESULT = PKG + "new_location_result";

    public static final String BROADCAST_LOCAL_LOCATION_RESULT = PKG + "local_location_result";

    public static final String BROADCAST_NEW_MESSAGE = PKG + "new_message";

    public static final String EXTRA_PAYLOAD = PKG + "payload";

    private static final String CHANNEL_ID = "channel";

    private static final int NOTIFICATION_ID = 17;

    private NotificationManager notificationManager;

    private WhereService whereService;

    private FusedLocationProviderClient fusedLocationClient;

    private LocationCallback locationCallback;

    private LocationDb locationDb;

    private LocalBroadcastManager broadcastManager;

    private LocationRequest locationRequest;

    private boolean dryRun;

    private boolean isAutomatic;

    @Override
    public void onCreate() {
        notificationManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID, "channel name", NotificationManager.IMPORTANCE_LOW);
            notificationManager.createNotificationChannel(channel);
        }

        OkHttpClient client = new OkHttpClient.Builder()
                .addInterceptor(new AuthInterceptor(
                        getString(R.string.server_user),
                        getString(R.string.server_password)))
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

        locationCallback = new LocationProcessor();

        locationDb = LocationDb.get();

        broadcastManager = LocalBroadcastManager.getInstance(this);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        // this service accepts various commands using this method
        // seems to be the way to send commands to a service from its own notifications
        // so let's extend it and use it for anything, which allows us to not bind
        String command = intent.getAction();

        if (COMMAND_START_AUTOMATIC.equals(command)) {
            locationRequest = intent.getParcelableExtra(EXTRA_START_AUTOMATIC_LOCATION_REQUEST);
            if (locationRequest == null) {
                throw new IllegalStateException("missing " + EXTRA_START_AUTOMATIC_LOCATION_REQUEST + " extra");
            }
            applyDryRun(intent);

            startAutomatic();
        } else if (COMMAND_STOP_AUTOMATIC.equals(command)) {
            stopAutomatic();
        } else if (COMMAND_MANUAL.equals(command)) {
            applyDryRun(intent);

            manualSave();
        } else if (COMMAND_UPLOAD_LOCAL.equals(command)) {
            uploadLocal();
        } else if (COMMAND_SET_DRY_RUN.equals(command)) {
            applyDryRun(intent);

            if (!isAutomatic) {
                stopSelf();
            }

        } else {
            throw new IllegalArgumentException("unknown command " + command);
        }

        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void startAutomatic() {
        if (isAutomatic) {
            broadcastMessage("Automatic tracking already running, doing nothing");
            return;
        }

        isAutomatic = true;
        startForeground(NOTIFICATION_ID, createNotification(null));
        fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, null);
        broadcastMessage("Automatic tracking started");
    }

    private void stopAutomatic() {
        if (isAutomatic) {
            fusedLocationClient.removeLocationUpdates(locationCallback);
            broadcastMessage("Automatic tracking stopped");
        } else {
            broadcastMessage("Automatic tracking not running, doing nothing");
        }

        stopSelf();
    }

    private void manualSave() {
        // not starting in foreground, manual save is only available when the activity is visible/foreground
        // also, this is a one-off event so there is no need for a notification
        fusedLocationClient.getLastLocation()
                .addOnSuccessListener(new OnSuccessListener<Location>() {

                    @Override
                    public void onSuccess(Location location) {
                        if (location != null) {
                            processNewLocations(Collections.singletonList(location), LocationDto.SaveMode.MANUAL);
                        }
                        if (!isAutomatic) {
                            // technically, due to threading this might stop self before retrofit
                            // callback runs; seems to be OK now, but observe; if fails, this will
                            // have to be moved to the callback
                            stopSelf();
                        }
                    }
                })
                .addOnFailureListener(new OnFailureListener() {

                    @Override
                    public void onFailure(@NonNull Exception e) {
                        if (!isAutomatic) {
                            stopSelf();
                        }
                    }
                });
    }

    private void processNewLocations(List<Location> locations, LocationDto.SaveMode saveMode) {
        // use the latest received location for foreground service - we do not batch so should only ever
        // get a single one anyway
        Location location = locations.get(locations.size() - 1);

        // if manual, update the notification only if the service is in foreground (already has notification)
        // the activity will show the information even if no notification is used
        if (isAutomatic) {
            notificationManager.notify(NOTIFICATION_ID, createNotification(location));
        }

        LocationDto locationDto = new LocationDto();
        locationDto.setTimestampUtc(Instant.ofEpochMilli(location.getTime()));
        locationDto.setTimeZone(ZoneId.systemDefault());
        locationDto.setLatitude(location.getLatitude());
        locationDto.setLongitude(location.getLongitude());
        if (location.hasAccuracy()) {
            locationDto.setAccuracy((double) location.getAccuracy());
        }
        locationDto.setSaveMode(saveMode);

        if (dryRun) {
            // dry run, create a dry run result
            NewLocationResultDto newLocationResultDto = new NewLocationResultDto();
            newLocationResultDto.setUploadSuccess(true);
            newLocationResultDto.setUploadMessage("not uploaded (dry run)");
            newLocationResultDto.setSaveSuccess(true);
            newLocationResultDto.setSaveMessage("not saved (dry run)");
            newLocationResultDto.setLocationDto(locationDto);

            broadcastNewLocationResult(newLocationResultDto);
        } else {
            LocationListDto listDto = new LocationListDto();
            listDto.setLocations(Collections.singletonList(locationDto));

            whereService.saveLocations(listDto).enqueue(new NewLocationCallback(locationDto));
        }
    }

    private void uploadLocal() {
        uploadIfNeeded();

        if (!isAutomatic) {
            stopSelf();
        }
    }

    private void uploadIfNeeded() {
        Map<Long, LocationDto> notUploaded = locationDb.getNotUploaded();
        if (notUploaded.isEmpty()) {
            broadcastMessage("Nothing to upload");
            return;
        }

        broadcastMessage("To upload: " + notUploaded.size());

        // upload one by one, this is slower than bulk but will be triggered manually for now
        // and I actually want this granularity to easily be able to mark single locations
        int ordinal = 0;
        for (Map.Entry<Long, LocationDto> entry : locationDb.getNotUploaded().entrySet()) {
            ++ordinal;
            LocationDto locationDto = entry.getValue();
            List<LocationDto> locationDtos = Collections.singletonList(locationDto);
            LocationListDto listDto = new LocationListDto();
            listDto.setLocations(locationDtos);

            whereService.saveLocations(listDto).enqueue(new LocalLocationCallback(entry.getKey(), ordinal));
        }
        broadcastMessage("Local locations upload triggered");
    }

    private Notification createNotification(Location location) {
        Intent activityIntent = new Intent(this, TrackerActivity.class);
        activityIntent.setAction("android.intent.action.MAIN");
        activityIntent.addCategory("android.intent.category.LAUNCHER");
        PendingIntent activityPendingIntent = PendingIntent.getActivity(this, 0, activityIntent, 0);

        Notification.Builder builder = new Notification.Builder(this)
                .setOngoing(true)
                .setSmallIcon(R.drawable.ic_stat_tracking)
                .setContentIntent(activityPendingIntent);

        if (location == null) {
            builder = builder.setContentText("No location yet");
        } else {
            LocalDateTime lastLocationDateTime = LocalDateTime.ofInstant(
                    Instant.ofEpochMilli(location.getTime()),
                    ZoneId.systemDefault());
            builder = builder
                    .setContentTitle("Last location info")
                    .setContentText(SpannableHelper.join(" ",
                            DateTimeHelper.date(lastLocationDateTime),
                            SpannableHelper.boldString(DateTimeHelper.time(lastLocationDateTime)),
                            "~",
                            String.valueOf(location.getAccuracy())));
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            builder = builder.setChannelId(CHANNEL_ID);
        }

        return builder.build();
    }

    private void applyDryRun(Intent intent) {
        if (!intent.hasExtra(EXTRA_PAYLOAD)) {
            throw new IllegalStateException("missing " + EXTRA_PAYLOAD + " extra");
        }

        dryRun = intent.getBooleanExtra(EXTRA_PAYLOAD, false);
        broadcastMessage("Using dry run: " + dryRun);
    }

    private void broadcastNewLocationResult(NewLocationResultDto newLocationResultDto) {
        Intent broadcastIntent = new Intent(BROADCAST_NEW_LOCATION_RESULT);
        broadcastIntent.putExtra(EXTRA_PAYLOAD, newLocationResultDto);
        broadcastManager.sendBroadcast(broadcastIntent);
    }

    private void broadcastLocalLocationResult(LocalLocationResultDto localLocationResultDto) {
        Intent broadcastIntent = new Intent(BROADCAST_LOCAL_LOCATION_RESULT);
        broadcastIntent.putExtra(EXTRA_PAYLOAD, localLocationResultDto);
        broadcastManager.sendBroadcast(broadcastIntent);
    }

    private void broadcastMessage(CharSequence message) {
        Intent broadcastIntent = new Intent(BROADCAST_NEW_MESSAGE);
        Bundle extras = new Bundle();
        extras.putCharSequence(EXTRA_PAYLOAD, message);
        broadcastIntent.putExtras(extras);
        broadcastManager.sendBroadcast(broadcastIntent);
    }


    private class LocationProcessor extends LocationCallback {

        private Instant lastUpdate;

        @Override
        public void onLocationResult(LocationResult locationResult) {
            Instant now = Instant.now();
            if (lastUpdate == null
                    || Duration.between(lastUpdate, now).getSeconds() > TrackerActivity.AUTOMATIC_INTERVAL_FILTER) {
                List<Location> locations = locationResult.getLocations();
                if (!locations.isEmpty()) {
                    lastUpdate = now;
                    processNewLocations(locations, LocationDto.SaveMode.AUTOMATIC);
                }
            }
        }
    }


    private class NewLocationCallback implements Callback<Void> {

        private final LocationDto locationDto;

        private final NewLocationResultDto newLocationResultDto;

        private NewLocationCallback(LocationDto locationDto) {
            this.locationDto = locationDto;
            newLocationResultDto = new NewLocationResultDto();
        }

        @Override
        public void onResponse(Call<Void> call, Response<Void> response) {
            process(response.isSuccessful(), "HTTP code: " + response.code());
        }

        @Override
        public void onFailure(Call<Void> call, Throwable t) {
            process(false, "failed: " + t);
        }

        private void process(boolean uploadSuccess, CharSequence uploadMessage) {
            newLocationResultDto.setUploadSuccess(uploadSuccess);
            newLocationResultDto.setUploadMessage(uploadMessage);
            try {
                locationDb.insert(locationDto, uploadSuccess);
                newLocationResultDto.setSaveSuccess(true);
                newLocationResultDto.setSaveMessage("OK");
            } catch (Throwable t) {
                newLocationResultDto.setSaveSuccess(false);
                newLocationResultDto.setSaveMessage("failed: " + t);
            }
            newLocationResultDto.setLocationDto(locationDto);

            broadcastNewLocationResult(newLocationResultDto);
        }
    }


    private class LocalLocationCallback implements Callback<Void> {

        private final long locationId;

        private final int ordinal;

        private final LocalLocationResultDto localLocationResultDto;

        private LocalLocationCallback(long locationId, int ordinal) {
            this.locationId = locationId;
            this.ordinal = ordinal;
            localLocationResultDto = new LocalLocationResultDto();
        }

        @Override
        public void onResponse(Call<Void> call, Response<Void> response) {
            process("HTTP code: " + response.code(), response.isSuccessful());
        }

        @Override
        public void onFailure(Call<Void> call, Throwable t) {
            process("failed: " + t, false);
        }

        private void process(CharSequence uploadMessage, boolean uploaded) {
            localLocationResultDto.setOrdinal(ordinal);
            localLocationResultDto.setUploadMessage(uploadMessage);
            if (uploaded) {
                localLocationResultDto.setUploadSuccess(true);
                try {
                    locationDb.markUploaded(locationId);
                    localLocationResultDto.setMarkSuccess(true);
                    localLocationResultDto.setMarkMessage("OK");
                } catch (Throwable t) {
                    localLocationResultDto.setMarkSuccess(false);
                    localLocationResultDto.setMarkMessage("failed: " + t);
                }
            } else {
                localLocationResultDto.setUploadSuccess(false);
                localLocationResultDto.setMarkSuccess(false);
                localLocationResultDto.setMarkMessage("not marked as uploaded");
            }

            broadcastLocalLocationResult(localLocationResultDto);
        }
    }
}
