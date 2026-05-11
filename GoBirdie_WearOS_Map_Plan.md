# GoBirdie: Wear OS Map Implementation Plan

This document outlines the strategy for implementing a "Garmin-style" hole overview on the Wear OS watch for the GoBirdie Android app. The goal is a full-screen hole map with a live GPS dot on the watch, mirroring the Apple Watch plan but adapted for the Android/Wear OS stack.

---

## 1. Technical Strategy: Snapshot-and-Sync Architecture

MapLibre does not run on Wear OS. The phone renders hole map images using its live `MapLibreMap` instance and pushes them to the watch via the **Wearable Data Layer API**.

### Core Benefits
- **Battery efficiency:** The watch displays a static JPEG rather than rendering vector geometry.
- **Consistency:** The map uses the exact same MapLibre style and golf geometry layers already rendered in `MapScreen.kt` — fairways, bunkers, water, rough.
- **Offline resilience:** Images are stored in the watch's local `filesDir`. The map remains available even if Bluetooth drops mid-round.
- **No new dependencies:** Both sides already depend on `play-services-wearable:18.2.0`.

### Constraint: `MapLibreMap.snapshot()` Requires a Live Map

Unlike Apple's `MLNMapSnapshotter` (which is fully off-screen), `MapLibreMap.snapshot()` requires a rendered, attached `MapView`. Two approaches:

| Approach | Pros | Cons |
|---|---|---|
| **A. Hidden off-screen MapView** (recommended) | No UI dependency; works even if user is on Round tab | Must warm up style + tiles before snapshotting; adds complexity |
| **B. Reuse the active MapView** | Simpler; map is already loaded | Requires user to visit the Map tab; not reliable |

**Decision: Approach A.** Create a hidden `MapView` in `WearMapSnapshotManager`, add it to a `WindowManager` overlay (or attach to a `FrameLayout` in a background `Service`), load the style, then snapshot each hole sequentially. Detach and destroy after all snapshots are complete.

---

## 2. Navigation Model

Extend the existing `ActiveRoundPager` in `WatchRoundScreen.kt` from **2 pages to 3 pages**:

| Swipe | Page Index | Content |
|---|---|---|
| Swipe down | **0** | Full-screen hole map + GPS blue dot *(new)* |
| Default (landing) | **1** | Current `ScoringPage` — distances, shot, putts, hole nav |
| Swipe up | **2** | Current `EndRoundPage` — end/cancel round |

- Page 1 remains the landing page when a round starts (initial `page = 1`).
- Page 0 observes the same `holeNumber` from `WatchRoundSession` — no independent hole navigation on the map page.
- Horizontal swipe and rotary input on page 1 continue to navigate between holes, unchanged.
- Page dots at the bottom update from 2 to 3 dots.

---

## 3. Image Specifications

| Property | Value |
|---|---|
| Format | JPEG |
| Compression quality | 0.6 (matches Apple plan) |
| Dimensions | 384 × 384 px (renders at ~1.5× density for Wear OS round screens) |
| Orientation | Tee-to-Green up — bearing applied via `CameraPosition.Builder().bearing()` |
| Content | Fairway, green, bunkers, water, rough — no labels, no roads, no POIs |
| Style | Same OSM raster + golf GeoJSON layers as `MapScreen.kt` |

### Tee-to-Green Bearing

Reuse the existing `teeToPinBearing(tee, green)` function already in `MapScreen.kt`. Extract it to a shared location (e.g., a top-level function in `core/model` or a utility file) so both `MapScreen.kt` and `WearMapSnapshotManager` can use it without duplication.

```kotlin
// Already exists in MapScreen.kt — move to shared util
fun teeToPinBearing(tee: GpsPoint, green: GpsPoint): Double {
    val dLon = Math.toRadians(green.lon - tee.lon)
    val lat1 = Math.toRadians(tee.lat)
    val lat2 = Math.toRadians(green.lat)
    val y = sin(dLon) * cos(lat2)
    val x = cos(lat1) * sin(lat2) - sin(lat1) * cos(lat2) * cos(dLon)
    return (Math.toDegrees(atan2(y, x)) + 360) % 360
}
```

---

## 4. Data Model

### 4.1 HoleMapMeta

A lightweight data class shared between phone and watch (lives in `core/model`):

```kotlin
data class HoleMapMeta(
    val holeNumber: Int,
    val version: String,       // SHA-256 hash of all holes' tee+green coords
    val swLat: Double,
    val swLon: Double,
    val neLat: Double,
    val neLon: Double,
    val imageWidth: Int,
    val imageHeight: Int,
    val bearing: Double,
)
```

