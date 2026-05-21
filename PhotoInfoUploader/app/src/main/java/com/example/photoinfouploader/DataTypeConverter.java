package com.example.photoinfouploader;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import androidx.exifinterface.media.ExifInterface;
import android.util.Base64;
import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class DataTypeConverter {

    private static final String TAG = "DataTypeConverter";
    private final EXIFReader exifReader;

    public DataTypeConverter() {
        exifReader = new EXIFReader();
    }

    public String photoToBase64(String photoPath) {
        try (FileInputStream fileInputStream = new FileInputStream(photoPath);
             ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream()) {

            byte[] buffer = new byte[1024];
            int bytesRead;
            while ((bytesRead = fileInputStream.read(buffer)) != -1) {
                byteArrayOutputStream.write(buffer, 0, bytesRead);
            }

            return Base64.encodeToString(byteArrayOutputStream.toByteArray(), Base64.DEFAULT);
        } catch (IOException e) {
            Log.e(TAG, "Error converting photo to Base64", e);
            return "None";
        }
    }
    /**
     * EXIF TAG_WHITE_BALANCE only returns:
     *   0 = Auto
     *   1 = Manual
     * It does NOT return specific modes (Daylight, Fluorescent, etc.)
     *
     * So we detect the lighting condition from weather + capture time + camera settings.
     *
     * Must match CameralessPhotography menu dropdown exactly:
     *   "Auto", "Daylight", "Fluorescent", "Incandescent", "Cloudy", "Twilight", "Shade"
     */
    private String detectWhiteBalanceFromContext(String weatherCondition, String dateTimeStr, int iso, float exposureTime) {
        // Parse hour from datetime string (format: "yyyy-MM-dd HH:mm:ss" or "yyyy:MM:dd HH:mm:ss")
        int hour = -1;
        try {
            if (dateTimeStr != null) {
                String timePart = dateTimeStr;
                if (dateTimeStr.contains(" ")) {
                    timePart = dateTimeStr.split(" ")[1];
                }
                hour = Integer.parseInt(timePart.split(":")[0]);
            }
        } catch (Exception e) {
            hour = -1;
        }

        // Detect indoor vs outdoor using ISO and exposure
        // High ISO (>800) + long exposure (>0.05s) = likely indoor or very dark
        boolean likelyIndoor = (iso > 800 && exposureTime > 0.05f);

        if (hour >= 0) {
            // Night time: 18:00 - 05:59
            if (hour >= 18 || hour < 6) {
                if (likelyIndoor) {
                    // Indoor at night: high ISO suggests artificial lighting
                    if (iso > 1600) {
                        return "Incandescent";  // Very high ISO = warm indoor light
                    }
                    return "Fluorescent";  // Moderate high ISO = office/fluorescent light
                }
                return "Twilight";  // Outdoor at night
            }

            // Early morning / late evening: 6:00-7:59 or 17:00-17:59
            if (hour < 8 || hour == 17) {
                return "Shade";
            }
        }

        // Daytime (8:00 - 16:59): check weather condition
        if (likelyIndoor) {
            return "Fluorescent";  // Indoor during day
        }

        if (weatherCondition != null) {
            String weather = weatherCondition.toLowerCase();
            if (weather.contains("cloud") || weather.contains("overcast")) {
                return "Cloudy";
            }
            if (weather.contains("rain") || weather.contains("drizzle") || weather.contains("mist") || weather.contains("fog")) {
                return "Shade";
            }
            if (weather.contains("clear") || weather.contains("sun")) {
                return "Daylight";
            }
        }

        // Default
        return "Daylight";
    }
    public Map<String, Object> getUploadMap4Firebase(String photoPath, JSONObject weatherObject, JSONObject orientationObject, String locationProvider) {
        Map<String, Object> uploadMap = new HashMap<>();
        try {
            exifReader.setPhotoPath(photoPath);
            HashMap<String, String> exifInfo = exifReader.getEXIF();

            uploadMap.put("orientation", safeParseInt(exifInfo.get("exifOrient"), 1));
            uploadMap.put("datetime", formatDateTime(exifInfo.get("exifDatetime")));
            uploadMap.put("maker", exifInfo.get("exifMaker"));
            uploadMap.put("model", exifInfo.get("exifModel"));
            uploadMap.put("flash", safeParseInt(exifInfo.get("exifFlash"), 0));
            uploadMap.put("image_length", safeParseInt(exifInfo.get("exifImgLen"), 0));
            uploadMap.put("image_width", safeParseInt(exifInfo.get("exifImgWid"), 0));
            uploadMap.put("exposure_time", safeParseFloat(exifInfo.get("exifExposure"), 0.0f));
            uploadMap.put("aperture", safeParseFloat(exifInfo.get("exifAperture"), 0.0f));
            uploadMap.put("iso", safeParseInt(exifInfo.get("exifISO"), 0));

            // White balance: detect from weather + time + camera settings
            // (EXIF TAG_WHITE_BALANCE only returns 0=Auto or 1=Manual, not specific modes)
            String weatherCondition = (weatherObject != null) ? weatherObject.optString("weather", "") : "";
            String captureDateTime = exifInfo.get("exifDatetime");
            int isoValue = safeParseInt(exifInfo.get("exifISO"), 0);
            float exposureValue = safeParseFloat(exifInfo.get("exifExposure"), 0.0f);
            String whiteBalanceText = detectWhiteBalanceFromContext(weatherCondition, captureDateTime, isoValue, exposureValue);
            uploadMap.put("white_balance", whiteBalanceText);
            uploadMap.put("focal_length", getFocalLenFlt(exifInfo.get("exifFocalLen")));

            float longitude = safeParseFloat(exifInfo.get("exifGPSLONG"), 0.0f);
            uploadMap.put("longitude", longitude);
            uploadMap.put("longitude_round", String.format(Locale.US, "%.3f", longitude));
            uploadMap.put("latitude", safeParseFloat(exifInfo.get("exifGPSLAT"), 0.0f));
            uploadMap.put("location_provider", locationProvider);

            if (weatherObject != null) {
                // ✅ Normalize apostrophe in station_name (Da'an → Da'an)
                String stationName = weatherObject.optString("locationName", "Unknown");
                stationName = stationName
                        .replace('\u2019', '\'')  // Right single quote → apostrophe
                        .replace('\u2018', '\'')  // Left single quote → apostrophe
                        .replace('\u02BC', '\''); // Modifier letter → apostrophe
                uploadMap.put("station_name", stationName);
                uploadMap.put("wind_direction", weatherObject.optString("windDirection", "N/A"));
                uploadMap.put("wind_speed", weatherObject.optString("windSpeed", "0.0"));

                String tempStr = weatherObject.optString("temperature", "0.0");
                uploadMap.put("temperature", formatDecimal(tempStr, 1));

                uploadMap.put("humidity", weatherObject.optString("humidity", "0"));
                uploadMap.put("pressure", weatherObject.optString("pressure", "0.0"));
                uploadMap.put("precipitation", weatherObject.optString("dayRain", "0.0"));
                uploadMap.put("gust_speed", weatherObject.optString("gustSpeed", "N/A"));
                uploadMap.put("gust_direction", weatherObject.optString("gustDirection", "N/A"));
                uploadMap.put("weather", weatherObject.optString("weather", "Unknown"));
            }

            if (orientationObject != null) {
                uploadMap.put("pitch", orientationObject.optDouble("pitch", 0.0));
                uploadMap.put("roll", orientationObject.optDouble("roll", 0.0));
                uploadMap.put("yaw", orientationObject.optDouble("yaw", 0.0));
            }

        } catch (Exception e) {
            Log.e(TAG, "Error creating Firebase upload map", e);
        }
        return uploadMap;
    }

    private String formatDecimal(String value, int places) {
        if (value == null || value.equals("N/A") || value.equals("Unknown")) return value;
        try {
            float f = Float.parseFloat(value);
            return String.format(Locale.US, "%." + places + "f", f);
        } catch (Exception e) {
            return value;
        }
    }

    public void photoCompress(String photoPath) {
        // Step 1: Read and store all EXIF attributes BEFORE compression
        Map<String, String> attributesMap = new HashMap<>();
        try {
            ExifInterface oldExif = new ExifInterface(photoPath);
            String[] tagsToPreserve = {
                    ExifInterface.TAG_ORIENTATION, ExifInterface.TAG_DATETIME, ExifInterface.TAG_MAKE,
                    ExifInterface.TAG_MODEL, ExifInterface.TAG_FLASH, ExifInterface.TAG_IMAGE_LENGTH,
                    ExifInterface.TAG_IMAGE_WIDTH, ExifInterface.TAG_EXPOSURE_TIME, ExifInterface.TAG_F_NUMBER,
                    ExifInterface.TAG_ISO_SPEED_RATINGS, ExifInterface.TAG_WHITE_BALANCE, ExifInterface.TAG_FOCAL_LENGTH,
                    ExifInterface.TAG_GPS_LATITUDE, ExifInterface.TAG_GPS_LATITUDE_REF,
                    ExifInterface.TAG_GPS_LONGITUDE, ExifInterface.TAG_GPS_LONGITUDE_REF
            };
            for (String tag : tagsToPreserve) {
                String value = oldExif.getAttribute(tag);
                if (value != null) {
                    attributesMap.put(tag, value);
                }
            }
        } catch (IOException e) {
            Log.e(TAG, "Error reading EXIF before compression", e);
        }

        // Step 2: Compress the image
        Bitmap bitmap = BitmapFactory.decodeFile(photoPath);
        if (bitmap == null) return;

        int targetHeight = 1080;
        int targetWidth = (int) ((float) bitmap.getWidth() / bitmap.getHeight() * targetHeight);
        Bitmap resizedBitmap = Bitmap.createScaledBitmap(bitmap, targetWidth, targetHeight, true);

        try (FileOutputStream outputStream = new FileOutputStream(photoPath)) {
            resizedBitmap.compress(Bitmap.CompressFormat.JPEG, 90, outputStream);
        } catch (IOException e) {
            Log.e(TAG, "Error saving compressed bitmap", e);
        }

        // Step 3: Write the stored EXIF attributes back to the new compressed file
        try {
            ExifInterface newExif = new ExifInterface(photoPath);
            for (Map.Entry<String, String> entry : attributesMap.entrySet()) {
                newExif.setAttribute(entry.getKey(), entry.getValue());
            }
            newExif.saveAttributes();
        } catch (IOException e) {
            Log.e(TAG, "Error restoring EXIF after compression", e);
        }

        bitmap.recycle();
        resizedBitmap.recycle();
    }

    private float getFocalLenFlt(String focalLenStr) {
        if (focalLenStr == null || !focalLenStr.contains("/")) return 0.0f;
        try {
            int idx_slash = focalLenStr.indexOf("/");
            float up = Float.parseFloat(focalLenStr.substring(0, idx_slash));
            float btm = Float.parseFloat(focalLenStr.substring(idx_slash + 1));
            return btm != 0 ? up / btm : 0.0f;
        } catch (NumberFormatException e) {
            return 0.0f;
        }
    }

    private String formatDateTime(String dateTime) {
        if (dateTime == null) return null;
        try {
            DateFormat inputFormat = new SimpleDateFormat("yyyy:MM:dd HH:mm:ss", Locale.US);
            Date date = inputFormat.parse(dateTime);
            if (date == null) return null;

            DateFormat outputFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US);
            return outputFormat.format(date);
        } catch (ParseException e) {
            return dateTime;
        }
    }

    private int safeParseInt(String s, int defaultValue) {
        if (s == null) return defaultValue;
        try {
            return Integer.parseInt(s.trim());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    private float safeParseFloat(String s, float defaultValue) {
        if (s == null) return defaultValue;
        try {
            return Float.parseFloat(s.trim());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }
}