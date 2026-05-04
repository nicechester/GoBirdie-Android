package io.github.nicechester.gobirdie.ui.scorecards

import android.content.Context
import android.graphics.*
import io.github.nicechester.gobirdie.core.model.*
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression.*
import org.maplibre.android.style.layers.*
import org.maplibre.android.style.layers.PropertyFactory.*
import org.maplibre.android.style.sources.GeoJsonSource
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.*

class ShotMapCoordinator(private val context: Context) {

    private var map: MapLibreMap? = null
    private var shots: List<Shot> = emptyList()
    private var courseHole: Hole? = null
    private var holeScore: HoleScore? = null
    private var selectedShotId: String? = null

    var onTapShot: (String) -> Unit = {}
    var onTapMap: (GpsPoint) -> Unit = {}
    var onMoveShot: (String, GpsPoint) -> Unit = { _, _ -> }

    private var draggingShotId: String? = null
    private var suppressNextTap = false

    fun attach(map: MapLibreMap) {
        this.map = map

        map.addOnMapClickListener { latLng ->
            if (suppressNextTap) { suppressNextTap = false; return@addOnMapClickListener true }
            val tapped = GpsPoint(latLng.latitude, latLng.longitude)
            val hit = shots.firstOrNull { s ->
                val dist = s.location.distanceMeters(tapped)
                dist < 30.0
            }
            if (hit != null) {
                onTapShot(hit.id)
            } else {
                onTapMap(tapped)
            }
            true
        }

        map.addOnMapLongClickListener { latLng ->
            val tapped = GpsPoint(latLng.latitude, latLng.longitude)
            val hit = shots.firstOrNull { s -> s.location.distanceMeters(tapped) < 30.0 }
            if (hit != null) {
                draggingShotId = hit.id
                map.uiSettings.isScrollGesturesEnabled = false
            }
            true
        }
    }

    fun onDragEnd(latLng: LatLng) {
        val sid = draggingShotId ?: return
        draggingShotId = null
        suppressNextTap = true
        map?.uiSettings?.isScrollGesturesEnabled = true
        onMoveShot(sid, GpsPoint(latLng.latitude, latLng.longitude))
    }

    fun update(
        shots: List<Shot>,
        courseHole: Hole?,
        holeScore: HoleScore,
        selectedShotId: String?,
        moveCamera: Boolean = false,
    ) {
        val holeChanged = courseHole != this.courseHole
        this.shots = shots
        this.courseHole = courseHole
        this.holeScore = holeScore
        this.selectedShotId = selectedShotId
        redraw()
        if (moveCamera || holeChanged) cameraToHole()
    }

    private fun cameraToHole() {
        val m = map ?: return
        val tee = courseHole?.tee
        val green = courseHole?.greenCenter
        if (tee != null && green != null) {
            val dLon = Math.toRadians(green.lon - tee.lon)
            val lat1 = Math.toRadians(tee.lat)
            val lat2 = Math.toRadians(green.lat)
            val y = sin(dLon) * cos(lat2)
            val x = cos(lat1) * sin(lat2) - sin(lat1) * cos(lat2) * cos(dLon)
            val bearing = (Math.toDegrees(atan2(y, x)) + 360) % 360
            val distMeters = tee.distanceMeters(green)
            val zoom = when {
                distMeters > 500 -> 15.5; distMeters > 300 -> 16.0
                distMeters > 150 -> 16.5; else -> 17.0
            }
            val center = LatLng(
                (tee.lat + green.lat) / 2 + (green.lat - tee.lat) * 0.1,
                (tee.lon + green.lon) / 2 + (green.lon - tee.lon) * 0.1,
            )
            m.animateCamera(CameraUpdateFactory.newCameraPosition(
                CameraPosition.Builder().target(center).zoom(zoom).bearing(bearing).tilt(0.0).build()
            ), 600)
        } else {
            val pts = shots.map { LatLng(it.location.lat, it.location.lon) }
            val target = if (pts.isNotEmpty())
                LatLng(pts.map { it.latitude }.average(), pts.map { it.longitude }.average())
            else LatLng(0.0, 0.0)
            m.animateCamera(CameraUpdateFactory.newCameraPosition(
                CameraPosition.Builder().target(target).zoom(16.0).build()
            ), 600)
        }
    }

