# 🔍 CameralessPhotography

**Android application for retrieving matching photos from Firebase based on location, weather, orientation, and user preferences.**

Part of the **Cameraless Photography System** — a context-aware photo retrieval platform using multi-sensor data fusion.

---

## 📋 Overview

CameralessPhotography retrieves the best matching photo from a cloud database by comparing the user's current conditions (or selected preferences) against stored photo metadata. It uses a **6-condition Firestore query** with a **composite index** and a **weighted similarity scoring algorithm** to find the most relevant photo.

### Two Retrieval Modes:

**Mode 1: Live Sensor Mode (Camera Button)**
- Uses current GPS, weather, and phone sensors automatically
- Finds photos matching your current location and conditions

**Mode 2: Smart Search Mode (Search Button)**
- Search from anywhere by typing location name
- Select weather, direction, angle, and lighting from dropdowns
- Step-by-step filtering for the best match

---

## 🏗️ System Architecture

### Live Sensor Mode
```
Camera Button Clicked
    ↓
GPS Location → Reverse Geocoding → Station Name
    ↓
OpenWeatherMap API → Weather, Wind, Temperature
    ↓
IMU Sensors → Pitch, Roll, Yaw
    ↓
Build 6-Condition Query
    ↓
Firebase Firestore → Composite Index Search
    ↓
Post-Query: Season Filter
    ↓
Similarity Scoring (Yaw 80% + Roll 10% + Pitch 5% + Exposure 5%)
    ↓
Display Best Match Photo
```

### Smart Search Mode
```
Step 1: User Types Location → Text Search in station_name field
    ↓
Step 2: User Selects Weather → Filter by weather condition
    ↓
Step 3: User Selects Direction → Map to yaw angle range
         User Selects Angle → Map to pitch angle range
         User Selects Lighting → Map to white_balance
    ↓
Step 4: Weighted Scoring (Yaw 35% + Pitch 25% + Lighting 20% + Roll 10% + Exposure 10%)
    ↓
Display Best Match with Score Details
```

---

## 🔎 6-Condition Firestore Query

The live sensor mode uses 6 conditions that require a **Firebase Composite Index**:

| # | Field | Method | Description | Example |
|---|-------|--------|-------------|---------|
| 1 | `station_name` | `whereEqualTo` | Exact city + district | "Taipei - Da'an District" |
| 2 | `weather` | `whereEqualTo` | Exact weather condition | "Clouds" |
| 3 | `longitude_round` | `whereIn` | 7 rounded values (±300m) | ["121.531"..."121.537"] |
| 4-5 | `latitude` | `whereGreaterThan/LessThan` | ±0.003 range (~300m) | 25.040 to 25.046 |
| 6 | `orientation` | `whereIn` | Device orientation category | [1, 3, 6, 8] |

### Post-Query Filters (in code)
| Filter | Description | Why |
|--------|-------------|-----|
| Season | Spring/Summer/Autumn/Winter must match | Different seasons look very different |
| ~~Time Slot~~ | ~~Removed~~ | Too restrictive, blocks finding best location match |

### Composite Index Setup
Create in Firebase Console → Firestore → Indexes:

| Order | Field | Direction |
|-------|-------|-----------|
| 1 | `station_name` | Ascending |
| 2 | `weather` | Ascending |
| 3 | `longitude_round` | Ascending |
| 4 | `orientation` | Ascending |
| 5 | `latitude` | Ascending |

Query scope: **Collection**

---

## 📊 Scoring Algorithms

### Live Sensor Mode (Senior's Algorithm)
```
Error = (Yaw_diff × 0.80) + (Roll_diff × 0.10) + (Pitch_diff × 0.05) + (Exposure_diff × 0.05)
```

| Parameter | Weight | Why |
|-----------|--------|-----|
| Yaw (compass direction) | **80%** | Most important — determines camera facing direction |
| Roll (horizontal tilt) | **10%** | How level the camera is held |
| Pitch (vertical tilt) | **5%** | Up/down angle |
| Exposure time | **5%** | Brightness/motion blur |

**Special case:** When shooting vertically (pitch > 45°), Roll and Pitch weights swap.

**Result:** Top 10 photos sorted by score. Lowest score = best match.

### Smart Search Mode (My Algorithm)
```
Score = (Yaw_diff × 0.35) + (Pitch_diff × 0.25) + (Lighting_diff × 0.20) + (Roll × 0.10) + (Exposure × 0.10)
```

| Parameter | Weight | Input Method |
|-----------|--------|-------------|
| Yaw (direction) | **35%** | Dropdown: North/Northeast/East/Southeast/South/Southwest/West/Northwest |
| Pitch (angle) | **25%** | Dropdown: Horizontal/Slightly Up/Slightly Down/Looking Up/Looking Down |
| Lighting | **20%** | Dropdown: Daylight/Cloudy/Shade/Twilight/Fluorescent/Incandescent |
| Roll | **10%** | Automatic (closer to 0 = more level) |
| Exposure | **10%** | Automatic |

