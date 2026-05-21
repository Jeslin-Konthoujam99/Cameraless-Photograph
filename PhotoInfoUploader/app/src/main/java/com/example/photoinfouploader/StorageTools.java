package com.example.photoinfouploader;

import android.content.Context;
import android.os.Environment;
import android.util.Base64;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Map;

public class StorageTools {

    private String photo_base64;
    private HashMap<String, String> exifInfo;
    private HashMap<String, String> weatherInfo;
    private float[] orientationArray;
    private EXIFReader exifReader;
    private String locationProvider;

    public StorageTools() {
        photo_base64 = "";
        exifInfo = new HashMap<>();
        weatherInfo = new HashMap<>();
        orientationArray = new float[3];
        exifReader = new EXIFReader();
    }

    private void reset() {
        photo_base64 = "";
        exifInfo.clear();
        weatherInfo.clear();
        orientationArray = new float[3];
    }

    private boolean createJsonFile(String dirPath, String fileName, String jsonContent) {
        try {
            File file = new File(dirPath, fileName + ".json");
            FileWriter fileWriter = new FileWriter(file.getPath());
            fileWriter.write(jsonContent);
            fileWriter.close();
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    public int createAlbum(Context context, String albumName) {
        try {
            int newAlbumId = -1;
            File crtDir = context.getFilesDir();
            File[] fileList = crtDir.listFiles();
            if (fileList != null) {
                for (File file : fileList) {
                    if (!file.isDirectory() || !file.getName().startsWith("album_")) continue;
                    try {
                        int dirId = Integer.parseInt(file.getName().substring(6));
                        if (dirId > newAlbumId) newAlbumId = dirId;
                    } catch (Exception ignored) {}
                }
                newAlbumId += 1;
                String newDirPath = crtDir.getAbsolutePath() + "/album_" + newAlbumId;
                File newDir = new File(newDirPath);
                if (!newDir.exists() && newDir.mkdir()) {
                    if (createPhotoPathesFile(newDirPath, albumName)) return newAlbumId;
                }
            }
            return -1;
        } catch (Exception e) {
            return -1;
        }
    }

    private boolean deleteFiles(File directory) {
        try {
            File[] fileList = directory.listFiles();
            if (fileList != null) {
                for (File file : fileList) {
                    if (file.isFile()) file.delete();
                    else if (file.isDirectory() && !deleteFiles(file)) return false;
                }
            }
            return directory.delete();
        } catch (Exception e) {
            return false;
        }
    }

    public boolean deleteAlbum(Context context, int albumId) {
        try {
            File crtDir = context.getFilesDir();
            File[] fileList = crtDir.listFiles();
            if (fileList == null) return false;
            for (File file : fileList) {
                if (!file.isDirectory() || !file.getName().startsWith("album_")) continue;
                try {
                    if (Integer.parseInt(file.getName().substring(6)) != albumId) continue;
                    String jsonPath = file.getAbsolutePath() + "/photoPathes.json";
                    File jsonFile = new File(jsonPath);
                    if (jsonFile.exists()) {
                        JSONObject mainObject = new JSONObject(readFile(jsonPath));
                        if (mainObject.has("photos")) {
                            JSONArray photoArray = mainObject.getJSONArray("photos");
                            for (int i = 0; i < photoArray.length(); ++i) {
                                String path = photoArray.getJSONObject(i).getString("photo path");
                                new File(path).delete();
                            }
                        }
                    }
                    return deleteFiles(file);
                } catch (Exception ignored) {}
            }
            return false;
        } catch (Exception e) {
            return false;
        }
    }

    public void deletePhoto(Context context, int albumId, String photoPath) throws IOException, JSONException {
        File crtDir = context.getFilesDir();
        File[] fileList = crtDir.listFiles();
        if (fileList == null) return;
        
        String photoPathesFilePath = null;
        File albumDir = null;
        for (File file : fileList) {
            if (!file.isDirectory() || !file.getName().startsWith("album_")) continue;
            try {
                if (Integer.parseInt(file.getName().substring(6)) == albumId) {
                    albumDir = file;
                    photoPathesFilePath = file.getAbsolutePath() + "/photoPathes.json";
                    break;
                }
            } catch (Exception ignored) {}
        }

        if (albumDir == null || photoPathesFilePath == null) return;

        JSONObject mainObject = new JSONObject(readFile(photoPathesFilePath));
        String albumName = mainObject.getString("album name");
        JSONArray photoArray = mainObject.getJSONArray("photos");
        JSONArray newPhotoArray = new JSONArray();
        
        String targetCanonical = new File(photoPath).getCanonicalPath();

        for (int i = 0; i < photoArray.length(); ++i) {
            JSONObject photoObject = photoArray.getJSONObject(i);
            String currentPath = photoObject.getString("photo path");
            if (!new File(currentPath).getCanonicalPath().equals(targetCanonical)) {
                newPhotoArray.put(photoObject);
            }
        }

        JSONObject newMain = new JSONObject();
        newMain.put("album name", albumName);
        newMain.put("photos", newPhotoArray);
        createJsonFile(albumDir.getAbsolutePath(), "photoPathes", newMain.toString());
        
        File photoFile = new File(photoPath);
        if (photoFile.exists()) photoFile.delete();
    }

    private boolean createPhotoPathesFile(String albumPath, String albumName) {
        try {
            JSONObject mainObject = new JSONObject();
            mainObject.put("album name", albumName);
            mainObject.put("photos", new JSONArray());
            return createJsonFile(albumPath, "photoPathes", mainObject.toString());
        } catch (JSONException e) {
            return false;
        }
    }

    public String readFile(String filePath) throws IOException {
        FileInputStream inputStream = new FileInputStream(filePath);
        InputStreamReader reader = new InputStreamReader(inputStream, "UTF-8");
        BufferedReader bufferedReader = new BufferedReader(reader);
        StringBuilder stringBuilder = new StringBuilder();
        String line;
        while ((line = bufferedReader.readLine()) != null) {
            stringBuilder.append(line);
        }
        inputStream.close();
        return stringBuilder.toString();
    }

    private String storePhotoPath(String albumPath, String photoPath) throws IOException, JSONException {
        String photoPathesFilePath = albumPath + "/photoPathes.json";
        JSONObject mainObject = new JSONObject(readFile(photoPathesFilePath));
        String albumName = mainObject.getString("album name");
        JSONArray photoArray = mainObject.has("photos") ? mainObject.getJSONArray("photos") : new JSONArray();

        JSONObject photoObject = new JSONObject();
        photoObject.put("photo path", photoPath);

        JSONObject weatherObject = new JSONObject();
        if (weatherInfo != null) {
            for (Map.Entry<String, String> entry : weatherInfo.entrySet()) {
                weatherObject.put(entry.getKey(), entry.getValue());
            }
        }
        photoObject.put("weatherInfo", weatherObject);

        JSONObject orientationObject = new JSONObject();
        orientationObject.put("pitch", orientationArray[1]);
        orientationObject.put("roll", orientationArray[2]);
        orientationObject.put("yaw", orientationArray[0]);
        photoObject.put("orientation info", orientationObject);
        photoObject.put("location_provider", locationProvider);

        photoArray.put(photoObject);
        JSONObject newMain = new JSONObject();
        newMain.put("album name", albumName);
        newMain.put("photos", photoArray);

        return newMain.toString();
    }

    private String getAlbumPath(File dir, int albumId) {
        File[] fileList = dir.listFiles();
        if (fileList != null) {
            for (File file : fileList) {
                if (!file.isDirectory() || !file.getName().startsWith("album_")) continue;
                try {
                    if (Integer.parseInt(file.getName().substring(6)) == albumId) return file.getAbsolutePath();
                } catch (Exception ignored) {}
            }
        }
        return "None";
    }

    public int save(Context context, String photoPath, int albumId, float[] orientationArray, HashMap<String, String> weatherInfo, String locationProvider) throws JSONException, IOException {
        if (photoPath != null && !photoPath.isEmpty()) {
            exifReader.setPhotoPath(photoPath);
            exifInfo = exifReader.getEXIF();
            if (exifInfo.get("exifGPSLAT") == null || exifInfo.get("exifGPSLONG") == null) return 1;
            this.weatherInfo = weatherInfo;
            this.orientationArray = orientationArray;
            this.locationProvider = locationProvider;
        }

        if (exifInfo.isEmpty()) {
            reset();
            return 2;
        } else {
            // Weather info is now optional to avoid blocking the user
            String albumPath = getAlbumPath(context.getFilesDir(), albumId);
            if ("None".equals(albumPath)) return 4;
            String jsonStr = storePhotoPath(albumPath, photoPath);
            File file = new File(albumPath, "photoPathes.json");
            FileWriter fileWriter = new FileWriter(file.getPath());
            fileWriter.write(jsonStr);
            fileWriter.close();
            reset();
            return 0;
        }
    }
}