    private fun redraw() {
        val style = map?.style ?: return
        val sortedShots = shots.sortedBy { it.sequence }

        // Remove old layers + sources
        listOf("shot-lines", "shot-pins", "shot-labels", "putt-label").forEach { id ->
            style.getLayer(id)?.let { style.removeLayer(it) }
        }
        listOf("shot-lines-src", "shot-pins-src", "shot-labels-src", "putt-label-src").forEach { id ->
            style.getSource(id)?.let { style.removeSource(it) }
        }

        // ── Lines (tee → shots → green) ──
        val lineFeatures = JSONArray()
        data class Pt(val latLng: LatLng, val gps: GpsPoint, val club: ClubType?)
        val chain = mutableListOf<Pt>()
        courseHole?.tee?.let { chain.add(Pt(LatLng(it.lat, it.lon), it, null)) }
        sortedShots.forEach { chain.add(Pt(LatLng(it.location.lat, it.location.lon), it.location, it.club)) }
        courseHole?.greenCenter?.let { chain.add(Pt(LatLng(it.lat, it.lon), it, null)) }

        for (i in 1 until chain.size) {
            val from = chain[i - 1]; val to = chain[i]
            val club = to.club ?: from.club ?: ClubType.UNKNOWN
            val coords = JSONArray().apply {
                put(JSONArray().put(from.latLng.longitude).put(from.latLng.latitude))
                put(JSONArray().put(to.latLng.longitude).put(to.latLng.latitude))
            }
            lineFeatures.put(JSONObject().apply {
                put("type", "Feature")
                put("geometry", JSONObject().apply { put("type", "LineString"); put("coordinates", coords) })
                put("properties", JSONObject().apply { put("color", clubColorHex(club)) })
            })
        }
        style.addSource(GeoJsonSource("shot-lines-src", featureCollection(lineFeatures).toString()))
        style.addLayer(LineLayer("shot-lines", "shot-lines-src").apply {
            setProperties(
                lineColor(get("color")),
                lineWidth(3f),
                lineDasharray(arrayOf(2f, 1.5f)),
            )
        })

        // ── Distance labels at midpoints ──
        val labelFeatures = JSONArray()
        for (i in 1 until chain.size) {
            val from = chain[i - 1]; val to = chain[i]
            val yards = from.gps.distanceYards(to.gps)
            if (yards > 0) {
                val mid = LatLng(
                    (from.latLng.latitude + to.latLng.latitude) / 2,
                    (from.latLng.longitude + to.latLng.longitude) / 2,
                )
                labelFeatures.put(JSONObject().apply {
                    put("type", "Feature")
                    put("geometry", JSONObject().apply {
                        put("type", "Point")
                        put("coordinates", JSONArray().put(mid.longitude).put(mid.latitude))
                    })
                    put("properties", JSONObject().apply { put("label", "${yards}y") })
                })
            }
        }
        style.addSource(GeoJsonSource("shot-labels-src", featureCollection(labelFeatures).toString()))
        style.addLayer(SymbolLayer("shot-labels", "shot-labels-src").apply {
            setProperties(
                textField(get("label")),
                textSize(11f),
                textColor("#FFFFFF"),
                textHaloColor("#000000"),
                textHaloWidth(1.5f),
                textFont(arrayOf("Open Sans Bold", "Arial Unicode MS Bold")),
            )
        })

        // ── Shot pins ──
        val pinFeatures = JSONArray()
        sortedShots.forEach { s ->
            val isSelected = s.id == selectedShotId
            val icon = shotIconName(s.club, isSelected)
            ensureShotIcon(style, s.club, isSelected)
            pinFeatures.put(JSONObject().apply {
                put("type", "Feature")
                put("geometry", JSONObject().apply {
                    put("type", "Point")
                    put("coordinates", JSONArray().put(s.location.lon).put(s.location.lat))
                })
                put("properties", JSONObject().apply {
                    put("icon", icon)
                    put("label", s.club.shortName)
                    put("shotId", s.id)
                })
            })
        }
        style.addSource(GeoJsonSource("shot-pins-src", featureCollection(pinFeatures).toString()))
        style.addLayer(SymbolLayer("shot-pins", "shot-pins-src").apply {
            setProperties(
                iconImage(get("icon")),
                iconAllowOverlap(true),
                iconSize(1f),
                textField(get("label")),
                textSize(10f),
                textColor("#FFFFFF"),
                textFont(arrayOf("Open Sans Bold", "Arial Unicode MS Bold")),
                textOffset(arrayOf(0f, 0f)),
                textAllowOverlap(true),
            )
        })

        // ── Putt label at green ──
        val putts = holeScore?.putts ?: 0
        if (putts > 0) {
            courseHole?.greenCenter?.let { green ->
                val puttFeature = JSONObject().apply {
                    put("type", "Feature")
                    put("geometry", JSONObject().apply {
                        put("type", "Point")
                        put("coordinates", JSONArray().put(green.lon).put(green.lat))
                    })
                    put("properties", JSONObject().apply { put("label", "$putts putts") })
                }
                style.addSource(GeoJsonSource("putt-label-src",
                    featureCollection(JSONArray().put(puttFeature)).toString()))
                style.addLayer(SymbolLayer("putt-label", "putt-label-src").apply {
                    setProperties(
                        textField(get("label")),
                        textSize(11f),
                        textColor("#FFFFFF"),
                        textHaloColor("#2E7D32"),
                        textHaloWidth(2f),
                        textFont(arrayOf("Open Sans Bold", "Arial Unicode MS Bold")),
                        textOffset(arrayOf(0f, 2f)),
                    )
                })
            }
        }
    }

