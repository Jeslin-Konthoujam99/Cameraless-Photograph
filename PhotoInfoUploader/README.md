# 📸 PhotoInfoUploader

**Android application for capturing photos with comprehensive environmental metadata and uploading to Firebase Cloud.**

Part of the **Cameraless Photography System** — a context-aware photo retrieval platform using multi-sensor data fusion.

---

## 📋 Overview

PhotoInfoUploader captures photos and automatically collects **21 metadata fields** from multiple sources:
- **GPS** — Latitude, longitude, reverse geocoding (city + district)
- **IMU Sensors** — Pitch, roll, yaw from accelerometer, gyroscope, magnetometer
- **Weather API** — Temperature, humidity, weather condition, wind direction/speed, pressure
- **Camera EXIF** — Exposure time, aperture, ISO, focal length, white balance, flash, orientation
- **Smart Detection** — White balance detected from weather + time + ISO (7 categories)

All data is uploaded to **Firebase Firestore** with the photo stored in **Firebase Cloud Storage**.

---

## 🏗️ System Architecture

```
Camera Capture → EXIF Extraction → GPS Location → Reverse Geocoding
                                                         ↓
                                    IMU Sensors → Pitch/Roll/Yaw Calculation
                                                         ↓
                                    OpenWeatherMap API → Weather Data
                                                         ↓
                                    Smart White Balance Detection
                                                         ↓
                                    21 Fields Assembled → Firebase Upload
                                                         ↓
                                    Photo Compressed → Firebase Storage → URL Generated
```

---

## 📊 Database Fields (21 Total)

### Location Data
| Field | Type | Description | Example |
|-------|------|-------------|---------|
| `latitude` | double | GPS latitude | 25.043968 |
| `longitude` | double | GPS longitude | 121.533782 |
| `longitude_round` | string | Rounded longitude for range queries | "121.534" |
| `station_name` | string | City + District from reverse geocoding | "Taipei - Da'an District" |
| `location_provider` | string | GPS source | "gps" |

### Weather Data
| Field | Type | Description | Example |
|-------|------|-------------|---------|
| `weather` | string | Weather condition | "Clouds", "Rain", "Clear" |
| `wind_direction` | string | 16-point compass direction | "East", "West-Northwest" |
| `wind_speed` | string | Wind speed in m/s | "4.6" |
| `temperature` | string | Temperature in Celsius | "23.7" |
| `humidity` | string | Humidity percentage | "58" |
| `pressure` | string | Atmospheric pressure in hPa | "1012" |
| `precipitation` | string | Daily rainfall in mm | "0.0" |
| `gust_speed` | string | Peak gust speed | "4.6" |
| `gust_direction` | string | Gust direction | "East" |

### Camera & Sensor Data
| Field | Type | Description | Example |
|-------|------|-------------|---------|
| `orientation` | int | EXIF orientation tag | 6 |
| `pitch` | double | Vertical tilt angle (degrees) | -38.637 |
| `roll` | double | Horizontal tilt angle (degrees) | 1.079 |
| `yaw` | double | Compass bearing (degrees) | 39.039 |
| `exposure_time` | double | Shutter speed (seconds) | 0.00833 |
| `aperture` | double | F-number | 1.7 |
| `iso` | int | ISO sensitivity | 400 |
| `focal_length` | double | Focal length (mm) | 6.3 |
| `white_balance` | string | Smart-detected lighting condition | "Daylight", "Twilight" |
| `flash` | int | Flash status (0=off, 1=on) | 0 |
| `image_length` | int | Image height in pixels | 3000 |
| `image_width` | int | Image width in pixels | 4000 |
| `maker` | string | Device manufacturer | "samsung" |
| `model` | string | Device model | "Galaxy S25 Ultra" |

### Metadata
| Field | Type | Description | Example |
|-------|------|-------------|---------|
| `photo_url` | string | Firebase Storage download URL | "https://firebasestorage..." |
| `datetime` | string | Capture date and time | "2026-05-01 14:06:25" |
| `upload_timestamp` | timestamp | Firebase server timestamp | May 1, 2026 at 2:06 PM |

---

## 🔧 Smart White Balance Detection

Instead of using the raw EXIF white balance value (which always returns 0 for "Auto"), the app detects the actual lighting condition from contextual data:

| Condition | Detected Value |
|-----------|---------------|
| Night (18:00-05:59) outdoor | **Twilight** |
| Night indoor, very high ISO (>1600) | **Incandescent** |
| Night indoor, high ISO (>800) | **Fluorescent** |
| Dawn/dusk (6:00-7:59, 17:00-17:59) | **Shade** |
| Daytime indoor (high ISO) | **Fluorescent** |
| Daytime + Clear/Sunny weather | **Daylight** |
| Daytime + Clouds/Overcast | **Cloudy** |
| Daytime + Rain/Fog/Mist | **Shade** |

---

## 📁 Project Structure

