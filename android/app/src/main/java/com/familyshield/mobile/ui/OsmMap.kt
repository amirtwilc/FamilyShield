package com.familyshield.mobile.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color as AndroidColor
import android.graphics.Paint
import android.graphics.Point
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.familyshield.mobile.net.Zone
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Overlay
import org.osmdroid.views.overlay.Polygon
import org.osmdroid.views.overlay.Polyline
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sin

private fun newMap(context: Context, zoom: Double): MapView = MapView(context).apply {
    setTileSource(TileSourceFactory.MAPNIK)
    setMultiTouchControls(true)
    controller.setZoom(zoom)
}

@Composable
private fun lifecycleBind(mapView: MapView) {
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val obs = LifecycleEventObserver { _, e ->
            when (e) {
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                else -> Unit
            }
        }
        owner.lifecycle.addObserver(obs)
        onDispose { owner.lifecycle.removeObserver(obs) }
    }
}

/** OpenStreetMap (osmdroid) map — never Google Maps. Single marker, optional tap. */
@Composable
fun OsmMap(
    lat: Double,
    lng: Double,
    modifier: Modifier = Modifier,
    description: String = "Map showing the location",
    zoom: Double = 15.0,
    onTap: ((Double, Double) -> Unit)? = null,
) {
    val context = LocalContext.current
    val mapView = remember { newMap(context, zoom) }
    val marker = remember { Marker(mapView) }
    lifecycleBind(mapView)
    AndroidView(
        modifier = modifier.semantics { contentDescription = description },
        factory = {
            marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
            mapView.overlays.add(marker)
            if (onTap != null) mapView.overlays.add(TapOverlay { p -> onTap(p.latitude, p.longitude) })
            mapView
        },
        update = { mv ->
            val p = GeoPoint(lat, lng)
            marker.position = p
            mv.controller.setCenter(p)
            mv.invalidate()
        },
        onRelease = { it.onDetach() },
    )
}

/** OSM map that also draws translucent safe-zone circles. */
@Composable
fun OsmMapZones(
    lat: Double,
    lng: Double,
    zones: List<Zone>,
    modifier: Modifier = Modifier,
    zoom: Double = 14.5,
    description: String = "Map showing live location and safe zones",
) {
    val context = LocalContext.current
    val mapView = remember { newMap(context, zoom) }
    val marker = remember { Marker(mapView) }
    lifecycleBind(mapView)
    AndroidView(
        modifier = modifier.semantics { contentDescription = description },
        factory = {
            marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
            mapView
        },
        update = { mv ->
            mv.overlays.clear()
            zones.forEach { z ->
                if (z.lat != 0.0 || z.lng != 0.0) {
                    val circle = Polygon(mv).apply {
                        points = Polygon.pointsAsCircle(GeoPoint(z.lat, z.lng), z.radiusM.toDouble())
                        fillPaint.color = AndroidColor.argb(40, 0x2E, 0x9E, 0x4F)
                        fillPaint.style = Paint.Style.FILL
                        outlinePaint.color = AndroidColor.argb(160, 0x2E, 0x9E, 0x4F)
                        outlinePaint.strokeWidth = 4f
                        setOnClickListener { _, _, _ -> true }
                    }
                    mv.overlays.add(circle)
                    mv.overlays.add(Marker(mv).apply {
                        position = GeoPoint(z.lat, z.lng)
                        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                        title = z.name
                        setTextIcon(z.name)
                        infoWindow = null
                        setOnMarkerClickListener { _, _ -> true }
                    })
                }
            }
            val p = GeoPoint(lat, lng)
            marker.position = p
            mv.overlays.add(marker)
            mv.controller.setCenter(p)
            mv.invalidate()
        },
        onRelease = { it.onDetach() },
    )
}

data class MapMarker(val lat: Double, val lng: Double, val label: String)

data class MapPoint(val lat: Double, val lng: Double)

data class FamilyMapMarker(
    val id: String,
    val lat: Double,
    val lng: Double,
    val label: String,
    val selected: Boolean = false,
)

/** OSM map showing several labelled child markers, auto-fitted to all of them. */
@Composable
fun OsmFamilyMap(markers: List<MapMarker>, modifier: Modifier = Modifier, description: String = "Family map") {
    val context = LocalContext.current
    val mapView = remember { newMap(context, 13.0) }
    lifecycleBind(mapView)
    AndroidView(
        modifier = modifier.semantics { contentDescription = description },
        factory = { mapView },
        update = { mv ->
            mv.overlays.clear()
            val pts = ArrayList<GeoPoint>()
            markers.forEach { m ->
                val gp = GeoPoint(m.lat, m.lng); pts.add(gp)
                mv.overlays.add(Marker(mv).apply {
                    position = gp
                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                    title = m.label
                    setTextIcon(m.label)
                })
            }
            when {
                pts.size == 1 -> { mv.controller.setZoom(15.0); mv.controller.setCenter(pts[0]) }
                pts.size > 1 -> {
                    val bbox = BoundingBox.fromGeoPoints(pts)
                    mv.post { runCatching { mv.zoomToBoundingBox(bbox.increaseByScale(1.5f), false, 70) } }
                }
            }
            mv.invalidate()
        },
        onRelease = { it.onDetach() },
    )
}

