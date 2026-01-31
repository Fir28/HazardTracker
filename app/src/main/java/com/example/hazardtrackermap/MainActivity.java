package com.example.hazardtrackermap;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Address;
import android.location.Geocoder;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;

import java.util.List;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {

    private static final int LOCATION_PERMISSION_REQUEST = 100;
    private Button btnGoToMap, btnLogout;
    private TextView tvWelcome, tvLocation;
    private FusedLocationProviderClient fusedLocationClient;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Fade in the info card when the app opens
        View infoCard = findViewById(R.id.infoCard);
        infoCard.setAlpha(0f);
        infoCard.animate().alpha(1f).setDuration(800).start();

        androidx.appcompat.widget.Toolbar toolbar = findViewById(R.id.my_toolbar);
        setSupportActionBar(toolbar);

        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayShowTitleEnabled(false);
        }

        // 1. Initialize UI
        tvWelcome = findViewById(R.id.tvWelcome);
        tvLocation = findViewById(R.id.tvLocation);
        btnGoToMap = findViewById(R.id.btnGoToMap);
        btnLogout = findViewById(R.id.btnLogout);

        // 2. Get data passed from LoginActivity or SignupActivity
        String realName = getIntent().getStringExtra("REAL_NAME");
        if (realName != null) {
            tvWelcome.setText("Welcome, " + realName);
        }

        // 3. Setup Location Client
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);

        // 4. Button Listeners
        btnGoToMap.setOnClickListener(v ->
                startActivity(new Intent(MainActivity.this, MapsActivity.class)));

        btnLogout.setOnClickListener(v -> {
            // Go back to Login screen and clear the activity stack
            Intent intent = new Intent(MainActivity.this, LoginActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            finish();
        });

        checkLocationPermission();
    }

    // ===== LOCATION LOGIC =====
    private void checkLocationPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                    LOCATION_PERMISSION_REQUEST);
        } else {
            getLocation();
        }
    }

    private void getLocation() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) return;

        fusedLocationClient.getLastLocation().addOnSuccessListener(location -> {
            if (location != null) {
                double lat = location.getLatitude();
                double lon = location.getLongitude();

                // 1. Initialize Geocoder
                Geocoder geocoder = new Geocoder(MainActivity.this, Locale.getDefault());
                String locationName = "Unknown Location";

                try {
                    // 2. Get address from coordinates (1 is the max number of results)
                    List<Address> addresses = geocoder.getFromLocation(lat, lon, 1);

                    if (addresses != null && !addresses.isEmpty()) {
                        Address address = addresses.get(0);

                        // 3. Format the address (e.g., "City, Country" or "Street Name")
                        // You can use address.getLocality() for City or address.getAddressLine(0) for full address
                        locationName = address.getLocality() + ", " + address.getCountryName();
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                    locationName = "Lat: " + lat + ", Lon: " + lon; // Fallback to coordinates on error
                }

                // 4. Update the UI
                tvLocation.setText("📍 " + locationName);
                tvLocation.setVisibility(View.VISIBLE);
            }
        });
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {

        int id = item.getItemId();

        if (id == R.id.menuAbout) {
            //Toast.makeText(this, "About clicked", Toast.LENGTH_SHORT).show();
            //return true;

            Intent intent = new Intent(MainActivity.this, AboutActivity.class);
            startActivity(intent);
        }

        else if (id == R.id.action_share){
            String url = "https://github.com/Fir28/ZakatGoldCalc.git"; // your app website

            Intent shareIntent = new Intent(Intent.ACTION_SEND);
            shareIntent.setType("text/plain");
            shareIntent.putExtra(Intent.EXTRA_TEXT, "Check out this Zakat Gold Calculator App: " + url);

            startActivity(Intent.createChooser(shareIntent, "Share App"));

            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    // --- Menu creation ---
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.menu, menu);
        return true;
    }
}