package com.familyshield.app.ui

import android.view.MotionEvent
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Overlay

/** Reports a single-tap geo-point for editable maps. */
class TapOverlay(private val onTap: (GeoPoint) -> Unit) : Overlay() {
    override fun onSingleTapConfirmed(e: MotionEvent, mapView: MapView): Boolean {
        val p = mapView.projection.fromPixels(e.x.toInt(), e.y.toInt()) as GeoPoint
        onTap(p)
        return true
    }
}