Serialized as JSON alongside each JPEG. The bounding box (`sw`/`ne`) is read from `map.projection.visibleRegion.latLngBounds` immediately after the snapshot callback fires.

### 4.2 GPS Dot Projection Formula

On the watch, project the player's GPS coordinate onto the JPEG pixel space using linear interpolation (accurate at single-hole scale):

```kotlin
fun project(lat: Double, lon: Double, meta: HoleMapMeta): Offset {
    val x = ((lon - meta.swLon) / (meta.neLon - meta.swLon) * meta.imageWidth).toFloat()
    val y = ((1.0 - (lat - meta.swLat) / (meta.neLat - meta.swLat)) * meta.imageHeight).toFloat()
    return Offset(x, y)
}
```

> ⚠️ No projection distortion correction needed at single-hole scale.

### 4.3 Snapshot Versioning

To avoid sending stale snapshots when course data changes:

1. Compute a version string: SHA-256 of the concatenated `"${hole.tee?.lat},${hole.tee?.lon},${hole.greenCenter?.lat},${hole.greenCenter?.lon}"` for all holes in order.
2. Store the version in each `HoleMapMeta`.
3. On round start, compare the stored version (from the watch's last-received metadata) against the freshly computed version.
4. If mismatched, regenerate all snapshots before transferring.
5. On the watch, reject any received file whose `version` does not match the version sent in the round-start `holeData` message.

---

## 5. Phone-Side Implementation

### 5.1 New File: `WearMapSnapshotManager.kt`

**Location:** `app/src/main/java/io/github/nicechester/gobirdie/connectivity/WearMapSnapshotManager.kt`

**Responsibilities:**
- Create and manage a hidden off-screen `MapView`.
- Load the OSM style + golf GeoJSON layers for each hole.
- Call `map.snapshot()` wrapped in a `suspendCoroutine` for async/await compatibility.
- Compute bearing and bounding box per hole.
- Compress to JPEG and hand off to `WearConnectivityService`.
- Destroy the hidden `MapView` when done.

**Key API:**
```kotlin
class WearMapSnapshotManager(private val context: Context) {

    suspend fun generateAndSend(
        course: Course,
        wearService: WearConnectivityService,
        version: String,
    )
}
```

**Snapshot loop pattern:**
```kotlin
// Must run on Dispatchers.Main (MapLibre requirement)
withContext(Dispatchers.Main) {
    val mapView = createHiddenMapView()
    val map = mapView.awaitMap()         // suspendCoroutine wrapper
    map.awaitStyle(osmStyleUri(context)) // suspendCoroutine wrapper

    for (hole in course.holes) {
        positionCamera(map, hole)
        delay(300) // allow tiles to render
        val bitmap = map.awaitSnapshot()
        val bounds = map.projection.visibleRegion.latLngBounds
        val meta = HoleMapMeta(
            holeNumber = hole.number,
            version = version,
            swLat = bounds.southWest.latitude,
            swLon = bounds.southWest.longitude,
            neLat = bounds.northEast.latitude,
            neLon = bounds.northEast.longitude,
            imageWidth = bitmap.width,
            imageHeight = bitmap.height,
            bearing = teeToPinBearing(hole.tee!!, hole.greenCenter!!),
        )
        val jpeg = bitmap.toJpeg(quality = 60)
        wearService.sendMapSnapshot(hole.number, jpeg, meta)
    }

    mapView.destroy()
}
```

**Hidden MapView creation:**

The `MapView` must be attached to a window to render. Use `WindowManager` to add it as a zero-size overlay, or attach it to the `Activity`'s root view off-screen. The cleanest approach for a `ViewModel`-owned manager is to pass the `Activity` reference temporarily during round start:

```kotlin
fun attach(activity: Activity) {
    val container = FrameLayout(activity)
    container.visibility = View.INVISIBLE
    activity.addContentView(container, ViewGroup.LayoutParams(1, 1))
    mapView = MapView(activity).also { container.addView(it) }
    mapView.onCreate(null)
    mapView.onStart()
    mapView.onResume()
}
```

> ⚠️ Call `mapView.onPause()`, `onStop()`, `onDestroy()` after all snapshots complete to avoid leaks.

### 5.2 Changes to `WearConnectivityService.kt`

Add `sendMapSnapshot()` using `DataClient.putDataItem()` with a `DataMap` asset. This is persistent (survives reconnect), unlike `sendMessage`.

```kotlin
fun sendMapSnapshot(holeNumber: Int, jpeg: ByteArray, meta: HoleMapMeta) {
    val request = PutDataMapRequest.create("/watch/holeMap/$holeNumber").apply {
        dataMap.putAsset("image", DataMapItem.Asset.createFromBytes(jpeg))
        dataMap.putInt("holeNumber", holeNumber)
        dataMap.putString("version", meta.version)
        dataMap.putDouble("swLat", meta.swLat)
        dataMap.putDouble("swLon", meta.swLon)
        dataMap.putDouble("neLat", meta.neLat)
        dataMap.putDouble("neLon", meta.neLon)
        dataMap.putInt("imageWidth", meta.imageWidth)
        dataMap.putInt("imageHeight", meta.imageHeight)
        dataMap.putDouble("bearing", meta.bearing)
    }.asPutDataRequest().setUrgent()
    Wearable.getDataClient(context).putDataItem(request)
}
```

### 5.3 Changes to `AppState.kt`

Add snapshot generation to `startRound()` and `resumeRound()`:

```kotlin
fun startRound(course: Course, startingHole: Int = 1): RoundSession {
    // ... existing code ...
    sendHoleDataToWatch()
    observeHoleChanges()
    triggerMapSnapshots(course)   // ← new
    return session
}

private fun triggerMapSnapshots(course: Course) {
    viewModelScope.launch {
        val version = computeCourseVersion(course)
        snapshotManager.generateAndSend(course, wearService, version)
    }
}

private fun computeCourseVersion(course: Course): String {
    val input = course.holes.joinToString("|") { h ->
        "${h.tee?.lat},${h.tee?.lon},${h.greenCenter?.lat},${h.greenCenter?.lon}"
    }
    return MessageDigest.getInstance("SHA-256")
        .digest(input.toByteArray())
        .joinToString("") { "%02x".format(it) }
        .take(16) // 16 hex chars is sufficient
}
```

`snapshotManager` is a new field on `AppState`, injected or constructed alongside `wearService`.

> ⚠️ `WearMapSnapshotManager.attach(activity)` must be called from the `Activity`, not the `ViewModel`. Wire this up in `MainActivity.onCreate()` after the `AppState` ViewModel is obtained.

---

## 6. Watch-Side Implementation

### 6.1 Changes to `DataLayerListenerService.kt`

Add a branch in `onDataChanged` for `/watch/holeMap/*` paths:

```kotlin
override fun onDataChanged(events: DataEventBuffer) {
    for (event in events) {
        val path = event.dataItem.uri.path ?: continue
        when {
            path == "/phone/holeData" -> {
                val dataMap = DataMapItem.fromDataItem(event.dataItem).dataMap
                WearSessionHolder.session?.handleMessageFromDataMap(dataMap)
            }
            path.startsWith("/watch/holeMap/") -> {
                val dataMap = DataMapItem.fromDataItem(event.dataItem).dataMap
                saveMapSnapshot(dataMap)
            }
        }
    }
    events.release()
}

private fun saveMapSnapshot(dataMap: DataMap) {
    val holeNumber = dataMap.getInt("holeNumber")
    val asset = dataMap.getAsset("image") ?: return
    val version = dataMap.getString("version") ?: return

    // Resolve asset bytes (blocking call — already on background thread)
    val result = Wearable.getDataClient(this)
        .getFdForAsset(asset).await()
    val jpeg = result.inputStream.readBytes()
    result.release()

    val dir = File(filesDir, "maps").also { it.mkdirs() }
    File(dir, "hole_$holeNumber.jpg").writeBytes(jpeg)

    val meta = HoleMapMeta(
        holeNumber = holeNumber,
        version = version,
        swLat = dataMap.getDouble("swLat"),
        swLon = dataMap.getDouble("swLon"),
        neLat = dataMap.getDouble("neLat"),
        neLon = dataMap.getDouble("neLon"),
        imageWidth = dataMap.getInt("imageWidth"),
        imageHeight = dataMap.getInt("imageHeight"),
        bearing = dataMap.getDouble("bearing"),
    )
    File(dir, "hole_$holeNumber.json").writeText(Json.encodeToString(meta))

    WearSessionHolder.session?.onMapReceived(holeNumber)
}
```

> `getFdForAsset` is a blocking call. `onDataChanged` is already called on a background thread by the Wearable framework, so this is safe.

### 6.2 Changes to `WatchRoundSession.kt`

Add map state tracking:

```kotlin
// New fields
val mapAvailableHoles = MutableStateFlow<Set<Int>>(emptySet())
val holeMapMeta = mutableMapOf<Int, HoleMapMeta>()

fun onMapReceived(holeNumber: Int) {
    val dir = File(context.filesDir, "maps")
    val metaFile = File(dir, "hole_$holeNumber.json")
    if (metaFile.exists()) {
        try {
            holeMapMeta[holeNumber] = Json.decodeFromString(metaFile.readText())
        } catch (_: Exception) {}
    }
    mapAvailableHoles.update { it + holeNumber }
}

fun loadMapBitmap(holeNumber: Int): Bitmap? {
    val file = File(context.filesDir, "maps/hole_$holeNumber.jpg")
    return if (file.exists()) BitmapFactory.decodeFile(file.absolutePath) else null
}

// In resetToWaiting() — add cleanup:
fun resetToWaiting() {
    // ... existing resets ...
    mapAvailableHoles.value = emptySet()
    holeMapMeta.clear()
    clearMapFiles()
}

private fun clearMapFiles() {
    File(context.filesDir, "maps").deleteRecursively()
}
```

### 6.3 New Composable: `WatchMapPage`

Added to `WatchRoundScreen.kt`:

```kotlin
@Composable
private fun WatchMapPage(session: WatchRoundSession) {
    val holeNum by session.holeNumber.collectAsState()
    val availableHoles by session.mapAvailableHoles.collectAsState()
    val isAvailable = holeNum in availableHoles

    Box(Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
        if (!isAvailable) {
            // Placeholder
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator(color = GolfGreen, modifier = Modifier.size(24.dp))
                Spacer(Modifier.height(8.dp))
                Text("Syncing map...", fontSize = 13.sp, color = Color.Gray)
            }
        } else {
            val bitmap = remember(holeNum) { session.loadMapBitmap(holeNum) }
            val meta = session.holeMapMeta[holeNum]

            if (bitmap != null) {
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = "Hole $holeNum map",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit,
                )
            }

            // GPS blue dot overlay
            if (meta != null) {
                val location by session.currentLocationFlow.collectAsState()
                if (location != null) {
                    val screenSize = LocalConfiguration.current
                    val dotOffset = remember(location, meta) {
                        project(location!!.latitude, location!!.longitude, meta)
                            .let { px ->
                                // Scale from image coords to screen coords
                                val scaleX = screenSize.screenWidthDp.toFloat() / meta.imageWidth
                                val scaleY = screenSize.screenHeightDp.toFloat() / meta.imageHeight
                                Offset(px.x * scaleX, px.y * scaleY)
                            }
                    }
                    Canvas(Modifier.fillMaxSize()) {
                        drawCircle(Color(0x3F2196F3), radius = 16.dp.toPx(), center = dotOffset)
                        drawCircle(Color(0xFF2196F3), radius = 8.dp.toPx(), center = dotOffset)
                        drawCircle(Color.White, radius = 8.dp.toPx(), center = dotOffset, style = Stroke(width = 2.dp.toPx()))
                    }
                }
            }
        }
    }
}
```

> `session.currentLocationFlow` is a new `StateFlow<Location?>` exposed from `WatchRoundSession` — the existing `currentLocation` private field needs to be promoted to a `MutableStateFlow`.

### 6.4 Changes to `WatchRoundScreen.kt` — `ActiveRoundPager`

```kotlin
@Composable
private fun ActiveRoundPager(session: WatchRoundSession) {
    var page by remember { mutableIntStateOf(1) }  // ← start on page 1 (scoring)

    Box(
        Modifier
            .fillMaxSize()
            .pointerInput(page) {
                var accumulated = 0f
                detectVerticalDragGestures(
                    onDragStart = { accumulated = 0f },
                    onDragEnd = {
                        if (accumulated < -60 && page > 0) page--
                        else if (accumulated > 60 && page < 2) page++
                    },
                    onVerticalDrag = { _, dragAmount -> accumulated += dragAmount },
                )
            }
    ) {
        when (page) {
            0 -> WatchMapPage(session)
            1 -> ScoringPage(session)
            2 -> EndRoundPage(session, onBack = { page = 1 })
        }

        // 3-dot page indicator
        Row(
            Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 4.dp)
                .clickable { page = (page + 1) % 3 },
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            PageDot(active = page == 0)
            PageDot(active = page == 1)
            PageDot(active = page == 2)
        }
    }
}
```

---

## 7. File & Storage Layout

```
watch filesDir/
└── maps/
    ├── hole_1.jpg
    ├── hole_1.json
    ├── hole_2.jpg
    ├── hole_2.json
    ...
    └── hole_18.jpg
    └── hole_18.json
```

- Keyed by hole number (1-based), not index.
- Entire `maps/` directory is deleted in `resetToWaiting()` on round end.
- No cross-round persistence needed — snapshots are regenerated each round (version hash check may skip regeneration if course data is unchanged).

---

## 8. Error Handling & Edge Cases

| Scenario | Handling |
|---|---|
| Watch not connected at round start | `DataClient.putDataItem` queues the item; delivered automatically when watch reconnects |
| Hole map not yet received | `WatchMapPage` shows "Syncing map..." placeholder |
| `getFdForAsset` fails | Log and skip; hole remains in "Syncing" state |
| `map.snapshot()` returns null | Retry once after 500ms; skip hole if still null |
| Watch storage full | `putDataItem` will fail silently; watch shows placeholder |
| Course data changes mid-round | Version hash mismatch triggers full regeneration on next round start |
| Dogleg holes | Camera anchors to green center (same logic as `animateToHole` in `MapScreen.kt`) |
| Round resumed after crash | `triggerMapSnapshots()` is called in `resumeRound()` — regenerates if version mismatches stored metadata |
| `MapView` lifecycle leak | `WearMapSnapshotManager` calls `onPause/onStop/onDestroy` in a `finally` block |

---

## 9. `currentLocationFlow` Promotion

`WatchRoundSession.currentLocation` is currently a private `var`. The map page needs to observe it reactively. Promote it:

```kotlin
// Before
private var currentLocation: Location? = null

// After
val currentLocationFlow = MutableStateFlow<Location?>(null)
private var currentLocation: Location?
    get() = currentLocationFlow.value
    set(value) { currentLocationFlow.value = value }
```

This is a non-breaking change — all existing internal usages of `currentLocation` continue to work.

---

## 10. Implementation Roadmap

| # | Task | Priority | File(s) |
|---|---|---|---|
| 1 | Move `teeToPinBearing` to shared util | High | `core/model` or new `GpsUtils.kt` |
| 2 | Add `HoleMapMeta` data class | High | `core/model/HoleMapMeta.kt` |
| 3 | `WearMapSnapshotManager` — hidden MapView + snapshot loop | High | new `app/connectivity/WearMapSnapshotManager.kt` |
| 4 | `WearConnectivityService.sendMapSnapshot()` | High | `WearConnectivityService.kt` |
| 5 | `AppState` — `triggerMapSnapshots()` + version hash | High | `AppState.kt` |
| 6 | `MainActivity` — `snapshotManager.attach(activity)` | High | `MainActivity.kt` |
| 7 | `DataLayerListenerService` — receive `/watch/holeMap/*` | High | `DataLayerListenerService.kt` |
| 8 | `WatchRoundSession` — `mapAvailableHoles`, `holeMapMeta`, `currentLocationFlow` | High | `WatchRoundSession.kt` |
| 9 | `WatchMapPage` composable + GPS dot projection | High | `WatchRoundScreen.kt` |
| 10 | `ActiveRoundPager` → 3-page layout (page 0 = map) | High | `WatchRoundScreen.kt` |
| 11 | Snapshot invalidation (version hash comparison) | Medium | `WearMapSnapshotManager.kt` |
| 12 | Map file cleanup on `resetToWaiting()` | Medium | `WatchRoundSession.kt` |
| 13 | "Syncing map..." placeholder with progress indicator | Medium | `WatchRoundScreen.kt` |
| 14 | Retry logic for failed snapshots | Low | `WearMapSnapshotManager.kt` |

---

## 11. Key Differences from Apple Plan

| Aspect | Apple | Android |
|---|---|---|
| Snapshot engine | `MLNMapSnapshotter` (fully off-screen, background thread) | `MapLibreMap.snapshot()` (requires attached `MapView`, main thread) |
| Transfer mechanism | `WCSession.transferFile` | `DataClient.putDataItem` with `Asset` |
| Receive on watch | `session(_:didReceive:)` | `onDataChanged` in `WearableListenerService` |
| Asset resolution | Automatic | `getDataClient().getFdForAsset()` (blocking, background thread) |
| Page navigation | `TabView(.page)` vertical | Manual `page` state + `detectVerticalDragGestures` (already in use) |
| GPS dot update | `@ObservableObject` round state | `StateFlow<Location?>` collected in Compose |
| Storage cleanup | `Documents` directory, manual delete | `filesDir/maps/`, `deleteRecursively()` |

---

## 12. No New Dependencies Required

Both `app` and `wear` modules already include `play-services-wearable:18.2.0`. `MapLibre` (`android-sdk-opengl:13.1.0`) is already in `app`. No additions to either `build.gradle.kts` are needed.
