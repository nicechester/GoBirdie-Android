package io.github.nicechester.gobirdie.connectivity

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import io.github.nicechester.gobirdie.BuildConfig
import io.github.nicechester.gobirdie.core.model.Course
import io.github.nicechester.gobirdie.core.model.HoleMapMeta
import io.github.nicechester.gobirdie.core.model.teeToPinBearing
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.snapshotter.MapSnapshot
import org.maplibre.android.snapshotter.MapSnapshotter
import java.io.ByteArrayOutputStream
import java.io.File
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.log2
import kotlin.math.max

private const val TAG = "WearMapSnapshot"

class WearMapSnapshotManager(private val context: Context) {

    fun attach(activity: android.app.Activity) {
        MapLibre.getInstance(activity)
        if (BuildConfig.DEBUG) Log.d(TAG, "attach() called")
    }

    suspend fun generateAndSend(course: Course, wearService: WearConnectivityService, version: String) {
        if (BuildConfig.DEBUG) Log.d(TAG, "generateAndSend: starting for ${course.holes.size} holes, version=$version")
        withContext(Dispatchers.Main) {
            try {
                val styleUri = osmStyleUri()
                for (hole in course.holes) {
                    val tee = hole.tee ?: run {
                        Log.w(TAG, "hole ${hole.number}: skipping, no tee")
                        continue
                    }
                    val green = hole.greenCenter ?: run {
                        Log.w(TAG, "hole ${hole.number}: skipping, no greenCenter")
                        continue
                    }
                    val bearing = teeToPinBearing(tee, green)
                    val center = LatLng(
                        (tee.lat + green.lat) / 2,
                        (tee.lon + green.lon) / 2,
                    )
                    val distMeters = tee.distanceMeters(green)
                    val altitude = max(distMeters * 3.5, 200.0)
                    val zoom = 18.2 - log2(altitude / 200.0)

                    if (BuildConfig.DEBUG) Log.d(TAG, "hole ${hole.number}: dist=${distMeters.toInt()}m zoom=${"%.2f".format(zoom)} bearing=${"%.1f".format(bearing)}")

                    val options = MapSnapshotter.Options(384, 384)
                        .withStyleBuilder(
                            org.maplibre.android.maps.Style.Builder().fromUri(styleUri)
                        )
                        .withCameraPosition(
                            CameraPosition.Builder()
                                .target(center)
                                .zoom(zoom)
                                .bearing(bearing)
                                .tilt(0.0)
                                .build()
                        )

                    val snapshot = try {
                        awaitSnapshot(options)
                    } catch (e: Exception) {
                        Log.e(TAG, "hole ${hole.number}: snapshotter failed", e)
                        continue
                    }

                    val bitmap = snapshot.bitmap
                    val sw = snapshot.latLngForPixel(android.graphics.PointF(0f, bitmap.height.toFloat()))
                    val ne = snapshot.latLngForPixel(android.graphics.PointF(bitmap.width.toFloat(), 0f))
                    val meta = HoleMapMeta(
                        holeNumber = hole.number,
                        version = version,
                        swLat = sw.latitude,
                        swLon = sw.longitude,
                        neLat = ne.latitude,
                        neLon = ne.longitude,
                        imageWidth = bitmap.width,
                        imageHeight = bitmap.height,
                        bearing = bearing,
                    )
                    val jpeg = bitmap.toJpeg(quality = 60)
                    if (BuildConfig.DEBUG) {
                        Log.d(TAG, "hole ${hole.number}: snapshot ${bitmap.width}x${bitmap.height}, jpeg=${jpeg.size} bytes")
                        saveDebugSnapshot(hole.number, jpeg)
                    }
                    wearService.sendMapSnapshot(hole.number, jpeg, meta)
                    if (BuildConfig.DEBUG) Log.d(TAG, "hole ${hole.number}: sent to watch")
                }
                if (BuildConfig.DEBUG) Log.d(TAG, "generateAndSend: all holes done")
            } catch (e: Exception) {
                Log.e(TAG, "generateAndSend failed", e)
            }
        }
    }

    private suspend fun awaitSnapshot(options: MapSnapshotter.Options): MapSnapshot =
        suspendCancellableCoroutine { cont ->
            val snapshotter = MapSnapshotter(context, options)
            cont.invokeOnCancellation { snapshotter.cancel() }
            snapshotter.start(
                { snapshot -> cont.resume(snapshot) },
                { error -> cont.resumeWithException(RuntimeException(error)) }
            )
        }

    private fun saveDebugSnapshot(holeNumber: Int, jpeg: ByteArray) {
        try {
            val dir = File(context.cacheDir, "snapshots").also { it.mkdirs() }
            val file = File(dir, "snapshot_hole$holeNumber.jpg")
            file.writeBytes(jpeg)
            Log.d(TAG, "saved debug snapshot: ${file.absolutePath}")
        } catch (e: Exception) {
            Log.e(TAG, "failed to save debug snapshot for hole $holeNumber", e)
        }
    }

    private fun Bitmap.toJpeg(quality: Int): ByteArray {
        val out = ByteArrayOutputStream()
        compress(Bitmap.CompressFormat.JPEG, quality, out)
        return out.toByteArray()
    }

    private fun osmStyleUri(): String {
        val json = """
            {
                "version": 8,
                "sources": {
                    "osm": {
                        "type": "raster",
                        "tiles": ["https://tile.openstreetmap.org/{z}/{x}/{y}.png"],
                        "tileSize": 256,
                        "minzoom": 0,
                        "maxzoom": 19
                    }
                },
                "layers": [{"id": "osm", "type": "raster", "source": "osm"}]
            }
        """.trimIndent()
        val file = File(context.cacheDir, "gobirdie-style.json")
        file.writeText(json)
        return "file://${file.absolutePath}"
    }
}
