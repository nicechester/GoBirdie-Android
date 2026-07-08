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
- [x] Heart rate sampling → send timeline to phone on round end
- [x] Ambient Mode / Always-On Display — high-contrast simplified scoring view

### M7 — Desktop Sync (Week 8-9) ✅
- [x] HTTP sync server on Android (NanoHTTPD)
- [x] `GET /api/rounds` — return round summaries JSON (same format as iOS `list` command)
- [x] `GET /api/rounds/:id` — return full round JSON (same format as iOS `round:` command)
- [x] mDNS/NSD service advertisement (`_gobirdie._tcp`) for desktop auto-discovery
- [x] Settings toggle to start/stop sync server
- [x] Desktop app: add HTTP transport + mDNS discovery (reqwest + mdns-sd)
- [x] WiFi-only guard (don't serve over mobile data)

### M8 — Release Prep (Week 10)
- [x] GitHub Actions CI workflow (android.yml)
- [ ] UI polish, Material 3 theming
- [ ] Edge cases, error handling
- [ ] ProGuard / R8 configuration
- [ ] Play Store listing, screenshots
- [ ] Internal testing track

### M9 — Wear OS Polish (2026-04-27)
- [ ] **Club picker: carousel → full-screen list** — Replace 3-item carousel overlay with `ScalingLazyColumn` full-screen list; tap any row confirms immediately; crown scrolls the list; highlighted club shown in green/bold
- [ ] **Cancel button on club picker** — Small red ✕ button; cancels the shot (undoes the stroke increment) and dismisses without recording a club
- [ ] **Club picker auto-submit timer: 10s → 15s** — Extend timer from 10s to 15s; show countdown next to highlighted club row
- [ ] **Swing detection via accelerometer** — `SensorManager` at 50Hz; detect g-force magnitude > 8g; auto-trigger `markShot()`; 2s debounce prevents double-triggers from follow-through
- [ ] **Practice swing detection** — Swing detected while club picker is open resets the 15s auto-submit timer instead of marking a new shot

### M10 — Phone App Polish (2026-04-27 / 2026-04-28)

#### 2026-04-27
- [x] **MiniScorecard read-only** — Disable `onHoleSelect` tap on scorecard rows; hole navigation only via Prev/Next buttons
- [x] **Move shots to correct hole** — Add "Move Shots to Hole..." in the round menu (visible when current hole has shots); moves all shots and putts from current hole to selected target hole, re-sequences shots, adjusts stroke counts on both holes
- [x] **Club selector as wheel Picker** — Replace `LazyColumn` list in `ClubPickerSheet` with `LazyColumn` + `rememberSnapFlingBehavior` wheel style; Confirm button submits selection
- [x] **Cancel button on club picker** — Rename "Skip" to "Cancel"; dismiss without recording any shot (no `UNKNOWN` club recorded)

#### 2026-04-28
- [x] **Shot auto-ordering by direction toward green** — When adding a shot in edit mode, insert it in sequence order based on proximity/direction toward the green among existing shots, rather than appending at the end
- [x] **Reorder shots in edit mode** — "Reorder Shots" button in edit mode opens a bottom sheet with up/down arrows to reorder shots, confirm to apply new sequence
- [x] **Cancel tap-to-add shot** — After tapping the map to place a new shot in edit mode, show a Cancel button to discard the placement before confirming
- [x] **Wrong zoom after delete/Done in edit mode** — Preserve current hole camera position after deleting a shot or tapping Done; don't reset to world zoom
- [x] **Accidental shot added after dragging** — Set a `suppressNextTap` flag on drag end in `ShotMapCoordinator` to prevent `onTapMap` firing on finger-lift after a drag

#### 2026-05-04
- [x] **Shot map pins not rendering** — Upgraded MapLibre `11.8.0` → `13.1.0`; rewrote `ShotMapCoordinator` to use `style.addImage()` with Canvas-drawn bitmaps instead of `SymbolLayer` with `textFont`, eliminating the glyph server dependency
- [x] **Shared club picker** — Extracted `ClubPickerSheet` to `ui/components/` so `ActiveRoundScreen` and `ScorecardsScreen` share the same scroll-snap implementation; bumped font size (`bodyLarge` / `titleLarge` for selected)
- [x] **Build modernization** — Gradle 9.4.1, AGP 9.2.0, Kotlin 2.3.21, KSP 2.3.7, Hilt 2.59.2, JVM 21; `org.gradle.java.home` moved to `local.properties`

#### 2026-06-25
- [x] **Sync Watch** — Mirror iOS `syncWatch()` introduced in commit `31cd8d3`. Add "Sync Watch" `DropdownMenuItem` to the round menu in `ActiveRoundScreen` (always visible, no reachability gate). Add `sendRoundStartContext(versionHash, courseId)` to `WearConnectivityService` (sends `/phone/action` data item with `action=roundStart`). Add `syncWear()` to `AppState` that calls `sendRoundStartContext`, `sendHoleDataToWatch()`, and `triggerMapSnapshots(course)`. Pass an `onSyncWatch: () -> Unit` lambda from `RoundScreen` down to `ActiveRoundScreen` to keep coupling loose.

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

---

## Tournament Scorecards

### Overview

A tournament view that aggregates multiple players' scorecards into a single leaderboard — like a printed tournament sheet. Players can collect each other's rounds via BLE or local WiFi, enter scores manually, and edit any entry. The view mirrors a standard golf tournament scorecard grid: players as rows, holes as columns, with running totals.

### Reference UI

Standard tournament scorecard grid (portrait, scrollable horizontally):
- Header row: player name | H1–H9 | Out | H10–H18 | In | Total | +/−
- One row per player, sorted by total score (best first)
- Color-coded score cells: eagle=yellow, birdie=green, par=white, bogey=orange, double+=red
- "You" row (own round) pinned or highlighted
- Tap a player row to expand full hole-by-hole detail
- Tap a cell to edit that player's score for that hole (if editable)

### Data Model

```kotlin
// core/model/TournamentPlayer.kt
@Serializable
data class TournamentPlayer(
    val id: String,                  // UUID
    val name: String,                // entered by host on receiver side
    val holes: List<HoleScore>,      // reuse existing HoleScore
    val source: PlayerSource,        // SELF | RECEIVED | MANUAL
)

enum class PlayerSource { SELF, RECEIVED, MANUAL }

// core/model/Tournament.kt
@Serializable
data class Tournament(
    val id: String,
    val title: String? = null,       // optional label e.g. "Saturday Scramble"
    val courseId: String,
    val courseName: String,
    val date: String,                // ISO-8601 date (yyyy-MM-dd)
    val players: List<TournamentPlayer>,
    val createdAt: String,
)
```

`TournamentStore` mirrors `RoundStore` — JSON files under `GoBirdie/tournaments/<id>.json`.

### Screens

| Screen | Description |
|--------|-------------|
| `TournamentsListScreen` | List of saved tournaments sorted by date; swipe-to-delete; FAB to create new |
| `TournamentDetailScreen` | Horizontally scrollable scorecard grid — players × holes; color-coded cells; sorted leaderboard |
| `CreateTournamentScreen` | Pick course (from saved courses), date picker, optional title; auto-adds own latest round as SELF |
| `EditPlayerScoreScreen` | Hole-by-hole score entry/edit for one player; +/− stepper per hole; par shown as reference |
| `CollectScoresScreen` | Camera QR scanner; after scan prompts host to enter player name; adds to tournament |

### Score Collection

mDNS/Bonjour only works on a shared LAN. On a golf course players are typically on cellular or have no shared network, so LAN-based discovery is not reliable. The following transports are viable:

| Transport | Infrastructure needed | Cross-platform | Friction |
|-----------|----------------------|----------------|----------|
| QR code | None | ✅ iOS + Android | Low — display/scan |
| WiFi hotspot + mDNS | One player creates hotspot, others join | ✅ | Medium — manual join |
| BLE | None | ✅ iOS + Android | Low — automatic scan |
| Nearby Connections API | None | ❌ Android-only | Low |

#### Option A — QR Code (P0, cross-platform)

Simplest zero-infrastructure approach. Works iOS ↔ Android with no extra setup.

- Sender taps "Share Score" → app generates a QR code containing the round's hole-by-hole scores as compact JSON
- Tournament host taps "Scan Score" → opens camera, scans QR → prompted to enter player name → round decoded and added as `TournamentPlayer(source = RECEIVED)`
- QR payload: course + date + 18 hole scores (strokes + putts per hole) — fits comfortably in a QR code
- iOS sender: add "Share Score" QR screen to GoBirdie iOS (small addition)
- Android sender: same screen in GoBirdie Android

#### Option B — WiFi Hotspot + mDNS (P1)

Reuses existing HTTP server infrastructure. One player creates a phone hotspot, others join, then mDNS discovery works within that subnet.

- Sender enables "Share Score" (same toggle as Desktop Sync) — starts HTTP server + mDNS
- Host opens `CollectScoresScreen`, scans for `_gobirdie._tcp` on the hotspot subnet
- Pulls round via `GET /api/rounds` + `GET /api/rounds/:id`
- Works iOS ↔ Android unchanged — both already speak the same protocol on port 7743
- Downside: requires all players to manually join the hotspot

#### Option C — BLE (P2, cross-platform)

No infrastructure needed, automatic discovery, works iOS ↔ Android.

- Sender advertises as BLE peripheral with a custom GoBirdie service UUID
- Host scans for BLE peripherals with that UUID, connects, reads score characteristic
- Score payload same compact JSON as QR option
- Requires CoreBluetooth additions on iOS and BluetoothLeScanner/Advertiser on Android
- Most seamless UX but highest implementation cost

#### Option D — Manual Entry (P0)

- "Add Player" button on `CreateTournamentScreen` / `TournamentDetailScreen`
- Enter player name, then step through holes 1–18 with a stroke stepper
- Saves as `TournamentPlayer(source = MANUAL)`
- Own round (if active or recently completed) auto-added as `source = SELF`

### Editing

- Long-press any player row in `TournamentDetailScreen` → edit menu (Edit Scores / Rename / Remove)
- `EditPlayerScoreScreen` shows all 18 holes with +/− steppers; par shown as reference
- Changes saved immediately to `TournamentStore`

### Navigation

Add **Tournaments** as a 5th bottom nav tab (icon: `Icons.Default.EmojiEvents`) between Scorecards and Map, or nest under Scorecards tab as a sub-screen — decide based on screen real estate.

### Milestones

#### M-T1 — Core (P0)
- [x] `Tournament` + `TournamentPlayer` + `PlayerSource` models with `kotlinx.serialization`
- [x] `TournamentStore` (JSON file I/O, mirrors `RoundStore`)
- [x] `TournamentsListScreen` — list, swipe-to-delete, FAB to create
- [x] `CreateTournamentScreen` — course picker, date picker, optional title; auto-adds own latest round as SELF
- [x] Manual player entry + `EditPlayerScoreScreen` (hole +/− steppers, par reference)
- [x] `TournamentDetailScreen` — horizontally scrollable grid (players × holes), color-coded cells, sorted by total, +/− column
- [x] Long-press player row → edit menu (Edit Scores / Rename / Remove)

#### M-T2 — QR Collection (P1)
- [x] `QrShareScreen` — full-screen QR on sender side (ZXing, `com.google.zxing:core:3.5.3`); QR icon in scorecard detail top bar
- [x] QR payload schema: `{c, d, h}` — course name, date, per-hole `[strokes, putts]`; player name entered by host after scan
- [x] `CollectScoresScreen` — camera QR scanner (`androidx.camera` + ZXing); after decode prompt host to enter player name; add as `TournamentPlayer(source = RECEIVED)`
- [ ] iOS: add matching "Share Score" QR screen to GoBirdie iOS
- [ ] Conflict handling: player already exists → prompt replace or keep both

#### M-T3 — Polish (P2)
- [ ] WiFi hotspot fallback — NSD browser for `_gobirdie._tcp`; pull via existing HTTP endpoints (works iOS ↔ Android when on same hotspot)
- [ ] BLE collection — advertise/scan custom GoBirdie service UUID; read score characteristic
- [ ] Export tournament as image (Canvas → JPEG share sheet)
- [ ] Handicap-adjusted net score column (optional toggle)
- [ ] Tap player row to expand full shot map (reuse `ShotMapView`)

