# GoBirdie Android

A golf GPS and shot tracking app for Android and Wear OS, with desktop sync via [GoBirdie Desktop](https://github.com/nicechester/GoBirdie-Desktop).

Course data is sourced from OpenStreetMap (Overpass API) and enriched with yardage/handicap from GolfCourseAPI.

<table>
  <tr>
    <td valign="center" width=300><img src="screenshots/rounding-view.png" width="200"><br>Android</td>
    <td valign="center" width=300><img src="screenshots/watch-rounding.png" width="100"><br>Wear OS</td>
  </tr>
</table>

## Features

- Live GPS distances to front, pin, and back of green
- Mark shots with GPS location and club selection
- Shot map with club-colored dots and distance lines
- Tap-to-measure anywhere on the map
- Course discovery via Overpass API and search by name
- Offline course downloads
- Hole geometry rendering (fairways, bunkers, water)
- Mini scorecard with running total
- Auto-save and crash recovery
- Wear OS companion with wrist distances, shot marking, and club picker
- Health Services ExerciseClient keeps GPS alive on watch

## Install from APK

Download the latest APK files from the [Releases](https://github.com/nicechester/GoBirdie-Android/releases) page.

### Phone

1. Download `app-release.apk` to your phone
2. Open the file and tap **Install** (you may need to enable "Install from unknown sources" in Settings → Security)
3. Open GoBirdie and grant location permission when prompted

### Wear OS Watch

The watch APK must be sideloaded via ADB since it's not on the Play Store.

1. Enable **Developer Options** on your watch: Settings → System → About → tap Build Number 7 times
2. Enable **ADB Debugging** and **Debug over WiFi** in Settings → Developer Options
3. Note the watch's IP address shown under "Debug over WiFi" (e.g. `192.168.1.100:5555`)
4. On your computer with [ADB](https://developer.android.com/tools/adb) installed:

```bash
# Connect to the watch
adb connect 192.168.1.100:5555

# Install the watch APK
adb install wear-release.apk
```

5. Open GoBirdie on the watch and grant location and body sensor permissions
6. Start a round on the phone — the watch will automatically receive hole data

## Build from Source

Requires Android Studio and JDK 17.

```bash
git clone https://github.com/nicechester/GoBirdie-Android.git
cd GoBirdie-Android
./gradlew :app:assembleDebug      # phone
./gradlew :wear:assembleDebug     # watch
```

## User Manual

See [MANUAL.md](MANUAL.md) for the full user guide with screenshots.

## License

This project is for personal use.
