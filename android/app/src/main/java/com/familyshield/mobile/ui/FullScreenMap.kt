package com.familyshield.mobile.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.familyshield.mobile.net.Zone

/** Full-screen in-app OSM view of the whole family (multiple labelled markers). */
@Composable
fun FullScreenFamilyMap(markers: List<MapMarker>, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize().background(Color.White)) {
            OsmFamilyMap(markers, Modifier.fillMaxSize())
            Surface(
                color = Color.White.copy(alpha = 0.95f), shadowElevation = 4.dp,
                modifier = Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.statusBars),
            ) {
                Row(Modifier.padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Filled.Close, "Close map", tint = MaterialTheme.colorScheme.primary)
                    }
                    Text("Family map", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }
}

/** Full-screen in-app OpenStreetMap (osmdroid) view — keeps everything open-source
 *  (no external Google Maps launch). */
@Composable
fun FullScreenMap(
    lat: Double,
    lng: Double,
    title: String,
    zones: List<Zone> = emptyList(),
    onDismiss: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize().background(Color.White)) {
            OsmMapZones(lat, lng, zones, Modifier.fillMaxSize(), zoom = 16.0)
            Surface(
                color = Color.White.copy(alpha = 0.95f), shadowElevation = 4.dp,
                modifier = Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.statusBars),
            ) {
                Row(Modifier.padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Filled.Close, "Close map", tint = MaterialTheme.colorScheme.primary)
                    }
                    Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }
}
