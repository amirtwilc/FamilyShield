package com.familyshield.mobile.parent

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.familyshield.mobile.ui.MapPoint
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

object ParentLocation {
    fun hasPermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    suspend fun currentOrLastKnown(context: Context): MapPoint? {
        val app = context.applicationContext
        if (!hasPermission(app)) return null
        val fused = LocationServices.getFusedLocationProviderClient(app)
        return suspendCancellableCoroutine { cont ->
            val cancellation = CancellationTokenSource()
            cont.invokeOnCancellation { cancellation.cancel() }
            try {
                fused.getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, cancellation.token)
                    .addOnSuccessListener { location ->
                        if (location != null) {
                            if (cont.isActive) cont.resume(MapPoint(location.latitude, location.longitude))
                        } else {
                            fused.lastLocation
                                .addOnSuccessListener { last -> if (cont.isActive) cont.resume(last?.let { MapPoint(it.latitude, it.longitude) }) }
                                .addOnFailureListener { if (cont.isActive) cont.resume(null) }
                        }
                    }
                    .addOnFailureListener {
                        fused.lastLocation
                            .addOnSuccessListener { last -> if (cont.isActive) cont.resume(last?.let { MapPoint(it.latitude, it.longitude) }) }
                            .addOnFailureListener { if (cont.isActive) cont.resume(null) }
                    }
            } catch (_: SecurityException) {
                if (cont.isActive) cont.resume(null)
            }
        }
    }
}
