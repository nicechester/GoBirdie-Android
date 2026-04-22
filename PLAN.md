# GoBirdie Android — Development Plan

## Overview

Android port of GoBirdie, a golf GPS and shot tracking app. The iOS version is the reference implementation — this plan maps every iOS feature to its Android equivalent.

## Technology Stack

| Layer | iOS (Reference) | Android |
|-------|-----------------|---------|
| Language | Swift | Kotlin |
| UI | SwiftUI | Jetpack Compose |
| Map | MapLibre iOS | MapLibre Android SDK |
| Location | CoreLocation | Google Fused Location Provider |
| Storage | JSON files (Documents/) | JSON files (internal storage) |
| HTTP | URLSession | Ktor Client or OkHttp |
| Architecture | MVVM + ObservableObject | MVVM + ViewModel + StateFlow |
| DI | EnvironmentObject | Hilt |
| Build | Xcode / SPM | Gradle / Kotlin DSL |
| Min SDK | iOS 16.6 | API 26 (Android 8.0) |

## Project Structure

```
android/
├── app/
│   └── src/main/
│       ├── java/io/github/nicechester/gobirdie/
│       │   ├── model/          # Data classes (mirrors GoBirdieCore/Models)
│       │   ├── storage/        # JSON file storage (mirrors GoBirdieCore/Storage)
│       │   ├── api/            # GolfCourseAPI + Overpass clients
│       │   ├── location/       # Fused location provider wrapper
│       │   ├── distance/       # Haversine distance engine
│       │   ├── session/        # RoundSession state machine
│       │   ├── ui/
│       │   │   ├── round/      # StartRoundScreen, ActiveRoundScreen, HoleControls
│       │   │   ├── map/        # MapLibre composable, overlays
│       │   │   ├── scorecards/ # ScorecardListScreen, ScorecardDetailScreen, ShotMapScreen
│       │   │   ├── settings/   # SettingsScreen, ClubManagerScreen, CourseManagerScreen
│       │   │   └── theme/      # Material 3 theme, colors
│       │   ├── di/             # Hilt modules
│       │   ├── AppState.kt     # Round lifecycle, auto-save, idle detection
│       │   └── MainActivity.kt
│       └── res/
├── build.gradle.kts
└── gradle/
```

## Data Models (1:1 with iOS)

All models use the same JSON field names as iOS for cross-platform data compatibility.

| iOS Model | Android Equivalent | Notes |
|-----------|-------------------|-------|
| `GpsPoint` | `data class GpsPoint(val lat: Double, val lon: Double)` | Include `distanceMeters()` extension |
| `ClubType` | `enum class ClubType` | Same raw values: `"driver"`, `"3w"`, `"pw"`, etc. |
| `Shot` | `data class Shot` | UUID id, sequence, location, timestamp, club |
| `HoleScore` | `data class HoleScore` | Computed `gir` property, same formula |
| `Round` | `data class Round` | Same JSON keys (`course_id`, `started_at`, etc.) |
| `Course` | `data class Course` | Holes list with geometry |
| `Hole` | `data class Hole` | Tee, green center/front/back, geometry |
| `HoleGeometry` | `data class HoleGeometry` | Fairway, bunkers, water polygons |

## Storage (1:1 with iOS)

Same file-based JSON approach for simplicity and data portability.

```
<internal storage>/GoBirdie/
├── rounds/       # <id>.json — completed rounds
├── courses/      # <id>.json — downloaded course definitions
├── overpass_cache/ # cached Overpass API responses
└── in_progress/  # auto-saved round state for crash recovery
```

- `RoundStore` — save/load/delete rounds as JSON files
- `CourseStore` — save/load/delete courses as JSON files
- `InProgressStore` — auto-save active round every 30s, restore on launch
- Use `kotlinx.serialization` with `@SerialName` annotations matching iOS `CodingKeys`

## Screens & Feature Mapping

### Phase 1 — Core Round Experience (MVP)

| # | Screen | iOS Reference | Priority |
|---|--------|---------------|----------|
| 1 | **Round Tab (empty state)** | `EmptyRoundStateView` | P0 |
| 2 | **Start Round** | `StartRoundView` — search bar, course list, starting hole picker | P0 |
| 3 | **Active Round** | `ActiveRoundView` — hole label, distances, mark shot, putts, prev/next | P0 |
| 4 | **Club Selection** | `MarkShotSheet` — list of enabled clubs | P0 |
| 5 | **Map (during round)** | `MapLibreView` + `MapOverlayView` — hole geometry, shot lines, distances | P0 |
| 6 | **Scorecards List** | `ScorecardsTab` — round rows with score, date, course name | P0 |
| 7 | **Scorecard Detail** | `ScorecardDetailView` — front/back 9, totals, stats | P0 |

### Phase 2 — Polish & Settings

| # | Screen | iOS Reference | Priority |
|---|--------|---------------|----------|
| 8 | **Shot Map** | `ShotMapView` — per-hole shot map with club dots, distance lines | P1 |
| 9 | **Settings** | `SettingsView` — tee color, club bag, course manager | P1 |
| 10 | **Club Manager** | `MyClubsView` — enable/disable clubs in bag | P1 |
| 11 | **Course Manager** | `CourseManagerView` — search, download, delete courses | P1 |
| 12 | **Resume Round** | `ResumeRoundView` — restore interrupted round | P1 |
| 13 | **Editable Shot Map** | `EditableShotMapView` — drag pins, add/delete shots, change clubs | P2 |

### Phase 3 — Explore & Sync