    fun insertionIndex(newPoint: GpsPoint, existing: List<Shot>, green: GpsPoint?): Int {
        if (existing.isEmpty() || green == null) return existing.size
        val newDist = newPoint.distanceMeters(green)
        val idx = existing.indexOfFirst { it.location.distanceMeters(green) < newDist }
        return if (idx < 0) existing.size else idx
    }

    private fun ensureShotIcon(mapStyle: Style, club: ClubType, selected: Boolean) {
        val name = shotIconName(club, selected)
        if (mapStyle.getImage(name) != null) return
        val size = 48
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val cx = size / 2f; val cy = size / 2f; val r = size / 2f - 2f
        canvas.drawCircle(cx, cy, r, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.style = Paint.Style.FILL; color = clubColorInt(club)
        })
        canvas.drawCircle(cx, cy, r, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.style = Paint.Style.STROKE; color = Color.WHITE; strokeWidth = if (selected) 4f else 2f
        })
        if (selected) canvas.drawCircle(cx, cy, r + 3f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.style = Paint.Style.STROKE; color = Color.WHITE; strokeWidth = 2f
        })
        mapStyle.addImage(name, bmp)
    }

    private fun shotIconName(club: ClubType, selected: Boolean) =
        "shot-${club.name.lowercase()}${if (selected) "-sel" else ""}"

    private fun clubColorInt(club: ClubType): Int = when (club) {
        ClubType.DRIVER -> Color.RED
        ClubType.WOOD_3, ClubType.WOOD_5 -> Color.rgb(255, 152, 0)
        ClubType.HYBRID_3, ClubType.HYBRID_4, ClubType.HYBRID_5 -> Color.rgb(0, 150, 136)
        ClubType.IRON_4, ClubType.IRON_5, ClubType.IRON_6,
        ClubType.IRON_7, ClubType.IRON_8, ClubType.IRON_9 -> Color.rgb(33, 150, 243)
        ClubType.PITCHING_WEDGE, ClubType.GAP_WEDGE,
        ClubType.SAND_WEDGE, ClubType.LOB_WEDGE -> Color.rgb(156, 39, 176)
        ClubType.PUTTER -> Color.rgb(46, 125, 50)
        ClubType.UNKNOWN -> Color.GRAY
    }

    private fun clubColorHex(club: ClubType): String {
        val c = clubColorInt(club)
        return "#%06X".format(c and 0xFFFFFF)
    }

    private fun featureCollection(features: JSONArray) = JSONObject().apply {
        put("type", "FeatureCollection"); put("features", features)
    }
}
