package com.example.cameralessphotography;

import android.util.Log;

import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class DatabaseHelper {
    private FirebaseFirestore db;
    private CollectionReference collection;
    private static final String TAG = "DatabaseHelper";

    public DatabaseHelper() {
        db = FirebaseFirestore.getInstance();
        collection = db.collection("cameraless_photography_db");
    }

    // ═══════════════════════════════════════════════════════════════
    //  EXISTING: GPS-based search (original shot button behavior)
    // ═══════════════════════════════════════════════════════════════

    public void getPhotoInfoList(Map<String, Object> photograph_parameters,
                                 OnPhotoInfoListRetrievedListener listener) {
        try {
            String weather = (String) photograph_parameters.get("weather");
            String stationName = normalizeText((String) photograph_parameters.get("station_name"));
            String datetime = (String) photograph_parameters.get("datetime");
            Double latitude = (Double) photograph_parameters.get("latitude");
            Double longitude = (Double) photograph_parameters.get("longitude");

            float userYaw = toFloat(photograph_parameters.get("yaw"));
            float userRoll = toFloat(photograph_parameters.get("roll"));
            float userPitch = toFloat(photograph_parameters.get("pitch"));
            float userExposure = toFloat(photograph_parameters.get("exposure"));

            int season = getSeason(datetime);
            List<String> longiRoundList = getLongiRoundList(longitude);
            List<Long> orientationList = getOrientationList(userPitch);

            logQuery("GPS-SEARCH", stationName, weather, latitude, longiRoundList, orientationList);

            Query query = collection
                    .whereEqualTo("station_name", stationName)
                    .whereEqualTo("weather", weather)
                    .whereIn("longitude_round", longiRoundList)
                    .whereGreaterThan("latitude", latitude - 0.003)
                    .whereLessThan("latitude", latitude + 0.003)
                    .whereIn("orientation", orientationList)
                    .limit(50);

            executeAndScore(query, userYaw, userRoll, userPitch, userExposure, season, -1, listener);

        } catch (Exception e) {
            Log.e(TAG, "Error in getPhotoInfoList", e);
            listener.onFailure(e);
        }
    }

    // ═══════════════════════════════════════════════════════════════
    //  NEW: Flexible text-based search (SearchActivity)
    // ═══════════════════════════════════════════════════════════════

    /**
     * Flexible search that doesn't require GPS.
     * Searches by location text (fuzzy match on station_name) + user preferences.
     *
     * Search params:
     *   location_text (String) - city/district/landmark name
     *   weather (String, optional) - desired weather condition
     *   yaw (float) - desired direction
     *   pitch (float) - desired elevation angle
     *   roll (float) - default 0
     *   exposure (float) - from camera settings
     *   time_slot (int, optional) - 0=morning, 1=afternoon, 2=evening, 3=night
     *   season (int, optional) - 0=spring, 1=summer, 2=autumn, 3=winter
     */
    public void flexibleSearch(Map<String, Object> searchParams,
                               OnPhotoInfoListRetrievedListener listener) {
        try {
            String locationText = normalizeText((String) searchParams.get("location_text"));
            String weather = (String) searchParams.get("weather");
            float userYaw = toFloat(searchParams.get("yaw"));
            float userPitch = toFloat(searchParams.get("pitch"));
            float userRoll = toFloat(searchParams.get("roll"));
            float userExposure = toFloat(searchParams.get("exposure"));
            int timeSlot = searchParams.containsKey("time_slot") ? (int) searchParams.get("time_slot") : -1;
            int season = searchParams.containsKey("season") ? (int) searchParams.get("season") : -1;

            Log.d(TAG, "=== FLEXIBLE SEARCH ===");
            Log.d(TAG, "  location_text: " + locationText);
            Log.d(TAG, "  weather: " + (weather != null ? weather : "Any"));
            Log.d(TAG, "  yaw=" + userYaw + ", pitch=" + userPitch);
            Log.d(TAG, "  timeSlot=" + timeSlot + ", season=" + season);

            List<Long> orientationList = getOrientationList(userPitch);

            // Step 1: Build query with station_name containing the location text
            // Firestore doesn't support "LIKE" or "CONTAINS", so we use range query
            // for prefix matching: station_name >= "text" AND station_name < "text\uf8ff"
            //
            // Strategy: Try exact match first, then prefix match, then broad search

            // First attempt: exact station_name match
            Query query;
            if (weather != null) {
                query = collection
                        .whereEqualTo("station_name", locationText)
                        .whereEqualTo("weather", weather)
                        .whereIn("orientation", orientationList)
                        .limit(100);
            } else {
                query = collection
                        .whereEqualTo("station_name", locationText)
                        .whereIn("orientation", orientationList)
                        .limit(100);
            }

            Log.d(TAG, "Attempt 1: exact station_name match");

            query.get()
                    .addOnSuccessListener(snapshots -> {
                        if (snapshots.size() > 0) {
                            Log.d(TAG, "Exact match found: " + snapshots.size() + " docs");
                            scoreAndReturn(snapshots, userYaw, userRoll, userPitch,
                                    userExposure, season, timeSlot, listener);
                        } else {
                            // Attempt 2: prefix match using Firestore range query
                            Log.d(TAG, "No exact match, trying prefix search...");
                            prefixSearch(locationText, weather, orientationList,
                                    userYaw, userRoll, userPitch, userExposure,
                                    season, timeSlot, listener);
                        }
                    })
                    .addOnFailureListener(e -> {
                        Log.e(TAG, "Exact search failed, trying prefix...", e);
                        prefixSearch(locationText, weather, orientationList,
                                userYaw, userRoll, userPitch, userExposure,
                                season, timeSlot, listener);
                    });

        } catch (Exception e) {
            Log.e(TAG, "Error in flexibleSearch", e);
            listener.onFailure(e);
        }
    }

    /**
     * Prefix search: finds station_name starting with the location text.
     * Uses Firestore range query: >= "text" and <= "text\uf8ff"
     */
    private void prefixSearch(String locationText, String weather,
                              List<Long> orientationList,
                              float userYaw, float userRoll, float userPitch,
                              float userExposure, int season, int timeSlot,
                              OnPhotoInfoListRetrievedListener listener) {

        String startAt = locationText;
        String endAt = locationText + "\uf8ff";

        Query query;
        if (weather != null) {
            query = collection
                    .whereGreaterThanOrEqualTo("station_name", startAt)
                    .whereLessThanOrEqualTo("station_name", endAt)
                    .whereEqualTo("weather", weather)
                    .limit(100);
        } else {
            query = collection
                    .whereGreaterThanOrEqualTo("station_name", startAt)
                    .whereLessThanOrEqualTo("station_name", endAt)
                    .limit(100);
        }

        Log.d(TAG, "Prefix search: [" + startAt + " ... " + endAt + "]");

        query.get()
                .addOnSuccessListener(snapshots -> {
                    if (snapshots.size() > 0) {
                        Log.d(TAG, "Prefix match found: " + snapshots.size() + " docs");
                        scoreAndReturn(snapshots, userYaw, userRoll, userPitch,
                                userExposure, season, timeSlot, listener);
                    } else {
                        // Attempt 3: contains search — scan station_name for substring
                        Log.d(TAG, "No prefix match, trying contains search...");
                        containsSearch(locationText, weather, orientationList,
                                userYaw, userRoll, userPitch, userExposure,
                                season, timeSlot, listener);
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Prefix search failed", e);
                    listener.onFailure(e);
                });
    }

    /**
     * Contains search: fetches all docs (with weather filter if set),
     * then client-side filters station_name containing the text.
     * Last resort for fuzzy matching.
     */
    private void containsSearch(String locationText, String weather,
                                List<Long> orientationList,
                                float userYaw, float userRoll, float userPitch,
                                float userExposure, int season, int timeSlot,
                                OnPhotoInfoListRetrievedListener listener) {

        String searchLower = locationText.toLowerCase(Locale.ROOT);

        Query query;
        if (weather != null) {
            query = collection.whereEqualTo("weather", weather).limit(500);
        } else {
            query = collection.limit(500);
        }

        query.get()
                .addOnSuccessListener(snapshots -> {
                    List<Map<String, Object>> filtered = new ArrayList<>();
                    List<Float> errors = new ArrayList<>();

                    float wExp = 0.05f, wYaw = 0.80f, wRoll = 0.10f, wPitch = 0.05f;

                    for (QueryDocumentSnapshot doc : snapshots) {
                        try {
                            Map<String, Object> data = doc.getData();
                            String stName = (String) data.get("station_name");
                            if (stName == null) continue;

                            // Client-side fuzzy match
                            if (!stName.toLowerCase(Locale.ROOT).contains(searchLower)) continue;

                            // Season filter
                            if (season >= 0) {
                                String dt = (String) data.get("datetime");
                                int dataSeason = getSeason(dt);
                                if (dataSeason >= 0 && dataSeason != season) continue;
                            }

                            // Time slot filter
                            if (timeSlot >= 0) {
                                String dt = (String) data.get("datetime");
                                int dataSlot = getTimeSlot(dt);
                                if (dataSlot >= 0 && dataSlot != timeSlot) continue;
                            }

                            // Score
                            float error = calcError(data, userExposure, userYaw, userRoll, userPitch,
                                    wExp, wYaw, wRoll, wPitch);
                            insertSorted(filtered, errors, data, error, 10);

                        } catch (Exception e) {
                            Log.e(TAG, "Error processing doc in contains search", e);
                        }
                    }

                    Log.d(TAG, "Contains search results: " + filtered.size());
                    listener.onPhotoInfoListRetrieved(filtered);
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Contains search failed", e);
                    listener.onFailure(e);
                });
    }

    // ═══════════════════════════════════════════════════════════════
    //  SHARED: Scoring and sorting logic
    // ═══════════════════════════════════════════════════════════════

    private void executeAndScore(Query query, float userYaw, float userRoll, float userPitch,
                                 float userExposure, int season, int timeSlot,
                                 OnPhotoInfoListRetrievedListener listener) {
        float wExp = 0.05f, wYaw = 0.80f, wRoll = 0.10f, wPitch = 0.05f;

        query.get()
                .addOnSuccessListener(snapshots -> {
                    scoreAndReturn(snapshots, userYaw, userRoll, userPitch,
                            userExposure, season, timeSlot, listener);
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Query failed: " + e.getMessage(), e);
                    listener.onFailure(e);
                });
    }

    private void scoreAndReturn(Iterable<QueryDocumentSnapshot> snapshots,
                                float userYaw, float userRoll, float userPitch,
                                float userExposure, int season, int timeSlot,
                                OnPhotoInfoListRetrievedListener listener) {
        List<Map<String, Object>> results = new ArrayList<>();
        List<Float> errors = new ArrayList<>();

        float wExp = 0.05f, wYaw = 0.80f, wRoll = 0.10f, wPitch = 0.05f;

        for (QueryDocumentSnapshot doc : snapshots) {
            try {
                Map<String, Object> data = doc.getData();

                // Season filter
                if (season >= 0) {
                    String dt = (String) data.get("datetime");
                    int ds = getSeason(dt);
                    if (ds >= 0 && ds != season) continue;
                }

                // Time slot filter
                if (timeSlot >= 0) {
                    String dt = (String) data.get("datetime");
                    int ts = getTimeSlot(dt);
                    if (ts >= 0 && ts != timeSlot) continue;
                }

                float error = calcError(data, userExposure, userYaw, userRoll, userPitch,
                        wExp, wYaw, wRoll, wPitch);
                insertSorted(results, errors, data, error, 10);

            } catch (Exception e) {
                Log.e(TAG, "Error scoring document", e);
            }
        }

        Log.d(TAG, "Scored results: " + results.size());
        listener.onPhotoInfoListRetrieved(results);
    }

    private float calcError(Map<String, Object> data, float userExp, float userYaw,
                            float userRoll, float userPitch,
                            float wExp, float wYaw, float wRoll, float wPitch) {
        float errExp = Math.abs(toFloat(data.get("exposure_time")) - userExp) * wExp;

        float errYaw = Math.abs(toFloat(data.get("yaw")) - userYaw);
        if (errYaw > 180) errYaw = 360 - errYaw;
        errYaw *= wYaw;

        float errRoll = Math.abs(toFloat(data.get("roll")) - userRoll);
        if (errRoll > 180) errRoll = 360 - errRoll;
        errRoll *= wRoll;

        float errPitch = Math.abs(toFloat(data.get("pitch")) - userPitch) * wPitch;

        return errExp + errYaw + errRoll + errPitch;
    }

    private void insertSorted(List<Map<String, Object>> results, List<Float> errors,
                              Map<String, Object> data, float error, int maxResults) {
        int idx = 0;
        for (; idx < errors.size(); idx++) {
            if (error < errors.get(idx)) break;
        }
        if (idx > maxResults) return;
        errors.add(idx, error);
        results.add(idx, data);
        if (errors.size() > maxResults) {
            errors.remove(maxResults);
            results.remove(maxResults);
        }
    }

    // ═══════════════════════════════════════════════════════════════
    //  SHARED: Utility methods
    // ═══════════════════════════════════════════════════════════════

    private int getTimeSlot(String datetime) {
        if (datetime == null) return -1;
        try {
            String timePart = datetime.split(" ")[1];
            int hour = Integer.parseInt(timePart.split(":")[0]);
            if (hour >= 6 && hour < 12) return 0;
            if (hour >= 12 && hour < 17) return 1;
            if (hour >= 17 && hour < 21) return 2;
            return 3;
        } catch (Exception e) { return -1; }
    }

    private int getSeason(String datetime) {
        if (datetime == null) return -1;
        try {
            int month = Integer.parseInt(datetime.split("-")[1].trim());
            if (month >= 3 && month <= 5) return 0;
            if (month >= 6 && month <= 8) return 1;
            if (month >= 9 && month <= 11) return 2;
            return 3;
        } catch (Exception e) { return -1; }
    }

    private List<String> getLongiRoundList(double longitude) {
        List<String> list = new ArrayList<>();
        double lonVal = Double.parseDouble(String.format(Locale.US, "%.3f", longitude));
        for (int i = -3; i <= 3; i++) {
            list.add(String.format(Locale.US, "%.3f", lonVal + i * 0.001));
        }
        return list;
    }

    private List<Long> getOrientationList(float pitch) {
        List<Long> list = new ArrayList<>();
        if (Math.abs(pitch) < 45) {
            list.add(1L); list.add(3L); list.add(6L); list.add(8L);
        } else if (pitch < -45) {
            list.add(5L); list.add(1L); list.add(6L);
        } else {
            list.add(5L); list.add(1L); list.add(6L);
        }
        return list;
    }

    private String normalizeText(String text) {
        if (text == null) return null;
        return text
                .replace('\u2019', '\'').replace('\u2018', '\'').replace('\u02BC', '\'')
                .replace('\u2013', '-').replace('\u2014', '-').replace('\u2012', '-');
    }

    private float toFloat(Object obj) {
        return (obj instanceof Number) ? ((Number) obj).floatValue() : 0f;
    }

    // ═══════════════════════════════════════════════════════════════
    //  Upload (unchanged)
    // ═══════════════════════════════════════════════════════════════

    public void uploadShotResult(Map<String, Object> shotResult, OnUploadCompleteListener listener) {
        try {
            db.collection("experiments").add(shotResult)
                    .addOnSuccessListener(ref -> {
                        Log.d(TAG, "Shot result uploaded successfully");
                        listener.onComplete(true);
                    })
                    .addOnFailureListener(e -> {
                        Log.e(TAG, "Failed to upload shot result", e);
                        listener.onComplete(false);
                    });
        } catch (Exception e) {
            Log.e(TAG, "Error uploading shot result", e);
            listener.onComplete(false);
        }
    }

    public interface OnPhotoInfoListRetrievedListener {
        void onPhotoInfoListRetrieved(List<Map<String, Object>> photoInfoList);
        void onFailure(Exception e);
    }

    public interface OnUploadCompleteListener {
        void onComplete(boolean success);
    }

    private void logQuery(String tag, String station, String weather, Double lat,
                          List<String> longiList, List<Long> orientList) {
        Log.d(TAG, "=== " + tag + " ===");
        Log.d(TAG, "  station: " + station + ", weather: " + weather);
        Log.d(TAG, "  lat: " + lat + ", longi_round: " + longiList);
        Log.d(TAG, "  orientation: " + orientList);
    }
}