| # | Screen | iOS Reference | Priority |
|---|--------|---------------|----------|
| 14 | **Map Explore Mode** | `MapTab` explore — browse courses without a round | P2 |
| 15 | **Tap-to-Measure** | Map tap → distance from player + distance to green | P2 |
| 16 | **Desktop Sync** | `SyncServer` — needs protocol design for Android (WiFi Direct or HTTP) | P3 |

### Not Porting (iOS-only)

| Feature | Reason |
|---------|--------|
| Apple Watch companion | No direct equivalent; Wear OS is a separate project |
| MultipeerConnectivity | Apple-only; replace with HTTP or WiFi Direct for sync |
| HKWorkoutSession | HealthConnect is the Android equivalent, lower priority |
| WatchConnectivity | N/A |

## API Clients

Both APIs are REST/HTTP — straightforward to port.

### OverpassClient
- Same Overpass QL queries for course discovery by location
- Same 15km → 30km radius fallback
- Cache responses in `overpass_cache/` directory

### GolfCourseAPIClient
- Same API key and endpoints for name-based search
- Same response parsing for hole yardage, handicap, par

## Map Implementation

MapLibre Android SDK (open-source, same tile sources as iOS).

| iOS Feature | Android Equivalent |
|-------------|-------------------|
| `MapLibreView` (UIViewRepresentable) | `MapView` composable wrapper |
| GeoJSON overlays (fairway, bunkers, water) | `GeoJsonSource` + `FillLayer` / `LineLayer` |
| Shot dots + distance lines | `SymbolLayer` + `LineLayer` |
| Player location blue dot | MapLibre `LocationComponent` |
| Tap-to-measure | `MapView.addOnMapClickListener` |
| Tee-to-green rotation | `CameraPosition` with bearing calculation |

## Location

| iOS | Android |
|-----|---------|
| `CLLocationManager` | `FusedLocationProviderClient` |
| `CLLocation` | `android.location.Location` |
| `requestWhenInUseAuthorization` | `ACCESS_FINE_LOCATION` runtime permission |
| `locationManager.startUpdatingLocation()` | `requestLocationUpdates()` with `LocationRequest` |

## Key Behaviors to Match

1. **Auto-save** — Save round state every 30 seconds + on `onPause()` (equivalent to iOS background)
2. **Crash recovery** — `InProgressStore` saves/restores active round
3. **Idle detection** — 30-minute inactivity prompt ("Are you still playing?")
4. **Orientation lock** — Lock to portrait during active round (`requestedOrientation = PORTRAIT`)
5. **Distance units** — Yards (matching iOS), with front/flag/back display
6. **Club defaults** — Same distance-based club suggestion logic
7. **Course search** — Saved courses first, then Overpass nearby, then GolfCourseAPI by name

## Dependencies

```kotlin
// build.gradle.kts
dependencies {
    // Compose
    implementation("androidx.compose.material3:material3:1.2+")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.7+")
    implementation("androidx.navigation:navigation-compose:2.7+")

    // MapLibre
    implementation("org.maplibre.gl:android-sdk:11.0+")

    // Location
    implementation("com.google.android.gms:play-services-location:21.0+")

    // Networking
    implementation("com.squareup.okhttp3:okhttp:4.12+")

    // Serialization
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6+")

    // DI
    implementation("com.google.dagger:hilt-android:2.50+")
    kapt("com.google.dagger:hilt-compiler:2.50+")
}
```

## Milestones

### M1 — Skeleton (Week 1)
- [ ] Android Studio project with Compose + Hilt
- [ ] All data models with kotlinx.serialization
- [ ] RoundStore + CourseStore (JSON file I/O)
- [ ] DistanceEngine (haversine)
- [ ] Bottom navigation: Scorecards / Round / Map / Settings (empty screens)

### M2 — Round Flow (Week 2-3)
- [ ] LocationService wrapper
- [ ] OverpassClient + GolfCourseAPIClient
- [ ] StartRoundScreen (search, course list, starting hole)
- [ ] CourseDownloadService
- [ ] ActiveRoundScreen (hole info, mark shot, club picker, putts, prev/next)
- [ ] RoundSession state machine
- [ ] AppState (round lifecycle, auto-save)

### M3 — Map (Week 3-4)
- [ ] MapLibre composable wrapper
- [ ] Hole geometry rendering (fairway, bunkers, water)
- [ ] Shot dots + distance lines during round
- [ ] Player location + distance to green display
- [ ] Front/Flag/Back distance panel

### M4 — Scorecards (Week 4)
- [ ] Scorecards list screen
- [ ] Scorecard detail (front 9, back 9, totals, stats)
- [ ] Shot map per hole
- [ ] Swipe-to-delete rounds

### M5 — Settings & Polish (Week 5)
- [ ] Settings screen (tee color picker)
- [ ] Club bag manager
- [ ] Course manager (search, download, delete)
- [ ] Resume round flow
- [ ] Crash recovery (InProgressStore)
- [ ] Idle detection prompt
- [ ] Orientation lock during round

### M6 — Release Prep (Week 6)
- [ ] UI polish, Material 3 theming
- [ ] Edge cases, error handling
- [ ] Play Store listing, screenshots
- [ ] Internal testing track

## Data Compatibility

The JSON format is identical between iOS and Android. A round saved on iOS can be loaded on Android and vice versa. This enables:
- Desktop sync working with both platforms
- Future cloud sync sharing the same data format
- Migration between platforms

## Open Questions

1. **Wear OS** — Port the Watch companion? Separate project or same repo?
2. **Desktop sync protocol** — MultipeerConnectivity is Apple-only. Options: HTTP server on phone, WiFi Direct, or cloud sync
3. **Play Store vs F-Droid** — Distribute on both?
4. **Garmin Connect integration** — Import rounds from Garmin watches on Android?