/** Live map with every located child, all safe zones, parent location, and explicit camera commands. */
@Composable
fun OsmLiveFamilyMap(
    childMarkers: List<FamilyMapMarker>,
    zones: List<Zone>,
    parentLocation: MapPoint?,
    cameraTarget: MapPoint?,
    cameraCommand: Long,
    modifier: Modifier = Modifier,
    zoom: Double = 14.5,
    description: String = "Map showing family locations and safe zones",
) {
    val context = LocalContext.current
    val mapView = remember { newMap(context, zoom) }
    var lastCameraCommand by remember { mutableStateOf<Long?>(null) }
    lifecycleBind(mapView)
    AndroidView(
        modifier = modifier.semantics { contentDescription = description },
        factory = { mapView },
        update = { mv ->
            mv.overlays.clear()
            zones.forEach { z ->
                if (z.lat != 0.0 || z.lng != 0.0) {
                    mv.overlays.add(Polygon(mv).apply {
                        points = Polygon.pointsAsCircle(GeoPoint(z.lat, z.lng), z.radiusM.toDouble())
                        fillPaint.color = AndroidColor.argb(36, 0x2E, 0x9E, 0x4F)
                        fillPaint.style = Paint.Style.FILL
                        outlinePaint.color = AndroidColor.argb(150, 0x2E, 0x9E, 0x4F)
                        outlinePaint.strokeWidth = 4f
                        setOnClickListener { _, _, _ -> true }
                    })
                    mv.overlays.add(Marker(mv).apply {
                        position = GeoPoint(z.lat, z.lng)
                        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                        title = z.name
                        setTextIcon(z.name)
                        infoWindow = null
                        setOnMarkerClickListener { _, _ -> true }
                    })
                }
            }
            offsetOverlappingMarkers(childMarkers).forEach { marker ->
                mv.overlays.add(Marker(mv).apply {
                    position = GeoPoint(marker.lat, marker.lng)
                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                    title = marker.label
                    setTextIcon(if (marker.selected) "* ${marker.label}" else marker.label)
                    alpha = if (marker.selected) 1f else 0.88f
                    infoWindow = null
                })
            }
            parentLocation?.let { parent ->
                mv.overlays.add(UserLocationDotOverlay(GeoPoint(parent.lat, parent.lng)))
            }
            if (cameraTarget != null && cameraCommand != lastCameraCommand) {
                if (mv.zoomLevelDouble < zoom) mv.controller.setZoom(zoom)
                mv.controller.animateTo(GeoPoint(cameraTarget.lat, cameraTarget.lng))
                lastCameraCommand = cameraCommand
            }
            mv.invalidate()
        },
        onRelease = { it.onDetach() },
    )
}

private class UserLocationDotOverlay(private val point: GeoPoint) : Overlay() {
    private val screenPoint = Point()
    private val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = AndroidColor.argb(55, 0x1A, 0x73, 0xE8)
        style = Paint.Style.FILL
    }
    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = AndroidColor.WHITE
        style = Paint.Style.FILL
    }
    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = AndroidColor.rgb(0x1A, 0x73, 0xE8)
        style = Paint.Style.FILL
    }

    override fun draw(canvas: Canvas, mapView: MapView, shadow: Boolean) {
        if (shadow) return
        mapView.projection.toPixels(point, screenPoint)
        canvas.drawCircle(screenPoint.x.toFloat(), screenPoint.y.toFloat(), 21f, shadowPaint)
        canvas.drawCircle(screenPoint.x.toFloat(), screenPoint.y.toFloat(), 11f, ringPaint)
        canvas.drawCircle(screenPoint.x.toFloat(), screenPoint.y.toFloat(), 8f, dotPaint)
    }
}

private fun offsetOverlappingMarkers(markers: List<FamilyMapMarker>): List<FamilyMapMarker> {
    val groups = markers.groupBy { "${(it.lat * 100000).roundToInt()}:${(it.lng * 100000).roundToInt()}" }
    return groups.values.flatMap { group ->
        if (group.size == 1) return@flatMap group
        val centerLat = group.map { it.lat }.average()
        val centerLng = group.map { it.lng }.average()
        val radiusM = 18.0
        group.mapIndexed { index, marker ->
            val angle = (2.0 * PI * index) / group.size
            val latOffset = (sin(angle) * radiusM) / 111_320.0
            val lngMeters = max(0.2, cos(Math.toRadians(centerLat)) * 111_320.0)
            val lngOffset = (cos(angle) * radiusM) / lngMeters
            marker.copy(lat = centerLat + latOffset, lng = centerLng + lngOffset)
        }
    }
}

/** Draws a route (departure → return) as a line with two markers. */
@Composable
fun OsmRoute(
    from: com.familyshield.mobile.net.Geo,
    to: com.familyshield.mobile.net.Geo,
    modifier: Modifier = Modifier,
    description: String = "Route from departure to return point",
) {
    val context = LocalContext.current
    val mapView = remember { newMap(context, 13.0) }
    lifecycleBind(mapView)
    AndroidView(
        modifier = modifier.semantics { contentDescription = description },
        factory = { mapView },
        update = { mv ->
            mv.overlays.clear()
            val a = GeoPoint(from.lat, from.lng)
            val b = GeoPoint(to.lat, to.lng)
            val line = Polyline(mv).apply {
                setPoints(listOf(a, b))
                outlinePaint.color = AndroidColor.argb(220, 0x0A, 0x6C, 0xDB)
                outlinePaint.strokeWidth = 8f
            }
            mv.overlays.add(line)
            mv.overlays.add(Marker(mv).apply { position = a; title = "Departure"; setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM) })
            mv.overlays.add(Marker(mv).apply { position = b; title = "Return"; setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM) })
            mv.controller.setCenter(GeoPoint((from.lat + to.lat) / 2, (from.lng + to.lng) / 2))
            mv.invalidate()
        },
        onRelease = { it.onDetach() },
    )
}

