package com.familyshield.app.net

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

@Serializable
private data class NominatimAddress(
    val road: String? = null,
    val pedestrian: String? = null,
    val suburb: String? = null,
    val neighbourhood: String? = null,
    val city: String? = null,
    val town: String? = null,
    val village: String? = null,
)

@Serializable
private data class NominatimResult(
    val address: NominatimAddress? = null,
    @SerialName("display_name") val displayName: String? = null,
)

/** Reverse geocoding via OpenStreetMap Nominatim — fully open-source, no Google services. */
object Geocoding {
    private val http = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS).readTimeout(10, TimeUnit.SECONDS).build()
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun reverse(lat: Double, lng: Double): String? = withContext(Dispatchers.IO) {
        try {
            val url = "https://nominatim.openstreetmap.org/reverse?format=json&zoom=17&lat=$lat&lon=$lng"
            val req = Request.Builder().url(url).header("User-Agent", "FamilyShield/1.0 (Android; OSM)").build()
            http.newCall(req).execute().use { res ->
                if (!res.isSuccessful) return@withContext null
                val r = json.decodeFromString<NominatimResult>(res.body?.string().orEmpty())
                val a = r.address
                a?.road ?: a?.pedestrian ?: a?.suburb ?: a?.neighbourhood
                    ?: a?.city ?: a?.town ?: a?.village ?: r.displayName?.substringBefore(",")
            }
        } catch (e: Exception) { null }
    }
}