### Direction → Yaw Mapping
| Dropdown | Target Yaw |
|----------|-----------|
| North | 0° |
| Northeast | 45° |
| East | 90° |
| Southeast | 135° |
| South | 180° |
| Southwest | -135° |
| West | -90° |
| Northwest | -45° |

### Angle → Pitch Mapping
| Dropdown | Target Pitch |
|----------|-------------|
| Horizontal (normal) | 0° |
| Slightly Tilted Up | 30° |
| Slightly Tilted Down | -30° |
| Looking Up (sky) | 80° |
| Looking Down (ground) | -80° |

### Lighting Similarity Grouping
| Group | Members | Score |
|-------|---------|-------|
| Exact match | Same value | 0 |
| Outdoor | Daylight ↔ Cloudy ↔ Shade | 15 |
| Indoor | Fluorescent ↔ Incandescent | 15 |
| Low light | Twilight ↔ Shade | 15 |
| Different group | Any cross-group | 40 |

---

## 📱 App Screens

### Main Screen
```
┌─────────────────────────────────────┐
│         [Photo Display Area]        │
│                                     │
│         Status / Query Info         │
│     Lat, Lon, Station, Weather      │
│     Wind, Yaw, Pitch, Roll          │
│                                     │
│      [🔍 SEARCH PHOTOS]            │
│                                     │
│  [WEATHER]   [📷]   [MENU]         │
└─────────────────────────────────────┘
```

### Weather Dialog
```
┌─────────────────────────────┐
│    Weather Information      │
│                             │
│  Station: Taipei - Da'an    │
│  Temperature: 28.36°C       │
│  Humidity: 74%              │
│  Weather: Clouds            │
│  Wind Speed: 5.07 m/s       │
│  Wind Direction: West-NW    │
│  Pressure: 1009 hPa         │
│  Peak Gust Speed: 8.82      │
│  Daily Rainfall: 0.0 mm     │
│                     [OK]    │
└─────────────────────────────┘
```

### Search Screen (Step-by-Step)
```
┌─────────────────────────────────────┐
│         🔍 Photo Search             │
│                                     │
│  Step 1: Enter Location             │
│  [Xinyi, Taipei 101, Da'an____]     │
│  [SEARCH LOCATION]                  │
│                                     │
│  Step 2: Select Weather             │
│  [Clouds               ▼]          │
│                                     │
│  Step 3: Direction, Angle, Lighting │
│  Direction: [Northeast    ▼]        │
│  Angle:     [Horizontal   ▼]        │
│  Lighting:  [Daylight     ▼]        │
│                                     │
│  ✅ Best Match:                     │
│  [Retrieved Photo Image]            │
│  📍 Taipei - Xinyi District         │
│  🌤 Clouds | 💡 Daylight            │
│  ⭐ Score: 12.5 (8 candidates)     │
│                           [BACK]    │
└─────────────────────────────────────┘
```

### Menu Screen (Camera Settings)
```
┌─────────────────────────────────────┐
│         Camera Settings             │
│                                     │
│  Shooting Direction  [Auto     ▼]   │
│  Aspect Ratio        [Auto     ▼]   │
│  Exposure Time       [Auto     ▼]   │
│  White Balance       [Auto     ▼]   │
│  Focal Length         [Auto     ▼]   │
└─────────────────────────────────────┘
```

---

## 📁 Project Structure

