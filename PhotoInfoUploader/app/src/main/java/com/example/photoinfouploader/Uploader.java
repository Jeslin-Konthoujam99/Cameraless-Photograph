package com.example.photoinfouploader;

import android.content.Context;
import android.net.Uri;
import android.util.Log;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageMetadata;
import com.google.firebase.storage.StorageReference;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.File;
import java.util.HashMap;
import java.util.Map;

public class Uploader {
    private static final String TAG = "Uploader";
    private final StorageTools storageTools;
    private final DataTypeConverter dataTypeConverter;
    private final FirebaseStorage storage;
    private final FirebaseFirestore db;

    public Uploader() {
        storageTools = new StorageTools();
        dataTypeConverter = new DataTypeConverter();
        storage = FirebaseStorage.getInstance();
        db = FirebaseFirestore.getInstance();
    }

    public void uploadPhoto2Firebase(final Context context, final int albumID, final String photoPath, final OnCompleteListener onCompleteListener) {
        Log.d(TAG, "═══════════════════════════════════════════");
        Log.d(TAG, "🚀 Starting upload for: " + photoPath);
        Log.d(TAG, "═══════════════════════════════════════════");

        try {
            // ✅ Step 1: Check if photo file exists
            File photoFile = new File(photoPath);
            if (!photoFile.exists()) {
                Log.e(TAG, "❌ STEP 1 FAILED: Photo file does NOT exist at: " + photoPath);
                onCompleteListener.onComplete(false);
                return;
            }
            Log.d(TAG, "✅ STEP 1: Photo file exists, size: " + (photoFile.length() / 1024) + " KB");

            // ✅ Step 2: Read photo metadata from JSON
            String albumPath = context.getFilesDir().getAbsolutePath() + "/album_" + albumID;
            String photoPathesFilePath = albumPath + "/photoPathes.json";

            File jsonFile = new File(photoPathesFilePath);
            if (!jsonFile.exists()) {
                Log.e(TAG, "❌ STEP 2 FAILED: JSON metadata file not found at: " + photoPathesFilePath);
                onCompleteListener.onComplete(false);
                return;
            }

            String fileContent = storageTools.readFile(photoPathesFilePath);
            Log.d(TAG, "✅ STEP 2: Read metadata JSON, length: " + fileContent.length() + " chars");

            JSONObject mainObject = new JSONObject(fileContent);
            JSONArray photos = mainObject.optJSONArray("photos");
            if (photos == null) {
                Log.e(TAG, "❌ STEP 3 FAILED: No 'photos' array in JSON");
                onCompleteListener.onComplete(false);
                return;
            }
            Log.d(TAG, "✅ STEP 3: Found " + photos.length() + " photos in JSON");

            // ✅ Step 4: Find matching photo entry
            JSONObject weatherObject = null;
            JSONObject orientationObject = null;
            String locationProvider = "unknown";
            boolean fileFound = false;
            String targetCanonical = photoFile.getCanonicalPath();

            for (int i = 0; i < photos.length(); i++) {
                JSONObject photoObject = photos.getJSONObject(i);
                String pathInJson = photoObject.optString("photo path", "");

                if (new File(pathInJson).getCanonicalPath().equals(targetCanonical)) {
                    weatherObject = photoObject.optJSONObject("weatherInfo");
                    orientationObject = photoObject.optJSONObject("orientation info");
                    locationProvider = photoObject.optString("location_provider", "unknown");
                    fileFound = true;
                    Log.d(TAG, "✅ STEP 4: Found matching photo entry in JSON");
                    break;
                }
            }

            if (!fileFound) {
                Log.w(TAG, "⚠️ STEP 4: Photo not found in JSON, using empty metadata");
                weatherObject = new JSONObject();
                orientationObject = new JSONObject();
            }

            // ✅ Step 5: Convert data for Firebase
            Log.d(TAG, "✅ STEP 5: Converting data for Firebase upload...");
            final Map<String, Object> convertMap = dataTypeConverter.getUploadMap4Firebase(
                    photoPath, weatherObject, orientationObject, locationProvider);

            Log.d(TAG, "   - Temperature: " + convertMap.get("temperature"));
            Log.d(TAG, "   - Latitude: " + convertMap.get("latitude"));
            Log.d(TAG, "   - Weather: " + convertMap.get("weather"));

            // ✅ Step 6: Compress photo
            Log.d(TAG, "✅ STEP 6: Compressing photo...");
            dataTypeConverter.photoCompress(photoPath);
            Log.d(TAG, "   - Compressed size: " + (photoFile.length() / 1024) + " KB");

            // ✅ Step 7: Upload to Firebase Storage
            Log.d(TAG, "✅ STEP 7: Uploading to Firebase Storage...");
            StorageReference storageRef = storage.getReference();
            Uri fileUri = Uri.fromFile(photoFile);
            String fileName = fileUri.getLastPathSegment();
            StorageReference photoRef = storageRef.child("cameraless_photos/" + fileName);

            StorageMetadata.Builder metadataBuilder = new StorageMetadata.Builder()
                    .setContentType("image/jpeg");

            for (Map.Entry<String, Object> entry : convertMap.entrySet()) {
                if (entry.getValue() != null) {
                    metadataBuilder.setCustomMetadata(entry.getKey(), String.valueOf(entry.getValue()));
                }
            }

            photoRef.putFile(fileUri, metadataBuilder.build())
                    .addOnSuccessListener(taskSnapshot -> {
                        Log.d(TAG, "✅ STEP 7A: File uploaded to Storage successfully");

                        // ✅ Step 8: Get download URL
                        photoRef.getDownloadUrl()
                                .addOnSuccessListener(downloadUri -> {
                                    Log.d(TAG, "✅ STEP 8: Got download URL: " + downloadUri.toString());

                                    // ✅ Step 9: Save to Firestore
                                    Map<String, Object> firestoreData = new HashMap<>(convertMap);
                                    firestoreData.put("photo_url", downloadUri.toString());
                                    firestoreData.put("upload_timestamp", new java.util.Date());

                                    Log.d(TAG, "STEP 9: Saving to Firestore");
                                    Log.d(TAG, "  Data fields: " + firestoreData.size());
                                    Log.d(TAG, "  Data: " + firestoreData.toString());

                                    db.collection("cameraless_photography_db")
                                            .add(firestoreData)
                                            .addOnSuccessListener(docRef -> {
                                                Log.d(TAG, "✅ STEP 9 SUCCESS");
                                                Log.d(TAG, "  Document ID: " + docRef.getId());
                                                onCompleteListener.onComplete(true);  // ← IMPORTANT!
                                            })
                                            .addOnFailureListener(e -> {
                                                Log.e(TAG, "❌ STEP 9 FAILED");
                                                Log.e(TAG, "  Firestore error: " + e.getMessage());
                                                Log.e(TAG, "  Error code: " + e.getClass().getSimpleName());
                                                onCompleteListener.onComplete(false);
                                            });
                                })
                                .addOnFailureListener(e -> {
                                    Log.e(TAG, "❌ STEP 8 FAILED: Get download URL error", e);
                                    onCompleteListener.onComplete(false);
                                });
                    })
                    .addOnFailureListener(e -> {
                        Log.e(TAG, "❌ STEP 7B FAILED: Storage upload error", e);
                        Log.e(TAG, "   Error code: " + e.getClass().getSimpleName());
                        Log.e(TAG, "   Error message: " + e.getMessage());
                        onCompleteListener.onComplete(false);
                    })
                    .addOnProgressListener(snapshot -> {
                        long progress = (100 * snapshot.getBytesTransferred()) / snapshot.getTotalByteCount();
                        Log.d(TAG, "   Upload progress: " + progress + "%");
                    });

        } catch (Exception e) {
            Log.e(TAG, "❌ CRITICAL ERROR:", e);
            Log.e(TAG, "Stack trace: " + Log.getStackTraceString(e));
            onCompleteListener.onComplete(false);
        }
    }

    public interface OnCompleteListener {
        void onComplete(boolean success);
    }
}