```
PhotoInfoUploader/
├── app/src/main/java/com/example/photoinfouploader/
│   ├── MainActivity.java          # Album management, permissions
│   ├── AlbumActivity.java         # Photo list within album
│   ├── AlbumAdapter.java          # Album RecyclerView adapter
│   ├── PhotoActivity.java         # Photo capture, EXIF/Weather/Sensor display, upload
│   ├── PhotoAdapter.java          # Photo grid adapter
│   ├── DataTypeConverter.java     # Data formatting, Firebase upload map, white balance detection
│   ├── EXIFReader.java            # EXIF metadata extraction from photos
│   ├── GeocodingHelper.java       # Reverse geocoding (GPS → city + district)
│   ├── SensorDataReader.java      # IMU sensor data collection
│   ├── StorageTools.java          # Local file storage utilities
│   ├── Uploader.java              # Firebase upload handler
│   └── WeatherAPICaller.java      # OpenWeatherMap API integration
├── app/src/main/res/
│   ├── layout/
│   │   ├── activity_main.xml      # Main album list
│   │   ├── activity_album.xml     # Photo grid
│   │   ├── item_album.xml         # Album list item
│   │   └── item_photo.xml         # Photo grid item
│   └── values/
│       ├── strings.xml
│       └── colors.xml
├── app/build.gradle
├── app/google-services.json       # Firebase config (not in repo)
└── build.gradle
```

---

## 🛠️ Tech Stack

| Component | Technology |
|-----------|-----------|
| Language | Java |
| Platform | Android 13+ (API 33) |
| Database | Firebase Firestore |
| Storage | Firebase Cloud Storage |
| Weather API | OpenWeatherMap |
| Image Loading | Glide 4.16.0 |
| Location | Android Location Services + Geocoder |
| Sensors | Android SensorManager |
| Build | Gradle 8.5 |

---

## 📦 Dependencies

```gradle
dependencies {
    implementation platform('com.google.firebase:firebase-bom:32.7.0')
    implementation 'com.google.firebase:firebase-analytics'
    implementation 'com.google.firebase:firebase-firestore'
    implementation 'com.google.firebase:firebase-storage'
    implementation 'androidx.appcompat:appcompat:1.7.0'
    implementation 'com.google.android.material:material:1.12.0'
    implementation 'com.github.bumptech.glide:glide:4.16.0'
    annotationProcessor 'com.github.bumptech.glide:compiler:4.16.0'
    implementation 'org.jsoup:jsoup:1.18.3'
}
```

---

## ⚙️ Setup

### Prerequisites
- Android Studio (latest)
- Android device with GPS and camera
- Firebase project with Firestore and Storage enabled
- OpenWeatherMap API key

### Steps
1. Clone the repository
2. Open in Android Studio
3. Add your `google-services.json` to `app/` folder
4. Update the OpenWeatherMap API key in `WeatherAPICaller.java`
5. Build and run on device

### Firebase Setup
1. Create a Firebase project at https://console.firebase.google.com
2. Add Android app with package name `com.example.photoinfouploader`
3. Download `google-services.json` and place in `app/` folder
4. Enable Firestore Database and Cloud Storage

---

## 📱 App Screens

### Main Screen
- Album list with thumbnails
- Create new album button

### Photo Activity
- Photo capture with camera
- EXIF data display (orientation, camera info, GPS, exposure, white balance)
- Weather information display (station, wind, temperature, humidity, pressure, rainfall)
- Sensor data display (pitch, roll, yaw)
- Auto-upload to Firebase

---

## 🔑 Key Improvements Made

| Issue | Before | After |
|-------|--------|-------|
| Weather API | Taiwan CWA (Chinese/XML) | OpenWeatherMap (English/JSON) |
| White Balance | Always "0" (EXIF raw) | Smart 7-category detection |
| Station Name | Weather station name | City + District from geocoding |
| GPS Precision | float (7 digits) | double (15 digits) |
| Geocoding | Race condition (random results) | thread.join() waits for completion |
| Wind Direction | 8-point compass | 16-point compass |
| Unicode | Invisible mismatches (apostrophe/dash) | Normalized to standard ASCII |

---

## 🌐 API Reference

### OpenWeatherMap
- Endpoint: `https://api.openweathermap.org/data/2.5/weather`
- Parameters: `lat`, `lon`, `appid`, `units=metric`
- Returns: weather condition, temperature, humidity, wind, pressure

### Firebase Firestore
- Collection: `cameraless_photography_db`
- Document: Auto-generated ID per photo
- Fields: 21 metadata fields (see Database Fields section)

### Firebase Storage
- Path: `cameraless_photos/YYYYMMDD_HHmmss.jpg`
- Compressed JPEG with EXIF preserved
- Public download URL stored in Firestore document

---

## 📄 License

This project is part of a thesis work. All rights reserved.

---

## 👤 Author

Thesis Project — Context-Aware Cameraless Photography System

---

## 🔗 Related

- [CameralessPhotography](../CameralessPhotography/) — Photo retrieval module