package io.github.nicechester.gobirdie.ui.scorecards

import android.content.Context
import android.graphics.*
import android.view.MotionEvent
import io.github.nicechester.gobirdie.core.model.*
import org.maplibre.android.annotations.*
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import kotlin.math.*

class ShotMapCoordinator(private val context: Context) {

    private var map: MapLibreMap? = null
    private var shots: List<Shot> = emptyList()
    private var courseHole: Hole? = null
    private var holeScore: HoleScore? = null
    private var editMode: Boolean = false
    private var selectedShotId: String? = null

    var onTapShot: (String) -> Unit = {}
    var onTapMap: (GpsPoint) -> Unit = {}
    var onMoveShot: (String, GpsPoint) -> Unit = { _, _ -> }

    // annotation id → shot id
    private val markerToShot = mutableMapOf<Long, String>()
    private var draggingShotMarkerId: Long? = null
    private var draggingDownX = 0f
    private var draggingDownY = 0f

    fun attach(map: MapLibreMap) {
        this.map = map
    }

    fun cameraToHole(shots: List<Shot>) {
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
                distMeters > 500 -> 15.5
                distMeters > 300 -> 16.0
                distMeters > 150 -> 16.5
                else -> 17.0
            }
            val center = LatLng(
                (tee.lat + green.lat) / 2 + (green.lat - tee.lat) * 0.1,
                (tee.lon + green.lon) / 2 + (green.lon - tee.lon) * 0.1,
            )
            m.animateCamera(
                CameraUpdateFactory.newCameraPosition(
                    CameraPosition.Builder().target(center).zoom(zoom).bearing(bearing).tilt(0.0).build()
                ), 600,
            )
        } else {
            val allPoints = shots.map { LatLng(it.location.lat, it.location.lon) }
            val target = if (allPoints.isNotEmpty())
                LatLng(allPoints.map { it.latitude }.average(), allPoints.map { it.longitude }.average())
            else LatLng(0.0, 0.0)
            m.animateCamera(CameraUpdateFactory.newCameraPosition(
                CameraPosition.Builder().target(target).zoom(16.0).build()
            ), 600)
        }
    }

    fun update(
        shots: List<Shot>,
        courseHole: Hole?,
        holeScore: HoleScore,
        editMode: Boolean,
        selectedShotId: String?,
        moveCamera: Boolean = false,
    ) {
        val shotsChanged = shots != this.shots
        val selectionChanged = selectedShotId != this.selectedShotId
        val editChanged = editMode != this.editMode
        val holeChanged = courseHole != this.courseHole

        this.shots = shots
        this.courseHole = courseHole
        this.holeScore = holeScore
        this.editMode = editMode
        this.selectedShotId = selectedShotId

        if (shotsChanged || holeChanged || editChanged || selectionChanged) {
            redraw()
        }
        if (moveCamera || holeChanged) {
            cameraToHole(shots)
        }
    }

    private fun redraw() {
        val m = map ?: return
        m.clear()
        markerToShot.clear()

        val hole = courseHole
        val sortedShots = shots.sortedBy { it.sequence }

        // Build chain: tee → shots → green
        data class ChainPoint(val latLng: LatLng, val gps: GpsPoint, val club: ClubType?)
        val chain = mutableListOf<ChainPoint>()
        hole?.tee?.let { chain.add(ChainPoint(LatLng(it.lat, it.lon), it, null)) }
        sortedShots.forEach { s -> chain.add(ChainPoint(LatLng(s.location.lat, s.location.lon), s.location, s.club)) }
        hole?.greenCenter?.let { chain.add(ChainPoint(LatLng(it.lat, it.lon), it, null)) }

        // Polylines + distance labels
        for (i in 1 until chain.size) {
            val from = chain[i - 1]
            val to = chain[i]
            val club = to.club ?: from.club ?: ClubType.UNKNOWN
            m.addPolyline(
                PolylineOptions()
                    .add(from.latLng, to.latLng)
                    .color(clubColor(club))
                    .width(3f)
            )
            val yards = from.gps.distanceYards(to.gps)
            if (yards > 0) {
                val mid = LatLng(
                    (from.latLng.latitude + to.latLng.latitude) / 2,
                    (from.latLng.longitude + to.latLng.longitude) / 2,
                )
                m.addMarker(
                    MarkerOptions()
                        .position(mid)
                        .icon(distanceLabelIcon("${yards}y"))
                )
            }
        }

        // Shot markers
        sortedShots.forEach { s ->
            val isSelected = s.id == selectedShotId
            val marker = m.addMarker(
                MarkerOptions()
                    .position(LatLng(s.location.lat, s.location.lon))
                    .icon(shotIcon(s.club, isSelected))
            )
            markerToShot[marker.id] = s.id
        }

        // Putt label at green
        val putts = holeScore?.putts ?: 0
        if (putts > 0) {
            hole?.greenCenter?.let {
                m.addMarker(
                    MarkerOptions()
                        .position(LatLng(it.lat, it.lon))
                        .icon(puttLabelIcon("$putts putts"))
                )
            }
        }
    }

    // ── Touch handling (called from MapView.onTouchEvent override) ──

    fun onTouchEvent(event: MotionEvent): Boolean {
        val m = map ?: return false

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                draggingDownX = event.x
                draggingDownY = event.y
                val hit = hitTestShot(event.x, event.y)
                if (hit != null) {
                    draggingShotMarkerId = hit.first
                    m.uiSettings.isScrollGesturesEnabled = false
                    m.uiSettings.isZoomGesturesEnabled = false
                    return true
                }
                return false
            }
            MotionEvent.ACTION_MOVE -> {
                if (draggingShotMarkerId != null) {
                    // visual drag feedback — move the marker
                    val coord = m.projection.fromScreenLocation(PointF(event.x, event.y))
                    m.markers.find { it.id == draggingShotMarkerId }?.position = coord
                    return true
                }
                return false
            }
            MotionEvent.ACTION_UP -> {
                val dx = event.x - draggingDownX
                val dy = event.y - draggingDownY
                val moved = sqrt(dx * dx + dy * dy) > 10f

                val dragId = draggingShotMarkerId
                if (dragId != null) {
                    m.uiSettings.isScrollGesturesEnabled = true
                    m.uiSettings.isZoomGesturesEnabled = true
                    val shotId = markerToShot[dragId]
                    if (shotId != null) {
                        if (moved) {
                            val coord = m.projection.fromScreenLocation(PointF(event.x, event.y))
                            onMoveShot(shotId, GpsPoint(coord.latitude, coord.longitude))
                        } else {
                            onTapShot(shotId)
                        }
                    }
                    draggingShotMarkerId = null
                    return true
                }

                // Tap on empty map
                if (!moved) {
                    val hit = hitTestShot(event.x, event.y)
                    if (hit != null) {
                        onTapShot(hit.second)
                        return true
                    }
                    if (editMode) {
                        val coord = m.projection.fromScreenLocation(PointF(event.x, event.y))
                        onTapMap(GpsPoint(coord.latitude, coord.longitude))
                        return true
                    }
                }
                return false
            }
            MotionEvent.ACTION_CANCEL -> {
                draggingShotMarkerId = null
                map?.uiSettings?.isScrollGesturesEnabled = true
                map?.uiSettings?.isZoomGesturesEnabled = true
                return false
            }
        }
        return false
    }

    private fun hitTestShot(x: Float, y: Float): Pair<Long, String>? {
        val m = map ?: return null
        for ((markerId, shotId) in markerToShot) {
            val marker = m.markers.find { it.id == markerId } ?: continue
            val px = m.projection.toScreenLocation(marker.position)
            val dx = px.x - x
            val dy = px.y - y
            if (sqrt(dx * dx + dy * dy) < 48f) return Pair(markerId, shotId)
        }
        return null
    }

    // ── Icon factories ──

    private fun clubColor(club: ClubType): Int = when (club) {
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

    private fun shotIcon(club: ClubType, selected: Boolean): Icon {
        val size = 56
        val bmp = Bitmap.createBitmap(size + 8, size + 8, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val cx = bmp.width / 2f
        val cy = bmp.height / 2f

        val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = clubColor(club)
        }
        val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            color = Color.WHITE
            strokeWidth = if (selected) 4f else 2f
        }
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 26f
            isFakeBoldText = true
            textAlign = Paint.Align.CENTER
        }

        canvas.drawCircle(cx, cy, size / 2f, fillPaint)
        canvas.drawCircle(cx, cy, size / 2f, strokePaint)
        if (selected) {
            strokePaint.strokeWidth = 2f
            canvas.drawCircle(cx, cy, size / 2f + 4f, strokePaint)
        }
        canvas.drawText(club.shortName, cx, cy + 9f, textPaint)

        return IconFactory.getInstance(context).fromBitmap(bmp)
    }

    private fun distanceLabelIcon(text: String): Icon {
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 32f
            isFakeBoldText = true
            textAlign = Paint.Align.CENTER
        }
        val w = textPaint.measureText(text) + 20f
        val h = 36f
        val bmp = Bitmap.createBitmap(w.toInt(), h.toInt(), Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(178, 0, 0, 0)
        }
        canvas.drawRoundRect(0f, 0f, w, h, 8f, 8f, bgPaint)
        canvas.drawText(text, w / 2f, h - 8f, textPaint)
        return IconFactory.getInstance(context).fromBitmap(bmp)
    }

    private fun puttLabelIcon(text: String): Icon {
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 32f
            isFakeBoldText = true
            textAlign = Paint.Align.CENTER
        }
        val w = textPaint.measureText(text) + 20f
        val h = 40f
        val bmp = Bitmap.createBitmap(w.toInt(), h.toInt(), Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(46, 125, 50)
        }
        canvas.drawRoundRect(0f, 0f, w, h, 20f, 20f, bgPaint)
        canvas.drawText(text, w / 2f, h - 8f, textPaint)
        return IconFactory.getInstance(context).fromBitmap(bmp)
    }
}
