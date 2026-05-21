package com.example.photoinfouploader;

import android.util.Log;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashMap;
import java.util.Locale;

public class WeatherAPICaller {
    private static final String TAG = "WeatherAPICaller";

    // Your OpenWeatherMap API Key
    private final String API_KEY = "114cc9fb42f75ec87425c7ec0f528998";

    private final Double latitude;
    private final Double longitude;
    private final String time;
    private final HashMap<String, String> weatherInfo = new HashMap<>();

    public interface WeatherCallback {
        void onDataReady(HashMap<String, String> info);
    }

    public WeatherAPICaller(Double lat, Double lon, String time) {
        this.latitude = lat != null ? lat : 0.0;
        this.longitude = lon != null ? lon : 0.0;
        this.time = time;
    }

    public void fetchWeather(WeatherCallback callback) {
        new Thread(() -> {
            try {
                Log.d(TAG, "========================================");
                Log.d(TAG, "Fetching weather for:");
                Log.d(TAG, "  Latitude:  " + latitude);
                Log.d(TAG, "  Longitude: " + longitude);
                Log.d(TAG, "========================================");

                // Validate Coordinates
                if (latitude == 0.0 && longitude == 0.0) {
                    Log.w(TAG, "⚠️ Coordinates are (0,0) - location not yet determined");
                    weatherInfo.put("weather", "Location unavailable");
                    callback.onDataReady(weatherInfo);
                    return;
                }

                // Build URL
                String url = String.format(
                        Locale.US,
                        "https://api.openweathermap.org/data/2.5/weather?lat=%.4f&lon=%.4f&appid=%s&units=metric",
                        latitude, longitude, API_KEY
                );
                Log.d(TAG, "URL: " + url.substring(0, url.indexOf("appid")) + "[KEY]");

                // Make HTTP Request
                HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
                conn.setConnectTimeout(8000);
                conn.setReadTimeout(8000);
                conn.setRequestMethod("GET");

                // Check Response Code
                int status = conn.getResponseCode();
                Log.d(TAG, "HTTP Response Code: " + status);

                if (status != 200) {
                    String errorMsg = readStream(conn.getErrorStream());
                    Log.e(TAG, "❌ API Error " + status);
                    Log.e(TAG, "Response: " + errorMsg);
                    weatherInfo.put("weather", "HTTP Error " + status);
                    callback.onDataReady(weatherInfo);
                    return;
                }

                // Read Response
                String jsonString = readStream(conn.getInputStream());
                Log.d(TAG, "Response length: " + jsonString.length() + " chars");

                // Parse JSON
                JSONObject json = new JSONObject(jsonString);
                Log.d(TAG, "✅ JSON parsed successfully");

                // Extract Data
                String city = json.getString("name");
                JSONObject main = json.getJSONObject("main");
                JSONObject wind = json.getJSONObject("wind");

                double temp = main.getDouble("temp");
                int humidity = main.getInt("humidity");
                int pressure = main.getInt("pressure");
                double windSpeed = wind.getDouble("speed");
                double windDeg = wind.optDouble("deg", 0);

                String weather = json.getJSONArray("weather").getJSONObject(0).getString("main");
                double rain = json.has("rain") ? json.getJSONObject("rain").optDouble("1h", 0) : 0;

                // ✨ CONVERT TO FULL NAMES ✨
                String windDirectionFull = getWindDirectionFull(windDeg);     // "North" instead of "N"
                String windDirectionShort = getWindDirectionShort(windDeg);   // "N" for short version
                String gustDirectionFull = getWindDirectionFull(windDeg);     // "North" for gust
                String gustDirectionShort = getWindDirectionShort(windDeg);   // "N" for short

                // Store Results - WITH BOTH FORMATS!
                weatherInfo.put("locationName", city + " Station");
                weatherInfo.put("temperature", String.format(Locale.US, "%.1f", temp));
                weatherInfo.put("humidity", String.valueOf(humidity));
                weatherInfo.put("pressure", String.valueOf(pressure));
                weatherInfo.put("windSpeed", String.format(Locale.US, "%.1f", windSpeed));

                // ✨ ADD BOTH VERSIONS ✨
                weatherInfo.put("windDirection", windDirectionFull);           // "North"
                weatherInfo.put("windDirectionShort", windDirectionShort);     // "N"
                weatherInfo.put("windDirectionFull", windDirectionFull);       // "North" (explicit)
                weatherInfo.put("gustDirection", gustDirectionFull);           // "North"
                weatherInfo.put("gustDirectionShort", gustDirectionShort);     // "N"
                weatherInfo.put("gustDirectionFull", gustDirectionFull);       // "North" (explicit)

                weatherInfo.put("weather", weather);
                weatherInfo.put("dayRain", String.format(Locale.US, "%.1f", rain));
                weatherInfo.put("gustSpeed", String.format(Locale.US, "%.1f", windSpeed));

                // Log Success
                Log.d(TAG, "========================================");
                Log.d(TAG, "✅ Weather Fetch Successful!");
                Log.d(TAG, "  Location: " + city);
                Log.d(TAG, "  Temperature: " + temp + "°C");
                Log.d(TAG, "  Wind Direction: " + windDirectionFull + " (" + windDirectionShort + ")");
                Log.d(TAG, "  Weather: " + weather);
                Log.d(TAG, "========================================");

            } catch (Exception e) {
                Log.e(TAG, "========================================");
                Log.e(TAG, "❌ Exception occurred!");
                Log.e(TAG, "Error type: " + e.getClass().getSimpleName());
                Log.e(TAG, "Error message: " + e.getMessage());
                Log.e(TAG, "========================================");
                e.printStackTrace();
                weatherInfo.put("weather", "Error: " + e.getClass().getSimpleName());
            }

            callback.onDataReady(weatherInfo);
        }).start();
    }

    /**
     * Helper method to read input stream
     */
    private String readStream(java.io.InputStream is) throws Exception {
        BufferedReader br = new BufferedReader(new InputStreamReader(is));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = br.readLine()) != null) {
            sb.append(line);
        }
        br.close();
        return sb.toString();
    }

    /**
     * ✨ Convert degrees to FULL cardinal direction name
     * Examples: 0° = "North", 90° = "East", 180° = "South", 270° = "West"
     */
    private String getWindDirectionFull(double degrees) {
        if (degrees < 0 || degrees >= 360) return "N/A";

        String[] fullNames = {
                "North",              // 0°
                "North-Northeast",    // 22.5°
                "Northeast",          // 45°
                "East-Northeast",     // 67.5°
                "East",               // 90°
                "East-Southeast",     // 112.5°
                "Southeast",          // 135°
                "South-Southeast",    // 157.5°
                "South",              // 180°
                "South-Southwest",    // 202.5°
                "Southwest",          // 225°
                "West-Southwest",     // 247.5°
                "West",               // 270°
                "West-Northwest",     // 292.5°
                "Northwest",          // 315°
                "North-Northwest"     // 337.5°
        };

        int index = (int) Math.round((degrees % 360) / 22.5) % 16;
        return fullNames[index];
    }

    /**
     * Convert degrees to SHORT cardinal direction (single letter)
     * Examples: 0° = "N", 90° = "E", 180° = "S", 270° = "W"
     */
    private String getWindDirectionShort(double degrees) {
        if (degrees < 0 || degrees >= 360) return "N/A";
        String[] dirs = {"N", "NNE", "NE", "ENE", "E", "ESE", "SE", "SSE",
                "S", "SSW", "SW", "WSW", "W", "WNW", "NW", "NNW"};
        int index = (int) Math.round((degrees % 360) / 22.5) % 16;
        return dirs[index];
    }
}