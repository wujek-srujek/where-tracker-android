package com.where.tracker.activity;


import java.util.List;
import java.util.concurrent.TimeUnit;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.support.v4.app.FragmentActivity;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.JointType;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLngBounds;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.PolylineOptions;
import com.google.android.gms.maps.model.RoundCap;
import com.where.tracker.R;
import com.where.tracker.db.LocationDb;
import com.where.tracker.dto.LocationDto;
import org.threeten.bp.Duration;
import org.threeten.bp.Instant;


public class RouteActivity extends FragmentActivity implements OnMapReadyCallback {

    private static final String PKG = RouteActivity.class.getPackage().getName() + ".";

    public static final String EXTRA_FROM_DAYS_AGO = PKG + "from_days_ago";

    public static final String EXTRA_NUMBER_OF_DAYS = PKG + "number_of_days";

    public static final String EXTRA_CONTINUOUS = PKG + "continuous";

    private static final int[] COLORS = {
            Color.RED, Color.GREEN, Color.BLUE,
            Color.CYAN, Color.MAGENTA, Color.YELLOW,
            Color.LTGRAY, Color.GRAY, Color.BLACK
    };

    private static final long HOUR_IN_SECONDS = TimeUnit.HOURS.toSeconds(1);

    private LocationDb locationDb;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_route);
        ((SupportMapFragment) getSupportFragmentManager().findFragmentById(R.id.map)).getMapAsync(this);

        locationDb = LocationDb.get();
    }

    @Override
    public void onMapReady(final GoogleMap googleMap) {
        googleMap.setOnMapLoadedCallback(new GoogleMap.OnMapLoadedCallback() {

            @Override
            public void onMapLoaded() {
                showRoute(googleMap);
            }
        });
    }

    private void showRoute(GoogleMap googleMap) {
        Intent startIntent = getIntent();
        int fromDaysAgo = startIntent.getIntExtra(EXTRA_FROM_DAYS_AGO, 1);
        int numberOfDays = startIntent.getIntExtra(EXTRA_NUMBER_OF_DAYS, 1);
        boolean continuous = startIntent.getBooleanExtra(EXTRA_CONTINUOUS, false);

        List<LocationDto> locationDtos = locationDb.getDays(fromDaysAgo, numberOfDays);

        if (locationDtos.isEmpty()) {
            new AlertDialog.Builder(this)
                    .setCancelable(false)
                    .setMessage("No locations available for the specified period")
                    .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {

                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            finish();
                        }
                    })
                    .create()
                    .show();
            return;
        }

        int routeNo = 0;
        LatLngBounds.Builder boundsBuilder = new LatLngBounds.Builder();
        LatLng currentLocation = null;

        Instant lastTimestampUtc = null;
        PolylineOptions options = null;

        for (LocationDto locationDto : locationDtos) {
            Instant timestampUtc = locationDto.getTimestampUtc();
            if (lastTimestampUtc == null
                    || Duration.between(lastTimestampUtc, timestampUtc).getSeconds() > HOUR_IN_SECONDS && !continuous) {
                // new route, finish and add polyline, start a new one
                if (options != null && options.getPoints().size() > 1) {
                    googleMap.addPolyline(options);
                    ++routeNo;
                }

                options = new PolylineOptions()
                        .startCap(new RoundCap())
                        .endCap(new RoundCap())
                        .width(15)
                        .color(COLORS[routeNo % COLORS.length])
                        .jointType(JointType.ROUND);
            }

            LatLng coordinates = new LatLng(locationDto.getLatitude(), locationDto.getLongitude());
            options.add(coordinates);
            boundsBuilder.include(coordinates);
            currentLocation = coordinates;

            lastTimestampUtc = timestampUtc;
        }

        googleMap.addPolyline(options);

        googleMap.addMarker(new MarkerOptions().position(currentLocation));

        googleMap.animateCamera(CameraUpdateFactory.newLatLngBounds(boundsBuilder.build(), 50));
    }
}
