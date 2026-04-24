# GoBirdie Android — Development Plan

## Overview

Android port of GoBirdie, a golf GPS and shot tracking app. The iOS version is the reference implementation — this plan maps every iOS feature to its Android equivalent.

## Technology Stack

| Layer | iOS (Reference) | Android |
|-------|-----------------|---------|
| Language | Swift | Kotlin |
| UI | SwiftUI | Jetpack Compose |
| Watch UI | SwiftUI (watchOS) | Compose for Wear OS + Horologist |
| Map | MapLibre iOS | MapLibre Android SDK |
| Location | CoreLocation | Google Fused Location Provider |
| Watch Location | CoreLocation + HKWorkoutSession | Health Services API (ExerciseClient) |
| Phone↔Watch | WatchConnectivity | Wearable Data Layer API |
| Storage | JSON files (Documents/) | JSON files (internal storage) |
| HTTP | URLSession | Ktor Client or OkHttp |
| Architecture | MVVM + ObservableObject | MVVM + ViewModel + StateFlow |
| DI | EnvironmentObject | Hilt |
| Build | Xcode / SPM | Gradle / Kotlin DSL |
| Min SDK | iOS 16.6 | Phone: API 26 (Android 8.0) / Wear: API 30 |

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
│       │   ├── sync/           # HTTP sync server + mDNS advertisement
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
├── wear/
│   └── src/main/
│       ├── java/io/github/nicechester/gobirdie/
│       │   ├── WatchRoundSession.kt  # Hole state, strokes, GPS distances, club picker
│       │   ├── DataLayerService.kt   # Wear Data Layer API (phone ↔ watch)
│       │   └── ui/                   # Compose for Wear OS screens
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
| 16 | **Desktop Sync** | `SyncServer` — HTTP server on Android for desktop discovery | P2 |

### Phase 4 — Watch & Desktop Sync

| # | Screen | iOS Reference | Priority |
|---|--------|---------------|----------|
| 17 | **Wear OS — Waiting / Active Round** | `WatchRoundView` — distances, mark shot, putts, club picker | P2 |
| 18 | **Wear OS — End/Cancel Round** | `EndRoundPage` — finish or cancel from wrist | P2 |
| 19 | **Desktop Sync (HTTP server)** | `SyncServer` (MultipeerConnectivity) — replaced with HTTP on Android | P2 |

### Not Porting (iOS-only)

| Feature | Reason |
|---------|--------|
| MultipeerConnectivity | Apple-only; Android uses HTTP server for desktop sync |
| WatchConnectivity | Apple-only; Android uses Wear OS Data Layer API |
| HKWorkoutSession | Wear OS uses Health Services API instead |

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
// app/build.gradle.kts
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

    // Wearable Data Layer (phone side)
    implementation("com.google.android.gms:play-services-wearable:18.1+")
}