```
CameralessPhotography/
├── app/src/main/java/com/example/cameralessphotography/
│   ├── MainActivity.java          # Main screen: Weather, Camera, Menu, Search buttons
│   ├── SearchActivity.java        # Step-by-step photo search (location + preferences)
│   ├── MenuActivity.java          # Camera settings (orientation, exposure, white balance)
│   ├── MenuAdapter.java           # Settings dropdown adapter
│   ├── DatabaseHelper.java        # Firebase queries, scoring algorithm, season filter
│   ├── DataTypeConverter.java     # Data type conversions for query parameters
│   ├── SensorDataReader.java      # IMU sensor collection (accelerometer, gyroscope, magnetometer)
│   └── WeatherAPICaller.java      # OpenWeatherMap API + reverse geocoding
├── app/src/main/res/
│   ├── layout/
│   │   ├── activity_main.xml      # Main screen layout
│   │   ├── activity_search.xml    # Search screen layout
│   │   ├── activity_menu.xml      # Settings menu layout
│   │   └── item_menu.xml          # Menu item layout
│   ├── drawable/
│   │   └── rounded_background.xml
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
| Database | Firebase Firestore (with Composite Index) |
| Weather API | OpenWeatherMap |
| Image Loading | Glide 4.16.0 |
| Location | Android Location Services + Geocoder |
| Sensors | Android SensorManager (Accelerometer, Gyroscope, Magnetometer) |
| Build | Gradle 8.5 |

---

## 📦 Dependencies

```gradle
dependencies {
    implementation platform('com.google.firebase:firebase-bom:31.5.0')
    implementation 'com.google.firebase:firebase-analytics'
    implementation 'com.google.firebase:firebase-firestore'
    implementation 'com.google.firebase:firebase-storage'
    implementation 'androidx.datastore:datastore-preferences:1.0.0'
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
- Android device with GPS, camera, and IMU sensors
- Firebase project (same as PhotoInfoUploader)
- OpenWeatherMap API key
- Photos uploaded via PhotoInfoUploader

### Steps
1. Clone the repository
2. Open in Android Studio
3. Add your `google-services.json` to `app/` folder
4. Update API key in `WeatherAPICaller.java`
5. Create **Composite Index** in Firebase Console (see Query section above)
6. Wait for index status to show "Enabled"
7. Build and run on device
8. Upload photos with PhotoInfoUploader first, then search

### Firebase Composite Index
1. Firebase Console → Firestore → Indexes tab
2. Click "Create Index"
3. Collection: `cameraless_photography_db`
4. Add fields: station_name, weather, longitude_round, orientation, latitude (all Ascending)
5. Query scope: Collection
6. Wait 2-5 minutes for "Enabled" status

---

## 🔑 Key Features

### Weather Button
- Fetches **fresh** weather data every click (not cached)
- Gets current GPS → calls OpenWeatherMap API
- Reverse geocoding for city + district name
- Displays: station, temperature, humidity, weather, wind, pressure, rainfall

### Camera Button (Live Sensor Mode)
- Automatically collects GPS, weather, and sensor data
- Builds 6-condition Firestore query
- Shows query parameters and sensor values on screen
- Displays best matching photo from database

### Search Button (Smart Search Mode)
- **Step 1:** Type any location (city, district, landmark)
- **Step 2:** Select weather condition from dropdown
- **Step 3:** Select facing direction, camera angle, and lighting
- **Step 4:** Algorithm finds best match with detailed score breakdown

### Menu Button
- Camera settings: orientation, aspect ratio, exposure time, white balance, focal length
- Settings affect how photos are filtered and matched

---

## 🐛 Issues Fixed

| # | Issue | Root Cause | Solution |
|---|-------|-----------|----------|
| 1 | Weather API mismatch | Used Taiwan CWA instead of OpenWeatherMap | Switched to OpenWeatherMap API |
| 2 | Wind direction format | 8-point vs 16-point compass | Standardized to 16-point |
| 3 | Station name inconsistency | Geocoding race condition | `thread.join()` waits for completion |
| 4 | Unicode mismatches | Invisible apostrophe/dash differences | Normalize to standard ASCII |
| 5 | Composite index missing | Query silently returned 0 results | Created 5-field composite index |
| 6 | NumberFormatException | `DataTypeConverter` parsed "West" as float | Bypass converter, build params directly |
| 7 | Firestore dependency crash | `PreferenceDataStoreDelegateKt` not found | Downgraded Firebase BOM to 31.5.0 |
| 8 | Stale GPS location | `getLastKnownLocation()` returned cached data | Age check: reject locations > 60 seconds old |
| 9 | Longitude range too narrow | ±0.001 missed nearby photos | Expanded to ±0.003 (7 values) |
| 10 | Orientation type mismatch | Firestore Long vs Java Integer | Changed `List<Integer>` to `List<Long>` |
| 11 | Time slot too restrictive | Photos unfindable after few hours | Removed time slot filter |
| 12 | NullPointerException in scoring | Null yaw/roll/pitch values | Null-safe extraction with instanceof checks |

---

## 🌐 API Reference

### OpenWeatherMap
- Endpoint: `https://api.openweathermap.org/data/2.5/weather`
- Parameters: `lat`, `lon`, `appid`, `units=metric`
- Returns: weather, temperature, humidity, wind, pressure
- Wind direction: converted from degrees to 16-point compass text

### Android Geocoder
- `getFromLocation(lat, lon, 1)` → Address object
- `getAdminArea()` → City name (e.g., "Taipei City" → "Taipei")
- `getSubAdminArea()` → District name (e.g., "Da'an District")
- Station name format: "City - District"

### Firebase Firestore
- Collection: `cameraless_photography_db`
- Query: 6 conditions with composite index
- Results: Up to 50 documents, scored and ranked

---

## 📊 Performance

| Metric | Value |
|--------|-------|
| Weather fetch | ~1 second |
| Firestore query | ~0.2 seconds |
| Scoring calculation | < 0.01 seconds |
| Image loading (Glide) | ~2 seconds |
| **Total end-to-end** | **~3-4 seconds** |

---

## 📄 License

This project is part of a thesis work. All rights reserved.

---

## 👤 Author

Thesis Project — Context-Aware Cameraless Photography System

---

## 🔗 Related

- [PhotoInfoUploader](../PhotoInfoUploader/) — Photo capture and upload module
