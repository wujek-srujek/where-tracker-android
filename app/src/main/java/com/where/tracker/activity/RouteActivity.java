package com.where.tracker.activity;


import java.util.List;
import java.util.concurrent.TimeUnit;

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
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.PolylineOptions;
import com.google.android.gms.maps.model.RoundCap;
import com.where.tracker.R;
import com.where.tracker.dto.LocationDto;
import org.threeten.bp.Duration;
import org.threeten.bp.Instant;


public class RouteActivity extends FragmentActivity implements OnMapReadyCallback {

    public static final String LOCATIONS_EXTRA = "LOCATIONS";

    private static final int[] COLORS = { Color.RED, Color.GREEN, Color.BLUE, Color.YELLOW, Color.BLACK };

    private static final long HOUR_IN_SECONDS = TimeUnit.HOURS.toSeconds(1);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_route);
        ((SupportMapFragment) getSupportFragmentManager().findFragmentById(R.id.map)).getMapAsync(this);
    }

    @Override
    public void onMapReady(final GoogleMap googleMap) {
        googleMap.setOnMapLoadedCallback(new GoogleMap.OnMapLoadedCallback() {

            @Override
            public void onMapLoaded() {
                List<LocationDto> locationDtos = getIntent().getParcelableArrayListExtra(LOCATIONS_EXTRA);

                int routeNo = 0;
                LatLngBounds.Builder boundsBuilder = new LatLngBounds.Builder();
                LatLng currentLocation = null;

                Instant lastTimestampUtc = null;
                PolylineOptions options = null;

                for (LocationDto locationDto : locationDtos) {
                    Instant timestampUtc = locationDto.getTimestampUtc();
                    if (lastTimestampUtc == null
                            || Duration.between(lastTimestampUtc, timestampUtc).getSeconds() > HOUR_IN_SECONDS) {
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
        });
    }
}
