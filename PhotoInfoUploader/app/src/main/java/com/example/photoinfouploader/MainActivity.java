package com.example.photoinfouploader;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Build;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.floatingactionbutton.FloatingActionButton;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class MainActivity extends Activity {
    private TextView mainHintTV;
    private RecyclerView rcv;
    private FloatingActionButton createAlbumFAB;
    private StorageTools storageTools;
    private AlbumAdapter albumAdapter;
    private EXIFReader exifReader;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        init();
    }

    private void init() {
        requestPermission();
        mainHintTV = findViewById(R.id.mainHintTextView);
        rcv = findViewById(R.id.albumsRCV);
        createAlbumFAB = findViewById(R.id.createAlbumFloatingActionButton);
        storageTools = new StorageTools();
        exifReader = new EXIFReader();

        // ENSURE ADD BUTTON IS ALWAYS VISIBLE
        createAlbumFAB.setVisibility(View.VISIBLE);

        refreshAlbumList();
        listeners();
    }

    private void refreshAlbumList() {
        List<Integer> ids = getAlbumIdList();
        List<String> names = getAlbumNameList();
        if(!ids.isEmpty()) mainHintTV.setVisibility(View.GONE);
        else mainHintTV.setVisibility(View.VISIBLE);

        albumAdapter = new AlbumAdapter(this, ids, getAlbumImageMap(), names);
        rcv.setAdapter(albumAdapter);
        rcv.setLayoutManager(new LinearLayoutManager(this));
    }

    private void listeners() {
        createAlbumFAB.setOnClickListener(v -> showInputDialog());
    }

    private void showInputDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Enter Album Name");
        final EditText input = new EditText(this);
        builder.setView(input);

        builder.setPositiveButton("Confirm", (dialog, which) -> {
            String newName = input.getText().toString().trim();
            if (newName.isEmpty()) {
                Toast.makeText(this, "Name required", Toast.LENGTH_SHORT).show();
                return;
            }

            List<String> existingNames = getAlbumNameList();
            for (String name : existingNames) {
                if (name.equalsIgnoreCase(newName)) {
                    Toast.makeText(this, "Album already exists!", Toast.LENGTH_SHORT).show();
                    return;
                }
            }

            int id = storageTools.createAlbum(getBaseContext(), newName);
            if (id != -1) {
                Intent intent = new Intent(this, AlbumActivity.class);
                intent.putExtra("album ID", id);
                startActivity(intent);
            }
        });
        builder.setNegativeButton("Cancel", (dialog, which) -> dialog.cancel());
        builder.show();
    }

    private List<Integer> getAlbumIdList() {
        List<Integer> list = new ArrayList<>();
        File[] files = getFilesDir().listFiles();
        if (files != null) {
            for(File f : files) {
                if (f.isDirectory() && f.getName().startsWith("album_")) {
                    try { list.add(Integer.parseInt(f.getName().substring(6))); } catch (Exception e) {}
                }
            }
        }
        return list;
    }

    private List<String> getAlbumNameList() {
        List<String> list = new ArrayList<>();
        File[] files = getFilesDir().listFiles();
        if (files != null) {
            for(File f : files) {
                if (f.isDirectory()) {
                    try {
                        String content = storageTools.readFile(f.getAbsolutePath() + "/photoPathes.json");
                        list.add(new JSONObject(content).getString("album name"));
                    } catch (Exception e) {}
                }
            }
        }
        return list;
    }

    private HashMap<Integer, Bitmap> getAlbumImageMap() {
        HashMap<Integer, Bitmap> map = new HashMap<>();
        File[] files = getFilesDir().listFiles();
        if (files != null) {
            for(File f : files) {
                if (f.isDirectory() && f.getName().startsWith("album_")) {
                    try {
                        int id = Integer.parseInt(f.getName().substring(6));
                        JSONObject json = new JSONObject(storageTools.readFile(f.getAbsolutePath() + "/photoPathes.json"));
                        JSONArray photos = json.getJSONArray("photos");
                        if (photos.length() > 0) {
                            String path = photos.getJSONObject(0).getString("photo path");
                            BitmapFactory.Options opt = new BitmapFactory.Options();
                            opt.inSampleSize = 8;
                            Bitmap b = BitmapFactory.decodeFile(path, opt);
                            if (b != null) map.put(id, b);
                        }
                    } catch (Exception e) {}
                }
            }
        }
        return map;
    }

    private void requestPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            String[] perms = { Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.CAMERA, Manifest.permission.ACCESS_FINE_LOCATION };
            requestPermissions(perms, 100);
        }
    }

    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            new AlertDialog.Builder(this).setTitle("Exit").setMessage("Close app?")
                .setPositiveButton("Exit", (d, w) -> finishAffinity())
                .setNegativeButton("Cancel", (d, w) -> d.dismiss()).show();
        }
        return true;
    }
}
