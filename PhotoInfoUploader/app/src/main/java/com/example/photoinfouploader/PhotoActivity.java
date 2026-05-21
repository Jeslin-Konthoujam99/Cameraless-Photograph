package com.example.photoinfouploader;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.material.floatingactionbutton.FloatingActionButton;

import org.json.JSONObject;

import java.util.HashMap;

public class PhotoActivity extends Activity {
    private static final String TAG = "PhotoActivity";
    private ScrollView sv;
    private ImageView photoIV;
    private Bitmap photoIV_bitmap;
    private Button exifBTN, weatherBTN, sensorBTN;
    private TextView exifTV, weatherTV, sensorTV;
    private FloatingActionButton saveFAB, deleteFAB, uploadFAB;
    private ProgressBar pd;
    private String showExifInfo, showWeatherInfo, showSensorInfo;

    private Bundle bd;
    private int albumId;
    private String photoPath;
    private EXIFReader exifReader;
    private WeatherAPICaller weatherAPICaller;
    private StorageTools storageTools;
    private Uploader uploader;
    private GeocodingHelper geocodingHelper;
    private HashMap<String, String> weatherInfo;
    private float[] orientationArray;

    // ✨ STORE DISTRICT INFO SEPARATELY SO IT DOESN'T GET OVERWRITTEN
    private String detectedCity = "";
    private String detectedDistrict = "";
    private String detectedCountry = "";
    private String detectedFullAddress = "";
    private boolean districtReady = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_photo);
        init();
    }

    private void init() {
        sv = findViewById(R.id.SV);
        photoIV = findViewById(R.id.photoImageView);
        exifBTN = findViewById(R.id.exifInfoButton);
        weatherBTN = findViewById(R.id.weatherInfoButton);
        sensorBTN = findViewById(R.id.sensorInfoButton);
        exifTV = findViewById(R.id.exifInfoTextView);
        weatherTV = findViewById(R.id.weatherInfoTextView);
        sensorTV = findViewById(R.id.sensorInfoTextView);
        saveFAB = findViewById(R.id.saveFloatingActionButton);
        deleteFAB = findViewById(R.id.deleteFloatingActionButton);
        uploadFAB = findViewById(R.id.uploadPhotoFloatingActionButton);
        pd = findViewById(R.id.photoProgressBar);

        bd = getIntent().getExtras();
        if (bd == null) { finish(); return; }
        albumId = bd.getInt("album ID");
        storageTools = new StorageTools();
        exifReader = new EXIFReader();
        uploader = new Uploader();
        geocodingHelper = new GeocodingHelper(this);
        weatherInfo = new HashMap<>();

        if(bd.getBoolean("preview mode")) {
            photoPath = bd.getString("photo path");
            exifReader.setPhotoPath(photoPath);
            HashMap<String, String> exifInfo = exifReader.getEXIF();

            Double lat = 0.0, lon = 0.0;
            try {
                if (exifInfo.get("exifGPSLAT") != null) lat = Double.parseDouble(exifInfo.get("exifGPSLAT"));
                if (exifInfo.get("exifGPSLONG") != null) lon = Double.parseDouble(exifInfo.get("exifGPSLONG"));
            } catch (Exception e) {
                Log.e(TAG, "Error parsing GPS coordinates: " + e.getMessage());
            }

            // ✨ GET DISTRICT INFO IN BACKGROUND THREAD
            final Double finalLat = lat;
            final Double finalLon = lon;
            Thread geocodingThread = new Thread(() -> {
                GeocodingHelper.AddressInfo address = geocodingHelper.getAddressFromCoordinates(finalLat, finalLon);
                if (address != null) {
                    Log.d(TAG, "✅ District detected: " + address.district);
                    Log.d(TAG, "   City: " + address.city);
                    Log.d(TAG, "   Country: " + address.country);
                    Log.d(TAG, "   Full Address: " + address.fullAddress);

                    // ✨ STORE IN SEPARATE VARIABLES
                    detectedCity = address.city != null ? address.city : "";
                    detectedDistrict = address.district != null ? address.district : "";
                    detectedCountry = address.country != null ? address.country : "";
                    detectedFullAddress = address.fullAddress != null ? address.fullAddress : "";
                    districtReady = true;
                } else {
                    Log.w(TAG, "⚠️ Could not get address info");
                }
            });
            geocodingThread.start();
            // ✅ WAIT for geocoding to finish (max 10 seconds)
            try {
                geocodingThread.join(10000);
            } catch (InterruptedException e) {
                Log.e(TAG, "Geocoding thread interrupted", e);
            }

            weatherAPICaller = new WeatherAPICaller(lat, lon, exifInfo.get("exifDatetime"));
            deleteFAB.setVisibility(View.INVISIBLE);

            float[] acc = bd.getFloatArray("accelerometer array");
            float[] mag = bd.getFloatArray("megnetometer array");
            orientationArray = new float[3];
            if (acc != null && mag != null) {
                float[] R = new float[9];
                SensorManager.getRotationMatrix(R, null, acc, mag);
                SensorManager.getOrientation(R, orientationArray);
                for(int i = 0; i < 3; i++) orientationArray[i] = (float)Math.toDegrees(orientationArray[i]);
            }
        } else {
            orientationArray = new float[3];
            setInfoFromJson(bd.getString("photo json object"));
            saveFAB.setVisibility(View.INVISIBLE);
        }

        photoIV_bitmap = BitmapFactory.decodeFile(photoPath);
        if (photoIV_bitmap != null) {
            if("6".equals(exifReader.getPhotoOrientation(photoPath))) {
                Matrix m = new Matrix(); m.postRotate(90);
                photoIV_bitmap = Bitmap.createBitmap(photoIV_bitmap, 0, 0, photoIV_bitmap.getWidth(), photoIV_bitmap.getHeight(), m, true);
            }
            photoIV.setImageBitmap(photoIV_bitmap);
        }
        listeners();
    }

    private void listeners() {
        exifBTN.setOnClickListener(v -> {
            if(exifTV.getVisibility() == View.GONE || exifTV.getText().length() == 0) {
                exifTV.setVisibility(View.VISIBLE);
                if(showExifInfo == null) {
                    HashMap<String, String> info = exifReader.getEXIF();

                    // Detect white balance from context (same logic as DataTypeConverter)
                    // so app display matches what DB stores
                    String wbDisplay = "Auto";
                    try {
                        String datetime = info.get("exifDatetime");
                        int isoVal = 0;
                        float expVal = 0.0f;
                        try {
                            if (info.get("exifISO") != null) isoVal = Integer.parseInt(info.get("exifISO").trim());
                            if (info.get("exifExposure") != null) expVal = Float.parseFloat(info.get("exifExposure").trim());
                        } catch (Exception ignored) {}

                        int hour = -1;
                        try {
                            if (datetime != null && datetime.contains(" ")) {
                                hour = Integer.parseInt(datetime.split(" ")[1].split(":")[0]);
                            } else if (datetime != null) {
                                hour = Integer.parseInt(datetime.split(":")[0]);
                            }
                        } catch (Exception ignored) {}

                        boolean likelyIndoor = (isoVal > 800 && expVal > 0.05f);
                        String weather = (weatherInfo != null) ? weatherInfo.get("weather") : null;

                        if (hour >= 0) {
                            if (hour >= 18 || hour < 6) {
                                wbDisplay = likelyIndoor ? (isoVal > 1600 ? "Incandescent" : "Fluorescent") : "Twilight";
                            } else if (hour < 8 || hour == 17) {
                                wbDisplay = "Shade";
                            } else if (likelyIndoor) {
                                wbDisplay = "Fluorescent";
                            } else if (weather != null) {
                                String w = weather.toLowerCase();
                                if (w.contains("cloud")) wbDisplay = "Cloudy";
                                else if (w.contains("rain") || w.contains("fog") || w.contains("mist")) wbDisplay = "Shade";
                                else wbDisplay = "Daylight";
                            } else {
                                wbDisplay = "Daylight";
                            }
                        }
                    } catch (Exception ignored) {}

                    showExifInfo = "Orientation: " + exifReader.getShotDirection(info.get("exifOrient")) + "\n" +
                            "Capture Time: " + info.get("exifDatetime") + "\n" +
                            "Camera Brand: " + info.get("exifMaker") + "\n" +
                            "Camera Model: " + info.get("exifModel") + "\n" +
                            "Flash: " + info.get("exifFlash") + "\n" +
                            "Image Height: " + info.get("exifImgLen") + "\n" +
                            "Image Width: " + info.get("exifImgWid") + "\n" +
                            "Latitude: " + info.get("exifGPSLAT") + "\n" +
                            "Longitude: " + info.get("exifGPSLONG") + "\n" +
                            "Exposure Time: " + info.get("exifExposure") + "\n" +
                            "Aperture: " + info.get("exifAperture") + "\n" +
                            "ISO: " + info.get("exifISO") + "\n" +
                            "White Balance: " + wbDisplay + "\n" +
                            "Focal Length: " + info.get("exifFocalLen");
                }
                exifTV.setText(showExifInfo);
            } else exifTV.setVisibility(View.GONE);
        });

        weatherBTN.setOnClickListener(v -> {
            if(weatherTV.getVisibility() == View.GONE) {
                weatherTV.setVisibility(View.VISIBLE);
                if(showWeatherInfo == null) {
                    if (weatherAPICaller != null) {
                        pd.setVisibility(View.VISIBLE);
                        weatherAPICaller.fetchWeather(info -> {
                            weatherInfo.putAll(info);

                            // ✨ AFTER WEATHER DATA ARRIVES, ADD DISTRICT DATA
                            addDistrictToWeatherInfo();

                            runOnUiThread(() -> {
                                pd.setVisibility(View.GONE);
                                updateWeatherUI();
                            });
                        });
                    } else {
                        updateWeatherUI();
                    }
                }
            } else weatherTV.setVisibility(View.GONE);
        });

        sensorBTN.setOnClickListener(v -> {
            if(sensorTV.getVisibility() == View.GONE || sensorTV.getText().length() == 0) {
                sensorTV.setVisibility(View.VISIBLE);
                showSensorInfo = "Pitch: " + orientationArray[1] + "°\nRoll: " + orientationArray[2] + "°\nYaw: " + orientationArray[0] + "°";
                sensorTV.setText(showSensorInfo);
            } else sensorTV.setVisibility(View.GONE);
        });

        saveFAB.setOnClickListener(v -> {
            pd.setVisibility(View.VISIBLE);
            if (weatherAPICaller != null) {
                weatherAPICaller.fetchWeather(info -> {
                    weatherInfo.putAll(info);
                    addDistrictToWeatherInfo();
                    runOnUiThread(() -> performSave());
                });
            } else {
                performSave();
            }
        });

        deleteFAB.setOnClickListener(v -> {
            new AlertDialog.Builder(this)
                    .setTitle("Delete Photo")
                    .setMessage("Are you sure you want to delete this photo?")
                    .setPositiveButton("Delete", (dialog, which) -> {
                        try {
                            storageTools.deletePhoto(this, albumId, photoPath);
                            Intent intent = new Intent(this, AlbumActivity.class);
                            intent.putExtra("album ID", albumId);
                            intent.putExtra("back mode", 2);
                            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                            startActivity(intent);
                            finish();
                        } catch (Exception e) {
                            e.printStackTrace();
                            Toast.makeText(this, "Delete failed", Toast.LENGTH_SHORT).show();
                        }
                    })
                    .setNegativeButton("Cancel", null).show();
        });

        uploadFAB.setOnClickListener(v -> {
            pd.setVisibility(View.VISIBLE);
            if (weatherAPICaller != null) {
                weatherAPICaller.fetchWeather(info -> {
                    weatherInfo.putAll(info);
                    addDistrictToWeatherInfo();
                    runOnUiThread(() -> performUpload());
                });
            } else {
                performUpload();
            }
        });
    }

    /**
     * ✨ ADD DISTRICT DATA TO WEATHER INFO
     * Race condition FIXED: geocoding thread.join() ensures data is ready
     */
    private void addDistrictToWeatherInfo() {
        weatherInfo.put("city", detectedCity);
        weatherInfo.put("district", detectedDistrict);
        weatherInfo.put("country", detectedCountry);
        weatherInfo.put("fullAddress", detectedFullAddress);

        // Build station name from geocoding data
        String city = detectedCity;
        if (city == null || city.isEmpty() || city.equals("Unknown")) {
            String currentName = weatherInfo.get("locationName");
            if (currentName != null) {
                city = currentName.replace(" Station", "").trim();
            } else {
                city = "Unknown";
            }
        }

        // Use "City - District" if district is available and different from city
        if (districtReady && detectedDistrict != null && !detectedDistrict.isEmpty()
                && !detectedDistrict.equals("Unknown") && !detectedDistrict.equals(city)) {
            weatherInfo.put("locationName", city + " - " + detectedDistrict);
        } else {
            weatherInfo.put("locationName", city);
        }

        Log.d(TAG, "✅ Station name: " + weatherInfo.get("locationName"));
        Log.d(TAG, "   City: " + detectedCity);
        Log.d(TAG, "   District: " + detectedDistrict);
        Log.d(TAG, "   districtReady: " + districtReady);
    }

    private void performSave() {
        try {
            int res = storageTools.save(this, photoPath, albumId, orientationArray, weatherInfo, bd.getString("location_provider"));
            pd.setVisibility(View.GONE);
            if(res == 0) {
                Intent intent = new Intent(this, AlbumActivity.class);
                intent.putExtra("album ID", albumId);
                intent.putExtra("back mode", 1);
                intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                startActivity(intent);
                finish();
            } else Toast.makeText(this, "Save Error: " + res, Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            e.printStackTrace();
            pd.setVisibility(View.GONE);
        }
    }

    private void performUpload() {
        if(bd.getBoolean("preview mode")) {
            try {
                storageTools.save(this, photoPath, albumId, orientationArray, weatherInfo, bd.getString("location_provider"));
            } catch (Exception e) {
                Log.e(TAG, "Error saving to local storage: " + e.getMessage());
            }
        }

        uploader.uploadPhoto2Firebase(this, albumId, photoPath, success -> {
            runOnUiThread(() -> {
                pd.setVisibility(View.GONE);

                if (success) {
                    Toast.makeText(PhotoActivity.this, "✅ Upload Successful!", Toast.LENGTH_SHORT).show();
                    Log.d("Upload", "✅ Photo uploaded successfully!");

                    new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
                        Intent intent = new Intent(PhotoActivity.this, AlbumActivity.class);
                        intent.putExtra("album ID", albumId);
                        intent.putExtra("back mode", 3);
                        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                        startActivity(intent);
                        finish();
                    }, 2000);
                } else {
                    Toast.makeText(PhotoActivity.this, "❌ Upload Failed!", Toast.LENGTH_SHORT).show();
                    Log.e("Upload", "❌ Photo upload failed!");
                }
            });
        });
    }

    private void updateWeatherUI() {
        if(weatherInfo != null && !weatherInfo.isEmpty() && weatherInfo.get("locationName") != null) {
            showWeatherInfo = "Station: " + weatherInfo.get("locationName") + "\n" +
                    "Wind Direction: " + weatherInfo.get("windDirection") + "\n" +
                    "Wind Speed: " + weatherInfo.get("windSpeed") + " m/s\n" +
                    "Temperature: " + weatherInfo.get("temperature") + "°C\n" +
                    "Humidity: " + weatherInfo.get("humidity") + "%\n" +
                    "Pressure: " + weatherInfo.get("pressure") + " hPa\n" +
                    "Daily Rainfall: " + weatherInfo.get("dayRain") + " mm\n" +
                    "Peak Gust Speed: " + weatherInfo.get("gustSpeed") + "\n" +
                    "Weather: " + weatherInfo.get("weather");
        } else {
            showWeatherInfo = "Weather information unavailable";
        }
        weatherTV.setText(showWeatherInfo);
    }

    private void setInfoFromJson(String json) {
        try {
            JSONObject obj = new JSONObject(json);
            photoPath = obj.getString("photo path");
            exifReader.setPhotoPath(photoPath);
            JSONObject w = obj.optJSONObject("weatherInfo");
            weatherInfo = new HashMap<>();
            if (w != null) {
                weatherInfo.put("locationName", w.optString("locationName", "N/A"));
                weatherInfo.put("weather", w.optString("weather", "N/A"));
                weatherInfo.put("gustSpeed", w.optString("gustSpeed", "N/A"));
                weatherInfo.put("windDirection", w.optString("windDirection", "N/A"));
                weatherInfo.put("windSpeed", w.optString("windSpeed", "N/A"));
                weatherInfo.put("temperature", w.optString("temperature", "N/A"));
                weatherInfo.put("humidity", w.optString("humidity", "N/A"));
                weatherInfo.put("pressure", w.optString("pressure", "N/A"));
                weatherInfo.put("dayRain", w.optString("dayRain", "N/A"));
                weatherInfo.put("city", w.optString("city", "N/A"));
                weatherInfo.put("district", w.optString("district", "N/A"));
            }
            JSONObject s = obj.optJSONObject("orientation info");
            if (s != null) {
                orientationArray = new float[]{(float)s.optDouble("yaw", 0.0), (float)s.optDouble("pitch", 0.0), (float)s.optDouble("roll", 0.0)};
            } else {
                orientationArray = new float[3];
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (photoIV_bitmap != null) photoIV_bitmap.recycle();
    }
}