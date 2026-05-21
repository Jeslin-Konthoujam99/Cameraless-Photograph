package com.example.cameralessphotography;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.request.target.CustomTarget;
import com.bumptech.glide.request.transition.Transition;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * NEW: Flexible search activity — search photos from anywhere without GPS.
 * Step-by-step: Location → Weather → Angle → Lighting → Results
 */
public class SearchActivity extends Activity {

    private static final String TAG = "SearchActivity";

    // UI - Location
    private EditText locationET;
    private TextView locationStatusTV;

    // UI - Dropdowns
    private Spinner weatherSpinner, directionSpinner, elevationSpinner;
    private Spinner timeSpinner, seasonSpinner;

    // UI - Results
    private Button searchBTN, backBTN;
    private ImageView photo1IV, photo2IV, photo3IV;
    private TextView resultTV;
    private ProgressBar searchPB;
    private LinearLayout resultsLayout;

    // Data
    private DatabaseHelper databaseHelper;
    private List<Map<String, Object>> searchResults;
    private Bundle bd;
    private Map<String, String> cameraSettings;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_search);
        init();
    }

    private void init() {
        databaseHelper = new DatabaseHelper();

        // Location input
        locationET = findViewById(R.id.searchLocationEditText);
        locationStatusTV = findViewById(R.id.locationStatusTextView);

        // Dropdowns
        weatherSpinner = findViewById(R.id.weatherSpinner);
        directionSpinner = findViewById(R.id.directionSpinner);
        elevationSpinner = findViewById(R.id.elevationSpinner);
        timeSpinner = findViewById(R.id.timeSpinner);
        seasonSpinner = findViewById(R.id.seasonSpinner);

        // Results
        searchBTN = findViewById(R.id.searchButton);
        backBTN = findViewById(R.id.backButton);
        photo1IV = findViewById(R.id.searchPhoto1);
        photo2IV = findViewById(R.id.searchPhoto2);
        photo3IV = findViewById(R.id.searchPhoto3);
        resultTV = findViewById(R.id.searchResultTextView);
        searchPB = findViewById(R.id.searchProgressBar);
        resultsLayout = findViewById(R.id.resultsLayout);

        // Setup spinners
        setupSpinners();

        // Camera settings from intent
        bd = getIntent().getExtras();
        if (bd == null) bd = new Bundle();
        initCameraSettings();

        // Location text watcher
        locationET.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (s.length() > 0) {
                    locationStatusTV.setText("Will search for: " + s.toString());
                } else {
                    locationStatusTV.setText("Enter a city, district, or landmark name");
                }
            }
            @Override
            public void afterTextChanged(Editable s) {}
        });

        // Click listeners
        searchBTN.setOnClickListener(v -> performSearch());

        backBTN.setOnClickListener(v -> {
            Intent intent = new Intent(SearchActivity.this, MainActivity.class);
            for (String key : cameraSettings.keySet()) {
                bd.putString(key, cameraSettings.get(key));
            }
            intent.putExtras(bd);
            startActivity(intent);
            finish();
        });

        // Photo click listeners — tap for full view
        photo1IV.setOnClickListener(v -> showPhotoDetail(0));
        photo2IV.setOnClickListener(v -> showPhotoDetail(1));
        photo3IV.setOnClickListener(v -> showPhotoDetail(2));
    }

    private void setupSpinners() {
        // Weather dropdown
        ArrayAdapter<CharSequence> weatherAdapter = ArrayAdapter.createFromResource(
                this, R.array.searchWeatherArray, android.R.layout.simple_spinner_dropdown_item);
        weatherSpinner.setAdapter(weatherAdapter);

        // Direction dropdown (yaw)
        ArrayAdapter<CharSequence> dirAdapter = ArrayAdapter.createFromResource(
                this, R.array.searchDirectionArray, android.R.layout.simple_spinner_dropdown_item);
        directionSpinner.setAdapter(dirAdapter);

        // Elevation dropdown (pitch)
        ArrayAdapter<CharSequence> elevAdapter = ArrayAdapter.createFromResource(
                this, R.array.searchElevationArray, android.R.layout.simple_spinner_dropdown_item);
        elevationSpinner.setAdapter(elevAdapter);

        // Time of day dropdown
        ArrayAdapter<CharSequence> timeAdapter = ArrayAdapter.createFromResource(
                this, R.array.searchTimeArray, android.R.layout.simple_spinner_dropdown_item);
        timeSpinner.setAdapter(timeAdapter);

        // Season dropdown
        ArrayAdapter<CharSequence> seasonAdapter = ArrayAdapter.createFromResource(
                this, R.array.searchSeasonArray, android.R.layout.simple_spinner_dropdown_item);
        seasonSpinner.setAdapter(seasonAdapter);
    }

    private void performSearch() {
        String locationText = locationET.getText().toString().trim();
        if (locationText.isEmpty()) {
            Toast.makeText(this, "Please enter a location", Toast.LENGTH_SHORT).show();
            return;
        }

        // Show progress
        searchPB.setVisibility(View.VISIBLE);
        resultsLayout.setVisibility(View.GONE);
        resultTV.setText("Searching...");

        // Build search parameters from dropdowns
        Map<String, Object> searchParams = new HashMap<>();

        // Location — the text input becomes station_name for Firestore query
        searchParams.put("location_text", locationText);

        // Weather
        String weather = weatherSpinner.getSelectedItem().toString();
        if (!weather.equals("Any")) {
            searchParams.put("weather", weather);
        }

        // Direction → yaw value
        float targetYaw = directionToYaw(directionSpinner.getSelectedItem().toString());
        searchParams.put("yaw", targetYaw);

        // Elevation → pitch value
        float targetPitch = elevationToPitch(elevationSpinner.getSelectedItem().toString());
        searchParams.put("pitch", targetPitch);

        // Time slot
        int timeSlot = timeToSlot(timeSpinner.getSelectedItem().toString());
        if (timeSlot >= 0) {
            searchParams.put("time_slot", timeSlot);
        }

        // Season
        int season = seasonToInt(seasonSpinner.getSelectedItem().toString());
        if (season >= 0) {
            searchParams.put("season", season);
        }

        // Roll default (horizontal)
        searchParams.put("roll", 0f);

        // Exposure from camera settings
        searchParams.put("exposure", parseExposure(cameraSettings.get("exposure")));

        Log.d(TAG, "Search params: " + searchParams);

        // Execute flexible search
        databaseHelper.flexibleSearch(searchParams, new DatabaseHelper.OnPhotoInfoListRetrievedListener() {
            @Override
            public void onPhotoInfoListRetrieved(List<Map<String, Object>> photoInfoList) {
                searchResults = photoInfoList;
                runOnUiThread(() -> displayResults(photoInfoList));
            }

            @Override
            public void onFailure(Exception e) {
                runOnUiThread(() -> {
                    searchPB.setVisibility(View.INVISIBLE);
                    resultTV.setText("Search failed: " + e.getMessage());
                });
            }
        });
    }

    private void displayResults(List<Map<String, Object>> results) {
        searchPB.setVisibility(View.INVISIBLE);

        if (results.isEmpty()) {
            resultTV.setText("No photos found matching your criteria.\nTry broadening your search.");
            resultsLayout.setVisibility(View.GONE);
            return;
        }

        resultTV.setText("Found " + results.size() + " matching photo(s). Tap to view.");
        resultsLayout.setVisibility(View.VISIBLE);

        // Load up to 3 photos
        ImageView[] photoViews = {photo1IV, photo2IV, photo3IV};
        for (int i = 0; i < 3; i++) {
            if (i < results.size()) {
                photoViews[i].setVisibility(View.VISIBLE);
                String url = (String) results.get(i).get("photo_url");
                loadPhoto(url, photoViews[i]);
            } else {
                photoViews[i].setVisibility(View.GONE);
            }
        }
    }

    private void loadPhoto(String url, ImageView imageView) {
        Glide.with(this)
                .asBitmap()
                .load(url)
                .diskCacheStrategy(DiskCacheStrategy.NONE)
                .skipMemoryCache(true)
                .into(new CustomTarget<Bitmap>() {
                    @Override
                    public void onResourceReady(@NonNull Bitmap resource, @Nullable Transition<? super Bitmap> transition) {
                        // Apply white balance from camera settings
                        String wb = cameraSettings.getOrDefault("white_balance", "Auto");
                        Bitmap output = applyWhiteBalance(resource, wb);
                        imageView.setImageBitmap(output);
                    }
                    @Override
                    public void onLoadCleared(@Nullable Drawable placeholder) {}
                });
    }

    private void showPhotoDetail(int index) {
        if (searchResults == null || index >= searchResults.size()) return;
        Map<String, Object> photo = searchResults.get(index);

        String info = "Station: " + photo.get("station_name")
                + "\nWeather: " + photo.get("weather")
                + "\nDatetime: " + photo.get("datetime")
                + "\nYaw: " + String.format("%.1f", toDouble(photo.get("yaw"))) + "°"
                + "\nPitch: " + String.format("%.1f", toDouble(photo.get("pitch"))) + "°"
                + "\nRoll: " + String.format("%.1f", toDouble(photo.get("roll"))) + "°";

        new AlertDialog.Builder(this)
                .setTitle("Photo #" + (index + 1) + " Details")
                .setMessage(info)
                .setPositiveButton("OK", (d, w) -> d.dismiss())
                .setNeutralButton("Generate with AI", (d, w) -> {
                    // TODO: Stage 3 — AI generation from this photo
                    Toast.makeText(this, "AI generation coming soon!", Toast.LENGTH_SHORT).show();
                })
                .show();
    }

    // ── Conversion helpers ──

    private float directionToYaw(String direction) {
        switch (direction) {
            case "North":     return 0f;
            case "Northeast": return 45f;
            case "East":      return 90f;
            case "Southeast": return 135f;
            case "South":     return 180f;
            case "Southwest": return 225f;
            case "West":      return 270f;
            case "Northwest": return 315f;
            default:          return 0f; // "Any"
        }
    }

    private float elevationToPitch(String elevation) {
        switch (elevation) {
            case "Looking up":   return 60f;
            case "Slightly up":  return 30f;
            case "Horizontal":   return 0f;
            case "Slightly down": return -30f;
            case "Looking down": return -60f;
            default:             return 0f; // "Any"
        }
    }

    private int timeToSlot(String time) {
        switch (time) {
            case "Morning (6-12)":   return 0;
            case "Afternoon (12-17)": return 1;
            case "Evening (17-21)":  return 2;
            case "Night (21-6)":     return 3;
            default:                 return -1; // "Any"
        }
    }

    private int seasonToInt(String season) {
        switch (season) {
            case "Spring": return 0;
            case "Summer": return 1;
            case "Autumn": return 2;
            case "Winter": return 3;
            default:       return -1; // "Any"
        }
    }

    private float parseExposure(String expStr) {
        if (expStr == null || expStr.equals("Auto")) return 0;
        if (expStr.contains("/")) {
            String[] parts = expStr.split("/");
            return Float.parseFloat(parts[0]) / Float.parseFloat(parts[1]);
        }
        return Float.parseFloat(expStr);
    }

    private double toDouble(Object obj) {
        return (obj instanceof Number) ? ((Number) obj).doubleValue() : 0;
    }

    // ── White balance (same as MainActivity) ──
    private Bitmap applyWhiteBalance(Bitmap src, String mode) {
        float temp = 6500;
        switch (mode) {
            case "Daylight":     temp = 5200; break;
            case "Fluorescent":  temp = 6000; break;
            case "Incandescent": temp = 7000; break;
        }
        Bitmap output = Bitmap.createBitmap(src.getWidth(), src.getHeight(), Bitmap.Config.ARGB_8888);
        android.graphics.Canvas canvas = new android.graphics.Canvas(output);
        android.graphics.Paint paint = new android.graphics.Paint();
        float k = temp / 100f;
        float r, g, b;
        if (k <= 66) {
            r = 255; g = (float)(99.4708025861 * Math.log(k) - 161.1195681661);
            b = k <= 19 ? 0 : (float)(138.5177312231 * Math.log(k - 10) - 305.0447927307);
        } else {
            r = (float)(329.698727446 * Math.pow(k - 60, -0.1332047592));
            g = (float)(288.1221695283 * Math.pow(k - 60, -0.0755148492)); b = 255;
        }
        r = Math.min(Math.max(r/255,0),1); g = Math.min(Math.max(g/255,0),1); b = Math.min(Math.max(b/255,0),1);
        android.graphics.ColorMatrix cm = new android.graphics.ColorMatrix(new float[]{r,0,0,0,0, 0,g,0,0,0, 0,0,b,0,0, 0,0,0,1,0});
        paint.setColorFilter(new android.graphics.ColorMatrixColorFilter(cm));
        canvas.drawBitmap(src, 0, 0, paint);
        return output;
    }

    private void initCameraSettings() {
        cameraSettings = new HashMap<>();
        String[] keys = {"orientation", "aspect_ratio", "exposure", "white_balance", "focalLen"};
        for (String key : keys) {
            cameraSettings.put(key, bd.getString(key, "Auto"));
        }
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            Intent intent = new Intent(SearchActivity.this, MainActivity.class);
            for (String key : cameraSettings.keySet()) {
                bd.putString(key, cameraSettings.get(key));
            }
            intent.putExtras(bd);
            startActivity(intent);
            finish();
        }
        return true;
    }
}