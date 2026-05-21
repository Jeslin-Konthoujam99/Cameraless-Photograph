package com.example.photoinfouploader;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.app.ActivityCompat;
import androidx.core.content.FileProvider;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.StaggeredGridLayoutManager;

import com.google.android.material.floatingactionbutton.FloatingActionButton;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;

public class AlbumActivity extends Activity {
    private TextView albumHintTV;
    private boolean isNoPhoto;
    private RecyclerView rcv;
    private FloatingActionButton plusFAB, shotFAB, uploadFAB, deleteFAB;
    private ProgressBar pb;

    private static final int REQUEST_IMAGE_CAPTURE = 1;
    private Bundle bd;
    private int albumID;
    private String photoPath;
    private float[] accelerometerValue, gyroscopeValue, megnetometerValue;

    private LocationManager locationManager;
    private final LocationListener locationListener = location -> {};

    private EXIFReader exifReader;
    private SensorDataReader sensorDataReader;
    private StorageTools storageTools;
    private Uploader uploader;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_album);
        init();
        handleIntent(getIntent());
    }
    private void debugLocation() {
        Location loc = getLocation();
        if (loc != null) {
            Log.d("DEBUG_LOC", "Latitude: " + loc.getLatitude());
            Log.d("DEBUG_LOC", "Longitude: " + loc.getLongitude());
            Toast.makeText(this, "Lat: " + loc.getLatitude() + "\nLon: " + loc.getLongitude(), Toast.LENGTH_LONG).show();
        } else {
            Toast.makeText(this, "No location available!", Toast.LENGTH_LONG).show();
        }
    }

    // Call in onCreate():

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleIntent(intent);
    }
    // debugLocation();  //
    private void handleIntent(Intent intent) {
        Bundle bundle = intent.getExtras();
        if (bundle != null && bundle.containsKey("back mode")) {
            showStatusToast(bundle.getInt("back mode"));
            refreshPhotos();
        }
    }

    private void showStatusToast(int mode) {
        String msg = "";
        switch (mode) {
            case 1: msg = "Saved successfully"; break;
            case 2: msg = "Deleted successfully"; break;
            case 3: msg = "Upload successfully"; break;
            case 4: msg = "Weather error (24h limit)"; break;
            case 99: msg = "Error loading info"; break;
        }
        if(!msg.isEmpty()) Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
    }

    @Override
    protected void onResume() {
        super.onResume();
        sensorDataReader.startListening();
        refreshPhotos();
    }

    public void refreshPhotos() {
        JSONArray photos = readAlbum();
        albumHintTV.setVisibility(photos.length() == 0 ? View.VISIBLE : View.GONE);
        isNoPhoto = photos.length() == 0;
        rcv.setAdapter(new PhotoAdapter(this, photos, albumID));
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQUEST_IMAGE_CAPTURE && resultCode == RESULT_OK) {
            try {
                accelerometerValue = sensorDataReader.getAccelerometerValue();
                gyroscopeValue = sensorDataReader.getGyroscopeValue();
                megnetometerValue = sensorDataReader.getMegnetometerValue();
                exifReader.setPhotoPath(photoPath);
                HashMap<String, String> info = exifReader.getEXIF();

                if (info.get("exifGPSLAT") == null) {
                    try {
                        Location loc = getLocation();
                        if (loc == null) {
                            Toast.makeText(this, "❌ Location failed", Toast.LENGTH_SHORT).show();
                            new File(photoPath).delete();
                            return;
                        }
                        exifReader.addGPSinfo(loc);
                        bd.putBoolean("isShoted", true);
                        bd.putString("location_provider", loc.getProvider());
                        toPreviewActivity();
                    } catch (Exception e) {
                        Log.e("PhotoCapture", "Error getting location", e);
                        Toast.makeText(this, "❌ Location error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                        new File(photoPath).delete();
                    }
                } else {
                    bd.putBoolean("isShoted", true);
                    bd.putString("location_provider", "gps");
                    toPreviewActivity();
                }
            } catch (Exception e) {
                Log.e("PhotoCapture", "Critical error", e);
                Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void init() {
        albumHintTV = findViewById(R.id.albumHintTextView);
        rcv = findViewById(R.id.photosRCV);
        plusFAB = findViewById(R.id.plusFloatingActionButton);
        shotFAB = findViewById(R.id.shotFloatingActionButton);
        uploadFAB = findViewById(R.id.uploadAlbumFloatingActionButton);
        deleteFAB = findViewById(R.id.deleteAlbumButton);
        pb = findViewById(R.id.albumProgressBar);

        sensorDataReader = new SensorDataReader(this);
        exifReader = new EXIFReader();
        storageTools = new StorageTools();
        uploader = new Uploader();
        locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);

        requestLocationUpdate();
        bd = getIntent().getExtras();
        if (bd != null) albumID = bd.getInt("album ID");
        ((TextView)findViewById(R.id.albumTitleTextView)).setText(readAlbumName());

        DisplayMetrics dm = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getMetrics(dm);

        rcv.setLayoutManager(new StaggeredGridLayoutManager(4, StaggeredGridLayoutManager.VERTICAL));
        listeners();
    }

    private void listeners() {
        plusFAB.setOnClickListener(v -> {
            int vis = (shotFAB.getVisibility() == View.INVISIBLE) ? View.VISIBLE : View.INVISIBLE;
            shotFAB.setVisibility(vis);
            uploadFAB.setVisibility(vis);
            deleteFAB.setVisibility(vis);
            if(isNoPhoto) albumHintTV.setText(vis == View.VISIBLE ? R.string.album_hint_take_photo : R.string.album_hint_expand_menu);
        });

        shotFAB.setOnClickListener(v -> {
            String time = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
            File dir = new File("/storage/emulated/0/DCIM/PhotoInfoUploader/");
            if (!dir.exists()) dir.mkdirs();
            File file = new File(dir, time + ".jpg");
            photoPath = file.getPath();
            exifReader.setPhotoPath(photoPath);
            Uri uri = FileProvider.getUriForFile(this, "com.example.fileprovider", file);
            Intent it = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
            it.putExtra(MediaStore.EXTRA_OUTPUT, uri);
            startActivityForResult(it, REQUEST_IMAGE_CAPTURE);
        });

        if (deleteFAB != null) {
            deleteFAB.setOnClickListener(v -> {
                new AlertDialog.Builder(this)
                        .setTitle("Delete Album").setMessage("Are you sure you want to delete this entire album?")
                        .setPositiveButton("Delete", (dialog, which) -> {
                            if (storageTools.deleteAlbum(this, albumID)) {
                                Toast.makeText(this, "Album Deleted", Toast.LENGTH_SHORT).show();
                                Intent main = new Intent(this, MainActivity.class);
                                main.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
                                startActivity(main);
                                finish();
                            } else {
                                Toast.makeText(this, "Delete Failed", Toast.LENGTH_SHORT).show();
                            }
                        })
                        .setNegativeButton("Cancel", null).show();
            });
        }

        uploadFAB.setOnClickListener(v -> {
            JSONArray photos = readAlbum();
            if(photos.length() == 0) return;
            new AlertDialog.Builder(this).setTitle("Upload").setMessage("Upload entire album?")
                .setPositiveButton("Confirm", (d, w) -> {
                    pb.setVisibility(View.VISIBLE);
                    new Thread(() -> {
                        try {
                            for(int i=0; i<photos.length(); i++) {
                                String path = photos.getJSONObject(i).getString("photo path");
                                uploader.uploadPhoto2Firebase(this, albumID, path, success -> {});
                            }
                            runOnUiThread(() -> {
                                pb.setVisibility(View.GONE);
                                Toast.makeText(this, "Upload successfully!", Toast.LENGTH_SHORT).show();
                            });
                        } catch (Exception e) {}
                    }).start();
                }).setNegativeButton("Cancel", null).show();
        });
    }

    private void toPreviewActivity() {
        Intent it = new Intent(this, PhotoActivity.class);
        bd.putInt("album ID", albumID); bd.putString("photo path", photoPath);
        bd.putFloatArray("accelerometer array", accelerometerValue);
        bd.putFloatArray("megnetometer array", megnetometerValue);
        bd.putBoolean("preview mode", true);
        it.putExtras(bd); startActivity(it);
    }

    private JSONArray readAlbum() {
        try {
            String path = getFilesDir().getAbsolutePath() + "/album_" + albumID + "/photoPathes.json";
            File f = new File(path);
            if(!f.exists()) return new JSONArray();
            JSONObject json = new JSONObject(storageTools.readFile(path));
            return json.getJSONArray("photos");
        } catch (Exception e) { return new JSONArray(); }
    }

    private String readAlbumName() {
        try {
            String path = getFilesDir().getAbsolutePath() + "/album_" + albumID + "/photoPathes.json";
            JSONObject json = new JSONObject(storageTools.readFile(path));
            return json.getString("album name");
        } catch (Exception e) { return "Album"; }
    }

    private void requestLocationUpdate() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, 100);
        }
    }

    @SuppressLint("MissingPermission")
    private Location getLocation() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this, "Location permission not granted", Toast.LENGTH_SHORT).show();
            return null;
        }

        // Try GPS first
        Location location = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);

        // Fallback to network location
        if (location == null) {
            location = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
        }

        if (location == null) {
            Log.e("Location", "No location available from either provider");
            Toast.makeText(this, "Waiting for GPS signal...", Toast.LENGTH_LONG).show();
        }

        return location;
    }
    public static String getFilePathFromContentUri(Uri uri, ContentResolver res) {
        Cursor c = res.query(uri, new String[]{MediaStore.MediaColumns.DATA}, null, null, null);
        if (c != null) { c.moveToFirst(); String p = c.getString(0); c.close(); return p; }
        return uri.getPath();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        sensorDataReader.stopListening();
    }
}