// wear/build.gradle.kts
dependencies {
    // Compose for Wear OS
    implementation("androidx.wear.compose:compose-material:1.3+")
    implementation("androidx.wear.compose:compose-foundation:1.3+")
    implementation("com.google.android.horologist:horologist-compose-layout:0.6+")

    // Health Services (ExerciseClient for GPS + heart rate)
    implementation("androidx.health:health-services-client:1.1+")

    // Wearable Data Layer (watch side)
    implementation("com.google.android.gms:play-services-wearable:18.1+")

    // Serialization
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6+")
}
```

## Milestones

### M1 — Skeleton (Week 1) ✅
- [x] Android Studio project with Compose + Hilt
- [x] All data models with kotlinx.serialization
- [x] RoundStore + CourseStore (JSON file I/O)
- [x] DistanceEngine (haversine — built into GpsPoint)
- [x] Bottom navigation: Scorecards / Round / Map / Settings (empty screens)
- [x] App icon generated from iOS source into all mipmap densities
- [x] Material 3 theme with golf green palette

### M2 — Round Flow (Week 2-3) ✅
- [x] LocationService wrapper (FusedLocationProviderClient)
- [x] OverpassClient + GolfCourseAPIClient
- [x] StartRoundScreen (search, course list, starting hole)
- [x] ActiveRoundScreen (hole info, mark shot, club picker, putts, prev/next)
- [x] DistanceDisplay (Front/Flag/Back columns, static yardage fallback)
- [x] MiniScorecard (vertical list with stroke circles, auto-scroll)
- [x] RoundSession state machine
- [x] AppState (round lifecycle)
- [x] Resume round flow (InProgressStore, resume prompt on launch)
- [x] Auto-save every 30s (InProgressStore)
- [x] Crash recovery (InProgressStore persist/restore)
- [x] Idle detection prompt (30-minute timeout)

### M3 — Map (Week 3-4) ✅
- [x] MapLibre composable wrapper (OSM raster tiles)
- [x] Hole geometry rendering (fairway, bunkers, water, rough via GeoJSON)
- [x] Shot dots + distance lines during round (color-coded by club)
- [x] Player location blue dot with pulse animation
- [x] Flag dot + player-to-green distance line
- [x] Tee-to-green camera rotation (bearing calculation)
- [x] Camera offset for hole info bar
- [x] Tap-to-measure (player→tap + tap→green distances)
- [x] Explore mode (course picker → map view without active round)
- [x] Hole navigation (prev/next) with info bar
- [x] Shared AppState between Round and Map tabs

### M4 — Scorecards (Week 4) ✅
- [x] Scorecards list screen (round rows with score, date, course name)
- [x] Scorecard detail (front 9, back 9, totals, stats)
- [x] Color-coded scores (eagle/birdie/par/bogey/double+)
- [x] Swipe-to-delete rounds
- [x] Shot map per hole

### M5 — Settings & Polish (Week 5) ✅
- [x] Settings screen (tee color picker)
- [x] Club bag manager (toggle clubs, persist to SharedPreferences)
- [x] Course manager (search, download, delete with swipe)
- [x] Tip jar (Venmo link)
- [x] About section (version, GitHub, attribution)
- [x] Orientation lock during round

### M6 — Wear OS Watch (Week 7-8) ✅
- [x] Wear OS module (`wear/`) with Compose for Wear OS
- [x] Data Layer API bridge (phone ↔ watch messaging, replaces WatchConnectivity)
  - Phone → Watch: hole coordinates, par, course name, club bag, stroke updates
  - Watch → Phone: shot (with GPS), stroke/putt counts, club selection, navigate, end/cancel
- [x] WatchRoundSession — hole number, par, strokes, putts, club bag state
- [x] GPS distances on wrist (front/pin/back yards via watch's onboard GPS)
- [x] Mark Shot button + club picker overlay (rotary/swipe scroll, auto-dismiss)
- [x] Add Stroke / Add Putt / Remove Putt controls
- [x] Hole navigation (vertical swipe, tappable page dots)
- [x] End Round / Cancel Round page
- [x] Round Ended confirmation screen
- [x] Watch screenshots added to MANUAL.md
- [x] Health Services API — ExerciseClient golf workout (keeps GPS alive when phone is in cart)
- [ ] Heart rate sampling → send timeline to phone on round end
- [ ] Ambient Mode / Always-On Display — high-contrast simplified scoring view

### M7 — Desktop Sync (Week 8-9)
- [ ] HTTP sync server on Android (NanoHTTPD or Ktor embedded server)
- [ ] `GET /api/rounds` — return round summaries JSON (same format as iOS `list` command)
- [ ] `GET /api/rounds/:id` — return full round JSON (same format as iOS `round:` command)
- [ ] mDNS/NSD service advertisement (`_gobirdie._tcp`) for desktop auto-discovery
- [ ] Settings toggle to start/stop sync server
- [ ] Desktop app: add HTTP transport alongside MultipeerConnectivity helper
- [ ] WiFi-only guard (don't serve over mobile data)
- [ ] Evaluate Nearby Connections API as alternative transport (cross-platform P2P, no WiFi required)

### M8 — Release Prep (Week 10)
- [x] GitHub Actions CI workflow (android.yml)
- [ ] UI polish, Material 3 theming
- [ ] Edge cases, error handling
- [ ] ProGuard / R8 configuration
- [ ] Play Store listing, screenshots
- [ ] Internal testing track

## Data Compatibility

The JSON format is identical between iOS and Android. A round saved on iOS can be loaded on Android and vice versa. This enables:
- Desktop sync working with both platforms
- Future cloud sync sharing the same data format
- Migration between platforms

## Open Questions

1. ~~**Wear OS** — Port the Watch companion? Separate project or same repo?~~ → Same repo, `wear/` module
2. ~~**Desktop sync protocol** — MultipeerConnectivity is Apple-only.~~ → HTTP server + mDNS on Android
3. **Play Store vs F-Droid** — Distribute on both?
4. **Garmin Connect integration** — Import rounds from Garmin watches on Android?
