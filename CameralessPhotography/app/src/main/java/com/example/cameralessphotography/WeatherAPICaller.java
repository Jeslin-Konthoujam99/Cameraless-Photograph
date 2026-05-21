package com.example.cameralessphotography;

import android.content.Context;
import android.location.Address;
import android.location.Geocoder;
import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;

public class WeatherAPICaller {

    private static final String TAG = "WeatherAPICaller";
    private static final String API_KEY = "114cc9fb42f75ec87425c7ec0f528998";
    private HashMap<String, String> weatherInfo;
    private Context context;

    public WeatherAPICaller(Context context) {
        this.context = context;
        weatherInfo = new HashMap<>();
    }

    // Keep old constructor for backward compatibility
    public WeatherAPICaller() {
        this.context = null;
        weatherInfo = new HashMap<>();
    }

    public HashMap<String, String> getWeatherInfo(Double latitude, Double longitude) throws JSONException, InterruptedException {
        weatherInfo.clear();

        // Fetch weather from OpenWeatherMap (same API as PhotoInfoUploader)
        Thread weatherThread = new Thread(() -> {
            try {
                String urlStr = "https://api.openweathermap.org/data/2.5/weather?lat=" + latitude
                        + "&lon=" + longitude + "&appid=" + API_KEY + "&units=metric";

                URL url = new URL(urlStr);
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("GET");
                connection.setConnectTimeout(10000);
                connection.setReadTimeout(10000);

                int responseCode = connection.getResponseCode();
                if (responseCode == HttpURLConnection.HTTP_OK) {
                    BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
                    StringBuilder response = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        response.append(line);
                    }
                    reader.close();

                    // Parse JSON response
                    JSONObject json = new JSONObject(response.toString());

                    // Weather condition
                    String weather = json.getJSONArray("weather").getJSONObject(0).getString("main");
                    weatherInfo.put("weather", weather);

                    // Temperature
                    JSONObject main = json.getJSONObject("main");
                    weatherInfo.put("temperature", String.valueOf(main.getDouble("temp")));
                    weatherInfo.put("humidity", String.valueOf(main.getInt("humidity")));
                    weatherInfo.put("pressure", String.valueOf(main.getInt("pressure")));

                    // Wind
                    JSONObject wind = json.getJSONObject("wind");
                    double windSpeed = wind.getDouble("speed");
                    int windDeg = wind.optInt("deg", 0);
                    double gustSpeed = wind.optDouble("gust", windSpeed);

                    // Convert wind degrees to direction text (same as PhotoInfoUploader)
                    String windDirection = convertDegreesToDirection(windDeg);
                    String gustDirection = convertDegreesToDirection(windDeg);

                    weatherInfo.put("windSpeed", String.valueOf(windSpeed));
                    weatherInfo.put("windDirection", windDirection);       // "East" not "100"
                    weatherInfo.put("gustSpeed", String.valueOf(gustSpeed));
                    weatherInfo.put("gustDirection", gustDirection);

                    // Precipitation
                    JSONObject rain = json.optJSONObject("rain");
                    String precipitation = "0.0";
                    if (rain != null) {
                        precipitation = String.valueOf(rain.optDouble("1h", 0.0));
                    }
                    weatherInfo.put("dayRain", precipitation);

                    // Station name: Use reverse geocoding to get "City - District"
                    // (same format as PhotoInfoUploader)
                    String stationName = getStationName(latitude, longitude);
                    weatherInfo.put("stationName", stationName);

                    Log.d(TAG, "✅ Weather fetched successfully");
                    Log.d(TAG, "   Station: " + stationName);
                    Log.d(TAG, "   Weather: " + weather);
                    Log.d(TAG, "   Wind Direction: " + windDirection);
                    Log.d(TAG, "   Temperature: " + weatherInfo.get("temperature"));

                } else {
                    Log.e(TAG, "❌ API returned error code: " + responseCode);
                }

                connection.disconnect();

            } catch (Exception e) {
                Log.e(TAG, "❌ Error fetching weather: " + e.getMessage());
                e.printStackTrace();
            }
        });

        weatherThread.start();
        weatherThread.join(15000); // Wait max 15 seconds

        return weatherInfo;
    }

    /**
     * Get station name using reverse geocoding
     * Returns "City - District" format for precise matching
     */
    private String getStationName(double latitude, double longitude) {
        if (context == null) {
            return "Unknown";
        }

        try {
            Geocoder geocoder = new Geocoder(context, Locale.getDefault());
            List<Address> addresses = geocoder.getFromLocation(latitude, longitude, 1);

            if (addresses != null && !addresses.isEmpty()) {
                Address address = addresses.get(0);

                // Get city from adminArea (works for Taiwan)
                String adminArea = address.getAdminArea();
                String city = null;
                if (adminArea != null && !adminArea.isEmpty()) {
                    city = adminArea
                            .replace(" City", "")
                            .replace(" County", "")
                            .replace("市", "")
                            .replace("縣", "")
                            .trim();
                }
                if (city == null || city.isEmpty()) {
                    city = address.getLocality();
                }
                if (city == null || city.isEmpty()) {
                    city = "Unknown";
                }

                // Get district from subAdminArea
                String district = address.getSubAdminArea();

                // Return "City - District" if district available
                if (district != null && !district.isEmpty() && !district.equals(city)) {
                    return city + " - " + district;
                }
                return city;
            }
        } catch (IOException e) {
            Log.e(TAG, "Geocoding error: " + e.getMessage());
        }

        return "Unknown";
    }

    /**
     * Convert wind degrees (0-360) to 16-point compass direction text
     * Must match PhotoInfoUploader's output EXACTLY
     * Format: "North", "North-Northeast", "Northeast", "East-Northeast",
     *         "East", "East-Southeast", "Southeast", "South-Southeast",
     *         "South", "South-Southwest", "Southwest", "West-Southwest",
     *         "West", "West-Northwest", "Northwest", "North-Northwest"
     */
    private String convertDegreesToDirection(int degrees) {
        degrees = degrees % 360;
        if (degrees < 0) degrees += 360;

        String[] directions = {
                "North",            // 0
                "North-Northeast",  // 22.5
                "Northeast",        // 45
                "East-Northeast",   // 67.5
                "East",             // 90
                "East-Southeast",   // 112.5
                "Southeast",        // 135
                "South-Southeast",  // 157.5
                "South",            // 180
                "South-Southwest",  // 202.5
                "Southwest",        // 225
                "West-Southwest",   // 247.5
                "West",             // 270
                "West-Northwest",   // 292.5
                "Northwest",        // 315
                "North-Northwest"   // 337.5
        };

        int index = (int) Math.round(degrees / 22.5) % 16;
        return directions[index];
    }
}