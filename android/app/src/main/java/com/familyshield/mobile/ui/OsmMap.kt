package com.familyshield.mobile.ui

import android.content.Context
import android.graphics.Color as AndroidColor
import android.graphics.Paint
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
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
import org.osmdroid.views.overlay.Polygon
import org.osmdroid.views.overlay.Polyline

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
                    }
                    mv.overlays.add(circle)
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

