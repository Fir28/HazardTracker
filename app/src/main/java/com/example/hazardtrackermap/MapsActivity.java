package com.example.hazardtrackermap;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.fragment.app.FragmentActivity;

import android.Manifest;
import android.app.AlertDialog;
import android.content.pm.PackageManager;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;

import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptor;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MarkerOptions;
import com.example.hazardtrackermap.databinding.ActivityMapsBinding;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class MapsActivity extends FragmentActivity implements OnMapReadyCallback {

    private GoogleMap mMap;
    private ActivityMapsBinding binding;
    // URL for your local server (using 10.0.2.2 for Android Emulator)
    private final String BASE_URL = "http://10.0.2.2/serverside/";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityMapsBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.map);
        if (mapFragment != null) {
            mapFragment.getMapAsync(this);
        }
    }

    @Override
    public void onMapReady(GoogleMap googleMap) {
        mMap = googleMap;

        // Check Permissions for GPS
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, 100);
            return;
        }

        enableUserLocation();

        // 1. Requirement: Crowdsourcing (Long Click to add markers)
        mMap.setOnMapLongClickListener(new GoogleMap.OnMapLongClickListener() {
            @Override
            public void onMapLongClick(@NonNull LatLng latLng) {
                showReportDialog(latLng);
            }
        });

        // 2. Initial Map View (Malaysia)
        LatLng malaysia = new LatLng(4.2105, 101.9758);
        mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(malaysia, 6));

        // 3. Requirement: Markers MUST BE downloadable from server
        loadHazardsFromServer();
    }

    private void enableUserLocation() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            mMap.setMyLocationEnabled(true);
            mMap.getUiSettings().setMyLocationButtonEnabled(true);

            // Detect blue-dot click to show info
            mMap.setOnMyLocationClickListener(location -> {
                String placeName = getLocationName(location);
                Toast.makeText(this, "Current Location:\n" + placeName, Toast.LENGTH_LONG).show();
            });
        }
    }

    // --- CROWDSOURCING LOGIC ---

    private void showReportDialog(LatLng latLng) {
        final String[] hazardTypes = {"Flood", "Landslide", "Road_Closure", "Other"};

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Report Hazard at this spot?");
        builder.setItems(hazardTypes, (dialog, which) -> {
            String selectedType = hazardTypes[which];
            Location tempLoc = new Location("");
            tempLoc.setLatitude(latLng.latitude);
            tempLoc.setLongitude(latLng.longitude);
            String locationName = getLocationName(tempLoc);

            sendHazardToServer(locationName, latLng.latitude, latLng.longitude, selectedType);
        });
        builder.show();
    }

    private void sendHazardToServer(String locName, double lat, double lon, String type) {
        // 1. READ the saved name from SharedPreferences
        android.content.SharedPreferences prefs = getSharedPreferences("UserPrefs", MODE_PRIVATE);
        // If for some reason no name is found, it will use "Anonymous"
        String loggedInUser = prefs.getString("username", "Anonymous");

        new Thread(() -> {
            try {
                URL url = new URL(BASE_URL + "add_hazard.php");
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setDoOutput(true);

                String rawAgent = "Android " + Build.VERSION.RELEASE + " " + Build.MODEL;
                String userAgent = rawAgent.length() > 100 ? rawAgent.substring(0, 100) : rawAgent;
                conn.setRequestProperty("User-Agent", userAgent);

                // 2. Use the 'loggedInUser' variable here
                String postData = "location_name=" + URLEncoder.encode(locName, "UTF-8") +
                        "&latitude=" + lat +
                        "&longitude=" + lon +
                        "&hazard_type=" + URLEncoder.encode(type.toLowerCase(), "UTF-8") +
                        "&reporter_name=" + URLEncoder.encode(loggedInUser, "UTF-8");

                OutputStream os = conn.getOutputStream();
                os.write(postData.getBytes());
                os.flush();
                os.close();

                if (conn.getResponseCode() == HttpURLConnection.HTTP_OK) {
                    runOnUiThread(() -> {
                        Toast.makeText(this, "Hazard Reported by " + loggedInUser, Toast.LENGTH_SHORT).show();
                        loadHazardsFromServer();
                    });
                }
            } catch (Exception e) {
                Log.e("MAP_ERROR", "Upload failed", e);
            }
        }).start();
    }

    // --- DOWNLOADABLE MARKERS LOGIC ---

    private void loadHazardsFromServer() {
        new Thread(() -> {
            try {
                URL url = new URL(BASE_URL + "api.php");
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");

                BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                StringBuilder json = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) json.append(line);
                reader.close();

                JSONArray hazards = new JSONArray(json.toString());
                runOnUiThread(() -> addMarkersToMap(hazards));
            } catch (Exception e) {
                Log.e("MAP_ERROR", "Download failed", e);
            }
        }).start();
    }

    private void addMarkersToMap(JSONArray hazards) {
        mMap.clear(); // Clear old markers before adding new ones
        for (int i = 0; i < hazards.length(); i++) {
            try {
                JSONObject h = hazards.getJSONObject(i);
                LatLng pos = new LatLng(h.getDouble("latitude"), h.getDouble("longitude"));
                String type = h.getString("hazard_type");

                mMap.addMarker(new MarkerOptions()
                        .position(pos)
                        .title(h.getString("location_name"))
                        .snippet("Type: " + type)
                        .icon(getMarkerColor(type)));
            } catch (Exception ignored) {}
        }
    }

    private BitmapDescriptor getMarkerColor(String type) {
        if (type == null) return BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_YELLOW);

        switch (type.toLowerCase().trim()) {
            case "flood":
                return BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_BLUE);
            case "landslide":
                return BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_ORANGE);
            case "road_closure": // Removed underscore to match your dialog
                return BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED);
            case "other":
                return BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_YELLOW);
            default:
                return BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_VIOLET);
        }
    }

    // --- HELPERS ---

    private String getLocationName(Location location) {
        try {
            Geocoder geocoder = new Geocoder(this, Locale.getDefault());
            List<Address> addresses = geocoder.getFromLocation(location.getLatitude(), location.getLongitude(), 1);
            if (addresses != null && !addresses.isEmpty()) {
                return addresses.get(0).getAddressLine(0);
            }
        } catch (Exception e) {
            Log.e("GEOCODER", "Error", e);
        }
        return "Unknown Location";
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 100 && grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            enableUserLocation();
        }
    }
}