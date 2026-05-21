package com.example.cameralessphotography;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Paint;
import android.graphics.drawable.Drawable;
import android.hardware.SensorManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Build;
import android.os.Bundle;
import android.util.Base64;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.DecodeFormat;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.load.resource.bitmap.CenterCrop;
import com.bumptech.glide.load.resource.bitmap.RoundedCorners;
import com.bumptech.glide.request.RequestOptions;
import com.bumptech.glide.request.target.CustomTarget;
import com.bumptech.glide.request.transition.Transition;
import com.google.firebase.firestore.QueryDocumentSnapshot;

public class MainActivity extends Activity {
    private Button shotBTN, menuBTN, weatherBTN, searchBTN;
    private ImageView photoIV;
    private TextView testTV;
    private ProgressBar pb;
    private WeatherAPICaller weatherAPICaller;
    private HashMap<String, String> weatherInfo;
    private String blob, photo_base64, datetime, uploadStr;
    private LocationManager locationManager;
    private LocationListener locationListener;
    private Location lastKnownLocation;
    private Bundle bd;
    private float[] accelerometerValue, gyroscopeValue, megnetometerValue, orientationArray;
    private SensorDataReader sensorDataReader;
    private DatabaseHelper databaseHelper;
    private DataTypeConverter dataTypeConverter;
    private Map<String, String> camera_settings;
    private Map<String, Object> photograph_parameters;
    private SimpleDateFormat sdf;
    private float exposure;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        init();
    }
    @Override
    protected void onResume()
    {
        super.onResume();
        sensorDataReader.startListening();
    }
    @Override
    protected void onPause() {
        super.onPause();
        sensorDataReader.stopListening();
    }
    private void init() {
        requestPermission();

        shotBTN = findViewById(R.id.shotButton);
        menuBTN = findViewById(R.id.menuButton);
        weatherBTN = findViewById(R.id.weatherButton);
        searchBTN = findViewById(R.id.searchButton);
        photoIV = findViewById(R.id.photoImageView);
        testTV = findViewById(R.id.testTextView);
        pb = findViewById(R.id.mainProgressBar);

        weatherAPICaller = new WeatherAPICaller(this);
        sensorDataReader = new SensorDataReader(this);
        databaseHelper = new DatabaseHelper();
        dataTypeConverter = new DataTypeConverter();

        sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        bd = getIntent().getExtras();
        if(bd == null) bd = new Bundle();
        initCameraSettings();

        locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);

        // Check permissions properly instead of blocking while loop
        if(ActivityCompat.checkSelfPermission(MainActivity.this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
                ActivityCompat.checkSelfPermission(MainActivity.this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED)
        {
            Toast.makeText(this, "Location permission required", Toast.LENGTH_SHORT).show();
            return;
        }

        locationListener = new LocationListener() {
            @Override
            public void onLocationChanged(Location location) {
                lastKnownLocation = location;
            }

            @Override
            public void onProviderEnabled(String provider) {}

            @Override
            public void onProviderDisabled(String provider) {}

            @Override
            public void onStatusChanged(String provider, int status, Bundle extras) {}
        };

        boolean provider_exist = false;
        if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER))
        {
            locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 5000, 0f, locationListener);
            provider_exist = true;
        }
        if (locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER))
        {
            locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 5000, 0f, locationListener);
            provider_exist = true;
        }
        if (!provider_exist)
        {
            Toast.makeText(this, "Please enable location services", Toast.LENGTH_SHORT).show();
            return;
        }

        clickListeners();
    }
    private void clickListeners() {
        shotBTN.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if(pb.getVisibility()==View.INVISIBLE) pb.setVisibility(View.VISIBLE);
                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        try
                        {
                            lastKnownLocation = getLocation();
                            if(lastKnownLocation==null)
                            {
                                onUIShotProcess(2);
                                return;
                            }

                            onUIShotProcess(0);

                            accelerometerValue = sensorDataReader.getAccelerometerValue();
                            gyroscopeValue = sensorDataReader.getGyroscopeValue();
                            megnetometerValue = sensorDataReader.getMegnetometerValue();

                            float[] rotationMatrix = new float[9];
                            SensorManager.getRotationMatrix(rotationMatrix, null, accelerometerValue, megnetometerValue);
                            orientationArray = new float[3];
                            SensorManager.getOrientation(rotationMatrix, orientationArray);
                            for(int i = 0; i < 3; ++i)
                            {
                                orientationArray[i] = (float)Math.toDegrees(orientationArray[i]);
                            }

                            datetime = sdf.format(new Date());

                            // ✅ FIX: Always fetch FRESH weather (don't use cached)
                            // Weather can change, and geocoding needs fresh coordinates
                            weatherInfo = weatherAPICaller.getWeatherInfo(lastKnownLocation.getLatitude(), lastKnownLocation.getLongitude());

                            if(!setPhotograph_parameters())
                            {
                                onUIShotProcess(2);
                                return;
                            }

                            // Show query info on UI
                            showQueryInfo();

                            databaseHelper.getPhotoInfoList(photograph_parameters, new DatabaseHelper.OnPhotoInfoListRetrievedListener() {
                                @Override
                                public void onPhotoInfoListRetrieved(List<Map<String, Object>> photoInfoList) {
                                    if(photoInfoList.size() == 0)
                                    {
                                        onUIShotProcess(2);
                                        return;
                                    }
                                    Map<String, Object> targetPhotoInfo = photoInfoList.get(0);
                                    String photo_url = (String)targetPhotoInfo.get("photo_url");
                                    onUIShotProcess(1);
                                    Glide.with(MainActivity.this)
                                            .asBitmap()
                                            .load(photo_url)
                                            .diskCacheStrategy(DiskCacheStrategy.NONE)
                                            .skipMemoryCache(true)
                                            .into(new CustomTarget<Bitmap>() {
                                                @Override
                                                public void onResourceReady(@NonNull Bitmap resource, @Nullable Transition<? super Bitmap> transition) {
                                                    Bitmap output = applyWhiteBalance(resource, camera_settings.get("white_balance"));
                                                    photoIV.setImageBitmap(output);
                                                    onUIShotProcess(3);
                                                }

                                                @Override
                                                public void onLoadCleared(@Nullable Drawable placeholder) {
                                                }
                                            });
                                }

                                @Override
                                public void onFailure(Exception e) {
                                    onUIShotProcess(2);
                                }
                            });
                        }
                        catch(Exception e)
                        {
                            e.printStackTrace();
                            onUIShotProcess(2);
                        }
                    }
                }).start();
            }
        });

        menuBTN.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(MainActivity.this, MenuActivity.class);
                bd.putString("orientation", camera_settings.get("orientation"));
                bd.putString("aspect_ratio", camera_settings.get("aspect_ratio"));
                bd.putString("exposure", camera_settings.get("exposure"));
                bd.putString("white_balance", camera_settings.get("white_balance"));
                bd.putString("focalLen", camera_settings.get("focalLen"));
                intent.putExtras(bd);
                startActivity(intent);
                finish();
            }
        });

        // ✅ FIXED: Weather button always fetches FRESH data
        weatherBTN.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Toast.makeText(MainActivity.this, "Fetching weather data...", Toast.LENGTH_SHORT).show();
                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            lastKnownLocation = getLocation();
                            if(lastKnownLocation == null) {
                                runOnUiThread(() -> Toast.makeText(MainActivity.this, "Location not available", Toast.LENGTH_SHORT).show());
                                return;
                            }
                            weatherInfo = weatherAPICaller.getWeatherInfo(lastKnownLocation.getLatitude(), lastKnownLocation.getLongitude());
                            runOnUiThread(() -> showWeatherDialog());
                        } catch (Exception e) {
                            runOnUiThread(() -> Toast.makeText(MainActivity.this, "Failed to fetch weather", Toast.LENGTH_SHORT).show());
                        }
                    }
                }).start();
            }
        });

        searchBTN.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(MainActivity.this, SearchActivity.class);
                bd.putString("orientation", camera_settings.get("orientation"));
                bd.putString("aspect_ratio", camera_settings.get("aspect_ratio"));
                bd.putString("exposure", camera_settings.get("exposure"));
                bd.putString("white_balance", camera_settings.get("white_balance"));
                bd.putString("focalLen", camera_settings.get("focalLen"));
                intent.putExtras(bd);
                startActivity(intent);
                finish();
            }
        });



    }

    // ✅ NEW: Show weather dialog
    private void showWeatherDialog() {
        if(weatherInfo == null || weatherInfo.isEmpty()) {
            Toast.makeText(this, "Weather information not available", Toast.LENGTH_SHORT).show();
            return;
        }
        String weatherStr = "Station: " + weatherInfo.get("stationName") + "\n" +
                "Temperature: " + weatherInfo.get("temperature") + "°C\n" +
                "Humidity: " + weatherInfo.get("humidity") + "%\n" +
                "Weather: " + weatherInfo.get("weather") + "\n" +
                "Wind Speed: " + weatherInfo.get("windSpeed") + " m/s\n" +
                "Wind Direction: " + weatherInfo.get("windDirection") + "\n" +
                "Pressure: " + weatherInfo.get("pressure") + " hPa\n" +
                "Peak Gust Speed: " + weatherInfo.get("gustSpeed") + "\n" +
                "Daily Rainfall: " + weatherInfo.get("dayRain") + " mm";
        new AlertDialog.Builder(MainActivity.this)
                .setTitle("Weather Information")
                .setMessage(weatherStr)
                .setPositiveButton("OK", (dialog, which) -> dialog.dismiss())
                .show();
    }

    // ✅ NEW: Show query parameters and sensor info after camera click
    private void showQueryInfo() {
        runOnUiThread(() -> {
            StringBuilder info = new StringBuilder();
            info.append("Querying database...\n");
            info.append("Station: ").append(photograph_parameters.get("station_name")).append("\n");
            info.append("Weather: ").append(photograph_parameters.get("weather")).append("\n");
            info.append("Wind: ").append(photograph_parameters.get("wind_direction")).append("\n");
            info.append("Yaw: ").append(String.format("%.1f", orientationArray[0])).append("°\n");
            info.append("Pitch: ").append(String.format("%.1f", orientationArray[1])).append("°\n");
            info.append("Roll: ").append(String.format("%.1f", orientationArray[2])).append("°");
            testTV.setText(info.toString());
        });
    }
    private void onUIShotProcess(int state)
    {
        // Build sensor info from whatever data is available
        StringBuilder sensorInfoBuilder = new StringBuilder();
        sensorInfoBuilder.append("\n\n--- Query Info ---\n");

        // Location info
        if (lastKnownLocation != null) {
            sensorInfoBuilder.append("Lat: ").append(String.format("%.4f", lastKnownLocation.getLatitude())).append("\n");
            sensorInfoBuilder.append("Lon: ").append(String.format("%.4f", lastKnownLocation.getLongitude())).append("\n");
        }

        // Weather info
        if (weatherInfo != null && !weatherInfo.isEmpty()) {
            sensorInfoBuilder.append("Station: ").append(weatherInfo.getOrDefault("stationName", "N/A")).append("\n");
            sensorInfoBuilder.append("Weather: ").append(weatherInfo.getOrDefault("weather", "N/A")).append("\n");
            sensorInfoBuilder.append("Wind: ").append(weatherInfo.getOrDefault("windDirection", "N/A")).append("\n");
        }

        // Sensor info
        if (orientationArray != null) {
            sensorInfoBuilder.append("Yaw: ").append(String.format("%.1f", orientationArray[0])).append("°\n");
            sensorInfoBuilder.append("Pitch: ").append(String.format("%.1f", orientationArray[1])).append("°\n");
            sensorInfoBuilder.append("Roll: ").append(String.format("%.1f", orientationArray[2])).append("°");
        }

        final String info = sensorInfoBuilder.toString();

        switch(state)
        {
            case 0:
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        testTV.setText("Processing..." + info);
                    }
                });
                break;
            case 1:
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        testTV.setText("Loading photo..." + info);
                    }
                });
                break;
            case 2:
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        testTV.setText("No matching photos found" + info);
                        pb.setVisibility(View.INVISIBLE);
                    }
                });
                break;
            case 3:
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        testTV.setText("Photo retrieved successfully" + info);
                        pb.setVisibility(View.INVISIBLE);
                    }
                });
                break;
        }
    }
    private Bitmap applyWhiteBalance(Bitmap src, String white_balance_mode)
    {
        Bitmap output = Bitmap.createBitmap(src.getWidth(), src.getHeight(), Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(output);
        Paint paint = new Paint();

        float temperature = 6500;
        switch(white_balance_mode)
        {
            case "Auto":
                temperature = 6500;
                break;
            case "Daylight":
                temperature = 5200;
                break;
            case "Fluorescent":
                temperature = 6000;
                break;
            case "Incandescent":
                temperature = 7000;

        }
        ColorMatrix colorMatrix = createColorMatrixForTemperature(temperature);
        paint.setColorFilter(new ColorMatrixColorFilter(colorMatrix));

        canvas.drawBitmap(src, 0, 0, paint);
        return output;
    }
    private ColorMatrix createColorMatrixForTemperature(float temperature) {
        float kelvin = temperature / 100;
        float r, g, b;

        if (kelvin <= 66)
        {
            r = 255;
            g = (float) (99.4708025861 * Math.log(kelvin) - 161.1195681661);
            b = kelvin <= 19 ? 0 : (float) (138.5177312231 * Math.log(kelvin - 10) - 305.0447927307);
        }
        else
        {
            r = (float) (329.698727446 * Math.pow(kelvin - 60, -0.1332047592));
            g = (float) (288.1221695283 * Math.pow(kelvin - 60, -0.0755148492));
            b = 255;
        }

        r = Math.min(Math.max(r / 255, 0), 1);
        g = Math.min(Math.max(g / 255, 0), 1);
        b = Math.min(Math.max(b / 255, 0), 1);

        ColorMatrix colorMatrix = new ColorMatrix(new float[]{
                r, 0, 0, 0, 0,
                0, g, 0, 0, 0,
                0, 0, b, 0, 0,
                0, 0, 0, 1, 0
        });
        return colorMatrix;
    }
    // ✅ NEW METHOD: Convert degrees to direction text
    private String convertDegreesToDirection(int degrees) {
        degrees = degrees % 360;

        if (degrees >= 337.5 || degrees < 22.5) {
            return "North";
        } else if (degrees >= 22.5 && degrees < 67.5) {
            return "North-East";
        } else if (degrees >= 67.5 && degrees < 112.5) {
            return "East";
        } else if (degrees >= 112.5 && degrees < 157.5) {
            return "South-East";
        } else if (degrees >= 157.5 && degrees < 202.5) {
            return "South";
        } else if (degrees >= 202.5 && degrees < 247.5) {
            return "South-West";
        } else if (degrees >= 247.5 && degrees < 292.5) {
            return "West";
        } else if (degrees >= 292.5 && degrees < 337.5) {
            return "North-West";
        }

        return "Unknown";
    }

    // ✅ NEW METHOD: Extract city from full station name
    private String extractCityFromStation(String fullStation) {
        if (fullStation == null || fullStation.isEmpty()) {
            return "";
        }

        // Split by "-" and get first part
        // "Taipei - Da'an District" → "Taipei"
        String[] parts = fullStation.split("-");
        if (parts.length > 0) {
            return parts[0].trim();
        }

        return fullStation;
    }
    private boolean setPhotograph_parameters()
    {
        if(weatherInfo == null || weatherInfo.isEmpty()) return false;

        // ✅ Build parameters directly from weatherInfo
        // Don't use dataTypeConverter.getWeatherInfoMap4FirebaseQuery() because
        // it tries to parseFloat("West") for wind direction and CRASHES!
        photograph_parameters = new HashMap<>();
        photograph_parameters.put("weather", weatherInfo.getOrDefault("weather", "Unknown"));
        photograph_parameters.put("station_name", weatherInfo.getOrDefault("stationName", "Unknown"));
        photograph_parameters.put("wind_direction", weatherInfo.getOrDefault("windDirection", "Unknown"));
        photograph_parameters.put("wind_speed", weatherInfo.getOrDefault("windSpeed", "0"));
        photograph_parameters.put("temperature", weatherInfo.getOrDefault("temperature", "0"));
        photograph_parameters.put("humidity", weatherInfo.getOrDefault("humidity", "0"));
        photograph_parameters.put("pressure", weatherInfo.getOrDefault("pressure", "0"));

        photograph_parameters.put("datetime", datetime);
        photograph_parameters.put("latitude", lastKnownLocation.getLatitude());
        photograph_parameters.put("longitude", lastKnownLocation.getLongitude());
        photograph_parameters.put("pitch", orientationArray[1]);
        photograph_parameters.put("roll", orientationArray[2]);
        photograph_parameters.put("yaw", orientationArray[0]);
        photograph_parameters.put("exposure", exposure);
        return true;
    }
    private Location getLocation()
    {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return null;
        }

        // ✅ FIX: Check if lastKnownLocation from listener is fresh (< 60 seconds)
        if (lastKnownLocation != null) {
            long age = System.currentTimeMillis() - lastKnownLocation.getTime();
            if (age < 60000) {
                return lastKnownLocation;  // Fresh location from listener
            }
        }

        // Try GPS provider with age check
        Location location = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
        if (location != null) {
            long age = System.currentTimeMillis() - location.getTime();
            if (age < 120000) {  // Less than 2 minutes
                lastKnownLocation = location;
                return location;
            }
        }

        // Try network provider
        location = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
        if (location != null) {
            long age = System.currentTimeMillis() - location.getTime();
            if (age < 120000) {
                lastKnownLocation = location;
                return location;
            }
        }

        // Fallback: return whatever we have
        if (lastKnownLocation != null) return lastKnownLocation;
        return locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
    }

    private JSONObject getUploadObjectForMySQL()
    {
        try {

            JSONObject uploadObject = new JSONObject();

            uploadObject.put("weather", weatherInfo.get("weather"));
            uploadObject.put("station_name", weatherInfo.get("StationName"));
            uploadObject.put("latitude", lastKnownLocation.getLatitude());
            uploadObject.put("longitude", lastKnownLocation.getLongitude());
            uploadObject.put("roll", orientationArray[2]);
            uploadObject.put("yaw", orientationArray[0]);
            uploadObject.put("pitch", orientationArray[1]);

            return uploadObject;
        }
        catch (JSONException e)
        {
            return (new JSONObject());
        }
    }

    private void initCameraSettings()
    {
        camera_settings = new HashMap<>();
        if(bd.isEmpty())
        {
            camera_settings.put("orientation", "Auto");
            camera_settings.put("aspect_ratio", "Auto");
            camera_settings.put("exposure", "Auto");
            camera_settings.put("white_balance", "Auto");
            camera_settings.put("focalLen", "Auto");
        }
        else
        {
            camera_settings.put("orientation", bd.getString("orientation"));
            camera_settings.put("aspect_ratio", bd.getString("aspect_ratio"));
            camera_settings.put("exposure", bd.getString("exposure"));
            camera_settings.put("white_balance", bd.getString("white_balance"));
            camera_settings.put("focalLen", bd.getString("focalLen"));
        }
        if(camera_settings.get("exposure").equals("Auto")) exposure = 0;
        else if(camera_settings.get("exposure").contains("/"))
        {
            String[] expstrs = camera_settings.get("exposure").split("/");
            exposure = Float.parseFloat(expstrs[0]) / Float.parseFloat(expstrs[1]);
        }
        else exposure = Float.parseFloat(camera_settings.get("exposure"));
    }

    private void requestPermission()
    {
        boolean[] permissionHasGone = new boolean[7];
        permissionHasGone[0] = checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED;
        permissionHasGone[1] = checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED;
        permissionHasGone[2] = checkSelfPermission(Manifest.permission.ACCESS_MEDIA_LOCATION) == PackageManager.PERMISSION_GRANTED;
        permissionHasGone[3] = checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
        permissionHasGone[4] = checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;
        permissionHasGone[5] = checkSelfPermission(Manifest.permission.ACCESS_LOCATION_EXTRA_COMMANDS) == PackageManager.PERMISSION_GRANTED;
        permissionHasGone[6] = checkSelfPermission(Manifest.permission.ACCESS_BACKGROUND_LOCATION) == PackageManager.PERMISSION_GRANTED;


        String[] permissionStr = new String[7];
        permissionStr[0] = Manifest.permission.WRITE_EXTERNAL_STORAGE;
        permissionStr[1] = Manifest.permission.READ_EXTERNAL_STORAGE;
        permissionStr[2] = Manifest.permission.ACCESS_MEDIA_LOCATION;
        permissionStr[3] = Manifest.permission.ACCESS_FINE_LOCATION;
        permissionStr[4] = Manifest.permission.ACCESS_COARSE_LOCATION;
        permissionStr[5] = Manifest.permission.ACCESS_LOCATION_EXTRA_COMMANDS;
        permissionStr[6] = Manifest.permission.ACCESS_BACKGROUND_LOCATION;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
        {
            int numOfPermissions = 0;
            for(int i=0;i<permissionHasGone.length;++i)
                if(!permissionHasGone[i])
                    ++numOfPermissions;
            if(numOfPermissions==0) return;
            String[] permissions = new String[numOfPermissions];

            int index=0;
            for(int i=0;i<permissionHasGone.length;++i)
            {
                if(!permissionHasGone[i])
                {
                    permissions[index] = permissionStr[i];
                    ++index;
                }
            }
            requestPermissions(permissions, 100);
        }
    }

    public boolean onKeyDown(int keyCode, KeyEvent event)
    {
        if (keyCode == KeyEvent.KEYCODE_BACK)
        {
            new AlertDialog.Builder(MainActivity.this)
                    .setTitle("Exit").setMessage("Exit application?")
                    .setPositiveButton("Exit", new DialogInterface.OnClickListener()
                    {
                        @Override
                        public void onClick(DialogInterface dialog, int which)
                        {
                            finishAffinity();
                        }
                    })
                    .setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which)
                        {
                            dialog.dismiss();
                        }
                    }).show();
        }
        return true;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Unregister location listener
        locationManager.removeUpdates(locationListener);
    }
}