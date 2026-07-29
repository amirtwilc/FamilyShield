package com.familyshield.mobile.net

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.familyshield.mobile.BuildConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import kotlin.math.cos

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

internal const val GEOCODING_CACHE_COORDINATE_DECIMALS = 4
/** History groups points within 75 m, so a known name inside the same radius is
 * safe to reuse even when a recomputed stop center lands in a different grid key. */
internal const val GEOCODING_CACHE_REUSE_RADIUS_M = 75.0

internal data class SharedLookup<T>(val deferred: Deferred<T>, val shared: Boolean)

internal class IndependentLookupPool<T>(private val scope: CoroutineScope) {
    private val inFlight = mutableMapOf<String, Deferred<T>>()

    fun getOrStart(key: String, lookup: suspend () -> T): SharedLookup<T> =
        synchronized(inFlight) {
            inFlight[key]?.let { return@synchronized SharedLookup(it, shared = true) }
            val created = scope.async(start = CoroutineStart.LAZY) { lookup() }
            inFlight[key] = created
            created.invokeOnCompletion {
                synchronized(inFlight) { inFlight.remove(key, created) }
            }
            created.start()
            SharedLookup(created, shared = false)
        }
}

/** Reverse geocoding via OpenStreetMap Nominatim, with a persistent successful-
 * result cache and serialized requests to keep names stable and avoid bursts. */
object Geocoding {
    private const val LOG_TAG = "FS-Geocoding"
    private const val CACHE_PREFS = "geocoding_names"
    private const val MIN_REQUEST_INTERVAL_MS = 1_100L
    private const val FAILURE_RETRY_MS = 5 * 60_000L

    private val http = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()
    private val json = Json { ignoreUnknownKeys = true }
    private val names = ConcurrentHashMap<String, String>()
    private val failedAt = ConcurrentHashMap<String, Long>()
    private val lookupMutex = Mutex()
    private val lookupScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val lookupPool = IndependentLookupPool<String?>(lookupScope)

    @Volatile private var preferences: SharedPreferences? = null
    @Volatile private var languageTag: String = Locale.getDefault().toLanguageTag()
    private var lastRequestStartedAt = 0L

    fun initialize(context: Context) {
        val app = context.applicationContext
        languageTag = context.resources.configuration.locales[0].toLanguageTag()
        val prefs = app.getSharedPreferences(CACHE_PREFS, Context.MODE_PRIVATE)
        preferences = prefs
        prefs.all.forEach { (key, value) ->
            (value as? String)?.takeIf { it.isNotBlank() }?.let { names[key] = it }
        }
        debug("initialized language=$languageTag cachedNames=${names.size}")
    }

    fun cached(lat: Double, lng: Double): String? {
        if (!lat.isFinite() || !lng.isFinite()) return null
        val key = cacheKey(lat, lng)
        names[key]?.let { return it }
        val prefix = "$languageTag|"
        return names.entries.asSequence()
            .filter { it.key.startsWith(prefix) }
            .mapNotNull { entry ->
                parseCoordinateKey(entry.key.removePrefix(prefix))
                    ?.let { point -> entry.value to distanceMeters(lat, lng, point.first, point.second) }
            }
            .filter { (_, distance) -> distance <= GEOCODING_CACHE_REUSE_RADIUS_M }
            .minByOrNull { (_, distance) -> distance }
            ?.first
    }

    suspend fun reverse(lat: Double, lng: Double): String? {
        if (!lat.isFinite() || !lng.isFinite()) {
            warn("ignored invalid coordinates lat=$lat lng=$lng")
            return null
        }
        val key = cacheKey(lat, lng)
        val point = coordinateKey(lat, lng)
        cached(lat, lng)?.let {
            debug("cache hit point=$point name=${it.logValue()}")
            return it
        }
        if (recentlyFailed(key)) {
            debug("failure cooldown point=$point")
            return null
        }

        val lookup = lookupPool.getOrStart(key) {
            resolveAndCache(key, lat, lng)
        }
        if (!lookup.shared) {
            lookup.deferred.invokeOnCompletion { cause ->
                if (cause != null) {
                    warn("lookup failed point=$point cause=${cause.javaClass.simpleName}: ${cause.message}")
                }
            }
        }
        debug("${if (lookup.shared) "joined" else "started"} lookup point=$point")
        return lookup.deferred.await()
    }

    private suspend fun resolveAndCache(key: String, lat: Double, lng: Double): String? =
        lookupMutex.withLock {
            val point = coordinateKey(lat, lng)
            cached(lat, lng)?.let {
                debug("queued cache hit point=$point name=${it.logValue()}")
                return@withLock it
            }
            if (recentlyFailed(key)) {
                debug("queued failure cooldown point=$point")
                return@withLock null
            }

            val waitMs = MIN_REQUEST_INTERVAL_MS - (System.currentTimeMillis() - lastRequestStartedAt)
            if (waitMs > 0) {
                debug("rate-limit wait point=$point waitMs=$waitMs")
                delay(waitMs)
            }
            lastRequestStartedAt = System.currentTimeMillis()

            val resolved = requestName(lat, lng)
            if (resolved.isNullOrBlank()) {
                failedAt[key] = System.currentTimeMillis()
                warn("no name resolved point=$point; retryInMs=$FAILURE_RETRY_MS")
                null
            } else {
                names[key] = resolved
                failedAt.remove(key)
                preferences?.edit()?.putString(key, resolved)?.apply()
                debug("cached point=$point name=${resolved.logValue()}")
                resolved
            }
        }

    private fun recentlyFailed(key: String): Boolean =
        System.currentTimeMillis() - (failedAt[key] ?: 0L) < FAILURE_RETRY_MS

    private suspend fun requestName(lat: Double, lng: Double): String? = withContext(Dispatchers.IO) {
        try {
            val url = "https://nominatim.openstreetmap.org/reverse?format=json&zoom=17&lat=$lat&lon=$lng"
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "FamilyShield/1.0 (Android; OSM)")
                .header("Accept-Language", languageTag)
                .build()
            http.newCall(request).execute().use { response ->
                val point = coordinateKey(lat, lng)
                debug("response point=$point http=${response.code}")
                if (!response.isSuccessful) return@withContext null
                val body = response.body?.string().orEmpty()
                val result = json.decodeFromString<NominatimResult>(body)
                val address = result.address
                val name = address?.road ?: address?.pedestrian ?: address?.suburb ?: address?.neighbourhood
                    ?: address?.city ?: address?.town ?: address?.village
                    ?: result.displayName?.substringBefore(",")
                if (name.isNullOrBlank()) {
                    warn(
                        "empty parsed result point=$point bodyBytes=${body.toByteArray().size} " +
                            "hasAddress=${address != null} hasDisplayName=${!result.displayName.isNullOrBlank()}",
                    )
                }
                name
            }
        } catch (error: Exception) {
            error(
                "request exception point=${coordinateKey(lat, lng)} " +
                    "type=${error.javaClass.simpleName}: ${error.message}",
            )
            null
        }
    }

    private fun cacheKey(lat: Double, lng: Double): String =
        "$languageTag|${coordinateKey(lat, lng)}"

    private fun debug(message: String) {
        if (BuildConfig.DEBUG) Log.d(LOG_TAG, message)
    }

    private fun warn(message: String) {
        if (BuildConfig.DEBUG) Log.w(LOG_TAG, message)
    }

    private fun error(message: String) {
        if (BuildConfig.DEBUG) Log.e(LOG_TAG, message)
    }
}

internal fun coordinateKey(lat: Double, lng: Double): String =
    "%.${GEOCODING_CACHE_COORDINATE_DECIMALS}f,%.${GEOCODING_CACHE_COORDINATE_DECIMALS}f"
        .format(Locale.US, lat, lng)

private fun parseCoordinateKey(value: String): Pair<Double, Double>? {
    val parts = value.split(',', limit = 2)
    if (parts.size != 2) return null
    val lat = parts[0].toDoubleOrNull() ?: return null
    val lng = parts[1].toDoubleOrNull() ?: return null
    return lat to lng
}

private fun distanceMeters(aLat: Double, aLng: Double, bLat: Double, bLng: Double): Double {
    val dLat = Math.toRadians(bLat - aLat)
    val dLng = Math.toRadians(bLng - aLng)
    val h = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
        cos(Math.toRadians(aLat)) * cos(Math.toRadians(bLat)) *
        Math.sin(dLng / 2) * Math.sin(dLng / 2)
    return 6_371_000.0 * 2 * Math.atan2(Math.sqrt(h), Math.sqrt(1 - h))
}

private fun String.logValue(): String =
    replace('\n', ' ').take(80)
